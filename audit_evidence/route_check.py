"""Static route checks: every local href/src in the pages resolves to a file, and every API path the
frontend calls matches a backend mapping (and the reverse). Usage: python3 audit_evidence/route_check.py"""
import os, re, glob
ST = "src/main/resources/static"
missing, hash_links = [], []
for page in glob.glob(ST + "/**/*.html", recursive=True):
    text = open(page, encoding="utf-8").read()
    for ref in re.findall(r'(?:href|src)="([^"]+)"', text):
        if ref.startswith(("http", "mailto:", "data:", "tel:")): continue
        if ref == "#" : hash_links.append(page); continue
        path = ref.split("#")[0].split("?")[0]
        if not path: continue
        full = os.path.normpath(os.path.join(ST, path.lstrip("/")) if path.startswith("/") else os.path.join(os.path.dirname(page), path))
        if not os.path.exists(full) and not os.path.exists(full + "index.html"):
            missing.append((page.replace(ST, ""), ref))
# links built in JS
for js in glob.glob(ST + "/js/**/*.js", recursive=True):
    if "/landing/" in js: continue
    for ref in re.findall(r'["`](/(?:student|admin)/[a-z0-9-]+\.html)', open(js, encoding="utf-8").read()):
        if not os.path.exists(ST + ref): missing.append((js.replace(ST, ""), ref))
print("missing local references:", len(missing)); [print("  ", m) for m in missing]
print("bare # links:", len(hash_links))

# backend mappings
routes = []
for f in glob.glob("src/main/java/**/*Controller.java", recursive=True):
    s = open(f).read()
    base = re.search(r'@RequestMapping\("([^"]*)"\)', s)
    base = base.group(1) if base else ""
    for m in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\((?:path = |value = )?\{?"?([^"\),]*)"?)?', s):
        routes.append((m.group(1).upper(), (base + (m.group(2) or "")).replace("/api/v1", "")))
def norm(p): return re.sub(r"\{[^}]+\}", "{}", p.split("?")[0]).rstrip("/")
backend = {norm(p) for _, p in routes}
called = set()
for js in glob.glob(ST + "/js/**/*.js", recursive=True):
    if "/landing/" in js: continue
    s = open(js, encoding="utf-8").read()
    for m in re.finditer(r'api\.(?:get|post|put|patch|delete|upload|postForm|postFile|putFile|uploadFile)\(\s*[`"]([^`"]+)[`"]', s):
        p = re.sub(r"\$\{[^}]+\}", "{}", m.group(1))
        called.add((norm(p), js.replace(ST, "")))
def matches(p):
    if p in backend: return True
    # a call that ends with a dynamic suffix, e.g. "/notes/{}" or "/x?y"
    return any(re.fullmatch(re.escape(b).replace(r"\{\}", r"[^/]+"), p) or re.fullmatch(re.escape(p).replace(r"\{\}", r"[^/]+"), b) for b in backend)
unknown = sorted({(p, f) for p, f in called if not matches(p)})
print("frontend calls without a backend route:", len(unknown)); [print("  ", u) for u in unknown]
used = {p for p, _ in called}
unused = sorted(b for b in backend if not any(re.fullmatch(re.escape(b).replace(r"\{\}", r"[^/]+"), u) or b == u or re.fullmatch(re.escape(u).replace(r"\{\}", r"[^/]+"), b) for u in used))
print("backend routes no page calls:", len(unused)); [print("  ", u) for u in unused]
