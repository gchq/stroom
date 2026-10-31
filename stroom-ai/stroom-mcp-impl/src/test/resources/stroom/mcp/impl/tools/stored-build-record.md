# Build record: acme-v1

What the MCP server remembers about this build's documents: which are working copies of production documents, and the code (by digest) that stepped clean, passed index verification, or gave Events that passed check_events, which promotion checks for. The server writes it; don't edit it. Don't move or rename it or anything else in this folder either: the server finds a build's documents by the folder they are in. Promotion removes it.

| Document | Type | Working copy of | Stepped clean | Index verified | Events checked | Sample files |
| --- | --- | --- | --- | --- | --- | --- |
| Acme | Pipeline | prod-1 | 01234567, fedcba98 | - | - | - |
| Other | Pipeline | - | - | aaaaaaaa | - | - |
| Acme | XSLT | - | - | - | - | yes |

Works from existing feeds: ACME, ACME-2.

## Record

Read by the server; do not edit.

```json build-record
{
 "docs": {
  "p": {
   "copy_of": "prod-1",
   "name": "Acme",
   "stepped": [
    "0123456789abcdef",
    "fedcba9876543210"
   ],
   "type": "Pipeline"
  },
  "q": {
   "name": "Other",
   "type": "Pipeline",
   "verified": [
    "aaaaaaaabbbbbbbb"
   ]
  },
  "x": {
   "name": "Acme",
   "sample_files": true,
   "type": "XSLT"
  }
 },
 "source_feeds": [
  "ACME",
  "ACME-2"
 ]
}
```
