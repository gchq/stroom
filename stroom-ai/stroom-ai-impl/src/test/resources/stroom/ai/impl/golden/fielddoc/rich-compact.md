Each record holds several events: one per `items` item. Sources marked "of the record" read the record round the items.

### Kept as Unknown

- `rest` (records no other rule matches): anything else

### Left untranslated

- `host` (of the record) matches `^qa-`: QA traffic

### Written by shared XSLTs

- `EventSource/Device`: the named template `eventSource` of `Common-V1`, given `ip` = `ip`

### EventSource

Every event carries these; From is the mapping.

| XPath | Description | From |
| --- | --- | --- |
| `EventTime/TimeCreated` | This element contains information about the time the event was created. | `ts` (time yyyy-MM-dd HH:mm:ss, UTC) |
| `EventSource/System/Name` | The name of the system. | "App" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "app" |
| `EventSource/User/Id` | An identifier for the object, e.g a document ID in a document management system. | first of `user`, `who` (lower), or "nobody" when empty |
| `EventSource/User/Domain` | The security domain that the user exists within, e.g. an Active Directory or OpenLDAP domain. | `dom` (of the record) (Europe/London) |

### Event types

One row per rule: its conditions and what it writes, each EventDetail element as path <- from.

| Rule | TypeId | Description | EventDetail |
| --- | --- | --- | --- |
| **logon**<br>`what` in dictionary `Logons` and `ok` present | "Logon" |  | `Authenticate/Action` <- "Logon"<br>`Authenticate/User/Id` <- lookup `USERS` by `user` -> `name`<br>`Authenticate/Outcome/Success` <- `ok`: yes -> true, no -> false; otherwise false<br>`Authenticate/Outcome/Description` <- `why` via dictionary `Reasons`<br>`Authenticate/Data[@Name='roles']/@Value` <- `roles`, one per value |
| **nothing**<br>`what` empty |  | Left untranslated on purpose |  |
| **rest**<br>any other record | lookup `TYPES` by `t` |  | `Unknown/Data[@Name='what']/@Value` <- `what` |
