package dev.ohnelags.flamethrowers.burn;

import java.util.List;

import dev.ohnelags.flamethrowers.FlameConfig;
import dev.ohnelags.flamethrowers.FlameSpace;
import dev.ohnelags.flamethrowers.FlamethrowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What the flame does in the world each tick (server side). Everything works in world space. */
public final class FlameLogic {
    private static final double STEP = 0.4;

    private FlameLogic() {
    }

    /**
     * @param power    shaft speed factor (1 = 64 RPM)
     * @param rangeMul range factor from the shaft speed
     * @param heatMul  heat factor from the shaft speed
     */
    public static void tick(ServerLevel level, FlamethrowerType type, FlameSpace f, float rangeMul, float heatMul, long age) {
        double range = type.range * rangeMul * FlameConfig.num(FlameConfig.RANGE_MULTIPLIER);
        RandomSource r = level.random;
        switch (type) {
            case JET -> {
                march(level, f.origin(), f.dir(), range, type.heat * heatMul, 0.55f, true);
                // a second, slightly offset ray each tick makes the jet about two blocks thick
                double a = (age % 8) * (Math.PI / 4);
                Vec3 o = f.at(0, Math.cos(a) * 0.55, Math.sin(a) * 0.55);
                march(level, o, f.dir(), range * 0.9, type.heat * heatMul * 0.8f, 0.55f, false);
                if (age % 4 == 0) {
                    hurtAlongRay(level, f, range, 1.0, heatMul);
                }
            }
            case FAN -> {
                double half = Math.toRadians(type.spread);
                for (int i = 0; i < 10; i++) {
                    Vec3 d = cone(f, half, r);
                    march(level, f.origin(), d, range * (0.8 + 0.2 * r.nextDouble()), type.heat * heatMul, 0.35f, i == 0);
                }
                if (age % 4 == 0) {
                    hurtInCone(level, f, range, half, heatMul);
                }
            }
            case TORNADO -> {
                double reach = march(level, f.origin(), f.dir(), range, type.heat * heatMul, 0.6f, true);
                double end = Math.min(range, reach + 1.5);
                double maxR = type.spread * Math.min(1.4, rangeMul);
                for (int i = 0; i < 48; i++) {
                    double t = 0.8 + r.nextDouble() * (end - 0.8);
                    double rad = maxR * (0.55 + 0.45 * t / range) * Math.sqrt(r.nextDouble());
                    double ang = r.nextDouble() * Math.PI * 2;
                    Vec3 p = f.at(t, Math.cos(ang) * rad, Math.sin(ang) * rad);
                    BlockPos bp = BlockPos.containing(p);
                    BurnManager.Result res = BurnManager.addHeat(level, bp, type.heat * heatMul * (float) (1 - 0.35 * rad / maxR));
                    if (res == BurnManager.Result.IMMUNE && r.nextInt(30) == 0
                            && Burnables.blocksFlame(level, bp, level.getBlockState(bp))) {
                        BurnManager.tryIgnite(level, bp.above());
                    }
                }
                if (age % 2 == 0) {
                    vortex(level, f, end, maxR, heatMul, age);
                }
            }
            case NAPALM -> {
                // handled by the block entity (fires projectiles)
            }
        }
    }

    /**
     * Walks a ray through the world, heating every burnable block on its way. Burnable blocks that are still
     * standing weaken the flame, a solid non-burnable block (or water) stops it.
     *
     * @return the distance the flame reached
     */
    public static double march(ServerLevel level, Vec3 origin, Vec3 dir, double range, float heat, float attenuation, boolean ignite) {
        float power = 1f;
        BlockPos last = null;
        BlockPos lastFree = null;
        for (double t = 0.3; t <= range; t += STEP) {
            Vec3 p = origin.add(dir.scale(t));
            BlockPos bp = BlockPos.containing(p);
            if (bp.equals(last)) {
                continue;
            }
            last = bp;
            if (!level.isLoaded(bp)) {
                return t;
            }
            BlockState s = level.getBlockState(bp);
            if (s.isAir()) {
                lastFree = bp;
                continue;
            }
            float falloff = (float) (1 - 0.45 * t / range);
            BurnManager.Result res = BurnManager.addHeat(level, bp, heat * power * falloff);
            if (res == BurnManager.Result.BURNED) {
                lastFree = bp;
                continue;
            }
            if (res == BurnManager.Result.HEATING) {
                power *= attenuation;
                if (power < 0.04f) {
                    return t;
                }
                continue;
            }
            // not burnable
            if (Burnables.blocksFlame(level, bp, s)) {
                if (ignite && lastFree != null && level.random.nextInt(12) == 0) {
                    BurnManager.tryIgnite(level, lastFree);
                }
                return t;
            }
            lastFree = bp;
        }
        if (ignite && lastFree != null && level.random.nextInt(25) == 0) {
            BurnManager.tryIgnite(level, lastFree);
        }
        return range;
    }

