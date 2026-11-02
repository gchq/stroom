`legacy-web` is Elasticsearch index `legacy-web`; the time field is `@timestamp`.

The survey read 7 of the 8 fields; the other 1 are listed as not surveyed.

| Index field | Description | Type | From (event-logging path) | In sample | Sample values |
| --- | --- | --- | --- | --- | --- |
| `StreamId` | The id of the stream the document came from. | long | (not recorded) | 67% of documents | `7` |
| `@timestamp` | The index's time field: searches and dashboards filter on it. | date | (not recorded) | 100% of documents | `2026-09-23T11:45:00.000Z`, `2026-09-20T08:00:00.000Z`, `2026-09-21T08:00:00.000Z` |
| `message` | Log message optimized for viewing in a log viewer. | text | (not recorded) | 67% of documents | `health check`, `GET /` |
| `user.name` | An identifier for the user. Several values per document. | keyword | `EventSource/User/Id` | 100% of documents | `alice`, `bob`, `eve` |
| `user.role` | The role the user acted in. | keyword | `EventSource/User/Role` | not surveyed | - |
| `http.status` | Numbers. | long | (not recorded) | 67% of documents | `200`, `404` |
| `source.ip` | IP address of the source. | ip | (not recorded) | 67% of documents | `10.3.0.1`, `10.3.0.2` |
| `secret` | Indexed but not stored: searchable, its values cannot be shown. | keyword | (not recorded) | not stored | - |
| `gone` | An identifier for the user. | keyword | `EventDetail/Authenticate/User/Id` | not in the index | - |
