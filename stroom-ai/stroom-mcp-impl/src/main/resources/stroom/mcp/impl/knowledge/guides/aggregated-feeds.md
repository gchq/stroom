# Aggregated feeds: source types and source type groups

Some feeds carry several sources at once: a wrapper of fixed fields (host, time, a source field) around a message
whose format depends on the source, or records whose own field says which source they came from and so which fields
they have. Such a feed is onboarded in parts, each part a build of its own making one source type group: the
sources the user picks for it, any number of them (ExchangeOnline and MicrosoftTeams as one "Messaging" group).

Terms:

* **Source type field**: the one field that tells the sources apart, at any depth (`event.source`). Never a
  combination: a second field that tells kinds apart within a source is a kind field, as usual. Or, only where the
  user names it, a stream meta attribute: `source_type_field=meta:<Attribute>` (a header each part of a stream
  carries, as `stroom:meta()` reads it), for a feed whose source is said by a header rather than a record's field.
  Every record of a part has the part's value, so a group passes over a whole part not of its own at once: the
  earliest cull inside Stroom, as a processor filter can't read a part's headers. The survey never looks for one
  itself. A feed's registry has one field, all meta or all a record's.
* **Source type**: the records one registry row matches: a value of that field exactly (`= Billing`), an anchored
  pattern on it (`~ ^/var/log/mail/`) where the values are many paths or names, or an item of a value that is a list
  (`∈ Mail Server`, in ASCII `in`: `Mail in Mail Server`), split on the separator the values use (`,` `;`
  `|`), each item stripped and compared exactly. Rows are tried in order: of a list holding several rows' items, the
  first row's takes the record, so put the rarer, more telling item first.