    /** Random direction inside a cone of the given half angle around the flame direction. */
    public static Vec3 cone(FlameSpace f, double half, RandomSource r) {
        double a = half * Math.sqrt(r.nextDouble());
        double az = r.nextDouble() * Math.PI * 2;
        double s = Math.sin(a);
        return f.dir().scale(Math.cos(a)).add(f.side().scale(Math.cos(az) * s)).add(f.up().scale(Math.sin(az) * s)).normalize();
    }

    private static List<Entity> candidates(ServerLevel level, FlameSpace f, double range, double grow) {
        Vec3 end = f.origin().add(f.dir().scale(range));
        AABB box = new AABB(f.origin(), end).inflate(grow);
        return level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator() && e instanceof LivingEntity);
    }

    private static boolean shouldHurt(FlameSpace f, Entity e) {
        return FlameConfig.bool(FlameConfig.HURT_CREW) || !f.isCrew(e);
    }

    public static void burnEntity(ServerLevel level, Entity e, float heatMul) {
        e.igniteForSeconds(6);
        float dmg = (float) (FlameConfig.num(FlameConfig.ENTITY_DAMAGE) * Mth.clamp(heatMul, 0.5f, 2.5f));
        if (dmg > 0) {
            e.hurt(level.damageSources().inFire(), dmg);
        }
    }

    private static void hurtAlongRay(ServerLevel level, FlameSpace f, double range, double radius, float heatMul) {
        for (Entity e : candidates(level, f, range, radius + 1)) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 rel = c.subtract(f.origin());
            double along = rel.dot(f.dir());
            if (along < 0 || along > range) {
                continue;
            }
            double dist = rel.subtract(f.dir().scale(along)).length();
            if (dist <= radius + e.getBbWidth() * 0.5 && shouldHurt(f, e)) {
                burnEntity(level, e, heatMul);
            }
        }
    }

    private static void hurtInCone(ServerLevel level, FlameSpace f, double range, double half, float heatMul) {
        for (Entity e : candidates(level, f, range, range * Math.sin(half) + 1)) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(f.origin());
            double len = rel.length();
            if (len > range || len < 1e-3) {
                continue;
            }
            double angle = Math.acos(Mth.clamp(rel.dot(f.dir()) / len, -1, 1));
            if ((angle <= half + 0.15 || len < 1.2) && shouldHurt(f, e)) {
                burnEntity(level, e, heatMul);
            }
        }
    }

    /** The tornado sucks mobs to its axis, spins them around, lifts and pushes them, and burns them. */
    private static void vortex(ServerLevel level, FlameSpace f, double length, double maxR, float heatMul, long age) {
        for (Entity e : candidates(level, f, length, maxR + 1)) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(f.origin());
            double along = rel.dot(f.dir());
            if (along < 0 || along > length) {
                continue;
            }
            Vec3 radial = rel.subtract(f.dir().scale(along));
            double dist = radial.length();
            double r = maxR * (0.55 + 0.45 * along / length) + 0.5;
            if (dist > r || !shouldHurt(f, e)) {
                continue;
            }
            Vec3 inward = dist > 1e-3 ? radial.scale(-1 / dist) : Vec3.ZERO;
            Vec3 swirl = f.dir().cross(inward);
            Vec3 push = swirl.scale(0.18).add(inward.scale(0.08)).add(f.dir().scale(0.06)).add(0, 0.07, 0);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(push));
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (age % 4 == 0) {
                burnEntity(level, e, heatMul);
            }
        }
    }

    /** Burns everything in a sphere (napalm splash). */
    public static void splash(ServerLevel level, Vec3 center, double radius, float heat, RandomSource r, int samples) {
        for (int i = 0; i < samples; i++) {
            Vec3 p = center.add((r.nextDouble() * 2 - 1) * radius, (r.nextDouble() * 2 - 1) * radius, (r.nextDouble() * 2 - 1) * radius);
            if (p.distanceToSqr(center) > radius * radius) {
                continue;
            }
            BlockPos bp = BlockPos.containing(p);
            BurnManager.Result res = BurnManager.addHeat(level, bp, heat);
            if (res == BurnManager.Result.IMMUNE && r.nextInt(6) == 0 && Burnables.blocksFlame(level, bp, level.getBlockState(bp))) {
                BurnManager.igniteSurface(level, bp, Direction.UP);
            }
        }
    }
}
