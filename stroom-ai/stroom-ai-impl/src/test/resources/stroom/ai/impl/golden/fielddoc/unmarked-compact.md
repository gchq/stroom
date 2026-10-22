### EventSource

Every event carries these. From is the mapping; Sample values are what the sample's events were written, 2 events in all.

| XPath | Description | From | Sample values |
| --- | --- | --- | --- |
| `EventTime/TimeCreated` | This element contains information about the time the event was created. | `time` | "2026-09-28T10:00:00.000Z"<br>"2026-09-28T10:01:00.000Z" |
| `EventSource/System/Name` | The name of the system. | "Acme VPN" | "Acme VPN" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "vpnd" | "vpnd" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host`, or "unknown" when empty | "unknown"<br>"ws01" |
| `EventSource/Client/IPAddress` | The network IP address of the device, e.g. 192.168.0.3. | `ip` (logoff) | (not in the sample) |
| `EventSource/User/Id` | An identifier for the object, e.g a document ID in a document management system. | `user` | "o'neil"<br>(logon events only) |

### Event types

One row per rule: its conditions, what it writes, how many of the sample's events it wrote, and each EventDetail element as path <- from = a sampled value.

| Rule | TypeId | Description | Events | EventDetail |
| --- | --- | --- | --- | --- |
| **logon**<br>`action` = login | "Logon" | `result`<br>"fail" | 1 of 2 | `Authenticate/Action` <- "Logon" = "Logon"<br>`Authenticate/User/Id` <- `user` = "o'neil"<br>`Authenticate/Outcome/Success` <- `result`: ok -> true, fail -> false = "false"<br>`Authenticate/Data[@Name='session']/@Value` <- `sid` = "s1" |
| **logoff**<br>`action` = logout | "Logon" |  | 0 of 2<br>(not in the sample) | `Authenticate/Action` <- "Logoff"<br>`Authenticate/User/Id` <- `user`<br>`Authenticate/Outcome/Success` <- `result`: ok -> true, fail -> false<br>`Authenticate/Data[@Name='session']/@Value` <- `sid` |
| **other**<br>any other record | `action`<br>"keepalive" |  | 1 of 2 | `Unknown/Data[@Name='action']/@Value` <- `action` = "keepalive" |
