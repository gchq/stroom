Documents for `raw-v1` (Elasticsearch, discovery): each raw record indexed as it is, with the source's field names. Elasticsearch maps fields dynamically as documents arrive: strings as keywords (up to 1024 characters), at most 2000 fields, malformed values ignored.

A string holding a JSON object is also indexed parsed, as `<field>_json`; the string is kept.

Left out: `junk`, `more`.

Fields starting with `_` (Elasticsearch reserves `_id` and others; Stroom drops the rest) or top-level ones named like a field written here are kept as `<field>_original`, without the leading `_` (`_id` is `id_original`); keys Elasticsearch cannot take (empty, or with an empty dotted part) are repaired.

| Index field | Description | Mapping | From |
| --- | --- | --- | --- |
| `StreamId` | The id of the raw stream the record came from. | long | the stream id |
| `EventId` | The record's number in its stream; with StreamId, it identifies the record. | long | the record number in the stream |
| `@timestamp` | The record's time, from its `ts` field. | date | `ts` (format `yyyy`) |
| `feed` | The stream's `Feed` meta attribute. | dynamic | stream meta `Feed` |
