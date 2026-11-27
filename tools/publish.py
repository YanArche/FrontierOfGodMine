import concurrent.futures, hashlib, http.client, json, os, pathlib, subprocess, sys, urllib.parse, urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
os.chdir(root)
slug='YanArche/FrontierOfGodMine'
tag=sys.argv[1]
launcher=tag.startswith("launcher-v")
secret=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,check=True).stdout
token=next(line[9:] for line in secret.splitlines() if line.startswith('password='))
headers={'Authorization':'Bearer '+token,'User-Agent':'Frontier-release','Accept':'application/vnd.github+json'}
def api(path,data=None,method=None):
    req=urllib.request.Request('https://api.github.com/repos/'+slug+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
    with urllib.request.urlopen(req) as r:return json.load(r)
release=next((r for r in api('/releases') if r['tag_name']==tag),None)
app_only='--app-only' in sys.argv[2:]
if app_only and not launcher:raise SystemExit('--app-only requires a launcher tag')
if launcher:
    details={'tag_name':tag,'name':'WoidWatch Launcher '+tag.removeprefix('launcher-v'),'draft':True,'target_commitish':'main','body':'Библиотека, импорт существующего Фронтира и подписанные обновления приложения. Полностью распакуйте архив Windows или macOS. Для перехода с 1.0.0 также доступен маленький архив Migration с инструкцией.'}
    if app_only:details['body']='Подписанное обновление WoidWatch. Установленный лаунчер получает новую версию автоматически. Для первой установки используйте полный клиентский архив из выпуска launcher-v1.1.0.'
else:
    details=None
if '--accounts' in sys.argv:
    details['body']+=' Регистрация по приглашению, вход по паролю и возобновление сессии находятся в окне «Аккаунт». Тест: распакуйте WoidWatch-Accounts.zip и запустите Test-accounts.cmd. В Frontier-Server.zip рабочая служба поднимается через Start-server.cmd; приглашения выдаёт accounts/Create-invite.cmd.'
if not release:release=api('/releases',details or {'tag_name':tag,'target_commitish':'main','name':'Frontier of God · '+tag,'draft':True,'body':'Распакуйте один архив для вашей платформы. Windows — WoidWatch.exe, macOS — WoidWatch Launcher.app, выделенный сервер Windows — Prepare-server.cmd для подготовки и Start-server.cmd для запуска.'})
def upload(name):
    f=Path('dist')/name
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
assets=['woidwatch.jar'] if app_only else (['woidwatch.jar','WoidWatch-Migration.zip','WoidWatch-Windows.zip','WoidWatch-macOS.zip'] if launcher else ['Frontier-Server.zip','WoidWatch-Windows.zip','WoidWatch-macOS.zip'])
if '--icons' in sys.argv:assets.append('WoidWatch-Icons.zip')
if '--accounts' in sys.argv:assets.extend(['WoidWatch-Accounts.zip','Frontier-Server.zip'])
with concurrent.futures.ThreadPoolExecutor(3) as pool:list(pool.map(upload,assets))
publication={'draft':False,'make_latest':'false' if launcher else 'true'}
if launcher:publication['body']=details['body']
print(api('/releases/'+str(release['id']),publication,'PATCH')['html_url'])
