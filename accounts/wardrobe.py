import base64
import binascii
import io
import json
import time
import secrets
from flask import abort, jsonify, request
from PIL import Image, UnidentifiedImageError

CATALOG = {
    "pathfinder": ("title", "Следопыт"),
    "mechanic": ("title", "Механик"),
    "amethyst": ("accessory", "Аметистовая брошь"),
    "copper": ("accessory", "Медная брошь"),
}

def skin_png(value):
    if not isinstance(value, str) or len(value) > 48000:
        abort(400)
    if not value:
        return ""
    try:
        with Image.open(io.BytesIO(base64.b64decode(value, validate=True))) as image:
            if image.format != "PNG" or image.size != (64, 64) or image.is_animated:
                abort(400)
            output = io.BytesIO()
            image.convert("RGBA").save(output, format="PNG")
            return base64.b64encode(output.getvalue()).decode("ascii")
    except (ValueError, OSError, binascii.Error, Image.DecompressionBombError):
        abort(400)

def install(app, database, digest):
    with database() as db:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS wardrobes(name TEXT COLLATE NOCASE PRIMARY KEY, data TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS wardrobe_tickets(ticket TEXT PRIMARY KEY, session TEXT NOT NULL, expires INT NOT NULL);
        """)

    def owner(db, ticket_ok=True):
        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            abort(401)
        key = digest(header[7:])
        if ticket_ok:
            ticket = db.execute("SELECT session FROM wardrobe_tickets WHERE ticket=? AND expires>?", (key,time.time())).fetchone()
            if ticket:
                key = ticket[0]
        user = db.execute("SELECT name FROM sessions WHERE token=? AND expires>?", (key,time.time())).fetchone()
        if not user:
            abort(401)
        return user[0], key

    def read(db, name):
        row = db.execute("SELECT data FROM wardrobes WHERE name=?", (name,)).fetchone()
        return json.loads(row[0]) if row else dict(skin="", title="", accessory="", owned=[], sets={})

    @app.post("/desktop/wardrobe/ticket")
    def wardrobe_ticket():
        with database() as db:
            _, session = owner(db, False)
            token = secrets.token_urlsafe(32)
            db.execute("DELETE FROM wardrobe_tickets WHERE expires<=?", (time.time(),))
            db.execute("INSERT INTO wardrobe_tickets VALUES(?,?,?)", (digest(token),session,time.time()+21600))
        return jsonify(ticket=token)

    @app.get("/wardrobe/player/<name>")
    def worn(name):
        with database() as db:
            owner(db)
            if not db.execute("SELECT name FROM password_users WHERE name=? UNION SELECT name FROM users WHERE name=?", (name,name)).fetchone():
                return jsonify(member=False)
            state = read(db, name)
        return jsonify(member=True, skin=state["skin"], accessory=state["accessory"], title=CATALOG[state["title"]][1] if state["title"] else "")

    @app.post("/desktop/wardrobe")
    def wardrobe():
        data = request.get_json()
        if not isinstance(data, dict):
            abort(400)
        op = data.get("op", "view")
        with database() as db:
            name, _ = owner(db)
            state = read(db, name)
            item = data.get("item","")
            if op in ("claim","equip"):
                if not isinstance(item,str) or item not in CATALOG:
                    abort(400)
                if op == "claim":
                    if item not in state["owned"]:
                        state["owned"].append(item)
                else:
                    if item not in state["owned"]:
                        abort(403)
                    state[CATALOG[item][0]] = item
            elif op == "remove" and item in ("title","accessory"):
                state[item] = ""
            elif op == "skin":
                state["skin"] = skin_png(data.get("skin"))
            elif op in ("save","load") and item in ("1","2"):
                if op == "save":
                    state["sets"][item] = {k:state[k] for k in ("title","accessory","skin")}
                elif item in state["sets"]:
                    state.update(state["sets"][item])
                else:
                    abort(400)
            elif op != "view":
                abort(400)
            if op != "view":
                db.execute("INSERT OR REPLACE INTO wardrobes VALUES(?,?)", (name,json.dumps(state)))
            return jsonify(**{k:v for k,v in state.items() if k!="sets"}, sets=list(state["sets"]), catalog=CATALOG)
