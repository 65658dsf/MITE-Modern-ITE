"""Package source artwork and modern data definitions. Author: NingMeng123."""
from pathlib import Path
from zipfile import ZipFile
import json, hashlib

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
REF = ROOT.parent / 'miteto1.21.1neoforge/.reference'
EX = ZipFile(REF / '[极难柳昊定制]Extreme-lh-0.6.6 (3).jar')
ITE = ZipFile(REF / '[MITE不可能这么简单]MITE-ITE-2.0.28.jar')
zh, en, provenance = {}, {}, []
def save(path, value):
    p = RES / path; p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def tex(jar, source, target):
    raw=jar.read(source); p=RES/f'assets/modern_ite/textures/{target}.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
    # Animation strips need their frame metadata when imported into the modern atlas.
    if source+'.mcmeta' in jar.namelist():
        save(f'assets/modern_ite/textures/{target}.png.mcmeta',json.loads(jar.read(source+'.mcmeta')))
    provenance.append({'jar':Path(jar.filename).name,'source':source,'target':str(p.relative_to(RES)).replace('\\','/'),'sha256':hashlib.sha256(raw).hexdigest()})
def named(id, cn, english, block=False):
    k=('block' if block else 'item')+'.modern_ite.'+id;zh[k]=cn;en[k]=english
def model(id, texture=None, parent='minecraft:item/generated'):
    save(f'assets/modern_ite/models/item/{id}.json',{'parent':parent,'textures':{'layer0':'modern_ite:item/'+(texture or id)}})

def lantern_models(metals, names):
    # Original held-lantern geometry by NingMeng123; reuse native light and metal textures.
    def box(start, end, texture='#metal', uv=None, **extra):
        faces={direction:{'texture':texture,**({'uv':uv} if uv else {})} for direction in ['down','up','north','south','west','east']}
        return {'from':start,'to':end,'faces':faces,**extra}
    elements=[
        box([4.5,1,4.5],[11.5,2,11.5]),
        box([5.25,2,5.25],[10.75,8,10.75],'#light',[1,3,5,8],shade=False),
        box([4.5,8,4.5],[11.5,9,11.5]),
        box([5.5,9,5.5],[10.5,10,10.5]),
        box([6.5,10,6.5],[9.5,11,9.5]),
    ]
    for x in [4.75,10.5]:
        for z in [4.75,10.5]:elements.append(box([x,2,z],[x+.75,8,z+.75]))
    # A solid open handle stays legible from either hand and in inventory.
    elements.extend([
        box([5.75,10.5,7.5],[6.75,13.5,8.5]),
        box([9.25,10.5,7.5],[10.25,13.5,8.5]),
        box([6.75,13,7.5],[9.25,14,8.5]),
    ])
    save('assets/modern_ite/models/item/template_material_lantern.json',{
        'credit':'NingMeng123','parent':'minecraft:block/block','ambientocclusion':False,
        'textures':{'particle':'#metal','light':'minecraft:block/lantern'},
        'elements':elements,
        'display':{
            'gui':{'rotation':[25,225,0],'translation':[0,.5,0],'scale':[1,1,1]},
            'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},
            'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.75,.75,.75]},
            'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,-3,0],'scale':[.6,.6,.6]},
            'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,-3,0],'scale':[.6,.6,.6]},
            'firstperson_righthand':{'rotation':[0,-45,0],'translation':[0,-2,-1],'scale':[.7,.7,.7]},
            'firstperson_lefthand':{'rotation':[0,45,0],'translation':[0,-2,-1],'scale':[.7,.7,.7]},
        },
    })
    for metal,cn in zip(metals,names):
        id='dynamic_core_'+metal
        named(id,cn+'灯笼',metal.replace('_',' ').title()+' Lantern')
        namespace='minecraft' if metal in ['copper','gold','iron'] else 'mite'
        save(f'assets/modern_ite/models/item/{id}.json',{
            'parent':'modern_ite:item/template_material_lantern',
            'textures':{'metal':namespace+':block/'+metal+'_block'},
        })
