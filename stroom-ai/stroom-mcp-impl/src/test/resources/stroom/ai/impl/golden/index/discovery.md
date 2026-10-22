Documents for `raw-v1` (Elasticsearch, discovery): each raw record indexed as it is, with the source's field names. Elasticsearch maps fields dynamically as documents arrive: strings as keywords (up to 1024 characters), at most 2000 fields, malformed values ignored.

A string holding a JSON object is also indexed parsed, as `<field>_json`; the string is kept.

Left out: `junk`, `more`.

Fields starting with `_` (Elasticsearch reserves `_id` and others; Stroom drops the rest) or top-level ones named like a field written here are kept as `<field>_original`, without the leading `_` (`_id` is `id_original`); keys Elasticsearch cannot take (empty, or with an empty dotted part) are repaired.

Arrays of objects are indexed flattened: each field inside is searchable, but not which values went together in one object: `items`.

| Index field | Description | Mapping | From | Sample values |
| --- | --- | --- | --- | --- |
| `StreamId` | The id of the raw stream the record came from. | long | the stream id | `7` |
| `EventId` | The record's number in its stream; with StreamId, it identifies the record. | long | the record number in the stream | (none in the sample) |
| `@timestamp` | The record's time, from its `ts` field. | date | `ts` (format `yyyy`) | `2026-10-01T10:00:00.000Z` |
| `feed` | The stream's `Feed` meta attribute. | dynamic | stream meta `Feed` | `F` |

Fields in the 3 documents written from the sample, mapped dynamically:

| Field | Description | In sample | Sample values |
| --- | --- | --- | --- |
| `UserId` | The source's `UserId` field. Several values per document. | 33% of documents | `bob`, `carol` |
| `body_json.k` | Parsed from the JSON held in the source's `body` field. | 33% of documents | `v` |
| `items.qty` | The source's `items.qty` field. | 33% of documents | `2` |
| `items.sku` | The source's `items.sku` field. | 33% of documents | `a` |
| `user.b` | The source's `user.b` field. | 33% of documents | `true` |
| `user.name` | The source's `user.name` field. | 33% of documents | `alice` |
