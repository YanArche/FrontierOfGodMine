"""Two accounts share visible appearance but cannot modify each other's inventory."""
import base64,io,os,secrets,sys,tempfile,time
from pathlib import Path
from PIL import Image
with tempfile.TemporaryDirectory() as folder:
    os.environ.update(WOIDWATCH_DATABASE=str(Path(folder)/"test.sqlite"),WOIDWATCH_COOKIE_KEY=secrets.token_urlsafe(48))
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"accounts"))
    from service import app,database,digest
    app.config["GAME_READY"]=lambda:True
    api=app.test_client()
    def account(name):
        invite=secrets.token_urlsafe()
        with database() as db:db.execute("INSERT INTO invites VALUES(?,?)",(digest(invite),time.time()+300))
        r=api.post("/desktop/register",json=dict(name=name,password="local test passphrase",invite=invite))
        return {"Authorization":"Bearer "+r.json["token"]}
    first,second=account("Tester"),account("Observer")
    ticket=api.post("/desktop/wardrobe/ticket",headers=first,json={}).json["ticket"]
    game={"Authorization":"Bearer "+ticket}
    def action(op="view",item="",headers=game,**extra):
        return api.post("/desktop/wardrobe",headers=headers,json=dict(op=op,item=item,**extra))
    assert action(headers={}).status_code==401 and api.get("/me",headers=game).status_code==401
    assert action("equip","mechanic").status_code==403
    for item in ("mechanic","amethyst"):
        assert action("claim",item).status_code==200 and action("equip",item).status_code==200
    png=io.BytesIO();Image.new("RGBA",(64,64),"red").save(png,"PNG")
    assert action("skin",skin=base64.b64encode(png.getvalue()).decode()).status_code==200
    assert action("save","1").status_code==200 and action("remove","title").status_code==200
    assert action("skin",skin="").json["skin"]=="" and action("load","1").json["skin"]
    assert action("skin",skin="bad").status_code==400
    assert action("apply", title="mechanic", accessory="copper", skin="").status_code==403
    assert action().json["skin"]
    assert action("apply", title="mechanic", accessory="amethyst", skin=action().json["skin"]).status_code==200
    public=api.get("/wardrobe/player/Tester",headers=second).json
    assert public["title"]=="Механик" and public["accessory"]=="amethyst" and public["skin"] and "owned" not in public
    assert not action(headers=second).json["owned"]
    api.post("/desktop/logout",headers=first,json={})
    assert action().status_code==401
print("PASS wardrobe: sessions, scoped tickets, ownership, skin, outfits, peer view, revocation")
