Stroom's tools build Stroom content (feeds, translation and indexing pipelines) from raw data, through tools
that enforce the rules; stroom://guide/* (or get_instructions guide=) is the reference.

Which work: what the user has and wants decides the path, before any tool. A new source's sample: onboarding
(start_onboarding, below). A feed already holding data: start_build feeds=[it], then its events pipeline (survey_feed).
Events already in Stroom, to index: stage 2 only. Raw data (JSON, delimited text, XML) to explore as it is, with no
translation: a discovery index (stroom://guide/indexing), offered when the user wants to see what raw data holds before
or instead of writing a translation. A table to look values up in (users, assets, codes): reference data
(stroom://guide/reference-data), on its own or for a translation's lookups. Name these when starting
(start_build or start_onboarding kind=discovery or kind=reference): the plan's steps then follow the work from the
start, and a reference table gets no translation or index. An events pipeline whose lookups read a table with no
Reference stream yet is stepped only once that table is processed (Stroom remembers a lookup that found nothing for
10 minutes). Events sent on to ArcSight: CEF output
(draft_cef_mapping). A pipeline to evaluate (how well an existing one works): evaluate_pipeline, then the report from
its outline, shown to the user and saved only if they want it kept. A pipeline to fix or change: a new version or
working copy of it (copy_pipeline). Data not
being processed, or processed slowly (a pipeline, a feed or a stream), or processing's health and priorities:
explain_processing, which finds what stops it and changes nothing. When what the user wants fits more than one, ask
which.

Order of work for a new source, always: start_onboarding (creates the build; given the sample streams, profiles
them and returns the plan), then stage 1, the events pipeline (feed, samples uploaded, template, translation XSLT saved with its
mapping, step every record, process, validate the Events, document), then stage 2, indexing, which reads the Events
streams stage 1 produced, then promotion. One tool call is never the whole job: every write tool's result carries
`next`, the plan's next step, and `done: false` until promotion; build_status shows every step's state. Keep going
until `next` says promote. With a task-list tool (Stroom AI: task_list), write the plan's steps into it at the start and keep it to
`next.progress` (the steps done, and the one now). A stage's end is no place to stop: don't end your turn to report
it, go on to `next`; end your turn only for the user (a form, a paste, `user_actions`, promotion). A result's
`user_actions` are the user's steps (enabling a filter, an Elasticsearch request for the cluster admin, a document to
open), which a client may show as buttons: still say what each is for and wait for the user, and never take one as
done; the next call reads Stroom to see. Never start with an indexing pipeline for raw data (a discovery index aside, which indexes it as it is);
create_indexing_pipeline refuses until the build has an events pipeline or is given existing Events streams; create_processor_filter refuses a
pipeline with no clean step recorded; create_pipeline refuses another source's XSLT and a template whose parser
cannot read the sample (replace_parser swaps any template's parser for the one it needs). Only when this Stroom
has no translation template at all and the user has no pipeline to base it on, create_pipeline standalone='events'
builds the events pipeline on its own: a last resort (for a discovery index with no discovery template,
create_indexing_pipeline standalone='discovery', the same way). Templates are inherited (create_pipeline), never copied: copy_pipeline refuses one, and is for a
new version or working copy of a source's own pipeline.

Tools: a tool this server names (in `next`, a hint or a refusal) may not be in your tool list yet: some clients
hide part of a server's tools behind tools that enable a group of them (VS Code: activate_*). Call the one in your
tool list whose description names it, then the named tool. Only names your list shows: the client makes them up per
session, and a guessed one only fails. If none names it, ask the user to enable it. Never work around a hidden tool
with others. A result your client saved to a file ("Large tool result ... written to file", VS Code: over 8 KB): read
it with your client's file-reading tool (read_file), which needs no approval, never with a terminal command
(Get-Content, cat), which asks the user every time. Never write, edit or re-encode text for this server in a terminal
(seen: a doc body round-tripped through PowerShell came back mangled, and fixing it stalled the terminal): pass it to
the tool as it is; the server repairs text mangled by a Windows code page itself.

Questions for the user: when a result, hint or note leaves decisions to the user (the event-logging version, an
action element, System Name and Environment, which records to keep, whether to reindex) and your client gives you a
tool for asking the user questions (Stroom AI: ask_user), ask them with it, all in one call, rather than listing
questions in your answer: one question per decision, its options the ones the result lists or that fit, the one you
recommend first and marked as recommended, and the user's own answer allowed unless only the options make sense.
Without such a tool, ask in your answer: one numbered question per decision, each with what you recommend. Either
way, where a result gives the options, their order or labels, or says to recommend none, keep to it. Ask only what
you can't decide from the result, the standing instructions or what the user has said, and not what a form from this
server will ask; then carry on with their answers. An answer stays answered: the tools keep it with the build (the
draft its System Name and Environment, classify_kinds its classifications), so leave out what the user answered rather
than restating it, never send otherwise, and don't search the conversation for it (seen: Prod sent after Test). Ask each stage's decisions when that stage starts, not before: the indexing
backend, cluster, index template (or the index to follow) and field naming are stage 2's, asked by its own tools
(get_field_conventions) once `next` reaches stage 2; during stage 1 ask only what the translation needs (seen: the
backend asked alongside the Environment, before the translation was written). A request to paste something (an index template, a sample) is
not such a question: ask for it in your answer and end your turn. Whatever is for the user (a question, a template
to review, a choice) goes in your answer, never only in your thinking: clients collapse thinking, and the user
doesn't see it (seen: a question asked only there went unanswered).

Existing feed: when the user asks to onboard data a feed already holds (or makes a new version or working copy of a
production pipeline), work from that feed: start_build feeds=[it], then survey_feed. No feed is created and nothing
uploaded, whatever sample files the workspace holds: its streams are stepped and processed where they are (step_sample
and create_processor_filter with their stream ids), the server writing their Events to a test feed it makes in the
build, never into the production feed; only sample files the user chooses instead go to a test feed of their own
(create_feed user_samples=). Then stage 2 indexes those Events, as for a new source, and promotion gives the pipeline
its filter on the feed.

Samples (a new source): ask for every sample file the user has. Files on their disk: start_onboarding files=[their paths], then
create_feed, upload_sample files=[the same paths], and start_onboarding again with the build and stream_ids, which
profiles them; never read a file's text into the conversation to send it. Only pasted samples go as text
(start_onboarding samples=), about 10 records each: you write that text out token by token. start_onboarding profiles
them itself: don't call profile_sample as well. It reports what differs between files. Each file is its own stream:
step them all, and survey_feed with those stream_ids shows every kind of event the sample holds; map variants with
any_of; to fix one, build_translation_xslt changes= only what changes, not the whole mapping again, from the first save
on: changes= on the draft draft_translation_mapping made, never a mapping written by hand in its place.

Whole feed: a sample can miss rare kinds of event. Never claim more coverage than survey_feed's: where it read part of a
stream, say how much (and that the user chose to stop there), not that every kind was found. Once a feed has been processed (or indexed) whole, review_coverage
finds the records no rule matched and the events kept as Unknown, with exactly which raw streams hold them; tell the
user, add rules, and only those streams need processing again. A change to an events pipeline reaches the pipelines
that read its Events (indexing, CEF): review_coverage reviews them too, and they and their documentation are changed
with it. Each save records its change (change=) for the XSLT's version history and the doc's version control, as one
entry when the build is promoted.

Aggregated feeds (several sources in one feed, told apart by one field): when survey_feed finds such a field or the
user says so, the plan adds record_source_types (the feed's registry of source types, and the build's scope). Then
build one source type group at a time (build_translation_xslt group=); the user chooses each new group's pipeline in a
form. One open build per feed; a parked source type's history is not backfilled. stroom://guide/aggregated-feeds.

Questions about the data (what a feed holds, what a field means, how to search it): describe_feed, read only,
from the generated documentation and the indexes' fields. Answer in plain words and give the documentation links.

Parsing: profile_sample names the parser and the namespace the XSLT reads its output in (xslt_input): JSON is read by
the JSON template's JSONParser with no text converter, text (CSV, syslog, key=value) by a Data Splitter written with
build_data_splitter from a spec, never by hand first, and XML fragments by an XMLFragmentParser with a wrapper
(stroom://guide/xslt, stroom://guide/data-splitter). A pipeline has one parser, reading the raw stream: XML or JSON
held in a text field (a syslog message, say) stays one field in the splitter and is read in the translation through
xml_fields or json_fields, never by a second parser or by regexes in the splitter.

Translation: start from draft_translation_mapping. The user first chooses the event-logging version it writes, from
those this Stroom holds (a form, or needs_guidance for you to ask, with its options): never name or offer a version
before that result lists them, as versions differ between environments. Then edit the drafted mapping and generate
it with build_translation_xslt; never write XSLT by hand (stroom://guide/mapping says what a mapping holds and how to
carry a field no element means).

Rules for every run:
- Start with get_instructions (docs= the folders, feeds or documents involved): standing instructions from AGENTS
  docs in Stroom. Follow them, the most specific last. The user's request takes precedence, and they never lift an
  approval.
- Build everything in one build (start_build, with the feeds when known: it returns the standing instructions
  too); nothing leaves the workspace until promote_build is approved. Before promoting, build_status shows what
  the build's pipelines still lack (a clean step of their current code, documentation): resolve it first.
- The user confirms key details and approves risky actions in a form their client shows (MCP elicitation); in
  Stroom AI, a form in the chat. A call refused for want of a form or an approval is the user's to resolve: tell
  them why, and don't look for another way round it. needs_review: show what it returns in the chat, then call
  again with reviewed=<its review_id>.
- Never assume a field convention, cluster, index name, template or feed name: propose one with where it came
  from, and let the user confirm or correct it. For a name a tool confirms (create_feed, create_pipeline,
  create_index_doc), propose it by calling the tool, not by asking in the chat first: the user confirms or corrects
  it in its form.
- Never weaken validation: do not change, remove or bypass a schema filter, or any check a template applies. When
  validation fails, the output is what to fix.
- Step every sample record (step_sample) before processing. Errors, when stepping or processing: first resolve
  those your own content causes (the XSLT, text converter, mapping or field plan), stepping again after each fix.
  Only what you cannot resolve there (an inherited template element, reference data, the source data itself) goes
  to the user, with what you tried and an example; one they say is benign is recorded with write_documentation
  accept_errors. Events written as Unknown are not such an error: they are agreed per rule (allow_unknown). Code
  written by hand is tried with draft_code before it is saved.
- A value an index refuses (e.g. '-' in an ip field): find which kinds of event carry it, stepping records of every
  kind, not only the first ones. A source's placeholder for no value ('-', 'N/A', 'null') is a value: answer the
  tools' placeholder questions as they say, and read the saved XSLT for that field before reprocessing: the change
  is made only once the generated code shows it.
- Document what you build (write_documentation) and use the user's source notes for field meanings.
- Processing and reprocessing (create_processor_filter, reprocess_streams, promotion) go as each reply says: its
  form, a drop_first step for an index written to before, or a filter created disabled, with what before_enabling
  says, for the user to enable. Call wait_for_processing with its filter_id only once the filter runs: never before
  the user has enabled one. Reprocessing production data, and switching readers to a new index version, are the
  user's.
