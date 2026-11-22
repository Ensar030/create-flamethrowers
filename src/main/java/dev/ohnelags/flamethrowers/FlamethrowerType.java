package dev.ohnelags.flamethrowers;

/**
 * The four flamethrower heads. All values are for 64 RPM; the block entity scales them with the shaft speed.
 */
public enum FlamethrowerType {
    /** Long, narrow jet: pierces several blocks of wood, best range. */
    JET("jet", 16.0, 0.0, 3.2, 2.0, 8.0),
    /** Wide, short cone: clears leaves, undergrowth and thin trunks in a broad arc. */
    FAN("fan", 7.5, 32.0, 1.7, 3.0, 6.0),
    /** Spinning fire vortex: a widening cylinder that sucks in and lifts mobs and burns everything inside. */
    TORNADO("tornado", 11.0, 2.8, 3.0, 4.0, 12.0),
    /** Lobs sticky burning napalm blobs that keep burning where they land. */
    NAPALM("napalm", 28.0, 0.0, 0.0, 5.0, 10.0);

    public final String id;
    /** Reach in blocks at 64 RPM. */
    public final double range;
    /** FAN: half cone angle in degrees, TORNADO: radius at the end of the vortex. */
    public final double spread;
    /** Heat per tick a block in the flame receives at full strength. */
    public final float heat;
    /** Lava use in mB per tick at 64 RPM (napalm: averaged, it drains 100 mB per shot). */
    public final float lavaPerTick;
    /** Stress impact in SU per RPM. */
    public final double stress;

    FlamethrowerType(String id, double range, double spread, double heat, double lavaPerTick, double stress) {
        this.id = id;
        this.range = range;
        this.spread = spread;
        this.heat = (float) heat;
        this.lavaPerTick = (float) lavaPerTick;
        this.stress = stress;
    }

    public String blockName() {
        return id + "_flamethrower";
    }
}
