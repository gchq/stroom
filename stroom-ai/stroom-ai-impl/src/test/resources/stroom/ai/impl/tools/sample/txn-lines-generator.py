# Generates txn-lines.log, stroom-ai's fixture for key=value lines tied into records by a key that is not auditd's
# (TestKeyedRecords, TestDraftKeyedRecords): an application's transactions, each several lines sharing txn=, named
# by step= (not the line's first field), each line with its own ISO time and the key no time at all. Some
# transactions are one line (a ping). Deterministic (seeded); prints the counts the tests assert.
# Run: python3 txn-lines-generator.py txn-lines.log
import random, sys
random.seed(20261107)
out = []
second = 10 * 3600
records = 0
users = ["alice", "bob", "carol"]
tables = ["orders", "customers", "stock"]


def ts():
    global second
    second += random.randint(1, 4)
    return f"2026-10-11T{second // 3600:02d}:{second // 60 % 60:02d}:{second % 60:02d}.{random.randint(0, 999):03d}Z"


for n in range(80):
    txn = f"{0x8f2c00 + n * 7:06x}"
    records += 1
    if random.random() < 0.15:
        out.append(f"ts={ts()} txn={txn} step=ping app=orders")
        continue
    out.append(f"ts={ts()} txn={txn} step=begin user={random.choice(users)} app=orders")
    for _ in range(random.randint(1, 3)):
        out.append(f"ts={ts()} txn={txn} step=query table={random.choice(tables)} rows={random.randint(0, 50)} "
                   f"duration_ms={random.randint(1, 40)}")
    ok = random.random() > 0.2
    out.append(f"ts={ts()} txn={txn} step={'commit' if ok else 'rollback'} status={'ok' if ok else 'error'} "
               f"duration_ms={random.randint(5, 90)}")
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(out) + "\n")
print("lines", len(out), "records", records, "first", out[0])
