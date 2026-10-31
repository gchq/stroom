Each Event becomes one CEF (ArcSight Common Event Format) line: `CEF:0|Device Vendor|Device Product|Device Version|Device Event Class ID|Name|Severity|` then key=value pairs. Each line is the value of one Kafka record on topic `t`, one Event a record.
Keys outside ArcSight's CEF dictionary are allowed.

### Header

| Position | CEF field | From | Example |
| --- | --- | --- | --- |
| 1 | Version | the constant 'CEF:0' | CEF:0 |
| 2 | Device Vendor | `EventSource/System/Organisation` |  |
| 3 | Device Product | `EventSource/System/Name` |  |
| 4 | Device Version | `EventSource/System/Version` |  |
| 5 | Device Event Class ID | `EventDetail/TypeId`, else the event's action element and its Action |  |
| 6 | Name | `EventDetail/Description`, else the event's action element and its Action |  |
| 7 | Severity | the constant '3' |  |

### Every event

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| `EventSource/Device/HostName` | dvchost | deviceHostName | Host name of the device that generated the event; at most 100 characters |  |
| `EventTime/TimeCreated` | rt | deviceReceiptTime | When the event happened; milliseconds since 1970 |  |
| `EventSource/Client/IPAddress` | cs1 | deviceCustomString1 | Custom string, named by its label; labelled 'Client IPAddress' (cs1Label = deviceCustomString1Label); at most 4000 characters |  |
| `EventSource/User/Id` | suser | sourceUserName | Source user: the account that acted; at most 1023 characters |  |
| `EventSource/Device/IPAddress` | dvc | deviceAddress | IPv4 address of the device that generated the event |  |
| `EventSource/System/Environment` | cs2 | deviceCustomString2 | Custom string, named by its label; labelled 'System Environment' (cs2Label = deviceCustomString2Label); at most 4000 characters |  |

### Authenticate events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| `EventDetail/Authenticate/Action` | act | deviceAction | Action the event records (e.g. Logon, View, blocked); at most 63 characters |  |
| `EventDetail/Authenticate/Data[@Name='d0']/@Value` | cs3 | deviceCustomString3 | Custom string, named by its label; labelled 'd0' (cs3Label = deviceCustomString3Label); at most 4000 characters |  |
| `EventDetail/Authenticate/Data[@Name='d1']/@Value` | cs4 | deviceCustomString4 | Custom string, named by its label; labelled 'd1' (cs4Label = deviceCustomString4Label); at most 4000 characters |  |
| `EventDetail/Authenticate/Data[@Name='d10']/@Value` | cs5 | deviceCustomString5 | Custom string, named by its label; labelled 'd10' (cs5Label = deviceCustomString5Label); at most 4000 characters |  |
| `EventDetail/Authenticate/Data[@Name='d11']/@Value` | cs6 | deviceCustomString6 | Custom string, named by its label; labelled 'd11' (cs6Label = deviceCustomString6Label); at most 4000 characters |  |
| `EventDetail/Authenticate/Data[@Name='d2']/@Value` | flexString1 | flexString1 | Custom string, named by its label; labelled 'd2' (flexString1Label = flexString1Label); at most 1023 characters |  |
| `EventDetail/Authenticate/Data[@Name='d3']/@Value` | flexString2 | flexString2 | Custom string, named by its label; labelled 'd3' (flexString2Label = flexString2Label); at most 1023 characters |  |
| `EventDetail/Authenticate/Data[@Name='d4']/@Value` | d4 | d4 | key outside the CEF dictionary |  |
| `EventDetail/Authenticate/Data[@Name='d5']/@Value` | d5 | d5 | key outside the CEF dictionary |  |
| `EventDetail/Authenticate/Data[@Name='d6']/@Value` | d6 | d6 | key outside the CEF dictionary |  |
| `EventDetail/Authenticate/Data[@Name='d7']/@Value` | d7 | d7 | key outside the CEF dictionary |  |
| `EventDetail/Authenticate/Data[@Name='d8']/@Value` | d8 | d8 | key outside the CEF dictionary |  |
| `EventDetail/Authenticate/Data[@Name='d9']/@Value` | d9 | d9 | key outside the CEF dictionary |  |
| `EventDetail/Authenticate/Data[@Name='note']/@Value` | note | note | key outside the CEF dictionary |  |
| `EventDetail/Authenticate/Data[@Name='session_id']/@Value` | cn1 | deviceCustomNumber1 | Custom number, named by its label; labelled 'session_id' (cn1Label = deviceCustomNumber1Label) |  |
| `EventDetail/Authenticate/Outcome/Success` | outcome | eventOutcome | Outcome: success or failure; from Outcome/Success (no Outcome: success); at most 63 characters |  |
| `EventDetail/Authenticate/User/Id` | duser | destinationUserName | Destination (target) user: the account acted on; at most 1023 characters |  |

### Network events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| the name of the event's action element (EventDetail/Network/*[1]) | act | deviceAction | Action the event records (e.g. Logon, View, blocked); at most 63 characters |  |
| `EventDetail/Network/*/Destination/Device/IPAddress` | dst | destinationAddress | Destination IPv4 address |  |
| `EventDetail/Network/*/Destination/Port` | dpt | destinationPort | Destination port |  |
| `EventDetail/Network/*/Source/Device/IPAddress` | src | sourceAddress | Source IPv4 address |  |
| `EventDetail/Network/*/Source/Port` | spt | sourcePort | Source port |  |
| `EventDetail/Network/*/TransportProtocol` | proto | transportProtocol | Transport protocol, e.g. TCP, UDP; at most 31 characters |  |

### View events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| the event's kind (View) | act | deviceAction | Action the event records (e.g. Logon, View, blocked); at most 63 characters |  |
| `EventDetail/View/Resource/Id` | cn1 | deviceCustomNumber1 | Custom number, named by its label; labelled 'View Resource Id' (cn1Label = deviceCustomNumber1Label) |  |
| `EventDetail/View/Resource/Name` | cs3 | deviceCustomString3 | Custom string, named by its label; labelled 'View Resource Name' (cs3Label = deviceCustomString3Label); at most 4000 characters |  |
| `EventDetail/View/Resource/Type` | cs4 | deviceCustomString4 | Custom string, named by its label; labelled 'View Resource Type' (cs4Label = deviceCustomString4Label); at most 4000 characters |  |
