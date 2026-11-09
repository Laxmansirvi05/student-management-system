"""Permission probe for Campus Companion on a LOCAL test server with synthetic demo accounts.
Usage: python3 audit_evidence/perm_probe.py http://localhost:8081
Checks: anonymous -> 401 on every API path; student -> 403 on admin paths; student A cannot read,
change or delete student B's records (IDOR); writes without a CSRF token are refused."""
import json, re, sys, urllib.request, urllib.error, http.cookiejar, urllib.parse

BASE = sys.argv[1].rstrip("/") + "/api/v1"
PASSWORD = "blue-Lamp-42"  # test value from PasswordChangeIT, local synthetic accounts only

class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
    def token(self):
        for c in self.jar:
            if c.name == "XSRF-TOKEN":
                return urllib.parse.unquote(c.value)
    def call(self, method, path, body=None, csrf=True):
        h = {"Accept": "application/json"}
        if csrf and self.token():
            h["X-XSRF-TOKEN"] = self.token()
        data = None
        if body is not None:
            data = json.dumps(body).encode(); h["Content-Type"] = "application/json"
        r = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
        try:
            with self.op.open(r, timeout=30) as resp:
                raw = resp.read(); return resp.status, (json.loads(raw) if raw[:1] in b"{[" else None)
        except urllib.error.HTTPError as e:
            raw = e.read()
            try: return e.code, json.loads(raw)
            except Exception: return e.code, None
    def login(self, email):
        self.call("GET", "/auth/csrf")
        s, _ = self.call("POST", "/auth/login", {"email": email, "password": PASSWORD})
        assert s == 200, (email, s)
        return self

paths = re.findall(r"^  (/[^:]+):", open("docs/openapi.yaml").read(), re.M)
def concrete(p):
    return re.sub(r"\{[^}]+\}", "1", p)
results, failures = [], []
def check(name, got, ok):
    results.append((name, got, ok))
    if not ok: failures.append((name, got))

anon = Client(); anon.call("GET", "/auth/csrf")
public = {"/auth/csrf", "/auth/login", "/auth/logout", "/notes/shared/1"}
for p in paths:
    c = concrete(p)
    if any(c.startswith(x) for x in public):
        continue
    s, _ = anon.call("GET", c)
    check(f"anonymous GET {c}", s, s in (401, 405))

a = Client().login("23cse001@university.example")
b = Client().login("23cse002@university.example")
for p in paths:
    if p.startswith("/admin"):
        s, _ = a.call("GET", concrete(p))
        check(f"student GET {concrete(p)}", s, s in (403, 405))

# Records of student B
created = {}
s, n = b.call("POST", "/notes", {"title": "B private note", "content": "<p>secret of B</p>"}); created["note"] = (s, n)
s, t = b.call("POST", "/planner/tasks", {"title": "B task", "dueDate": None}); created["task"] = (s, t)
s, c = b.call("POST", "/complaints", {"type": "PRIVATE", "category": "OTHER", "title": "B complaint", "description": "Private complaint by student B for the probe."}); created["complaint"] = (s, c)
s, ch = b.call("POST", "/assistant/chat", {"message": "B question", "mode": "quick"}); created["chat"] = (s, ch)
print("created:", {k: v[0] for k, v in created.items()})

def idor(label, method, path, body=None):
    s, _ = a.call(method, path, body)
    check(f"IDOR {label}: A {method} {path}", s, s in (403, 404))

if created["note"][0] in (200, 201):
    nid = created["note"][1]["id"]
    idor("note", "GET", f"/notes/{nid}"); idor("note", "PATCH", f"/notes/{nid}", {"title": "x"}); idor("note", "DELETE", f"/notes/{nid}")
if created["task"][0] in (200, 201):
    tid = created["task"][1]["id"]
    idor("task", "PUT", f"/planner/tasks/{tid}", {"title": "x"}); idor("task", "POST", f"/planner/tasks/{tid}/complete"); idor("task", "DELETE", f"/planner/tasks/{tid}")
if created["complaint"][0] in (200, 201):
    cid = created["complaint"][1]["id"]
    idor("complaint", "GET", f"/complaints/{cid}"); idor("complaint", "DELETE", f"/complaints/{cid}")
if created["chat"][0] == 200:
    conv = created["chat"][1]["conversation"]["id"]
    idor("chat", "GET", f"/assistant/conversations/{conv}/messages"); idor("chat", "DELETE", f"/assistant/conversations/{conv}")
    s, _ = a.call("POST", "/assistant/chat", {"conversationId": conv, "message": "hijack", "mode": "quick"})
    check("IDOR chat: A continues B's chat", s, s in (403, 404))
s, me = b.call("GET", "/students/me")
if s == 200 and me.get("id"):
    for path in [f"/students/{me['id']}", f"/admin/students/{me['id']}"]:
        s2, _ = a.call("GET", path); check(f"IDOR profile: A GET {path}", s2, s2 in (403, 404, 405))

# CSRF
s, _ = a.call("POST", "/planner/tasks", {"title": "no csrf"}, csrf=False)
check("CSRF: write without token", s, s == 403)

print(f"\n{len(results)} checks, {len(failures)} failures")
for f in failures: print("FAIL", f)
