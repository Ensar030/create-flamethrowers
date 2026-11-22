package dev.ohnelags.flamethrowers;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a flamethrower really is. A flamethrower on a Create Aeronautics vehicle or a Let it Walk! walker sits on a
 * Sable sub-level: its block position lies in the far-away plot grid, and the vehicle can be turned and tilted. The
 * flame always acts in the real world, so origin and direction are transformed with the sub-level's pose.
 *
 * @param origin   world position of the nozzle mouth
 * @param dir      world direction of the flame (unit vector)
 * @param side     unit vector perpendicular to dir
 * @param up       unit vector perpendicular to dir and side
 * @param velocity world velocity of the nozzle in blocks per tick (zero on the ground)
 * @param subLevel the ship / walker the flamethrower is mounted on, or null
 */
public record FlameSpace(Vec3 origin, Vec3 dir, Vec3 side, Vec3 up, Vec3 velocity, SubLevel subLevel) {

    public static FlameSpace of(Level level, BlockPos pos, Direction facing, double mouthOffset) {
        Vec3 localDir = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vec3 localOrigin = Vec3.atCenterOf(pos).add(localDir.scale(mouthOffset));
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        if (sub == null) {
            return make(localOrigin, localDir, Vec3.ZERO, null);
        }
        Pose3dc pose = sub instanceof ClientSubLevel c ? c.renderPose() : sub.logicalPose();
        Vec3 origin = pose.transformPosition(localOrigin);
        Vec3 dir = pose.transformNormal(localDir).normalize();
        Vec3 vel = Vec3.ZERO;
        try {
            // Sable reports velocities in blocks per second
            vel = Sable.HELPER.getVelocity(level, Vec3.atCenterOf(pos)).scale(1 / 20.0);
        } catch (RuntimeException ignored) {
        }
        return make(origin, dir, vel, sub);
    }

    private static FlameSpace make(Vec3 origin, Vec3 dir, Vec3 vel, SubLevel sub) {
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(ref).normalize();
        Vec3 up = side.cross(dir).normalize();
        return new FlameSpace(origin, dir, side, up, vel, sub);
    }

    /** True if the entity stands on or rides the same ship / walker as the flamethrower. */
    public boolean isCrew(Entity e) {
        if (subLevel == null) {
            return false;
        }
        try {
            return Sable.HELPER.getTrackingOrVehicleSubLevel(e) == subLevel;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public Vec3 at(double along, double sideways, double upwards) {
        return origin.add(dir.scale(along)).add(side.scale(sideways)).add(up.scale(upwards));
    }
}
