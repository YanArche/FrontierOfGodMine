"""Run the production supervisor with an inert stand-in for the game JVM."""
from pathlib import Path
import os,sys,subprocess,tempfile,socket,time,json,urllib.request,urllib.error
class Fixture:
    def __init__(self,args):
        self.p=subprocess.Popen(args,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    def signal(self,s):
        self.p.stdin.write(s+"\n");self.p.stdin.flush()
    def close(self):
        if self.p.poll() is None:self.p.terminate()
        self.p.wait(10)
def call(url,body=None):
    req=urllib.request.Request(url,json.dumps(body).encode() if body is not None else None,
                               {"Content-Type":"application/json"})
    try:
        with urllib.request.urlopen(req,timeout=1) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,None
def wait_for(url,ready):
    for _ in range(100):
        try:
            if call(url+"/health")[1]["registration"]==ready:return
        except OSError:pass
        time.sleep(.1)
    raise AssertionError("readiness timeout")

with tempfile.TemporaryDirectory() as temporary:
    tmp=Path(temporary)
    marker=tmp/"started"
    game=tmp/"fake.py"
    game.write_text('import sys\nfor cmd in sys.stdin:\n if cmd.strip()=="ready":print("[Server thread/INFO] [minecraft/DedicatedServer]: Done (1.0s)! For help, type help",flush=True)\n if cmd.strip()=="stop":break\n')
    with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
    server=tmp/"fixture.py"
    accounts=str(Path(__file__).resolve().parents[1]/"accounts")
    server.write_text(f"""import sys,subprocess
sys.path.insert(0,{accounts!r})
import host
original=host.create_server
host.create_server=lambda app,**kwargs:original(app,host='127.0.0.1',port={port})
original_popen=subprocess.Popen
host.subprocess.Popen=lambda args,**kwargs:original_popen([sys.executable,{str(game)!r}],**kwargs)
sys.exit(host.main())
""",encoding="utf8")
    url=f"http://127.0.0.1:{port}"
    args=[sys.executable,str(server),"--mode","server","--data",str(tmp/"production"),"--game",str(tmp),"--java","fixture"]
    fixture=Fixture(args)
    try:
        wait_for(url,False)
        assert call(url+"/desktop/register",{})[0]==503
        fixture.signal("ready");wait_for(url,True)
        assert call(url+"/desktop/register",{})[0]==400
        fixture.signal("stop");assert fixture.p.wait(10)==0
        try:call(url+"/health");raise AssertionError("service survived game")
        except OSError:pass
        assert (tmp/"production/accounts.sqlite").exists()
        print("PASS: DB created, closed during startup, opens on Done, stops with game, database persists")
    finally:fixture.close()
