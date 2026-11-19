# Synthetic Pega Platform logs for Stroom AI's kind field tests (TestE2ePegaLoggerKinds; seen in testing,
# 2026-11-19). Not real data. A log shipper's JSON lines: each record's `event` is a JSON string holding Pega's own
# JSON log record (its log4j2 JSON layout fields: logger_name, level, thread_name, source_host, nodeId, nodeType,
# pegathread, RequestorId, userid, CorrelationId, message), plus `log_type`, the Pega log the record came from
# (App, Security, Alert, Integration or Cluster): about five values, a source type field. Within each log type, the
# record's kind of event is told by `logger_name`, a Java class name, several of them longer than 80 characters.
# Alerts carry `alertType` (PEGA0xxx performance alerts; SECU0xxx security alerts). The shipper's own fields: `time`
# (epoch milliseconds), `host`, `service`, and `fields` holding `orig_rt` (epoch nanoseconds), `collector` and `env`.
#
# Usage: python3 pega-loggers-generator.py <dir>
# Writes <dir>/pega-loggers.log: JSON lines, one stream's text. Seeded, so the file doesn't change from run to run.
import json, random, sys, datetime as dt, uuid
out = sys.argv[1]
r = random.Random(6119)
users = ["anja.michel", "li.wei", "omar.haddad", "priya.nair", "sam.taylor", "kate.jones"]
services = ["svc-integration", "svc-batch"]
# Pega's nodes: a web node and a batch node a host, as a Kubernetes deployment names its pods.
NODES = [("pega-web-7d9f8c6b5-x2lqk", "WebUser"), ("pega-web-7d9f8c6b5-m8hrt", "WebUser"),
         ("pega-batch-5c7b9d8f4-q9wzp", "BackgroundProcessing")]
SOAP = "com.pega.pegarules.integration.engine.internal.services.soap.SOAPService"
REST = "com.pega.pegarules.integration.engine.internal.services.rest.RESTServiceRequestProcessorImpl"
HTTP = "com.pega.pegarules.integration.engine.internal.client.http.HTTPClientConnectorRequestProcessor"
EMAIL = "com.pega.pegarules.integration.engine.internal.services.email.EmailListenerServiceImpl"
AUTHN = "com.pega.pegarules.session.internal.authentication.service.AuthenticationServiceProviderImpl"
AUTHZ = "com.pega.pegarules.session.internal.authorization.context.AuthorizationContextManagerImpl"
SECEVT = "com.pega.pegarules.web.security.SecurityEventLogger"
EXEC = "com.pega.pegarules.session.internal.mgmt.Executable"
DB = "com.pega.pegarules.data.internal.access.DatabaseImpl"
PASSIVATE = "com.pega.pegarules.session.internal.mgmt.base.ThreadPassivation"
JOBS = "com.pega.platform.executor.jobscheduler.scheduler.internal.JobSchedulerImplementationService"
QP = "com.pega.platform.executor.queueprocessor.internal.QueueProcessorExecutionService"
ALERT = "com.pega.pegarules.priv.PegaAlertLogger"
SECALERT = "com.pega.pegarules.priv.PegaSecurityAlertLogger"
HAZEL = "com.pega.pegarules.cluster.internal.PRClusterHazelcastImpl"
HEALTH = "com.pega.platform.clustermanagement.internal.ClusterHealthMonitorService"
# Each Pega log's loggers, and how often each writes.
LOGS = {
    "Integration": [(SOAP, 4), (REST, 4), (HTTP, 3), (EMAIL, 1)],
    "Security": [(AUTHN, 4), (AUTHZ, 2), (SECEVT, 2)],
    "App": [(EXEC, 4), (DB, 4), (PASSIVATE, 1), (JOBS, 2), (QP, 2)],
    "Alert": [(ALERT, 3), (SECALERT, 1)],
    "Cluster": [(HAZEL, 2), (HEALTH, 2)],
}
LOG_WEIGHTS = {"Integration": 5, "Security": 3, "App": 6, "Alert": 2, "Cluster": 1}
PEGA_ALERTS = [("PEGA0001", "HTTP interaction time exceeds limit"), ("PEGA0004", "Quantity of data received by "
               "database query exceeds limit"), ("PEGA0005", "Query time exceeds limit"),
               ("PEGA0020", "Total connect interaction time exceeds limit")]
SECU_ALERTS = [("SECU0001", "Unexpected properties received in HTTP request"), ("SECU0008", "Invalid URL "
               "parameters"), ("SECU0019", "Unauthorized request detected")]
SOAP_SERVICES = [("CustomerService", "CreateCase"), ("CustomerService", "GetCaseStatus"),
                 ("ClaimsService", "SubmitClaim")]
REST_PATHS = [("POST", "/prweb/api/v1/cases"), ("GET", "/prweb/api/v1/cases/{id}"), ("PATCH", "/prweb/api/v1/"
              "assignments/{id}"), ("GET", "/prweb/api/v1/data/D_CustomerSummary")]
