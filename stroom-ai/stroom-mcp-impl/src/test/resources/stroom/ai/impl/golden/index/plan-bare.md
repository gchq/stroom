Documents for `acme-v1` (elasticsearch); the time field is `@timestamp`.

Events left out of the index:

- `EventDetail/TypeId = 'hb'`

### Common fields

Any kind of event can have these: from EventTime, EventSource, the event type, and the ids.

| Index field | Description | Type | From (event-logging path) |
| --- | --- | --- | --- |
| `StreamId` | The id of the Events stream the event came from. | long | `@StreamId` |
| `@timestamp` | Date/time when the event originated. | date | `EventTime/TimeCreated` |
| `event.code` | Identification code for this event. | keyword | `EventDetail/TypeId` |
| `user.id` | Unique identifier of the user. | keyword | `EventSource/User/Id` |
| `host.ip` | Host ip addresses. | ip | `EventSource/Device/IPAddress` |

### Event types

Each from the action element that kind of event has: only those events have them.

#### Send, Receive

| Index field | Description | Type | From (event-logging path) |
| --- | --- | --- | --- |
| `email.from` |  | keyword | `EventDetail/Send/Source/User/Id` |
| `email.to` |  | keyword | `EventDetail/Send/Destination/User/Id` |
| `inbox.from` |  | keyword | `EventDetail/Receive/Source/User/Id` |
| `inbox.to` |  | keyword | `EventDetail/Receive/Destination/User/Id` |

#### View

| Index field | Description | Type | From (event-logging path) |
| --- | --- | --- | --- |
| `file.name` | Name of the file including the extension, without the directory. | keyword | `EventDetail/View/Document/Title` |

#### Network

| Index field | Description | Type | From (event-logging path) |
| --- | --- | --- | --- |
| `net.src` |  | ip | `EventDetail/Network/*/Source/Device/IPAddress` |

#### Authenticate

| Index field | Description | Type | From (event-logging path) |
| --- | --- | --- | --- |
| `agent` | The `agent` value recorded in a Data element of Authenticate. | keyword | `EventDetail/Authenticate/Data[@Name='agent']/@Value` |
