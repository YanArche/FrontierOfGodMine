"""Invite-only WoidWatch identity service, behind an HTTPS reverse proxy."""
import hashlib
import json
import os
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager, closing
from pathlib import Path

from flask import Flask, abort, jsonify, request, send_from_directory, session
from webauthn import (generate_registration_options, generate_authentication_options,
                      verify_registration_response, verify_authentication_response, options_to_json)
from webauthn.helpers import bytes_to_base64url, base64url_to_bytes
from webauthn.helpers.structs import (AuthenticatorSelectionCriteria, ResidentKeyRequirement,
                                      UserVerificationRequirement, PublicKeyCredentialDescriptor)
from webauthn.helpers.exceptions import WebAuthnException

RP = "accounts.noboredgames.win"
ORIGIN = "https://" + RP
app = Flask(__name__)
app.secret_key = os.environ["WOIDWATCH_COOKIE_KEY"]
if len(app.secret_key) < 32:
    raise ValueError("Set a random WOIDWATCH_COOKIE_KEY of at least 32 characters")
app.config.update(SESSION_COOKIE_NAME="__Host-ww", SESSION_COOKIE_SECURE=True,
                  SESSION_COOKIE_HTTPONLY=True, SESSION_COOKIE_SAMESITE="Strict", MAX_CONTENT_LENGTH=65536)
DB = Path(os.environ.get("WOIDWATCH_DATABASE", "woidwatch.sqlite")).resolve()

@contextmanager
def database():
    with closing(sqlite3.connect(DB, timeout=6)) as db, db:
        db.execute("BEGIN IMMEDIATE")
        yield db

with database() as db:
    db.executescript("""
        CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, name TEXT NOT NULL, expires INT NOT NULL);
        CREATE TABLE IF NOT EXISTS invites (hash TEXT PRIMARY KEY, expires INT NOT NULL);
        CREATE TABLE IF NOT EXISTS users (id BLOB PRIMARY KEY, name TEXT COLLATE NOCASE UNIQUE NOT NULL,
            credential BLOB UNIQUE NOT NULL, public_key BLOB NOT NULL, counter INT NOT NULL);
        CREATE TABLE IF NOT EXISTS challenges (id TEXT PRIMARY KEY, payload TEXT NOT NULL, expires INT NOT NULL);
        CREATE TABLE IF NOT EXISTS pairs (secret TEXT PRIMARY KEY, browser TEXT UNIQUE NOT NULL, code TEXT NOT NULL,
            approved TEXT, expires INT NOT NULL);
    """)

def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()

@app.before_request
def protect():
    if request.path not in ("/health","/","/account.js") and not app.config.get("GAME_READY",lambda:False)():abort(503)
    if request.path == "/session/minecraft/join":
        if request.headers.get("Origin") or not request.is_json:
            abort(403)
        return
    if request.method == "POST" and not request.path.startswith("/desktop/"):
        if request.headers.get("Origin") != ORIGIN or not request.is_json:
            abort(403)
    if request.path.startswith("/desktop/") and (request.headers.get("Origin") or not request.is_json):
        abort(403)

@app.after_request
def headers(response):
    response.headers.update({"Cache-Control":"no-store","Referrer-Policy":"no-referrer",
        "X-Content-Type-Options":"nosniff","Content-Security-Policy":
        "default-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"})
    return response

@app.get("/")
def index():
    return send_from_directory(Path(__file__).parent, "index.html")

@app.get("/account.js")
def script():
    return send_from_directory(Path(__file__).parent, "account.js")

@app.post("/desktop/start")
def start():
    token, browser, code = secrets.token_urlsafe(32), secrets.token_urlsafe(32), f"{secrets.randbelow(1000000):06}"
    with database() as db:
        db.execute("DELETE FROM pairs WHERE expires<=?",(time.time(),))
        if db.execute("SELECT count(*) FROM pairs").fetchone()[0]>=2000: abort(429)
        db.execute("INSERT INTO pairs VALUES(?,?,?,NULL,?)",(digest(token),digest(browser),code,time.time()+300))
    return jsonify(secret=token, browser=browser, code=code)

@app.post("/desktop/poll")
def poll():
    token=digest(str(request.json.get("secret","")))
    with database() as db:
        row=db.execute("SELECT approved FROM pairs WHERE secret=? AND expires>?",(token,time.time())).fetchone()
        if not row: abort(410)
        if row[0] is None:return jsonify(pending=True)
        db.execute("DELETE FROM pairs WHERE secret=?",(token,))
        session_key=secrets.token_urlsafe(32)
        db.execute("DELETE FROM sessions WHERE expires<=?",(time.time(),))
        db.execute("INSERT INTO sessions VALUES(?,?,?)",(digest(session_key),row[0],time.time()+30*86400))
        return jsonify(name=row[0],token=session_key)

