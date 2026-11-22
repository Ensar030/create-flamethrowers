package dev.ohnelags.flamethrowers.content;

import java.util.List;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.ohnelags.flamethrowers.FlameConfig;
import dev.ohnelags.flamethrowers.FlameSpace;
import dev.ohnelags.flamethrowers.FlamethrowerType;
import dev.ohnelags.flamethrowers.burn.FlameLogic;
import dev.ohnelags.flamethrowers.client.FlameEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FlamethrowerBlockEntity extends KineticBlockEntity {
    public static final int CAPACITY = 4000;
    public static final int NAPALM_SHOT = 100;
    /** Distance of the nozzle mouth from the block centre. */
    public static final double MOUTH = 0.55;

    public SmartFluidTankBehaviour tank;

    /** Synced to the client: the flame is burning. */
    private boolean firing;
    private float lavaDebt;
    private int shotCooldown;
    private long age;
    /** Client: animation phase of the vortex. */
    public float spin;

    public enum Status { FIRING, NO_LAVA, TOO_SLOW, OVERSTRESSED, REDSTONE }

    public FlamethrowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        tank = SmartFluidTankBehaviour.single(this, CAPACITY).forbidExtraction();
        tank.getPrimaryHandler().setValidator(fs -> fs.getFluid().is(FluidTags.LAVA));
        behaviours.add(tank);
    }

    public FlamethrowerType type() {
        return getBlockState().getBlock() instanceof FlamethrowerBlock b ? b.type : FlamethrowerType.JET;
    }

    public Direction facing() {
        return getBlockState().getValue(FlamethrowerBlock.FACING);
    }

    public boolean isFiring() {
        return firing;
    }

    public int lava() {
        return tank == null ? 0 : tank.getPrimaryHandler().getFluidAmount();
    }

    /** Shaft speed factor: 1 at 64 RPM, up to 4 at 256 RPM. */
    public float power() {
        return Mth.clamp(Math.abs(getSpeed()) / 64f, 0.25f, 4f);
    }

    public float rangeMul() {
        return Mth.clamp(0.6f + 0.4f * power(), 0.7f, 1.6f);
    }

    public float heatMul() {
        return Mth.clamp(power(), 0.4f, 3f);
    }

    public Status status() {
        if (level != null && level.hasNeighborSignal(worldPosition)) {
            return Status.REDSTONE;
        }
        if (isOverStressed()) {
            return Status.OVERSTRESSED;
        }
        if (getSpeed() == 0 || !isSpeedRequirementFulfilled()) {
            return Status.TOO_SLOW;
        }
        if (lava() <= 0 || type() == FlamethrowerType.NAPALM && lava() < NAPALM_SHOT && shotCooldown <= 0) {
            return Status.NO_LAVA;
        }
        return Status.FIRING;
    }

    public FlameSpace space() {
        return FlameSpace.of(level, worldPosition, facing(), MOUTH);
    }

    @Override
    public float calculateStressApplied() {
        float impact = (float) FlameConfig.stress(type());
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        age++;
        if (level.isClientSide) {
            if (firing) {
                FlameEffects.tick(this);
            }
            return;
        }
        boolean shouldFire = status() == Status.FIRING;
        if (shouldFire != firing) {
            firing = shouldFire;
            if (firing) {
                level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8f, 0.7f);
            }
            sendData();
        }
        if (!firing) {
            return;
        }
        FlamethrowerType type = type();
        FlameSpace f = space();
        ServerLevel sl = (ServerLevel) level;
        if (type == FlamethrowerType.NAPALM) {
            if (shotCooldown > 0) {
                shotCooldown--;
                return;
            }
            int cost = Math.max(1, Math.round(NAPALM_SHOT * (float) FlameConfig.num(FlameConfig.LAVA_MULTIPLIER)));
            if (FlameConfig.num(FlameConfig.LAVA_MULTIPLIER) > 0) {
                FluidStack drained = tank.getPrimaryHandler().drain(cost, IFluidHandler.FluidAction.EXECUTE);
                if (drained.getAmount() < cost) {
                    return;
                }
            }
            shotCooldown = Math.max(5, Math.round(20 / power()));
            double speed = 1.25 * Math.sqrt(rangeMul() * FlameConfig.num(FlameConfig.RANGE_MULTIPLIER));
            // mortar arc: about 30 degrees above the barrel (a barrel pointing straight up/down shoots straight)
            Vec3 dir = Math.abs(f.dir().y) > 0.9 ? f.dir() : f.dir().add(0, 0.55, 0).normalize();
            Vec3 v = dir.scale(speed).add(f.velocity());
            NapalmBlob.fire(sl, f.origin().add(f.dir().scale(0.4)), v, heatMul());
            level.playSound(null, BlockPos.containing(f.origin()), SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0f, 0.6f);
            return;
        }
        // continuous flames: lava per tick, scaled with the heat
        lavaDebt += type.lavaPerTick * heatMul() * (float) FlameConfig.num(FlameConfig.LAVA_MULTIPLIER);
        if (lavaDebt >= 1) {
            int amount = (int) lavaDebt;
            FluidStack drained = tank.getPrimaryHandler().drain(amount, IFluidHandler.FluidAction.EXECUTE);
            lavaDebt -= amount;
            if (drained.getAmount() < amount) {
                lavaDebt = 0;
            }
        }
        FlameLogic.tick(sl, type, f, rangeMul(), heatMul(), age);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Firing", firing);
        if (!clientPacket) {
            tag.putFloat("LavaDebt", lavaDebt);
            tag.putInt("ShotCooldown", shotCooldown);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        firing = tag.getBoolean("Firing");
        if (!clientPacket) {
            lavaDebt = tag.getFloat("LavaDebt");
            shotCooldown = tag.getInt("ShotCooldown");
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        FlamethrowerType type = type();
        tooltip.add(Component.literal("    ").append(Component.translatable("create_flamethrowers.goggles.title",
                Component.translatable("block.create_flamethrowers." + type.blockName()))).withStyle(ChatFormatting.GRAY));
        Status s = firing ? Status.FIRING : status();
        tooltip.add(Component.literal("     ").append(Component.translatable("create_flamethrowers.status." + s.name().toLowerCase()))
                .withStyle(s == Status.FIRING ? ChatFormatting.GOLD : ChatFormatting.RED));
        tooltip.add(Component.literal("     ").append(Component.translatable("create_flamethrowers.goggles.lava", lava(), CAPACITY))
                .withStyle(ChatFormatting.GOLD));
        if (getSpeed() != 0) {
            double range = type.range * rangeMul() * FlameConfig.num(FlameConfig.RANGE_MULTIPLIER);
            tooltip.add(Component.literal("     ").append(Component.translatable("create_flamethrowers.goggles.range",
                    String.format("%.1f", range), String.format("%.1f", heatMul()))).withStyle(ChatFormatting.DARK_GRAY));
            if (type == FlamethrowerType.NAPALM) {
                tooltip.add(Component.literal("     ").append(Component.translatable("create_flamethrowers.goggles.napalm_use",
                        NAPALM_SHOT, String.format("%.1f", 20f / Math.max(5, Math.round(20 / power()))))).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                float perSecond = type.lavaPerTick * heatMul() * 20 * (float) FlameConfig.num(FlameConfig.LAVA_MULTIPLIER);
                tooltip.add(Component.literal("     ").append(Component.translatable("create_flamethrowers.goggles.use",
                        Math.round(perSecond))).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return true;
    }
}