def block_item(id):save(f'assets/modern_ite/models/item/{id}.json',{'parent':'modern_ite:block/'+id})
def block_model(id, parent, textures, **extra):save(f'assets/modern_ite/models/block/{id}.json',{'parent':parent,'textures':textures,**extra})
def state(id, value):save(f'assets/modern_ite/blockstates/{id}.json',value)
def loot(id, item=None, count=None, double_slab=False):
    functions=[]
    if count: functions.append({'function':'minecraft:set_count','count':count})
    if double_slab: functions.append({'function':'minecraft:set_count','count':2,'conditions':[{'condition':'minecraft:block_state_property','block':'modern_ite:'+id,'properties':{'type':'double'}}]})
    entry={'type':'minecraft:item','name':item or 'modern_ite:'+id}
    if functions:entry['functions']=functions
    save(f'data/modern_ite/loot_table/blocks/{id}.json',{'type':'minecraft:block','pools':[{'rolls':1,'conditions':[{'condition':'minecraft:survives_explosion'}],'entries':[entry]}]})
def cube(id, texture=None):
    block_model(id,'minecraft:block/cube_all',{'all':'modern_ite:block/'+(texture or id)});state(id,{'variants':{'':{'model':'modern_ite:block/'+id}}});block_item(id);loot(id)
def ingredient(id):return id if isinstance(id,(dict,list)) else {'item':id}
def recipe(id, value):save(f'data/modern_ite/recipe/{id}.json',value)
def shaped(id, pattern, keys, result=None, count=1):
    recipe(id,{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:ingredient(v) for k,v in keys.items()},'result':{'id':result or 'modern_ite:'+id,'count':count}})

def smithing(id, base, addition, result=None):
    recipe(id,{'type':'minecraft:smithing_transform','template':ingredient('mite:equipment_upgrade_smithing_template'),'base':ingredient(base),'addition':ingredient(addition),'result':{'id':result or 'modern_ite:'+id}})
def shapeless(id, items, result=None, count=1):
    recipe(id,{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[ingredient(i) for i in items],'result':{'id':result or 'modern_ite:'+id,'count':count}})
def smelt(id, source, result, xp=.1):recipe(id,{'type':'minecraft:smelting','category':'misc','ingredient':ingredient(source),'result':{'id':result},'experience':xp,'cookingtime':200})
def tag(namespace, kind, id, values):save(f'data/{namespace}/tags/{kind}/{id}.json',{'replace':False,'values':values})
def stairs(id, base):
    t='modern_ite:block/'+base
    for suffix,parent in [('', 'stairs'),('_inner','inner_stairs'),('_outer','outer_stairs')]:block_model(id+suffix,'minecraft:block/'+parent,{'bottom':t,'top':t,'side':t})
    variants={};facing={'east':0,'south':90,'west':180,'north':270}
    for face,y in facing.items():
      for half in ['bottom','top']:
       for shape in ['straight','inner_left','inner_right','outer_left','outer_right']:
        suffix='' if shape=='straight' else '_'+shape.split('_')[0]
        rot=y+(270 if shape.endswith('_left') else 0)
        if half=='top' and shape!='straight':rot+=90
        v={'model':'modern_ite:block/'+id+suffix,'uvlock':True}
        if rot%360:v['y']=rot%360
        if half=='top':v['x']=180
        variants[f'facing={face},half={half},shape={shape}']=v
    state(id,{'variants':variants});block_item(id);loot(id)
def slab(id, base):
    t='modern_ite:block/'+base
    for suffix,parent in [('', 'slab'),('_top','slab_top')]:block_model(id+suffix,'minecraft:block/'+parent,{'bottom':t,'top':t,'side':t})
    state(id,{'variants':{'type=bottom':{'model':'modern_ite:block/'+id},'type=top':{'model':'modern_ite:block/'+id+'_top'},'type=double':{'model':'modern_ite:block/'+base}}});block_item(id);loot(id,double_slab=True)
