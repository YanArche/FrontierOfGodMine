"""Account-scoped social graph with reciprocal friendships and directed blocks."""
import re
import time

from flask import abort, jsonify, request


def install(app, database, digest):
    with database() as db:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS friend_requests(sender TEXT, recipient TEXT,
                PRIMARY KEY(sender,recipient));
            CREATE TABLE IF NOT EXISTS friends(first TEXT, second TEXT, PRIMARY KEY(first,second));
            CREATE TABLE IF NOT EXISTS blocks(owner TEXT, target TEXT, PRIMARY KEY(owner,target));
            CREATE TABLE IF NOT EXISTS server_invites(sender TEXT, recipient TEXT, expires INT,
                PRIMARY KEY(sender,recipient));
        """)

    def overview(db, name):
        def column(sql):
            return [row[0] for row in db.execute(sql,(name,))]
        return jsonify(
            friends=column("SELECT second FROM friends WHERE first=? ORDER BY second"),
            incoming=column("SELECT sender FROM friend_requests WHERE recipient=? ORDER BY sender"),
            outgoing=column("SELECT recipient FROM friend_requests WHERE sender=? ORDER BY recipient"),
            blocked=column("SELECT target FROM blocks WHERE owner=? ORDER BY target"),
            invites=column("SELECT sender FROM server_invites WHERE recipient=? AND expires>unixepoch() ORDER BY sender"),
            sent=column("SELECT recipient FROM server_invites WHERE sender=? AND expires>unixepoch() ORDER BY recipient"),
        )

    @app.post("/desktop/social")
    def social():
        header=request.headers.get("Authorization","")
        if not header.startswith("Bearer "):
            abort(401)
        data=request.get_json()
        if not isinstance(data,dict):
            abort(400)
        action=data.get("op","view")
        with database() as db:
            key=digest(header[7:])
            scoped=db.execute("SELECT session FROM wardrobe_tickets WHERE ticket=? AND expires>?",(key,time.time())).fetchone()
            if scoped:
                key=scoped[0]
            user=db.execute("SELECT name FROM sessions JOIN password_users USING(name) WHERE token=? AND expires>?",
                            (key,time.time())).fetchone()
            if not user:
                abort(401)
            name=user[0]
            if action=="view":
                return overview(db,name)
            target=data.get("target")
            if not isinstance(target,str) or not re.fullmatch(r"[a-zA-Z0-9_]{3,16}",target):
                abort(400)
            person=db.execute("SELECT name FROM password_users WHERE name=?",(target,)).fetchone()
            if not person or name==person[0]:
                abort(404)
            target=person[0]
            pair=(name,target,target,name)
            blocked=db.execute("SELECT 1 FROM blocks WHERE (owner=? AND target=?) OR (owner=? AND target=?)",pair).fetchone()
            friend=db.execute("SELECT 1 FROM friends WHERE first=? AND second=?",(name,target)).fetchone()
            if action in ("request","accept","invite") and blocked:
                abort(403)
            if action=="request":
                if not friend:
                    db.execute("INSERT OR IGNORE INTO friend_requests VALUES(?,?)",(name,target))
            elif action=="accept":
                if not db.execute("SELECT 1 FROM friend_requests WHERE sender=? AND recipient=?",(target,name)).fetchone():
                    abort(409)
                db.executemany("INSERT OR IGNORE INTO friends VALUES(?,?)",[(name,target),(target,name)])
                db.execute("DELETE FROM friend_requests WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)",pair)
            elif action in ("decline","cancel"):
                db.execute("DELETE FROM friend_requests WHERE sender=? AND recipient=?",
                           (target,name) if action=="decline" else (name,target))
            elif action in ("remove","block"):
                if action=="block":
                    db.execute("INSERT OR IGNORE INTO blocks VALUES(?,?)",(name,target))
                db.execute("DELETE FROM friends WHERE (first=? AND second=?) OR (first=? AND second=?)",pair)
                db.execute("DELETE FROM friend_requests WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)",pair)
                db.execute("DELETE FROM server_invites WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)",pair)
            elif action=="unblock":
                db.execute("DELETE FROM blocks WHERE owner=? AND target=?",(name,target))
            elif action=="invite":
                if not friend:
                    abort(403)
                db.execute("DELETE FROM server_invites WHERE expires<=unixepoch()")
                db.execute("INSERT OR REPLACE INTO server_invites VALUES(?,?,?)",(name,target,int(time.time())+86400))
            elif action in ("revoke","dismiss"):
                db.execute("DELETE FROM server_invites WHERE sender=? AND recipient=?",
                           (name,target) if action=="revoke" else (target,name))
            else:
                abort(400)
            return overview(db,name)
