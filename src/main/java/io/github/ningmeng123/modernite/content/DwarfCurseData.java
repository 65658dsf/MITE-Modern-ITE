package io.github.ningmeng123.modernite.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Extra curse identity without changing the base mod's sixteen fixed enum IDs. @author NingMeng123 */
public final class DwarfCurseData extends SavedData {
    public static final String NAME = "modern_ite_dwarf_curses";
    private static final Factory<DwarfCurseData> FACTORY = new Factory<>(DwarfCurseData::new, DwarfCurseData::load);
    public record Entry(UUID player, UUID witch, long realizationTime, boolean realized) {
        Entry realize() { return new Entry(player, witch, realizationTime, true); }
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static DwarfCurseData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }
    public Entry entry(UUID player) { return entries.get(player); }
    public boolean add(Entry entry) {
        if (entries.putIfAbsent(entry.player(), entry) != null) return false;
        setDirty();
        return true;
    }
    void replace(Entry entry) { entries.put(entry.player(), entry); setDirty(); }
    public boolean remove(UUID player) {
        if (entries.remove(player) == null) return false;
        setDirty();
        return true;
    }
    public boolean removeForWitch(UUID witch) {
        boolean removed = entries.values().removeIf(entry -> entry.witch().equals(witch));
        if (removed) setDirty();
        return removed;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        var list = new ListTag();
        for (Entry entry : entries.values()) {
            var value = new CompoundTag();
            value.putUUID("player", entry.player());
            value.putUUID("witch", entry.witch());
            value.putLong("realization_time", entry.realizationTime());
            value.putBoolean("realized", entry.realized());
            list.add(value);
        }
        tag.put("curses", list);
        return tag;
    }
    public static DwarfCurseData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") > 1) throw new IllegalArgumentException("Unsupported dwarf curse save version");
        var data = new DwarfCurseData();
        for (Tag value : tag.getList("curses", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) value;
            if (!saved.hasUUID("player") || !saved.hasUUID("witch")) continue;
            var entry = new Entry(saved.getUUID("player"), saved.getUUID("witch"),
                    saved.getLong("realization_time"), saved.getBoolean("realized"));
            data.entries.put(entry.player(), entry);
        }
        return data;
    }
}
