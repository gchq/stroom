### Common fields

Every event type writes these, from the same source.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/*/Data[@Name='Folder']/@Value` | The `Folder` value recorded in a Data element of the event's action element. | `folder` |

### Event types

#### login, checkout

When `a` = login (login). When `a` = checkout (checkout). Below the action element: login 2 typed, 1 kept as Data; checkout 2 typed, 1 kept as Data.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/(Authenticate\|Process)/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" (login)<br>"Access" (checkout) |
| `EventDetail/(Authenticate\|Process)/User/Id` | An identifier for the user. | `user` |
| `EventDetail/(Authenticate\|Process)/Data[@Name='Result']/@Value` | The `Result` value recorded in a Data element of Authenticate. | `result` |

#### view

Any other record. Below the action element: 1 typed, 1 kept as Data.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/View/Object/Id` | An identifier for the object, e.g a document ID in a document management system. | `id` |
| `EventDetail/View/Data[@Name='Result']/@Value` | The `Result` value recorded in a Data element of View. | `outcome` |

