Each Event becomes one CEF (ArcSight Common Event Format) line: `CEF:0|Device Vendor|Device Product|Device Version|Device Event Class ID|Name|Severity|` then key=value pairs. The lines are written as text, one an Event.
Keys outside ArcSight's CEF dictionary are not used: ArcSight indexes only its dictionary's keys. Its custom slots (cs1-cs6 and the rest) carry the values no standard key fits, each named by its Label key.

### Header

| Position | CEF field | From | Example |
| --- | --- | --- | --- |
| 1 | Version | the constant 'CEF:0' | CEF:0 |
| 2 | Device Vendor | the constant 'Acme' |  |
| 3 | Device Product | the constant 'Mail' |  |
| 4 | Device Version | the constant '1' |  |
| 5 | Device Event Class ID | `EventDetail/TypeId` |  |
| 6 | Name | `EventDetail/Description` |  |
| 7 | Severity | the constant '3' |  |

### Every event

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| `EventTime/TimeCreated` | rt | deviceReceiptTime | When the event happened; milliseconds since 1970 |  |

### Send, Receive events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| `EventDetail/(Send\|Receive)/Source/User/Id` | suser | sourceUserName | Source user: the account that acted; at most 1023 characters | alice (Send); bob (Receive) |
| `EventDetail/(Send\|Receive)/Destination/User/Id` | duser | destinationUserName | Destination (target) user: the account acted on; at most 1023 characters | carol |

### View events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| `EventDetail/View/Document/Title` | fname | fileName | File's name; at most 1023 characters |  |
