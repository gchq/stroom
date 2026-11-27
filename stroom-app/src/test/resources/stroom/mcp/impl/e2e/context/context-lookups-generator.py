# Synthetic Windows-style events with context data for Stroom AI's context lookup e2e test (TestE2eContextLookups).
# Not real data: event XML parts, each sent with a Context child holding event metadata entries (one per provider, event
# id and version: a description template with %1..%n insertion strings, a keyword mask -> name table, and the opcode
# and task names). Each part's context is its own: the version-changing provider has both its versions' entries in
# both parts, and one template is worded differently in each part, so a lookup that read another part's context would
# write a different description.
#
# The same entries are also written as JSON context data (TestE2eContextLookups' JSON variant): one object wrapping an
# array of entries, the event id and version JSON numbers, the keywords both a table of {mask, name} objects and an
# object keyed by mask. The records, and so what each should say, are the same; writing it draws nothing from the
# seeded random numbers, so the XML files are as they were.
#
# Usage: python3 context-lookups-generator.py <dir>
# Writes <dir>/context-parts.json ([{sample, context}], one per part, XML context), <dir>/context-parts-json.json (the
# same samples, JSON context) and <dir>/context-expected.json (per part, per record: description, keyword names and
# opcode name, computed here independently of Stroom and the tools).
import json, os, random, re, sys, datetime as dt

out = sys.argv[1]
r = random.Random(5848)
NS = "http://schemas.microsoft.com/win/2004/08/events/event"
SECURITY = "Microsoft-Windows-Security-Auditing"
CONTOSO = "Contoso-Inventory-Service"
FABRIKAM = "Fabrikam-Backup-Agent"
GUIDS = {SECURITY: "{54849625-5478-4994-A5BA-3E3B0328C30D}", CONTOSO: "{0B7A6E1C-1A2B-4C3D-8E9F-001122334455}",
         FABRIKAM: "{9F8E7D6C-5B4A-4321-9ABC-DEF012345678}"}
# Each provider's keywords: mask -> name (bit 63 among them).
KEYWORDS = {
    SECURITY: [("0x10000000000000", "Audit Failure"), ("0x20000000000000", "Audit Success"),
               ("0x8000000000000000", "Classic")],
    CONTOSO: [("0x1", "Startup"), ("0x2", "Configuration"), ("0x4000000000000000", "Diagnostic")],
    FABRIKAM: [("0x10", "Job"), ("0x20", "Storage")],
}
# (provider, event id, version): (template by part, or one for both; opcode value, opcode name, task name,
# keyword mask of the records, insertion string names).
EVENTS = {
    (SECURITY, "4624", "2"): ("An account was successfully logged on. Subject: %1\\%2. New logon: %6\\%5 from "
                              "workstation %10 (logon type %9). Subject again: %1.",
                              "0", "Info", "Logon", "0x8020000000000000",
                              ["SubjectUserSid", "SubjectUserName", "SubjectDomainName", "SubjectLogonId",
                               "TargetUserName", "TargetDomainName", "TargetLogonId", "LogonProcessName",
                               "LogonType", "WorkstationName"]),
    (SECURITY, "4625", "0"): ("An account failed to log on. Account: %2\\%1. Reason: %3.",
                              "0", "Info", "Logon", "0x8010000000000000",
                              ["TargetUserName", "TargetDomainName", "FailureReason"]),
    (SECURITY, "4634", "0"): (["An account was logged off. Account: %1, logon ID %2.",
                               "The account %1 (logon ID %2) was logged off."],
                              "0", "Info", "Logoff", "0x8020000000000000",
                              ["TargetUserName", "TargetLogonId"]),
    (CONTOSO, "100", "0"): ("Inventory service started on %1.",
                            "1", "Start", "Service", "0x1", ["HostName"]),
    (CONTOSO, "100", "1"): ("Inventory service %2 started on %1 with configuration %3.",
                            "1", "Begin", "Service", "0x4000000000000003", ["HostName", "ServiceVersion", "ConfigFile"]),
    (FABRIKAM, "100", "0"): ("Backup job %1 finished: %2 files.",
                             "2", "Stop", "Job", "0x30", ["JobName", "FileCount"]),
}
# The events each part's records are of: the version-changing provider is at version 0 in part 1, 1 in part 2.
PART_EVENTS = [
    [(SECURITY, "4624", "2"), (SECURITY, "4625", "0"), (SECURITY, "4634", "0"), (CONTOSO, "100", "0"),
     (FABRIKAM, "100", "0")],
    [(SECURITY, "4624", "2"), (SECURITY, "4634", "0"), (CONTOSO, "100", "1"), (FABRIKAM, "100", "0")],
]
USERS = ["alice", "bob", "carol", "dave", "svc-backup"]
HOSTS = ["ws01.corp.example", "ws02.corp.example", "srv01.corp.example"]


def template(key, part):
    given = EVENTS[key][0]
    return given[part] if isinstance(given, list) else given