* **Registry**: `<feed> source types`, a Documentation doc made in the build and promoted beside the feed. One row a
  source type, tried in order (the first match wins), each with its status: `translated`, `parked` (for a later
  build), `dropped` (never, on the user's word) or `new` (met after the registry was made).
* **Scope**: the source types a build covers, which are its one source type group. The survey, samples,
  `classify_kinds`, the draft and the sample checks read only the scope's records; the rest are counted by source type
  and nothing more.
* **Source type group**: the source types one translation handles: one mapping, one XSLT, one pipeline. A build makes
  at most one.

## How it starts

* **On evidence**: when `survey_feed` finds a candidate source type field (several values whose records differ in
  their fields or message formats), it asks the user to confirm it, as it confirms a kind field. The plan then adds
  the `source_types` step. A feed of one source never sees it. If the user says the feed aggregates sources, say so
  when the survey asks.
* **As an assessment**: `start_onboarding kind=assessment feed=<feed>` plans only the survey, the registry and
  promotion: for a user who wants to know what a feed holds before onboarding any of it. A user asking only to
  survey a feed is offered this, since a survey outside a build keeps nothing. What lasts is the registry, beside
  the feed; the survey doc stays in the build.
* **From the registry**: a later build on a feed that has one (`start_build feeds=[feed]`, then `survey_feed`)
  surveys the feed afresh, carries on from the registry beside the feed, and offers the parked and new source types.

At promotion the survey doc always stays in the build (nobody is asked); the registry always goes beside the feed.

## The source types step

`record_source_types build= feed=` in two rounds, as `promote_build` is:

1. The first call profiles each source type from the survey (size, message formats, fields, kinds, levels, signals
   of an audit trail or of operational traffic, and a suggested category) and returns a table (`needs_review`). Show
   the table in the chat; the agent suggests, the user chooses.
2. Called again with `reviewed=<review_id>`: where the values are many paths or names, the proposed patterns are put
   to the user in a form (narrower before wider: `^/var/log/mail/audit/` before `^/var/log/mail/`); where they are
   lists, a row an item, rarest first; then the group
   form: which source types go in this build's group (a yes each) and the group's name, which you suggest with
   `group=<name>` and the user keeps or changes. Rows the user confirmed are given back as `rows=` and not asked again.
   It writes the registry, the scope naming the group; the source types not chosen are `parked` for a later build.
   Naming an existing group adds the chosen source types to it: they join its pipeline and output feed when it is
   built (through a working copy of its XSLT where it is promoted). Named with none chosen, the build changes that
   group alone.

Pass the feed's `system_name` and `environment` (and `system_names` for a source type whose system differs) as the
user gave them. `dropped=` only on the user's word. A later build's `rows=` keeps the registry's translated and placed
rows it leaves out; `remove=` drops a row, on the user's word. Removing a translated or placed row, or changing a
translated row's value or pattern, is confirmed by the user in a form (refused for a translated row without forms). A
translated row can't be renamed: add the new row and remove the old one. A row no record could reach (an earlier row always matches first)
is refused. Without forms, `patterns=`, `scope=`, `group=` (required with `scope=`) and `reason=` carry the user's
answers; `scope=[]` with an existing group's name changes that group alone (refused for a new group).

A row the user names that the survey hasn't met (`unseen_source_types`) can be found two ways, as the user prefers:
a stream they know holds it (`survey_feed build= feed= stream_ids=[id] find=<row>`), or a search of the feed's
streams not surveyed yet, newest first (`survey_feed build= feed= find=<row>`; call again to carry on). Its records
then count in the build's survey like any other source type's.

## Building a group

One build makes one group, its scope's: `build_translation_xslt` builds it with its own mapping, drafted from the
scope's records (the draft and its checks read only those). `group` and `source_types` default to the scope's (with
the source types an existing group translates already); another group name is refused in the build: the next group
is a later build's, after this one is promoted.

* Its messages are its own format, so the draft reads a held format (`json_fields`, `xml_fields`, `extract`) with
  no threshold, namespaces and all (stroom://guide/json-input).
* A group's XSLT is the XSLT of a pipeline of its own: it writes the Events, translates its source types' records
  (the registry's rows tried in order) and passes over every other record, counted.
* A group's XSLT translates each of its source types; its rules tell them apart, as kinds.
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
When a build saves a new group, a form asks **where its Events go**: into the same feed (the default) or an output
feed of its own. Without forms, the call is refused until `own_output_feed=true|false` carries the user's answer.
Either way the group's index is its own (below). For a group whose pipeline is promoted, the first save in a
build asks whether the change goes to **that pipeline** (through working copies) or to **a new version** of it
(`copy_pipeline`, kept for history); without forms, `pipeline=change|new_version`. `group.group_next` says what
follows. Source types added to a group in a later build join its pipeline and output feed (said in `group.notes`). A
source type belongs to one group: one another group translates is refused.

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
  not faults. Stepping counts them apart (`records_passed_over`, by source type), never in `records_with_errors`.
  Check a group's translation with values of its own source types' records: the survey's shapes and locations say
  their `source_types`. An unsaved group's code is stepped as `draft_code` for its pipeline's XSLT element, as any
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

**Each source type group has an index of its own**, even where its Events go into the same feed as other groups':
its indexing pipeline's processor filter selects only the Events its group's pipeline wrote
(`create_processor_filter source_pipeline_uuid=<the group's pipeline>`, a `Pipeline` condition beside the feed's;
promotion keeps it, naming the original where the build processed through a working copy). Don't ask the user
whether groups share an index. Name a group's index like the feed's others, with the group in it
(`get_field_conventions` suggests one, `index_name`); a name another group's index has is refused. The first build's
backend and field naming are the feed's shared decisions, kept in the registry: later builds inherit them
(`get_field_conventions` asks nothing), and another backend, or another name for a group's recorded index, is refused
unless the user asks (`change_shared=true`, confirmed in a form). `feed_index` in `draft_index_mapping`'s reply says
the group, its output feed and its index.

Only when the user asks for one index with another group's Events ("in the same index as group X"), draft with
`share_index_with=<that group, or its index>` and that index's name: the plan is then the union of the groups'
fields, and a field already indexed with another type, or another meaning, is put to the user in one form (a field of
another name, or a new index version). A later build of a group checks its own index's fields the same way. Promotion
is held while a clash remains, and refuses a plan naming another group's index not shared at the user's word.

## Promotion and later builds

* Promotion moves the modules, the registry (always; the survey doc stays in the build) and any group output
  feed beside the feed (in a folder of the source's own where the feed's folder holds other sources' folders; the
  build's test feed is never promoted: don't offer it), the group pipelines where its destinations say; its
  review shows the pipeline changes
  (reference data each group pipeline lacks, only ever added) and the index changes (fields added, forward only).
* One open build per aggregated feed: another is refused, naming the open one. Finish (promote) or abandon it first.
* A later build changes promoted modules through working copies (imported by the build's own modules, so the chain
  runs the copies). Promotion writes them back, the chain pointed at the originals again.
* **Forward only**, per group: a source type moved out of parked is translated from the next processing on. Tell the
  user its history isn't: backfilling means reprocessing the period's raw streams through the group's pipeline,
  which reruns only that group (Stroom supersedes that pipeline's Events streams; their documents stay in the index).
  A new version of the group's pipeline (`copy_pipeline`) can take the feed's earlier streams without touching the
  old version's Events (Stroom supersedes output per pipeline); where both write to one index, it holds both
  versions' documents for the period. A group's indexing filter names the pipeline whose Events it reads: the new
  version's Events are indexed only by a filter naming the new version (`source_pipeline_uuid=`). Say so. Reprocessing is the user's.
