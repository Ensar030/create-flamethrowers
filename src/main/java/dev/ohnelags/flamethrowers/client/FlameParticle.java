package dev.ohnelags.flamethrowers.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Fire puff made from tinted vanilla smoke sprites: starts small and white-hot, grows, turns orange, red and fades.
 */
public class FlameParticle extends TextureSheetParticle {
    public enum Style {
        //       friction, life, extra life, start size, end size, gravity
        STREAM(0.985f, 14, 7, 0.15f, 0.95f, -0.004f),
        PUFF(0.90f, 9, 5, 0.22f, 1.05f, -0.010f),
        SWIRL(0.80f, 7, 4, 0.35f, 0.70f, -0.010f),
        EMBER(0.96f, 20, 16, 0.05f, 0.03f, 0.020f);

        final float friction, startSize, endSize, gravity;
        final int life, extraLife;

        Style(float friction, int life, int extraLife, float startSize, float endSize, float gravity) {
            this.friction = friction;
            this.life = life;
            this.extraLife = extraLife;
            this.startSize = startSize;
            this.endSize = endSize;
            this.gravity = gravity;
        }
    }

    private final SpriteSet sprites;
    private final Style style;
    private final float sizeJitter;

    protected FlameParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Style style) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.style = style;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = style.friction;
        this.gravity = style.gravity;
        this.lifetime = style.life + random.nextInt(style.extraLife + 1);
        this.hasPhysics = true;
        this.sizeJitter = 0.8f + random.nextFloat() * 0.4f;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.quadSize = style.startSize * sizeJitter;
        setSpriteFromAge(sprites);
        updateColor(0);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        float t = (float) age / lifetime;
        quadSize = Mth.lerp(Mth.sqrt(t), style.startSize, style.endSize) * sizeJitter;
        oRoll = roll;
        roll += 0.08f * (1 - t);
        setSpriteFromAge(sprites);
        updateColor(t);
    }

    private void updateColor(float t) {
        if (style == Style.EMBER) {
            setColor(1f, 0.75f - 0.4f * t, 0.25f - 0.2f * t);
            alpha = 1 - t * t;
            return;
        }
        float r, g, b;
        if (t < 0.2f) {
            float k = t / 0.2f;
            r = 1f;
            g = Mth.lerp(k, 1.00f, 0.88f);
            b = Mth.lerp(k, 0.85f, 0.45f);
        } else if (t < 0.6f) {
            float k = (t - 0.2f) / 0.4f;
            r = 1f;
            g = Mth.lerp(k, 0.88f, 0.52f);
            b = Mth.lerp(k, 0.45f, 0.10f);
        } else {
            float k = (t - 0.6f) / 0.4f;
            r = Mth.lerp(k, 1f, 0.55f);
            g = Mth.lerp(k, 0.52f, 0.16f);
            b = Mth.lerp(k, 0.10f, 0.05f);
        }
        setColor(r, g, b);
        alpha = t < 0.65f ? 0.95f : 0.95f * (1 - (t - 0.65f) / 0.35f);
    }

    @Override
    protected int getLightColor(float partialTick) {
        float t = (age + partialTick) / lifetime;
        return t < 0.75f ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new FlameParticle(level, x, y, z, vx, vy, vz, sprites, style);
        }
    }
}