def value(name, i):
    return {"SubjectUserSid": "S-1-5-18", "SubjectUserName": "WS01$", "SubjectDomainName": "CORP",
            "SubjectLogonId": "0x3e7", "TargetUserName": r.choice(USERS), "TargetDomainName": "CORP",
            "TargetLogonId": "0x%x" % r.randint(0x10000, 0xfffff), "LogonProcessName": "User32",
            "LogonType": str(r.choice([2, 3, 10])), "WorkstationName": "WS%02d" % r.randint(1, 30),
            "FailureReason": r.choice(["Unknown user name or bad password.", "Account locked out."]),
            "HostName": r.choice(HOSTS), "ServiceVersion": "2.%d" % r.randint(0, 9),
            "ConfigFile": "C:\\Contoso\\inventory-%d.json" % r.randint(1, 5),
            "JobName": "nightly-%d" % (i + 1), "FileCount": str(r.randint(10, 5000))}[name]


def esc(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def context(part):
    lines = ["<EventMetadata>"]
    # Every event of the providers this part's records come from, both versions of the version-changing one.
    for key in EVENTS:
        provider, event_id, version = key
        _, opcode, opcode_name, task, _, _ = EVENTS[key]
        lines.append(f'  <Event Provider="{provider}" EventID="{event_id}" Version="{version}">')
        lines.append(f"    <Description>{esc(template(key, part))}</Description>")
        lines.append(f"    <Opcode>{opcode_name}</Opcode>")
        lines.append(f"    <Task>{task}</Task>")
        lines.append("    <Keywords>")
        for mask, name in KEYWORDS[provider]:
            lines.append(f'      <Keyword Mask="{mask}" Name="{name}"/>')
        lines.append("    </Keywords>")
        lines.append("  </Event>")
    lines.append("</EventMetadata>")
    return "\n".join(lines) + "\n"


def json_context(part):
    # The same entries as context(part), as JSON.
    entries = []
    for key in EVENTS:
        provider, event_id, version = key
        _, opcode, opcode_name, task, _, _ = EVENTS[key]
        entries.append({"provider": provider, "event_id": int(event_id), "version": int(version),
                        "description": template(key, part), "opcode": opcode_name, "task": task,
                        "keywords": [{"mask": mask, "name": name} for mask, name in KEYWORDS[provider]],
                        "keyword_names": {mask: name for mask, name in KEYWORDS[provider]}})
    return json.dumps({"source": "context-lookups-generator", "events": entries}, indent=1, ensure_ascii=False) + "\n"


def filled(text, values):
    return re.sub(r"%(\d+)", lambda m: values[int(m.group(1)) - 1] if 1 <= int(m.group(1)) <= len(values)
                  else m.group(0), text)


def names(mask, table):
    value_ = int(mask, 16)
    found = []
    for m, name in table:
        bits = int(m, 16)
        if bits and value_ & bits == bits and name not in found:
            found.append(name)
    return found


parts = []
json_parts = []
expected = []
record_id = 1000
t = dt.datetime(2026, 11, 14, 9, 0, 0)
for part, keys in enumerate(PART_EVENTS):
    events = [f'<Events xmlns="{NS}">']
    wanted = []
    for i in range(12):
        key = keys[i % len(keys)] if i < len(keys) else r.choice(keys)
        provider, event_id, version = key
        _, opcode, opcode_name, task, mask, data_names = EVENTS[key]
        values = [value(name, i) for name in data_names]
        t += dt.timedelta(seconds=r.randint(1, 90), milliseconds=r.randint(0, 999))
        record_id += 1
        events.append("<Event>")
        events.append(f'<System><Provider Name="{provider}" Guid="{GUIDS[provider]}"/><EventID>{event_id}</EventID>'
                      f"<Version>{version}</Version><Level>{'0' if provider == SECURITY else '4'}</Level>"
                      f"<Task>{12544 if provider == SECURITY else 1}</Task><Opcode>{opcode}</Opcode>"
                      f"<Keywords>{mask}</Keywords><TimeCreated SystemTime=\"{t:%Y-%m-%dT%H:%M:%S}."
                      f"{t.microsecond // 1000:03d}Z\"/><EventRecordID>{record_id}</EventRecordID>"
                      f"<Computer>{r.choice(HOSTS)}</Computer></System>")
        events.append("<EventData>" + "".join(f'<Data Name="{n}">{esc(v)}</Data>' for n, v in zip(data_names, values))
                      + "</EventData>")
        events.append("</Event>")
        wanted.append({"record_id": str(record_id), "description": filled(template(key, part), values),
                       "keywords": names(mask, KEYWORDS[provider]), "opcode": opcode_name})
    events.append("</Events>")
    parts.append({"sample": "\n".join(events) + "\n", "context": context(part)})
    json_parts.append({"sample": "\n".join(events) + "\n", "context": json_context(part)})
    expected.append(wanted)

with open(os.path.join(out, "context-parts.json"), "w", encoding="utf-8") as f:
    json.dump(parts, f, indent=1, ensure_ascii=False)
    f.write("\n")
with open(os.path.join(out, "context-parts-json.json"), "w", encoding="utf-8") as f:
    json.dump(json_parts, f, indent=1, ensure_ascii=False)
    f.write("\n")
with open(os.path.join(out, "context-expected.json"), "w", encoding="utf-8") as f:
    json.dump(expected, f, indent=1, ensure_ascii=False)
    f.write("\n")
