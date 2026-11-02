### Common fields

Every event type writes these, from the same source. Sample values are what the sample's events were written, 2 events in all.

| XPath | Description | From | Sample values |
| --- | --- | --- | --- |
| `EventTime/TimeCreated` | Time the event was created. | `time` | "2026-09-28T10:00:00.000Z"<br>"2026-09-28T10:01:00.000Z" |
| `EventSource/System/Name` | The name of the system. | "Acme VPN" | "Acme VPN" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "vpnd" | "vpnd" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host`, or "unknown" when empty | "unknown"<br>"ws01" |
| `EventSource/User/Id` | An identifier for the user. | `user` | "o'neil" |

### Event types

#### logon, logoff

When `action` = login (logon): 1 of the sample's 2 events. When `action` = logout (logoff): none in the sample.

| XPath | Description | From | Sample values |
| --- | --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | "Logon" | "Logon" |
| `EventDetail/Description` | Some human-readable descriptive text for the event type. | `result` (logon) | "fail" |
| `EventDetail/Authenticate/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" (logon)<br>"Logoff" (logoff) | "Logon" |
| `EventDetail/Authenticate/User/Id` | An identifier for the user. | `user` | "o'neil" |
| `EventDetail/Authenticate/Outcome/Success` | If the outcome of an event was successful then 'true', 'false' otherwise. | `result`: ok -> true, fail -> false | "false" |
| `EventDetail/Authenticate/Data[@Name='session']/@Value` | The `session` value recorded in a Data element of Authenticate. | `sid` | "s1" |
| `EventSource/Client/IPAddress` | The network IP address of the client. | `ip` (logoff) | (not in the sample) |

#### other

Any other record: 1 of the sample's 2 events.

| XPath | Description | From | Sample values |
| --- | --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | `action` | "keepalive" |
| `EventDetail/Unknown/Data[@Name='action']/@Value` | The `action` value recorded in a Data element of Unknown. | `action` | "keepalive" |

