"""Exercise challenge binding, expiry, replay and revocation without starting a world."""
import os, secrets, sys, tempfile, time
from pathlib import Path

with tempfile.TemporaryDirectory() as folder:
    os.environ.update(WOIDWATCH_DATABASE=str(Path(folder)/"test.sqlite"),WOIDWATCH_COOKIE_KEY=secrets.token_urlsafe(48))
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"accounts"))
    from service import app, database, digest
    verifier=secrets.token_urlsafe()
    app.config.update(GAME_READY=lambda:True, GAME_VERIFIER=verifier)
    client=app.test_client()
    invite=secrets.token_urlsafe()
    with database() as db:
        db.execute("INSERT INTO invites VALUES(?,?)",(digest(invite),time.time()+600))
    account=client.post("/desktop/register",json=dict(name="Tester",password="test password phrase",invite=invite)).json
    headers={"Authorization":"Bearer "+account["token"]}
    assert client.post("/desktop/game-ticket",json={}).status_code==401
    grant=client.post("/desktop/game-ticket",headers=headers,json={}).json
    body=dict(accessToken=grant["ticket"],selectedProfile=grant["profile"],serverId="-abcdef")
    def join(**change):
        return client.post("/session/minecraft/join",json=body|change)
    def verify(name="Tester",challenge="-abcdef",key=verifier):
        return client.get("/verify/"+key+"/session/minecraft/hasJoined",query_string=dict(username=name,serverId=challenge))
    assert join(accessToken="forged").status_code==403
    assert join(selectedProfile="0"*32).status_code==403
    assert join().status_code==204
    assert verify(key="unknown").status_code==404
    assert verify("Imposter").status_code==204
    assert verify(challenge="1234").status_code==204
    assert verify().json==dict(name="Tester",id=grant["profile"],properties=[])
    assert verify().status_code==204
    assert join().status_code==204
    with database() as db:
        db.execute("UPDATE game_joins SET expires=0")
    assert verify().status_code==204
    assert join().status_code==204
    client.post("/desktop/logout",headers=headers,json={})
    assert verify().status_code==204 and join().status_code==403
print("PASS: game proof, profile ownership, invalid ticket, verifier, challenge, replay, expiration, logout")
