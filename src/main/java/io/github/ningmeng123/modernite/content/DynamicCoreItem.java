package io.github.ningmeng123.modernite.content;

import net.minecraft.world.item.Item;
/** Source portable night-vision core. @author NingMeng123 */
public final class DynamicCoreItem extends Item {
    public final int level;
    public DynamicCoreItem(int level,Properties properties) { super(properties.durability(3200*level).fireResistant()); this.level=level; }
}
