import concurrent.futures, hashlib, os, pathlib, shutil, subprocess, sys, urllib.request, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
os.chdir(root)
dist=Path('dist')
dist.mkdir(exist_ok=True)
runtimes={
 'windows':('x64_windows','zip','e15ee1e54a5a3b38027a09cedfa87bc3348d7dd3ce50866dd3d9671d7fffc6c1'),
 'aarch64':('aarch64_mac','tar.gz','848feb1906c7de79de4f8391636042193fbd46eff86e26299506c4656f62c260'),
 'x64':('x64_mac','tar.gz','10cee6ba4cd83fd4613a3f0bb3ef9dddd68c68d5f3ccfcc7e456707c9e1eafe0')}
def fetch(key):
    arch,ext,sha=runtimes[key]
    target=dist/('runtime-'+key+'.'+ext)
    if not target.exists():
        url='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jre_'+arch+'_hotspot_21.0.13.1_2.'+ext
        subprocess.run(['curl.exe','-fsSL','--retry','3','-o',str(target)+'.part',url],check=True)
        os.replace(str(target)+'.part',target)
    assert hashlib.file_digest(target.open('rb'),'sha256').hexdigest()==sha,key
server_only='--server-only' in sys.argv[1:]
targets=('windows',) if server_only else runtimes
with concurrent.futures.ThreadPoolExecutor(3) as pool:list(pool.map(fetch,targets))
def build(name):
    mac=name=='macOS'
    server=name=='Server'
    prefix='WoidWatch Launcher.app/Contents/' if mac else ''
    res=prefix+('Resources/' if mac else 'resources/')
    with zipfile.ZipFile(dist/(('Frontier-' if server else 'WoidWatch-')+name+'.zip'),'w',allowZip64=True) as z:
        for src,dst in [(dist/('server.zip' if server else 'client.zip'),'payload.zip'),('pack/index.json','index.json'),(('dist/frontier-server.jar' if server_only else 'dist/frontier.jar'),'frontier.jar'),('lib/gson.jar','gson.jar'),('dist/loader.jar','loader.jar')]:
            z.write(src,res+dst)
        if mac:
            z.write('platform/Info.plist',prefix+'Info.plist')
            z.write('platform/Frontier.icns',res+'Frontier.icns')
            script=zipfile.ZipInfo(prefix+'MacOS/Frontier')
            script.create_system=3
            script.external_attr=0o100755<<16
            z.writestr(script,Path('platform/Frontier').read_bytes().replace(b'\r\n',b'\n'))
            for key in ('aarch64','x64'):z.write(dist/('runtime-'+key+'.tar.gz'),res+'runtime-'+key+'.tar.gz')
        else:
            with zipfile.ZipFile(dist/'runtime-windows.zip') as runtime:
                for entry in runtime.infolist():
                    if not entry.is_dir():z.writestr(res+'runtime/'+entry.filename.split('/',1)[1],runtime.read(entry),zipfile.ZIP_DEFLATED,1)
            if server:
                for file in Path('accounts').iterdir():
                    if file.is_file():z.write(file,'accounts/'+file.name)
                z.write('platform/frontier.ps1','frontier.ps1')
                for file in ('Run-server.ps1','Stop-server.ps1','Install-Autostart.ps1','server-jvm-defaults.txt'):
                    z.write('platform/'+file,file)
                z.write('SERVER-DEPLOY.md','SERVER-DEPLOY.md')
                wrappers={'Prepare-server':('frontier.ps1',' -Server -PrepareOnly'), 'Start-server':('Run-server.ps1',''), 'Stop-server':('Stop-server.ps1','')}
                for action,(script,flags) in wrappers.items():
                    z.writestr(action+'.cmd','@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0'+script+'"'+flags+' %*\r\n')
            else:z.write('dist/Frontier.exe','WoidWatch.exe')
        z.write('README.md','README.md')
    print(name,flush=True)
for platform in (('Server',) if server_only else ('Server','Windows','macOS')):build(platform)
if server_only:sys.exit(0)

with zipfile.ZipFile(dist/'WoidWatch-Migration.zip','w') as patch:
    patch.write(dist/'woidwatch.jar','frontier.jar')
    patch.write('launcher/MIGRATION.md','README.md')

with zipfile.ZipFile(dist/'WoidWatch-Accounts.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in Path('accounts').iterdir():
        if f.is_file():z.write(f,f.name)
