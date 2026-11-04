# Generates auditd-lines.log, stroom-ai's fixture for records of several lines told apart by a key each line holds
# (TestKeyedRecords, TestStroomSplitterRun, TestDraftKeyedRecords, and stroom-app's TestE2eKeyedLines, which reads a
# copy): Linux auditd lines, each `type=NAME msg=audit(EPOCH:SERIAL): pairs`, the lines of one event sharing the
# msg=audit(...) envelope. Multi-line SYSCALL events (SYSCALL, EXECVE, CWD, one or two PATH lines, PROCTITLE, EOE),
# single-line USER_* events whose own message is single-quoted pairs (msg='op=login ... res=success'), and one pair
# of events whose lines interleave, which consecutive grouping splits (the user's decision: not put back together).
# Variants of the same events (a second argument): `node`, each line prefixed with the host an audispd forwarder
# names (`node=web01 type=...`, the same on all of an event's lines); `enriched`, auditd's ENRICHED log_format, its
# translated fields after a group separator (\x1d) on SYSCALL, PATH and USER_* lines; `ausearch`, ausearch's output,
# each event after a `----` row and a `time->` line, complete (no interleaving).
# Deterministic (seeded); prints the counts the tests assert.
# Run: python3 auditd-lines-generator.py auditd-lines.log [node|enriched|ausearch]
import calendar, random, sys, time
random.seed(20261106)
epoch = calendar.timegm((2026, 10, 11, 9, 0, 0))
serial = 4500
out = []
events = 0
lines = 0
users = [(1000, "alice"), (1001, "bob"), (1002, "carol")]
commands = [("ls", "/usr/bin/ls", ["-la"]), ("cat", "/usr/bin/cat", ["/etc/hosts"]),
            ("sudo", "/usr/bin/sudo", ["systemctl", "restart", "nginx"]), ("id", "/usr/bin/id", [])]


def envelope():
    global epoch, serial
    epoch += random.randint(1, 9) + random.randint(0, 999) / 1000
    serial += 1
    return f"msg=audit({epoch:.3f}:{serial}):"


def syscall(env):
    uid, _ = random.choice(users)
    comm, exe, args = random.choice(commands)
    ok = random.random() > 0.15
    items = 2 if comm in ("ls", "sudo") else 1
    head = [f"type=SYSCALL {env} arch=c000003e syscall=59 success={'yes' if ok else 'no'} "
            f"exit={0 if ok else -13} a0=55d1 a1=55d2 a2=55d3 a3=0 items={items} ppid={random.randint(900, 1999)} "
            f"pid={random.randint(2000, 9999)} auid={uid} uid=0 gid=0 euid=0 tty=pts0 ses={uid - 997} "
            f"comm=\"{comm}\" exe=\"{exe}\" key=\"exec\"",
            f"type=EXECVE {env} argc={len(args) + 1} " + " ".join(
                f"a{n}=\"{a}\"" for n, a in enumerate([comm] + args))]
    tail = [f"type=CWD {env} cwd=\"/home/{dict(users)[uid]}\""]
    for item in range(items):
        name = exe if item == 0 else "/lib64/ld-linux-x86-64.so.2"
        tail.append(f"type=PATH {env} item={item} name=\"{name}\" inode={random.randint(10000, 99999)} dev=fd:00 "
                    f"mode=0100755 ouid=0 ogid=0 rdev=00:00 nametype=NORMAL")
    tail.append(f"type=PROCTITLE {env} proctitle={' '.join([comm] + args).encode().hex().upper()}")
    tail.append(f"type=EOE {env}")
    return head, tail


def user_event(env):
    uid, name = random.choice(users)
    kind = random.choice(["USER_LOGIN", "USER_AUTH", "CRED_ACQ", "USER_START", "USER_END"])
    res = "failed" if kind in ("USER_LOGIN", "USER_AUTH") and random.random() < 0.2 else "success"
    op = {"USER_LOGIN": "login", "USER_AUTH": "PAM:authentication", "CRED_ACQ": "PAM:setcred",
          "USER_START": "PAM:session_open", "USER_END": "PAM:session_close"}[kind]
    addr = f"10.1.{random.randint(0, 3)}.{random.randint(2, 250)}"
    return (f"type={kind} {env} pid={random.randint(2000, 9999)} uid=0 auid={uid} ses={uid - 997} "
            f"msg='op={op} acct=\"{name}\" exe=\"/usr/sbin/sshd\" hostname={addr} addr={addr} terminal=ssh "
            f"res={res}'")


syscalls = 0
variant = sys.argv[2] if len(sys.argv) > 2 else None
# Each event's lines; the interleaved pair as one entry, its lines in the order written.
written = []
for i in range(150):
    if i == 75:
        # Two events whose lines interleave (auditd under load): a SYSCALL's lines, another event, then the rest.
        head, tail = syscall(envelope())
        other = user_event(envelope())
        written.append(("interleaved", head, other, tail))
        events += 2
        syscalls += 1
        continue
    if random.random() < 0.45:
        head, tail = syscall(envelope())
        written.append(("event", head + tail))
        syscalls += 1
    else:
        written.append(("event", [user_event(envelope())]))
    events += 1

names = {str(uid): name for uid, name in users}
hosts = random.Random(7)
ENRICHED = {"SYSCALL": lambda line: f'ARCH=x86_64 SYSCALL=execve AUID="{names[field(line, "auid")]}" UID="root" '
                                    f'GID="root" EUID="root"',
            "PATH": lambda line: 'OUID="root" OGID="root"',
            "USER": lambda line: f'UID="root" AUID="{names[field(line, "auid")]}"'}


def field(line, key):
    return line.split(f" {key}=", 1)[1].split(" ", 1)[0]


def styled(lines, node):
    out_lines = []
    for line in lines:
        if variant == "node":
            line = f"node={node} {line}"
        elif variant == "enriched":
            kind = line.split(" ", 1)[0][len("type="):]
            add = ENRICHED.get("USER" if kind.startswith(("USER_", "CRED_")) else kind)
            if add:
                line += "\x1d" + add(line)
        out_lines.append(line)
    return out_lines


def stamp(line):
    return time.strftime("%a %b %d %H:%M:%S %Y", time.gmtime(float(line.split("audit(", 1)[1].split(":", 1)[0])))


for entry in written:
    node = f"web0{hosts.randint(1, 2)}"
    if variant == "ausearch":
        # ausearch prints each event whole, after a separator row and its time.
        blocks = [entry[1]] if entry[0] == "event" else [entry[1] + entry[3], [entry[2]]]
        for block in blocks:
            out += ["----", "time->" + stamp(block[0])] + block
    elif entry[0] == "event":
        out += styled(entry[1], node)
    else:
        out += styled(entry[1], node) + styled([entry[2]], f"web0{hosts.randint(1, 2)}") + styled(entry[3], node)
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(out) + "\n")
# Records: each event one, the interleaved SYSCALL two (ausearch's whole).
records = events if variant == "ausearch" else events + 1
print("lines", len(out), "events", events, "records", records, "syscalls", syscalls, "first", out[0][:60])
