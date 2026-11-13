# Synthetic aggregated feeds for Stroom AI's source type e2e tests (TestE2eSourceType*, TestE2eAggregatedFeedWalks).
# Not real data: PEGA logs in a JSON wrapper (messages of text, JSON or SOAP XML), Azure audit records chosen by
# Workload, and collector records chosen by LogFilePath.
#
# Usage: python3 aggregated-feeds-generator.py <dir>
# Writes <dir>/{pega,azure-audit,collector-paths}/<name>-{1,2}.json. Each file is the first lines of a fixed,
# seeded run (the full run is made so the slices don't change with the number of lines kept).
import json, random, sys, datetime as dt, uuid
out = sys.argv[1]
r = random.Random(6061)
users = ["anja.michel", "li.wei", "omar.haddad", "priya.nair", "sam.taylor", "kate.jones", "svc-batch", "svc-integration"]
hosts = ["pega-app01.corp.example", "pega-app02.corp.example"]

def ts(t):
    return t.strftime("%Y-%m-%dT%H:%M:%S.") + f"{t.microsecond // 1000:03d}Z"

def soap(op, ns, truncated):
    body = (f'<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" '
            f'xmlns:{ns}="urn:example:{ns}:v2"><soapenv:Header><{ns}:CorrelationId>{uuid.UUID(int=r.getrandbits(128))}'
            f'</{ns}:CorrelationId></soapenv:Header><soapenv:Body><{ns}:{op}><{ns}:CustomerId>C{r.randint(10000, 99999)}'
            f'</{ns}:CustomerId><{ns}:Channel>{r.choice(["web", "branch", "mobile"])}</{ns}:Channel>'
            f'<{ns}:RequestedBy>{r.choice(users)}</{ns}:RequestedBy></{ns}:{op}></soapenv:Body></soapenv:Envelope>')
    return body[:r.randint(120, len(body) - 20)] if truncated else body

def pega(n, day):
    t = dt.datetime(2026, 11, day, 0, 0, 0)
    kinds = [("PegaDB", .44), ("PegaRULES", .25), ("PegaClusterHeartbeat", .15), ("PegaIntegration", .08),
             ("PegaSecurityEvent", .05), ("PegaALERT", .03)]
    lines = []
    for _ in range(n):
        t += dt.timedelta(milliseconds=r.randint(50, 4000))
        st = r.choices([k for k, _ in kinds], [w for _, w in kinds])[0]
        thread = f"http-nio-8443-exec-{r.randint(1, 40)}"
        if st == "PegaDB":
            msg = (f"{t:%Y-%m-%d %H:%M:%S},{r.randint(0, 999):03d} [{thread}] [  STANDARD] (internal.access.DatabaseImpl) DEBUG "
                   f"- Executing query SELECT pzInsKey, pyStatusWork FROM pc_work WHERE pyID = 'W-{r.randint(1000, 99999)}' took {r.randint(1, 900)}ms")
        elif st == "PegaRULES":
            msg = (f"{t:%Y-%m-%d %H:%M:%S},{r.randint(0, 999):03d} [{thread}] [  STANDARD] (internal.mgmt.Executable) "
                   f"{r.choice(['INFO ', 'INFO ', 'WARN ', 'ERROR'])} {r.choice(users)} - {r.choice(['Activity started', 'Flow advanced to Approve', 'Case W-%d resolved' % r.randint(1000, 9999), 'Data page refreshed'])}")
        elif st == "PegaClusterHeartbeat":
            msg = f"Heartbeat node={r.choice(hosts)} members=2 state=ACTIVE latency_ms={r.randint(1, 30)}"
        elif st == "PegaIntegration":
            op = r.choice(["GetCustomerRequest", "UpdateAddressRequest", "CreateCaseRequest", "GetCustomerResponse"])
            msg = ("Request: " if op.endswith("Request") else "Response: ") + soap(op, r.choice(["cus", "crm"]), r.random() < .06)
        elif st == "PegaSecurityEvent":
            ev = r.choice([("login", "success"), ("login", "failure"), ("logout", "success"), ("access", "denied"),
                           ("role change", "success"), ("password change", "success")])
            msg = json.dumps({"eventCategory": "Security", "eventType": ev[0], "outcome": ev[1], "operatorId": r.choice(users),
                              "ipAddress": f"10.20.{r.randint(0, 9)}.{r.randint(1, 254)}", "appName": "CustomerService",
                              "eventTime": ts(t)})
        else:
            msg = (f"PEGA{r.choice(['0001', '0005', '0011', '0035'])} ({r.choice(['HTTP interaction time exceeds limit', 'Query time exceeds limit', 'Total list size exceeds limit'])}) "
                   f"{r.randint(1000, 20000)} ms; operator={r.choice(users)}")
        lines.append(json.dumps({"timestamp": ts(t), "hostname": r.choice(hosts), "sourcetype": st,
                                 "app": "pega", "environment": "prod", "message": msg}))
    return lines

