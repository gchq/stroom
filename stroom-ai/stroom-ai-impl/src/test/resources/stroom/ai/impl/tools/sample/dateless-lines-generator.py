# Generates dateless-lines.log, stroom-ai's fixture for logs whose lines hold only a time of day, their date in a
# header line (TestLineFormats, TestXsltGeneratorStreamValues, and stroom-app's TestE2eMixedLines, which reads a
# copy): a "Log opened" header, lines crossing midnight (so the date must roll over), stack traces on errors, and a
# later "Day changed" header giving the next date. Deterministic (seeded); prints the counts the tests assert.
# Run: python3 dateless-lines-generator.py dateless-lines.log
import random, sys
random.seed(20261105)
out = ["=== Log opened 2026-10-11 ==="]
lines = 0
traces = 0
second = 23 * 3600 + 50 * 60          # 23:50:00 on the first day
users = ["alice", "bob", "carol", "zoë"]
for i in range(120):
    second += random.randint(5, 20)
    if i == 80:
        # The application notes the new day itself: a header for the lines after it.
        out.append("--- Day changed to 2026-10-12 ---")
    t = second % 86400
    stamp = f"{t // 3600:02d}:{t // 60 % 60:02d}:{t % 60:02d}.{i * 7 % 1000:03d}"
    level = random.choice(["INFO", "INFO", "INFO", "WARN", "ERROR"])
    user = random.choice(users)
    out.append(f"{stamp} {level} [worker-{i % 6}] user={user} request {i} {'failed' if level == 'ERROR' else 'done'}")
    lines += 1
    if level == "ERROR":
        out += ["java.lang.IllegalStateException: request " + str(i) + " rejected",
                "\tat com.example.app.Service.handle(Service.java:42)",
                "\tat com.example.app.Main.main(Main.java:7)"]
        traces += 1
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(out) + "\n")
print("records", lines + 2, "lines", lines, "headers", 2, "traces", traces, "last", out[-1][:12])
