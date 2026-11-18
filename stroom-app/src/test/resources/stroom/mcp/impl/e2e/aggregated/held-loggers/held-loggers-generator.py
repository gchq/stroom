# Synthetic application logs for Stroom AI's eval case 65 (held source types, many of them). Not real data: a log
# shipper's JSON lines, every service's loggers in one feed. Each record's `event` is a JSON string holding the
# application's own record, whose `logger_name` says which logger (source) wrote it: 43 loggers, ten of them the
# security audit ones. The shipper's own fields: `time` (epoch milliseconds, when the shipper read the line), `host`,
# `service`, and `fields` holding `orig_rt` (epoch nanoseconds, when the application wrote it), `collector` and
# `env`. The audit loggers' records say what was done in `action`; the other loggers' records hold a level, a thread
# and a message, some a duration or a status.
#
# Usage: python3 held-loggers-generator.py <dir>
# Writes <dir>/held-loggers.json: {"streams": [[{"meta": {...}, "lines": [...]}]]}, one stream of one part, as a
# proxy aggregates it. Seeded, so the file doesn't change from run to run.
import json, random, sys, datetime as dt
out = sys.argv[1]
r = random.Random(6500)
users = ["anja.michel", "li.wei", "omar.haddad", "priya.nair", "sam.taylor", "kate.jones"]
admins = ["it.admin", "sec.ops"]
hosts = ["app-node-01", "app-node-02", "app-node-03", "app-node-04"]
# The security audit loggers: each its actions, and whether an action is done by an admin to another user.
AUDIT = {
    "com.acme.auth.LoginAudit": ["LOGIN_SUCCESS", "LOGIN_SUCCESS", "LOGIN_FAILURE", "LOGOUT"],
    "com.acme.auth.MfaAudit": ["MFA_SUCCESS", "MFA_FAILURE"],
    "com.acme.auth.PasswordAudit": ["PASSWORD_CHANGE", "PASSWORD_RESET"],
    "com.acme.session.SessionAudit": ["SESSION_EXPIRED", "SESSION_REVOKED"],
    "com.acme.iam.RoleAudit": ["ROLE_GRANTED", "ROLE_REVOKED"],
    "com.acme.iam.UserAdminAudit": ["USER_CREATED", "USER_DISABLED", "USER_DELETED"],
    "com.acme.iam.ApiKeyAudit": ["KEY_CREATED", "KEY_REVOKED"],
    "com.acme.data.ExportAudit": ["EXPORT_DOWNLOADED"],
    "com.acme.data.RecordAccessAudit": ["RECORD_VIEWED"],
    "com.acme.config.SettingsAudit": ["SETTING_CHANGED"],
}
BY_ADMIN = {"PASSWORD_RESET", "SESSION_REVOKED", "ROLE_GRANTED", "ROLE_REVOKED", "USER_CREATED", "USER_DISABLED",
            "USER_DELETED"}
# The other loggers: 33 of them, three in the audit loggers' own packages, so no package is the audit set's alone.
OTHER = [
    "com.acme.auth.TokenCache", "com.acme.iam.DirectorySync", "com.acme.data.ExportWorker",
    "com.acme.web.RequestLogger", "com.acme.web.StaticResources", "com.acme.web.CorsFilter",
    "com.acme.api.OrdersController", "com.acme.api.CustomersController", "com.acme.api.SearchController",
    "com.acme.billing.InvoiceService", "com.acme.billing.PaymentGateway", "com.acme.billing.TaxCalculator",
    "com.acme.mail.SmtpSender", "com.acme.mail.TemplateRenderer", "com.acme.jobs.Scheduler",
    "com.acme.jobs.NightlyReport", "com.acme.cache.RedisClient", "com.acme.cache.LocalCache",
    "com.acme.db.ConnectionPool", "com.acme.db.MigrationRunner", "com.acme.search.IndexWriter",
    "com.acme.search.QueryParser", "com.acme.metrics.Reporter", "com.acme.health.Probe",
    "com.acme.queue.Consumer", "com.acme.queue.Producer", "com.acme.files.UploadHandler",
    "org.hibernate.SQL", "org.springframework.web.servlet.DispatcherServlet", "org.apache.kafka.clients.NetworkClient",
    "io.netty.channel.DefaultChannelPipeline", "com.zaxxer.hikari.pool.HikariPool", "org.eclipse.jetty.server.Server",
]
MESSAGES = ["Handled request in {n} ms", "Cache miss for key k{n}", "Pool stats: active={n}, idle=4",
            "Retrying after {n} ms", "Processed {n} items", "Connection reset by peer", "Completed in {n} ms",
            "Queued message m-{n}", "Rendered template t{n}", "Health check ok"]

