#!/usr/bin/env python3
# Samples for the draft's choice of event time (the work package's test matrix T1-T16): records whose time is in a
# container's header, in the record itself, in both, in some records only, beside times that aren't the event's
# (an object's creation or expiry), or written as numbers (Unix epoch, Windows FILETIME).
#
# Seeded: the same files every run. Usage: python3 generate.py [out_dir]
# TestDraftEventTime reads each file and states, beside it, what the draft should choose and why.
import json
import os
import random
import sys
from datetime import datetime, timedelta, timezone

rnd = random.Random(20261107)
START = datetime(2026, 11, 6, 9, 0, 0, tzinfo=timezone.utc)
LINES = 40
KINDS = ["APPFW_SQL", "APPFW_XSS", "APPFW_STARTURL"]
USERS = ["alice", "bob", "carol", "dave"]


def times():
    t = START
    for _ in range(LINES):
        t += timedelta(seconds=rnd.uniform(1.0, 30.0))
        yield t


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%S.") + "%03d" % (t.microsecond // 1000) + "+00:00"


def cef_rt(t):
    # CEF's rt in its date form: MMM dd yyyy HH:mm:ss.
    return t.strftime("%b %d %Y %H:%M:%S")


def epoch_ms(t):
    return str(int(t.timestamp() * 1000))


def filetime(t):
    # Windows FILETIME: 100 ns ticks since 1601-01-01.
    return str(int((t.timestamp() + 11_644_473_600) * 10_000_000))


def relayed(t, body):
    # rsyslog's RFC 3339 header before a CEF line; the relay's time is a little after the device's.
    return "<134>%s relay1 ns: %s" % (iso(t + timedelta(seconds=rnd.uniform(0.5, 3.0))), body)


def cef(kind, ext):
    return "CEF:0|Citrix|NetScaler|NS13.1|APPFW|%s|6|%s" % (kind, " ".join(ext))


def base_ext():
    return ["src=10.0.%d.%d" % (rnd.randrange(0, 4), rnd.randrange(1, 250)),
            "act=%s" % rnd.choice(["blocked", "not blocked"])]


def t1():
    # CEF after rsyslog's ISO header, no time of its own: the header's time.
    return [relayed(t, cef(rnd.choice(KINDS), base_ext())) for t in times()]


def t2():
    # rt on every record: rt, falling back to the header's time.
    return [relayed(t, cef(rnd.choice(KINDS), ["rt=" + cef_rt(t)] + base_ext())) for t in times()]


def t3():
    # rt on 40%: rt where given, else the header's time (not the stream's received time).
    out = []
    for n, t in enumerate(times()):
        ext = (["rt=" + cef_rt(t)] if n % 5 in (0, 2) else []) + base_ext()
        out.append(relayed(t, cef(rnd.choice(KINDS), ext)))
    return out


def t4():
    # rt in epoch milliseconds on some: a number and a pattern can't be one format, so the header's time for all.
    out = []
    for n, t in enumerate(times()):
        ext = (["rt=" + epoch_ms(t)] if n % 2 == 0 else []) + base_ext()
        out.append(relayed(t, cef(rnd.choice(KINDS), ext)))
    return out


def t5():
    # JSON: event_time, an account's creation (2019) and a token's expiry (2027): event_time.
    out = []
    for t in times():
        out.append(json.dumps({"event_time": iso(t), "user": rnd.choice(USERS), "action": "token_issued",
                               "created_at": "2019-03-%02dT08:00:00Z" % rnd.randrange(1, 28),
                               "expires": "2027-01-%02dT00:00:00Z" % rnd.randrange(1, 28)}))
    return out


def t6():
    # JSON with created_at only, close together: created_at, noted as a name that may not be the event's.
    return [json.dumps({"created_at": iso(t), "user": rnd.choice(USERS), "action": "comment"}) for t in times()]


def t7():
    # Two kinds: file_access has event_time; session has start and end, both close: a choice for the agent.
    out = []
    for t in times():
        if rnd.random() < 0.5:
            out.append(json.dumps({"type": "file_access", "event_time": iso(t), "user": rnd.choice(USERS),
                                   "file": "/srv/share/%d.docx" % rnd.randrange(100)}))
        else:
            out.append(json.dumps({"type": "session", "start": iso(t - timedelta(minutes=rnd.randrange(1, 50))),
                                   "end": iso(t), "user": rnd.choice(USERS)}))
    return out


