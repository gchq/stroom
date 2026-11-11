### EventSource

Every event carries these; From is the mapping.

| XPath | Description | From |
| --- | --- | --- |
| `EventTime/TimeCreated` | This element contains information about the time the event was created. | `time` |
| `EventSource/System/Name` | The name of the system. | "Acme VPN" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "vpnd" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host`, or "unknown" when empty |
| `EventSource/Client/IPAddress` | The network IP address of the device, e.g. 192.168.0.3. | `ip` (logon) |
| `EventSource/User/Id` | An identifier for the object, e.g a document ID in a document management system. | `user` |

### Event types

One row per rule: its conditions and what it writes, each EventDetail element as path <- from.

| Rule | TypeId | Description | EventDetail |
| --- | --- | --- | --- |
| **logon**<br>`action` in login, logon | "Logon" |  | `Authenticate/Action` <- "Logon"<br>`Authenticate/User/Id` <- `user`<br>`Authenticate/Outcome/Success` <- `result`: ok -> true, fail -> false<br>`Authenticate/Data[@Name='session']/@Value` <- `sid` |
| **keepalive**<br>`action` = keepalive |  | Left untranslated on purpose |  |
| **other**<br>any other record | `action` |  | `Unknown/Data[@Name='action']/@Value` <- `action` |
