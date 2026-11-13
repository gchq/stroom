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
* A group's XSLT is the XSLT of a pipeline of its own: it writes the Events, translates its source types' records
  (the registry's rows tried in order) and passes over every other record, counted.
* The first group of a feed makes `<feed> common` (the wrapper's or record's shared Event parts: time, System,
  Generator, Device, and the site's shared calls) and `<feed> helpers`; later groups reuse them and keep only what
  differs, said in `group.notes` (a shared call of a later group's is added to the common module). A group writes
  its whole Event. Imports are a chain: the group imports only the common module, which imports the helpers and the
  site's libraries; a module imported twice in one stylesheet is refused.
* Each save sets the group's rows `translated`. Never edit a common or helpers module by hand: they are
  regenerated.
* A group that looks values up gives `references=`; its pipeline loads them.

**Every group has a pipeline of its own**, `<feed> <group>`, its one XSLT the group's. Each pipeline reads every
stream of the feed (processor filters choose streams, not records) and passes over the other groups' records; the user
accepts that cost for groups isolated from each other (their own processor filter, priority, reprocessing and errors).
When a build first saves a new group, a form asks **where its Events go**: the feed's Events (the default, sharing the
feed's index) or an output feed of its own (with an index of its own). Without forms, the call is refused until
`own_output_feed=true|false` carries the user's answer. For a group whose pipeline is promoted, the first save in a
build asks whether the change goes to **that pipeline** (through working copies) or to **a new version** of it
(`copy_pipeline`, kept for history); without forms, `pipeline=change|new_version`. `group.group_next` says what
follows. Source types added to a group later join its pipeline and output feed (said in `group.notes`). A source type
belongs to one group: one another group translates is refused.

* `create_feed group=<name> stream_type=Events` makes a group's own output feed, before its pipeline.
* `create_pipeline build= group=<name> template_uuid=<translation template>` makes the group's pipeline: its XSLT
  the group's, every stream appender's `feed` set to the group's output feed (refused until that feed is made), the
  group's reference data loaded, the pipeline recorded in the registry's rows. One pipeline a group; a common or
  helpers module is refused as a pipeline's XSLT.
* `copy_pipeline source_uuid=<the group's pipeline> working_copy=true` steps and processes a promoted group's
  pipeline in the build with its working copies (the import chain pointed at the build's copies; a build processes
  only its own pipelines, so this is also how a promoted group's pipeline takes the build's later data);
  `working_copy=false` after a `new_version` answer makes the new version, and the group's registry rows move to it.

A user who doesn't want a pipeline per group treats the feed as a normal source: `build_translation_xslt` without
`group=`, one mapping for every source type, surveyed and translated in its entirety (below).

## Stepping, processing and coverage

* Step and process a group's pipeline as any events pipeline: records of other source types give no Event and are
  not faults. An unsaved group's code is stepped as `draft_code` for its pipeline's XSLT element, as any
  translation's.
* Processing logs, once a document, `Passed over N record(s) not of this group's source types` (INFO): the other
  groups', parked, dropped and new source types alike, never warned of. These are counts, not errors: don't fix
  them.
* `review_coverage` on a group's pipeline judges the kinds missed against the group's source types: a kind of
  another group's source type is left to that group's pipeline (`other_groups`), never missed; others outside the
  group are `passed_over`. `source_types.processing.passed_over` sums the group's lines over the documents and
  streams read. A value no row matches, met in a kind missed, goes into the build's registry as `new`; a group's
  pipeline never adds one from processing. Tell the user: a later build on the feed offers the parked and new source
  types.

## A feed translated whole

When the survey confirmed a source type field but the feed is translated as a normal source (no group), the values
the survey saw (or the registry's rows, where record_source_types wrote one) are kept with the mapping
(`seen_source_types`, set by `build_translation_xslt`, never by hand). Its XSLT logs, once a stream for each, a value
outside them: `New source type 'X' in <field>, not seen when this feed was surveyed: survey the feed again to cover
it` (INFO). Its records are translated by the rules as any other, so it is triaged benign; `review_coverage` reports
the values (`source_types.unseen_source_types`). Suggest to the user that they survey the feed again, in a build on
it, so the new source's kinds are classified and translated. Nothing is written to a registry. A feed without a
source type field gets none of this.

## Indexes

Groups writing to the same Events feed share its index; a group with an output feed of its own has its own. The
first build's backend and field naming are the feed's shared decisions, kept in the registry: later builds inherit
them (`get_field_conventions` asks nothing), and another backend or index name is refused unless the user asks
(`change_shared=true`, confirmed in a form). `draft_index_mapping` plans the union of every group whose pipeline
writes to that output feed (`feed_index` says which). A field already indexed with another type, or another meaning,
is put to the user in one form: a field of another name, or a new index version; promotion is held while a clash
remains.

## Promotion and later builds

* Promotion moves the modules, the registry (always, whatever was chosen for the survey doc) and any group output
  feed beside the feed, the group pipelines where its destinations say; its review shows the pipeline changes
  (reference data each group pipeline lacks, only ever added) and the index changes (fields added, forward only).
* One open build per aggregated feed: another is refused, naming the open one. Finish (promote) or abandon it first.
* A later build changes promoted modules through working copies (imported by the build's own modules, so the chain
  runs the copies). Promotion writes them back, the chain pointed at the originals again.
* **Forward only**, per group: a source type moved out of parked is translated from the next processing on. Tell the
  user its history isn't: backfilling means reprocessing the period's raw streams through the group's pipeline,
  which reruns only that group (Stroom supersedes that pipeline's Events streams; their documents stay in the index).
  A new version of the group's pipeline (`copy_pipeline`) can take the feed's earlier streams without touching the
  old version's Events (Stroom supersedes output per pipeline); where both write to one index, it holds both
  versions' documents for the period. Reprocessing is the user's.
