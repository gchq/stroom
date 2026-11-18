The fields in the 3 documents the pipeline wrote from the sample. Its XSLT keeps no index plan, so where each comes from is not recorded here (save the XSLT with its plan to add it).

| Index field | Description | In sample | Sample values |
| --- | --- | --- | --- |
| `EventId` | The event's number in its stream; with StreamId, it identifies the event. | 33% of documents | `3` |
| `Host` | Not described: no plan or schema covers it. | 33% of documents | `ws01` |
| `UserId` | Not described: no plan or schema covers it. | 67% of documents | `alice`, `bob` |
| `n` | Numbers. Several values per document. | 67% of documents | `1`, `2`, `3` |
