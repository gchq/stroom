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
(draft_cef_mapping). A pipeline to fix or change: a new version or working copy of it (copy_pipeline). When what the
user wants fits more than one, ask which.

A discovery index: the source is a feed already holding raw data (start_build feeds=[it] kind=discovery) or a sample
(start_build kind=discovery, create_feed, upload_sample); nothing is surveyed, as Elasticsearch maps the fields itself. Ask the user, in one
message: the cluster, the index name (e.g. stroom-discovery-<source>-v1), the discovery template
(find_pipeline_templates stage=discovery), the field holding each record's time (and its format if it is not ISO 8601
or epoch milliseconds), any stream meta to add, any fields to leave out, and an example index template from a sibling
index. Delimited text needs a Data Splitter in the build first (build_data_splitter). Then draft_index_mapping
discovery= with the raw streams, and on as its reply says.

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
server will ask; then carry on with their answers. Ask each stage's decisions when that stage starts, not before: the indexing
backend, cluster, index template (or the index to follow) and field naming are stage 2's, asked by its own tools
(get_field_conventions) once `next` reaches stage 2; during stage 1 ask only what the translation needs (seen: the
backend asked alongside the Environment, before the translation was written). A request to paste something (an index template, a sample) is
not such a question: ask for it in your answer and end your turn. Whatever is for the user (a question, a template
to review, a choice) goes in your answer, never only in your thinking: clients collapse thinking, and the user
doesn't see it (seen: a question asked only there went unanswered).

Existing feed: when the user asks to onboard data a feed already holds, work from that feed:
start_build feeds=[it], then survey_feed. No feed is created and nothing uploaded, whatever sample files the
workspace holds: its streams are stepped and processed where they are (create_processor_filter with their stream
ids), the server writing their Events to a test feed it makes in the build; then stage 2 indexes those Events, as for
a new source, and promotion gives the pipeline its filter on the feed.

Samples (a new source): ask for every sample file the user has. Files on their disk: start_onboarding files=[their paths], then
create_feed, upload_sample files=[the same paths], and start_onboarding again with the build and stream_ids, which
profiles them; never read a file's text into the conversation to send it. Only pasted samples go as text
(start_onboarding samples=), about 10 records each: you write that text out token by token. start_onboarding profiles
them itself: don't call profile_sample as well. It reports what differs between files. Each file is its own stream:
step them all, and survey_feed with those stream_ids shows every kind of event the sample holds; map variants with
any_of. To fix a translation mapping, build_translation_xslt changes= only the rules or entries that change (with the
uuid it saved, or the same build and name), not the whole mapping again.

Whole feed: a sample can miss rare kinds of event. Once a feed has been processed (or indexed) whole, review_coverage
finds the records no rule matched and the events kept as Unknown, with exactly which raw streams hold them; tell the
user, add rules, and only those streams need processing again. A change to an events pipeline reaches the pipelines
that read its Events (indexing, CEF): review_coverage reviews them too, and they and their documentation are changed
with it. Each save records its change (change=) for the XSLT's version history and the doc's version control, as one
entry when the build is promoted.

Questions about the data (what a feed holds, what a field means, how to search it): describe_feed, read only,
from the generated documentation and the indexes' fields. Answer in plain words and give the documentation links.

Parsing: profile_sample names the parser. JSON (an array, or one object per line) is parsed by the Event Data
(JSON) template's JSONParser element with no text converter; a Data Splitter is for text (CSV, syslog, key=value).
XML fragments (several root elements, e.g. one <Event> per line, no root) take an XMLFragmentParser with an
XML_FRAGMENT wrapper converter: a template with one, else create_pipeline from a template whose parser is the XMLParser, with
replace_parser='XMLFragmentParser'; fragments without a namespace take the wrapper root's (records:2, or event-logging:3
for an <Events> wrapper): build_data_splitter gives the environment's own wrapper and saves it. The XSLT reads
the parser's output in its namespace (records:2 for a Data Splitter; http://www.w3.org/2013/XSL/json for JSON, root
/map for JSON lines, /array for an array), so set xpath-default-namespace to it.

