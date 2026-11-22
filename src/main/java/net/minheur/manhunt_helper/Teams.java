package net.minheur.manhunt_helper;

import com.mojang.serialization.Codec;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;

public enum Teams implements StringIdentifiable {
    RUNNER("runner", Formatting.GREEN),
    HUNTER("hunter", Formatting.BLUE),
    SPECTATOR("spectator", Formatting.WHITE);

    private final String key;
    private final Formatting color;

    public static final Codec<Teams> CODEC =
            StringIdentifiable.createCodec(Teams::values);

    Teams(String key, Formatting color) {
        this.key = key;
        this.color = color;
    }

    @Override
    public String asString() {
        return key;
    }
    public Formatting getColor() {
        return color;
    }
}
