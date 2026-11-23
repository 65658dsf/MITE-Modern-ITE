package io.github.ningmeng123.modernite.gear;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Eight authoritative sockets; zero is empty, positive codes identify registered gems. @author NingMeng123 */
public record GemSockets(List<Integer> values) {
    private static final int MAX_CODE = GemType.values().length * 6;
    public static final GemSockets EMPTY = new GemSockets(Collections.nCopies(8, 0));
    public static final Codec<GemSockets> CODEC = Codec.intRange(0, MAX_CODE).listOf().validate(values ->
            values.size() == 8 ? com.mojang.serialization.DataResult.success(values)
                    : com.mojang.serialization.DataResult.error(() -> "Expected eight gem sockets"))
            .xmap(GemSockets::new, GemSockets::values);
    public GemSockets {
        values = List.copyOf(values);
        if (values.size() != 8 || values.stream().anyMatch(n -> n < 0 || n > MAX_CODE))
            throw new IllegalArgumentException("Invalid gem sockets");
    }
    public GemSockets with(int slot, int value) {
        var next = new ArrayList<>(values); next.set(slot, value); return new GemSockets(next);
    }
    public int level(GemType type) {
        return levelOutside(type, -1);
    }
    public int levelOutside(GemType type, int excludedSocket) {
        int highest = 0;
        for (int socket = 0; socket < values.size(); socket++) {
            int code = values.get(socket);
            if (socket != excludedSocket && code > 0 && (code - 1) / 6 == type.ordinal())
                highest = Math.max(highest, (code - 1) % 6 + 1);
        }
        return highest;
    }
}
