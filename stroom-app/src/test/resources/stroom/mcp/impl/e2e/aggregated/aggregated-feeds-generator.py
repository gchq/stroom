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
# The Azure records' details (bodies, recipients, folders, members, properties and the like) come from a generator of
# their own, so adding them left every other value the same. Message bodies aren't in Microsoft's audit records: they
# are here to test long, nested, multi-line text needing escapes.
rd = random.Random(6062)
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

TEAMS = {"Ops": "6f1e2d3c-0a1b-4c2d-8e3f-4a5b6c7d8e9f", "Finance": "0b9a8c7d-6e5f-4a3b-9c2d-1e0f2a3b4c5d"}

NAMES = {x: " ".join(p.capitalize() for p in x.split(".")) for x in users}
SUBJECT_TOPICS = ["Q3 figures", "Invoice 4471 – overdue", "Re: roster", "Lunch?", "FW: Change freeze & CAB notes",
                  "Draft: \"Project Kōwhai\" proposal", "Incident 20261110-03 <action required>"]
GREETINGS = ["Hi {to},", "Hello {to},", "{to} –", "Morning all,", "G'day {to},"]
LINES = ["Please find the figures attached; the totals in column F are still provisional.",
         "Can you confirm the roster for next week? I've swapped Tue/Thu with Omar.",
         "The vendor says the invoice was paid on the 3rd, but our ledger shows $4,471.20 outstanding.",
         "CAB approved the change for Saturday 22:00–02:00 AEDT. Rollback plan is in section 4.",
         "Quick reminder: passwords must not be sent by email, even internally.",
         "See the diff below – the regex was `^(?<user>[^@]+)@contoso\\.example$` before the fix.",
         "Thanks — that's sorted. I'll close the ticket after the 5 p.m. check.",
         "Attaching the export (CSV, UTF-8). Some names have accents: Zoë, José, Søren.",
         "If the build fails again, ping #ops-oncall and don't retry more than twice.",
         "FYI the <Event> elements in the sample were missing their <System> child."]
# Text that has broken, or could break, a translation: markup and syntax the XSLT, XPath, XML or JSON handling must
# carry as plain text. Control characters other than tab, line feed and carriage return can't appear in XML 1.0 at
# all, so a record holding one tests what processing does with a value it can't write.
HOSTILE = ["Template test: {Event/EventTime/TimeCreated} and {{doubled}} and {$user} should stay as typed.",
           "Variables: $user, $(whoami), ${HOME}, %USERPROFILE%, and an XPath //Event[@id='1' or 1=1].",
           "Markup in text: <xsl:value-of select=\"'injected'\"/> <![CDATA[ inner ]]> ]]> <!-- not a comment --> <?pi x?>",
           "Entities as typed: &amp; &lt; &nbsp; &#x41; &unknown; & alone, and a lone < or >.",
           "Quotes: \"double\", 'single', `back`, “curly”, ‘curly’, and an apostrophe in O'Brien's.",
           "Backslashes: C:\\Users\\li.wei\\AppData, \\\\fileserver\\share, a trailing one\\",
           "Regex: ^(?:[a-z]+\\.){2,}$ | (a|b)* [^\\]] \\d{3}-\\d{4} .*?",
           "JSON inside text: {\"user\": \"omar\", \"roles\": [\"admin\", null], \"n\": 1e3}",
           "Right to left: مرحبا بالعالم — שלום, then left to right again.",
           "Astral characters: 𝔘𝔫𝔦𝔠𝔬𝔡𝔢, 👩🏽‍💻, 🇦🇺, and a combining é (e\u0301).",
           "Line and paragraph separators:\u2028here\u2029and a tab\there.",
           "Control characters: form feed \u000c, bell \u0007, escape \u001b[31mred\u001b[0m.",
           "A NUL in text: before\u0000after."]

SIGNATURES = ["Regards,\n{me}\nFinance Operations | Contoso\n+61 2 5550 1234",
              "Cheers,\n{me}", "--\n{me}\nSent from Outlook for iOS",
              "Kind regards,\n{me}\n\nThis email and any attachments are confidential. If you are not the intended "
              "recipient, please delete it and notify the sender."]