ENDPOINTS = ["https://crm.corp.example/api/customers", "https://payments.corp.example/v2/authorise",
             "https://address.corp.example/lookup"]


def requestor():
    return "H" + "".join(r.choice("0123456789ABCDEF") for _ in range(32))


def base(log_type, logger, t):
    node, node_type = r.choice(NODES)
    batch = node_type == "BackgroundProcessing"
    return {"@timestamp": t.strftime("%Y-%m-%dT%H:%M:%S.") + f"{t.microsecond // 1000:03d}Z",
            "log_type": log_type, "logger_name": logger, "level": "INFO",
            "thread_name": r.choice(["PRPCWorkManager : DefaultWorkManager", "Batch-Executor-" + str(r.randint(1, 8))])
            if batch else f"http-nio-8080-exec-{r.randint(1, 40)}",
            "source_host": node, "nodeId": node, "nodeType": node_type,
            "pegathread": "STANDARD" if not batch else "BATCH", "app": "CustomerService",
            "RequestorId": requestor(), "userid": r.choice(services if batch else users),
            "CorrelationId": str(uuid.UUID(int=r.getrandbits(128)))}


def detail(rec):
    logger = rec["logger_name"]
    n = r.randint(5, 4000)
    if logger == SOAP:
        service, op = r.choice(SOAP_SERVICES)
        rec.update(serviceName=service, serviceMethod=op, elapsedMs=n, status=r.choice(["Success"] * 4 + ["Fault"]))
        # The SOAP request or response itself, as Pega's service logging writes it into the message.
        ns = "cus" if service == "CustomerService" else "clm"
        if r.random() < .5:
            body = (f"<{ns}:{op}Request><{ns}:CustomerId>C{r.randint(10000, 99999)}</{ns}:CustomerId>"
                    f"<{ns}:Channel>{r.choice(['web', 'branch', 'mobile'])}</{ns}:Channel>"
                    f"<{ns}:RequestedBy>{rec['userid']}</{ns}:RequestedBy></{ns}:{op}Request>")
            said = "Request"
        elif rec["status"] == "Fault":
            body = (f"<soapenv:Fault><faultcode>soapenv:Server</faultcode><faultstring>"
                    f"{r.choice(['Case not found', 'Validation failed', 'Service unavailable'])}"
                    f"</faultstring></soapenv:Fault>")
            said = "Response"
        else:
            body = (f"<{ns}:{op}Response><{ns}:CaseId>C-{r.randint(1000, 99999)}</{ns}:CaseId>"
                    f"<{ns}:Status>{r.choice(['Open', 'Pending-Review', 'Resolved'])}</{ns}:Status>"
                    f"</{ns}:{op}Response>")
            said = "Response"
        rec["message"] = (f"{said}: <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                          f"xmlns:{ns}=\"urn:example:{ns}:v2\"><soapenv:Header><{ns}:CorrelationId>"
                          f"{rec['CorrelationId']}</{ns}:CorrelationId></soapenv:Header><soapenv:Body>{body}"
                          f"</soapenv:Body></soapenv:Envelope>")
    elif logger == REST:
        method, path = r.choice(REST_PATHS)
        status = r.choice([200, 200, 201, 400, 404, 500])
        rec.update(httpMethod=method, path=path, statusCode=status, elapsedMs=n)
        rec["message"] = f"{method} {path} returned {status} in {n} ms"
    elif logger == HTTP:
        url = r.choice(ENDPOINTS)
        status = r.choice([200, 200, 200, 503, 504])
        rec.update(endpoint=url, statusCode=status, elapsedMs=n)
        rec["message"] = f"Connect-REST call to {url} returned {status} after {n} ms"
        if status >= 500:
            rec["level"] = "ERROR"
    elif logger == EMAIL:
        rec.update(mailbox="cases@corp.example", messageCount=r.randint(0, 12))
        rec["message"] = f"Email listener polled cases@corp.example: {rec['messageCount']} messages"
    elif logger == AUTHN:
        outcome = r.choice(["success"] * 4 + ["failure"])
        rec.update(authScheme=r.choice(["SAML2", "OIDC", "Basic"]), outcome=outcome,
                   clientIp=f"10.20.{r.randint(0, 9)}.{r.randint(1, 254)}")
        rec["message"] = f"Authentication {outcome} for operator {rec['userid']} using {rec['authScheme']}"
        if outcome == "failure":
            rec["level"] = "WARN"
            rec["reason"] = r.choice(["Invalid credentials", "Account locked", "Token expired"])
    elif logger == AUTHZ:
        rec.update(accessGroup=r.choice(["CustomerService:Users", "CustomerService:Managers",
                                         "CustomerService:Administrators"]),
                   privilege=r.choice(["pxAccessHistory", "pyCanCreateCase", "pzCanExportReports"]),
                   granted=r.choice([True, True, True, False]))
        rec["message"] = (f"Privilege {rec['privilege']} {'granted' if rec['granted'] else 'denied'} to "
                          f"{rec['userid']} in {rec['accessGroup']}")
    elif logger == SECEVT:
        event = r.choice(["Operator record change", "Access group change", "Password change", "Ruleset unlock"])
        rec.update(eventCategory="Security administration event", eventType=event,
                   target=r.choice(users))
        rec["message"] = f"{event} by {rec['userid']} for {rec['target']}"
    elif logger == EXEC:
        rec.update(activity=r.choice(["pyStartCase", "pzRefreshDataPage", "CreateWorkObject"]),
                   stepCount=r.randint(1, 30), elapsedMs=n)
        rec["level"] = r.choice(["INFO", "INFO", "DEBUG", "WARN"])
        rec["message"] = f"Activity {rec['activity']} finished after {rec['stepCount']} steps in {n} ms"
    elif logger == DB:
        rec.update(table=r.choice(["pc_work", "pc_assign_worklist", "pr_data_customer"]), elapsedMs=n,
                   rowCount=r.randint(0, 5000))
        rec["level"] = "DEBUG"
        rec["message"] = f"Query against {rec['table']} returned {rec['rowCount']} rows in {n} ms"
    elif logger == PASSIVATE:
        rec.update(passivatedRequestors=r.randint(1, 40))
        rec["message"] = f"Passivated {rec['passivatedRequestors']} idle requestors"
    elif logger == JOBS:
        rec.update(jobName=r.choice(["pyClusterAndDBCleaner", "pzUpdateDataFlowRuns", "SLAEscalation"]),
                   result=r.choice(["Completed", "Completed", "Failed"]), elapsedMs=n)
        rec["message"] = f"Job scheduler {rec['jobName']} {rec['result'].lower()} in {n} ms"
        if rec["result"] == "Failed":
            rec["level"] = "ERROR"
    elif logger == QP:
        rec.update(queueProcessor=r.choice(["pyProcessNotification", "pzStandardProcessor"]),
                   itemsProcessed=r.randint(0, 200), itemsFailed=r.randint(0, 3))
        rec["message"] = (f"Queue processor {rec['queueProcessor']} processed {rec['itemsProcessed']} items, "
                          f"{rec['itemsFailed']} failed")
    elif logger == ALERT:
        code, text = r.choice(PEGA_ALERTS)
        threshold = r.choice([500, 1000, 2000])
        rec.update(alertType=code, kpiValue=threshold + n, kpiThreshold=threshold)
        rec["level"] = "WARN"
        rec["message"] = f"{code}*{text}*{threshold + n} ms exceeds {threshold} ms"
    elif logger == SECALERT:
        code, text = r.choice(SECU_ALERTS)
        rec.update(alertType=code, clientIp=f"10.20.{r.randint(0, 9)}.{r.randint(1, 254)}")
        rec["level"] = "WARN"
        rec["message"] = f"{code}*{text}* from {rec['clientIp']}"
    elif logger == HAZEL:
        rec.update(clusterMembers=r.choice([3, 3, 3, 2]))
        rec["message"] = f"Cluster membership changed: {rec['clusterMembers']} members"
        if rec["clusterMembers"] < 3:
            rec["level"] = "WARN"
    elif logger == HEALTH:
        rec.update(healthStatus=r.choice(["HEALTHY"] * 5 + ["DEGRADED"]), heapUsedPercent=r.randint(30, 95))
        rec["message"] = f"Node health {rec['healthStatus']}, heap {rec['heapUsedPercent']}%"
    return rec


