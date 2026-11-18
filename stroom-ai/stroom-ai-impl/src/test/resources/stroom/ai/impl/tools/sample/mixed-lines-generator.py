# Generates mixed-lines.log, stroom-ai's fixture for streams of several formats (TestLineFormats, TestDraftMixedLines,
# and stroom-app's TestE2eMixedLines, which reads a copy): a generic stream of many log formats, some entries carrying stack
# traces. Deterministic (seeded); prints the counts the tests
# assert. Run: python3 mixed-lines-generator.py mixed-lines.log
import random, sys, collections
random.seed(20261104)
users = ["alice", "bob", "carol", "dave", "erin", "frank", "zoë", "o'brien"]
hosts = ["web01", "web02", "db01", "gw-edge"]
months = ["Oct"]
out = []
counts = collections.Counter()
continued = 0
unclosed = 0

def java_trace(depth):
    exc = random.choice(["java.lang.IllegalStateException", "java.io.IOException", "com.example.app.RequestRejectedError",
                         "java.util.concurrent.TimeoutException"])
    lines = [f"{exc}: request {random.randint(1, 9999)} rejected"]
    lines += [f"\tat com.example.app.Service{n}.handle(Service{n}.java:{10 + n})" for n in range(depth)]
    r = random.random()
    if r < 0.4:
        lines += ["Caused by: java.sql.SQLException: connection reset", "\tat com.example.db.Pool.get(Pool.java:88)",
                  f"\t... {depth} more"]
        if random.random() < 0.3:
            lines += ["Caused by: java.net.SocketException: broken pipe", "\tat java.base/sun.nio.ch.Net.poll(Net.java:12)",
                      "\t... 3 common frames omitted"]
    elif r < 0.55:
        lines += ["\tSuppressed: java.io.IOException: close failed", "\t\tat com.example.io.Stream.close(Stream.java:5)",
                  f"\t\t... {depth} more"]
    return lines

def python_trace():
    return ["Traceback (most recent call last):",
            '  File "/srv/app/handler.py", line 42, in handle',
            "    result = process(request)",
            '  File "/srv/app/process.py", line 7, in process',
            "    raise ValueError(\"bad input\")",
            "ValueError: bad input"]

def t(i):
    return f"{(i // 3600) % 24:02d}:{(i // 60) % 60:02d}:{i % 60:02d}"

# The stream starts part-way through a stack trace.
out += ["\tat com.example.app.Worker.run(Worker.java:41)", "\tat java.base/java.lang.Thread.run(Thread.java:1583)"]
counts["unrecognised"] += 1
continued += 1
for i in range(1000):
    u = random.choice(users); h = random.choice(hosts); ts = t(i * 7)
    r = random.random()
    if r < 0.16:
        out.append(f"<13>Oct 11 {ts} {h} app[{100 + i % 50}]: user={u} action={random.choice(['login', 'logout'])} src=10.0.{i % 256}.{i % 200}")
        counts["syslog"] += 1
    elif r < 0.22:
        out.append(f"<13>Oct 11 {ts} {h} app[{100 + i % 50}]: user={u} action=update msg=\"changed the {random.choice(['policy', 'password', 'role'])}\" id={i}")
        counts["syslog"] += 1
    elif r < 0.30:
        out.append(f"<86>Oct 11 {ts} {h} sshd[{2000 + i % 90}]: Connection from 10.1.{i % 256}.{i % 100} port {40000 + i} closed")
        counts["syslog"] += 1
    elif r < 0.36:
        out.append(f"<165>1 2026-10-11T{ts}.{i % 1000:03d}Z {h} billing {3000 + i % 40} ID{i % 7} [meta seq=\"{i}\"] Invoice {i} issued")
        counts["syslog5424"] += 1
    elif r < 0.40:
        if random.random() < 0.5:
            out.append(f"CEF:0|Acme|Gateway|2.1|{100 + i % 9}|Blocked request \\| rule {i % 4}|5|src=10.2.{i % 256}.1 suser={u} act=blocked msg=policy {i % 5} matched a\\=b")
        else:
            out.append(f"<134>Oct 11 {ts} {h} CEF:0|Acme|Gateway|2.1|{100 + i % 9}|Allowed request|3|src=10.2.{i % 256}.1 suser={u} act=allowed")
        counts["cef"] += 1
    elif r < 0.52:
        q = random.random()
        if q < 0.25:
            msg = f' note="{random.choice(["session ended", "idle timeout", "user requested"])}"'
        elif q < 0.35:
            msg = ' msg="said \\"hi\\" twice"'
        elif q < 0.45:
            msg = ' query="a=b&c=d|e=f"'
        elif q < 0.52:
            msg = ' path="C:\\Program Files\\Acme\\app.exe"'
        elif q < 0.57:
            msg = ' note="unterminated value'
            unclosed += 1
        elif q < 0.62:
            msg = " owner='single quoted'"
        else:
            msg = ""
        out.append(f"user={u} action={random.choice(['logout', 'download', 'share'])} session=s{i} bytes={i * 13}{msg}")
        counts["key_value"] += 1
    elif r < 0.86:
        level = random.choice(["INFO", "INFO", "WARN", "ERROR", "DEBUG"])
        layout = random.random()
        if layout < 0.4:
            head = f"2026-10-11 {ts}.{i % 1000:03d}"
        elif layout < 0.6:
            head = f"2026-10-11T{ts}.{i % 1000:03d}+10:00"
        elif layout < 0.8:
            head = f"[2026-10-11 {ts}]"
        else:
            head = f"11/Oct/2026:{ts} +1000"
        tail = random.choice(["", "", ' "quoted \\"reason\\" here"', " path='/tmp/a b'", " key=value inside text", " \t trailing   "])
        out.append(f"{head} {level} [worker-{i % 12}] Request {i} {'failed' if level == 'ERROR' else 'served in ' + str(i % 300) + 'ms'}{tail}")
        counts["text"] += 1
        if level == "ERROR":
            out += java_trace(random.randint(3, 25)) if random.random() < 0.8 else python_trace()
            continued += 1
        elif level == "WARN" and random.random() < 0.3:
            out.append(f"    retrying in {i % 9} seconds (attempt {i % 4 + 1} of 5)")
            continued += 1
    elif r < 0.93:
        out.append(f"10/11/2026 {ts} AUDIT {u} opened report-{i}.pdf")
        counts["text"] += 1
    elif r < 0.97:
        out.append(random.choice(["--- log rotated ---", "####", "heartbeat", "<garbled \x7f>", "ok",
                                  '"alice","login","2026-10-11 10:00:00"', "col1\tcol2\tcol3", "\"unbalanced quote at start"]))
        counts["unrecognised"] += 1
    else:
        out.append("")
    if i == 500:
        # One very long record: a key=value line whose value runs to 30,000 characters.
        out.append("user=bulk action=upload payload=" + "x" * 30000)
        counts["key_value"] += 1
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(out) + "\n")
lines = [l for l in out if l.strip()]
print("lines", len(lines), "records", sum(counts.values()), dict(counts), "continued", continued,
      "unclosed quotes", unclosed)
