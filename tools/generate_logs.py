#!/usr/bin/env python3
"""
Generates 30 days of synthetic request logs (5-minute cadence, 5 integrations) in the same JSON
format as the supplied sample. The last hour (2026-08-04 09:00-09:55) is the supplied sample
verbatim, so the live view still produces the original Green/Amber/Red picture.

Usage: python3 tools/generate_logs.py            (deterministic, seed 42)
Output: src/main/resources/sample/integration_logs_month.json
"""
import json, random
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
rng = random.Random(42)
START = datetime(2026, 7, 5, 10, 0)
END = datetime(2026, 8, 4, 9, 0)          # exclusive; supplied hour is appended after this
STEP = timedelta(minutes=5)

def biz(ts):                               # weekday, 09:00-17:59
    return ts.weekday() < 5 and 9 <= ts.hour < 18

def between(ts, a, b):
    return datetime.fromisoformat(a) <= ts < datetime.fromisoformat(b)

def ok(mu, sd):   return ("success", 200, max(40, round(rng.gauss(mu, sd))))
def err(code, mu, sd): return ("error", code, max(60, round(rng.gauss(mu, sd))))
def timeout():    return ("timeout", None, 30000)

def ramp(ts, start, days, lo, hi):         # linear ramp lo -> hi over `days`, starting at `start`
    d = (ts - datetime.fromisoformat(start)).total_seconds() / 86400
    if d < 0: return lo
    return lo + (hi - lo) * min(1.0, d / days)

def slack(ts):
    if between(ts, "2026-07-18T14:00", "2026-07-18T15:35"):        # short full outage
        return err(503, 300, 30)
    return err(500, 310, 30) if rng.random() < 0.006 else ok(140, 9)

def adp(ts):                                                       # gradual degradation from 15 Jul
    p = ramp(ts, "2026-07-15T00:00", 20, 0.02, 0.45)
    return err(500, 410, 20) if rng.random() < p else ok(222, 12)

def docusign(ts):                                                  # quota pressure in business hours
    if ts >= datetime(2026, 7, 15):
        p = ramp(ts, "2026-07-15T00:00", 20, 0.15, 0.35) if biz(ts) else 0.02
    else:
        p = 0.01
    if rng.random() < p: return err(429, 850, 40)
    return err(500, 800, 40) if rng.random() < 0.002 else ok(680, 25)

def workday(ts):
    if between(ts, "2026-07-27T01:00", "2026-07-27T06:00"):        # silence gap (no logs at all)
        return None
    if between(ts, "2026-07-10T08:00", "2026-07-10T11:00"):        # expired token
        return err(401, 90, 8)
    if rng.random() < 0.004: return err(503, 280, 25)
    mu = 285 if between(ts, "2026-07-21T00:00", "2026-07-22T00:00") else 118   # one-day latency regression
    return ok(mu, 8 if mu < 200 else 20)

def greenhouse(ts):                                                # intermittent timeouts, worsening
    p = ramp(ts, "2026-07-20T00:00", 15, 0.03, 0.55)
    if rng.random() < p:
        return timeout() if rng.random() < 0.85 else err(500, 340, 25)
    return ok(200, 8)

GENERATORS = [("Slack Notifications", slack), ("ADP Payroll Sync", adp), ("DocuSign Envelope API", docusign),
              ("Workday Directory Sync", workday), ("Greenhouse ATS Sync", greenhouse)]

def entry(name, ts, res):
    status, code, lat = res
    return {"integration": name, "timestamp": ts.strftime("%Y-%m-%dT%H:%M:%S"),
            "status": status, "status_code": code, "latency_ms": lat}

rows = []
for name, gen in GENERATORS:
    ts = START
    while ts < END:
        res = gen(ts)
        if res is not None:
            rows.append(entry(name, ts, res))
        ts += STEP

supplied = json.loads((ROOT / "src/main/resources/sample/integration_logs.json").read_text())["logs"]
for name, _ in GENERATORS:                                         # append the supplied final hour verbatim
    rows.extend(r for r in supplied if r["integration"] == name)

out = ROOT / "src/main/resources/sample/integration_logs_month.json"
with out.open("w") as f:
    f.write('{\n  "logs": [\n')
    f.write(",\n".join("    " + json.dumps(r) for r in rows))
    f.write("\n  ]\n}\n")
print(f"wrote {len(rows)} entries to {out} ({out.stat().st_size/1e6:.1f} MB)")