def audit(logger, t):
    action = r.choice(AUDIT[logger])
    actor = r.choice(admins) if action in BY_ADMIN else r.choice(users)
    record = {"logger_name": logger, "level": "WARN" if "FAILURE" in action else "INFO",
              "thread": f"http-nio-8443-exec-{r.randint(1, 30)}", "action": action, "user": actor,
              "src_ip": f"10.20.{r.randint(0, 9)}.{r.randint(1, 254)}"}
    if action in BY_ADMIN:
        record["target_user"] = r.choice(users)
    if action.startswith("ROLE_"):
        record["role"] = r.choice(["auditor", "billing-admin", "support"])
    if action.startswith("KEY_"):
        record["key_id"] = f"ak-{r.randint(1000, 9999)}"
    if action == "EXPORT_DOWNLOADED":
        record["export_id"] = f"exp-{r.randint(100, 999)}"
        record["rows"] = r.randint(10, 50000)
    if action == "RECORD_VIEWED":
        record["record_id"] = f"cust-{r.randint(10000, 99999)}"
    if action == "SETTING_CHANGED":
        record["setting"] = r.choice(["session.timeout", "mfa.required", "export.max_rows"])
        record["new_value"] = str(r.randint(1, 100))
    if action == "LOGIN_FAILURE" or action == "MFA_FAILURE":
        record["reason"] = r.choice(["bad_password", "unknown_user", "code_expired"])
    record["message"] = f"{action.replace('_', ' ').lower()} for {record.get('target_user', actor)}"
    return record

def other(logger):
    record = {"logger_name": logger, "level": r.choice(["DEBUG", "INFO", "INFO", "WARN"]),
              "thread": r.choice(["main", f"worker-{r.randint(1, 8)}", f"http-nio-8443-exec-{r.randint(1, 30)}"]),
              "message": r.choice(MESSAGES).format(n=r.randint(1, 900))}
    if r.random() < .4:
        record["duration_ms"] = r.randint(1, 3000)
    if logger.startswith("com.acme.api") or logger == "com.acme.web.RequestLogger":
        record["status"] = r.choice([200, 200, 201, 404, 500])
    return record

lines = []
t = dt.datetime(2026, 11, 18, 9, 0, 0, tzinfo=dt.timezone.utc)
loggers = list(AUDIT) + OTHER
# A quarter or so of the records are the audit loggers'.
weights = [3] * len(AUDIT) + [3] * len(OTHER)
for i in range(600):
    t += dt.timedelta(milliseconds=r.randint(100, 6000))
    logger = r.choices(loggers, weights)[0]
    # Each logger writes at least once, in the first records.
    if i < len(loggers):
        logger = loggers[i]
    inner = audit(logger, t) if logger in AUDIT else other(logger)
    written = t - dt.timedelta(milliseconds=r.randint(5, 900))
    orig_ns = int(written.timestamp()) * 1_000_000_000 + written.microsecond * 1000 + r.randint(0, 999)
    lines.append(json.dumps({"time": int(t.timestamp() * 1000), "host": r.choice(hosts), "service": "acme-platform",
                             "fields": {"orig_rt": orig_ns, "collector": r.choice(["fwd-01", "fwd-02"]),
                                        "env": "prod"},
                             "event": json.dumps(inner)}))
meta = {"Feed": "ACME-PLATFORM-APP", "RemoteHost": "fwd-01.corp.example"}
with open(f"{out}/held-loggers.json", "w") as f:
    json.dump({"streams": [[{"meta": meta, "lines": lines}]]}, f, indent=1)
    f.write("\n")
