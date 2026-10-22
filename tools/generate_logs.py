#!/usr/bin/env python3
"""
Generates a rolling 30 days of synthetic request logs (5-minute cadence, 5 integrations) through
the current time. The final hour follows the supplied sample's status pattern, so the live view
still produces the original Green/Amber/Red picture.

Usage: python3 tools/generate_logs.py            (deterministic, seed 42)
Output: src/main/resources/sample/integration_logs_month.json
"""
import json, random
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
rng = random.Random(42)
STEP = timedelta(minutes=5)
LATEST = datetime.now().replace(second=0, microsecond=0)
LATEST -= timedelta(minutes=LATEST.minute % 5)
END = LATEST - timedelta(minutes=55)       # supplied final hour ends at LATEST
START = END - timedelta(days=30)
ORIGIN = START.replace(hour=0, minute=0, second=0, microsecond=0)

def biz(ts):                               # weekday, 09:00-17:59
    return ts.weekday() < 5 and 9 <= ts.hour < 18

def at_day(day, hour=0, minute=0):
    return ORIGIN + timedelta(days=day, hours=hour, minutes=minute)

def between(ts, a, b):
    return a <= ts < b

def ok(mu, sd):   return ("success", 200, max(40, round(rng.gauss(mu, sd))))
def err(code, mu, sd): return ("error", code, max(60, round(rng.gauss(mu, sd))))
def timeout():    return ("timeout", None, 30000)

def ramp(ts, start, days, lo, hi):         # linear ramp lo -> hi over `days`, starting at `start`
    d = (ts - start).total_seconds() / 86400
    if d < 0: return lo
    return lo + (hi - lo) * min(1.0, d / days)

def slack(ts):
    if between(ts, at_day(13, 14), at_day(13, 15, 35)):             # short full outage
        return err(503, 300, 30)
    return err(500, 310, 30) if rng.random() < 0.006 else ok(140, 9)

def adp(ts):                                                       # gradual degradation from day 10
    p = ramp(ts, at_day(10), 20, 0.02, 0.45)
    return err(500, 410, 20) if rng.random() < p else ok(222, 12)

def docusign(ts):                                                  # quota pressure in business hours
    if ts >= at_day(10):
        p = ramp(ts, at_day(10), 20, 0.15, 0.35) if biz(ts) else 0.02
    else:
        p = 0.01
    if rng.random() < p: return err(429, 850, 40)
    return err(500, 800, 40) if rng.random() < 0.002 else ok(680, 25)

def workday(ts):
    if between(ts, at_day(22, 1), at_day(22, 6)):                   # silence gap (no logs at all)
        return None
    if between(ts, at_day(5, 8), at_day(5, 11)):                    # expired token
        return err(401, 90, 8)
    if rng.random() < 0.004: return err(503, 280, 25)
    mu = 285 if between(ts, at_day(16), at_day(17)) else 118         # one-day latency regression
    return ok(mu, 8 if mu < 200 else 20)

def greenhouse(ts):                                                # intermittent timeouts, worsening
    p = ramp(ts, at_day(15), 15, 0.03, 0.55)
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
for name, _ in GENERATORS:
    sample = [r for r in supplied if r["integration"] == name]
    for index, row in enumerate(sample):
        shifted = dict(row)
        shifted["timestamp"] = (END + STEP * index).strftime("%Y-%m-%dT%H:%M:%S")
        rows.append(shifted)

out = ROOT / "src/main/resources/sample/integration_logs_month.json"
with out.open("w") as f:
    f.write('{\n  "logs": [\n')
    f.write(",\n".join("    " + json.dumps(r) for r in rows))
    f.write("\n  ]\n}\n")
print(f"wrote {len(rows)} entries to {out} ({out.stat().st_size/1e6:.1f} MB)")
