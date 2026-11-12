# Aggregated feeds: source types and source type groups

Some feeds carry several sources at once: a wrapper of fixed fields (host, time, a source field) around a message
whose format depends on the source, or records whose own field says which source they came from and so which fields
they have. Such a feed is onboarded in parts, a few sources at a time, each part a build of its own.

Terms:

* **Source type field**: the one field that tells the sources apart, at any depth (`event.source`). Never a
  combination: a second field that tells kinds apart within a source is a kind field, as usual.
* **Source type**: the records one registry row matches: a value of that field exactly (`= Billing`), or an
  anchored pattern on it (`~ ^/var/log/mail/`) where the values are many paths or names.
* **Registry**: `<feed> source types`, a Documentation doc made in the build and promoted beside the feed. One row a
  source type, tried in order (the first match wins), each with its status: `translated`, `parked` (for a later
  build), `dropped` (never, on the user's word) or `new` (met after the registry was made).
* **Scope**: the source types a build covers. The survey, samples, `classify_kinds`, the draft and the sample checks
  read only the scope's records; the rest are counted by source type and nothing more.
* **Source type group**: the source types one translation handles: one mapping, one XSLT module.

## How it starts

* **On evidence**: when `survey_feed` finds a candidate source type field (several values whose records differ in
  their fields or message formats), it asks the user to confirm it, as it confirms a kind field. The plan then adds
  the `source_types` step. A feed of one source never sees it. If the user says the feed aggregates sources, say so
  when the survey asks.
* **As an assessment**: `start_onboarding kind=assessment feed=<feed>` plans only the survey, the registry and
  promotion: for a user who wants to know what a feed holds before onboarding any of it. A user asking only to
  survey a feed is offered this, since a survey outside a build keeps nothing.
* **From the registry**: a later build on a feed that has one (`start_build feeds=[feed]`, then `survey_feed`)
  carries on from the promoted survey and registry, and offers the parked and new source types.

Once the survey is covered (or the user stops it), the user is asked whether the survey doc goes beside the feed at
promotion; its example records are then as readable as the feed's folder.

## The source types step

`record_source_types build= feed=` in two rounds, as `promote_build` is:

1. The first call profiles each source type from the survey (size, message formats, fields, kinds, levels, signals
   of an audit trail or of operational traffic, and a suggested category) and returns a table (`needs_review`). Show
   the table in the chat; the agent suggests, the user chooses.
2. Called again with `reviewed=<review_id>`: where the values are many paths or names, the proposed patterns are put
   to the user in a form (narrower before wider: `^/var/log/mail/audit/` before `^/var/log/mail/`); then the scope
   form, a yes per source type and a reason for the rest. Rows the user confirmed are given back as `rows=` and not
   asked again. It writes the registry; the source types not chosen are `parked` with the user's reason.

Pass the feed's `system_name` and `environment` (and `system_names` for a source type whose system differs) as the
user gave them. `dropped=` only on the user's word. A later build's `rows=` keeps the registry's translated and placed
rows it leaves out; `remove=` drops a row, on the user's word. Removing a translated or placed row, or changing a
translated row's value or pattern, is confirmed by the user in a form (refused for a translated row without forms). A
translated row can't be renamed: add the new row and remove the old one. A row no record could reach (an earlier row always matches first)
is refused. Without forms, `patterns=`, `scope=` and `reason=` carry the user's answers.

## Building a group

One build covers its scope; each group in it is built with `build_translation_xslt group=<name>
source_types=[...]` and its own mapping, drafted from the scope's records (the draft and its checks read only those):

* Its messages are its own format, so the draft reads a held format (`json_fields`, `xml_fields`, `extract`) with
  no threshold, namespaces and all (stroom://guide/json-input).
* The first group of a feed makes `<feed> common` (the wrapper's or record's shared Event parts: time, System,
  Generator, Device) and `<feed> helpers`; later groups reuse them and keep only what differs, said in
  `group.notes`. A group writes its whole Event.
* Each save regenerates `<feed> dispatcher` from the registry and sets the group's rows `translated`. Never edit a
  dispatcher, common or helpers module by hand: they are regenerated.
* A group that looks values up gives `references=`; the dispatcher's pipeline loads every group's reference data.

**Which pipeline** handles a new group is always the user's choice, in a form when it is first saved: the feed's
pipeline (the default; one pass over every record), a pipeline of its own (it reads every stream of the feed and
passes over the other groups' records; optionally to an output feed of its own), or a new version of the feed's
pipeline (`copy_pipeline`). A feed with no pipeline yet is offered only the first two. Without forms, the call is
refused until `pipeline=shared|own|new_version` (and `own_output_feed`, for a pipeline of its own only) carries the
user's answer. Source types added to a group later join its pipeline and output feed (said in `group.notes`). A
source type belongs to one group: one another group translates is refused, as is a group name whose mode meets
another group's (`PEGA` and `PEGA mail`). A group's own
output feed is made with `create_feed group=<name>` before its pipeline.

`create_pipeline` puts the build's dispatcher in the pipeline's XSLT slot (naming both when the build holds two:
then give the one with `set_properties`), sets a group's output feed on the stream appender, and refuses a group,
common or helpers module as the pipeline's XSLT.

## Stepping, processing and coverage

* Step and process the dispatcher's pipeline as any events pipeline: records outside its groups give no Event and
  are not faults. An unsaved group's code given as `draft_code` is stepped through a temporary dispatcher.
* Processing logs, per document, `Passed over N record(s) of source type X` (INFO; another pipeline's source types
  `handled by pipeline '...'`), and a value no registry row matches once a stream (WARN, `New source type: ...`).
  These are counts, not errors: don't fix them.
* `review_coverage` judges the kinds missed against the build's scope (other source types `passed_over`, not
  missed; another pipeline's `handled_elsewhere`), reports the dispatcher's counts by source type and status
  (`source_types.processing`), and adds every new value to the build's registry as `new`. Tell the user: a later
  build on the feed offers the new source types with the parked ones.

## Indexes

Groups writing to the same Events feed share its index; a group with an output feed of its own has its own. The
first build's backend and field naming are the feed's shared decisions, kept in the registry: later builds inherit
them (`get_field_conventions` asks nothing), and another backend or index name is refused unless the user asks
(`change_shared=true`, confirmed in a form). `draft_index_mapping` plans the union of every group routed to that
output feed (`feed_index` says which). A field already indexed with another type, or another meaning, is put to the
user in one form: a field of another name, or a new index version; promotion is held while a clash remains.

## Promotion and later builds

* Promotion moves the modules, the registry (always, whatever was chosen for the survey doc) and any group output
  feed beside the feed; its review shows the
  pipeline changes (reference data, only ever added) and the index changes (fields added, forward only).
* One open build per aggregated feed: another is refused, naming the open one. Finish (promote) or abandon it first.
* A later build changes promoted modules through working copies; `copy_pipeline working_copy=true` steps the feed's
  pipeline with them. Promotion writes them back and regenerates the dispatcher against the originals.
* **Forward only**: a source type moved out of parked is translated from the next processing on. Tell the user its
  history isn't: backfilling means reprocessing the period's raw streams, which translates and indexes every
  group's records again (the superseded Events streams' documents stay in the index). For history without that, the
  placement form offers a pipeline of its own (its filter can take earlier streams) or a new version of the feed's
  pipeline with its own index. Reprocessing is the user's.
