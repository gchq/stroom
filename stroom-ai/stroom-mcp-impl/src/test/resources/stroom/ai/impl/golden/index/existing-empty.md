`legacy-web` is Elasticsearch index `legacy-web`; the time field is `@timestamp`. Where each field comes from is not recorded: no pipeline that writes to it keeps an index plan. No documents came back through Stroom, so no field below was read (see Data surveyed).

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `StreamId` | Not read: no documents came back through Stroom. | long | (not recorded) | not read | - |
| `@timestamp` | Not read: no documents came back through Stroom. | date | (not recorded) | not read | - |
| `message` | Not read: no documents came back through Stroom. | text | (not recorded) | not read | - |
| `user.name` | Not read: no documents came back through Stroom. | keyword | (not recorded) | not read | - |
| `user.role` | Not read: no documents came back through Stroom. | keyword | (not recorded) | not read | - |
| `http.status` | Not read: no documents came back through Stroom. | long | (not recorded) | not read | - |
| `source.ip` | Not read: no documents came back through Stroom. | ip | (not recorded) | not read | - |
| `secret` | Indexed but not stored: searchable, its values cannot be shown. | keyword | (not recorded) | not stored | - |
