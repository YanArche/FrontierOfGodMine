"""Offline HTTPS fixture with a virtual platform authenticator; no public DNS required."""
import os
from pathlib import Path
import secrets
import sys
import tempfile
import time
from urllib.parse import urlsplit

from playwright.sync_api import sync_playwright

with tempfile.TemporaryDirectory() as temp:
    os.environ["WOIDWATCH_DATABASE"]=str(Path(temp)/"test.sqlite")
    os.environ["WOIDWATCH_COOKIE_KEY"]=secrets.token_urlsafe(48)
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"accounts"))
    from service import app, database, digest, ORIGIN
    app.config["GAME_READY"]=lambda:True
    client=app.test_client()
    invite=secrets.token_urlsafe(24)
    with database() as db:
        db.execute("INSERT INTO invites VALUES(?,?)",(digest(invite),time.time()+600))
    pair=client.post("/desktop/start",json={}).json
    with sync_playwright() as playwright:
        edge=os.environ.get("WW_TEST_BROWSER")
        browser=playwright.chromium.launch(executable_path=edge,headless=True)
        page=browser.new_page()
        def route(r):
            q=r.request
            headers={"Origin":q.headers["origin"]} if "origin" in q.headers else {}
            res=client.open(urlsplit(q.url).path,method=q.method,data=q.post_data,headers=headers,
                            content_type=q.headers.get("content-type"),base_url=ORIGIN)
            r.fulfill(status=res.status_code,body=res.data,content_type=res.content_type)
        page.route(ORIGIN+"/**",route)
        cdp=page.context.new_cdp_session(page)
        cdp.send("WebAuthn.enable")
        cdp.send("WebAuthn.addVirtualAuthenticator",{"options":{"protocol":"ctap2","transport":"internal","hasResidentKey":True,
                    "hasUserVerification":True,"isUserVerified":True,"automaticPresenceSimulation":True}})
        page.goto(ORIGIN+"/#device="+pair["browser"])
        page.locator("#name").fill("FrontierTester")
        page.locator("#invite").fill(invite)
        page.locator("#register").click()
        page.locator("#confirm").wait_for(state="visible")
        page.locator("#code").fill(pair["code"])
        page.locator("#approve").click()
        page.get_by_text("Вход подтверждён.",exact=False).wait_for()
        token=client.post("/desktop/poll",json={"secret":pair["secret"]}).json["token"]
        assert client.get("/me",headers={"Authorization":"Bearer "+token}).json["name"]=="FrontierTester"
        assert client.post("/desktop/poll",json={"secret":pair["secret"]}).status_code==410
        page.goto(ORIGIN)
        page.locator("#name").fill("FrontierTester")
        page.locator("#login").click()
        page.get_by_text("Вы вошли как FrontierTester").wait_for()
        browser.close()
    print("PASS: invitation, passkey registration and login, desktop confirmation, authenticated session, single-use pickup")