simple={
 'fancy_red':('红钻','Red Diamond'), 'diamond_chunk':('钻石碎块','Diamond Chunk'),
 'blaze_coal_powder':('烈焰煤粉','Blaze Coal Powder'), 'nether_ender_eye':('堡垒之眼','Fortress Eye'),
 'stack_torch':('捆装火把','Torch Bundle'), 'iron_bucket_helmet':('铁桶头盔','Iron Bucket Helmet'),
 'crossbow':('极难弩','Extreme Crossbow')}
for id,(cn,eng) in simple.items():named(id,cn,eng);model(id)
for id in ['fancy_red','nether_ender_eye','stack_torch']:tex(EX,f'assets/extreme/textures/items/{id}.png','item/'+id)
for id in ['diamond_chunk','blaze_coal_powder']:tex(ITE,f'assets/miteite/textures/items/{id}.png','item/'+id)
# The source helmet has an armor layer but no independent inventory sprite.
save('assets/modern_ite/models/item/iron_bucket_helmet.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/bucket'}})
tex(EX,'assets/extreme/textures/models/armor/ironBucket_layer_1.png','models/armor/iron_bucket_layer_1')
for id,cn,eng in [('fishing','钓鱼','Fishing'),('planting','种植','Planting'),('villager','村民','Villager'),('club_core','金属棒核心','Club Core')]:
    name='voucher_'+id;named(name,cn+'凭证',eng+' Voucher');model(name);tex(EX,f'assets/extreme/textures/items/voucher/{name}.png','item/'+name)
metals=['copper','silver','gold','iron','ancient_metal','mithril','adamantium','vibranium'];metal_cn=['铜','银','金','铁','古代金属','秘银','艾德曼','振金']
lantern_models(metals,metal_cn)
for metal,cn in zip(metals,metal_cn):
    id='club_'+metal;named(id,cn+'棒',metal.replace('_',' ').title()+' Club');model(id,parent='minecraft:item/handheld');tex(EX,f'assets/extreme/textures/items/club/{metal}_club.png','item/'+id)
for suffix,source in [('', 'standby'),('_pulling_0','pulling_0'),('_pulling_1','pulling_1'),('_pulling_2','pulling_2'),('_arrow','arrow')]:
    tex(EX,'assets/extreme/textures/items/crossbow/crossbow_'+source+'.png','item/crossbow'+suffix)
    model('crossbow'+suffix,parent='minecraft:item/crossbow')
crossbow={'parent':'minecraft:item/crossbow','textures':{'layer0':'modern_ite:item/crossbow'},'overrides':[
 {'predicate':{'pulling':1},'model':'modern_ite:item/crossbow_pulling_0'},
 {'predicate':{'pulling':1,'pull':.58},'model':'modern_ite:item/crossbow_pulling_1'},
 {'predicate':{'pulling':1,'pull':1},'model':'modern_ite:item/crossbow_pulling_2'},
 {'predicate':{'charged':1},'model':'modern_ite:item/crossbow_arrow'},
 {'predicate':{'charged':1,'firework':1},'model':'minecraft:item/crossbow_firework'}]}