Text formats: write the Data Splitter with build_data_splitter from a spec (delimited, regex, key=value, syslog with a
parsed body), which runs it on the sample and shows the records and field names; never by hand first. Records
longer than the Data Splitter's read buffer (bufferSize on <dataSplitter>, 20,000 characters by default) are lost:
build_data_splitter sets it from the sample, and buffer_size= raises it when the feed holds longer records
(stroom://guide/data-splitter). build_data_splitter also runs the converter through Stroom's own parser
(stroom_check): fix the spec until it matches, as processing does what that parser does.

Translation: start from draft_translation_mapping (the user first chooses the event-logging version it writes, from
those this Stroom holds, the newest recommended, 4.x as much as 3.x (namespace event-logging:3 is every version's):
a form, or needs_guidance for you to ask, with its options; never name or offer a version before that result lists
them, as versions differ between environments (seen: an agent offered 4.1.0, which the cluster didn't hold); then
a valid mapping drafted
from the sample: decide the action element per kind of event, the system name and environment; read_document
type=XMLSchema element= says what an element takes: EventDetail for the action elements, EventDetail/<Action> for its
children and values), then build_translation_xslt with the edited mapping and the draft's schema_version, the sample
(and the splitter spec) so fields no record has and time formats the values do not fit are caught before stepping;
never write XSLT by hand, and never send the field inventory as the mapping. A field that no element of the schema
means (a rule id, a byte count, a vendor code) is carried as Data of the action element (path
EventDetail/<Action>/Data with data_name): never invent an element for it, and never leave the event Unknown because
of it. Sources:
field, any_of (first of several names), value, xpath, lookup (reference data); modifiers: transform (lower, upper,
trim, strip_domain, domain, digits), dictionary, map, default, time_format. Values the record does not carry come
from reference data (find_reference_data; build_reference_xslt and a reference-data pipeline for new tables; the
events pipeline names the feed in references) or from a Dictionary doc (save_dictionary). Text fields holding several
values (a message string with a time, user, action and description) are parsed with the mapping's extract (a
regular expression whose groups become fields), not with substring-before/after chains; JSON held in a string is
read with an xpath using json-to-xml(). Only stroom: functions that exist may be used (format-date, lookup, meta,
dictionary, log, json-to-xml, ...; there is no stroom:json-parse); check_xslt refuses unknown ones, elements the
event-logging schema has no place for (e.g. EventDetail/ServerEvent: EventDetail holds TypeId, Description and one
action element such as Authenticate, Process, View, Alert, Unknown), and match/select expressions that would
select nothing for want of a namespace.

Rules for every run:
- Start with get_instructions (docs= the folders, feeds or documents involved): standing instructions from AGENTS
  docs in Stroom. Follow them, the most specific last. The user's request takes precedence, and they never lift an
  approval.
- Build everything in one build (start_build, with the feeds when known: it returns the standing instructions
  too); nothing leaves the workspace until promote_build is approved. Before promoting, build_status shows what
  the build's pipelines still lack (a clean step of their current code, documentation): resolve it first.
- The user confirms key details and approves risky actions in a form their client shows (MCP elicitation); in
  Stroom AI, a form in the chat. You are never given an id to approve a call with: a call that changes something in
  Stroom, from a client that can't show forms, is refused (the read-only tools still work): tell the user why, and
  don't look for another way round it. needs_review (promote_build's layout, propose_index_template's template):
  show what it returns in the chat, then call again with reviewed=<its review_id>, and the user approves it in a
  short form.
- Never assume a field convention, cluster, index name, template or feed name: propose one with where it came
  from, and let the user confirm or correct it. For a name a tool confirms (create_feed, create_pipeline,
  create_index_doc), propose it by calling the tool, not by asking in the chat first: the user confirms or corrects
  it there (a form their client shows, the name editable in it).
- Never weaken validation: do not change, remove or bypass a schema filter, or any check a template applies. When
  validation fails, the output is what to fix.
- Step every sample record (step_sample) before processing. Errors, when stepping or processing: first resolve
  those your own content causes (the XSLT, text converter, mapping or field plan), stepping again after each fix.
  Only what you cannot resolve there (an inherited template element, reference data, the source data itself) goes
  to the user, with what you tried and an example. If the user says an error is benign and can be ignored, record it
  with write_documentation accept_errors=[{element, example, reason in their words, matches: the kind it covers,
  with * for what varies, e.g. 'No HR record for user svc-*'}]; they confirm it, and it is then reported as benign
  with their reason and not raised again. Events written as Unknown are not such an error: they are agreed per rule
  in build_translation_xslt (allow_unknown). A generated XSLT is saved by build_translation_xslt (build, name) and
  stepped as saved; code written by hand is tried with draft_code before it is saved.
- Document what you build (write_documentation; stream_ids default to the streams its current code last stepped
  clean on) and use the user's source notes for field meanings.
- A processor filter on an events or indexing pipeline (create_processor_filter stream_ids=, or a feed's streams)
  runs once the user approves it in a form: Elasticsearch, only once the agreed index template is committed. An index
  the pipeline wrote to before comes first: a drop_first reply, with a button; once the user says it is dropped, call
  again with index_dropped=true for the form. Then wait_for_processing with its filter_id. Any other filter (a CEF
  output, reference data) is created disabled, as promotion's are: give the user its pipeline_link, what
  enabling does and anything before_enabling says; ask them to enable it on the pipeline's Processors tab and to tell
  you when they have. Then wait_for_processing with its filter_id: never before.
- Reprocessing in a build, after a fix (reprocess_streams, at most 10 streams a call, one task at a time): for an
  events or indexing pipeline the user approves it in a form and it runs (an index is dropped first, so it starts
  fresh: give every Events stream it should hold); any other pipeline's (CEF output, reference data) is created
  disabled, for the user to enable. Then wait_for_processing with its filter_id. Reprocessing
  production data, and switching readers from one index version to the next, are the user's.
- A build on data already in Stroom (an existing feed, a production pipeline's new version or working copy) starts
  with start_build feeds=[the feed it works from], creates no feed and uploads nothing: sample files in the workspace
  aren't used. Its records are stepped where they are (step_sample with their stream ids, step_records), and
  processed where they are (create_processor_filter with their stream ids): the server writes their Events to a test
  feed it makes in the build, never into the production feed. Only sample files the user chooses instead of the
  feed's data go to a test feed of their own (create_feed user_samples=[the files they named]). Test feeds stay in
  the workspace, and promotion's filters name the feed the build works from. Promotion creates each pipeline's processor filter, disabled, with
  no minimum creation time: give the user the pipeline link to review and enable it, and say that enabled it
  processes the feed's existing data too, the build's streams included. An indexing pipeline's says before_enabling:
  its index is dropped (or emptied) first, so it starts fresh, and, with its events pipeline promoted too, it is
  enabled only once that pipeline's filter has processed the build's streams again; ask the user to do it in that
  order. A filter marked existing is one the pipeline already had, enabled: no second one is made.
- Elasticsearch indexing in the workspace runs in batches of 10 (create_processor_filter and reprocess_streams set
  it), so a document Elasticsearch rejects is reported whole, with its reason; wait_for_processing restores the
  template's batch size once indexing completes without errors.
- Elasticsearch indexing runs only through the Stroom indexing pipeline, with an index template the user agreed
  (built by propose_index_template from their example, or their correction), and only once the cluster admin has
  committed it to the cluster: the user enables the filter then, or confirms it when approving a reprocessing.
