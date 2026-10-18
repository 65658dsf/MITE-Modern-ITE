package io.github.ningmeng123.modernite.entity;

import java.util.Collection;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** The real death drops are escrowed separately from the mimic's disposable display equipment. @author NingMeng123 */
final class PlayerMimicInventory {
    private static final String CONTENTS="modern_ite:player_death_inventory";
    private PlayerMimicInventory(){}

    static void capture(Mob mimic,Collection<ItemEntity> drops){
        ListTag contents=new ListTag();
        for(ItemEntity entity:drops){
            ItemStack stack=entity.getItem();if(stack.isEmpty())continue;
            contents.add(stack.copy().save(mimic.registryAccess()).copy());
            EquipmentSlot slot=mimic.getEquipmentSlotForItem(stack);
            if(slot!=EquipmentSlot.BODY&&mimic.getItemBySlot(slot).isEmpty())mimic.setItemSlot(slot,stack.copy());
        }
        mimic.getPersistentData().put(CONTENTS,contents);
        for(EquipmentSlot slot:EquipmentSlot.values())mimic.setDropChance(slot,0);
    }

    static void release(Mob mimic,Collection<ItemEntity> drops){
        ListTag contents=mimic.getPersistentData().getList(CONTENTS,Tag.TAG_COMPOUND);
        for(int i=0;i<contents.size();i++){
            ItemStack stack=ItemStack.parseOptional(mimic.registryAccess(),contents.getCompound(i));
            if(!stack.isEmpty())drops.add(new ItemEntity(mimic.level(),mimic.getX(),mimic.getY(),mimic.getZ(),stack));
        }
        // LivingDropsEvent is raised even with doMobLoot=false. Consume once to prevent duplicate callbacks.
        mimic.getPersistentData().remove(CONTENTS);
    }
}
