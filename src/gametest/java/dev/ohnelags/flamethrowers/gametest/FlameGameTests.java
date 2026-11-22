package dev.ohnelags.flamethrowers.gametest;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ohnelags.flamethrowers.FlameSpace;
import dev.ohnelags.flamethrowers.FlamethrowerType;
import dev.ohnelags.flamethrowers.Flamethrowers;
import dev.ohnelags.flamethrowers.burn.BurnManager;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlock;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlockEntity;
import dev.ohnelags.flamethrowers.content.NapalmBlob;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Flamethrowers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlameGameTests {
    private FlameGameTests() {
    }

    private static void floor(ServerLevel level, BlockPos min, int sx, int sz) {
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                level.setBlock(min.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    /** Oak-like tree: trunk of the given height at base, leaf ball around the top. Returns all its blocks. */
    private static List<BlockPos> tree(ServerLevel level, BlockPos base, int height) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dy = height - 2; dy <= height + 1; dy++) {
            int r = dy >= height ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    level.setBlock(p, leaves, 2);
                    blocks.add(p);
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            BlockPos p = base.above(dy);
            level.setBlock(p, Blocks.OAK_LOG.defaultBlockState(), 2);
            blocks.add(p);
        }
        return blocks;
    }

    private static long remaining(ServerLevel level, List<BlockPos> blocks) {
        return blocks.stream().map(level::getBlockState)
                .filter(s -> s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_LEAVES)).count();
    }

    /** Creative motor + flamethrower pointing east, full of lava. Returns the flamethrower position. */
    private static BlockPos rig(ServerLevel level, BlockPos motorPos, FlamethrowerType type, int rpm) {
        level.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST), 3);
        BlockPos ft = motorPos.east();
        level.setBlock(ft, Flamethrowers.FLAMETHROWERS.get(type).get().defaultBlockState().setValue(FlamethrowerBlock.FACING, Direction.EAST), 3);
        if (level.getBlockEntity(motorPos) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
        }
        fill(level, ft);
        return ft;
    }

    private static void fill(ServerLevel level, BlockPos ft) {
        if (level.getBlockEntity(ft) instanceof FlamethrowerBlockEntity be) {
            be.tank.getPrimaryHandler().fill(new FluidStack(Fluids.LAVA, FlamethrowerBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static FlamethrowerBlockEntity be(GameTestHelper h, ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FlamethrowerBlockEntity be) {
            return be;
        }
        h.fail("no flamethrower at " + pos);
        return null;
    }

    @GameTest(template = "flame_arena", timeoutTicks = 400)
    public static void jetBurnsTreeDown(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 20));
        floor(level, o.offset(0, -1, -4), 30, 9);
        BlockPos ft = rig(level, o, FlamethrowerType.JET, 64);
        List<BlockPos> tree = tree(level, o.offset(8, 0, 0), 6);
        BlockPos wall = o.offset(14, 0, 0);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        BlockPos plankWall = o.offset(12, 1, 0); // not in line: stays
        level.setBlock(plankWall, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        // a log hidden behind stone: the flame must not reach it
        BlockPos hidden = o.offset(15, 0, 0);
        level.setBlock(hidden, Blocks.OAK_LOG.defaultBlockState(), 3);
        long before = BurnManager.burnedTotal(level);

        h.startSequence()
                .thenExecuteAfter(10, () -> {
                    FlamethrowerBlockEntity be = be(h, level, ft);
                    h.assertTrue(be.getSpeed() != 0, "flamethrower not powered: " + be.getSpeed());
                    h.assertTrue(be.isFiring(), "jet not firing, status " + be.status());
                })
                .thenExecuteAfter(140, () -> {
                    long left = remaining(level, tree);
                    FlamethrowerBlockEntity be = be(h, level, ft);
                    Flamethrowers.LOG.info("[test] jet: {} of {} tree blocks left, {} burned, lava {}",
                            left, tree.size(), BurnManager.burnedTotal(level) - before, be.lava());
                    h.assertTrue(level.getBlockState(o.offset(8, 0, 0)).isAir() || level.getBlockState(o.offset(8, 0, 0)).is(Blocks.FIRE),
                            "trunk in the flame not burned");
                    h.assertTrue(left <= tree.size() / 10, "tree did not burn down: " + left + " of " + tree.size() + " left");
                    h.assertTrue(level.getBlockState(wall).is(Blocks.STONE), "stone burned");
                    h.assertTrue(level.getBlockState(hidden).is(Blocks.OAK_LOG), "flame went through stone");
                    h.assertTrue(be.lava() < FlamethrowerBlockEntity.CAPACITY, "no lava used");
                })
                .thenSucceed();
    }

    @GameTest(template = "flame_arena", timeoutTicks = 400)
    public static void noLavaNoFlame(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 20));
        floor(level, o.offset(0, -1, -2), 16, 5);
        BlockPos ft = rig(level, o, FlamethrowerType.JET, 64);
        if (level.getBlockEntity(ft) instanceof FlamethrowerBlockEntity be) {
            be.tank.getPrimaryHandler().drain(FlamethrowerBlockEntity.CAPACITY, IFluidHandler.FluidAction.EXECUTE);
            // water is refused
            int took = be.tank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(took == 0, "tank accepted water");
        }
        BlockPos log = o.offset(4, 0, 0);
        level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 3);
        h.startSequence()
                .thenExecuteAfter(60, () -> {
                    FlamethrowerBlockEntity be = be(h, level, ft);
                    h.assertFalse(be.isFiring(), "fires without lava");
                    h.assertTrue(be.status() == FlamethrowerBlockEntity.Status.NO_LAVA, "status " + be.status());
                    h.assertTrue(level.getBlockState(log).is(Blocks.OAK_LOG), "log burned without lava");
                    // too slow: 16 RPM is below the medium speed requirement
                    if (level.getBlockEntity(o) instanceof CreativeMotorBlockEntity motor) {
                        motor.generatedSpeed.setValue(16);
                    }
                    fill(level, ft);
                })
                .thenExecuteAfter(30, () -> {
                    FlamethrowerBlockEntity be = be(h, level, ft);
                    h.assertTrue(be.status() == FlamethrowerBlockEntity.Status.TOO_SLOW, "status at 16 RPM: " + be.status());
                    h.assertTrue(level.getBlockState(log).is(Blocks.OAK_LOG), "log burned at 16 RPM");
                    // redstone switches it off
                    if (level.getBlockEntity(o) instanceof CreativeMotorBlockEntity motor) {
                        motor.generatedSpeed.setValue(64);
                    }
                    level.setBlock(ft.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                })
                .thenExecuteAfter(30, () -> {
                    FlamethrowerBlockEntity be = be(h, level, ft);
                    h.assertTrue(be.status() == FlamethrowerBlockEntity.Status.REDSTONE, "status with redstone: " + be.status());
                    level.setBlock(ft.above(), Blocks.AIR.defaultBlockState(), 3);
                })
                .thenExecuteAfter(60, () -> {
                    h.assertFalse(level.getBlockState(log).is(Blocks.OAK_LOG), "log not burned after switching on again");
                })
                .thenSucceed();
    }

    @GameTest(template = "flame_arena", timeoutTicks = 400)
    public static void fanClearsUndergrowth(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 2, 20));
        floor(level, o.offset(0, -2, -8), 14, 17);
        BlockPos ft = rig(level, o, FlamethrowerType.FAN, 64);
        List<BlockPos> bush = new ArrayList<>();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = 2; x <= 6; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos p = o.offset(x, y, z);
                    level.setBlock(p, leaves, 2);
                    bush.add(p);
                }
            }
        }
        h.startSequence()
                .thenExecuteAfter(120, () -> {
                    long left = bush.stream().filter(p -> level.getBlockState(p).is(Blocks.OAK_LEAVES)).count();
                    Flamethrowers.LOG.info("[test] fan: {} of {} leaves left", left, bush.size());
                    h.assertTrue(left <= bush.size() / 5, "fan left too many leaves: " + left + " of " + bush.size());
                })
                .thenSucceed();
    }

    @GameTest(template = "flame_arena", timeoutTicks = 400)
    public static void tornadoBurnsWideAndSucksMobs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 3, 20));
        floor(level, o.offset(0, -3, -6), 20, 13);
        BlockPos ft = rig(level, o, FlamethrowerType.TORNADO, 128);
        // a wall of logs 6 blocks ahead, 5 wide: off-axis logs only burn because the vortex is wide
        List<BlockPos> wall = new ArrayList<>();
        for (int z = -2; z <= 2; z++) {
            for (int y = -2; y <= 2; y++) {
                BlockPos p = o.offset(7, y, z);
                level.setBlock(p, Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                wall.add(p);
            }
        }
        Pig pig = EntityType.PIG.create(level);
        level.setBlock(o.offset(5, -2, 1), Blocks.STONE.defaultBlockState(), 2);
        pig.moveTo(o.getX() + 5.5, o.getY() - 1, o.getZ() + 1.0);
        level.addFreshEntity(pig);
        Vec3 pigStart = pig.position();
        h.startSequence()
                .thenExecuteAfter(12, () -> {
                    FlamethrowerBlockEntity tb = be(h, level, ft);
                    Flamethrowers.LOG.info("[test] tornado status {} speed {}, pig {} -> {} fire {} hp {}", tb.status(), tb.getSpeed(), pigStart, pig.position(), pig.getRemainingFireTicks(), pig.getHealth());
                    h.assertTrue(pig.isOnFire() || pig.isDeadOrDying() || pig.isRemoved(), "pig in the vortex is not burning");
                    Flamethrowers.LOG.info("[test] pig moved {} -> {}", pigStart, pig.position());
                })
                .thenExecuteAfter(140, () -> {
                    long left = wall.stream().filter(p -> level.getBlockState(p).is(Blocks.SPRUCE_PLANKS)).count();
                    long corners = wall.stream().filter(p -> Math.abs(p.getZ() - o.getZ()) == 2)
                            .filter(p -> !level.getBlockState(p).is(Blocks.SPRUCE_PLANKS)).count();
                    Flamethrowers.LOG.info("[test] tornado: {} of {} planks left, {} outer burned", left, wall.size(), corners);
                    h.assertTrue(left <= wall.size() / 3, "tornado left too much: " + left + " of " + wall.size());
                    h.assertTrue(corners >= 3, "tornado did not burn the outer ring: " + corners);
                })
                .thenSucceed();
    }

    @GameTest(template = "flame_arena", timeoutTicks = 400)
    public static void napalmLobsAndBurns(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 20));
        floor(level, o.offset(0, -1, -5), 38, 11);
        BlockPos ft = rig(level, o, FlamethrowerType.NAPALM, 64);
        List<BlockPos> tree = tree(level, o.offset(20, 0, 0), 5);
        h.startSequence()
                .thenExecuteAfter(25, () -> {
                    int blobs = level.getEntitiesOfClass(NapalmBlob.class, new AABB(o).inflate(40)).size();
                    FlamethrowerBlockEntity be = be(h, level, ft);
                    Flamethrowers.LOG.info("[test] napalm: {} blobs, lava {}", blobs, be.lava());
                    h.assertTrue(be.lava() <= FlamethrowerBlockEntity.CAPACITY - 100, "no napalm shot");
                })
                .thenExecuteAfter(200, () -> {
                    long left = remaining(level, tree);
                    Flamethrowers.LOG.info("[test] napalm: {} of {} tree blocks left", left, tree.size());
                    h.assertTrue(left < tree.size(), "napalm did not burn anything of the tree 20 blocks away");
                })
                .thenSucceed();
    }

    /**
     * A flamethrower on a Sable sub-level (what Create Aeronautics vehicles and Let it Walk! walkers are): the flame
     * must come out of the vehicle in the world, not in the plot grid.
     */
    @GameTest(template = "flame_arena", timeoutTicks = 500)
    public static void worksOnShip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(4, 2, 20));
        floor(level, o.offset(-4, -2, -6), 34, 13);
        // the vehicle: stone platform with motor + jet on top
        List<BlockPos> ship = new ArrayList<>();
        for (int x = -3; x <= 4; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos p = o.offset(x, -1, z);
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                ship.add(p);
            }
        }
        BlockPos ft = rig(level, o, FlamethrowerType.JET, 96);
        ship.add(o);
        ship.add(ft);
        // trees in the world in front of the vehicle
        List<BlockPos> tree = tree(level, o.offset(10, -1, 0), 6);
        // do not let the flame start before assembly: switch it off with redstone
        if (level.getBlockEntity(ft) instanceof FlamethrowerBlockEntity be) {
            be.tank.getPrimaryHandler().drain(FlamethrowerBlockEntity.CAPACITY, IFluidHandler.FluidAction.EXECUTE);
        }
        BoundingBox3i bounds = new BoundingBox3i(o.offset(-3, -1, -2), o.offset(4, 0, 2));
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, o, ship, bounds);
        h.assertTrue(sub != null, "ship was not assembled");
        long before = BurnManager.burnedTotal(level);
        BlockPos[] plot = new BlockPos[1];

        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    // find the flamethrower in the plot
                    var b = sub.getPlot().getBoundingBox();
                    for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                        if (level.getBlockEntity(p) instanceof FlamethrowerBlockEntity) {
                            plot[0] = p.immutable();
                        }
                    }
                    h.assertTrue(plot[0] != null, "flamethrower not moved into the plot");
                    SubLevel containing = Sable.HELPER.getContaining(level, plot[0]);
                    h.assertTrue(containing == sub, "flamethrower is not on the ship");
                    fill(level, plot[0]);
                })
                .thenExecuteAfter(20, () -> {
                    FlamethrowerBlockEntity be = be(h, level, plot[0]);
                    FlameSpace f = be.space();
                    Flamethrowers.LOG.info("[test] ship flamethrower plot {} -> world origin {} dir {} vel {}, speed {}, status {}",
                            plot[0], f.origin(), f.dir(), f.velocity(), be.getSpeed(), be.status());
                    h.assertTrue(f.subLevel() == sub, "FlameSpace did not find the ship");
                    h.assertTrue(f.origin().distanceTo(Vec3.atCenterOf(ft)) < 3, "world origin is not at the vehicle: " + f.origin());
                    h.assertTrue(be.getSpeed() != 0, "kinetics do not run on the ship");
                    h.assertTrue(be.isFiring(), "ship flamethrower not firing: " + be.status());
                })
                .thenExecuteAfter(160, () -> {
                    long left = remaining(level, tree);
                    Flamethrowers.LOG.info("[test] ship: {} of {} tree blocks left, {} burned", left, tree.size(), BurnManager.burnedTotal(level) - before);
                    h.assertTrue(left <= tree.size() / 10, "ship flamethrower did not burn the tree: " + left + " of " + tree.size());
                    // the ship itself (stone + machines in the plot) is untouched
                    h.assertTrue(level.getBlockEntity(plot[0]) instanceof FlamethrowerBlockEntity, "flamethrower destroyed");
                })
                .thenSucceed();
    }
}
