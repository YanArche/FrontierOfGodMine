"""Export only pack-owned resources. Exact CurseForge files stay referenced by URL."""
import argparse, hashlib, json, re, shutil, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(data): return hashlib.sha256(data).hexdigest()
def export(profile, baseline):
    pack=ROOT/'pack'
    dist=ROOT/'dist'
    dist.mkdir(exist_ok=True)
    (pack/'vendor').mkdir(exist_ok=True)
    exclude=tuple(x for x in (pack/'server-exclude.txt').read_text(encoding='utf-8-sig').lower().splitlines() if x and not x.startswith('#'))
    addons=json.loads((profile/'minecraftinstance.json').read_bytes())['installedAddons']
    upstream={a['fileNameOnDisk']:a['installedFile'] for a in addons}
    files={}
    with zipfile.ZipFile(baseline) as z:
        names={n[5:] for n in z.namelist() if n.startswith('game/') and not n.startswith('game/mods/')}
    names.update(f.relative_to(profile).as_posix() for f in (profile/'config/ftbquests/quests').rglob('*.snbt'))
    names.update('mods/'+f.name for f in (profile/'mods').glob('*.jar'))
    # Unique, data-only mod makes the counter available in new and existing worlds.
    day=pack/'day';dayjar=dist/'frontier-days.jar'
    with zipfile.ZipFile(dayjar,'w',zipfile.ZIP_DEFLATED) as z:
        for f in sorted(day.rglob('*')):
            if f.is_file():z.writestr(zipfile.ZipInfo(f.relative_to(day).as_posix()),f.read_bytes())
    names.add('mods/frontier-days.jar')
    with zipfile.ZipFile(pack/'overrides.zip','w',zipfile.ZIP_DEFLATED) as overrides, zipfile.ZipFile(dist/'client.zip','w',zipfile.ZIP_DEFLATED) as client, zipfile.ZipFile(dist/'server.zip','w',zipfile.ZIP_DEFLATED) as server:
        for path in sorted(names):
            source=dayjar if path=='mods/frontier-days.jar' else profile/path
            if not source.is_file():continue
            data=source.read_bytes()
            if path=='options.txt':data=data.replace(b'showSubtitles:false',b'showSubtitles:true')
            mod=path.startswith('mods/')
            onserver=(mod and not source.name.lower().startswith(exclude)) or path.startswith(('config/ftbquests/quests/','defaultconfigs/','datapacks/')) or (path.startswith('config/') and path.endswith(('-common.toml','-server.toml','-startup.toml')))
            entry={'sha256':sha(data),'server':onserver}
            a=upstream.get(source.name,{})
            if mod and any(h['value']==hashlib.sha1(data).hexdigest() for h in a.get('hashes',[]) if h['type']==1) and a.get('downloadUrl','').startswith('https://'):
                entry['url']=a['downloadUrl']
            elif mod:
                vendor=pack/'vendor'/source.name;vendor.write_bytes(data);entry['repo']=vendor.relative_to(ROOT).as_posix()
            else:overrides.writestr(path,data)
            files[path]=entry
            client.writestr(path,data,compress_type=zipfile.ZIP_STORED if mod else zipfile.ZIP_DEFLATED)
            if onserver:server.writestr(path,data,compress_type=zipfile.ZIP_STORED if mod else zipfile.ZIP_DEFLATED)
    index={'format':1,'minecraft':'1.21.1','neoforge':'21.1.267','overrides':sha((pack/'overrides.zip').read_bytes()),'files':files}
    (pack/'index.json').write_text(json.dumps(index,ensure_ascii=False,indent=2),encoding='utf8')
if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('profile',type=Path);ap.add_argument('baseline',type=Path);a=ap.parse_args();export(a.profile,a.baseline)
