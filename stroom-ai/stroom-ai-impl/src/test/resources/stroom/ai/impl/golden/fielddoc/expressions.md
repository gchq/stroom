### Common fields

Every event type writes these, from the same source.

| XPath | Description | From |
| --- | --- | --- |
| `EventTime/TimeCreated` | Time the event was created. | `time` |
| `EventSource/System/Name` | The name of the system. | "Acme VPN" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "vpnd" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host`, or "unknown" when empty |
| `EventSource/User/Id` | An identifier for the user. | `normalize-space(user)` |

### Event types

#### logon

When `concat(action, '-', a/b)` = login-x.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | "Logon" |
| `EventDetail/Authenticate/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" |
| `EventDetail/Authenticate/User/Id` | An identifier for the user. | `user` |
| `EventDetail/Authenticate/Outcome/Success` | If the outcome of an event was successful then 'true', 'false' otherwise. | `result`: ok -> true, fail -> false |
| `EventDetail/Authenticate/Data[@Name='session']/@Value` | The `session` value recorded in a Data element of Authenticate. | `sid` |

