import json, urllib.request, urllib.error, http.cookiejar, urllib.parse
PASSWORD = "blue-Lamp-42"
class Client:
    def __init__(self, base="http://localhost:8081"):
        self.base = base.rstrip("/") + "/api/v1"
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
        r = urllib.request.Request(self.base + path, data=data, method=method, headers=h)
        try:
            with self.op.open(r, timeout=30) as resp:
                raw = resp.read()
                try: return resp.status, json.loads(raw)
                except ValueError: return resp.status, None
        except urllib.error.HTTPError as e:
            raw = e.read()
            try: return e.code, json.loads(raw)
            except Exception: return e.code, None
    def login(self, email):
        self.call("GET", "/auth/csrf")
        s, _ = self.call("POST", "/auth/login", {"email": email, "password": PASSWORD})
        assert s == 200, (email, s)
        return self

