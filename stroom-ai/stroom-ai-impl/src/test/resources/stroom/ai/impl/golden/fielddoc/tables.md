### Common fields

Every event type writes these, from the same source.

| XPath | Description | From |
| --- | --- | --- |
| `EventTime/TimeCreated` | Time the event was created. | `time` |
| `EventSource/System/Name` | The name of the system. | "Acme VPN" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "vpnd" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host`, or "unknown" when empty |
| `EventSource/User/Id` | An identifier for the user. | `user` |

### Event types

Left untranslated on purpose: `keepalive` (`action` = keepalive).

#### logon

When `action` in login, logon.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | "Logon" |
| `EventSource/Client/IPAddress` | The network IP address of the client. | `ip` |
| `EventDetail/Authenticate/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" |
| `EventDetail/Authenticate/User/Id` | An identifier for the user. | `user` |
| `EventDetail/Authenticate/Outcome/Success` | If the outcome of an event was successful then 'true', 'false' otherwise. | `result`: ok -> true, fail -> false |
| `EventDetail/Authenticate/Data[@Name='session']/@Value` | The `session` value recorded in a Data element of Authenticate. | `sid` |

#### other

Any other record.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | `action` |
| `EventDetail/Unknown/Data[@Name='action']/@Value` | The `action` value recorded in a Data element of Unknown. | `action` |

