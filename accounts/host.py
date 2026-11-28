"""Own the Minecraft child and the account API in one process."""
import argparse
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import threading
import time
from waitress import create_server

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--mode",choices=("server","stub","invite","prepare"),required=True)
    parser.add_argument("--data",type=Path,required=True)
    parser.add_argument("--game",type=Path)
    parser.add_argument("--java",type=Path)
    args=parser.parse_args()
    stub=args.mode=="stub"
    data=args.data.resolve()
    # A persistent mode marker prevents opening a production DB from the test command.
    data.mkdir(parents=True,exist_ok=True)
    marker=data/"environment"
    purpose="test" if stub else "production"
    try:
        with marker.open("x") as f:f.write(purpose)
    except FileExistsError:
        if marker.read_text()!=purpose:raise SystemExit("Wrong database environment")
    key=data/"cookie.key"
    try:
        with key.open("x") as f:f.write(secrets.token_urlsafe(48))
    except FileExistsError:pass
    os.environ["WOIDWATCH_COOKIE_KEY"]=key.read_text()
    os.environ["WOIDWATCH_DATABASE"]=str(data/("test.sqlite" if stub else "accounts.sqlite"))
    from service import app,database,digest
    if args.mode in ("invite","stub"):
        invite=secrets.token_urlsafe(24)
        with database() as db:db.execute("INSERT INTO invites VALUES(?,?)",(digest(invite),time.time()+86400))
        print("Invitation (single use, 24h):",invite,flush=True)
    if args.mode in ("prepare","invite"):return 0
    ready=threading.Event()
    game=None
    app.config["GAME_READY"]=lambda:ready.is_set() and (stub or game is not None and game.poll() is None)
    server=create_server(app,host="127.0.0.1",port=18787 if stub else 8787)
    try:
        if stub:
            ready.set();print("TEST accounts only: http://127.0.0.1:18787",flush=True)
            server.run();return 0
        props=args.game/"server.properties"
        settings=props.read_text(encoding="iso-8859-1").splitlines() if props.exists() else []
        settings=[line for line in settings if not re.match(r"^\s*(online-mode|enforce-secure-profile)(?:\s|[:=]|$)",line)]
        if any(line.rstrip().endswith(chr(92)) for line in settings):
            raise SystemExit("Multiline server.properties is unsupported")
        temp=props.with_suffix(".woidwatch.tmp")
        temp.write_text("\n".join(settings+["online-mode=true","enforce-secure-profile=false",""]),encoding="iso-8859-1")
        os.replace(temp,props)
        verifier=secrets.token_urlsafe(32)
        app.config["GAME_VERIFIER"]=verifier
        game=subprocess.Popen([str(args.java),"@user_jvm_args.txt",
                               "-Dminecraft.api.session.host=http://127.0.0.1:8787/verify/"+verifier,
                               "-Dminecraft.api.services.host=http://127.0.0.1:8787","@libraries/net/neoforged/neoforge/21.1.267/win_args.txt","nogui"],
                              cwd=args.game,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,bufsize=0)
        threading.Thread(target=server.run,daemon=True).start()
        for line in game.stdout:
            sys.stdout.buffer.write(line.replace(verifier.encode(),b"[redacted]"));sys.stdout.buffer.flush()
            if re.search(rb"\[Server thread/INFO\].*:\s*Done \([0-9.,]+s\)! ",line):ready.set()
            elif b"Stopping server" in line:ready.clear()
        return game.wait()
    finally:
        ready.clear()
        if game is not None and game.poll() is None:
            game.terminate();game.wait()
        server.close()

if __name__=="__main__":sys.exit(main())
