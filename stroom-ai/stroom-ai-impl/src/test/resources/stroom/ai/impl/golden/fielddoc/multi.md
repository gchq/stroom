Each record holds several events: one per `events` item. Sources marked "of the record" read the record round the items.

### Left untranslated

- `host` (of the record) matches `^qa-`: QA traffic
- `action` = HEARTBEAT: heartbeats are not events

### Common fields

Every event type writes these, from the same source.

| XPath | Description | From |
| --- | --- | --- |
| `EventTime/TimeCreated` | Time the event was created. | `ts` |
| `EventSource/System/Name` | The name of the system. | "App" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Eval" |
| `EventSource/Generator` | The thing that generated the event. | "audit" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host` (of the record) |
| `EventSource/User/Id` | An identifier for the user. | `user` |
| `EventSource/User/Groups/Group/Name` | The name of the group. | `roles`, one per value |

### Event types

#### logon, logoff

When `action` = LOGIN (logon). When `action` = LOGOUT (logoff).

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | `action` |
| `EventDetail/Authenticate/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" (logon)<br>"Logoff" (logoff) |
| `EventDetail/Authenticate/User/Id` | An identifier for the user. | `user` |
| `EventDetail/Authenticate/Data[@Name='batch']/@Value` | The `batch` value recorded in a Data element of Authenticate. | `batch` (of the record) |

