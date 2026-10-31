Each record holds several events: one per `items` item. Sources marked "of the record" read the record round the items.

### Kept as Unknown

- `rest` (records no other rule matches): anything else

### Left untranslated

- `host` (of the record) matches `^qa-`: QA traffic

### Written by shared XSLTs

- `EventSource/Device`: the named template `eventSource` of `Common-V1`, given `ip` = `ip`

### Common fields

Every event type writes these, from the same source.

| XPath | Description | From |
| --- | --- | --- |
| `EventTime/TimeCreated` | Time the event was created. | `ts` (time yyyy-MM-dd HH:mm:ss, UTC) |
| `EventSource/System/Name` | The name of the system. | "App" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "app" |
| `EventSource/User/Id` | An identifier for the user. | first of `user`, `who` (lower), or "nobody" when empty |
| `EventSource/User/Domain` | The security domain that the user exists within, e.g. an Active Directory or OpenLDAP domain. | `dom` (of the record) (Europe/London) |

### Event types

Left untranslated on purpose: `nothing` (`what` empty).

#### logon

When `what` in dictionary `Logons` and `ok` present.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | "Logon" |
| `EventDetail/Authenticate/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" |
| `EventDetail/Authenticate/User/Id` | An identifier for the user. | lookup `USERS` by `user` -> `name` |
| `EventDetail/Authenticate/Outcome/Success` | If the outcome of an event was successful then 'true', 'false' otherwise. | `ok`: yes -> true, no -> false; otherwise false |
| `EventDetail/Authenticate/Outcome/Description` | Human readable text that describes the outcome. | `why` via dictionary `Reasons` |
| `EventDetail/Authenticate/Data[@Name='roles']/@Value` | The `roles` value recorded in a Data element of Authenticate. | `roles`, one per value |

#### rest

Any other record.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | lookup `TYPES` by `t` |
| `EventDetail/Unknown/Data[@Name='what']/@Value` | The `what` value recorded in a Data element of Unknown. | `what` |

