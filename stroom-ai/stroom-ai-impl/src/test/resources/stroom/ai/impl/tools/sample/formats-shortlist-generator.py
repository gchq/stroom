# Generates stroom-ai's fixtures for the formats shortlist (TestFormatsShortlist): key=value variations and lines
# tied into records by a key, one file per format, each modelled on a real product's documented layout with
# synthetic values. Deterministic (seeded); prints each file's line count.
#   checkpoint-lines.log  Check Point Log Exporter syslog: key:"value" pairs separated by "; " in brackets
#   iptables-lines.log    netfilter kernel log via syslog: empty values (OUT=), bare flags (DF, SYN)
#   leef1-lines.log       LEEF 1.0 after a syslog header: pairs separated by tabs
#   leef2-lines.log       LEEF 2.0: the pair delimiter declared in the header (^)
#   postfix-lines.log     Postfix via syslog: pairs separated by ", ", <> values, trailing text, interleaved
#                         queue ids
#   slapd-lines.log       OpenLDAP slapd: conn= and op= tie lines, verbs name them, connections interleave
#   ise-lines.log         Cisco ISE: one message split over several syslog lines (message id, segment count and
#                         number), pairs separated by ", ", keys holding spaces
#   postgres-lines.log    PostgreSQL with log_line_prefix '%m [%p] %c ': session id ties LOG/DETAIL/STATEMENT
#                         lines, SQL over several tab-indented lines
#   modsec-lines.log      ModSecurity serial audit log: transactions of sections between --id-X-- markers,
#                         the id only on the markers
# Run: python3 formats-shortlist-generator.py <directory>
import os, random, sys
random.seed(20261108)
out_dir = sys.argv[1]
users = ["alice", "bob", "carol", "dave"]
second = [10 * 3600]


def clock():
    second[0] += random.randint(1, 5)
    s = second[0]
    return s, f"{s // 3600:02d}:{s // 60 % 60:02d}:{s % 60:02d}"


def syslog(host, tag):
    _, t = clock()
    return f"<134>Oct 11 {t} {host} {tag}"


def ip(net="10.1"):
    # The network's octets, the rest made up.
    octets = net.split(".")
    while len(octets) < 3:
        octets.append(str(random.randint(0, 9)))
    return ".".join(octets) + f".{random.randint(2, 250)}"


