# Synthetic application logs for Stroom AI's eval case 112 (an aggregated feed of a platform's loggers, onboarded
# with its Tomcat logging dropped). Not real data: shaped like a tester's feed (2026-11-26). A log shipper's JSON
# lines, `@timestamp`, `host` and `event`, a JSON string holding the application's own record: its `logger_name`,
# its `appender_ref` (the source type: SECURITY, ALERT, APPLICATION, CATALINA, ACCESS), `level`, `thread_name` and
# `message`. SecurityEventLogger's records say what happened in `eventType` (Login, Logout), with `userid`, `src_ip`
# and `outcome`; 'DSM Alerts' raises alerts (`alert_id`, `severity`); SearchAuditLogger's records hold `original`,
# JSON text again, whose `details` is JSON text holding the search's `type` ('search' or 'QA search') and its
# `query_text`. Tomcat's loggers (org.apache.*) write to CATALINA, ACCESS and APPLICATION: the operational logging
# the user drops. Some records' `event` is longer than the JSONParser's stringTruncateLength (10,000 characters): a
# long stack trace in the message, on Tomcat's errors and on the kiosk accounts' failed logins.
#
# The streams are spread over a week, each holding only some of the appenders; the newest stream's first 40 lines
# are SecurityEventLogger's and CATALINA's only.
#
# Usage: python3 app-loggers-generator.py <dir>
# Writes <dir>/app-loggers.json: {"streams": [[{"meta": {...}, "lines": [...]}], ...]}, each stream of one part, as a
# proxy aggregates them. Seeded, so the file doesn't change from run to run.
import json, random, sys, datetime as dt
out = sys.argv[1]
r = random.Random(11200)
users = ["anja.michel", "li.wei", "omar.haddad", "priya.nair", "sam.taylor", "kate.jones"]
kiosks = ["kiosk01", "kiosk02", "kiosk03"]
hosts = ["app-web-01", "app-web-02", "app-web-03"]
queries = ["invoice 2026", "failed logins", "quarterly report", "vpn users", "supplier contracts",
           "leave requests", "badge access level 3", "printer queue", "project falcon", "expense claims"]
SECURITY = "SecurityEventLogger"
ALERTS = "DSM Alerts"
SEARCH = "SearchAuditLogger"
VALVE = "org.apache.catalina.core.StandardWrapperValve"
STARTUP = "org.apache.catalina.startup.Catalina"
COYOTE = "org.apache.coyote.http11.Http11Processor"
ACCESS_LOG = "org.apache.catalina.valves.AccessLogValve"
# Each stream: its day and the kinds of record it holds; the newest's first 40 lines only security and CATALINA.
STREAMS = [
    ("2026-11-20", ["security", "alert", "catalina"]),
    ("2026-11-21", ["search", "security", "access"]),
    ("2026-11-22", ["alert", "search", "catalina"]),
    ("2026-11-23", ["security", "access", "search"]),
    ("2026-11-24", ["alert", "catalina", "security", "search"]),
    ("2026-11-25", ["access", "alert", "security"]),
    ("2026-11-26", ["security", "catalina"]),
]
NEWEST_LATER = ["alert", "search", "security"]
PER_STREAM = 60
NEWEST_HEAD = 40
counter = [0]


def stack_trace(n):
    trace = "\njava.lang.IllegalStateException: request " + str(n) + " failed"
    frame = 0
    while len(trace) < 12000:
        trace += ("\n\tat com.example.platform.web.Handler%d.handle(Handler%d.java:%d)"
                  % (frame % 23, frame % 23, 100 + frame))
        frame += 1
    return trace


