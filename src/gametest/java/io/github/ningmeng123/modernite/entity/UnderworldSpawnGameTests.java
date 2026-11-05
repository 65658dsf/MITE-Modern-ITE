package io.github.ningmeng123.modernite.entity;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.modernite.config.IteConfig;
import io.github.ningmeng123.modernite.config.IteSpawnConfig;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real addon-injected candidates still pass native ground checks and dimension-only pacing. @author NingMeng123 */
@GameTestHolder("modern_ite_underworld_spawn")
@PrefixGameTestTemplate(false)
public final class UnderworldSpawnGameTests {
    private UnderworldSpawnGameTests() {}

    @GameTest(template="gear_empty")
    public static void sourceHellhoundInjectedIntoAnEmptyModernPoolCannotFloatButAcceptsStoneAndMycelium(GameTestHelper h) {
        try (Room room=new Room(h);var site=new SpawnBalanceGameTests.Site(h)) {
            site.date(224,224,true);
            var policy=MiteExtensions.policy();
            h.assertTrue(R196MobSpawning.positionY(room.level,room.pos,R196SpawnRules.Category.MONSTER)==null,
                    "The initial candidate column has no immediate ground and therefore selects the modern-only branch");
            var context=new MiteExtensions.SpawnContext(room.level,R196SpawnRules.Category.MONSTER,null);
            var injected=policy.spawnPool(context,room.pos,List.of()).stream()
                    .filter(entry->entry.type()==MiteMobs.type(MobKind.HELLHOUND)).toList();
            h.assertTrue(injected.size()==1&&!injected.getFirst().modern()
                    &&MobKind.HELLHOUND.sourceClass().equals(injected.getFirst().sourceClass()),
                    "The actual ITE policy injects a source hellhound even when the supplied modern-only pool is empty");
            boolean selected=false;
            // Fresh adjacent LegacyRandomSource seeds have correlated first nextDouble values
            // (0..511 all draw between .721 and .767). Sample independent seeds from one fixed stream.
            RandomSource seeds=RandomSource.create(0x4E696E674D656E67L);
            Set<String> selectedIdentities=new LinkedHashSet<>();
            for(int attempt=0;attempt<512&&!selected;attempt++) {
                var entry=R196MobSpawning.select(room.level,room.pos,R196SpawnRules.Category.MONSTER,
                        RandomSource.create(seeds.nextLong()),true,true);
                if(entry!=null)selectedIdentities.add(BuiltInRegistries.ENTITY_TYPE.getKey(entry.type()).toString());
                selected=entry!=null&&entry.type()==MiteMobs.type(MobKind.HELLHOUND)&&!entry.modern();
            }
            h.assertTrue(selected,"The real modern-only selection path can choose that addon-injected source hellhound; actual selections="+selectedIdentities);
            Mob hellhound=MiteMobs.type(MobKind.HELLHOUND).create(room.level);
            h.assertTrue(hellhound!=null,"The actual registered source hellhound creates in the real underworld");
            try {
                hellhound.moveTo(room.pos.getX()+.5,room.pos.getY()+.005,room.pos.getZ()+.5,0,0);
                for(int seed=0;seed<32;seed++) {
                    hellhound.getRandom().setSeed(seed);
                    h.assertFalse(qualifies(hellhound,room.level),
                            "A source candidate injected after the empty ground-pool switch still cannot naturally spawn over air");
                }
                for(var ground:List.of(Blocks.STONE,Blocks.MYCELIUM)) {
                    room.floor(ground.defaultBlockState());
                    Double supported=R196MobSpawning.positionY(room.level,room.pos,R196SpawnRules.Category.MONSTER);
                    h.assertTrue(supported!=null,"The same candidate column has a legal supported position on "+ground);
                    hellhound.moveTo(room.pos.getX()+.5,supported,room.pos.getZ()+.5,0,0);
                    h.assertTrue(qualifies(hellhound,room.level),
                            "The real ITE/source event pipeline accepts a grounded hellhound on "+ground);
                }
            } finally {hellhound.discard();SpawnPopulation.clearReservations(room.level);}
        }
        h.succeed();
    }

