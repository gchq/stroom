Documents for `acme-v1` (elasticsearch); the time field is `@timestamp`.

Events left out of the index:

- `EventDetail/TypeId = 'hb'`

Sample values are what the 3 documents written from the sample got.

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `StreamId` | The id of the Events stream the event came from. | long | `@StreamId` | always | `7`, `8` |
| `@timestamp` | Time the event was created. | date | `EventTime/TimeCreated` | not in the sample | (none in the sample) |
| `event.code` | A name/code/ID to uniquely identify the logical event type within the service providing the events. | keyword | `EventDetail/TypeId` | not in the sample | (none in the sample) |
| `email.from` | An identifier for the user. | keyword | `EventDetail/Send/Source/User/Id` | 30% of events | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` |
| `email.to` | An identifier for the user. | keyword | `EventDetail/Send/Destination/User/Id` | not in the sample | (none in the sample) |
| `inbox.from` | An identifier for the user. | keyword | `EventDetail/Receive/Source/User/Id` | 20% of events | (none in the sample) |
| `inbox.to` | An identifier for the user. | keyword | `EventDetail/Receive/Destination/User/Id` | not in the sample | (none in the sample) |
| `file.name` | The title or name of the document. | keyword | `EventDetail/View/Document/Title` | not in the sample | (none in the sample) |
| `net.src` | The network IP address of the device, e.g. 192.168.0.3. | ip | `EventDetail/Network/*/Source/Device/IPAddress` | 79.1667% of events | `1.2.3.4` |
| `user.id` | An identifier for the user. Several values per document. | keyword | `EventSource/User/Id` | 100% of events | `alice`, `bob`, `carol` (+2) |
| `host.ip` | The network IP address of the device, e.g. 192.168.0.3. | ip | `EventSource/Device/IPAddress` | not in the sample | `10.0.0.1` |
| `agent` | The `agent` value recorded in a Data element of Authenticate. | keyword | `EventDetail/Authenticate/Data[@Name='agent']/@Value` | not in the sample | (none in the sample) |
