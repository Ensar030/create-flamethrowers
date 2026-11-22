package dev.ohnelags.flamethrowers.content;

import dev.ohnelags.flamethrowers.Flamethrowers;
import dev.ohnelags.flamethrowers.burn.BurnManager;
import dev.ohnelags.flamethrowers.burn.FlameLogic;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A sticky burning lump of napalm. Flies in a ballistic arc, sticks where it hits (ground, tree, mob) and burns
 * everything around it for a few seconds.
 */
public class NapalmBlob extends Projectile {
    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(NapalmBlob.class, EntityDataSerializers.BOOLEAN);
    public static final int BURN_TICKS = 100;
    public static final double RADIUS = 2.6;

    private int burnTicks;
    private int flightTicks;
    private float heatMul = 1;

    public NapalmBlob(EntityType<? extends NapalmBlob> type, Level level) {
        super(type, level);
    }

    public static NapalmBlob fire(ServerLevel level, Vec3 pos, Vec3 velocity, float heatMul) {
        NapalmBlob b = new NapalmBlob(Flamethrowers.NAPALM_BLOB.get(), level);
        b.setPos(pos);
        b.setDeltaMovement(velocity);
        b.heatMul = heatMul;
        level.addFreshEntity(b);
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LANDED, false);
    }

    public boolean landed() {
        return entityData.get(LANDED);
    }

    @Override
    public void tick() {
        super.tick();
        Level level = level();
        if (landed()) {
            if (level instanceof ServerLevel sl) {
                burnTicks++;
                FlameLogic.splash(sl, position(), RADIUS, 1.6f * heatMul, random, 10);
                if (burnTicks % 5 == 0) {
                    AABB box = new AABB(position(), position()).inflate(RADIUS);
                    for (Entity e : sl.getEntities(this, box, e -> e instanceof LivingEntity && e.isAlive())) {
                        FlameLogic.burnEntity(sl, e, heatMul * 0.6f);
                    }
                }
                if (isInWaterOrRain() && random.nextInt(4) == 0 || burnTicks > BURN_TICKS) {
                    discard();
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 2 * RADIUS * 0.6, getY() + 0.1,
                            getZ() + (random.nextDouble() - 0.5) * 2 * RADIUS * 0.6, 0, 0.06 + random.nextDouble() * 0.05, 0);
                }
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 0, 0.08, 0);
                }
            }
            return;
        }

        flightTicks++;
        Vec3 v = getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            hitTargetOrDeflectSelf(hit);
            if (isRemoved() || landed()) {
                return;
            }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        setDeltaMovement(v.scale(0.99).add(0, -0.045, 0));
        if (level.isClientSide) {
            level.addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), -v.x * 0.1, -v.y * 0.1, -v.z * 0.1);
            level.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
        } else {
            if (level.getFluidState(blockPosition()).is(FluidTags.WATER)) {
                ((ServerLevel) level).sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                discard();
            } else if (flightTicks > 200 || getY() < level.getMinBuildHeight()) {
                discard();
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level() instanceof ServerLevel sl) {
            FlameLogic.burnEntity(sl, result.getEntity(), heatMul * 2);
            land(result.getLocation());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel) {
            Vec3 at = result.getLocation().add(Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(0.1));
            land(at);
            BurnManager.igniteSurface((ServerLevel) level(), result.getBlockPos(), result.getDirection());
        }
    }

    private void land(Vec3 at) {
        setPos(at);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(LANDED, true);
        ServerLevel sl = (ServerLevel) level();
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.6f);
        sl.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, 0.6, 0.3, 0.6, 0.1);
        // the first splash is strong
        FlameLogic.splash(sl, at, RADIUS, 12f * heatMul, random, 60);
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Landed", landed());
        tag.putInt("BurnTicks", burnTicks);
        tag.putFloat("Heat", heatMul);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(LANDED, tag.getBoolean("Landed"));
        burnTicks = tag.getInt("BurnTicks");
        heatMul = tag.contains("Heat") ? tag.getFloat("Heat") : 1;
    }
}
