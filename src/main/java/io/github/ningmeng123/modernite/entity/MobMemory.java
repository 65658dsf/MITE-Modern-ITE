package io.github.ningmeng123.modernite.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Counters survive unloads; summoned troops are never recreated by reading a save. @author NingMeng123 */
public final class MobMemory {
    public boolean initialized, entourage, mirrored, exchangerTried;
    public int clock, summons, cooldown, phaseCharges = 3, phaseMaximum = 3, stage, fleeingPotion;
    public int bats;
    public boolean cursed;
    public String mimicName = "";
    public ItemStack spare = ItemStack.EMPTY;
    public final Map<UUID, Float> contribution = new HashMap<>();
    public void write(CompoundTag outer, net.minecraft.core.HolderLookup.Provider registry) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Initialized", initialized); t.putBoolean("Entourage", entourage);
        t.putBoolean("Mirrored", mirrored); t.putBoolean("ExchangerTried", exchangerTried);
        t.putInt("Clock",clock); t.putInt("Summons",summons); t.putInt("Cooldown",cooldown);
        t.putInt("PhaseCharges",phaseCharges); t.putInt("PhaseMaximum",phaseMaximum); t.putInt("Stage",stage);
        t.putInt("Bats", bats); t.putBoolean("Cursed",cursed); t.putInt("FleeingPotion",fleeingPotion);
        t.putString("MimicName", mimicName);
        if (!spare.isEmpty()) t.put("Spare",spare.save(registry));
        CompoundTag damage = new CompoundTag(); contribution.forEach((id,value)->damage.putFloat(id.toString(),value));
        t.put("Contribution",damage); outer.put("ModernIteAbilities",t);
    }
    public void read(CompoundTag outer, net.minecraft.core.HolderLookup.Provider registry) {
        CompoundTag t=outer.getCompound("ModernIteAbilities");
        initialized=t.getBoolean("Initialized"); entourage=t.getBoolean("Entourage"); mirrored=t.getBoolean("Mirrored");
        exchangerTried=t.getBoolean("ExchangerTried"); clock=t.getInt("Clock"); summons=t.getInt("Summons");
        cooldown=t.getInt("Cooldown"); phaseMaximum=Math.clamp(t.getInt("PhaseMaximum"),2,4);
        phaseCharges=Math.clamp(t.getInt("PhaseCharges"),0,phaseMaximum); stage=t.getInt("Stage");
        bats=t.getInt("Bats"); cursed=t.getBoolean("Cursed"); fleeingPotion=t.getInt("FleeingPotion");
        mimicName=t.getString("MimicName"); spare=ItemStack.parseOptional(registry,t.getCompound("Spare"));
        contribution.clear(); CompoundTag d=t.getCompound("Contribution");
        for(String key:d.getAllKeys()) try {contribution.put(UUID.fromString(key),d.getFloat(key));}catch(IllegalArgumentException ignored){}
    }
}
