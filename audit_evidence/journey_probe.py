"""Cross-role journey and negative-input probe on a LOCAL test server with synthetic accounts.
Usage: python3 audit_evidence/journey_probe.py http://localhost:8081"""
import json, sys, datetime
from decimal import Decimal, ROUND_HALF_UP
sys.path.insert(0, "audit_evidence")
from perm_probe_client import Client  # noqa

out = []
def check(name, ok, detail=""):
    out.append((name, ok)); print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail and not ok else ""))

admin = Client(sys.argv[1]).login("admin@university.example")
st = Client(sys.argv[1]).login("23cse001@university.example")
s, me = st.call("GET", "/students/me")
s, found = admin.call("GET", "/admin/students?q=" + me["rollNo"])
rows = found.get("content", found) if isinstance(found, dict) else found
sid = next(x["id"] for x in rows if x.get("rollNo") == me["rollNo"])
s, a = st.call("GET", "/attendance/me"); subj = {"id": a["subjects"][0]["subjectId"], "code": a["subjects"][0]["subjectCode"]}
print("student", me.get("rollNo"), "section", me.get("section"), "subject", subj["id"], subj.get("code"))

# 1 notice: admin publishes, student sees it
title = f"Audit notice {datetime.datetime.now():%H%M%S} <b>&</b>"
s, n = admin.call("POST", "/admin/notices", {"title": title, "body": "Synthetic audit notice.", "category": "GENERAL"})
check("admin creates a notice", s in (200, 201), f"{s} {n}")
if s in (200, 201) and not n.get("published"):
    s2, _ = admin.call("POST", f"/admin/notices/{n['id']}/publish"); check("admin publishes the notice", s2 in (200, 204), s2)
s, lst = st.call("GET", "/notices")
items = lst.get("content", lst.get("notices", lst)) if isinstance(lst, dict) else lst
check("student sees the published notice, text unchanged", any(i.get("title") == title for i in items), title)
s, _ = admin.call("POST", "/admin/notices", {"title": "x" * 201, "body": "b", "category": "GENERAL"})
check("notice title over 200 characters is refused (400)", s == 400, s)

# 2 attendance: admin marks one class, student's counts move by one
def att():
    s, a = st.call("GET", "/attendance/me")
    row = next((x for x in a["subjects"] if x["subjectId"] == subj["id"]), None)
    return a, row
a0, r0 = att()
day = (datetime.date.today() - datetime.timedelta(days=1 + datetime.datetime.now().minute)).isoformat()  # a fresh class each run
s, res = admin.call("PUT", "/admin/attendance", {"subjectId": subj["id"], "section": me["section"], "classDate": day, "periodNo": 7, "entries": [{"studentId": sid, "status": "ABSENT"}]})
check("admin saves an attendance sheet", s == 200, f"{s} {res}")
a1, r1 = att()
check("student's held count rises by one", (r1 or {}).get("classesHeld", 0) == (r0 or {}).get("classesHeld", 0) + 1, f"{r0} -> {r1}")
held = sum(x["classesHeld"] for x in a1["subjects"]); attd = sum(x["classesAttended"] for x in a1["subjects"])
if held and a1.get("overallPercent") is not None:
    mine = (Decimal(attd) * 100 / Decimal(held)).quantize(Decimal("0.01"), ROUND_HALF_UP)
    check("overall attendance % recomputed independently matches", mine == Decimal(str(a1["overallPercent"])), f"{mine} vs {a1['overallPercent']}")
future = (datetime.date.today() + datetime.timedelta(days=3)).isoformat()
s, _ = admin.call("PUT", "/admin/attendance", {"subjectId": subj["id"], "section": me["section"], "classDate": future, "periodNo": 7, "entries": [{"studentId": sid, "status": "PRESENT"}]})
check("attendance for a future date is refused (400)", s == 400, s)
s, _ = admin.call("PUT", "/admin/attendance", {"subjectId": subj["id"], "section": me["section"], "classDate": day, "periodNo": 13, "entries": [{"studentId": sid, "status": "PRESENT"}]})
check("period 13 is refused (400)", s == 400, s)

# 3 marks: create a published assessment, enter marks, student sees them; invalid marks refused
s, asmt = admin.call("POST", "/admin/assessments", {"subjectId": subj["id"], "name": f"Audit test {datetime.datetime.now():%H%M%S}", "type": "INTERNAL", "maxMarks": 20, "heldOn": day, "academicYear": "2026-27", "published": True})
check("admin creates an assessment", s in (200, 201), f"{s} {asmt}")
if s in (200, 201):
    aid = asmt["id"]
    for marks, label in [(25, "above maximum"), (-1, "negative"), (12.345, "three decimals")]:
        s, _ = admin.call("PUT", f"/admin/assessments/{aid}/marks", {"entries": [{"studentId": sid, "marksObtained": marks, "absent": False}]})
        check(f"marks {label} ({marks}) refused (400)", s == 400, s)
    s, _ = admin.call("PUT", f"/admin/assessments/{aid}/marks", {"entries": [{"studentId": sid, "marksObtained": 18.5, "absent": False}]})
    check("valid marks saved", s == 200, s)
    s, m = st.call("GET", "/marks/me")
    text = json.dumps(m)
    check("student sees 18.5 of 20 for the new assessment", '18.5' in text and asmt["name"] in text, text[:200])
    s, _ = admin.call("PUT", f"/admin/assessments/{aid}/marks", {"entries": [{"studentId": 999999, "marksObtained": 5, "absent": False}]})
    check("marks for a student not in the class refused (400/404)", s in (400, 404), s)

# 4 CGPA recomputed independently from the student's own semester results
s, r = st.call("GET", "/results/me")
counted = [x for x in r.get("semesters", []) if x.get("resultStatus") != "WITHHELD"]
sem = [x for x in counted if x.get("sgpa") is not None]
if any(x.get("sgpa") is None for x in counted):
    check("no CGPA while a counted semester has no SGPA (documented rule, docs/08 4.8)", r["cgpa"] is None, r["cgpa"])
    first = counted[0]
    check("CGPA to date after semester 1 equals its SGPA", first["sgpa"] is None or Decimal(str(first["cgpaToDate"])) == Decimal(str(first["sgpa"])), first)
elif sem:
    num = sum(Decimal(str(x["sgpa"])) * x["creditsRegistered"] for x in sem); den = sum(x["creditsRegistered"] for x in sem)
    mine = (num / den).quantize(Decimal("0.01"), ROUND_HALF_UP)
    check("CGPA recomputed independently matches", mine == Decimal(str(r["cgpa"])), f"{mine} vs {r['cgpa']}")
else:
    print("no published semesters for this student")

# 5 rate limit on login (brute force)
bad = Client(sys.argv[1]); codes = []
for i in range(8):
    bad.call("GET", "/auth/csrf"); s, _ = bad.call("POST", "/auth/login", {"email": "23cse004@university.example", "password": "wrong-pass-" + str(i)}); codes.append(s)
check("repeated wrong passwords end in a lock or rate limit (423/429)", any(c in (423, 429) for c in codes), codes)

print(f"\n{len(out)} checks, {sum(1 for _, ok in out if not ok)} failures")