@app.post("/begin")
def begin():
    body=request.json
    name=str(body.get("name",""))
    if not re.fullmatch(r"[a-zA-Z0-9_]{3,24}",name):abort(400)
    registration=body.get("mode")=="register"
    user=secrets.token_bytes(16)
    with database() as db:
        db.execute("DELETE FROM challenges WHERE expires<=?",(time.time(),))
        if db.execute("SELECT count(*) FROM challenges").fetchone()[0]>=2000:abort(429)
        if registration:
            invite=digest(str(body.get("invite","")))
            if not db.execute("SELECT 1 FROM invites WHERE hash=? AND expires>?",(invite,time.time())).fetchone():abort(403)
            opt=generate_registration_options(rp_id=RP,rp_name="WoidWatch",user_id=user,user_name=name,
                authenticator_selection=AuthenticatorSelectionCriteria(resident_key=ResidentKeyRequirement.PREFERRED,
                user_verification=UserVerificationRequirement.REQUIRED))
        else:
            invite=None
            found=db.execute("SELECT credential FROM users WHERE name=?",(name,)).fetchone()
            opt=generate_authentication_options(rp_id=RP,allow_credentials=[PublicKeyCredentialDescriptor(id=found[0] if found else secrets.token_bytes(32))],
                                                user_verification=UserVerificationRequirement.REQUIRED)
        key=secrets.token_urlsafe(32);session["challenge"]=key
        data=dict(name=name,register=registration,user=bytes_to_base64url(user),invite=invite,challenge=bytes_to_base64url(opt.challenge))
        db.execute("INSERT INTO challenges VALUES(?,?,?)",(key,json.dumps(data),time.time()+180))
    return app.response_class(options_to_json(opt),mimetype="application/json")

@app.post("/finish")
def finish():
    key=session.pop("challenge",None)
    with database() as db:
        row=db.execute("SELECT payload FROM challenges WHERE id=? AND expires>?",(key,time.time())).fetchone()
        db.execute("DELETE FROM challenges WHERE id=?",(key,))
    if not row:abort(403)
    data=json.loads(row[0]); credential=request.json
    checks=dict(credential=credential,expected_rp_id=RP,expected_origin=ORIGIN,
                expected_challenge=base64url_to_bytes(data["challenge"]),require_user_verification=True)
    try:
        with database() as db:
            if data["register"]:
                verified=verify_registration_response(**checks)
                if not db.execute("DELETE FROM invites WHERE hash=? AND expires>?",(data["invite"],time.time())).rowcount:abort(403)
                db.execute("INSERT INTO users VALUES(?,?,?,?,?)",(base64url_to_bytes(data["user"]),data["name"],
                    verified.credential_id,verified.credential_public_key,verified.sign_count))
            else:
                user=db.execute("SELECT credential,public_key,counter FROM users WHERE name=?",(data["name"],)).fetchone()
                if not user:abort(403)
                verified=verify_authentication_response(**checks,credential_public_key=user[1],credential_current_sign_count=user[2])
                if user[0]!=verified.credential_id:abort(403)
                db.execute("UPDATE users SET counter=? WHERE name=?",(verified.new_sign_count,data["name"]))
    except (WebAuthnException,ValueError,sqlite3.IntegrityError):abort(403)
    session.clear();session["name"]=data["name"];session["verified"]=time.time()
    return jsonify(name=data["name"])

@app.post("/approve")
def approve():
    if not session.get("name") or time.time()-session.get("verified",0)>180:abort(403)
    with database() as db:
        if not db.execute("UPDATE pairs SET approved=? WHERE browser=? AND code=? AND approved IS NULL AND expires>?",
                 (session["name"],digest(str(request.json.get("browser",""))),str(request.json.get("code","")),time.time())).rowcount:abort(403)
    return jsonify(ok=True)

@app.cli.command("invite")
def create_invite():
    token=secrets.token_urlsafe(24)
    with database() as db:db.execute("INSERT INTO invites VALUES(?,?)",(digest(token),time.time()+86400))
    print(token)

@app.get("/me")
def me():
    header=request.headers.get("Authorization","")
    if not header.startswith("Bearer "):abort(401)
    with database() as db:
        row=db.execute("SELECT name FROM sessions WHERE token=? AND expires>?",(digest(header[7:]),time.time())).fetchone()
    if not row:abort(401)
    with database() as db:
        account=db.execute("SELECT id FROM password_users WHERE name=?",(row[0],)).fetchone()
    return jsonify(name=row[0],id=account[0] if account else None)

@app.get("/health")
def health():
    with database() as db:db.execute("SELECT id FROM users LIMIT 0")
    return jsonify(status="ok",registration=bool(app.config.get("GAME_READY",lambda:False)()))

from passwords import install
install(app,database,digest)
from wardrobe import install as install_wardrobe
install_wardrobe(app,database,digest)

from game_login import install as install_game_login
install_game_login(app,database,digest)
