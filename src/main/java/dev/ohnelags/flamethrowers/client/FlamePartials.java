package dev.ohnelags.flamethrowers.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ohnelags.flamethrowers.Flamethrowers;

public final class FlamePartials {
    public static final PartialModel TORNADO_HEAD = PartialModel.of(Flamethrowers.id("block/tornado_head"));

    private FlamePartials() {
    }

    /** Must run before the models are baked (client mod constructor). */
    public static void init() {
    }
}
