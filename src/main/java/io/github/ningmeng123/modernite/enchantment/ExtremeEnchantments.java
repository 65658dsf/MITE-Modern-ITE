package io.github.ningmeng123.modernite.enchantment;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.mite.progression.GearAffixRules;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.config.IteConfig;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Source ITE/Extreme enchantments using separate ordinary and growth effect sources. @author NingMeng123 */
@EventBusSubscriber(modid=ModernIte.MOD_ID)
public final class ExtremeEnchantments {
    public record Definition(String id,int maximum,int weight,int difficulty) {}
    public static final List<Definition> DEFINITIONS=List.of(
        new Definition("fixed",1,10,15),new Definition("chain",1,5,30),new Definition("slowdown",5,10,30),
        new Definition("plucking",1,25,10),new Definition("reaping",1,25,10),new Definition("defenced",1,10,20),
        new Definition("crit",5,10,10),new Definition("extend",2,10,15),new Definition("emergency",1,10,15),
        new Definition("conqueror",5,10,15),new Definition("beheading",5,10,10),new Definition("experience",3,7,10));
    private static final DeferredRegister.DataComponents COMPONENTS=DeferredRegister.createDataComponents(ModernIte.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<Integer>> EMERGENCY_COOLDOWN=
        COMPONENTS.registerComponentType("emergency_cooldown_v1",builder->builder.persistent(Codec.intRange(0,Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT));
    private static final Set<UUID> MINING=new HashSet<>();
    private ExtremeEnchantments() {}
    public static ResourceLocation key(String id) { return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,id); }
    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        int id=2000;
        for (Definition definition:DEFINITIONS) MiteExtensions.registerExternalEnchantment(new R196EnchantmentCatalog.Definition(
            id++,definition.id(),key(definition.id()).toString(),definition.weight(),definition.difficulty(),definition.maximum()));
    }
    public static Definition definition(String name) {
        if(name.indexOf(':')>=0) {
            ResourceLocation key=ResourceLocation.tryParse(name);
            if(key==null || !key.getNamespace().equals(ModernIte.MOD_ID)) return null;
            name=key.getPath();
        }
        for(Definition definition:DEFINITIONS) if(definition.id().equals(name)) return definition;
        return null;
    }
    public static EnchantmentSources.Levels levels(ItemStack stack,String id) {
        if(stack==null || stack.isEmpty()) return new EnchantmentSources.Levels(0,0);
        ResourceLocation key=key(id);int normal=0;
        for(var entry:stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).entrySet())
            if(entry.getKey().unwrapKey().map(k->k.location().equals(key)).orElse(false)) normal=Math.max(normal,entry.getIntValue());
        return new EnchantmentSources.Levels(normal,GearProgression.affixLevel(stack,key));
    }
    public static int level(ItemStack stack,String id) { return levels(stack,id).totalLevel(); }
    public static boolean supported(Holder<Enchantment> holder,boolean original) {
        return holder.unwrapKey().map(k->definition(k.location().toString())!=null).orElse(false)||original;
    }
    public static boolean applicable(String id,ItemStack stack,boolean original) {
        Definition definition=definition(id);
        if(definition==null) return original;
        if(stack.isEmpty()) return false;
        ToolFamily family=R196EnchantmentCatalog.family(stack);
        boolean armor=stack.getItem() instanceof ArmorItem && !(stack.getItem() instanceof AnimalArmorItem);
        boolean weapon=family!=null && switch(family) {case SWORD,BATTLE_AXE,WAR_HAMMER ->true; default->false;};
        return switch(definition.id()) {
            case "fixed" -> armor && ((ArmorItem)stack.getItem()).getType()==ArmorItem.Type.CHESTPLATE;
            case "chain" -> (family==ToolFamily.PICKAXE || family==ToolFamily.WAR_HAMMER) && EquipmentDurability.toolProfile(stack)!=null && EquipmentDurability.toolProfile(stack).materialDurability()>=8F;
            case "slowdown" -> stack.getItem() instanceof BowItem;
            case "plucking" -> family==ToolFamily.MATTOCK;
            case "reaping" -> family==ToolFamily.SCYTHE;
            case "defenced" -> family!=null && "vibranium".equals(GearProgression.material(stack));
            case "crit" -> family==ToolFamily.SWORD || family==ToolFamily.CUDGEL || family==ToolFamily.CLUB;
            case "extend" -> family!=null;
            case "emergency" -> armor;
            case "conqueror","experience" -> weapon;
            case "beheading" -> family==ToolFamily.AXE;
            default -> false;
        };
    }
    public static boolean compatible(String left,String right,boolean original) {
        return original && !(isChain(left)&&right.equals("minecraft:silk_touch")||isChain(right)&&left.equals("minecraft:silk_touch"));
    }
    private static boolean isChain(String id) { return id.equals("chain")||id.equals("modern_ite:chain"); }
    public static GearAffixRules.Definition affixDefinition(ItemStack stack,Holder<Enchantment> holder,GearAffixRules.Definition original) {
        var definition=holder.unwrapKey().map(k->definition(k.location().toString())).orElse(null);
        return definition==null ? original : new GearAffixRules.Definition(definition.weight(),definition.maximum());
    }
    public static void preserveStack(ItemStack source,ItemStack result) {
        Integer value=source.get(EMERGENCY_COOLDOWN.get());
        if(value==null) result.remove(EMERGENCY_COOLDOWN.get()); else result.set(EMERGENCY_COOLDOWN.get(),value);
    }
    public static boolean guard(ItemStack stack) {
        return level(stack,"defenced")>0 || !IteConfig.bool("player_enchant_defense") && applicable("defenced",stack,false);
    }
    public static int guardDuration() { return IteConfig.integer("player_defence_max_time"); }
    public static int guardCooldown() { return IteConfig.integer("player_defense_cooldown"); }
    public static boolean beginGuard(Player player,net.minecraft.world.InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!guard(stack)||player.getCooldowns().isOnCooldown(stack.getItem())) return false;
        player.startUsingItem(hand);return true;
    }
    @SubscribeEvent public static void attributes(ItemAttributeModifierEvent event) {
        int extend=level(event.getItemStack(),"extend");
        if(extend>0) {
            event.addModifier(Attributes.BLOCK_INTERACTION_RANGE,new AttributeModifier(key("extend_block"),extend*.25,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
            event.addModifier(Attributes.ENTITY_INTERACTION_RANGE,new AttributeModifier(key("extend_entity"),extend*.25,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
        }
    }
    @SubscribeEvent public static void critical(CriticalHitEvent event) {
        Player player=event.getEntity();int rank=level(player.getMainHandItem(),"crit");
        if(rank<=0||immuneConqueror(event.getTarget())) return;
        if(event.isCriticalHit()||player.getRandom().nextFloat()<Math.min(1D,rank*IteConfig.number("crit_enchantment_chance_boost_per_lvl")/100D)) {
            event.setCriticalHit(true);event.setDamageMultiplier(Math.max(1.5F,event.getDamageMultiplier()));
            player.getPersistentData().putLong("modern_ite:critical_tick",player.level().getGameTime());
            player.getPersistentData().putUUID("modern_ite:critical_target",event.getTarget().getUUID());
        }
    }
    private static boolean immuneConqueror(Entity target) { return BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).equals(key("zombie_boss")); }
    private static boolean steppedMob(Mob mob) {
        return !(mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)&&!(mob instanceof Slime)
                &&!(mob instanceof io.github.ningmeng123.mite.entity.R196Cube);
    }
    private static boolean steppedPlayer(Player player,LivingEntity target) {
        return target instanceof Mob&&!immuneConqueror(target)&&EquipmentDurability.toolProfile(player.getMainHandItem())!=null;
    }
    private static int priorMobHits(Player target,Mob attacker) {
        return target.getPersistentData().getCompound("modern_ite:stepped_mobs_v1").getInt(attacker.getUUID().toString());
    }
    private static void recordMobHit(Player target,Mob attacker) {
        var root=target.getPersistentData();var hits=root.getCompound("modern_ite:stepped_mobs_v1");String key=attacker.getUUID().toString();
        if(!hits.contains(key)&&hits.getAllKeys().size()>=256)hits.remove(hits.getAllKeys().stream().sorted().findFirst().orElseThrow());
        hits.putInt(key,Math.min(1000000,Math.max(0,hits.getInt(key))+1));root.put("modern_ite:stepped_mobs_v1",hits);
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void incoming(LivingIncomingDamageEvent event) {
        if(event.isCanceled()||event.getEntity().level().isClientSide) return;
        if(event.getEntity() instanceof Player defender&&event.getSource().getEntity() instanceof Mob mob&&steppedMob(mob)) {
            int hits=priorMobHits(defender,mob);
            double day=io.github.ningmeng123.modernite.ExtremeRules.currentDay((ServerLevel)defender.level());
            double step=Math.min(IteConfig.number("stepped_mob_damage_progress_max"),IteConfig.number("stepped_mob_damage_factor")
                    +day/Math.max(1,IteConfig.integer("stepped_mob_damage_progress_increase_day")));
            if(hits>0&&step>0)event.setAmount(event.getAmount()+(float)(hits*step));
        }
        if(event.getSource().getDirectEntity() instanceof Mob attacker&&event.getSource().getEntity()==attacker) {
            int rank=level(attacker.getMainHandItem(),"crit");
            if(rank>0&&attacker.getRandom().nextFloat()<Math.min(1D,rank*IteConfig.number("crit_enchantment_chance_boost_per_lvl")/100D))
                event.setAmount(event.getAmount()*1.5F+(float)(IteConfig.number("crit_enchantment_damage_boost_per_lvl")*rank));
        }
        if(event.getEntity() instanceof Player defender && defender.isUsingItem() && guard(defender.getUseItem())
                && !event.getSource().is(DamageTypeTags.BYPASSES_SHIELD) && event.getSource().getSourcePosition()!=null) {
            Vec3 direction=event.getSource().getSourcePosition().subtract(defender.position()).multiply(1,0,1).normalize();
            if(defender.getViewVector(1).multiply(1,0,1).dot(direction)>0) {
                event.setAmount(event.getAmount()*.5F);defender.playSound(SoundEvents.SHIELD_BLOCK,.7F,1.2F);
                EquipmentDurability.hurt(defender.getUseItem(),1,defender,defender.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);
            }
        }
        if(event.getSource().getDirectEntity() instanceof Player attacker && event.getSource().getEntity()==attacker && !immuneConqueror(event.getEntity())) {
            ItemStack weapon=attacker.getMainHandItem();int rank=level(weapon,"conqueror");
            int hits=event.getEntity().getPersistentData().getInt("modern_ite:conqueror_"+attacker.getUUID());
            double extra=rank*IteConfig.number("conqueror_damage_boost_per_lvl")*hits;
            if(steppedPlayer(attacker,event.getEntity()))extra+=Math.min(IteConfig.number("stepped_player_damage_progress"),
                    hits*(double)Math.max(0,attacker.experienceLevel)*IteConfig.number("stepped_player_damage_increase_per_lvl"));
            if(extra>0)event.setAmount(event.getAmount()+(float)extra);
            var memory=attacker.getPersistentData();
            if(memory.getLong("modern_ite:critical_tick")==attacker.level().getGameTime() && memory.hasUUID("modern_ite:critical_target")
                    && memory.getUUID("modern_ite:critical_target").equals(event.getEntity().getUUID())) {
                event.setAmount(event.getAmount()+(float)(level(weapon,"crit")*IteConfig.number("crit_enchantment_damage_boost_per_lvl")));memory.remove("modern_ite:critical_target");
            }
        }
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if(event.getEntity().level().isClientSide||event.getNewDamage()<=0) return;
        if(event.getEntity() instanceof ServerPlayer injured&&injured.isAlive()&&injured.getHealth()<=injured.getMaxHealth()*.3F)activateEmergency(injured);
        if(event.getSource().getDirectEntity() instanceof Player player && event.getSource().getEntity()==player&&!immuneConqueror(event.getEntity())
                &&(steppedPlayer(player,event.getEntity())||level(player.getMainHandItem(),"conqueror")>0)) {
            var data=event.getEntity().getPersistentData();String key="modern_ite:conqueror_"+player.getUUID();
            data.putInt(key,Math.min(1000000,data.getInt(key)+1));
        }
        if(event.getEntity() instanceof Player player&&event.getSource().getEntity() instanceof Mob mob&&steppedMob(mob)) {
            recordMobHit(player,mob);
        }
        if(event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
            ItemStack weapon=arrow.getWeaponItem();var ranks=levels(weapon,"slowdown");
            if(ranks.present() && arrow.getRandom().nextFloat()<EnchantmentSources.probability(ranks,rank->rank/5D)) {
                int duration=(int)EnchantmentSources.number(ranks,rank->160+48D*rank);
                int amplifier=(ranks.normal()>0?arrow.getRandom().nextInt(ranks.normal()):0)+(ranks.affix()>0?arrow.getRandom().nextInt(ranks.affix()):0);
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,duration,amplifier),arrow.getOwner());
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void emergency(LivingDeathEvent event) {
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player)) return;
        if(activateEmergency(player))event.setCanceled(true);
    }
    private static boolean activateEmergency(ServerPlayer player) {
        var ready=new ArrayList<ItemStack>();
        for(ItemStack stack:player.getArmorSlots()) if(level(stack,"emergency")>0&&stack.getOrDefault(EMERGENCY_COOLDOWN.get(),0)<=0) ready.add(stack);
        if(ready.isEmpty()) return false;
        player.setHealth(Math.min((float)IteConfig.number("emergency_health_recover_amount"),player.getMaxHealth()));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,60,1));
        int cooldown=(int)Math.round(IteConfig.integer("emergency_cooldown_")*Math.max(0D,1D-IteConfig.number("emergency_cooldown_reduce_every_armor")*(ready.size()-1)));
        for(ItemStack stack:ready) stack.set(EMERGENCY_COOLDOWN.get(),cooldown);
        player.playSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,2F,.75F);
        player.playSound(SoundEvents.ANVIL_LAND,.4F,.4F);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,player.getX(),player.getY()+1,player.getZ(),24,.5,.7,.5,.05);
        player.getInventory().setChanged();
        return true;
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        if(player.tickCount%2==0) for(ItemStack stack:player.getArmorSlots()) {
            int cooldown=stack.getOrDefault(EMERGENCY_COOLDOWN.get(),0);
            if(cooldown>0) stack.set(EMERGENCY_COOLDOWN.get(),Math.max(0,cooldown-2));
        }
        if(player.isUsingItem() && guard(player.getUseItem()) && player.getTicksUsingItem()>=guardDuration()-1) {
            player.getCooldowns().addCooldown(player.getUseItem().getItem(),guardCooldown());player.stopUsingItem();
        }
    }
    @SubscribeEvent public static void guardStopped(LivingEntityUseItemEvent.Stop event) {
        if(event.getEntity() instanceof Player player && guard(event.getItem())) player.getCooldowns().addCooldown(event.getItem().getItem(),guardCooldown());
    }
    @SubscribeEvent public static void guardFinished(LivingEntityUseItemEvent.Finish event) {
        if(event.getEntity() instanceof Player player && guard(event.getItem())) player.getCooldowns().addCooldown(event.getItem().getItem(),guardCooldown());
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if(event.isCanceled()||!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        int rank=level(player.getMainHandItem(),"beheading");
        if(rank<=0||player.getRandom().nextFloat()>=Math.min(1D,IteConfig.number("beheading_chance_boost_per_lvl")*rank)) return;
        LivingEntity target=event.getEntity();
        Item head=target instanceof WitherSkeleton?Items.WITHER_SKELETON_SKULL:target instanceof AbstractSkeleton?Items.SKELETON_SKULL:
            target instanceof Zombie?Items.ZOMBIE_HEAD:target instanceof Creeper?Items.CREEPER_HEAD:target instanceof Player?Items.PLAYER_HEAD:null;
        if(head!=null) event.getDrops().add(new ItemEntity(target.level(),target.getX(),target.getY()+.5,target.getZ(),new ItemStack(head)));
    }
    @SubscribeEvent public static void experience(LivingExperienceDropEvent event) {
        if(event.isCanceled()||event.getAttackingPlayer()==null) return;
        var ranks=levels(event.getAttackingPlayer().getMainHandItem(),"experience");
        if(ranks.present()) event.setDroppedExperience((int)Math.min(Integer.MAX_VALUE,event.getDroppedExperience()+EnchantmentSources.number(ranks,rank->1D+rank)));
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if(level(event.getItemStack(),"emergency")>0) event.getToolTip().add(Component.translatable("tooltip.modern_ite.emergency_cooldown",event.getItemStack().getOrDefault(EMERGENCY_COOLDOWN.get(),0)/20).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    /** Called only after a real successful block removal; extra removals re-enter native permissions, drops and wear. */
    public static void afterMined(ServerPlayer player,BlockPos pos,BlockState original) {
        if(player.isCreative()||player.isSpectator()||MINING.contains(player.getUUID())||!ore(original)) return;
        int left=level(player.getMainHandItem(),"chain")*27;if(left<=0) return;
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();seen.add(pos.immutable());queue.add(pos.immutable());
        MINING.add(player.getUUID());
        try {
            while(!queue.isEmpty() && left>0 && !player.getMainHandItem().isEmpty()) {
                BlockPos start=queue.removeFirst();
                for(int dy=1;dy>=-1;dy--) for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                    if(left<=0) break;
                    BlockPos next=start.offset(dx,dy,dz);
                    if(!seen.add(next)||!player.serverLevel().hasChunkAt(next)||!ore(player.serverLevel().getBlockState(next))) continue;
                    if(player.gameMode.destroyBlock(next)) { queue.add(next);left--; }
                }
            }
        } finally {MINING.remove(player.getUUID());}
    }
    private static boolean ore(BlockState state) {
        return state.getBlock() instanceof DropExperienceBlock || state.getBlock() instanceof io.github.ningmeng123.mite.block.R196OreBlock || state.is(BlockTags.COAL_ORES)||state.is(BlockTags.COPPER_ORES)||state.is(BlockTags.IRON_ORES)||state.is(BlockTags.GOLD_ORES)||state.is(BlockTags.REDSTONE_ORES)||state.is(BlockTags.LAPIS_ORES)||state.is(BlockTags.DIAMOND_ORES)||state.is(BlockTags.EMERALD_ORES);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void harvest(PlayerInteractEvent.RightClickBlock event) {
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player)||player.isSpectator()) return;
        ItemStack tool=event.getItemStack();ToolFamily family=R196EnchantmentCatalog.family(tool);
        boolean pluck=family==ToolFamily.MATTOCK&&level(tool,"plucking")>0, reap=family==ToolFamily.SCYTHE&&level(tool,"reaping")>0;
        if(!pluck&&!reap) return;
        ServerLevel world=player.serverLevel();BlockPos pos=event.getPos();BlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof CropBlock crop)||!crop.isMaxAge(state)||CubeCropBlight.dead(state)||CubeCropBlight.blighted(state)||reap&&!state.is(Blocks.WHEAT)) return;
        if(!world.mayInteract(player,pos)||!player.mayUseItemAt(pos,event.getFace(),tool)||player.getCooldowns().isOnCooldown(tool.getItem())) return;
        if(io.github.ningmeng123.modernite.economy.ExtremeEconomy.cropCheck(() -> NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,pos,state,player))).isCanceled()) return;
        List<ItemStack> drops=Block.getDrops(state,world,pos,world.getBlockEntity(pos),player,tool);
        if(!world.setBlock(pos,crop.getStateForAge(0),3)) return;
        io.github.ningmeng123.modernite.economy.ExtremeEconomy.harvestVoucher(world,pos,player);
        for(ItemStack drop:drops) Block.popResource(world,pos,drop);
        EquipmentDurability.mineBlock(tool,world,state,pos,player);
        if(!player.isCreative()) player.causeFoodExhaustion(Math.max(0,state.getDestroySpeed(world,pos)));
        player.getCooldowns().addCooldown(tool.getItem(),10);player.swing(event.getHand(),true);
        world.playSound(null,pos,SoundEvents.CROP_BREAK,net.minecraft.sounds.SoundSource.BLOCKS,1F,1F);
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
