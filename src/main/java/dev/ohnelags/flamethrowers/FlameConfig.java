package dev.ohnelags.flamethrowers;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config: create_flamethrowers-server.toml */
public final class FlameConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue BURN_BLOCKS;
    public static final ModConfigSpec.BooleanValue PLACE_FIRE;
    public static final ModConfigSpec.BooleanValue TREE_CASCADE;
    public static final ModConfigSpec.IntValue CASCADE_DEPTH;
    public static final ModConfigSpec.IntValue MAX_BURNS_PER_TICK;
    public static final ModConfigSpec.DoubleValue CHARCOAL_CHANCE;
    public static final ModConfigSpec.DoubleValue HEAT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RANGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LAVA_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ENTITY_DAMAGE;
    public static final ModConfigSpec.BooleanValue HURT_CREW;
    public static final ModConfigSpec.BooleanValue BURN_BLOCK_ENTITIES;
    public static final ModConfigSpec.DoubleValue[] STRESS = new ModConfigSpec.DoubleValue[FlamethrowerType.values().length];

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("burning");
        BURN_BLOCKS = b.comment("Flames burn away wood, leaves, plants, wool, snow ... (false = only fire and damage)")
                .define("burnBlocks", true);
        PLACE_FIRE = b.comment("Flames also light fires on the ground where they hit")
                .define("placeFire", true);
        TREE_CASCADE = b.comment("A burning log or leaf sets the connected logs/leaves of the same tree on fire, so whole trees collapse quickly")
                .define("treeCascade", true);
        CASCADE_DEPTH = b.comment("How far a tree fire crawls from the hit block (logs cost 1, leaves 3)")
                .defineInRange("cascadeDepth", 24, 0, 128);
        MAX_BURNS_PER_TICK = b.comment("Upper limit of blocks burned away per tick and dimension (protects the server)")
                .defineInRange("maxBurnsPerTick", 96, 1, 4096);
        CHARCOAL_CHANCE = b.comment("Chance that a burned log drops charcoal")
                .defineInRange("charcoalChance", 0.08, 0.0, 1.0);
        BURN_BLOCK_ENTITIES = b.comment("Also burn flammable blocks that hold a block entity (chests, lecterns ...)")
                .define("burnBlockEntities", false);
        HEAT_MULTIPLIER = b.comment("Multiplies how fast blocks burn")
                .defineInRange("heatMultiplier", 1.0, 0.05, 20.0);
        b.pop();

        b.push("flamethrower");
        RANGE_MULTIPLIER = b.comment("Multiplies the range of all flamethrowers")
                .defineInRange("rangeMultiplier", 1.0, 0.1, 4.0);
        LAVA_MULTIPLIER = b.comment("Multiplies the lava consumption")
                .defineInRange("lavaMultiplier", 1.0, 0.0, 20.0);
        ENTITY_DAMAGE = b.comment("Fire damage per hit to mobs/players in the flame (at 64 RPM)")
                .defineInRange("entityDamage", 3.0, 0.0, 100.0);
        HURT_CREW = b.comment("Flames also hurt entities standing on / riding the same ship or walker")
                .define("hurtCrew", false);
        for (FlamethrowerType t : FlamethrowerType.values()) {
            STRESS[t.ordinal()] = b.comment("Stress impact (SU per RPM) of the " + t.id + " flamethrower")
                    .defineInRange("stress_" + t.id, t.stress, 0.0, 1024.0);
        }
        b.pop();
        SPEC = b.build();
    }

    private FlameConfig() {
    }

    public static boolean loaded() {
        return SPEC.isLoaded();
    }

    public static boolean bool(ModConfigSpec.BooleanValue v) {
        return loaded() ? v.get() : v.getDefault();
    }

    public static int integer(ModConfigSpec.IntValue v) {
        return loaded() ? v.get() : v.getDefault();
    }

    public static double num(ModConfigSpec.DoubleValue v) {
        return loaded() ? v.get() : v.getDefault();
    }

    public static double stress(FlamethrowerType t) {
        return num(STRESS[t.ordinal()]);
    }
}
