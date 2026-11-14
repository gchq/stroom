# Synthetic Windows security events for Stroom AI's meta source type e2e test (TestE2eMetaSourceTypes). Not real
# data: hosts' events as JSON lines, one part a host, aggregated by a proxy into streams of several parts. Each part
# carries the receipt headers its host sent: a server's WindowsServerInstalledServedRoles lists its roles; a
# workstation sends none. Two of the servers are domain controllers (their roles hold Active Directory Domain Services).
#
# Usage: python3 windows-meta-generator.py <dir>
# Writes <dir>/windows-meta.json: {"streams": [[{"meta": {...}, "lines": [...]}, ...], ...]}, a stream a list of
# parts. Seeded, so the file doesn't change from run to run.
import json, random, sys, datetime as dt
out = sys.argv[1]
r = random.Random(6100)
users = ["anja.michel", "li.wei", "omar.haddad", "priya.nair", "sam.taylor", "svc-backup"]
ROLES = "WindowsServerInstalledServedRoles"
# (host, roles or None for a workstation)
hosts = [("dc01.corp.example", "DNS Server, Active Directory Domain Services, File Server"),
         ("fs01.corp.example", "File Server"),
         ("ws-0142.corp.example", None),
         ("web01.corp.example", "Web Server (IIS)"),
         ("ws-0187.corp.example", None),
         ("dns02.corp.example", "DNS Server, File Server"),
         ("dc02.corp.example", "Active Directory Domain Services, DNS Server"),
         ("ws-0203.corp.example", None)]
DC_KINDS = [("4624", "An account was successfully logged on."), ("4625", "An account failed to log on."),
            ("4768", "A Kerberos authentication ticket (TGT) was requested."),
            ("4769", "A Kerberos service ticket was requested.")]
OTHER_KINDS = [("4624", "An account was successfully logged on."), ("4625", "An account failed to log on."),
               ("4688", "A new process has been created."), ("4663", "An attempt was made to access an object.")]

def ts(t):
    return t.strftime("%Y-%m-%dT%H:%M:%S.") + f"{t.microsecond // 1000:03d}Z"

def part(host, roles, t, count):
    kinds = DC_KINDS if roles and "Active Directory Domain Services" in roles else OTHER_KINDS
    lines = []
    for _ in range(count):
        t += dt.timedelta(milliseconds=r.randint(100, 5000))
        event_id, message = r.choice(kinds)
        lines.append(json.dumps({"EventTime": ts(t), "Hostname": host, "EventID": event_id, "Channel": "Security",
                                 "Provider": "Microsoft-Windows-Security-Auditing",
                                 "TargetUserName": r.choice(users),
                                 "IpAddress": f"10.1.{r.randint(0, 9)}.{r.randint(1, 254)}", "Message": message}))
    meta = {"Feed": "E2E-WALK-WINDOWS", "RemoteHost": host}
    if roles:
        meta[ROLES] = roles
    return {"meta": meta, "lines": lines}

streams = []
t = dt.datetime(2026, 11, 14, 8, 0, 0)
# Two proxy aggregates, each a part a host, the hosts in a different order.
for order in [hosts[:5], hosts[5:] + hosts[1:3]]:
    streams.append([part(host, roles, t, r.randint(15, 25)) for host, roles in order])
    t += dt.timedelta(hours=1)
with open(f"{out}/windows-meta.json", "w") as f:
    json.dump({"streams": streams}, f, indent=1)
    f.write("\n")