def write(name, lines):
    open(os.path.join(out_dir, name), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(name, len(lines))


# 1. Check Point Log Exporter (syslog format).
lines = []
for n in range(120):
    s, _ = clock()
    action = random.choice(["Accept", "Accept", "Drop", "Reject"])
    pairs = [("action", action), ("flags", "411908"), ("ifdir", random.choice(["inbound", "outbound"])),
             ("ifname", "eth1"), ("loguid", f"{{0x{s:x},0x0,0x6a0210ac,0xc000000{n % 10}}}"),
             ("origin", "10.0.0.1"), ("time", str(1791676800 + s)), ("version", "5"), ("dst", ip()),
             ("proto", "6"), ("s_port", str(random.randint(1024, 65000))),
             ("service", random.choice(["443", "22", "3389"])), ("src", ip("203.0.113"))]
    if action != "Accept":
        pairs.append(("rule_name", f"Block {random.choice(['SSH', 'RDP'])} from Internet"))
    body = "; ".join(f'{k}:"{v}"' for k, v in pairs)
    lines.append(f"<134>1 2026-10-11T{s // 3600:02d}:{s // 60 % 60:02d}:{s % 60:02d}Z cp-gw01 CheckPoint "
                 f"{random.randint(1000, 9999)} - [{body}]")
write("checkpoint-lines.log", lines)

# 2. netfilter via syslog.
lines = []
for n in range(120):
    flags = random.choice([" SYN URGP=0", " ACK PSH URGP=0", " SYN URGP=0"])
    df = " DF" if random.random() < 0.7 else ""
    lines.append(f"{syslog('fw01', 'kernel:')} [{12345 + n}.{random.randint(100000, 999999)}] "
                 f"{random.choice(['IPTABLES-DROP', 'IPTABLES-ACCEPT'])}: IN=eth0 OUT= "
                 f"MAC=00:16:3e:4a:5b:6c:00:16:3e:7d:8e:9f:08:00 SRC={ip('203.0.113')} DST={ip()} LEN=60 TOS=0x00 "
                 f"PREC=0x00 TTL={random.randint(40, 64)} ID={random.randint(1000, 65000)}{df} PROTO=TCP "
                 f"SPT={random.randint(1024, 65000)} DPT={random.choice([22, 80, 443])} WINDOW=29200 RES=0x00{flags}")
write("iptables-lines.log", lines)

# 3, 4. LEEF 1.0 (tabs) and 2.0 (^ declared).
for version, delimiter, name in (("1.0", "\t", "leef1-lines.log"), ("2.0", "^", "leef2-lines.log")):
    lines = []
    for n in range(120):
        event = random.choice(["4624", "4625", "4634"])
        pairs = [("devTime", f"Oct 11 2026 {clock()[1]}"), ("devTimeFormat", "MMM dd yyyy HH:mm:ss"),
                 ("src", ip()), ("usrName", random.choice(users)), ("domain", "ACME"),
                 ("logonType", random.choice(["2", "3", "10"])), ("sev", "3" if event == "4625" else "1")]
        header = f"LEEF:{version}|Microsoft|Windows Security|10.0|{event}|"
        if version == "2.0":
            header += f"{delimiter}|"
        lines.append(f"{syslog('dc01', '')}{header}{delimiter.join(f'{k}={v}' for k, v in pairs)}")
    write(name, lines)

# 5. Postfix: queue ids interleaved.
lines = []
active = []
for n in range(60):
    qid = f"{random.randint(0x10000000, 0xFFFFFFFF):X}{random.choice('ABCDEF')}"
    client = ip("198.51.100")
    active.append([qid, [f"postfix/smtpd[{2000 + n}]: connect from unknown[{client}]",
                         f"postfix/smtpd[{2000 + n}]: {qid}: client=unknown[{client}]",
                         f"postfix/cleanup[{3000 + n}]: {qid}: message-id=<{n}.{qid}@acme.org>",
                         f"postfix/qmgr[1188]: {qid}: from=<{random.choice(users)}@acme.org>, "
                         f"size={random.randint(500, 90000)}, nrcpt=1 (queue active)",
                         f"postfix/smtp[{4000 + n}]: {qid}: to=<{random.choice(users)}@example.com>, "
                         f"relay=mx.example.com[198.51.100.7]:25, delay={random.randint(1, 9) / 10}, "
                         f"delays=0.1/0/0.2/0.2, dsn=2.0.0, status=sent (250 2.0.0 Ok: queued as 1A2B3C)",
                         f"postfix/qmgr[1188]: {qid}: removed"]])
    while active and (random.random() < 0.6 or n == 59):
        pick = random.choice(active)
        lines.append(f"{syslog('mx01', '')}{pick[1].pop(0)}")
        if not pick[1]:
            active.remove(pick)
        if n == 59 and not active:
            break
write("postfix-lines.log", lines)

# 6. OpenLDAP slapd: connections interleave.
lines = []
conns = []
for n in range(40):
    conn = 1000 + n
    user = random.choice(users)
    ops = [f"conn={conn} fd={10 + n % 20} ACCEPT from IP={ip()}:{random.randint(1024, 65000)} (IP=0.0.0.0:389)",
           f'conn={conn} op=0 BIND dn="uid={user},ou=people,dc=acme,dc=org" method=128',
           f'conn={conn} op=0 BIND dn="uid={user},ou=people,dc=acme,dc=org" mech=SIMPLE ssf=0',
           f"conn={conn} op=0 RESULT tag=97 err={random.choice([0, 0, 49])} text=",
           f'conn={conn} op=1 SRCH base="dc=acme,dc=org" scope=2 deref=0 filter="(uid={random.choice(users)})"',
           f"conn={conn} op=1 SRCH attr=cn mail",
           f"conn={conn} op=1 SEARCH RESULT tag=101 err=0 nentries=1 text=",
           f"conn={conn} op=2 UNBIND", f"conn={conn} fd={10 + n % 20} closed"]
    conns.append(ops)
    while conns and random.random() < 0.7:
        pick = random.choice(conns[-2:])
        lines.append(f"{syslog('ldap01', 'slapd[812]:')} {pick.pop(0)}")
        if not pick:
            conns.remove(pick)
for pick in conns:
    lines += [f"{syslog('ldap01', 'slapd[812]:')} {op}" for op in pick]
write("slapd-lines.log", lines)

# 7. Cisco ISE: one message over several segments.
lines = []
for n in range(60):
    msgid = f"{1234500 + n:010d}"
    user = random.choice(users)
    passed = random.random() > 0.2
    category = "CISE_Passed_Authentications" if passed else "CISE_Failed_Attempts"
    _, t = clock()
    pairs = [f"2026-10-11 {t}.{random.randint(100, 999)} +00:00 {random.randint(10000, 99999):010d} "
             f"{'5200 NOTICE Passed-Authentication: Authentication succeeded' if passed else '5400 NOTICE Failed-Attempt: Authentication failed'}",
             "ConfigVersionId=101", f"Device IP Address={ip()}", "DestinationIPAddress=10.0.0.5",
             "DestinationPort=1812", f"UserName={user}", "Protocol=Radius", "RequestLatency=12",
             f"NetworkDeviceName=sw0{random.randint(1, 4)}", f"User-Name={user}", "NAS-Port=50112",
             f"Calling-Station-ID={random.randint(10, 99)}-AB-CD-EF-01-23", "AcsSessionID=ise01/123456789/1",
             f"AuthenticationMethod={random.choice(['PAP_ASCII', 'MSCHAPV2'])}", "SelectedAccessService=Default",
             "Location=Location#All Locations#HQ", "Device Type=Device Type#All Device Types#Switch"]
    segments = [pairs[:7], pairs[7:13], pairs[13:]] if n % 3 else [pairs]
    for number, part in enumerate(segments):
        lines.append(f"<181>Oct 11 {t} ise01 {category} {msgid} {len(segments)} {number} {', '.join(part)},")
write("ise-lines.log", lines)

# 8. PostgreSQL: sessions mostly consecutive.
lines = []
for n in range(50):
    pid = 4500 + n
    session = f"6713a2f1.{pid:x}"
    s, _ = clock()
    stamp = f"2026-10-11 {s // 3600:02d}:{s // 60 % 60:02d}:{s % 60:02d}.{random.randint(100, 999)} UTC"
    prefix = f"{stamp} [{pid}] {session} "
    user = random.choice(users)
    if random.random() < 0.7:
        lines += [f"{prefix}LOG:  statement: SELECT id, name",
                  "\tFROM users",
                  "\tWHERE name = $1",
                  f"{prefix}DETAIL:  parameters: $1 = '{user}'"]
    else:
        lines += [f"{prefix}ERROR:  relation \"user_{n}\" does not exist at character 15",
                  f"{prefix}STATEMENT:  SELECT * FROM user_{n}",
                  f"{prefix}HINT:  Perhaps you meant to reference the table \"users\"."]
write("postgres-lines.log", lines)

# 9. ModSecurity serial audit log.
lines = []
for n in range(40):
    tid = f"{random.randint(0x10000000, 0xFFFFFFFF):08x}"
    s, t = clock()
    attack = random.random() < 0.4
    uri = "/login.php?user=admin%27%20OR%201%3D1--" if attack else random.choice(["/", "/index.html", "/api/orders"])
    status = "403 Forbidden" if attack else "200 OK"
    lines += [f"--{tid}-A--",
              f"[11/Oct/2026:{t} +0000] YzAb{n:04d}CdEf {ip('203.0.113')} {random.randint(1024, 65000)} 10.1.2.3 443",
              "", f"--{tid}-B--", f"GET {uri} HTTP/1.1", "Host: www.acme.org",
              f"User-Agent: {'sqlmap/1.7' if attack else 'Mozilla/5.0'}", "Accept: */*",
              "", f"--{tid}-F--", f"HTTP/1.1 {status}", f"Content-Length: {random.randint(100, 900)}",
              "Content-Type: text/html", ""]
    if attack:
        lines += [f"--{tid}-H--",
                  'Message: Access denied with code 403 (phase 2). detected SQLi using libinjection. '
                  '[file "/etc/modsecurity/crs/REQUEST-942-APPLICATION-ATTACK-SQLI.conf"] [line "65"] '
                  '[id "942100"] [msg "SQL Injection Attack Detected via libinjection"] [severity "CRITICAL"]',
                  "Action: Intercepted (phase 2)",
                  f"Stopwatch: {(1791676800 + s) * 1000000} {random.randint(500, 5000)} (- - -)",
                  "Producer: ModSecurity for Apache/2.9.7 (http://www.modsecurity.org/); OWASP_CRS/3.3.4.",
                  "Server: Apache", 'Engine-Mode: "ENABLED"', ""]
    lines += [f"--{tid}-Z--", ""]
write("modsec-lines.log", lines)
