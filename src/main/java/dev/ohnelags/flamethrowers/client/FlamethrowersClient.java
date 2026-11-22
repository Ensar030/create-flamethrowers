package dev.ohnelags.flamethrowers.client;

import dev.ohnelags.flamethrowers.Flamethrowers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@Mod(value = Flamethrowers.MOD_ID, dist = Dist.CLIENT)
public final class FlamethrowersClient {
    public FlamethrowersClient(IEventBus modBus) {
        FlamePartials.init();
        modBus.addListener(this::renderers);
        modBus.addListener(this::particles);
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Flamethrowers.FLAMETHROWER_BE.get(), FlamethrowerRenderer::new);
        e.registerEntityRenderer(Flamethrowers.NAPALM_BLOB.get(), NoopRenderer::new);
    }

    private void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Flamethrowers.FLAME_STREAM.get(), s -> new FlameParticle.Provider(s, FlameParticle.Style.STREAM));
        e.registerSpriteSet(Flamethrowers.FLAME_PUFF.get(), s -> new FlameParticle.Provider(s, FlameParticle.Style.PUFF));
        e.registerSpriteSet(Flamethrowers.FLAME_SWIRL.get(), s -> new FlameParticle.Provider(s, FlameParticle.Style.SWIRL));
        e.registerSpriteSet(Flamethrowers.EMBER.get(), s -> new FlameParticle.Provider(s, FlameParticle.Style.EMBER));
    }
}
