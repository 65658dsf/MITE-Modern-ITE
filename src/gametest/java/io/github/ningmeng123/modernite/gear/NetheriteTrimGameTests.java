package io.github.ningmeng123.modernite.gear;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.phantom.PhantomBladeItem;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.modernite.content.DynamicCoreItem;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native armor trimming, saved equipment and dropped-item hazards. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class NetheriteTrimGameTests {
    private static final List<Item> TEMPLATES = List.of(
            Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE);

    private NetheriteTrimGameTests() {}

    private static ItemStack stack(String id) {
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).filter(value -> value != Items.AIR).orElseThrow();
        return new ItemStack(item);
    }

    private static ItemEntity dropped(GameTestHelper h, ItemStack stack) {
        var center = h.absolutePos(new BlockPos(1, 2, 1)).getCenter();
        return new ItemEntity(h.getLevel(), center.x, center.y, center.z, stack.copy());
    }

    private static void assertDestroyed(GameTestHelper h, ItemStack stack, DamageSource source, String message) {
        ItemEntity entity = dropped(h, stack);
        try { h.assertTrue(entity.hurt(source, 10F) && entity.isRemoved(), message); }
        finally { entity.discard(); }
    }

    private static void assertProtected(GameTestHelper h, ItemStack stack, String context) {
        ItemEntity entity = dropped(h, stack);
        try {
            h.assertTrue(entity.fireImmune(), "The actual dropped armor reports fire immunity: " + context);
            h.assertTrue(!entity.hurt(h.getLevel().damageSources().inFire(), 10F) && !entity.isRemoved(),
                    "The actual dropped armor survives ordinary fire: " + context);
            h.assertTrue(!entity.hurt(h.getLevel().damageSources().lava(), 10F) && !entity.isRemoved(),
                    "The actual dropped armor survives lava: " + context);
        } finally { entity.discard(); }
    }

    private static ItemStack trimmed(GameTestHelper h, ItemStack source, ResourceKey<TrimMaterial> material) {
        ItemStack result = source.copy();
        result.set(DataComponents.TRIM, new ArmorTrim(
                h.getLevel().registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).getHolderOrThrow(material),
                h.getLevel().registryAccess().registryOrThrow(Registries.TRIM_PATTERN).getHolderOrThrow(TrimPatterns.SENTRY)));
        return result;
    }

    @GameTest(template = "gear_empty")
    public static void everyAdamantiumAndVibraniumEquipmentLosesFreshAndSavedDefaultFireProtection(GameTestHelper h) {
        Set<String> checked = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            ItemStack fresh = new ItemStack(item);
            boolean metal = id.contains("adamantium") || id.contains("vibranium");
            if (!metal || item instanceof DynamicCoreItem
                    || !(fresh.isDamageableItem() || item instanceof ArmorItem || item instanceof PhantomBladeItem)) continue;
            checked.add(id);
            h.assertTrue(!fresh.has(DataComponents.FIRE_RESISTANT), "Fresh high-tier equipment has no innate fire component: " + id);
            for (boolean savedFlag : new boolean[]{false, true}) {
                ItemStack source = fresh.copy();
                if (savedFlag) source.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
                ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), source.save(h.getLevel().registryAccess())).orElseThrow();
                h.assertTrue(restored.canBeHurtBy(h.getLevel().damageSources().inFire())
                        && restored.canBeHurtBy(h.getLevel().damageSources().lava()), "Fresh and old explicit components obey the new equipment hazards: " + id);
                ItemEntity drop = dropped(h, restored);
                try { h.assertTrue(!drop.fireImmune(), "Saved innate components cannot make equipment drops fire-immune: " + id); }
                finally { drop.discard(); }
                assertDestroyed(h, restored, h.getLevel().damageSources().inFire(), "Untrimmed equipment burns in fire: " + id);
                assertDestroyed(h, restored, h.getLevel().damageSources().lava(), "Untrimmed equipment burns in lava: " + id);
            }
        }
        h.assertTrue(checked.containsAll(Set.of("mite:adamantium_chestplate", "mite:vibranium_chestplate",
                "mite:adamantium_pickaxe", "mite:vibranium_pickaxe", "mite:adamantium_shield", "mite:vibranium_shield",
                "mite:vibranium_bow", "mite:adamantium_phantom_blade", "mite:vibranium_phantom_blade",
                "modern_ite:adamantium_knife", "modern_ite:club_vibranium")),
                "The registry matrix includes armor, tools, shields, bows, phantom blades and extension weapons");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void allNativeTemplatesAndMiteArmorUseImmediateTrimMenusAndPreserveEquipmentData(GameTestHelper h) {
        ItemStack decorated = stack("mite:vibranium_chestplate");
        EquipmentDurability.setQuality(decorated, Quality.SUPERB);
        decorated.setDamageValue(71);
        decorated.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved netherite trim equipment"));
        decorated.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 2);
        decorated.set(ExtremeGear.SOCKETS.get(), GemSockets.EMPTY.with(0, 6).with(7, 12));
        decorated.set(ExtremeGear.FORGE_GRADE.get(), 6);
        decorated.set(ExtremeGear.GRINDINGS.get(), 2);
        decorated.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 8, 713,
                Map.of(ResourceLocation.withDefaultNamespace("protection"), 2), 1));
        try (TrimSession session = new TrimSession(h)) {
            for (Item template : TEMPLATES) {
                ItemStack output = session.apply(template, decorated, Items.NETHERITE_INGOT);
                ItemStack expected = decorated.copy();
                expected.set(DataComponents.TRIM, output.get(DataComponents.TRIM));
                h.assertTrue(ItemStack.matches(output, expected), "Native trim keeps all quality, wear, name, enchantment, growth, socket and forge data");
                assertProtected(h, output, BuiltInRegistries.ITEM.getKey(template).toString());
            }
            for (var armor : MiteArmorContent.definitions()) {
                if (armor.horse()) continue;
                ItemStack output = session.apply(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
                        new ItemStack(MiteArmorContent.get(armor.id())), Items.NETHERITE_INGOT);
                assertProtected(h, output, armor.id());
            }
            for (Item item : List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
                    Items.IRON_BOOTS, Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE))
                assertProtected(h, session.apply(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, new ItemStack(item), Items.NETHERITE_INGOT),
                        BuiltInRegistries.ITEM.getKey(item).toString());
        }
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void netheriteTrimSurvivesEntityStorageButOtherDamageAndGoldReplacementRemainEffective(GameTestHelper h) {
        ItemStack armor;
        try (TrimSession session = new TrimSession(h)) {
            armor = session.apply(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, stack("mite:adamantium_chestplate"), Items.NETHERITE_INGOT);
            ItemEntity original = dropped(h, armor);
            ItemEntity restored = new ItemEntity(EntityType.ITEM, h.getLevel());
            try {
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                restored.load(saved);
                h.assertTrue(ItemStack.matches(restored.getItem(), armor) && restored.fireImmune(), "Native dropped-item storage preserves the exact trim and protection");
                h.assertTrue(!restored.hurt(h.getLevel().damageSources().inFire(), 10F)
                        && !restored.hurt(h.getLevel().damageSources().lava(), 10F) && !restored.isRemoved(),
                        "A reloaded trimmed item remains protected from fire and lava");
                h.assertTrue(restored.hurt(h.getLevel().damageSources().generic(), 10F) && restored.isRemoved(),
                        "The trim leaves ordinary non-fire damage able to destroy the item");
            } finally { original.discard(); restored.discard(); }
            ItemStack gold = session.apply(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, armor, Items.GOLD_INGOT);
            h.assertTrue(gold.get(DataComponents.TRIM).material().is(TrimMaterials.GOLD), "The same native menu replaces the netherite trim material with gold");
            assertDestroyed(h, gold, h.getLevel().damageSources().inFire(), "Gold replacement removes the added fire protection from adamantium armor");
            assertDestroyed(h, gold, h.getLevel().damageSources().lava(), "Gold replacement removes the added lava protection from adamantium armor");
        }
        assertDestroyed(h, trimmed(h, stack("mite:vibranium_sword"), TrimMaterials.NETHERITE), h.getLevel().damageSources().lava(),
                "A manually stored armor-trim component cannot protect a weapon drop");
        h.succeed();
    }

    @GameTest(template = "gear_empty")
    public static void materialResourcesAndNativeNetheriteKeepTheirExistingHazards(GameTestHelper h) {
        for (String id : new String[]{"mite:raw_adamantium", "mite:vibranium_ingot", "mite:vibranium_nugget", "mite:vibranium_block"}) {
            ItemStack material = stack(id);
            h.assertTrue(material.has(DataComponents.FIRE_RESISTANT), "Equipment changes preserve the existing material component: " + id);
            ItemEntity entity = dropped(h, material);
            try {
                h.assertTrue(!entity.hurt(h.getLevel().damageSources().inFire(), 10F)
                        && !entity.hurt(h.getLevel().damageSources().lava(), 10F) && !entity.isRemoved(),
                        "Existing high-tier raw materials, ingots and storage blocks keep their protection: " + id);
            } finally { entity.discard(); }
        }
        for (String id : new String[]{"mite:adamantium_ingot", "mite:adamantium_nugget"}) {
            ItemEntity entity = dropped(h, stack(id));
            try { h.assertTrue(!entity.hurt(h.getLevel().damageSources().inFire(), 10F)
                    && !entity.hurt(h.getLevel().damageSources().lava(), 10F) && !entity.isRemoved(),
                    "Source adamantium material susceptibility is retained: " + id); }
            finally { entity.discard(); }
        }
        h.assertTrue(new ItemStack(Items.NETHERITE_CHESTPLATE).has(DataComponents.FIRE_RESISTANT), "Native netherite retains its original default fire component");
        assertProtected(h, new ItemStack(Items.NETHERITE_CHESTPLATE), "native netherite armor");
        assertDestroyed(h, new ItemStack(Items.PAPER), h.getLevel().damageSources().inFire(), "Ordinary unrelated drops retain fire vulnerability");
        h.succeed();
    }

    private static final class TrimSession implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final SmithingMenu menu;
        private final BlockPos table;
        private final BlockState previous;

        private TrimSession(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "netherite-trim"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previous = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, Blocks.SMITHING_TABLE.defaultBlockState());
            player.moveTo(table.getCenter());
            player.setNoGravity(true);
            player.getInventory().clearContent();
            menu = new SmithingMenu(92, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
        }

        private ItemStack apply(Item template, ItemStack armor, Item material) {
            var input = new SmithingRecipeInput(new ItemStack(template), armor.copy(), new ItemStack(material));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, helper.getLevel()).orElseThrow();
            helper.assertTrue(recipe.value() instanceof SmithingTrimRecipe, "The actual recipe manager selects a native armor-trim recipe");
            ItemStack expected = recipe.value().assemble(input, helper.getLevel().registryAccess());
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(new ItemStack(template, 2));
            menu.getSlot(1).set(armor.copy());
            menu.getSlot(2).set(new ItemStack(material, 2));
            helper.assertTrue(ItemStack.matches(menu.getSlot(3).getItem(), expected) && expected.has(DataComponents.TRIM),
                    "The native smithing menu displays the exact registered trim result");
            menu.clicked(3, 0, ClickType.PICKUP, player);
            ItemStack output = menu.getCarried().copy();
            helper.assertTrue(ItemStack.matches(output, expected) && menu.getSlot(1).getItem().isEmpty(), "Taking the native trim output consumes its one source armor");
            helper.assertTrue(menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1,
                    "Native trimming consumes one template and one material through its ordinary pickup path");
            helper.assertTrue(!TimedSmithingController.isActive(player), "An armor-trim transaction completes immediately");
            helper.assertTrue(output.get(DataComponents.TRIM).material().is(material == Items.NETHERITE_INGOT ? TrimMaterials.NETHERITE : TrimMaterials.GOLD),
                    "The output stores the material chosen in the native menu");
            return output;
        }

        @Override public void close() {
            try {
                TimedSmithingController.cancel(player);
                for (int slot = 0; slot < 3; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
                menu.setCarried(ItemStack.EMPTY);
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, previous);
                channel.finishAndReleaseAll();
            }
        }
    }
}
