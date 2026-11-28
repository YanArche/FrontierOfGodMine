"""Minecraft login proof backed by WoidWatch sessions."""
import re
import secrets
import time
from flask import abort, jsonify, request

def install(app, database, digest):
    with database() as db:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS game_tickets(token TEXT PRIMARY KEY, session TEXT, profile TEXT, expires INTEGER);
            CREATE TABLE IF NOT EXISTS game_joins(challenge TEXT, name TEXT COLLATE NOCASE, ticket TEXT, expires INTEGER,
                PRIMARY KEY(challenge,name));
        """)

    @app.post("/desktop/game-ticket")
    def issue_game_ticket():
        token = request.headers.get("Authorization", "").removeprefix("Bearer ")
        with database() as db:
            session = digest(token)
            row = db.execute("""SELECT password_users.id FROM sessions JOIN password_users USING(name)
                                WHERE token=? AND expires>?""", (session,time.time())).fetchone()
            if not row:
                abort(401)
            profile=row[0].replace("-","")
            key = secrets.token_urlsafe(32)
            db.execute("DELETE FROM game_tickets WHERE expires<=?", (time.time(),))
            db.execute("INSERT INTO game_tickets VALUES(?,?,?,?)",(digest(key),session,profile,time.time()+86400))
        return jsonify(ticket=key, profile=profile)

    @app.post("/session/minecraft/join")
    def game_join():
        data = request.get_json()
        if not isinstance(data, dict):
            abort(400)
        challenge, profile, token = (data.get(k) for k in ("serverId","selectedProfile","accessToken"))
        if (not isinstance(challenge,str) or not re.fullmatch(r"-?[0-9a-f]{1,40}",challenge)
            or not isinstance(profile,str) or not isinstance(token,str) or len(token)>128):
            abort(400)
        with database() as db:
            now=time.time()
            key=digest(token)
            row=db.execute("""SELECT sessions.name FROM game_tickets JOIN sessions ON sessions.token=game_tickets.session
                WHERE game_tickets.token=? AND profile=? AND game_tickets.expires>? AND sessions.expires>?""",
                (key,profile,now,now)).fetchone()
            if not row:
                return jsonify(error="ForbiddenOperationException",errorMessage="WoidWatch session invalid"),403
            db.execute("DELETE FROM game_joins WHERE expires<=?",(now,))
            db.execute("INSERT OR REPLACE INTO game_joins VALUES(?,?,?,?)",(challenge,row[0],key,now+45))
        return "",204

    @app.get("/verify/<secret>/session/minecraft/hasJoined")
    def game_has_joined(secret):
        expected=app.config.get("GAME_VERIFIER","")
        if not expected or not secrets.compare_digest(secret,expected):
            abort(404)
        challenge,name=request.args.get("serverId",""),request.args.get("username","")
        with database() as db:
            now=time.time()
            row=db.execute("""SELECT game_tickets.profile, sessions.name FROM game_joins
                JOIN game_tickets ON game_tickets.token=game_joins.ticket
                JOIN sessions ON sessions.token=game_tickets.session
                WHERE challenge=? AND game_joins.name=? AND game_joins.expires>? AND game_tickets.expires>? AND sessions.expires>?""",
                (challenge,name,now,now,now)).fetchone()
            if not row:
                return "",204
            db.execute("DELETE FROM game_joins WHERE challenge=? AND name=?",(challenge,name))
        return jsonify(id=row[0],name=row[1],properties=[])
