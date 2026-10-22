# CEF output for ArcSight

ArcSight ESM reads events as CEF (Common Event Format) lines, usually from a Kafka topic. A CEF output pipeline
reads the Events an events pipeline wrote and turns each into one flattened line of text, not CEF fields as XML
elements:

```
CEF:0|Device Vendor|Device Product|Device Version|Device Event Class ID|Name|Severity|key=value key=value ...
```

`draft_cef_mapping` drafts which Event value goes to which CEF key, saves the XSLT with that plan, and reviews an
existing CEF pipeline. `write_documentation` generates the pipeline's Field mapping section from the plan.

## Ask first: keys outside the dictionary

ArcSight indexes the keys in its CEF dictionary; a key outside it (`myCustomField1`) is usually not indexed, so is
of no use there. Before drafting, the user says whether such keys are allowed (`draft_cef_mapping` asks in a form,
unless the standing instructions say, e.g. "Custom CEF keys are not allowed"). The dictionary's own custom slots
are built-in keys and are used either way. Without keys outside the dictionary, an Event value that no standard key
and no free slot takes is **not sent**: the draft and the documentation list each one, per kind of event.

## The header

| Position | Field | By default |
| --- | --- | --- |
| 2 | Device Vendor | `EventSource/System/Organisation`, else ask the user |
| 3 | Device Product | `EventSource/System/Name` |
| 4 | Device Version | `EventSource/System/Version` |
| 5 | Device Event Class ID | `EventDetail/TypeId`, else the action |
| 6 | Name | `EventDetail/Description`, else the action element and its Action |
| 7 | Severity | 3 (Low), unless the user gives a rule: a constant, or an Event value mapped to 0-10 |

