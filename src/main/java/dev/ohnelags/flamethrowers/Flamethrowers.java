package dev.ohnelags.flamethrowers;

import java.util.EnumMap;
import java.util.Map;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.ohnelags.flamethrowers.burn.BurnManager;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlock;
import dev.ohnelags.flamethrowers.content.FlamethrowerBlockEntity;
import dev.ohnelags.flamethrowers.content.NapalmBlob;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Flamethrowers.MOD_ID)
public final class Flamethrowers {
    public static final String MOD_ID = "create_flamethrowers";
    public static final Logger LOG = LoggerFactory.getLogger("Flamethrowers");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Map<FlamethrowerType, DeferredBlock<FlamethrowerBlock>> FLAMETHROWERS = new EnumMap<>(FlamethrowerType.class);

    static {
        for (FlamethrowerType t : FlamethrowerType.values()) {
            DeferredBlock<FlamethrowerBlock> block = BLOCKS.register(t.blockName(), () -> new FlamethrowerBlock(t,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0f, 6.0f)
                            .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()));
            ITEMS.registerSimpleBlockItem(block);
            FLAMETHROWERS.put(t, block);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlamethrowerBlockEntity>> FLAMETHROWER_BE =
            BLOCK_ENTITIES.register("flamethrower", () -> BlockEntityType.Builder.of((pos, state) -> new FlamethrowerBlockEntity(Flamethrowers.FLAMETHROWER_BE.get(), pos, state),
                    FLAMETHROWERS.values().stream().map(DeferredBlock::get).toArray(FlamethrowerBlock[]::new)).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<NapalmBlob>> NAPALM_BLOB = ENTITIES.register("napalm_blob",
            () -> EntityType.Builder.<NapalmBlob>of(NapalmBlob::new, MobCategory.MISC).sized(0.4f, 0.4f).fireImmune()
                    .clientTrackingRange(8).updateInterval(2).build("napalm_blob"));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME_STREAM = PARTICLES.register("flame_stream", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME_PUFF = PARTICLES.register("flame_puff", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME_SWIRL = PARTICLES.register("flame_swirl", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = PARTICLES.register("ember", () -> new SimpleParticleType(false));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_flamethrowers"))
            .icon(() -> new ItemStack(FLAMETHROWERS.get(FlamethrowerType.JET).get()))
            .displayItems((params, out) -> FLAMETHROWERS.values().forEach(b -> out.accept(b.get())))
            .build());

    public Flamethrowers(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        PARTICLES.register(modBus);
        TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, FlameConfig.SPEC);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::levelTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> BurnManager.clear());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            for (Map.Entry<FlamethrowerType, DeferredBlock<FlamethrowerBlock>> en : FLAMETHROWERS.entrySet()) {
                FlamethrowerType t = en.getKey();
                BlockStressValues.IMPACTS.register(en.getValue().get(), () -> FlameConfig.stress(t));
            }
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLAMETHROWER_BE.get(), (be, side) -> {
            if (be.tank == null) {
                return null;
            }
            Direction front = be.facing();
            return side == front ? null : be.tank.getCapability();
        });
    }

    private void levelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel sl) {
            BurnManager.tick(sl);
        }
    }
}
