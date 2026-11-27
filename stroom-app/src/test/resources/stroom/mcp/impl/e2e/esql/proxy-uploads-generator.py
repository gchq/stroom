#!/usr/bin/env python3
# Copyright 2026 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# The web proxy upload events of the ES|QL evaluation cases (62 to 64), and the answers the cases expect, worked out
# here independently of Stroom and Elasticsearch. EvalProxyUploads (stroom.mcp.impl.e2e) makes the same events in
# Java with SeededRandom, which draws as CPython's random.Random does; the reference runs (TestE2eEvalReference)
# check Elasticsearch's answers against those the cases give, which this prints.
#
# Run: python3 proxy-uploads-generator.py

import random
from datetime import datetime, timedelta, timezone

SEED = 62
EVENTS = 10000
START = datetime(2026, 1, 1, tzinfo=timezone.utc)
# Users, each with the weight of how often they upload (of 100) and how much more than others they send.
USERS = [("alice", 22, 1), ("bob", 18, 1), ("carol", 15, 2), ("dave", 13, 1), ("erin", 12, 4), ("frank", 10, 1),
         ("grace", 6, 3), ("heidi", 4, 1)]
# Site domains, each with its weight (of 100); example.com is the company's own.
DOMAINS = [("files.example.com", 15), ("intranet.example.com", 8), ("box.com", 12), ("dropbox.com", 10),
           ("github.com", 10), ("drive.google.com", 10), ("mega.nz", 8), ("pastebin.com", 7), ("transfer.sh", 8),
           ("wetransfer.com", 7), ("icloud.com", 5)]
MULTIPLIERS = [1, 1, 1, 2, 5, 20]


def weighted(rng, items):
    pick = rng.randint(0, 99)
    for item in items:
        if pick < item[1]:
            return item
        pick -= item[1]
    raise ValueError("weights must add up to 100")


def events():
    rng = random.Random(SEED)
    out = []
    for i in range(EVENTS):
        user = weighted(rng, USERS)
        domain = weighted(rng, DOMAINS)[0]
        size = rng.randint(1, 50000) * rng.choice(MULTIPLIERS) * user[2]
        blocked = rng.randint(0, 9) == 0
        second = rng.randint(0, 59)
        out.append({
            "time": START + timedelta(seconds=60 * i + second),
            "user": user[0],
            "domain": domain,
            "bytes": size,
            "action": "blocked" if blocked else "allowed",
            "ip": "10.1.0." + str(10 + [u[0] for u in USERS].index(user[0])),
        })
    return out


def main():
    data = events()
    # 62: total bytes by user and domain, the top 10, and how many pairs.
    totals = {}
    for e in data:
        key = (e["user"], e["domain"])
        totals[key] = totals.get(key, 0) + e["bytes"]
    ranked = sorted(totals.items(), key=lambda kv: (-kv[1], kv[0]))
    print("62: %d pairs" % len(ranked))
    for (user, domain), total in ranked[:10]:
        print("      - [%s, %s, %d]" % (user, domain, total))
    # 63: uploads and bytes by day and domain, 2 to 3 January 2026.
    days = {}
    for e in data:
        if datetime(2026, 1, 2, tzinfo=timezone.utc) <= e["time"] < datetime(2026, 1, 4, tzinfo=timezone.utc):
            key = (e["time"].strftime("%Y-%m-%d"), e["domain"])
            count, total = days.get(key, (0, 0))
            days[key] = (count + 1, total + e["bytes"])
    print("63: %d rows" % len(days))
    for (day, domain) in sorted(days):
        print("      - [\"%s\", %s, %d, %d]" % (day, domain, days[(day, domain)][0], days[(day, domain)][1]))
    # 64: allowed uploads to domains outside example.com, by user, in MB (1,000,000 bytes), over 100 MB.
    outside = {}
    for e in data:
        if e["action"] == "allowed" and not e["domain"].endswith("example.com"):
            outside[e["user"]] = outside.get(e["user"], 0) + e["bytes"]
    print("64:")
    for user, total in sorted(outside.items(), key=lambda kv: -kv[1]):
        print("      - [%s, %.1f]  # %d bytes" % (user, total / 1e6, total))


if __name__ == "__main__":
    main()
