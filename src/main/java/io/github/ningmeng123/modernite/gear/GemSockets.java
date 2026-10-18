package io.github.ningmeng123.modernite.gear;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Eight authoritative sockets; zero is empty, 1..60 identifies an actual gem. @author NingMeng123 */
public record GemSockets(List<Integer> values) {
    public static final GemSockets EMPTY = new GemSockets(Collections.nCopies(8, 0));
    public static final Codec<GemSockets> CODEC = Codec.intRange(0, 60).listOf().validate(values ->
            values.size() == 8 ? com.mojang.serialization.DataResult.success(values)
                    : com.mojang.serialization.DataResult.error(() -> "Expected eight gem sockets"))
            .xmap(GemSockets::new, GemSockets::values);
    public GemSockets {
        values = List.copyOf(values);
        if (values.size() != 8 || values.stream().anyMatch(n -> n < 0 || n > 60))
            throw new IllegalArgumentException("Invalid gem sockets");
    }
    public GemSockets with(int slot, int value) {
        var next = new ArrayList<>(values); next.set(slot, value); return new GemSockets(next);
    }
    public int level(GemType type) {
        int highest = 0;
        for (int code : values) if (code > 0 && (code - 1) / 6 == type.ordinal())
            highest = Math.max(highest, (code - 1) % 6 + 1);
        return highest;
    }
}
