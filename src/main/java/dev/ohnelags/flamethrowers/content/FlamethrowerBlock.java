package dev.ohnelags.flamethrowers.content;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ohnelags.flamethrowers.FlamethrowerType;
import dev.ohnelags.flamethrowers.Flamethrowers;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * A flamethrower: nozzle at the front (FACING), shaft input at the back, lava from pipes on any other side.
 */
public class FlamethrowerBlock extends DirectionalKineticBlock implements IBE<FlamethrowerBlockEntity> {
    public final FlamethrowerType type;

    public FlamethrowerBlock(FlamethrowerType type, Properties props) {
        super(props);
        this.type = type;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction look = ctx.getNearestLookingDirection();
        // the nozzle points away from the player (sneak: towards the player)
        Direction facing = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown() ? look.getOpposite() : look;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public SpeedLevel getMinimumRequiredSpeedLevel() {
        return SpeedLevel.MEDIUM;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        FlamethrowerBlockEntity be = getBlockEntity(level, pos);
        if (be == null || be.tank == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, be.tank.getCapability())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public Class<FlamethrowerBlockEntity> getBlockEntityClass() {
        return FlamethrowerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FlamethrowerBlockEntity> getBlockEntityType() {
        return Flamethrowers.FLAMETHROWER_BE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("create_flamethrowers.tooltip." + type.id).withStyle(ChatFormatting.GRAY));
    }
}