def base(logger, appender, level, t):
    return {"@timestamp": t.strftime("%Y-%m-%dT%H:%M:%S.") + "%03dZ" % (t.microsecond // 1000),
            "logger_name": logger, "appender_ref": appender, "level": level,
            "thread_name": "http-nio-8443-exec-%d" % r.randint(1, 20)}


def security(t):
    counter[0] += 1
    long_one = counter[0] % 9 == 0
    if long_one:
        # A kiosk account's failed logon, its LDAP error's stack trace in the message.
        user = kiosks[counter[0] // 9 % len(kiosks)]
        record = base(SECURITY, "SECURITY", "WARN", t)
        record.update({"eventType": "Login", "userid": user, "src_ip": "10.30.1.%d" % r.randint(2, 60),
                       "outcome": "failure",
                       "message": "Login failed for " + user + ": directory lookup failed" + stack_trace(counter[0])})
        return record
    login = r.random() < 0.6
    user = r.choice(users)
    success = not login or r.random() < 0.85
    record = base(SECURITY, "SECURITY", "INFO" if success else "WARN", t)
    record.update({"eventType": "Login" if login else "Logout", "userid": user,
                   "src_ip": "10.20.%d.%d" % (r.randint(0, 9), r.randint(1, 254)),
                   "outcome": "success" if success else "failure",
                   "message": ("Login " + ("succeeded" if success else "failed") + " for " + user) if login
                   else "Logout for " + user})
    return record


def alert(t):
    record = base(ALERTS, "ALERT", "WARN", t)
    code = r.choice(["ALERT0010", "ALERT0012", "ALERT0015"])
    record.update({"alert_id": code, "severity": r.choice(["low", "medium", "high"]),
                   "message": code + " interaction took " + str(r.randint(1000, 9000)) + " ms"})
    return record


def search(t):
    if r.random() < 0.3:
        # Tomcat's servlet errors go to the application's own appender too.
        record = base(VALVE, "APPLICATION", "ERROR", t)
        counter[0] += 1
        record.update({"message": "Servlet.service() for servlet [dispatcher] threw exception"
                                  + (stack_trace(counter[0]) if r.random() < 0.5 else "")})
        return record
    record = base(SEARCH, "APPLICATION", "INFO", t)
    kind = "QA search" if r.random() < 0.3 else "search"
    details = {"type": kind, "query_text": r.choice(queries) + " " + str(r.randint(1, 99)),
               "results": r.randint(0, 250)}
    original = {"user": r.choice(users), "client": "console-%d" % r.randint(1, 3),
                "session": "s%d" % r.randint(1000, 9999), "details": json.dumps(details)}
    record.update({"message": "Search run", "original": json.dumps(original)})
    return record


def catalina(t):
    logger = r.choice([STARTUP, COYOTE, COYOTE])
    if logger == STARTUP:
        record = base(STARTUP, "CATALINA", "INFO", t)
        record["message"] = "Server startup in [%d] milliseconds" % r.randint(2000, 9000)
        return record
    record = base(COYOTE, "CATALINA", "ERROR", t)
    counter[0] += 1
    record["message"] = "Error parsing HTTP request header" + (stack_trace(counter[0]) if r.random() < 0.4 else "")
    return record


def access(t):
    record = base(ACCESS_LOG, "ACCESS", "INFO", t)
    record["message"] = "10.20.%d.%d - - \"GET /dsm/%s HTTP/1.1\" %d %d" % (
        r.randint(0, 9), r.randint(1, 254), r.choice(["home", "search", "cases", "reports"]),
        r.choice([200, 200, 200, 302, 404]), r.randint(200, 90000))
    return record


MAKERS = {"security": security, "alert": alert, "search": search, "catalina": catalina, "access": access}


def line(kind, t):
    return json.dumps({"@timestamp": t.strftime("%Y-%m-%dT%H:%M:%S.") + "%03dZ" % (t.microsecond // 1000),
                       "host": r.choice(hosts), "event": json.dumps(MAKERS[kind](t))})


streams = []
for number, (day, kinds) in enumerate(STREAMS):
    t = dt.datetime.fromisoformat(day + "T08:00:00")
    lines = []
    newest = number == len(STREAMS) - 1
    for n in range(PER_STREAM):
        t += dt.timedelta(seconds=r.randint(5, 300), milliseconds=r.randint(0, 999))
        pool = NEWEST_LATER if newest and n >= NEWEST_HEAD else kinds
        lines.append(line(r.choice(pool), t))
    meta = {"Feed": "DSM-PLATFORM-LOGS", "RemoteHost": "fwd-0%d.corp.example" % (number % 2 + 1)}
    streams.append([{"meta": meta, "lines": lines}])
with open(out + "/app-loggers.json", "w") as f:
    json.dump({"streams": streams}, f, indent=1)
