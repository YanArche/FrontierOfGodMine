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
server_bundle='--server-bundle' in sys.argv[2:]
if server_bundle and not app_only:raise SystemExit('--server-bundle requires --app-only')
artifact=next((arg.split('=',1)[1] for arg in sys.argv[2:] if arg.startswith('--launcher-artifact=')),None)
if app_only and not launcher:raise SystemExit('--app-only requires a launcher tag')
if launcher:
    details={'tag_name':tag,'name':'WoidWatch Launcher '+tag.removeprefix('launcher-v'),'draft':True,'target_commitish':'main','body':'Библиотека, импорт существующего Фронтира и подписанные обновления приложения. Полностью распакуйте архив Windows или macOS. Для перехода с 1.0.0 также доступен маленький архив Migration с инструкцией.'}
    if app_only:details['body']='Подписанное обновление WoidWatch. Установленный лаунчер получает новую версию автоматически. Для первой установки используйте полный клиентский архив из выпуска launcher-v1.1.0.'
else:
    details=None
if '--accounts' in sys.argv:
    details['body']+=' Регистрация по приглашению, вход по паролю и возобновление сессии находятся в окне «Аккаунт». Тест: распакуйте WoidWatch-Accounts.zip и запустите Test-accounts.cmd. В Frontier-Server.zip рабочая служба поднимается через Start-server.cmd; приглашения выдаёт accounts/Create-invite.cmd.'
if server_bundle:
    details['body'] = """## Развёртывание сервера

Скачайте Frontier-Server.zip и Frontier-Server.zip.sha256. Сверьте SHA-256 и распакуйте архив в D:/FrontierServer. Инструкция SERVER-DEPLOY.md лежит в архиве и приложена отдельно.

Комплект включает NeoForge, Java 21, службу WoidWatch, регистрацию, игровые билеты, друзей, гардероб и сценарии ручного запуска и автозапуска. Перед первым запуском владелец подтверждает Minecraft EULA. Для подключения из интернета настройте отдельные маршруты аккаунтов и Minecraft по инструкции.

woidwatch.jar — подписанное автообновление лаунчера 1.10.0. Существующая установка получит его через стабильный канал. Проверки на рабочем сервере, после перезагрузки и из двух разных сетей выполняются на ПК развёртывания."""
if not release:release=api('/releases',details or {'tag_name':tag,'target_commitish':'main','name':'Frontier of God · '+tag,'draft':True,'body':'Распакуйте один архив для вашей платформы. Windows — WoidWatch.exe, macOS — WoidWatch Launcher.app, выделенный сервер Windows — Prepare-server.cmd для подготовки и Start-server.cmd для запуска.'})
def upload(name):
    f=Path(artifact) if name=='woidwatch.jar' and artifact else Path('dist')/name
    with f.open('rb') as source:
        sha='sha256:'+hashlib.file_digest(source,'sha256').hexdigest()
    existing=next((a for a in release['assets'] if a['name']==name),None)
    if existing:
        if existing.get('digest')==sha:return
        raise RuntimeError('Asset differs: '+name)
    conn=http.client.HTTPSConnection('uploads.github.com',timeout=1200)
    with f.open('rb') as body:
        conn.request('POST','/repos/'+slug+'/releases/'+str(release['id'])+'/assets?name='+name,body=body,headers={**headers,'Content-Type':'application/zip','Content-Length':str(f.stat().st_size)})
        r=conn.getresponse();a=json.load(r)
    if r.status!=201 or a.get('digest')!=sha:raise RuntimeError((r.status,a.get('message'),name))
    print('Verified',name,flush=True)
assets=['woidwatch.jar'] if app_only else (['woidwatch.jar','WoidWatch-Migration.zip','WoidWatch-Windows.zip','WoidWatch-macOS.zip'] if launcher else ['Frontier-Server.zip','WoidWatch-Windows.zip','WoidWatch-macOS.zip'])
if '--wardrobe' in sys.argv:
    assets.append('WoidWatch-Accounts.zip')
    details['body']+=' Гардероб, свои скины, два комплекта, титулы и броши. Для обновления службы аккаунтов распакуйте WoidWatch-Accounts.zip в папку accounts и перезапустите службу.'
if server_bundle:assets.extend(['Frontier-Server.zip','Frontier-Server.zip.sha256','SERVER-DEPLOY.md'])
if '--icons' in sys.argv:assets.append('WoidWatch-Icons.zip')
if '--accounts' in sys.argv:assets.extend(['WoidWatch-Accounts.zip','Frontier-Server.zip'])
with concurrent.futures.ThreadPoolExecutor(3) as pool:list(pool.map(upload,assets))
publication={'draft':False,'make_latest':'false' if launcher else 'true'}
if launcher:publication['body']=details['body']
print(api('/releases/'+str(release['id']),publication,'PATCH')['html_url'])
