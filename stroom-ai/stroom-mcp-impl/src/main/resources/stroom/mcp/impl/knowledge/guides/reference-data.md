# Reference data and dictionaries

Translations often need values the record does not carry: a user's department, a host's site, a vendor code's
meaning. Stroom has two mechanisms, and the mapping (`build_translation_xslt`) writes the XSLT for both.

| Need | Mechanism | In the mapping |
| --- | --- | --- |
| A table that changes, is large, or comes as a feed (user list, asset register, GeoIP) | Reference data: a reference feed, a reference-data pipeline, `stroom:lookup()` | `lookup: {map, field}` |
| A small static table or list kept with the content | A Dictionary doc, read with `stroom:dictionary()` | `dictionary: 'name'` (key=value lines), `in_dictionary` (a list) |
| A value computed from the record | `transform` (lower, upper, trim, strip_domain, domain, digits), `any_of`, `extract`, `xpath` | see the XSLT guide |

## Reference data

A table loaded on its own is a build of its own: start it as one (`start_build kind=reference`, or
`start_onboarding kind=reference`), and its plan has the reference steps (feed, sample, converter, the reference-data
pipeline, stepped, processed, documented) with no translation, Events or index. A build holding a `Raw Reference`
sample or a reference-data pipeline is planned this way too. The reference filter is created disabled: the user
enables it on the pipeline's Processors tab, then `wait_for_processing`.

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
   for 10 minutes (its effective stream cache), and every step in that time finds no reference data. The plan holds
   an events pipeline that looks values up in a feed with no `Reference` stream yet before stepping, until it is
   processed.
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

## Context data

Some sources send, with each file of events, context data describing them (a Context child of each stream part:
message templates, flag names, the names of codes). It is read only when the user asks for it; the survey never looks
at it. The user's guidance may be broad ("look up the keywords and opcode from the context, render the description
from its template"): work out the details from the data, then confirm them with the user before saving.

1. **The sample with its context**: `upload_sample sample=... context=...`, or `parts=[{sample, context}, ...]` for
   one stream of several parts, each with its own context (a file on disk: a zip of `NNN.dat` and `NNN.ctx` pairs,
   sent with `files`). `fetch_events streamId=... childType='Context'` reads a part's context.
2. **The proposal**: `propose_context_lookup stream_ids=[...]` reads each part's context entries and records and
   finds the key: the entry fields (up to 3) whose values pick out exactly one entry for most records, with the
   record fields that match them, and its match rate. It lists `entry_fields` and `entry_tables` (lists inside an
   entry, such as a flag table of mask and name). Propose `outputs` for what the user asked, each `{path, from,
   render}`: `text` (the entry's value as it is), `fill_template` (a template whose numbered placeholders the
   record's `values` fill in order; give `pattern`, a regex whose first group is the number, inferred from the
   templates if left out) or `mask_names` (the record's `mask`, a bit mask, as the names of the table's masks it
   holds; `mask_field` and `name_field` are a table item's paths; `repeat` for one element a name). The reply
   previews each for sample records, before and after, as a table: show it, then call again with `confirm=true`;
   the user agrees in a short form.
3. **The context loader**: the confirmed reply's `reference_mapping` goes to `build_reference_xslt` (input `xml`, the
   map's `key_fields`), saved with `save_xslt`, then `create_pipeline` from `find_reference_data`'s
   `context_loader_template` with that XSLT (`translationFilter.xslt`) and `combinedParser.type` `XML` in
   `set_properties` (XML needs no text converter). Nothing is processed: the loader reads each stream's context as the
   stream is processed.
4. **The lookups**: add the reply's `mapping_entries` to the translation's mapping (lookups with `key_fields`, the
   same fields in the same order as the loader's; `fill_template`; `mask_names` with a lookup of the copied table),
   and give the events pipeline `references=[{"stream_type": "Context", "loader_pipeline": "<the loader>"}]` (no
   feed needed). Each part's lookups read that part's own context.
5. **Step**: stepping warns of a record whose key finds no entry (Stroom's lookup warning), a placeholder left
   unfilled, and set bits no name covers.

A key of several fields (`key_fields`) is each field's value, stripped, joined by an unlikely separator (U+241F);
build_reference_xslt and build_translation_xslt make it alike, and build_translation_xslt warns when a lookup's key
has a different number of fields than its map's loader writes. `key_fields` works for any reference map, not only
context data.

## Dictionaries

A Dictionary doc is plain text, one entry per line. `save_dictionary` makes one in the build (and
`save_dictionary (uuid=...)` changes it); the generated XSLT reads it at run time, so later edits apply without
regenerating:

- `key=value` lines and `{"path": "...", "field": "code", "dictionary": "Vendor codes"}` give the value for a
  record's key (`default` when the key is missing).
- Plain lines and a rule condition `{"field": "user", "in_dictionary": "VIP users"}` test membership.

Keys and values are trimmed; comparison is exact and case-sensitive, so `transform: lower` the key when the
dictionary is lower case.
