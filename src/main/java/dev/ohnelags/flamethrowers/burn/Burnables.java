package dev.ohnelags.flamethrowers.burn;

import dev.ohnelags.flamethrowers.FlameConfig;
import dev.ohnelags.flamethrowers.Flamethrowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** What the flame does to a block. */
public final class Burnables {
    /** Never burned (even if flammable). */
    public static final TagKey<Block> FLAME_PROOF = TagKey.create(Registries.BLOCK, Flamethrowers.id("flame_proof"));
    /** Always burnable, even if vanilla says it is not flammable (mushroom blocks, cobwebs ...). */
    public static final TagKey<Block> EXTRA_BURNABLE = TagKey.create(Registries.BLOCK, Flamethrowers.id("extra_burnable"));
    /** A burning block of this tag sets its burnable neighbours of this tag on fire (the whole tree burns down). */
    public static final TagKey<Block> TREE_PARTS = TagKey.create(Registries.BLOCK, Flamethrowers.id("tree_parts"));

    public static final float NOT_BURNABLE = -1;

    private Burnables() {
    }

    /** How much heat the block needs until it is gone, or {@link #NOT_BURNABLE}. */
    public static float threshold(Level level, BlockPos pos, BlockState s) {
        if (s.isAir() || s.getBlock() instanceof BaseFireBlock || s.is(FLAME_PROOF)) {
            return NOT_BURNABLE;
        }
        if (!s.getFluidState().isEmpty() && s.getFluidState().isSource()) {
            return NOT_BURNABLE;
        }
        if (s.hasBlockEntity() && !FlameConfig.bool(FlameConfig.BURN_BLOCK_ENTITIES)) {
            return NOT_BURNABLE;
        }
        if (s.getDestroySpeed(level, pos) < 0) {
            return NOT_BURNABLE; // bedrock-like
        }
        if (s.is(BlockTags.LEAVES)) {
            return 3f;
        }
        if (s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW)) {
            return 2f;
        }
        if (s.is(Blocks.SNOW_BLOCK)) {
            return 10f;
        }
        if (s.is(BlockTags.ICE) && !s.is(Blocks.BLUE_ICE)) {
            return s.is(Blocks.PACKED_ICE) ? 30f : 14f;
        }
        int flammability = s.getFlammability(level, pos, Direction.UP);
        boolean plant = s.canBeReplaced() && s.getFluidState().isEmpty();
        if (plant && (flammability > 0 || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS))) {
            return 1f;
        }
        if (flammability > 0 || s.is(EXTRA_BURNABLE) || s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS)) {
            float hardness = Math.max(0.2f, s.getDestroySpeed(level, pos));
            return 8f + hardness * 16f; // logs/planks (2.0): 40, wool (0.8): 21, hay (0.5): 16
        }
        if (s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS) || s.is(BlockTags.FLOWERS)) {
            return 1f;
        }
        return NOT_BURNABLE;
    }

    /** What is left after the block burned away. */
    public static BlockState residue(BlockState s) {
        if (s.is(BlockTags.ICE)) {
            return Blocks.WATER.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    public static boolean isTreePart(BlockState s) {
        return s.is(TREE_PARTS) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
    }

    /** Cost of crawling through this block in a tree fire. */
    public static int cascadeCost(BlockState s) {
        return s.is(BlockTags.LEAVES) ? 3 : 1;
    }

    /** Stops a flame: water, or a solid block that does not burn. */
    public static boolean blocksFlame(Level level, BlockPos pos, BlockState s) {
        if (s.getFluidState().is(FluidTags.WATER)) {
            return true;
        }
        return !s.getCollisionShape(level, pos).isEmpty();
    }
}
