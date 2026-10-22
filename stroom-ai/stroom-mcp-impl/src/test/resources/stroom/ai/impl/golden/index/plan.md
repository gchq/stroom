Documents for `acme-v1` (elasticsearch); the time field is `@timestamp`.

Events left out of the index:

- `EventDetail/TypeId = 'hb'`

Sample values are what the 3 documents written from the sample got.

### Common fields

Any kind of event can have these: from EventTime, EventSource, the event type, and the ids.

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `StreamId` | The id of the Events stream the event came from. | long | `@StreamId` | always | `7`, `8` |
| `@timestamp` | Time the event was created. | date | `EventTime/TimeCreated` | not in the sample | (none in the sample) |
| `event.code` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | keyword | `EventDetail/TypeId` | not in the sample | (none in the sample) |
| `user.id` | An identifier for the user. Several values per document. | keyword | `EventSource/User/Id` | 100% of events | `alice`, `bob`, `carol` (+2) |
| `host.ip` | The network IP address of the device, e.g. 192.168.0.3. | ip | `EventSource/Device/IPAddress` | not in the sample | `10.0.0.1` |

### Event types

Each from the action element that kind of event has: only those events have them.

#### Send, Receive

In 30% (Send), 20% (Receive) of the sample's events.

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `email.from` | An identifier for the user. | keyword | `EventDetail/Send/Source/User/Id` | 30% of events | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` |
| `email.to` | An identifier for the user. | keyword | `EventDetail/Send/Destination/User/Id` | not in the sample | (none in the sample) |
| `inbox.from` | An identifier for the user. | keyword | `EventDetail/Receive/Source/User/Id` | 20% of events | (none in the sample) |
| `inbox.to` | An identifier for the user. | keyword | `EventDetail/Receive/Destination/User/Id` | not in the sample | (none in the sample) |

#### View

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `file.name` | The title or name of the document. | keyword | `EventDetail/View/Document/Title` | not in the sample | (none in the sample) |

#### Network

In 66.6667% of the sample's events.

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `net.src` | The network IP address of the device, e.g. 192.168.0.3. | ip | `EventDetail/Network/*/Source/Device/IPAddress` | 79.1667% of events | `1.2.3.4` |

#### Authenticate

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `agent` | The `agent` value recorded in a Data element of Authenticate. | keyword | `EventDetail/Authenticate/Data[@Name='agent']/@Value` | not in the sample | (none in the sample) |
