# Stroom AI: findings

How Stroom actually behaves, as learned building and testing Stroom AI's tools (the chat's and `/mcp`'s): the
facts the tools are designed around. Most were found by running the tools against a real Stroom (the local dev
stack, the integration test database, a production instance read-only) or by testers using the agent; some by
reading Stroom's code to explain what was seen.

Each finding says what Stroom does, what the tools do because of it, and where that is handled. Before changing how
a tool reads, steps, validates, processes, indexes or searches, read the area here: most of these were learned the
hard way, and a tool that "simplifies" past one tends to bring the failure back. When a new fact is learned, add it
here with the class that handles it. Code comments mark such facts with "Seen:" or "Seen in testing:".

The classes named are in `stroom-ai/stroom-ai-impl` (`stroom.ai.impl...`) or `stroom-ai/stroom-mcp-impl`
(`stroom.mcp.impl...`).


## Reading streams

| Finding | What the tools do | Where |
|---|---|---|
| A fetch of a segmented stream (e.g. Events) returns one record, whatever record count is asked for. A raw stream is held as one record. | Segmented streams are read record by record. A raw stream's records are split from its text as a survey splits them (each line kept as it is in the stream, with its line number, so a record can be stepped where it is). | `StreamReader` |
| A segmented record comes back wrapped in the stream's root element, but a prefixed root (`<evt:Events>`, from an XSLT with a namespace prefix) is closed by its local name alone (`</Events>`), so the record isn't well-formed. | The root is closed by its own name before the record is parsed. | `StreamReader` |
| A raw stream's text read through the stream store is checked against the user's view of the feed, but not against View Data, which the data viewer needs. | View Data is checked before any stream's text (raw, cached or an Error stream's) is read. | `StreamReader` |
| The data viewer refuses a stream with bytes its feed's character set can't decode ("Unable to decode ..."). A pipeline decodes with the feed's encoding. | Raw text is decoded with the feed's encoding, as a pipeline decodes it, and undecodable bytes are reported, not hidden. | `StreamCharsets`, `StreamReader` |
| Receipt headers (`Feed`, `ReceivedTime`, `RemoteAddress`, `UploadUserId`, custom headers) are in the `Meta Data` child part as `key:value` lines; the database holds only some attributes, and ages some off. | `stream_meta` reads the store for values the database no longer holds (`workspace.maxStreamStoreReads` a call). | `StreamMetaTool` |
| Stroom keeps a deleted feed's streams under its name (the UI doesn't show them). A new feed of the same name sees them in any search by feed name. | A feed's own streams are those created since its document was; tools building or validating with streams refuse an older one, naming the feed's own. | `StroomBuildFacts` |
| Stroom matches feed names in stream data whatever their case: a stream's headers can spell a feed differently from its meta. Stroom keeps one feed per name, whatever its case. | `create_feed` refuses a name differing from an existing feed's only in case; the build's feed is found whatever case the data spells it in. | `BuildFeeds`, `OutputFeeds`, `StreamProcessing` |
| A one-line JSON array of hundreds of KB, read in part, isn't JSON; a sample's head cut part-way through a record can look like key=value (e.g. `User: x` inside messages). | A JSON array read in part is profiled from its complete first records; a head cut part-way is profiled by its whole lines. | `SampleProfiler` |
| A CSV header over columns of plain text (`user,name,department`) is missed by a sniffer; one whose other columns are times or numbers is not. | A column whose every value is typed (a time, a number, an address) under a plain name makes the first row a header. | `SampleProfiler` |
| Very long fields are real (a 250,000-character URL query in a proxy log). | The survey's CSV reader has no field length limit; windows are read to the end of a part cut at its head. | `PyCsv`, `FeedSampling` |


## Stepping

