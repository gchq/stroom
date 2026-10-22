Each Event becomes one CEF (ArcSight Common Event Format) line: `CEF:0|Device Vendor|Device Product|Device Version|Device Event Class ID|Name|Severity|` then key=value pairs. Each line is the value of one Kafka record on topic `arcsight-cef`, one Event a record.
Keys outside ArcSight's CEF dictionary are not used: ArcSight indexes only its dictionary's keys. Its custom slots (cs1-cs6 and the rest) carry the values no standard key fits, each named by its Label key.

### Header

| Position | CEF field | From | Example |
| --- | --- | --- | --- |
| 1 | Version | the constant 'CEF:0' | CEF:0 |
| 2 | Device Vendor | `EventSource/System/Organisation` | Delinea |
| 3 | Device Product | `EventSource/System/Name` | SecretServer |
| 4 | Device Version | `EventSource/System/Version` | 11.4 |
| 5 | Device Event Class ID | `EventDetail/TypeId`, else the event's action element and its Action | User-Login |
| 6 | Name | `EventDetail/Description`, else the event's action element and its Action | User logged in |
| 7 | Severity | the constant '3' | 3 |

### Every event

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| `EventSource/Device/HostName` | dvchost | deviceHostName | Host name of the device that generated the event; at most 100 characters | ss-web-02 |
| `EventTime/TimeCreated` | rt | deviceReceiptTime | When the event happened; milliseconds since 1970 | 1790552890000 |
| `EventSource/Client/IPAddress` | cs1 | deviceCustomString1 | Custom string, named by its label; labelled 'Client IPAddress' (cs1Label = deviceCustomString1Label); at most 4000 characters | 192.0.2.45 |
| `EventSource/User/Id` | suser | sourceUserName | Source user: the account that acted; at most 1023 characters | priya.patel |
| `EventSource/Device/IPAddress` | dvc | deviceAddress | IPv4 address of the device that generated the event | 10.1.2.3 |
| `EventSource/System/Environment` | cs2 | deviceCustomString2 | Custom string, named by its label; labelled 'System Environment' (cs2Label = deviceCustomString2Label); at most 4000 characters | Prod |

### Authenticate events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| `EventDetail/Authenticate/Action` | act | deviceAction | Action the event records (e.g. Logon, View, blocked); at most 63 characters | Logon |
| `EventDetail/Authenticate/Data[@Name='note']/@Value` | cs3 | deviceCustomString3 | Custom string, named by its label; labelled 'note' (cs3Label = deviceCustomString3Label); at most 4000 characters | a=b\|c\d second line |
| `EventDetail/Authenticate/Data[@Name='session_id']/@Value` | cn1 | deviceCustomNumber1 | Custom number, named by its label; labelled 'session_id' (cn1Label = deviceCustomNumber1Label) | 6857 |
| `EventDetail/Authenticate/Outcome/Success` | outcome | eventOutcome | Outcome: success or failure; from Outcome/Success (no Outcome: success); at most 63 characters | failure |
| `EventDetail/Authenticate/User/Id` | duser | destinationUserName | Destination (target) user: the account acted on; at most 1023 characters | priya.patel |

### Network events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| the name of the event's action element (EventDetail/Network/*[1]) | act | deviceAction | Action the event records (e.g. Logon, View, blocked); at most 63 characters | Deny |
| `EventDetail/Network/*/Destination/Device/IPAddress` | dst | destinationAddress | Destination IPv4 address | 10.0.0.2 |
| `EventDetail/Network/*/Destination/Port` | dpt | destinationPort | Destination port | 443 |
| `EventDetail/Network/*/Source/Device/IPAddress` | src | sourceAddress | Source IPv4 address | 10.0.0.1 |
| `EventDetail/Network/*/Source/Port` | spt | sourcePort | Source port | 5555 |
| `EventDetail/Network/*/TransportProtocol` | proto | transportProtocol | Transport protocol, e.g. TCP, UDP; at most 31 characters | TCP |

### View events

| Event path | CEF key | ArcSight field | What it holds | Example |
| --- | --- | --- | --- | --- |
| the event's kind (View) | act | deviceAction | Action the event records (e.g. Logon, View, blocked); at most 63 characters | View |
| `EventDetail/View/Resource/Id` | cn1 | deviceCustomNumber1 | Custom number, named by its label; labelled 'View Resource Id' (cn1Label = deviceCustomNumber1Label) | 10005 |
| `EventDetail/View/Resource/Name` | cs3 | deviceCustomString3 | Custom string, named by its label; labelled 'View Resource Name' (cs3Label = deviceCustomString3Label); at most 4000 characters | Firewall \| Edge |
| `EventDetail/View/Resource/Type` | cs4 | deviceCustomString4 | Custom string, named by its label; labelled 'View Resource Type' (cs4Label = deviceCustomString4Label); at most 4000 characters | Secret |
