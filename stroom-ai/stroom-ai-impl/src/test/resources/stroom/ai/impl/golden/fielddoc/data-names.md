### Common fields

Every event type writes these, from the same source.

| XPath | Description | From |
| --- | --- | --- |

### Event types

#### login, checkout

When `a` = login (login). When `a` = checkout (checkout).

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/(Authenticate\|Process)/Action` | The authentication action that was performed, e.g. Logon, Logoff. | "Logon" (login)<br>"Access" (checkout) |
| `EventDetail/(Authenticate\|Process)/User/Id` | An identifier for the user. | `user` |
| `EventDetail/(Authenticate\|Process)/Data[@Name='Folder']/@Value` | The `Folder` value recorded in a Data element of Authenticate. | `folder` |
| `EventDetail/(Authenticate\|Process)/Data[@Name='Result']/@Value` | The `Result` value recorded in a Data element of Authenticate. | `result` |

#### view

Any other record.

| XPath | Description | From |
| --- | --- | --- |
| `EventDetail/View/Object/Id` | An identifier for the object, e.g a document ID in a document management system. | `id` |
| `EventDetail/View/Data[@Name='Folder']/@Value` | The `Folder` value recorded in a Data element of View. | `folder` |
| `EventDetail/View/Data[@Name='Result']/@Value` | The `Result` value recorded in a Data element of View. | `outcome` |

