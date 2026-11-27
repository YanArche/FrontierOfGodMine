"""Server-owned credentials and one-time invitations for the desktop client."""
import re
import secrets
import time
import uuid
from flask import abort, jsonify, request
from werkzeug.security import generate_password_hash, check_password_hash

def install(app, database, digest):
    with database() as db:
        db.executescript("""
          CREATE TABLE IF NOT EXISTS password_users (
            id TEXT PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE,
            password_hash TEXT NOT NULL, created INTEGER NOT NULL);
          CREATE TABLE IF NOT EXISTS login_limits (bucket TEXT PRIMARY KEY, attempts INTEGER, expires INTEGER);
          CREATE TRIGGER IF NOT EXISTS passkey_unique_name BEFORE INSERT ON users
            WHEN EXISTS(SELECT 1 FROM password_users WHERE name=NEW.name)
            BEGIN SELECT RAISE(ABORT,'name in use'); END;
        """)
    dummy=generate_password_hash(secrets.token_urlsafe(32), method="scrypt:32768:8:1")
    def limited(db,key,maximum):
        now=int(time.time())
        db.execute("DELETE FROM login_limits WHERE expires<=?",(now,))
        db.execute("INSERT INTO login_limits VALUES(?,1,?) ON CONFLICT(bucket) DO UPDATE SET attempts=attempts+1",(key,now+300))
        return db.execute("SELECT attempts FROM login_limits WHERE bucket=?",(key,)).fetchone()[0]>maximum

    @app.post("/desktop/register")
    @app.post("/desktop/login")
    def password_login():
        body=request.get_json()
        if not isinstance(body,dict):abort(400)
        name=body.get("name");password=body.get("password")
        registration=request.path.endswith("/register")
        if not isinstance(name,str) or not re.fullmatch("[A-Za-z0-9_]{3,16}",name) or not isinstance(password,str) or not 1<=len(password)<=128:abort(400)
        if registration and len(password)<12:abort(400)
        with database() as db:
            # The reverse proxy is loopback: use a global bound and a per-name bound.
            global_limit=limited(db,"all",300)
            name_limit=limited(db,"name:"+name.lower(),20)
        if global_limit or name_limit:abort(429)
        if registration:
            invite=body.get("invite")
            if not isinstance(invite,str) or len(invite)>128:abort(403)
            fingerprint=digest(invite)
            with database() as db:
                if not db.execute("SELECT 1 FROM invites WHERE hash=? AND expires>?",(fingerprint,time.time())).fetchone():abort(403)
            hashed=generate_password_hash(password,method="scrypt:32768:8:1")
            with database() as db:
                if not app.config["GAME_READY"]():abort(503)
                if not db.execute("DELETE FROM invites WHERE hash=? AND expires>?",(fingerprint,time.time())).rowcount:abort(403)
                if db.execute("SELECT 1 FROM users WHERE name=? UNION ALL SELECT 1 FROM password_users WHERE name=?",(name,name)).fetchone():abort(409)
                user_id=str(uuid.uuid4())
                db.execute("INSERT INTO password_users VALUES(?,?,?,?)",(user_id,name,hashed,int(time.time())))
        else:
            with database() as db:
                row=db.execute("SELECT id,name,password_hash FROM password_users WHERE name=?",(name,)).fetchone()
            valid=check_password_hash(row[2] if row else dummy,password)
            if not valid or not row:abort(401)
            user_id,name=row[:2]
        token=secrets.token_urlsafe(32)
        with database() as db:
            db.execute("DELETE FROM sessions WHERE expires<=?",(time.time(),))
            db.execute("INSERT INTO sessions VALUES(?,?,?)",(digest(token),name,time.time()+30*86400))
        return jsonify(name=name,id=user_id,token=token)

    @app.post("/desktop/logout")
    def logout():
        header=request.headers.get("Authorization","")
        if not header.startswith("Bearer "):abort(401)
        with database() as db:db.execute("DELETE FROM sessions WHERE token=?",(digest(header[7:]),))
        return jsonify(ok=True)