lines = []
t = dt.datetime(2026, 11, 19, 1, 0, 0, tzinfo=dt.timezone.utc)
order = [(log_type, logger) for log_type, loggers in LOGS.items() for logger, _ in loggers]
for i in range(600):
    t += dt.timedelta(milliseconds=r.randint(100, 6000))
    if i < len(order):
        # Each logger writes at least once, in the first records.
        log_type, logger = order[i]
    else:
        log_type = r.choices(list(LOG_WEIGHTS), list(LOG_WEIGHTS.values()))[0]
        loggers = LOGS[log_type]
        logger = r.choices([name for name, _ in loggers], [w for _, w in loggers])[0]
    inner = detail(base(log_type, logger, t))
    written = t - dt.timedelta(milliseconds=r.randint(5, 900))
    orig_ns = int(written.timestamp()) * 1_000_000_000 + written.microsecond * 1000 + r.randint(0, 999)
    lines.append(json.dumps({"time": int(t.timestamp() * 1000), "host": inner["source_host"],
                             "service": "pega-platform",
                             "fields": {"orig_rt": orig_ns, "collector": r.choice(["fwd-01", "fwd-02"]),
                                        "env": "prod"},
                             "event": json.dumps(inner)}))
with open(f"{out}/pega-loggers.log", "w") as f:
    f.write("\n".join(lines) + "\n")
