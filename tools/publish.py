import concurrent.futures, hashlib, http.client, json, os, pathlib, subprocess, sys, urllib.parse, urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
os.chdir(root)
slug='YanArche/FrontierOfGodMine'
tag=sys.argv[1]
secret=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,check=True).stdout
token=next(line[9:] for line in secret.splitlines() if line.startswith('password='))
headers={'Authorization':'Bearer '+token,'User-Agent':'Frontier-release','Accept':'application/vnd.github+json'}
def api(path,data=None,method=None):
    req=urllib.request.Request('https://api.github.com/repos/'+slug+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
    with urllib.request.urlopen(req) as r:return json.load(r)
release=next((r for r in api('/releases') if r['tag_name']==tag),None)
if not release:release=api('/releases',{'tag_name':tag,'target_commitish':'main','name':'Frontier of God · '+tag,'draft':True,'body':'Распакуйте один архив для вашей платформы. Windows — Frontier.exe, macOS — Frontier of God.app, выделенный сервер Windows — Prepare-server.cmd для подготовки и Start-server.cmd для запуска.'})
def upload(name):
    f=Path('dist/Frontier-'+name+'.zip')
    sha='sha256:'+hashlib.file_digest(f.open('rb'),'sha256').hexdigest()
    existing=next((a for a in release['assets'] if a['name']==f.name),None)
    if existing:
        if existing.get('digest')==sha:return
        raise RuntimeError('Asset differs: '+f.name)
    conn=http.client.HTTPSConnection('uploads.github.com',timeout=1200)
    with f.open('rb') as body:
        conn.request('POST','/repos/'+slug+'/releases/'+str(release['id'])+'/assets?name='+f.name,body=body,headers={**headers,'Content-Type':'application/zip','Content-Length':str(f.stat().st_size)})
        r=conn.getresponse();a=json.load(r)
    if r.status!=201 or a.get('digest')!=sha:raise RuntimeError((r.status,a.get('message'),f.name))
    print('Verified',f.name,flush=True)
with concurrent.futures.ThreadPoolExecutor(3) as pool:list(pool.map(upload,['Server','Windows','macOS']))
print(api('/releases/'+str(release['id']),{'draft':False},'PATCH')['html_url'])
