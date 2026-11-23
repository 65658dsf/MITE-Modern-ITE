package io.github.ningmeng123.modernite.config.client;

import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen.Category;
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen.CategoryEntry;
import io.github.ningmeng123.modernite.config.client.IteConfigValuesScreen.Group;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Adds optional ITE pages to their corresponding MITE settings categories. @author NingMeng123 */
final class IteConfigMenu {
    private IteConfigMenu() {}

    static void register() {
        MiteSettingsScreen.registerCategoryProvider(IteConfigMenu::entries);
    }

    private static List<CategoryEntry> entries(Category category) {
        return switch (category) {
            case WORLD -> List.of(group(Group.NETHER_ORES));
            case CREATURES -> List.of(new CategoryEntry(Component.translatable("modern_ite.config.monster_spawns"),
                    Component.translatable("modern_ite.config.monster_spawns.tooltip"), true,
                    parent -> IteConfigSearch.spawns(parent, "")), group(Group.CREATURE_BEHAVIOR));
            case COMBAT -> List.of(group(Group.COMBAT), group(Group.ENCHANTMENT_EFFECTS));
            case SURVIVAL -> List.of(group(Group.SURVIVAL), group(Group.CRAFTING));
            case SYSTEM -> List.of(group(Group.SYSTEM));
        };
    }

    private static CategoryEntry group(Group group) {
        return new CategoryEntry(group.title(), Component.translatable(group.translationKey() + ".tooltip"),
                true, parent -> new IteConfigValuesScreen(parent, group));
    }
}
