"""Audit release boundaries and record exact local source asset provenance. Author: NingMeng123."""
from pathlib import Path
from zipfile import ZipFile
from hashlib import sha256
import json, re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
REF=ROOT.parent/'miteto1.21.1neoforge/.reference'
out=ROOT/'build/verification';out.mkdir(parents=True,exist_ok=True)
def newest_log(*paths):
    existing=[path for path in paths if path.exists()]
    return max(existing,key=lambda path:path.stat().st_mtime) if existing else paths[0]
sources=[REF/'[极难柳昊定制]Extreme-lh-0.6.6 (3).jar',REF/'[MITE不可能这么简单]MITE-ITE-2.0.28.jar']
lookup={};source_hashes={}
for jar in sources:
    source_hashes[jar.name]=sha256(jar.read_bytes()).hexdigest()
    with ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.endswith(('.png','.ogg')):
                lookup.setdefault(sha256(archive.read(name)).hexdigest(),[]).append({'jar':jar.name,'entry':name})
assets=[]
for p in sorted((RES/'assets').rglob('*')):
    if p.suffix in ['.png','.ogg']:
        digest=sha256(p.read_bytes()).hexdigest()
        assets.append({'path':p.relative_to(RES).as_posix(),'sha256':digest,'sources':lookup.get(digest,[])})
json_files=list(RES.rglob('*.json'))
for p in json_files:json.loads(p.read_text(encoding='utf-8'))
version=re.search(r'^mod_version=(.+)$',(ROOT/'gradle.properties').read_text(),re.M)[1].strip()
jar=ROOT/f'build/libs/modern-ite-{version}.jar'
report={'author':'NingMeng123','version':version,'source_archives':source_hashes,'json_files_checked':len(json_files),'asset_count':len(assets),'assets':assets}
if jar.exists():
    with ZipFile(jar) as archive:
        names=archive.namelist()
        forbidden=[n for n in names if n.endswith('.jar') or n.startswith(('net/minecraft/','cn/wensc/','net/xiaoyu233/','io/github/ningmeng123/mite/')) or n.endswith('GameTests.class') or 'ModernIteClientAudit' in n or 'MobRenderRegressionAudit' in n or 'LanternClientAudit' in n]
        if forbidden:raise SystemExit('Release includes forbidden runtime or test entries: '+str(forbidden))
        metadata=archive.read('META-INF/neoforge.mods.toml').decode()
        assert 'modId="modern_ite"' in metadata and 'versionRange="[0.1.9,)"' in metadata
        assert all(s.name not in names for s in sources)
    report['release_jar']={'path':str(jar),'sha256':sha256(jar.read_bytes()).hexdigest(),'bytes':jar.stat().st_size,'nested_jars':False,'old_runtime_classes':False,'base_classes_bundled':False,'test_classes_bundled':False}
tests=[]
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
    t=ET.parse(p).getroot();tests.append({'suite':t.get('name'),'tests':int(t.get('tests')),'failures':int(t.get('failures')),'errors':int(t.get('errors'))})
report['junit']=tests
client=out/'client-audit.json'
if client.exists():
    data=json.loads(client.read_text());report['client']={k:data.get(k) for k in ['status','failures','creature_renderer_count','projectile_renderer_count','item_model_count','effect_sprite_count','world_created','server_connected','gameplay_verified']}
    client_log=ROOT/'extension-client.log'
    latest=client_log.read_text(encoding='utf-8',errors='replace') if client_log.exists() else ''
    report['client']['latest_launch_confirmed']='MODERN_ITE_CLIENT_AUDIT passed' in latest and 'BUILD SUCCESSFUL' in latest
    if not report['client']['latest_launch_confirmed']:report['client']['status']='latest_launch_not_verified'
mob_render=out/'mob-render-audit.json'
if mob_render.exists():
    report['mob_render']=json.loads(mob_render.read_text(encoding='utf-8'))
    render_log=ROOT/'extension-mob-render.log'
    latest=render_log.read_text(encoding='utf-8',errors='replace') if render_log.exists() else ''
    report['mob_render']['latest_launch_confirmed']='MODERN_ITE_MOB_RENDER_AUDIT passed' in latest and 'BUILD SUCCESSFUL' in latest
    if not report['mob_render']['latest_launch_confirmed']:report['mob_render']['status']='latest_launch_not_verified'
lantern=out/'lantern-client-audit.json'
if lantern.exists():
    report['lantern_client']=json.loads(lantern.read_text(encoding='utf-8'))
    lantern_log=out/'lantern-client.log'
    latest=lantern_log.read_text(encoding='utf-8',errors='replace') if lantern_log.exists() else ''
    report['lantern_client']['latest_launch_confirmed']='MODERN_ITE_MOB_RENDER_AUDIT passed' in latest and 'BUILD SUCCESSFUL' in latest
    if not report['lantern_client']['latest_launch_confirmed']:report['lantern_client']['status']='latest_launch_not_verified'
log=newest_log(out/'lantern-final.log',out/'lantern-gametest.log',ROOT/'extension-render-gametest.log',ROOT/'extension-final.log',ROOT/'extension-gametest.log')
if log.exists():
    text=log.read_text(encoding='utf-8',errors='replace');match=re.search(r'All (\d+) required tests passed',text)
    report['gametest']={'passed':int(match[1]) if match else None,'recipe_errors':len(re.findall('Parsing error loading recipe',text)),'tag_errors':len(re.findall("Couldn.t load tag",text))}
(out/'release-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k not in ['assets','junit']},ensure_ascii=False,indent=2))