def azure(n, day):
    t = dt.datetime(2026, 11, day, 0, 0, 0)
    wl = [("SharePoint", .32), ("ExchangeOnline", .30), ("AzureActiveDirectory", .18), ("MicrosoftTeams", .12), ("OneDrive", .08)]
    lines = []
    for _ in range(n):
        t += dt.timedelta(milliseconds=r.randint(100, 6000))
        w = r.choices([k for k, _ in wl], [x for _, x in wl])[0]
        u = r.choice(users) + "@contoso.example"
        if w == "ExchangeOnline":
            op = r.choice(["MailItemsAccessed", "MailItemsAccessed", "Send", "MoveToDeletedItems", "New-InboxRule"])
            ev = {"MailboxOwnerUPN": u, "ClientInfoString": r.choice(["Client=OWA", "Client=REST;Outlook"]),
                  "LogonType": r.choice([0, 1, 2]), "ExternalAccess": r.random() < .05}
            if op == "Send":
                ev["Item"] = {"Subject": r.choice(["Q3 figures", "Invoice", "Re: roster"]), "SizeInBytes": r.randint(2000, 900000)}
        elif w in ("SharePoint", "OneDrive"):
            op = r.choice(["FileAccessed", "FileAccessed", "FileModified", "FileDownloaded", "SharingSet"])
            ev = {"SiteUrl": f"https://contoso.sharepoint.example/sites/{r.choice(['finance', 'hr', 'eng'])}",
                  "SourceFileName": r.choice(["budget.xlsx", "plan.docx", "notes.txt"]),
                  "SourceRelativeUrl": "Shared Documents", "UserAgent": "Mozilla/5.0"}
        elif w == "AzureActiveDirectory":
            op = r.choice(["UserLoggedIn", "UserLoggedIn", "UserLoginFailed", "Add member to role.", "Reset user password."])
            ev = {"ApplicationId": str(uuid.UUID(int=r.getrandbits(128))), "LogonError": "InvalidPasswordCount" if op == "UserLoginFailed" else None,
                  "ResultStatus": "Failed" if op == "UserLoginFailed" else "Success"}
        else:
            op = r.choice(["MessageSent", "MeetingParticipantDetail", "TeamMemberAdded"])
            ev = {"TeamName": r.choice(["Ops", "Finance"]), "ChannelName": "General", "CommunicationType": "Team"}
        lines.append(json.dumps({"CreationTime": t.strftime("%Y-%m-%dT%H:%M:%S"), "Id": str(uuid.UUID(int=r.getrandbits(128))),
                                 "Operation": op, "OrganizationId": "11111111-2222-3333-4444-555555555555",
                                 "RecordType": r.choice([2, 6, 15, 25, 50]), "UserId": u, "ClientIP": f"203.0.113.{r.randint(1, 254)}",
                                 "Workload": w, "event": ev}))
    return lines

def collector(n, day):
    t = dt.datetime(2026, 11, day, 0, 0, 0)
    paths = [("/var/log/mail/maillog", .35), ("/var/log/mail/audit/mailaudit.log", .05), ("/var/log/httpd/access_log", .30),
             ("/var/log/httpd/error_log", .05), ("/var/log/secure", .15), ("/opt/app/logs/app-2026-11-%02d.log" % day, .10)]
    lines = []
    for _ in range(n):
        t += dt.timedelta(milliseconds=r.randint(100, 3000))
        p = r.choices([k for k, _ in paths], [w for _, w in paths])[0]
        if "httpd/access" in p:
            m = f'10.0.{r.randint(0, 9)}.{r.randint(1, 254)} - - [{t:%d/%b/%Y:%H:%M:%S} +0000] "GET /{r.choice(["", "login", "api/v1/orders"])} HTTP/1.1" {r.choice([200, 200, 302, 404, 500])} {r.randint(200, 9000)}'
        elif "httpd/error" in p:
            m = f"[{t:%a %b %d %H:%M:%S %Y}] [error] [client 10.0.1.{r.randint(1, 254)}] File does not exist: /var/www/html/favicon.ico"
        elif p == "/var/log/secure":
            m = f"{t:%b %d %H:%M:%S} bastion sshd[{r.randint(1000, 9999)}]: {r.choice(['Accepted password', 'Failed password'])} for {r.choice(users)} from 10.0.2.{r.randint(1, 254)} port {r.randint(40000, 60000)} ssh2"
        elif "mail/audit" in p:
            m = f"audit user={r.choice(users)} action={r.choice(['export', 'delete', 'rule-change'])} mailbox={r.choice(users)}"
        elif "mail" in p:
            m = f"{t:%b %d %H:%M:%S} mx01 postfix/smtpd[{r.randint(1000, 9999)}]: {r.choice(['connect from', 'disconnect from'])} unknown[203.0.113.{r.randint(1, 254)}]"
        else:
            m = f"{t:%Y-%m-%d %H:%M:%S} INFO [main] OrderService - order {r.randint(1000, 9999)} {r.choice(['created', 'shipped'])}"
        lines.append(json.dumps({"@timestamp": ts(t), "host": {"name": r.choice(["web01", "mx01", "bastion"])},
                                 "LogFilePath": p, "message": m}))
    return lines

import os

# The lines the tests read of each file.
KEPT = {"pega-1": 3000, "pega-2": 2000, "azure-audit-1": 1000, "azure-audit-2": 1000,
        "collector-paths-1": 1000, "collector-paths-2": 1000}
for name, fn, n in [("pega", pega, 5000), ("azure-audit", azure, 3000), ("collector-paths", collector, 2000)]:
    os.makedirs(f"{out}/{name}", exist_ok=True)
    for s, day in enumerate([10, 11]):
        kept = KEPT[f"{name}-{s + 1}"]
        with open(f"{out}/{name}/{name}-{s + 1}.json", "w") as f:
            f.write("\n".join(fn(n, day)[:kept]) + "\n")