def t8():
    # kv after an RFC 3164 header (no year), with date= and time=: the record's own, joined.
    out = []
    for t in times():
        out.append("<134>%s fw1 date=%s time=%s devname=fw1 action=%s srcip=10.1.1.%d" % (
            t.strftime("%b %e %H:%M:%S"), t.strftime("%Y-%m-%d"), t.strftime("%H:%M:%S"),
            rnd.choice(["accept", "deny"]), rnd.randrange(1, 250)))
    return out


def t9():
    # kv with ts and logged_at, both event-time names, seconds apart: a choice for the agent.
    out = []
    for t in times():
        out.append("ts=%s logged_at=%s user=%s action=%s" % (
            iso(t), iso(t + timedelta(seconds=rnd.uniform(0.1, 4.0))), rnd.choice(USERS),
            rnd.choice(["read", "write"])))
    return out


def t10():
    # Backfilled: every timestamp years before the stream arrives; still the event's time.
    return [json.dumps({"timestamp": iso(t - timedelta(days=2500)), "user": rnd.choice(USERS),
                        "action": "login"}) for t in times()]


def t11():
    # Mixed stream through one relay: CEF with rt (its own time), and plain host lines with none.
    out = []
    for t in times():
        if rnd.random() < 0.5:
            out.append(relayed(t, cef(rnd.choice(KINDS), ["rt=" + cef_rt(t)] + base_ext())))
        else:
            # RFC 3164 (no year): rsyslog's RFC 3339 header on a plain line isn't read as syslog yet.
            out.append("<30>%s host7 sshd[%d]: Accepted publickey for %s from 10.2.0.%d" % (
                t.strftime("%b %e %H:%M:%S"), rnd.randrange(1000, 9999), rnd.choice(USERS), rnd.randrange(1, 250)))
    return out


def t12():
    # No time anywhere.
    return ["user=%s action=%s bytes=%d" % (rnd.choice(USERS), rnd.choice(["get", "put"]), rnd.randrange(1, 9999))
            for _ in range(LINES)]


def t13():
    # CEF with fileCreateTime (the file's, months back) and rt: rt; fileCreateTime is data.
    out = []
    for t in times():
        created = t - timedelta(days=rnd.randrange(30, 400))
        out.append(relayed(t, cef("APPFW_FILEUPLOAD", ["rt=" + cef_rt(t), "fileCreateTime=" + cef_rt(created),
                                                       "fname=f%d.pdf" % rnd.randrange(100)] + base_ext())))
    return out


def t15():
    # A Windows event as JSON: TimeCreated, and the account's lastLogon as a FILETIME: TimeCreated; lastLogon is an
    # object's time, kept as data.
    out = []
    for t in times():
        last = t - timedelta(days=rnd.randrange(1, 90))
        out.append(json.dumps({"TimeCreated": iso(t), "EventID": 4738, "TargetUserName": rnd.choice(USERS),
                               "lastLogon": filetime(last)}))
    return out


def t16():
    # The event's time itself as a FILETIME: windows_filetime.
    return [json.dumps({"EventTime": filetime(t), "EventID": 4624, "TargetUserName": rnd.choice(USERS)})
            for t in times()]


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    for name, fn in [("t01_cef_header_only", t1), ("t02_cef_rt_every", t2), ("t03_cef_rt_some", t3),
                     ("t04_cef_rt_epoch_some", t4), ("t05_json_object_times", t5), ("t06_json_created_only", t6),
                     ("t07_json_two_kinds", t7), ("t08_kv_date_time_3164", t8), ("t09_kv_two_event_times", t9),
                     ("t10_json_backfilled", t10), ("t11_mixed_relay", t11), ("t12_no_time", t12),
                     ("t13_cef_file_time", t13), ("t15_json_filetime_object", t15),
                     ("t16_json_filetime_event", t16)]:
        with open(os.path.join(out, name + ".log"), "w", newline="\n") as f:
            f.write("\n".join(fn()) + "\n")


if __name__ == "__main__":
    main()
