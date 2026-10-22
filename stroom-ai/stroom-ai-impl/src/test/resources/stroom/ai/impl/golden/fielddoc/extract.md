### Extracted fields

- `ts`, `user`, `action`, `desc`: parsed from `message` with `^(\S+) (\S+) (\S+) (.*)$`

### Common fields

Every event type writes these, from the same source.

| XPath | Description | From |
| --- | --- | --- |
| `EventTime/TimeCreated` | Time the event was created. | `ts` |
| `EventSource/System/Name` | The name of the system. | "App" |
| `EventSource/System/Environment` | The environment describes a specific instance of a system. | "Dev" |
| `EventSource/Generator` | The thing that generated the event. | "app" |
| `EventSource/Device/HostName` | The network host name of the device, e.g. someserver.somenet.org.uk. | `host` |
| `EventSource/User/Id` | An identifier for the user. | `user` |

### Event types

Left untranslated on purpose: `unparsed` (`ts` empty).

#### logon, logoff

When `action` = LOGIN (logon). When `action` = LOGOUT (logoff).

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/TypeId` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | `action` |
| `EventDetail/Description` | Some human-readable descriptive text for the event type. | `desc` |
| `EventDetail/Authenticate/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" (logon)<br>"Logoff" (logoff) |
| `EventDetail/Authenticate/User/Id` | An identifier for the user. | `user` |

