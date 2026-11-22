package dev.ohnelags.flamethrowers.burn;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import dev.ohnelags.flamethrowers.FlameConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Heat bookkeeping for the world. Flames add heat to the blocks they touch; once a block's heat reaches its
 * {@link Burnables#threshold threshold} it burns away. Heat cools off when the flame moves on. A burned log or leaf
 * passes the fire on to the connected parts of its tree with a short delay, so a whole tree collapses in a crawling
 * wave of fire instead of floating in the air.
 */
public final class BurnManager {
    public enum Result { IMMUNE, HEATING, BURNED }

    private static final Map<ResourceKey<Level>, LevelBurns> LEVELS = new HashMap<>();
    private static final int COOL_AFTER = 30;

    private BurnManager() {
    }

    private static final class Heat {
        float value;
        long touched;
    }

    private record Spread(long pos, long due, int depth) {
    }

    private static final class LevelBurns {
        final Long2ObjectOpenHashMap<Heat> heat = new Long2ObjectOpenHashMap<>();
        final PriorityQueue<Spread> spread = new PriorityQueue<>((a, b) -> Long.compare(a.due, b.due));
        int burnedThisTick;
        long burnedTotal;
    }

    private static LevelBurns of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new LevelBurns());
    }

    /** Blocks burned away in this dimension since the server started (for tests and the debug command). */
    public static long burnedTotal(ServerLevel level) {
        LevelBurns b = LEVELS.get(level.dimension());
        return b == null ? 0 : b.burnedTotal;
    }

    public static void clear() {
        LEVELS.clear();
    }

    /**
     * Heats a world block. The caller already scaled the amount (distance, penetration, shaft speed).
     */
    public static Result addHeat(ServerLevel level, BlockPos pos, float amount) {
        BlockState state = level.getBlockState(pos);
        float threshold = Burnables.threshold(level, pos, state);
        if (threshold < 0) {
            return Result.IMMUNE;
        }
        if (!FlameConfig.bool(FlameConfig.BURN_BLOCKS)) {
            return Result.HEATING;
        }
        LevelBurns b = of(level);
        long key = pos.asLong();
        Heat h = b.heat.get(key);
        if (h == null) {
            h = new Heat();
            b.heat.put(key, h);
        }
        h.value += amount * (float) FlameConfig.num(FlameConfig.HEAT_MULTIPLIER);
        h.touched = level.getGameTime();
        if (h.value < threshold) {
            if (level.random.nextFloat() < 0.04f) {
                level.sendParticles(ParticleTypes.SMOKE, pos.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble(),
                        pos.getZ() + level.random.nextDouble(), 1, 0, 0.02, 0, 0.01);
            }
            return Result.HEATING;
        }
        if (b.burnedThisTick >= FlameConfig.integer(FlameConfig.MAX_BURNS_PER_TICK)) {
            return Result.HEATING; // over budget: stays hot and burns in a later tick
        }
        b.heat.remove(key);
        burn(level, b, pos, state, 0);
        return Result.BURNED;
    }

    private static void burn(ServerLevel level, LevelBurns b, BlockPos pos, BlockState state, int depth) {
        b.burnedThisTick++;
        b.burnedTotal++;
        RandomSource r = level.random;
        boolean log = state.is(BlockTags.LOGS);
        level.setBlock(pos, Burnables.residue(state), Block.UPDATE_ALL);
        if (log && r.nextDouble() < FlameConfig.num(FlameConfig.CHARCOAL_CHANCE)) {
            Block.popResource(level, pos, new ItemStack(Items.CHARCOAL));
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, log ? 3 : 1, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 2, 0.3, 0.3, 0.3, 0.02);
        if (r.nextInt(4) == 0) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 0.6f + r.nextFloat() * 0.6f);
        }
        if (FlameConfig.bool(FlameConfig.PLACE_FIRE) && r.nextInt(5) == 0) {
            tryIgnite(level, pos);
        }
        if (FlameConfig.bool(FlameConfig.TREE_CASCADE) && Burnables.isTreePart(state)) {
            int next = depth + Burnables.cascadeCost(state);
            if (next <= FlameConfig.integer(FlameConfig.CASCADE_DEPTH)) {
                long now = level.getGameTime();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPos n = pos.offset(dx, dy, dz);
                            if (Burnables.isTreePart(level.getBlockState(n))) {
                                // upwards fire is faster, like a real tree fire
                                long delay = 2 + r.nextInt(4) + (dy < 0 ? 3 : 0) + (dy > 0 ? 0 : 1);
                                b.spread.add(new Spread(n.asLong(), now + delay, next));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Lights a fire in or above the given position, if fire can stand there. */
    public static void tryIgnite(ServerLevel level, BlockPos pos) {
        if (!FlameConfig.bool(FlameConfig.PLACE_FIRE)) {
            return;
        }
        for (BlockPos p : new BlockPos[]{pos, pos.above()}) {
            if (level.getBlockState(p).isAir()) {
                BlockState fire = BaseFireBlock.getState(level, p);
                if (fire.canSurvive(level, p)) {
                    level.setBlock(p, fire, Block.UPDATE_ALL);
                    return;
                }
            }
        }
    }

    /** Lights fire on the face of a block the flame hit but could not burn. */
    public static void igniteSurface(ServerLevel level, BlockPos hit, Direction towardsFlame) {
        tryIgnite(level, hit.relative(towardsFlame));
    }

    /** Called at the end of every server level tick. */
    public static void tick(ServerLevel level) {
        LevelBurns b = LEVELS.get(level.dimension());
        if (b == null) {
            return;
        }
        long now = level.getGameTime();
        int budget = FlameConfig.integer(FlameConfig.MAX_BURNS_PER_TICK);
        // tree fires: burn the parts that are due (they share the budget with the direct flames of this tick)
        int handled = 0;
        while (!b.spread.isEmpty() && b.spread.peek().due <= now && handled < budget * 4) {
            Spread s = b.spread.poll();
            handled++;
            BlockPos pos = BlockPos.of(s.pos);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!Burnables.isTreePart(state) || Burnables.threshold(level, pos, state) < 0) {
                continue; // already gone
            }
            if (b.burnedThisTick >= budget) {
                b.spread.add(new Spread(s.pos, now + 1, s.depth));
                break;
            }
            b.heat.remove(s.pos);
            burn(level, b, pos, state, s.depth);
        }
        b.burnedThisTick = 0;
        // cool down blocks the flame left
        if (now % 10 == 0 && !b.heat.isEmpty()) {
            ObjectIterator<Long2ObjectMap.Entry<Heat>> it = b.heat.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                Heat h = it.next().getValue();
                if (now - h.touched > COOL_AFTER) {
                    h.value *= 0.5f;
                    if (h.value < 0.5f) {
                        it.remove();
                    }
                }
            }
        }
        if (b.spread.size() > 200_000) {
            b.spread.clear(); // runaway forest fire safety net
        }
    }
}
