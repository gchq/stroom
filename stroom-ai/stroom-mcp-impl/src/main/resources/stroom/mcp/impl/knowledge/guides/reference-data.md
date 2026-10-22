# Reference data and dictionaries

Translations often need values the record does not carry: a user's department, a host's site, a vendor code's
meaning. Stroom has two mechanisms, and the mapping (`build_translation_xslt`) writes the XSLT for both.

| Need | Mechanism | In the mapping |
| --- | --- | --- |
| A table that changes, is large, or comes as a feed (user list, asset register, GeoIP) | Reference data: a reference feed, a reference-data pipeline, `stroom:lookup()` | `lookup: {map, field}` |
| A small static table or list kept with the content | A Dictionary doc, read with `stroom:dictionary()` | `dictionary: 'name'` (key=value lines), `in_dictionary` (a list) |
| A value computed from the record | `transform` (lower, upper, trim, strip_domain, domain, digits), `any_of`, `extract`, `xpath` | see the XSLT guide |

## Reference data

How it fits together:

1. **A reference feed** holds the table as raw data (stream type `Raw Reference`): `create_feed` with
   `stream_type='Raw Reference'`, `upload_sample` with `stream_type='Raw Reference'`.
   Reference data is versioned by time: a lookup uses the version in effect at the event stream's time, so a
   table in effect only from its upload is invisible to sample streams uploaded before it. A reference sample
   applies from `2000-01-01T00:00:00.000Z` unless `effective_time` says otherwise; real feeds carry it in their
   receipt headers.
2. **A reference-data pipeline**, a child of the `Reference Data` template (`find_pipeline_templates
   stage=reference`), parses it like any feed (a Data Splitter on `combinedParser.textConverter` for text) and its
   XSLT writes `reference-data:2`: one `<reference>` per record and map, with the map name, the key and the
   value. `build_reference_xslt` writes that XSLT from a mapping:

   ```json
   {"input": "data_splitter",
    "maps": [{"name": "USER_TO_DEPARTMENT", "key": "user",
              "values": [{"element": "department", "field": "dept"}, {"element": "site", "field": "site"}]},
             {"name": "USER_TO_NAME", "key_xpath": "lower-case(data[@name='user']/@value)", "values": [{"field": "name"}]}]}
   ```

   Records the table should not contribute (disabled accounts, say) are left out with `drop_when`, conditions with
   a reason, as in a translation mapping. Process the reference stream (`create_processor_filter`, then
   `wait_for_processing`, which takes the output type, `Reference`, from the pipeline): the pipeline writes
   `Reference` streams. A whole-feed filter takes the feed's own stream type, `Raw Reference`. Do this before the
   events pipeline is first stepped: a lookup made before the feed has a `Reference` stream is remembered by Stroom
   for 10 minutes (its effective stream cache), and every step in that time finds no reference data.
3. **The events pipeline names the feed** as a pipeline reference on its translation step:
   `create_pipeline references=[{"feed": "ACME-USERS"}]`, or `update_pipeline (references=...)` on a pipeline that
   exists. The loader is found, not assumed: the one other pipelines already load that feed with, else the
   environment's only loader (a pipeline with a `ReferenceDataFilter`; `find_reference_data` lists them, and with
   several, give `loader_pipeline`). Without the reference the lookups find nothing and stepping shows a lookup
   warning on every record.
4. **The translation looks keys up**: `{"path": "EventSource/User/UserDetails/Organisation", "lookup": {"map":
   "USER_TO_DEPARTMENT", "field": "user", "path": "department"}}`. `path` names an element inside the value
   (omit it for a text value). A key the map lacks gives no value, so the element is left out, or `default` is
   written. Keys are compared as written: normalise them the same way on both sides (`transform: lower` or
   `strip_domain` on the events side, `key_xpath` with `lower-case()` on the reference side).
   A person's details from a directory go in `EventSource/User`: `Name`, and `UserDetails` for the rest
   (`Organisation`, `Unit` for a department, `Group` for a business group, `Title`, `StaffNumber`). `User/Groups`
   is for the security groups an account belongs to, not the person's department.

`find_reference_data` lists what the environment already loads: each map, its key and value shape, the feeds
and pipeline that load it, the loader to name, and which pipelines use it. Reuse those before creating new
reference data; `describe_template` shows how sibling pipelines attach them.

## Dictionaries

A Dictionary doc is plain text, one entry per line. `save_dictionary` makes one in the build (and
`save_dictionary (uuid=...)` changes it); the generated XSLT reads it at run time, so later edits apply without
regenerating:

- `key=value` lines and `{"path": "...", "field": "code", "dictionary": "Vendor codes"}` give the value for a
  record's key (`default` when the key is missing).
- Plain lines and a rule condition `{"field": "user", "in_dictionary": "VIP users"}` test membership.

Keys and values are trimmed; comparison is exact and case-sensitive, so `transform: lower` the key when the
dictionary is lower case.