    @GameTest(template="gear_empty")
    public static void underworldFrequencyBoostRunsAfterTheIteGraceVetoWithoutChangingItsIntervalOrSpeciesWeights(GameTestHelper h) {
        try(var site=new SpawnBalanceGameTests.Site(h)) {
            ServerLevel underworld=site.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            h.assertTrue(underworld!=null,"The real underworld is loaded for addon scheduling verification");
            site.date(224,224,true);
            site.legacyDates.set("hostile_spawn_interval_ticks",7);
            site.legacyDates.set("deep_spawn_chance_percent",10D);
            site.legacyDates.set("surface_spawn_chance_percent",17D);
            site.legacyDates.set("other_dimension_spawn_chance_multiplier",.25D);
            var settingsBefore=IteConfig.effectiveSnapshot();
            var ratesBefore=IteSpawnConfig.effectiveSnapshot();var weightsBefore=IteSpawnConfig.effectiveWeights();
            var policy=MiteExtensions.policy();
            var settings=policy.hostileSpawningSettings(MiteConfig.monsterSpawningSettings());
            h.assertTrue(settings.value(Option.HOSTILE_INTERVAL_TICKS)==7D,
                    "The addon still publishes the exact existing integer interval rather than rounding interval/1.2");
            for(boolean deep:List.of(true,false)) {
                float raw=settings.probability(deep,(float)settings.value(Option.DIMENSION_CHANCE_MULTIPLIER),false);
                float accepted=policy.hostilePassProbability(underworld,deep,raw);
                close(h,accepted,deep?.025D:.0425D,"ITE retains its configured pre-boost probability");
                close(h,R196SpawnRules.dimensionHostileProbability(accepted,R196MobSpawning.underworld(underworld)),
                        deep?.03D:.051D,"Only the final underworld natural-pass probability gains twenty percent");
                for(var dimension:List.of(Level.OVERWORLD,Level.NETHER,Level.END)) {
                    var other=site.level.getServer().getLevel(dimension);
                    h.assertTrue(other!=null,"The ordinary control dimension is loaded: "+dimension.location());
                    close(h,R196SpawnRules.dimensionHostileProbability(policy.hostilePassProbability(other,deep,raw),
                            R196MobSpawning.underworld(other)),raw,"The same addon probability remains unchanged outside the underworld");
                }
            }
            site.date(1,1,false);
            for(boolean deep:List.of(true,false)) {
                float veto=policy.hostilePassProbability(underworld,deep,1F);
                h.assertTrue(veto==0F&&R196SpawnRules.dimensionHostileProbability(veto,true)==0F,
                        "The final dimension multiplier cannot revive a natural pass vetoed by the actual ITE grace policy");
            }
            h.assertTrue(IteConfig.effectiveSnapshot().equals(settingsBefore)
                    &&IteSpawnConfig.effectiveSnapshot().equals(ratesBefore)&&IteSpawnConfig.effectiveWeights().equals(weightsBefore),
                    "Applying the final multiplier never edits the addon interval, species rates or absolute weights");
        }
        h.succeed();
    }

    private static boolean qualifies(Mob mob,ServerLevel level) {
        // Light is independently covered by the host; isolate the actual natural ground/event admission here.
        return R196MobSpawning.qualifiesWithEvent(mob,level,MobSpawnType.NATURAL,false,false);
    }
    private static void close(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<.000001D,message+": "+actual+" vs "+expected);
    }

    private static final class Room implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos=new BlockPos(-9_176,180,9_608);
        final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
        Room(GameTestHelper h) {
            level=h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            h.assertTrue(level!=null,"The actual underworld is loaded; this regression must not silently skip it");
            for(BlockPos point:BlockPos.betweenClosed(pos.offset(-4,-4,-4),pos.offset(4,5,4))) {
                boolean shell=point.getY()==pos.getY()-4||point.getY()==pos.getY()+5
                        ||Math.abs(point.getX()-pos.getX())==4||Math.abs(point.getZ()-pos.getZ())==4;
                set(point,(shell?Blocks.STONE:Blocks.AIR).defaultBlockState());
            }
        }
        void floor(BlockState ground) {
            for(BlockPos point:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,-1,2)))set(point,ground);
        }
        void set(BlockPos point,BlockState state) {
            previous.putIfAbsent(point.immutable(),level.getBlockState(point));level.setBlock(point,state,2);
        }
        @Override public void close() {previous.forEach((point,state)->level.setBlock(point,state,2));}
    }
}