ATTACHMENTS = [("figures-Q3.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
               ("invoice_4471.pdf", "application/pdf"), ("roster (v2).docx",
               "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
               ("export.csv", "text/csv"), ("screenshot 2026-11-10 at 09.14.png", "image/png")]

def item_id():
    return "AAMk" + uuid.UUID(int=rd.getrandbits(128)).hex.upper()

def recipients(sender):
    # One to three people other than the sender, now and then someone outside the organisation.
    chosen = rd.sample([x for x in users if not x.startswith("svc-") and x + "@contoso.example" != sender], rd.randint(1, 3))
    out = [x + "@contoso.example" for x in chosen]
    if rd.random() < .1:
        out.append(rd.choice(["accounts@fabrikam.example", "j.smith@partner.example"]))
    return out

def member(name):
    return {"UPN": name + "@contoso.example", "DisplayName": NAMES[name], "Role": rd.choice([1, 1, 2])}

def display(address):
    return NAMES.get(address.split("@")[0], address)

def mail_body(sender, to, t):
    # A body of a few paragraphs, a signature, now and then a quoted earlier message: plain text or HTML.
    me = display(sender)
    first = display(to[0]).split(" ")[0]
    paragraphs = [rd.choice(GREETINGS).format(to=first)] + rd.sample(LINES, rd.randint(1, 4))
    if rd.random() < .25:
        paragraphs.insert(rd.randint(1, len(paragraphs)), rd.choice(HOSTILE))
    if rd.random() < .02:
        # Now and then a long body: a pasted log or report.
        paragraphs.append("\n".join(f"{i:04d} " + rd.choice(LINES + HOSTILE) for i in range(rd.randint(200, 600))))
    if rd.random() < .3:
        earlier = t - dt.timedelta(hours=rd.randint(1, 72))
        quoted = "\n".join("> " + line for line in rd.sample(LINES, rd.randint(1, 2)))
        paragraphs.append(f"On {earlier:%a %d %b %Y at %H:%M}, {first} <{to[0]}> wrote:\n{quoted}")
    signature = rd.choice(SIGNATURES).format(me=me)
    if rd.random() < .4:
        html = "".join("<p>" + escape(x).replace("\n", "<br/>") + "</p>" for x in paragraphs + [signature])
        return {"ContentType": "HTML", "Content": '<html><head><meta charset="utf-8"></head><body dir="ltr">'
                                                  + html + "</body></html>"}
    return {"ContentType": "Text", "Content": "\r\n\r\n".join(paragraphs + [signature]).replace("\n", "\r\n")}

def teams_body(to):
    # A Teams message: HTML, sometimes @mentioning a recipient, sometimes with a code block or an emoji.
    text = escape(rd.choice(LINES + HOSTILE) if rd.random() < .25 else rd.choice(LINES))
    mentions = []
    if rd.random() < .5:
        mentions.append({"MentionedUser": to[0], "DisplayName": display(to[0])})
        text = f'<at id="0">{escape(display(to[0]))}</at> ' + text
    if rd.random() < .2:
        text += "<pre><code>kubectl -n ops rollout restart deploy/api &amp;&amp; echo done</code></pre>"
    if rd.random() < .2:
        text += ' <emoji id="like" alt="👍" title="Like"></emoji>'
    return {"ContentType": "html", "Content": f"<div><p>{text}</p></div>", "Mentions": mentions}

def escape(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def guid():
    return str(uuid.UUID(int=rd.getrandbits(128)))

def azure_common(u):
    # Fields every record has in Microsoft's schema, and the app context some carry.
    common = {"UserKey": f"1003{rd.getrandbits(40):010X}", "UserType": 5 if u.startswith("svc-") else 0, "Version": 1}
    if rd.random() < .3:
        common["AppAccessContext"] = {"AADSessionId": guid(), "UniqueTokenId": uuid.UUID(int=rd.getrandbits(128)).hex[:22],
                                      "ClientAppId": guid(), "IssuedAtTime": None}
    return common

def azure_details(w, op, u, ev, t):
    if w == "ExchangeOnline":
        ev["MailboxGuid"] = guid()
        if op == "Send":
            to = recipients(u)
            ev["Item"].update({"Id": item_id(), "InternetMessageId": f"<{guid()}@contoso.example>",
                               "Subject": rd.choice(SUBJECT_TOPICS) if rd.random() < .9 else rd.choice(HOSTILE)[:80],
                               "ParentFolder": {"Id": item_id(), "Path": "\\Sent Items"},
                               "Recipients": to, "Body": mail_body(u, to, t)})
            if rd.random() < .4:
                ev["Item"]["Attachments"] = "; ".join(f"{name} ({rd.randint(10, 9000)}b)"
                                                      for name, _ in rd.sample(ATTACHMENTS, rd.randint(1, 3)))
                ev["Item"]["AttachmentDetails"] = [{"Name": name, "ContentType": kind, "SizeInBytes": rd.randint(10_000, 9_000_000)}
                                                   for name, kind in rd.sample(ATTACHMENTS, rd.randint(1, 2))]
        elif op == "MoveToDeletedItems":
            source = rd.choice(["\\Inbox", "\\Inbox", "\\Archive", "\\Sent Items", "\\Inbox\\Projects\\Kōwhai"])
            ev["Folder"] = {"Id": item_id(), "Path": source}
            ev["DestFolder"] = {"Id": item_id(), "Path": "\\Deleted Items"}
            ev["AffectedItems"] = [{"Id": item_id(), "InternetMessageId": f"<{guid()}@contoso.example>",
                                    "Subject": rd.choice(SUBJECT_TOPICS), "ParentFolder": {"Id": item_id(), "Path": source}}
                                   for _ in range(rd.randint(1, 4))]
        elif op == "MailItemsAccessed":
            ev["OperationProperties"] = [{"Name": "MailAccessType", "Value": rd.choice(["Bind", "Sync"])},
                                         {"Name": "IsThrottled", "Value": rd.choice(["False", "False", "True"])}]
            ev["Folders"] = [{"Id": item_id(), "Path": path,
                              "FolderItems": [{"InternetMessageId": f"<{guid()}@contoso.example>", "SizeInBytes": rd.randint(2000, 400000)}
                                              for _ in range(rd.randint(1, 3))]}
                             for path in rd.sample(["\\Inbox", "\\Sent Items", "\\Archive", "\\Drafts"], rd.randint(1, 2))]
            ev["OperationCount"] = sum(len(f["FolderItems"]) for f in ev["Folders"])
        elif op == "New-InboxRule":
            forward = rd.random() < .3
            ev["Parameters"] = [{"Name": "Name", "Value": rd.choice(["Move invoices", "FW to personal", ".", "Clean-up"])},
                                {"Name": "SubjectContainsWords", "Value": rd.choice(["invoice;payment", "password", "urgent"])},
                                {"Name": "ForwardTo" if forward else "MoveToFolder",
                                 "Value": rd.choice(["someone@gmail.example", "j.smith@partner.example"]) if forward
                                 else rd.choice(["RSS Feeds", "Archive", "Conversation History"])},
                                {"Name": "StopProcessingRules", "Value": "True"}]
    elif w in ("SharePoint", "OneDrive"):
        name = ev["SourceFileName"]
        ev.update({"ListItemUniqueId": guid(), "ItemType": "File", "SourceFileExtension": name.rsplit(".", 1)[1],
                   "ObjectId": ev["SiteUrl"] + "/Shared Documents/" + name, "WebId": guid(), "ListId": guid(),
                   "Platform": rd.choice(["WinDesktop", "MacDesktop", "iOS"]), "IsManagedDevice": rd.random() < .7})
        if op == "SharingSet":
            target = rd.choice(users)
            ev["TargetUserOrGroupName"] = target + "@contoso.example"
            ev["TargetUserOrGroupType"] = rd.choice(["Member", "Guest"])
            # SharePoint carries details as an XML fragment in a string.
            ev["EventData"] = (f"<PermissionsGranted>{rd.choice(['Read', 'Contribute', 'Full Control'])}</PermissionsGranted>"
                               f"<ExpirationDate>{(t + dt.timedelta(days=30)):%Y-%m-%dT%H:%M:%S}Z</ExpirationDate>"
                               f"<Type>{rd.choice(['Edit', 'View'])}</Type><MembersCanShare>true</MembersCanShare>")
    elif w == "AzureActiveDirectory":
        ev["ExtendedProperties"] = [{"Name": "UserAgent", "Value": rd.choice([
                                         "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Edg/131.0",
                                         "Microsoft Authentication Library (MSAL) for .NET"])},
                                    {"Name": "RequestType", "Value": rd.choice(["Login:login", "OAuth2:Token", "SAS:EndAuth"])},
                                    {"Name": "ResultStatusDetail", "Value": "Redirect" if op != "UserLoginFailed" else "UserError"}]
        ev["DeviceProperties"] = [{"Name": "OS", "Value": rd.choice(["Windows10", "MacOs", "Ios"])},
                                  {"Name": "BrowserType", "Value": rd.choice(["Edge", "Chrome", "Safari"])},
                                  {"Name": "IsCompliantAndManaged", "Value": rd.choice(["True", "False"])}]
        ev["Actor"] = [{"ID": guid(), "Type": 0}, {"ID": u, "Type": 5}]
        if op in ("Add member to role.", "Reset user password."):
            target = rd.choice([x for x in users if x + "@contoso.example" != u])
            ev["Target"] = [{"ID": "User_" + guid(), "Type": 2}, {"ID": target + "@contoso.example", "Type": 5}]
            if op == "Add member to role.":
                role = rd.choice(["Global Reader", "Exchange Administrator", "Security Reader"])
                ev["ModifiedProperties"] = [{"Name": "Role.DisplayName", "NewValue": role, "OldValue": ""},
                                            {"Name": "Role.TemplateId", "NewValue": guid(), "OldValue": ""}]
            else:
                ev["ModifiedProperties"] = [{"Name": "ForceChangePasswordNextLogin", "NewValue": "[true]", "OldValue": "[false]"}]
    else:
        ev["TeamGuid"] = TEAMS[ev["TeamName"]]
        if op == "MessageSent":
            to = recipients(u)
            ev.update({"MessageId": str(rd.randint(10**12, 10**13 - 1)), "ChatThreadId": f"19:{uuid.UUID(int=rd.getrandbits(128)).hex}@thread.tacv2",
                       "Recipients": to, "Body": teams_body(to)})
            if rd.random() < .3:
                ev["MessageURLs"] = [rd.choice(["https://contoso.sharepoint.example/sites/eng/Shared%20Documents/plan.docx",
                                                "https://status.example/incidents/20261110-03"])]
        elif op == "TeamMemberAdded":
            ev["Members"] = [member(added) for added in rd.sample([x for x in users if x != u.split("@")[0]], rd.randint(1, 2))]
        elif op == "MeetingParticipantDetail":
            joined = t - dt.timedelta(minutes=rd.randint(5, 90))
            ev["MeetingDetailId"] = guid()
            ev["Attendees"] = [{"UPN": x + "@contoso.example", "DisplayName": NAMES[x], "Role": rd.choice(["Organizer", "Attendee"]),
                                "JoinTime": f"{joined + dt.timedelta(seconds=rd.randint(0, 300)):%Y-%m-%dT%H:%M:%S}Z",
                                "LeaveTime": f"{t:%Y-%m-%dT%H:%M:%S}Z"}
                               for x in rd.sample([y for y in users if not y.startswith("svc-")], rd.randint(2, 4))]
            ev["DeviceInformation"] = rd.choice(["Teams desktop (Windows)", "Teams mobile (iOS)", "Browser (Edge)"])

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
        azure_details(w, op, u, ev, t)
        record = {"CreationTime": t.strftime("%Y-%m-%dT%H:%M:%S"), "Id": str(uuid.UUID(int=r.getrandbits(128))),
                  "Operation": op, "OrganizationId": "11111111-2222-3333-4444-555555555555",
                  "RecordType": r.choice([2, 6, 15, 25, 50]), "UserId": u, "ClientIP": f"203.0.113.{r.randint(1, 254)}",
                  "Workload": w, "event": ev}
        record.update(azure_common(u))
        lines.append(json.dumps(record, ensure_ascii=False))
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