| Finding | What the tools do | Where |
|---|---|---|
| Stroom's stepping engine captures the whole stream in a session and keeps the session, and its capture, until it is ended or has been idle for a while. A step with no session starts another capture. 500 records stepped with a fresh session each ran 500 captures of one stream at once and held Stroom's CPU at 100%. | Every step of a walk through a stream is in one session, ended however the walk ends. Each step request waits briefly and is asked again until complete. | `SteppingWalks` |
| Each step reads the stream from its start, so a step's cost grows with how far in its record is (0.15 s near the start, over 1 s at the 151st record of a production stream). | Records for documentation, review and comparison are spread evenly over the streams, not taken deep in one; `step_sample` steps at most 500 records a call and says calling again steps the same ones. | `PipelineStepping`, `StepSampleTool` |
| Stepping drops comments from output, including those an XSLT writes with `xsl:comment`, but keeps processing instructions. A literal comment in an XSLT template isn't copied to the output at all. | The translation generated for a documentation run (never the saved XSLT) marks each event with its rule as a processing instruction, so documentation can tell which rule wrote an event from stepping output. | `XsltGenerator`, `FieldMappingSections` |
| Stepping puts an XML declaration (`<?xml version="1.1" ...?>`) before a text XSLT's output. | The declaration is dropped from text output (a syslog prefix kept) before CEF lines are checked. | `CefLines` |
| Stepping one record may not number its event as processing did: `IdEnrichmentFilter` counts the events it sees, and a record stepped alone was numbered 1 on a production Stroom (the local stack numbered it right). | A traced document whose EventId differs from the hit's is matched on the hit's other columns, saying so. | `IndexChecks` |
| Stepping sees one record a document; processing gives the whole stream. An XPath from the document root (`//*:EventData/*:Data`) stepped clean, then failed processing fatally ("more than one item ... fn:replace()"). | A mapping path from the document root is a problem, saying to write `.//`. | `EventTrees` |
| An XSLTFilter with no XSLT is a no-op: records pass through unchanged. | Unset XSLT steps after a template's first are optional; the first is where a child's translation goes. | `PipelineShapes` |
| JSONParser output is in `http://www.w3.org/2013/XSL/json` (not `xpath-functions`, which is `json-to-xml()`'s). With the wrong namespace no template matched, stepping reported no error, and processing wrote no Events and no Error stream. | The tools give the namespace to match; stepping output with no XML elements, or records producing no Event, block. | `MappingFacts`, `SampleProfiler`, `SteppingVerdicts` |
| A JSON parser's root map (`addRootObject` true, the default) makes a whole JSON array one record: a 755 KB array of 985 events stepped as one record and outlasted every step's wait. | `addRootObject` is set false when the build's sample is a JSON array (not for JSON lines, which need the root map); `step_sample` says when a JSON pipeline stepped a stream as one record. | `PipelineWrites`, `SteppedPipeline` |
| A pipeline property written as the wrong type (`addRootObject` as the string `"false"`) is accepted when saved, and then every step of that pipeline, of any stream, outlasted its wait. | Plain property values are written as the types Stroom declares for the element; a value not of its type, or a property the element lacks, is refused; stepping refuses a pipeline holding one. | `PipelinePropertyTypes`, `PipelineWrites`, `SteppedPipeline` |
| XML fragments that declare no namespace take the fragment wrapper's (records:2 by default). Read with none, every record came out as an empty Events and stepping said clean. Environments often have their own wrapper (an `<Events xmlns="event-logging:3">` one for fragments that are Events already). | The environment's own wrapper is followed (its copy tidied: comments dropped, DOCTYPE named after its root), and fragments are checked inside it, as Stroom reads them. | `FragmentWrappers`, `SampleProfiler`, `MappingFacts`, `SteppingVerdicts` |
| A template with no link into its parser (Stroom's own Event Data templates store no Source link) still processes, but a replaced parser shows unconnected in the editor. | A replaced parser takes over any link into the old one; with none, Source is added and linked, as Stroom's own child pipelines store it. | `PipelineWrites` |
| Error stream markers carry no record position; a bad date was a WARNING in the XSLT that caused two schema ERRORs. | Record positions come from stepping; triage keeps the cause (own XSLT warning) beside its effect (schema error). | `ErrorTriage`, `error_rules.yaml` |


## Validation and schemas

| Finding | What the tools do | Where |
|---|---|---|
| Stroom matches a schema location's system id exactly. For one it doesn't hold, in a namespace it holds schemas for, its schema filter validated against another version (event-logging 3.2.3's, refusing `Version 4.1.0`) rather than saying the id was unknown. | `check_xslt` (every save and draft step) and `validate_events` refuse a location Stroom doesn't hold, giving the id to write. | `HeldSchemas`, `CheckXsltTool` |
| Which event-logging versions a Stroom holds varies by instance (content packs ship 3.0.0 to 4.1.0; a production instance held 3.5.2 and 4.0.2). Every version, 4.x as much as 3.x, is in namespace `event-logging:3`, which models read as "a 3.x schema". | There is no configured version: the build's version is chosen from those held (the newest recommended), asked once and remembered; versions are listed newest first. | `EventVersions`, `HeldSchemas` |
| Records are written as XML 1.1: control characters (an ANSI colour code's ESC) are allowed in records that an XML 1.0 parser refuses. | Sample records are read as XML 1.1; `transform: strip_control` removes control characters. | `XPathChecks` |
| A records:2 document, or a source's own XML, fails every element when judged against event-logging. | The schema is picked by the root (event-logging, records-v2.0); a source's own XML is said not to be Events. | `EventChecks` |


## Stroom's XSLT engine

| Finding | What the tools do | Where |
|---|---|---|
| Stroom runs Saxon-HE 9.9: XPath 3.1 and XSLT 3.0 (`let`, `!`, `=>`, maps and arrays, `xsl:iterate`, `xsl:for-each-group`), but no higher-order functions. Inline functions, function references (`upper-case#1`), functions taking a function (`fold-left`, `for-each`, `filter`, `sort` with a key) and string constructors fail to compile, Saxon saying they need Saxon-PE. Checked against Stroom's own Saxon, case by case. | A mapping XPath using one is a problem when the XSLT is built; the compile error is said in Stroom's terms with what to use instead. | `StroomXPath`, `XsltCompileCheck` |
| An XSLT that doesn't compile ("Variable kv has not been declared") is only found by stepping. | Every generated XSLT is compiled as Stroom compiles it (Stroom's and imported functions known) and not saved if it fails. | `XsltCompileCheck` |
| `stroom:format-date` keeps milliseconds by cutting finer digits (`.456789` gives `.456`), never rounding. The schema takes exactly three digits. | Generated times are cut, not rounded, so an event's time is never moved on (rounding moved `23:59:59.9996` into the next day). | `XPathText` |
| `stroom:record-no()` counts from 0, and in processing it is only right if read for every record: the location it reads is queued per record and the queue stops storing past the split count plus 10, so a translation that read it only for unmatched records lost their numbers after the first 110 (stepping, a record at a time, hid it). | The translation reads `stroom:record-no()` once for every record; review examples are numbered from 0, as `step_records` takes them. | `XsltGenerator`, `CoverageReads` |
| `stroom:log('WARN', ...)` lands in the Error stream of the raw stream being processed (`parentMetaId`), as "Log - ...". | Unmatched records are logged at WARN, and records kept as Unknown at INFO (benign in triage), so coverage is found from Error streams over the whole feed. | `XsltGenerator`, `CoverageReads`, `ReviewCoverageTool` |
| `json-to-xml()` on an empty value, or one that isn't JSON, is a fatal "empty sequence" error that stops processing. | Generated XSLT reads JSON held in a field through a guarded function (`xsl:try`); `check_xslt` warns of an unguarded call in a hand-written XSLT. | `XsltGenerator`, `CheckXsltTool` |
| An XSLT's functions can reach outside Stroom (`http-call`, `fetch-json`, `ask-ai`, `stroom:host-name`, `stroom:host-address`) and read Stroom's own files and environment (`unparsed-text()`, `doc()`, `environment-variable()`, `xsl:result-document`, an import by URL), with Stroom's access on its nodes. | Such calls are refused at save and in mapping XPaths; `host-name` and `host-address` are allowed once the user agrees in a form; a working copy keeps only what its production XSLT already did. | `CheckXsltTool`, `XPathChecks` |
| Hand-written translations often do what no mapping holds, e.g. build a variable of distinct users with `xsl:iterate` and `xsl:copy-of` it. | Such an edit is the user's to keep or drop; `rebuild_mapping` refuses while part of an event couldn't be read back unless the loss is accepted; documentation of a hand-edited XSLT comes from its output. | `HandEditGate`, `RebuildMappingTool`, `EventFieldDocs` |


## Text conversion (Data Splitter)

| Finding | What the tools do | Where |
|---|---|---|
| A Data Splitter only holds a record up to half its buffer (it refills only when below half): `bufferSize` must be at least twice the longest record. CSV lines of about 20,000 characters were dropped by the default, and a 751,000-character record exhausted a 1,000,000 buffer. | The generated splitter sets `bufferSize` from the longest record seen; the limit is said where the agent meets it. | `SplitterXml`, `BuildDataSplitterTool` |
| A split on `=` writing `$2` fails ("Group number 2 not found"): a split has one group. A value written as `$1$2` from two groups is refused when one took nothing. | Key=value is one regex with one value group, matched along the text; a headerless column is one group. | `SplitterXml`, `SplitterDryRun` |
| Element ids must match `[a-z][a-zA-Z0-9]*` (data-splitter-v3.0.xsd, `IdSimpleType`). | Generated ids follow it (e.g. `blankLine`). | `SplitterXml` |
| Stroom doesn't strip a byte order mark: it became part of the first key, and the first event had no time. | Every generated splitter takes a BOM first. | `SplitterXml` |
| Blank lines are written as records unless passed over; carriage returns are taken out, so lines ended by a carriage return alone run together. | Generated splitters pass over blank lines; CR-only line ends are recognised. | `SplitterXml`, `BuildDataSplitterTool` |
| A doubled quote inside a quoted CSV value is kept doubled. A record holding quoted line breaks is split on the line break by a splitter that splits on `\n`, making fragments. | The dry run keeps doubled quotes as Stroom does (`unescape_quotes` reads them); the survey reports quoted values over several lines. | `SplitterDryRun`, `SurveyFeedTool` |
| A splitter's regexes can't be stopped part-way. | The local run of a splitter is bounded in time, a run over it an error. | `StroomSplitterRun` |


## Processing and reprocessing

| Finding | What the tools do | Where |
|---|---|---|
| A pipeline's new processor takes its first filter's enabled flag, and a disabled processor runs none of its filters. | The tools enable the processor they make with a disabled filter, so enabling the filter is the one choice left. | `ProcessorFilterWrites` |
| Reprocessing a raw stream with the same filter marks the earlier output deleted (one Events stream per raw stream). `purgeOnReprocess` doesn't apply to a second filter on the same streams, and nothing is removed from a Lucene index or Elasticsearch. | A second plain filter over processed streams is refused, pointing to `reprocess_streams`; a build's own Lucene index is cleared first once approved; superseded (deleted) Events streams are refused, naming those in their place. | `ReprocessStreamsTool`, `IndexingRuns` |
| A stream filter's `Pipeline` term is a DocRef: `IS_DOC_REF` selects only that pipeline's outputs; `EQUALS` on a name is refused. Production filters name feeds three ways: `Feed IS_DOC_REF`, `Feed IN_DICTIONARY` (a Dictionary, with imports) and a stream `Id` alone. | Filters are written by reference; filters read back are resolved all three ways. | `ProcessorFilters`, `PipelineFeeds` |
| Translation pipelines write Events, and Error streams, into the input's feed. | A build on an existing feed writes its Events to a test feed of its own (`<FEED>-MCP-TEST`, or the next free name) through the StreamAppender's `feed` property. | `ProcessingSources`, `OutputFeeds` |
| Filters of the same priority get work in Stroom's order: the filter whose tracker is furthest behind first (`ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR`), not by filter id. A profile's creation budget is used up by a filter's existing backlog, so a high-priority filter can starve lower ones. | `explain_processing` uses Stroom's order and counts same-priority filters ahead. | `ProcessingExplainer` |
| Stroom never retries or clears a tracker in ERROR. The first poll after a filter is created creates no tasks. Overnight profile periods (end before start) only apply until midnight. | `explain_processing` says so, with the fix. | `ProcessingExplainer` |
| Why a task failed goes only to its node's application log. | `processor_tasks` gives the node and times to look up there; Stroom AI never reads logs. | `ProcessorTasksTool` |
| A meta search gives an Error stream's Warning, Error and Fatal Error counts only sometimes. An Error stream can be unreadable (its volume gone). | An Error stream is clean only when its counts are there and all zero; an unreadable one is never summarised as clean. | `CoverageReads`, `StreamSummaries` |
| Stroom never deletes data for want of space: only retention rules (by age) or a user delete it. Asked, a model said Stroom deletes the oldest streams. | `volumes` and `explain_retention` say how Stroom behaves; the chat's prompt says to describe Stroom only from tool results and its documents. | `VolumeReport`, `ExplainRetentionTool` |


## Reference data

| Finding | What the tools do | Where |
|---|---|---|
| Stroom remembers "no effective streams" for a reference feed for 10 minutes (the effective stream cache): a pipeline stepped before its reference data was processed found nothing for the next 10 minutes. | `step_sample` refuses a pipeline whose reference feed has no stream of the referenced type yet; the plan holds stepping until it has. | `PipelineStepping`, `BuildPlan` |
| A reference stream takes effect from its effective time, else when it arrived: a directory uploaded after the events it was to enrich didn't apply to them. | A reference sample applies from 2000-01-01 unless told otherwise. | `UploadSampleTool` |
| Some pipelines can't be built by a given Stroom (an element type it lacks, such as `StateFilter`). | They are left out of template and loader searches and listed as unreadable. | `ReferenceData`, `PipelineTemplates` |


## Indexing: Lucene

| Finding | What the tools do | Where |
|---|---|---|
| Fields of type `KEYWORD` were accepted but indexed and stored nothing. `TEXT` with the `KEYWORD` analyser works, as the content pack's Example Index does. | A keyword field is `TEXT` + `KEYWORD` on Lucene. | `FieldPlans` |
| Stroom drops a value for a field the index lacks, with only a warning ("Attempt to index unknown field"): the pipeline runs and indexes nothing searchable. | An indexing XSLT's fields are checked against the index before processing. | `CreateIndexingPipelineTool` |
| On Lucene, STARTS_WITH and ENDS_WITH, CONTAINS on a keyword field (or part of a word), a range on a text field (e.g. an address), and IN with a value holding a space all find nothing, with no error; EQUALS with wildcards finds them. | `verify_index` refuses those searches, naming the one to use. | `MisleadingSearches` |
| A Lucene index's fields may be case-insensitive (`EQUALS Bob` finds `bob`). | Index docs say which fields aren't case-sensitive. | `IndexDocs` |
| Shard document counts aren't reliable until flushed and committed. | Verification counts by search, never by shard counts. | `IndexChecks` |


## Indexing: Elasticsearch

| Finding | What the tools do | Where |
|---|---|---|
| Stroom leaves fields of types `match_only_text`, `flattened`, `geo_point` and `search_as_you_type` (and their subfields) out of an Elastic Index doc's fields, so it can neither search nor show them; `ecs@mappings` maps `message` as `match_only_text`. | Such fields are mapped as the plan says (`message` as `text`); a search on a field the doc doesn't list is refused, saying why. | `EcsSchema`, `IndexFields`, `IndexChecks` |
| A field mapped `index: false` is listed by Stroom as not queryable; searches on it find nothing. | Checks on such fields are refused, saying why. | `MisleadingSearches`, `IndexFields` |
| A wildcard doesn't match an `ip` field (`IpAddress EQUALS 192.0.2.*` found 0). STARTS_WITH and CONTAINS find nothing. IS_NULL and IS_NOT_NULL match every document. | `verify_index` names the CIDR range or the wildcard to use; IS_NULL and IS_NOT_NULL are searched as NOT_EQUALS and EQUALS `*`. | `MisleadingSearches`, `IndexEvents` |
| A value Elasticsearch can't take for a field's type fails the document: W3C logs' `-` for a missing address was refused by an `ip` field for every document holding it. | Placeholder values (`-`, `N/A` and the like) are found and the user asked whether to leave them out. | `Placeholders`, `PlaceholderChoices` |
| A data stream keeps the mapping it was made with: a changed template doesn't reach it. | Before reprocessing into a changed index, the user is given the request to drop it first. | `IndexingRuns` |
| A GET of an index or component template carries fields Elasticsearch sets itself (`created_date_millis`, `modified_date_millis`, a lifecycle's `rollover`), each refused in a PUT. | Pasted templates are kept to what a PUT takes (an allowlist). | `IndexTemplates` |
| Elasticsearch has `ecs@mappings` but no `ecs@settings`. | Only components that exist are composed. | `IndexingHelpers` |
| A field written with `value-of` over several values gives one joined value (`"80 443"`, not a number). | Fields whose source can repeat are written as arrays (one `data` element per value on Lucene). | `IndexingXslt` |
| Stroom never runs Elasticsearch requests for the agent. | Template commits and index drops are given to the user as requests for the cluster admin. | `UserActionService` |


## Searching an index (both backends)

| Finding | What the tools do | Where |
|---|---|---|
| A hit is returned only when its `StreamId` is a stream in this Stroom the user may read (`ExtractionDecorator`, shared by Lucene and Elasticsearch): documents with no `StreamId`, or one Stroom doesn't know, are left out silently. | An index written from outside Stroom is surveyed only as far as its documents point at this Stroom's streams; a survey with nothing to show says the streams may be unreadable or deleted. | `IndexSurveys` |
| Stroom sorts a table itself, so a sorted search reads every matching document; an unsorted, ungrouped search ends once it has its rows. | The agent's surveys and checks search unsorted. | `IndexSurveys`, `IndexChecks` |
| Stroom keeps a search running for up to a day by default, even when nobody asks for its results: three whole-index scans carried on after a tool gave up on them. | Every search a tool starts is stopped once read. | `AgentSearches`, `IndexSearches` |
| A table's search result output reads extraction values by name, the first given for each name; extraction replaces the index's values for that table. | Extraction previews read values as Stroom does. | `ExtractionXslt`, `ExtractionPreview` |


## Documents, folders and pipelines

| Finding | What the tools do | Where |
|---|---|---|
| Stroom's explorer search returns the folders that contain a match whatever types are asked for, and gives paths from `System`. | Results are kept to the types asked for; paths with or without `System` are taken. | `FolderContentsTool`, `DocumentAccess`, `WriteGuard` |
| The explorer search can list a document for a while after it is deleted, and miss one just created. | A listed document Stroom can't give is skipped; a build's folder is found in the explorer tree. | `GeneratedDocs`, `DocumentPlaces` |
| Stroom's content index is built on a node only once something searches it; until it is ready a search finds nothing. Document content is indexed in the background, so content just saved is missed for a while. | The first search starts the build and waits a moment; while not ready, the documents of the type are looked in directly. | `PipelineIndex` |
| A parent may hold several folders of the same name. | Folders are looked for again before one is made, and made one at a time. | `DocumentPlaces` |
| Stroom refuses a save of a document changed since it was read. | Two writers of a build record retry on the newer version rather than lose a record. | `BuildRecords` |
| A pipeline whose stored structure Stroom no longer loads (a property its element type no longer has) fails any read of it. | Such pipelines are reported as unreadable and never hide the others. | `PipelineUnreadableException`, `PipelineTemplates`, `DescribeFeedTool` |
| A Documentation doc can be made directly under System. AGENTS docs are read per folder, a doc applying to its folder and below. | Standing instructions are read from the folders above a document. | `AgentInstructions` |
| A link to a document is `?action=open-doc&docType=<type>&docUuid=<uuid>`. | Answers link documents in that form. | `AnswerLinks` |


## Sessions, clients and `/mcp`

| Finding | What the tools do | Where |
|---|---|---|
| With Stroom's internal identity provider, the token Stroom issues an MCP server for a user lasts 5 minutes and is reused until a minute is left (`UserTokenIssuerImpl`), so a long tool call on that server can outlast it. | Slow tools stop before a token runs out, saying to call again. | `McpToolServerFactory` |
| MCP's `initialize` carries no session id, so a load balancer hashing the `Mcp-Session-Id` header sends follow-ups to the wrong node. | Affinity is by client IP (nginx `ip_hash`, `sessionAffinity: ClientIP`, ingress-nginx `upstream-hash-by`). | `McpServlet` |
| Since Jackson 3.2, the MCP SDK's `ElicitRequest` couldn't be written ("Conflict between type id property 'mode' and bean property with same name"): no form reached a client, and the call waited until it timed out. | `McpJson` is the SDK's mapper with a mix-in; both `/mcp` and the chat's MCP client use it. | `McpJson` |
| The Java MCP SDK's client speaks protocol 2025-11-25 at most, where a form is a request from the server during the call; stateless servers can't send one. | The chat's client keeps sessions; `/mcp` is stateful. | `McpToolset`, `StroomMcpServer` |
| VS Code saves a tool result over about 8 KB to a file for the model to read in parts, and its `read_file` cuts a line at 2,000 characters; a model that read a cut sample made up the rest. Windows PowerShell round-trips text in the ANSI code page (`â€”` for an em dash). | Results are kept short; sample files go from the user's disk straight to `/mcp/upload`; tool arguments are repaired of that mojibake. | `UploadSampleTool`, `MojibakeRepair` |
| Stroom's UI shows no form of more than 20 fields, a 2,000-character message or a 16,000-character schema. | Tools check a form against the limits and ask in the chat when it is too large. | `ElicitationSchema`, `Consent` |


## The chat and models

| Finding | What the tools do | Where |
|---|---|---|
| The 103 built-in tools' definitions took about 51k tokens of a 131k context window before the question. | Tools only some questions need are offered behind `activate_<group>` tools (~14k tokens at a question's start). | `BuiltInToolGroups`, `ToolGroups` |
| A blocking call to the model for a summary hit the 60 s read timeout and was retried (about 28 of 66 minutes of one onboarding). | Summaries stream, as other calls do, and Stop ends them. | `ModelStreamer`, `McpAgentChat` |
| Answering a tool's form ends a run and starts another from the stored steps: without care each form was followed by a summary of every step so far. | The latest summary is kept per chat and put back as a run starts. | `TurnSummaries` |
| Models often end a reply by saying what they will do next, with no tool call, and the run ends there. | Such a reply is shown as interim and the model told to make the call (at most twice in a row). | `UnfinishedReplies` |
| A model asked what it is guesses (Qwen gave `claude-opus-4-5`). | The model is taken from the request's `_meta` (`stroom.ai/model`), never from the agent. | `McpToolset` |
| Models invent tool names (360 calls to made-up `activate_*` names in one session) and stop when a tool is hidden in a group. | Every `next` names the group to activate; only listed groups are offered. | `BuildPlan` |
| Small self-hosted models write slowly (about 22 tokens a second): sending a 4,400-character sample took 77 s, and each whole mapping resent 43 s. | Samples go by stream id or file path, never through the model; fixes are sent as `changes=`, merged into the mapping kept for the session. | `SampleTexts`, `MappingChanges`, `SentMappings` |
| Models describe Stroom from other products (Splunk, Elasticsearch, Kafka). | The chat's prompt says to describe Stroom only from tool results, its documents and the conversation. | `AskStroomAiConfig.chatSystemPrompt` |
| A class Stroom can't load (its jar rewritten under the running node) fails a run part-way. | The run fails, saying so, rather than hanging. | `AiChatRunManager` |
| A rate limit (HTTP 429) can mention tokens. A server's "too large" error gives the real size (vLLM: "135904 in the messages"). | A 429 is never taken as the context being too large; the size given corrects the estimate. | `ContextAssembler`, `ChatCompactor` |


## Not yet tested

* The disabled-node claim: a node disabled in Stroom still processes data while its Data Processor job node is
  enabled. Found by reading the code (`DistributedTaskFetcher` fetches regardless), not proven by a two-node test,
  so `explain_processing` doesn't state it.
* Stepping EventId numbering on a production Stroom: one record stepped alone was numbered 1 there, while the local
  stack numbers it as processing does. Not reproduced.
* The stepping progress counter (gh-5930): on some large Data Splitter streams, starting stepping runs the progress
  counter instead of landing on record 1. Not explained.
* Refresh on the Stepping screen after the chat applies an unsaved XSLT change: reported once as not showing the
  change until the next step; no fault found, waiting for a re-test.
* Elasticsearch fields of the native `wildcard` type are searched with `query_string`
  (`SearchExpressionQueryBuilder.buildStringQuery`), a core Stroom issue not handled by the tools.
* Extracted values for Elasticsearch KEYWORD and IPV4 fields come out blank (`FieldValueExtractor.convertValue`);
  being fixed separately.
* The cost of the Data Splitter's blank-line regex with very large buffers.
* Very large indexes and streams: the survey's and verification's search limits are unit-tested and run on the
  local stack, not measured on an index of millions of documents.
