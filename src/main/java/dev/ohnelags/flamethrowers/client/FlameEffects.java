package dev.ohnelags.flamethrowers.client;

import dev.ohnelags.flamethrowers.FlameConfig;
import dev.ohnelags.flamethrowers.FlameSpace;
import dev.ohnelags.flamethrowers.FlamethrowerType;
import dev.ohnelags.flamethrowers.Flamethrowers;
import dev.ohnelags.flamethrowers.burn.FlameLogic;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Client side: particles and sound of a burning flamethrower. Positions are in world space (see {@link FlameSpace}). */
public final class FlameEffects {
    private FlameEffects() {
    }

    public static void tick(FlamethrowerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        RandomSource r = level.random;
        FlamethrowerType type = be.type();
        FlameSpace f = be.space();
        Vec3 o = f.origin();
        Vec3 carry = f.velocity();
        double range = type.range * be.rangeMul() * FlameConfig.num(FlameConfig.RANGE_MULTIPLIER);
        long time = level.getGameTime();

        switch (type) {
            case JET -> {
                double speed = range / 15.5;
                for (int i = 0; i < 10; i++) {
                    Vec3 d = jitter(f.dir(), 0.035, r);
                    double s = speed * (0.9 + 0.2 * r.nextDouble());
                    Vec3 p = o.add(d.scale(r.nextDouble() * s));
                    add(level, Flamethrowers.FLAME_STREAM.get(), p, d.scale(s).add(carry));
                }
                if (r.nextInt(3) == 0) {
                    add(level, Flamethrowers.EMBER.get(), o, jitter(f.dir(), 0.15, r).scale(speed * 0.8).add(carry));
                }
            }
            case FAN -> {
                double half = Math.toRadians(type.spread);
                double speed = range / 6.5;
                for (int i = 0; i < 10; i++) {
                    Vec3 d = FlameLogic.cone(f, half, r);
                    double s = speed * (0.75 + 0.35 * r.nextDouble());
                    add(level, Flamethrowers.FLAME_PUFF.get(), o.add(d.scale(r.nextDouble() * 0.5)), d.scale(s).add(carry));
                }
            }
            case TORNADO -> {
                // a spinning three-armed helix of fire that widens along its length
                be.spin += Math.abs(be.getSpeed()) * 0.004f + 0.15f;
                double maxR = type.spread * Math.min(1.4, be.rangeMul());
                int perArm = 14;
                for (int i = 0; i < perArm * 3; i++) {
                    int arm = i % 3;
                    double t = 0.3 + ((i / 3) + r.nextDouble()) / perArm * (range - 0.3);
                    double ang = be.spin + t * 0.55 + arm * (Math.PI * 2 / 3) + (r.nextDouble() - 0.5) * 0.2;
                    // opens up like a funnel right after the nozzle, then widens slowly
                    double rad = maxR * (0.55 + 0.45 * t / range) * Math.min(1, 0.15 + t / 3) * (0.9 + 0.2 * r.nextDouble());
                    Vec3 radial = f.side().scale(Math.cos(ang)).add(f.up().scale(Math.sin(ang)));
                    Vec3 p = o.add(f.dir().scale(t)).add(radial.scale(rad));
                    if (level.getBlockState(BlockPos.containing(p)).isSolidRender(level, BlockPos.containing(p))) {
                        continue;
                    }
                    Vec3 tangent = f.dir().cross(radial);
                    Vec3 v = tangent.scale(0.12 + rad * 0.05).add(f.dir().scale(0.15)).add(carry);
                    add(level, Flamethrowers.FLAME_SWIRL.get(), p, v);
                }
                for (int i = 0; i < 3; i++) {
                    add(level, Flamethrowers.FLAME_STREAM.get(), o, jitter(f.dir(), 0.08, r).scale(range / 15.5).add(carry));
                }
            }
            case NAPALM -> {
                // pilot flame at the muzzle
                if (r.nextInt(2) == 0) {
                    add(level, ParticleTypes.SMALL_FLAME, o.add(f.up().scale(0.1)), f.dir().scale(0.02).add(0, 0.01, 0).add(carry));
                }
                if (r.nextInt(6) == 0) {
                    add(level, ParticleTypes.SMOKE, o, f.dir().scale(0.03).add(0, 0.03, 0).add(carry));
                }
            }
        }

        if (type != FlamethrowerType.NAPALM) {
            if (r.nextInt(4) == 0) {
                add(level, ParticleTypes.SMOKE, o.add(f.dir().scale(range * (0.6 + 0.4 * r.nextDouble()))), new Vec3(0, 0.05, 0).add(carry));
            }
            if (time % 6 == 0) {
                level.playLocalSound(o.x, o.y, o.z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.6f, 0.5f + r.nextFloat() * 0.2f, false);
            }
            if (time % 14 == 0) {
                level.playLocalSound(o.x, o.y, o.z, SoundEvents.BLAZE_BURN, SoundSource.BLOCKS, 0.5f, 0.5f + r.nextFloat() * 0.2f, false);
            }
        }
    }

    private static void add(Level level, ParticleOptions p, Vec3 pos, Vec3 vel) {
        level.addParticle(p, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
    }

    private static Vec3 jitter(Vec3 d, double amount, RandomSource r) {
        return d.add((r.nextDouble() - 0.5) * 2 * amount, (r.nextDouble() - 0.5) * 2 * amount, (r.nextDouble() - 0.5) * 2 * amount).normalize();
    }
}