Header fields escape `\` and `|`; values the header carries are not sent again as keys.

## Common mappings

Standard keys come first; the first free key whose type fits the sample's values wins (src, dst and dvc take IPv4
addresses only, ports and sizes whole numbers). `*` is any one step: an action element (Authenticate, View...) or
Network's (Permit, Deny...). A kind of event with no Action element sends its kind (View, Create) as `act`.

| Event path | CEF key | ArcSight field | What it holds |
| --- | --- | --- | --- |
| `EventTime/TimeCreated` | rt | deviceReceiptTime | When the event happened (milliseconds since 1970) |
| `EventSource/Device/HostName` | dvchost | deviceHostName | Host name of the device that generated the event |
| `EventSource/Device/IPAddress` | dvc | deviceAddress | IPv4 address of the device that generated the event |
| `EventSource/Device/MACAddress` | dvcmac | deviceMacAddress | MAC address of the device that generated the event |
| `EventSource/Client/HostName` | shost | sourceHostName | Source host name |
| `EventSource/Client/IPAddress` | src | sourceAddress | Source IPv4 address |
| `EventSource/Client/MACAddress` | smac | sourceMacAddress | Source MAC address |
| `EventSource/Client/Port` | spt | sourcePort | Source port |
| `EventSource/Server/HostName` | dhost | destinationHostName | Destination (target) host name |
| `EventSource/Server/IPAddress` | dst | destinationAddress | Destination IPv4 address |
| `EventSource/Server/MACAddress` | dmac | destinationMacAddress | Destination MAC address |
| `EventSource/Server/Port` | dpt | destinationPort | Destination port |
| `EventSource/User/Id` | suser | sourceUserName | Source user: the account that acted |
| `EventSource/User/Domain` | sntdom | sourceNtDomain | Source user's domain |
| `EventDetail/*/Action` | act | deviceAction | Action the event records (e.g. Logon, View, blocked) |
| `EventDetail/*/Outcome/Success` | outcome | eventOutcome | Outcome: success or failure (success or failure; no Outcome is success) |
| `EventDetail/*/Outcome/Reason` | reason | reason | Why the outcome was what it was |
| `EventDetail/*/Outcome/Description` | reason | reason | Why the outcome was what it was |
| `EventDetail/*/User/Id` | duser | destinationUserName | Destination (target) user: the account acted on |
| `EventDetail/*/User/Domain` | dntdom | destinationNtDomain | Destination (target) user's domain |
| `EventDetail/*/Device/HostName` | dhost | destinationHostName | Destination (target) host name |
| `EventDetail/*/Device/IPAddress` | dst | destinationAddress | Destination IPv4 address |
| `EventDetail/*/Device/MACAddress` | dmac | destinationMacAddress | Destination MAC address |
| `EventDetail/*/File/Path` | filePath | filePath | File's full path |
| `EventDetail/*/File/Name` | fname | fileName | File's name |
| `EventDetail/*/File/Size` | fsize | fileSize | File's size |
| `EventDetail/*/File/Hash` | fileHash | fileHash | File's hash |
| `EventDetail/*/File/Type` | fileType | fileType | File's type |
| `EventDetail/*/Folder/Path` | filePath | filePath | File's full path |
| `EventDetail/*/Source/File/Path` | oldFilePath | oldFilePath | File's path before the change |
| `EventDetail/*/Source/File/Name` | oldFileName | oldFileName | File's name before the change |
| `EventDetail/*/Destination/File/Path` | filePath | filePath | File's full path |
| `EventDetail/*/Destination/File/Name` | fname | fileName | File's name |
| `EventDetail/*/Resource/URL` | request | requestUrl | URL requested |
| `EventDetail/Network/*/Source/Device/IPAddress` | src | sourceAddress | Source IPv4 address |
| `EventDetail/Network/*/Source/Device/HostName` | shost | sourceHostName | Source host name |
| `EventDetail/Network/*/Source/Device/MACAddress` | smac | sourceMacAddress | Source MAC address |
| `EventDetail/Network/*/Source/Port` | spt | sourcePort | Source port |
| `EventDetail/Network/*/Destination/Device/IPAddress` | dst | destinationAddress | Destination IPv4 address |
| `EventDetail/Network/*/Destination/Device/HostName` | dhost | destinationHostName | Destination (target) host name |
| `EventDetail/Network/*/Destination/Device/MACAddress` | dmac | destinationMacAddress | Destination MAC address |
| `EventDetail/Network/*/Destination/Port` | dpt | destinationPort | Destination port |
| `EventDetail/Network/*/TransportProtocol` | proto | transportProtocol | Transport protocol, e.g. TCP, UDP |
| `EventDetail/Network/*/ApplicationProtocol` | app | applicationProtocol | Application protocol, e.g. HTTP, SSH |
| `EventDetail/Network/*/Outcome/Success` | outcome | eventOutcome | Outcome: success or failure (success or failure; no Outcome is success) |
| `EventDetail/Network/*/ProcessName` | sproc | sourceProcessName | Source process name |
| `EventDetail/Process/ProcessId` | dpid | destinationProcessId | Destination process id |
| `EventDetail/Alert/Type` | cat | deviceEventCategory | Category the device gives the event |
| `EventDetail/Alert/Subject` | msg | message | Free text about the event |

## Custom slots

Values no standard key fits go to ArcSight's custom slots, each sent with its label (`cs1Label=Session id`), so
ArcSight shows what it holds. Slots are given out per kind of event: `cs3` may hold the folder in View events and
the ticket in Authenticate events, each labelled. A value is put in a slot of its type (numbers in cn, decimals in
cfp, times in deviceCustomDate) where one is free, else a string slot.

| Type | Keys | ArcSight fields |
| --- | --- | --- |
| string | cs1, cs2, cs3, cs4, cs5, cs6, flexString1, flexString2 | deviceCustomString1, deviceCustomString2, ... |
| long | cn1, cn2, cn3 | deviceCustomNumber1, deviceCustomNumber2, ... |
| float | cfp1, cfp2, cfp3, cfp4 | deviceCustomFloatingPoint1, deviceCustomFloatingPoint2, ... |
| time | deviceCustomDate1, deviceCustomDate2, flexDate1 | deviceCustomDate1, deviceCustomDate2, ... |

## Overrides, in AGENTS docs or from the user

Standing instructions may say where a value goes, one per line, which the draft applies before inferring the rest:

```
- EventDetail/Authorise/User/Name -> deviceCustomString6 (label: 'Authorised user')
- EventSource/System/Environment -> cs1
Custom CEF keys are not allowed.
Kafka topic: arcsight-secretserver
Pipeline template: CEF to ArcSight
```

Keys may be given by their short or full names (cs6 or deviceCustomString6). The user's own overrides
(`draft_cef_mapping overrides=[{path, key, label, event_type}]`, or `{path, drop: true}` to leave a value out) apply
on top. A template the instructions name is used for the pipeline whatever else exists.

## The line

Extension values escape `\` and `=`, and line breaks as `\n`; `|` needs no escape there. Times (`rt`, `start`,
`end`, custom dates) are milliseconds since 1970. Each value is cut to its key's length (cs1-cs6 4,000 characters,
most others 1,023, `act` and `outcome` 63). A key is sent only when its value is not empty, a slot's label only with
its value. Where a kind of event gives a key the common fields give too, its own value is sent.

## Through Kafka

The XSLT writes `kafka-records:1`: a `kafkaRecord` per Event (its `topic`, a `timestamp` from the event time, an
optional `key`), its `value` the CEF line. The `kafka-records v1.1` schema takes **one record a document**, so the
pipeline splits one Event a record (SplitFilter `splitCount` 1) before the XSLT; a split of more fails the schema
filter. A StandardKafkaProducer sends the records, through a KafkaConfig doc (the broker settings, the admin's): the
user picks one of the environment's (`draft_cef_mapping` lists them). Stepping sends nothing; processing does, so
processing a CEF pipeline is approved by the user with that in mind.

The pipeline comes from, in order: a template the standing instructions name; a forwarding template
(`find_pipeline_templates stage=forwarding`: pipelines with a StandardKafkaProducer), or the one existing CEF
pipelines inherit from; else `create_pipeline standalone='kafka'`, a pipeline of its own the user confirms.
For lines of text instead (`output=text`), the pipeline needs a TextWriter: from a template the instructions name or
an existing CEF pipeline's.

## Reviewing, or changing, a CEF pipeline

`draft_cef_mapping pipeline_uuid=<the pipeline> stream_ids=<Events streams>` steps it and parses what it writes:
headers that aren't seven fields, severities CEF doesn't have, keys outside the dictionary, a custom slot with no
label, values of another type or longer than their key takes, a key twice in a line. It matches each key to the
Event value it carries, and lists what each kind of event sends nowhere. A pipeline whose XSLT this server saved
comes back with its plan; one written by hand, with the mapping its lines imply, to check before saving a new XSLT
from it. To change one in production: `copy_pipeline` makes a working copy in a build, `draft_cef_mapping uuid=<its
XSLT> overrides=[...]` saves the change, then step, review and document it; `promote_build` writes it back.