save('assets/modern_ite/models/item/crossbow.json',crossbow)
for id,src,cn,eng in [('resurrection_array','block_spawn','复活传送阵','Resurrection Array'),('fancy_red_ore','fancy_red','红钻矿石','Red Diamond Ore')]:tex(EX,f'assets/extreme/textures/blocks/{src}.png','block/'+id);cube(id);named(id,cn,eng,True)
tex(ITE,'assets/miteite/textures/blocks/nether_adamantium_ore.png','block/nether_adamantium_ore');cube('nether_adamantium_ore');named('nether_adamantium_ore','下界艾德曼矿石','Nether Adamantium Ore',True)
for id,src in [('maple_log','log_maple'),('maple_log_top','log_maple_top'),('maple_leaves','leaves_maple'),('maple_planks','planks_maple'),('maple_sapling','sapling_maple')]:tex(EX,'assets/extreme/textures/blocks/'+src+'.png','block/'+id)
cube('maple_planks');cube('maple_leaves');slab('maple_slab','maple_planks');stairs('maple_stairs','maple_planks')
block_model('maple_leaves','minecraft:block/leaves',{'all':'modern_ite:block/maple_leaves'},render_type='minecraft:cutout_mipped')
block_model('maple_log','minecraft:block/cube_column',{'end':'modern_ite:block/maple_log_top','side':'modern_ite:block/maple_log'})
state('maple_log',{'variants':{'axis=y':{'model':'modern_ite:block/maple_log'},'axis=x':{'model':'modern_ite:block/maple_log','x':90,'y':90},'axis=z':{'model':'modern_ite:block/maple_log','x':90}}});block_item('maple_log');loot('maple_log')
block_model('maple_sapling','minecraft:block/cross',{'cross':'modern_ite:block/maple_sapling'},render_type='minecraft:cutout');state('maple_sapling',{'variants':{'':{'model':'modern_ite:block/maple_sapling'}}});model('maple_sapling',parent='minecraft:item/generated');save('assets/modern_ite/models/item/maple_sapling.json',{'parent':'minecraft:item/generated','textures':{'layer0':'modern_ite:block/maple_sapling'}});loot('maple_sapling')
for id,cn in [('log','原木'),('planks','木板'),('leaves','树叶'),('sapling','树苗'),('slab','台阶'),('stairs','楼梯')]:named('maple_'+id,'枫木'+cn,'Maple '+id.title(),True)
silk={'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}
save('data/modern_ite/loot_table/blocks/maple_leaves.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':[{'type':'minecraft:item','name':'modern_ite:maple_leaves','conditions':[{'condition':'minecraft:any_of','terms':[silk,{'condition':'minecraft:match_tool','predicate':{'items':'minecraft:shears'}}]}]},{'type':'minecraft:item','name':'modern_ite:maple_sapling','conditions':[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:table_bonus','enchantment':'minecraft:fortune','chances':[.05,.0625,.083333336,.1]}]}]}]}]})
for side in ['side','top','front_on','front_off']:tex(ITE,f'assets/miteite/textures/blocks/furnace/vibranium/{side}.png','block/vibranium_furnace_'+side)
for lit in [False,True]:block_model('vibranium_furnace'+('_on' if lit else ''),'minecraft:block/orientable',{'top':'modern_ite:block/vibranium_furnace_top','front':'modern_ite:block/vibranium_furnace_front_'+('on' if lit else 'off'),'side':'modern_ite:block/vibranium_furnace_side'})
state('vibranium_furnace',{'variants':{f'facing={f},lit={str(lit).lower()}':{'model':'modern_ite:block/vibranium_furnace'+('_on' if lit else ''),'y':y} for f,y in [('north',0),('east',90),('south',180),('west',270)] for lit in [False,True]}});block_item('vibranium_furnace');loot('vibranium_furnace');named('vibranium_furnace','振金熔炉','Vibranium Furnace',True)
state('vibranium_chest',{'variants':{'':{'model':'minecraft:block/chest'}}});save('assets/modern_ite/models/item/vibranium_chest.json',{'parent':'minecraft:builtin/entity','textures':{'particle':'mite:block/vibranium_block'}});loot('vibranium_chest');named('vibranium_chest','振金私人箱','Vibranium Private Chest',True)
shaped('resurrection_array',['ABA','BCB','ABA'],{'A':'mite:mithril_ingot','B':'minecraft:diamond','C':'minecraft:ender_pearl'})
shaped('vibranium_furnace',['VOA','DND','AOV'],{'V':'mite:vibranium_ingot','O':'minecraft:obsidian','A':'mite:adamantium_ingot','D':'minecraft:diamond','N':'mite:netherrack_furnace'})
shaped('vibranium_chest',['VVV','V V','VVV'],{'V':'mite:vibranium_ingot'})
for metal in metals:
    namespace='minecraft' if metal in ['copper','gold','iron'] else 'mite'
    shaped('dynamic_core_'+metal,[' I ','IGI','ITI'],{'I':namespace+':'+metal+'_ingot','G':'minecraft:glass','T':'minecraft:torch'})
shapeless('voucher_club_core',['modern_ite:voucher_'+v for v in ['fishing','planting','villager']])
shapeless('nether_ender_eye',['mite:mithril_ingot','minecraft:ender_pearl'])
for binding in ['minecraft:string','mite:sinew']:
 for coal in ['minecraft:coal','minecraft:charcoal']:shapeless('stack_torch_'+binding.split(':')[1]+'_'+coal.split(':')[1],[{'tag':'minecraft:logs'},binding,coal,coal],'modern_ite:stack_torch')
shapeless('unpack_torches_1',['modern_ite:stack_torch'],'minecraft:torch',16)
# Modern MITE torches stack to 16; larger recipe results are rejected by its codec.
for n in range(2,5):
    stale=RES/f'data/modern_ite/recipe/unpack_torches_{n}.json'
    if stale.exists():stale.unlink()
shaped('club_iron',['#*#','#*#',' * '],{'#':'minecraft:iron_nugget','*':'minecraft:iron_ingot'})
for mat in ['copper','silver','gold']:
    shaped('club_'+mat,['#*#','#*#',' * '],{'#':('minecraft:' if mat=='gold' else 'mite:')+mat+'_nugget','*':('mite:' if mat=='silver' else 'minecraft:')+mat+'_ingot'})
    smithing('equipment_upgrade/club_'+mat+'_to_iron','modern_ite:club_'+mat,'minecraft:iron_ingot','modern_ite:club_iron')
for mat,prev in [('ancient_metal','iron'),('mithril','ancient_metal'),('adamantium','mithril'),('vibranium','adamantium')]:
    # Match the base mod's native one-template, one-equipment, one-target-ingot route.
    smithing('club_'+mat,'modern_ite:club_'+prev,'mite:'+mat+'_ingot')
shaped('blaze_coal_powder',[' B ','BCB',' B '],{'B':'minecraft:blaze_powder','C':'minecraft:coal'})
shapeless('diamond_from_chunks',['modern_ite:diamond_chunk']*4,'minecraft:diamond')
shapeless('wither_skull',['minecraft:skeleton_skull']+['minecraft:coal_block']*8,'minecraft:wither_skeleton_skull')
shapeless('maple_planks',['modern_ite:maple_log'],count=4)
shaped('maple_slab',['PPP'],{'P':'modern_ite:maple_planks'},count=6);shaped('maple_stairs',['P  ','PP ','PPP'],{'P':'modern_ite:maple_planks'},count=4)
smelt('diamond_chunk_from_coal','minecraft:coal_block','modern_ite:diamond_chunk')
smelt('fancy_red','modern_ite:fancy_red_ore','modern_ite:fancy_red',1)
smelt('nether_adamantium_ingot','modern_ite:nether_adamantium_ore','mite:adamantium_ingot',1)
smelt('maple_charcoal','modern_ite:maple_log','minecraft:charcoal',.15)
tag('minecraft','item','logs',['modern_ite:maple_log']);tag('minecraft','item','logs_that_burn',['modern_ite:maple_log']);tag('minecraft','item','planks',['modern_ite:maple_planks']);tag('minecraft','block','logs',['modern_ite:maple_log']);tag('minecraft','block','logs_that_burn',['modern_ite:maple_log']);tag('minecraft','block','leaves',['modern_ite:maple_leaves']);tag('minecraft','block','saplings',['modern_ite:maple_sapling']);tag('minecraft','item','saplings',['modern_ite:maple_sapling'])
stone=['resurrection_array','fancy_red_ore','nether_adamantium_ore','vibranium_furnace']
tag('minecraft','block','mineable/pickaxe',['modern_ite:'+s for s in stone]);tag('minecraft','block','mineable/axe',['modern_ite:maple_'+s for s in ['log','planks','slab','stairs']]+['modern_ite:vibranium_chest']);tag('minecraft','block','needs_diamond_tool',['modern_ite:fancy_red_ore','modern_ite:nether_adamantium_ore']);tag('modern_ite','worldgen/structure','fortresses',['minecraft:fortress'])
tag('minecraft','block','beacon_base_blocks',['mite:vibranium_block']);tag('minecraft','item','beacon_payment_items',['mite:vibranium_ingot'])
for ore,base,ymin,ymax,size,count,biomes in [('fancy_red_ore','minecraft:stone_ore_replaceables',131,274,3,1,['mite:underworld']),('nether_adamantium_ore','minecraft:base_stone_nether',0,128,3,4,'#minecraft:is_nether')]:
    save(f'data/modern_ite/worldgen/configured_feature/{ore}.json',{'type':'minecraft:ore','config':{'size':size,'discard_chance_on_air_exposure':0,'targets':[{'target':{'predicate_type':'minecraft:tag_match','tag':base},'state':{'Name':'modern_ite:'+ore}}]}})
    save(f'data/modern_ite/worldgen/placed_feature/{ore}.json',{'feature':'modern_ite:'+ore,'placement':[{'type':'minecraft:count','count':count},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':ymin},'max_inclusive':{'absolute':ymax}}},{'type':'minecraft:biome'}]})
    save(f'data/modern_ite/neoforge/biome_modifier/{ore}.json',{'type':'neoforge:add_features','biomes':biomes,'features':['modern_ite:'+ore],'step':'underground_ores'})
provider=lambda name:{'type':'minecraft:simple_state_provider','state':{'Name':name}}
save('data/modern_ite/worldgen/configured_feature/maple.json',{'type':'minecraft:tree','config':{'ignore_vines':True,'force_dirt':False,'minimum_size':{'type':'minecraft:two_layers_feature_size','limit':1,'lower_size':0,'upper_size':1},'dirt_provider':provider('minecraft:dirt'),'trunk_provider':provider('modern_ite:maple_log'),'trunk_placer':{'type':'minecraft:straight_trunk_placer','base_height':4,'height_rand_a':2,'height_rand_b':0},'foliage_provider':provider('modern_ite:maple_leaves'),'foliage_placer':{'type':'minecraft:blob_foliage_placer','radius':2,'offset':0,'height':3},'decorators':[]}})
save('data/modern_ite/worldgen/placed_feature/maple.json',{'feature':'modern_ite:maple','placement':[{'type':'minecraft:rarity_filter','chance':8},{'type':'minecraft:in_square'},{'type':'minecraft:surface_water_depth_filter','max_water_depth':0},{'type':'minecraft:heightmap','heightmap':'OCEAN_FLOOR'},{'type':'minecraft:biome'},{'type':'minecraft:block_predicate_filter','predicate':{'type':'minecraft:would_survive','state':{'Name':'modern_ite:maple_sapling'}}}]})
save('data/modern_ite/neoforge/biome_modifier/maple.json',{'type':'neoforge:add_features','biomes':['minecraft:forest','minecraft:birch_forest','minecraft:flower_forest'],'features':['modern_ite:maple'],'step':'vegetal_decoration'})
effects=[('chaos','混乱','Chaos'),('flash','闪光','Flash'),('occlusion','遮蔽','Occlusion'),('reversed','颠倒','Reversed'),('fast_sensitivity','灵敏失控','Fast Sensitivity'),('look_up','强制仰视','Look Up'),('big_head','大头','Big Head')]
for id,cn,eng in effects:zh['effect.modern_ite.'+id]=cn;en['effect.modern_ite.'+id]=eng
# Reuse meaningful native sprites where the source's extended-potion API supplied no unique icon.
save('assets/minecraft/atlases/mob_effects.json',{'sources':[{'type':'minecraft:single','resource':'minecraft:mob_effect/'+native,'sprite':'modern_ite:'+id} for id,native in [('chaos','nausea'),('flash','glowing'),('occlusion','blindness'),('reversed','unluck'),('fast_sensitivity','speed'),('look_up','levitation'),('big_head','health_boost')]]})
messages={
 'itemGroup.modern_ite':('MITE: Modern ITE','MITE: Modern ITE'),
 'tooltip.modern_ite.lantern.offhand':('仅放在副手时生效。','Only active while held in the offhand.'),
 'tooltip.modern_ite.lantern.remaining':('耐久：%s / %s','Durability: %s / %s'),
 'tooltip.modern_ite.lantern.durability':('照明时消耗耐久，耐久耗尽后停止生效。','Consumes durability while lighting; stops when depleted.'),
 'message.modern_ite.underworld_penalty':('你在地底停留过久，离开一天后可恢复。','You have stayed in the Underworld too long. Leave for a day to recover.'),
 'message.modern_ite.underworld_recovered':('地底停留惩罚已消退。','The Underworld penalty has worn off.'),
 'message.modern_ite.array_used':('复活传送阵已消耗，你失去了全部经验。','The resurrection array was consumed. All experience was lost.'),
 'message.modern_ite.array_exists':('你已有一个复活传送阵。','You already have a resurrection array.'),
 'message.modern_ite.strongbox_broken':('%s 破坏了 %s 的私人箱，坐标：%s','%s broke a private chest belonging to %s at %s'),
 'message.modern_ite.fortress_missing':('附近没有找到下界堡垒。','No Nether fortress was found nearby.'),
 'message.modern_ite.chest_locked':('这是 %s 的私人箱。','This private chest belongs to %s.')}
for k,(cn,eng) in messages.items():zh[k]=cn;en[k]=eng
save('modern_ite/content-lang-zh_cn.json',zh);save('modern_ite/content-lang-en_us.json',en)
glm=RES/'modern_ite/economy-global-loot-modifiers.json'
if glm.exists():save('data/neoforge/loot_modifiers/global_loot_modifiers.json',json.loads(glm.read_text(encoding='utf-8')))
sounds=RES/'modern_ite/mobs-sounds.json'
if sounds.exists():save('assets/modern_ite/sounds.json',json.loads(sounds.read_text(encoding='utf-8')))
for locale in ['zh_cn','en_us']:
    merged={}
    for fragment in sorted((RES/'modern_ite').glob('*-lang-'+locale+'.json')):merged.update(json.loads(fragment.read_text(encoding='utf-8')))
    save(f'assets/modern_ite/lang/{locale}.json',merged)
save('modern_ite/content-provenance.json',{'author':'NingMeng123','assets':provenance,'adaptations':['Modern namespaced identities replace old metadata subtypes.','Existing cherry wood, beds and lantern retain modern identities.','Material lanterns use original shared held-item geometry by NingMeng123 with native Minecraft lantern and Minecraft/MITE metal block textures; legacy mithril and adamantium core identities remain compatible.','Eight material lantern recipes use five matching ingots, glass and a torch; lanterns only light from the offhand and consume material-scaled durability.','Copper, silver and gold clubs receive matching material recipes to make the source-registered identities obtainable.','Native chest geometry uses the original ITE vibranium chest skin; the bucket inventory icon is reused.','Torch bundles unpack one at a time because modern MITE torches stack to sixteen.','Nether adamantium and red diamond heights are mapped to modern dimensions.']})
print(f'Packaged {len(provenance)} source textures; merged all language fragments.')
