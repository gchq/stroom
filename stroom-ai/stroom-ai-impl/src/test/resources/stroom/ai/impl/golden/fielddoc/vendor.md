### Common fields

Every event type writes these, from the same source. Sample values are what the sample's events were written, 2 events in all.

| XPath | Description | From | Sample values |
| --- | --- | --- | --- |
| `EventTime/TimeCreated` | Time the event was created. | `time` | "2026-09-28T10:00:00.000Z"<br>"2026-09-28T10:01:00.000Z" |
| `EventSource/System/Name` | The name of the system. | "Acme VPN" | "Acme VPN" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "vpnd" | "vpnd" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host`, or "unknown" when empty | "unknown"<br>"ws01" |
| `EventSource/User/Id` | Login of the account that signed in. | `user` | "o'neil" |

### Event types

#### logon

When `action` = login: 1 of the sample's 2 events.

| XPath | Description | From | Sample values |
| --- | --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | "Logon" | "Logon" |
| `EventDetail/Authenticate/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" | "Logon" |
| `EventDetail/Authenticate/User/Id` | Login of the account that signed in. | `user` | "o'neil" |
| `EventDetail/Authenticate/Outcome/Success` | If the outcome of an event was successful then 'true', 'false' otherwise. | `result`: ok -> true, fail -> false | "false" |
| `EventDetail/Authenticate/Data[@Name='session']/@Value` | VPN session. Codes: s1: first session. | `sid` | "s1" |

#### other

Any other record: 1 of the sample's 2 events.

| XPath | Description | From | Sample values |
| --- | --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | `action` | "keepalive" |
| `EventDetail/Unknown/Data[@Name='action']/@Value` | The `action` value recorded in a Data element of Unknown. | `action` | "keepalive" |

