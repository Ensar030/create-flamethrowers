package dev.ohnelags.flamethrowers.showcase;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ohnelags.flamethrowers.FlamethrowerType;
import dev.ohnelags.flamethrowers.Flamethrowers;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlock;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Dev-only visual check (run "showcase"): loads a flat world, builds every flamethrower with trees in front of it,
 * lets each burn and saves screenshots to run/screenshots, then quits.
 */
@EventBusSubscriber(modid = Flamethrowers.MOD_ID, value = Dist.CLIENT)
public final class Showcase {
    private static final boolean ENABLED = Boolean.getBoolean("flamethrowers.showcase");
    private static int ticks = -1;
    private static BlockPos base;

    private Showcase() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        ticks++;
        IntegratedServer server = mc.getSingleplayerServer();
        if (ticks > 40) {
            mc.options.hideGui = true;
            if (mc.screen != null) {
                mc.setScreen(null);
            }
        }
        if (ticks == 40) {
            mc.options.hideGui = true;
            server.execute(() -> setup(server));
        }
        if (ticks == 60) {
            server.execute(() -> look(server, display(0).offset(-1, 2, 5), display(0).offset(2, 0, 0)));
        }
        if (ticks == 110) {
            shot(mc, "models");
        }
        if (ticks == 120) {
            server.execute(() -> look(server, display(0).offset(1, 1, -3), display(0).offset(1, 0, 0)));
        }
        if (ticks == 160) {
            shot(mc, "models_back");
        }
        FlamethrowerType[] types = FlamethrowerType.values();
        for (int i = 0; i < types.length; i++) {
            int t0 = 180 + i * 140;
            int lane = i;
            FlamethrowerType type = types[i];
            if (ticks == t0) {
                server.execute(() -> {
                    BlockPos ft = lane(lane).east();
                    look(server, lane(lane).offset(2, 3, 9), lane(lane).offset(type == FlamethrowerType.NAPALM ? 12 : 7, 1, 0));
                    if (server.overworld().getBlockEntity(ft) instanceof FlamethrowerBlockEntity be) {
                        be.tank.getPrimaryHandler().fill(new FluidStack(Fluids.LAVA, 4000), IFluidHandler.FluidAction.EXECUTE);
                    }
                });
            }
            if (ticks == t0 + 25) {
                shot(mc, type.id + "_1");
            }
            if (ticks == t0 + 60) {
                shot(mc, type.id + "_2");
            }
            if (ticks == t0 + 120) {
                shot(mc, type.id + "_3");
            }
        }
        if (ticks == 180 + types.length * 140 + 20) {
            Flamethrowers.LOG.info("[showcase] done");
            mc.stop();
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "showcase_" + name + ".png", mc.getMainRenderTarget(),
                msg -> Flamethrowers.LOG.info("[showcase] {}", msg.getString()));
    }

    private static BlockPos display(int i) {
        return base.offset(0, 0, -8 + i);
    }

    private static BlockPos lane(int i) {
        return base.offset(0, 0, 4 + i * 16);
    }

    private static void cmd(IntegratedServer server, String c) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
    }

    private static void look(IntegratedServer server, BlockPos from, BlockPos at) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 d = Vec3.atCenterOf(at).subtract(a);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.teleportTo(server.overworld(), a.x, a.y - 1.6, a.z, yaw, pitch);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
    }

    private static void setup(IntegratedServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        p.setGameMode(GameType.CREATIVE);
        cmd(server, "gamerule doDaylightCycle false");
        cmd(server, "gamerule doFireTick false");
        cmd(server, "time set 6000");
        cmd(server, "weather clear");
        BlockPos at = p.blockPosition();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
        base = new BlockPos(at.getX(), y, at.getZ());
        // grass ground
        for (int x = -6; x < 40; x++) {
            for (int z = -14; z < 74; z++) {
                level.setBlock(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int h = 0; h < 12; h++) {
                    level.setBlock(base.offset(x, h, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // display row of all four models (no shaft, no lava)
        FlamethrowerType[] types = FlamethrowerType.values();
        for (int i = 0; i < types.length; i++) {
            level.setBlock(display(0).offset(i, 0, 0), Flamethrowers.FLAMETHROWERS.get(types[i]).get().defaultBlockState()
                    .setValue(FlamethrowerBlock.FACING, Direction.UP), 3);
        }
        // one lane per type: motor + flamethrower facing east, a small forest in front
        for (int i = 0; i < types.length; i++) {
            BlockPos m = lane(i);
            level.setBlock(m, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST), 3);
            level.setBlock(m.east(), Flamethrowers.FLAMETHROWERS.get(types[i]).get().defaultBlockState()
                    .setValue(FlamethrowerBlock.FACING, Direction.EAST), 3);
            if (level.getBlockEntity(m) instanceof CreativeMotorBlockEntity motor) {
                motor.generatedSpeed.setValue(types[i] == FlamethrowerType.TORNADO ? 128 : 96);
            }
            int start = types[i] == FlamethrowerType.NAPALM ? 14 : 6;
            tree(level, m.offset(start, 0, 0), 6);
            tree(level, m.offset(start + 4, 0, 3), 7);
            tree(level, m.offset(start + 5, 0, -3), 5);
            tree(level, m.offset(start + 9, 0, 1), 6);
            for (int k = 2; k < start; k++) {
                level.setBlock(m.offset(k, 0, 1), Blocks.TALL_GRASS.defaultBlockState(), 2);
                level.setBlock(m.offset(k, 0, -1), Blocks.FERN.defaultBlockState(), 2);
            }
        }
    }

    private static void tree(ServerLevel level, BlockPos base, int height) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dy = height - 2; dy <= height + 1; dy++) {
            int r = dy >= height ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    level.setBlock(base.offset(dx, dy, dz), leaves, 2);
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            level.setBlock(base.above(dy), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
    }
}
