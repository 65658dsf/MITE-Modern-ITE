package io.github.ningmeng123.modernite.entity;

import io.netty.buffer.Unpooled;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Combat state regressions for special attacks, equipment and weapon changes. @author NingMeng123 */
@GameTestHolder("modern_ite")
@PrefixGameTestTemplate(false)
public final class ExtremeCombatRegressionGameTests {
    private ExtremeCombatRegressionGameTests(){}
    private static Mob mob(GameTestHelper h,ExtremeKind kind){
        Mob mob=ExtremeMobs.type(kind).create(h.getLevel());
        h.assertTrue(mob!=null,"Registered mob creates: "+kind);
        mob.moveTo(h.absolutePos(new BlockPos(1,2,1)).getCenter());
        MobAbilities.initialize(mob,(ExtremeMob)mob);return mob;
    }
    private static String armorDiagnostics(Mob mob){
        var protection=mob.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        StringBuilder result=new StringBuilder(" revision=").append(mob.getPersistentData().getInt("modern_ite:combat_revision"));
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            var stack=mob.getItemBySlot(slot);
            result.append(" ").append(slot).append("=").append(stack.getItem()).append(" level=").append(stack.getEnchantmentLevel(protection))
                .append(" raw=").append(stack.get(DataComponents.ENCHANTMENTS));
        }
        return result.toString();
    }
    @GameTest(template="gear_empty")
    public static void mirrorSkeletonRetainsMeleeGoalAndCanReturnToBow(GameTestHelper h){
        Mob mirror=mob(h,ExtremeKind.MIRROR_SKELETON);ExtremeMob state=(ExtremeMob)mirror;
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.moveTo(mirror.position().add(1,0,0));
        player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));mirror.setTarget(player);
        state.extremeMemory().clock=19;MobAbilities.tick(mirror,state);
        var melee=SourceMeleeGoal.first((net.minecraft.world.entity.PathfinderMob)mirror);
        h.assertTrue(melee!=null&&melee.canUse(),"Copied sword selects the source melee AI and reaches its player");
        state.extremeMemory().clock=39;MobAbilities.tick(mirror,state);
        h.assertTrue(SourceMeleeGoal.first((net.minecraft.world.entity.PathfinderMob)mirror)==melee,"Repeated copying preserves the running melee goal");
        player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        state.extremeMemory().clock=59;MobAbilities.tick(mirror,state);
        h.assertTrue(SourceMeleeGoal.first((net.minecraft.world.entity.PathfinderMob)mirror)==null,"Returning to a bow removes melee scheduling");
        h.assertTrue(mirror.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof SourceRangedAttackGoal).count()==1,"Exactly one ranged goal remains");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void launchSkillsUseTheirExpectedImpulses(GameTestHelper h){
        for(var kind:new ExtremeKind[]{ExtremeKind.ZOMBIE_FLY_AWAY,ExtremeKind.DARK_IRON_GOLEM}){
            var launched=realMeleeLaunch(h,kind,true,new Vec3(1.8,-.0784,-1.2),0);
            if(kind==ExtremeKind.ZOMBIE_FLY_AWAY)h.assertTrue(launched.getDeltaMovement().y>1.69&&launched.getDeltaMovement().y<1.70,"The strong zombie uses the same fifteen-block launch as the skeleton");
            else h.assertTrue(launched.getDeltaMovement().y>3.9,"The dark golem retains its strong upward velocity after vanilla knockback");
            assertMostlyVertical(h,launched,kind);
        }
        var target=realMeleeLaunch(h,ExtremeKind.SKELETON_KNOCKBACK,false,Vec3.ZERO,1);
        h.assertTrue(target.getDeltaMovement().y>1.69&&target.getDeltaMovement().y<1.70,"Launch skeleton uses the same fifteen-block launch in the air");
        Mob floating=mob(h,ExtremeKind.SKELETON_FLOATING);MobAbilities.hit(floating,(ExtremeMob)floating,target);
        h.assertTrue(target.hasEffect(MobEffects.LEVITATION),"Floating skeleton's real hit handler applies levitation");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void knockbackZombiePushesAwayWithoutChangingVanillaVerticalMotion(GameTestHelper h){
        var directions=new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1)};
        for(int resistance=0;resistance<=1;resistance++){
            for(int scenario=0;scenario<3;scenario++){
                boolean grounded=scenario==0;
                var initial=new Vec3(.2,scenario==0?-.0784:scenario==1?.6:-.8,-.1);
                for(var away:directions){
                    Zombie reference=EntityType.ZOMBIE.create(h.getLevel());
                    h.assertTrue(reference!=null,"The vanilla zombie reference creates");
                    var normal=realMeleeMotion(h,reference,grounded,initial,resistance,away);
                    var pushed=realMeleeMotion(h,mob(h,ExtremeKind.ZOMBIE_KNOCKBACK),grounded,initial,resistance,away);
                    Vec3 extra=pushed.subtract(normal);
                    String context=" scenario="+scenario+" resistance="+resistance+" away="+away;
                    h.assertTrue(extra.dot(away)>3,"The real knockback zombie hit adds a strong horizontal push away from the attacker"+context);
                    h.assertTrue(Math.abs(extra.x*away.z-extra.z*away.x)<.000001,"The extra push follows the attack direction"+context);
                    h.assertTrue(Math.abs(pushed.y-normal.y)<.000001,"The real knockback zombie hit preserves vanilla grounded, jumping and falling vertical motion"+context);
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void zombieAndSkeletonLaunchHeightsMatchUnderRealPlayerTravel(GameTestHelper h){
        double[] initialVertical={-.0784,0,.6,-.8,3,-4};
        for(int resistance=0;resistance<=1;resistance++)for(int scenario=0;scenario<initialVertical.length;scenario++){
            boolean grounded=scenario==0;
            var initial=new Vec3(1.8,initialVertical[scenario],-1.2);
            var zombieTarget=realMeleeLaunch(h,ExtremeKind.ZOMBIE_FLY_AWAY,grounded,initial,resistance);
            var skeletonTarget=realMeleeLaunch(h,ExtremeKind.SKELETON_KNOCKBACK,grounded,initial,resistance);
            h.assertTrue(Math.abs(zombieTarget.getDeltaMovement().y-skeletonTarget.getDeltaMovement().y)<.000001,"Both real melee hits produce the same vertical velocity: scenario="+scenario+" resistance="+resistance);
            h.assertTrue(zombieTarget.getDeltaMovement().y>1.69&&zombieTarget.getDeltaMovement().y<1.70,"Every damaging hit gives a fixed positive launch regardless of prior motion and resistance");
            assertMostlyVertical(h,zombieTarget,ExtremeKind.ZOMBIE_FLY_AWAY);
            double zombieRise=travelToApex(h,zombieTarget),skeletonRise=travelToApex(h,skeletonTarget);
            h.assertTrue(Math.abs(zombieRise-skeletonRise)<.000001,"Actual Player.travel reaches the same height for the zombie and skeleton");
            h.assertTrue(Math.abs(zombieRise-15)<.01,"The real ascent reaches fifteen blocks for ground, air, falling and resistant targets");
        }
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void successiveDamagingHitsEachRestoreTheFifteenBlockLaunch(GameTestHelper h){
        for(var kind:new ExtremeKind[]{ExtremeKind.ZOMBIE_FLY_AWAY,ExtremeKind.SKELETON_KNOCKBACK}){
            Mob attacker=mob(h,kind);attacker.setNoAi(true);attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
            var target=launchTarget(h);
            for(int hit=0;hit<3;hit++){
                target.invulnerableTime=0;target.setOnGround(false);target.setDeltaMovement(.2,-4-hit,-.1);
                attacker.moveTo(target.position().add(-1,0,0));float health=target.getHealth();
                h.assertTrue(attacker.doHurtTarget(target)&&target.getHealth()<health,"Each successive attack really deals damage: "+kind);
                h.assertTrue(target.getDeltaMovement().y>1.69&&target.getDeltaMovement().y<1.70,"Every accepted attack restores the same fifteen-block launch");
                double start=target.getY();target.travel(Vec3.ZERO);
                h.assertTrue(target.getY()>start,"The victim actually rises after every accepted hit");
            }
        }
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void fifteenBlockLaunchOverridesAnExistingFastFall(GameTestHelper h){
        for(var kind:new ExtremeKind[]{ExtremeKind.ZOMBIE_FLY_AWAY,ExtremeKind.SKELETON_KNOCKBACK}){
            var target=realMeleeLaunch(h,kind,false,new Vec3(0,-4,0),0);
            h.assertTrue(Math.abs(travelToApex(h,target)-15)<.01,"The completed hit launches even a fast-falling target fifteen blocks upward: "+kind);
        }
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void absorbedLaunchHitStillLiftsThePlayer(GameTestHelper h){
        for(var kind:new ExtremeKind[]{ExtremeKind.ZOMBIE_FLY_AWAY,ExtremeKind.SKELETON_KNOCKBACK}){
            Mob attacker=mob(h,kind);attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
            var target=launchTarget(h);target.setOnGround(false);target.setDeltaMovement(0,.6,0);
            target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100);target.setAbsorptionAmount(100);
            attacker.moveTo(target.position().add(-1,0,0));
            float health=target.getHealth();h.assertTrue(attacker.doHurtTarget(target),"An absorbed attack is still a real successful hit");
            h.assertTrue(target.getHealth()==health&&target.getAbsorptionAmount()<100,"The attack is fully absorbed: "+kind);
            h.assertTrue(Math.abs(travelToApex(h,target)-15)<.01,"A hit that consumes absorption still launches the player fifteen blocks: "+kind);
        }
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void darkGolemLaunchApexStaysFixedAcrossGroundAirMotionAndKnockbackResistance(GameTestHelper h){
        for(var kind:new ExtremeKind[]{ExtremeKind.DARK_IRON_GOLEM}){
            double baseline=-1;
            for(int resistance=0;resistance<=1;resistance++){
                for(int scenario=0;scenario<3;scenario++){
                    boolean grounded=scenario==0;
                    var initial=scenario==0?new Vec3(2,-.0784,-1):scenario==1?new Vec3(-2,3,1):new Vec3(1,-4,2);
                    var target=realMeleeLaunch(h,kind,grounded,initial,resistance);
                    assertMostlyVertical(h,target,kind);
                    double rise=travelToApex(h,target);
                    if(baseline<0)baseline=rise;
                    h.assertTrue(Math.abs(rise-baseline)<.02,"Prior falling/jumping motion, ground state and ordinary knockback resistance cannot change the special launch height: "+kind+" scenario="+scenario+" resistance="+resistance);
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void rejectedMeleeDamageNeverAddsTheSpecialLaunch(GameTestHelper h){
        for(var kind:new ExtremeKind[]{ExtremeKind.ZOMBIE_FLY_AWAY,ExtremeKind.ZOMBIE_KNOCKBACK,ExtremeKind.SKELETON_KNOCKBACK,ExtremeKind.DARK_IRON_GOLEM}){
            Mob attacker=mob(h,kind);
            var target=launchTarget(h);
            attacker.moveTo(target.position().add(-1,0,0));
            target.setInvulnerable(true);
            var before=new Vec3(.12,-.4,.08);target.setDeltaMovement(before);
            float health=target.getHealth();
            h.assertFalse(attacker.doHurtTarget(target),"Invulnerable players reject the real melee attack: "+kind);
            h.assertTrue(target.getDeltaMovement().equals(before)&&target.getHealth()==health,"A rejected attack preserves the player's existing motion and health: "+kind);
        }
        h.succeed();
    }
    private static Player launchTarget(GameTestHelper h){
        var target=h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos anchor=h.absolutePos(BlockPos.ZERO);
        // Start above every test structure. The ascent can continue beyond build height
        // without hitting a template ceiling or entities belonging to another test.
        target.moveTo(anchor.getX()+1.5,Math.max(128D,anchor.getY()+64D),anchor.getZ()+1.5);
        target.getInventory().clearContent();target.removeAllEffects();target.setNoGravity(false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getAttribute(Attributes.GRAVITY).setBaseValue(.08);
        BlockPos start=target.blockPosition();
        for(int x=(start.getX()-4)>>4;x<=(start.getX()+4)>>4;x++)
            for(int z=(start.getZ()-4)>>4;z<=(start.getZ()+4)>>4;z++)h.getLevel().getChunk(x,z);
        h.assertTrue(h.getLevel().noCollision(target,target.getBoundingBox()),"The real launch target starts in open air");
        return target;
    }
    private static Player realMeleeLaunch(GameTestHelper h,ExtremeKind kind,boolean grounded,Vec3 initial,double resistance){
        Mob attacker=mob(h,kind);
        var target=launchTarget(h);
        attacker.moveTo(target.position().add(-1,0,0));attacker.setNoAi(true);
        attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
        target.setOnGround(grounded);target.setDeltaMovement(initial);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
        float health=target.getHealth();
        h.assertTrue(attacker.doHurtTarget(target)&&target.getHealth()<health,"The registered monster completes real damaging melee before launch checks: "+kind);
        return target;
    }
    private static Vec3 realMeleeMotion(GameTestHelper h,Mob attacker,boolean grounded,Vec3 initial,double resistance,Vec3 away){
        var target=launchTarget(h);
        attacker.moveTo(target.position().subtract(away));attacker.setNoAi(true);attacker.removeAllEffects();
        for(var slot:EquipmentSlot.values())attacker.setItemSlot(slot,ItemStack.EMPTY);
        attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
        var knockback=attacker.getAttribute(Attributes.ATTACK_KNOCKBACK);
        if(knockback!=null)knockback.setBaseValue(0);
        target.setOnGround(grounded);target.setDeltaMovement(initial);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
        float health=target.getHealth();
        h.assertTrue(attacker.doHurtTarget(target)&&target.getHealth()<health,"The comparison completes a real damaging zombie melee hit");
        return target.getDeltaMovement();
    }
    private static void assertMostlyVertical(GameTestHelper h,Player target,ExtremeKind kind){
        Vec3 velocity=target.getDeltaMovement();
        h.assertTrue(velocity.horizontalDistance()<=.250001&&velocity.x>0&&Math.abs(velocity.z)<.000001,"The launch points away from the attacker and caps total horizontal speed despite prior motion: "+kind+" "+velocity);
    }
    private static double travelToApex(GameTestHelper h,Player target){
        double start=target.getY(),highest=start;
        int ticks=0;
        while(target.getDeltaMovement().y>0&&ticks++<200){
            target.travel(Vec3.ZERO);
            highest=Math.max(highest,target.getY());
            h.assertFalse(target.verticalCollision,"No ceiling may truncate the real player ascent");
        }
        h.assertTrue(ticks>1&&ticks<200&&highest>start,"Player.travel actually advances the launched player to a finite apex");
        return highest-start;
    }
    @GameTest(template="gear_empty")
    public static void largeLaunchVelocitySurvivesTheClientProtocolWithoutVanillaClamping(GameTestHelper h){
        var velocity=new Vec3(9.6,5,-9.6);var vanilla=new ClientboundSetEntityMotionPacket(1,velocity);
        h.assertTrue(vanilla.getXa()==3.9&&vanilla.getYa()==3.9&&vanilla.getZa()==-3.9,"The ordinary motion packet clamps the requested special launch");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            LaunchVelocityPayload.STREAM_CODEC.encode(buffer,new LaunchVelocityPayload(velocity.x,velocity.y,velocity.z));
            var restored=LaunchVelocityPayload.STREAM_CODEC.decode(buffer);
            h.assertTrue(restored.x()==9.6&&restored.y()==5&&restored.z()==-9.6,"The special launch packet preserves all three full-strength velocity components");
        }finally{buffer.release();}
        h.succeed();
    }
    @GameTest(template="gear_empty")
    @SuppressWarnings("unchecked")
    public static void fifteenBlockLaunchCancelsAnOlderPendingHighVelocity(GameTestHelper h) throws ReflectiveOperationException{
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"test-launch"),net.minecraft.server.level.ClientInformation.createDefault());
        var field=LaunchVelocityPayload.class.getDeclaredField("PENDING");field.setAccessible(true);
        var pending=(java.util.Map<net.minecraft.server.level.ServerPlayer,Vec3>)field.get(null);
        try{
            pending.put(player,new Vec3(.2,6.2,0));
            player.setDeltaMovement(.2,1.6929821475097433,0);
            LaunchVelocityPayload.queue(player);
            h.assertFalse(pending.containsKey(player),"The fifteen-block launch cancels an older high-speed packet that would override its native synchronization");
        }finally{pending.remove(player);}
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void dragonBreathNeverExpandsBeyondOneAndHalfBlocks(GameTestHelper h){
        Mob dragon=mob(h,ExtremeKind.DRAGON_ZOMBIE);var target=h.makeMockPlayer(GameType.SURVIVAL);target.moveTo(dragon.position().add(3,0,0));
        MobAbilities.dragonBreath(dragon,target);
        var clouds=h.getLevel().getEntitiesOfClass(AreaEffectCloud.class,dragon.getBoundingBox().inflate(12),c->c.getOwner()==dragon);
        h.assertTrue(clouds.size()==1,"Dragon zombie produces one owned breath cloud");
        var cloud=clouds.getFirst();
        h.assertTrue(cloud.getRadius()==1.5F&&cloud.getRadiusPerTick()==0&&cloud.getRadiusOnUse()==0,"Neither time nor victims can increase the 1.5-block radius");
        cloud.discard();h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void randomZombieRollsArmorSlotsIndependently(GameTestHelper h){
        boolean partial=false,empty=false,full=false;
        for(int seed=0;seed<96;seed++){
            Mob zombie=ExtremeMobs.type(ExtremeKind.RANDOM_ZOMBIE).create(h.getLevel());zombie.getRandom().setSeed(seed*0x9E3779B97F4A7C15L);
            MobAbilities.initialize(zombie,(ExtremeMob)zombie);int worn=0;
            for(var stack:zombie.getArmorSlots())if(!stack.isEmpty())worn++;
            partial|=worn>0&&worn<4;empty|=worn==0;full|=worn==4;
        }
        h.assertTrue(partial&&empty&&full,"Independent rolls produce partial, empty and complete armor across fixed seeds");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void emperorAndDarkGolemKeepTheirRequestedEquipmentAndDamage(GameTestHelper h){
        Mob emperor=mob(h,ExtremeKind.UNDYING_ZOMBIE_EMPEROR);int pieces=0;
        var protection=emperor.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for(var stack:emperor.getArmorSlots()){
            h.assertTrue(!stack.isEmpty()&&stack.getEnchantmentLevel(protection)==5,"Each emperor armor piece has Protection V");pieces++;
        }
        h.assertTrue(pieces==4,"The emperor wears all four armor pieces");
        Mob bigHead=mob(h,ExtremeKind.FISHING_ROD_ZOMBIE);h.assertTrue(bigHead.getMainHandItem().isEmpty(),"Big-head zombie no longer carries a fishing rod");
        Mob golem=mob(h,ExtremeKind.DARK_IRON_GOLEM);
        h.assertTrue(Math.abs(golem.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)-(30+MobAbilities.day(h.getLevel())/20D)*2)<.001,"Dark iron golem base damage is doubled");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void existingSaveMigrationRemovesOldRodAndRestoresProtection(GameTestHelper h){
        Mob bigHead=mob(h,ExtremeKind.FISHING_ROD_ZOMBIE);bigHead.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));bigHead.getPersistentData().remove("modern_ite:combat_revision");
        MobAbilities.initialize(bigHead,(ExtremeMob)bigHead);h.assertTrue(bigHead.getMainHandItem().isEmpty(),"Already initialized legacy big-head zombie loses its old rod");
        Mob emperor=mob(h,ExtremeKind.UNDYING_ZOMBIE_EMPEROR);for(var stack:emperor.getArmorSlots())stack.remove(DataComponents.ENCHANTMENTS);
        emperor.getPersistentData().remove("modern_ite:combat_revision");MobAbilities.initialize(emperor,(ExtremeMob)emperor);
        var protection=emperor.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            var stack=emperor.getItemBySlot(slot);
            var definition=io.github.ningmeng123.mite.registry.MiteArmorContent.definition(stack.getItem());
            h.assertTrue(stack.getItem() instanceof ArmorItem armor&&armor.getEquipmentSlot()==slot
                &&definition!=null&&!definition.horse()&&"vibranium".equals(definition.material())&&definition.type().getSlot()==slot
                &&stack.getEnchantmentLevel(protection)==5,"Already initialized emperor wears the proper vibranium piece with ordinary Protection V in "+slot+";"+armorDiagnostics(emperor));
        }
        Mob lord=mob(h,ExtremeKind.ZOMBIE_LORD);lord.setNoAi(true);lord.setInvulnerable(true);lord.getPersistentData().remove("modern_ite:combat_revision");MobAbilities.initialize(lord,(ExtremeMob)lord);
        h.assertTrue(!lord.isNoAi()&&!lord.isInvulnerable(),"Legacy frozen or invulnerable lord state is repaired once");h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void ordinaryWitchCallsOneToThreeHostileWolvesOnceAfterPlayerInjury(GameTestHelper h){
        var before=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try(var site=new SpawnBalanceGameTests.Site(h)){
            site.date(65,65,true);var player=site.player(0);
            BlockPos center=player.blockPosition();
            for(BlockPos point:BlockPos.betweenClosed(center.offset(-18,-1,-18),center.offset(18,3,18))){
                before.put(point.immutable(),site.level.getBlockState(point));
                site.level.setBlock(point,point.getY()==center.getY()-1?net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
            }
            Mob navigationControl=site.mob(EntityType.WOLF);navigationControl.moveTo(center.getX()+10.5,center.getY()+.005,center.getZ()+.5,0,0);
            h.assertTrue(io.github.ningmeng123.mite.worldgen.R196SpawnQualification.qualifies(navigationControl,site.level,false,false),
                    "The wolf control passes the real source grass, volume and collision qualification");
            navigationControl.setOnGround(true);
            var path=navigationControl.getNavigation().createPath(player,0);
            h.assertTrue(path!=null&&path.getEndNode()!=null&&path.getEndNode().asBlockPos().distSqr(player.blockPosition())<=2,
                    "The supported wolf control can navigate to the actual player on the fixture platform");
            Mob witch=site.mob(EntityType.WITCH);witch.getRandom().setSeed(196);
            witch.moveTo(site.origin.add(20,0,0));witch.setTarget(player);
            h.assertTrue(site.level.addFreshEntity(witch),"The real witch joins on an isolated walkable platform");
            for(int i=0;i<200;i++)MobAbilities.witchSupportTick(witch);
            h.assertTrue(SpawnPopulation.supportCount(witch)==0,"A target without player injury cannot start a passive summon loop");
            witch.setLastHurtByMob(player);MobAbilities.witchSupportTick(witch);
            for(int i=0;i<59;i++)MobAbilities.witchSupportTick(witch);
            h.assertTrue(SpawnPopulation.supportCount(witch)==0,"No wolf appears before the sixty-tick retaliation countdown ends");
            MobAbilities.witchSupportTick(witch);
            var wolves=site.level.getEntitiesOfClass(Mob.class,witch.getBoundingBox().inflate(48),m->m.getPersistentData().hasUUID(SpawnPopulation.SUMMONER)
                    &&m.getPersistentData().getUUID(SpawnPopulation.SUMMONER).equals(witch.getUUID()));
            site.mobs.addAll(wolves);
            h.assertTrue(wolves.size()>=1&&wolves.size()<=3,"The successful source call creates one to three followers: "+wolves.size());
            h.assertTrue(wolves.stream().allMatch(m->m.getType()==EntityType.WOLF&&m.getTarget()==player
                    &&m.getPersistentData().getBoolean("mite_wolf_witch_ally_032")),"Every follower is an allied hostile wolf targeting the attacker");
            for(int i=0;i<400;i++)MobAbilities.witchSupportTick(witch);
            h.assertTrue(SpawnPopulation.supportCount(witch)==wolves.size(),"Continued targeting and the old injury cannot repeat a successful call");
        }finally{before.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));}
        h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void siegeRiderRemainsABabyAcrossSave(GameTestHelper h){
        Mob siege=mob(h,ExtremeKind.ZOMBIE_SIEGE);h.getLevel().addFreshEntity(siege);MobAbilities.tick(siege,(ExtremeMob)siege);
        h.assertTrue(siege.getFirstPassenger() instanceof Zombie&&((Zombie)siege.getFirstPassenger()).isBaby(),"Siege rider is always a baby zombie");
        var rider=(Zombie)siege.getFirstPassenger();var saved=new net.minecraft.nbt.CompoundTag();rider.saveWithoutId(saved);
        var restored=EntityType.ZOMBIE.create(h.getLevel());restored.load(saved);
        h.assertTrue(restored.isBaby(),"Baby rider state survives world reloads");
        rider.discard();siege.discard();h.succeed();
    }
    @GameTest(template="gear_empty")
    public static void zombieLordCanExchangeRealPlayerMeleeDamage(GameTestHelper h){
        Mob lord=mob(h,ExtremeKind.ZOMBIE_LORD);var player=h.makeMockPlayer(GameType.SURVIVAL);player.moveTo(lord.position().add(.8,0,0));lord.setTarget(player);
        float health=lord.getHealth();lord.hurt(h.getLevel().damageSources().playerAttack(player),6);
        h.assertTrue(lord.getHealth()<health,"The armored zombie lord accepts real player melee damage");
        float playerHealth=player.getHealth();var melee=SourceMeleeGoal.first((net.minecraft.world.entity.PathfinderMob)lord);
        h.assertTrue(melee!=null&&melee.canUse(),"Zombie lord has usable source melee AI");melee.tick();
        h.assertTrue(player.getHealth()<playerHealth,"Zombie lord's actual melee goal damages the player");
        h.succeed();
    }
}
