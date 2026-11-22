package dev.ohnelags.flamethrowers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.ohnelags.flamethrowers.FlamethrowerType;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlock;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the turning shaft stub at the back and the spinning head of the fire tornado. */
public class FlamethrowerRenderer extends SafeBlockEntityRenderer<FlamethrowerBlockEntity> {
    public FlamethrowerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    protected void renderSafe(FlamethrowerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(FlamethrowerBlock.FACING);
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, facing.getOpposite());
        KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, light).renderInto(ms, vb);

        if (be.type() == FlamethrowerType.TORNADO) {
            Direction.Axis axis = facing.getAxis();
            float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis) * 2;
            SuperByteBuffer head = CachedBuffers.partialFacing(FlamePartials.TORNADO_HEAD, state, facing);
            KineticBlockEntityRenderer.kineticRotationTransform(head, be, axis, angle, light).renderInto(ms, vb);
        }
    }
}
