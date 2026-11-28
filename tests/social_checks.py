import os, secrets, sys, tempfile, time
from pathlib import Path

with tempfile.TemporaryDirectory() as tmp:
    os.environ.update(WOIDWATCH_DATABASE=str(Path(tmp)/"social.sqlite"),WOIDWATCH_COOKIE_KEY=secrets.token_urlsafe(48))
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"accounts"))
    from service import app, database, digest
    app.config["GAME_READY"]=lambda:True
    client=app.test_client()
    def account(name):
        invite=secrets.token_urlsafe()
        with database() as db:
            db.execute("INSERT INTO invites VALUES(?,?)",(digest(invite),time.time()+300))
        return client.post("/desktop/register",json=dict(name=name,invite=invite,password="testing passwords over here")).json["token"]
    first,second=account("First"),account("Second")
    def action(token, op="view", target=""):
        return client.post("/desktop/social",json=dict(op=op,target=target),headers={"Authorization":"Bearer "+token})
    assert action("invalid").status_code==401
    scope=client.post("/desktop/wardrobe/ticket",json={},headers={"Authorization":"Bearer "+first}).json["ticket"]
    assert action(scope).status_code==200
    assert client.get("/me",headers={"Authorization":"Bearer "+scope}).status_code==401
    assert action(first,"invite","Second").status_code==403
    assert action(first,"request","second").json["outgoing"]==["Second"]
    assert action(second,"accept","first").json["friends"]==["First"]
    assert action(first).json["friends"]==["Second"]
    assert action(first,"invite","Second").json["sent"]==["Second"]
    assert action(second).json["invites"]==["First"]
    assert action(first,"revoke","Second").json["sent"]==[]
    assert action(second).json["invites"]==[]
    action(first,"invite","Second")
    with database() as db:
        db.execute("UPDATE server_invites SET expires=0")
    assert action(second).json["invites"]==[]
    action(first,"invite","Second")
    assert action(second,"block","First").json["blocked"]==["First"]
    assert action(first).json["sent"]==[] and action(first).json["friends"]==[]
    assert action(first,"request","Second").status_code==403
    assert action(second,"unblock","First").json["blocked"]==[]
    assert action(first,"request","Second").json["outgoing"]==["Second"]
print("PASS social: friendship, invitation, expiry, revocation, block and unblock")
