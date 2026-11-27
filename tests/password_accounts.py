"""Exercise the real credential API against a throwaway SQLite database."""
import os,secrets,sys,tempfile,time
from pathlib import Path

with tempfile.TemporaryDirectory() as folder:
    os.environ.update(WOIDWATCH_DATABASE=str(Path(folder)/"temp.sqlite"),WOIDWATCH_COOKIE_KEY=secrets.token_urlsafe(48))
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"accounts"))
    from service import app,database,digest
    api=app.test_client()
    body={"name":"Tester","password":"a temporary test passphrase","invite":secrets.token_urlsafe(24)}
    def post(action, data, **kwargs):
        return api.post("/desktop/"+action,json=data,**kwargs)
    with database() as db:db.execute("INSERT INTO invites VALUES(?,?)",(digest(body["invite"]),time.time()+300))
    assert post("register",body).status_code==503
    app.config["GAME_READY"]=lambda:True
    assert post("register",body|{"password":"short"}).status_code==400
    assert post("register",body|{"invite":"wrong"}).status_code==403
    result=post("register",body);assert result.status_code==200
    token=result.json["token"];uid=result.json["id"]
    assert post("register",body|{"name":"OtherUser"}).status_code==403
    spare=secrets.token_urlsafe(24)
    with database() as db:db.execute("INSERT INTO invites VALUES(?,?)",(digest(spare),time.time()+300))
    assert post("register",body|{"name":"tester","invite":spare}).status_code==409
    assert post("login",body|{"password":"wrong password"}).status_code==401
    assert post("login",body|{"name":"TESTER"}).json["id"]==uid
    h={"Authorization":"Bearer "+token}
    assert api.get("/me",headers=h).json["id"]==uid
    app.config["GAME_READY"]=lambda:False
    assert api.get("/me",headers=h).status_code==503
    app.config["GAME_READY"]=lambda:True
    assert post("logout",{},headers=h).status_code==200
    assert api.get("/me",headers=h).status_code==401
    with database() as db:assert db.execute("SELECT password_hash FROM password_users").fetchone()[0].startswith("scrypt:")
    print("PASS: readiness, invitation, password policy, registration, unique name, password verification, current session, logout")
