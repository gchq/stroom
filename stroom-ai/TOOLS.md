# Built-in tool inventory

A developer reference for the tools "Ask Stroom AI" gives its agent (`stroom-ai-impl`, package
`stroom.ai.impl.tools`), and how they line up with the tools of the Stroom MCP server
([stroom-fastmcp-server](https://github.com/gradata-systems/stroom-fastmcp-server)). It is groundwork for folding
the MCP server into Stroom (see the roadmap in [README.md](README.md)). `TestToolInventory` fails if a built-in tool
is added, removed or renamed without this table changing too, or if a tool's read-only flag differs from the table.

What the columns mean:

* **Modes**: the chat modes the tool is offered in. Tools that change something are only offered in Agent mode;
  the others in Agent, Ask and Plan (`golden/tools/catalogue.txt` has the exact lists).
* **Read-only**: `StroomTool.isReadOnly()`, which maps to MCP's `readOnlyHint`.
* **Requires**, **Unlocked by**, **Documents**: `StroomTool.permissions()` (`ToolPermissions`): the app permissions
  without which the tool refuses, those that unlock parts of its output, and the document permissions it checks on
  each document it reads or uses. They are metadata only: each tool makes its own checks.
* **Chat-only**: whether the tool needs a chat session.
  * *yes*: its purpose is the chat itself: the tab the user attached, the chat's workspace files, its history,
    or a user-action button.
  * *workspace*: it refuses today without a session workspace, as every tool built on `AbstractWorkspaceTool`
    does (for large results and `out` files), but nothing else about it needs the chat.
  * *no*: it works in any session.
* **MCP server**: the MCP server tool with the same purpose, if any. The differences are listed below the table.

Each description is made of general and chat-only parts (`ToolDescription`): the chat's model is shown every part,
and `ToolDefinition.general()` leaves out the chat-only ones (other tools' names, the workspace, `out` files, the
attached tab) for a client outside the chat. `TestBuiltInToolDescriptions` fails if a general part mentions any of
those. The general text of a *yes* tool is thin, as most of what it says is about the chat.

## Built-in tools

| Tool | What it does | Modes | Read-only | Requires | Unlocked by | Documents | Chat-only | MCP server |
|------|--------------|-------|-----------|----------|-------------|-----------|-----------|------------|
| `ask_user` | Asks the user questions in a form and waits for the answers | all | yes | | | | no (needs a way to ask, e.g. elicitation) | none (each MCP tool asks for its own consent) |
| `config_properties` | Searches, gets and lists system properties, with overrides and their sources; secrets masked | all | yes | Manage Properties | | | workspace | |
| `current_results` | Snapshots the results table of the attached query or dashboard tab | all | yes | | | Use | yes (attached tab) | |
| `delete_file` | Deletes a file in the workspace's `/outputs/` | Agent | no | | | | yes (workspace) | |
| `describe_data_source` | A data source's fields, conditions, documentation and AGENTS docs, and the searches of an index that would mislead | all | yes | | | View, Use | workspace | `describe_feed`, `find_events` (part) |
| `document_documentation` | A document's Documentation tab | all | yes | | | View | workspace | `describe_document` (type Documentation) |
| `document_info` | A document's type, path, tags, history, version and dependencies | all | yes | | | View | no | `describe_document` (part), `find_documents` (by path) |
| `document_links` | Follows dependency links (dependants or dependencies) from documents | all | yes | | Manage Processors | View | workspace | |
| `elastic_index_templates` | An Elastic Index's index template and its component templates, read from its cluster, each by name with its JSON body | all | yes | | | View | no | none (`check_index_template` and `propose_index_template` take templates the user gives) |
| `fetch_events` | Fetches the events behind StreamId/EventId rows into an XML file | all | yes | View Data | | | yes (workspace) | `read_stream`, `locate_event` (part) |
| `find_in_content` | Finds text in documents' content (XSLTs, converters, dictionaries) | all | yes | | | View | workspace | `find_documents` (with `content`) |
| `folder_contents` | An Explorer folder's documents, streams, processor filters and task counts; finds documents by name | all | yes | | Manage Processors | View | no | `find_documents`, `processing_status` (part) |
| `get_current_date` | Today's date in UTC | all | yes | | | | no | |
| `get_current_datetime` | The current date and time in UTC | all | yes | | | | no | |
| `get_current_time` | The current time of day in UTC | all | yes | | | | no | |
| `get_instructions` | The AGENTS docs that apply to folders or documents | all | yes | | | View | no | `get_instructions` |
| `get_utc_time_offset` | A time zone's offset from UTC | all | yes | | | | no | |
| `grep` | Finds lines of a workspace file containing some text | all | yes | | | | yes (workspace) | |
| `jq` | Runs a jq query on a workspace JSON file | all | yes | | | | yes (workspace) | |
| `list_data_sources` | Lists the data sources the user can query | all | yes | | | View, Use | workspace | |
| `list_visualisations` | The charting libraries installed, the chart kinds each draws with their columns and settings, and the Visualisations in Stroom | all | yes | | | | workspace | |
| `ls` | Lists the chat's workspace files | all | yes | | | | yes (workspace) | |
| `memory` | Reads and changes the user's and the chat's memory | Agent | no | | | | yes (chat) | |
| `pipeline` | Chains data tools over workspace files | all | yes | | | | yes (workspace) | |
| `preview_extraction` | Tries a draft search extraction for the attached dashboard's table on some of its events, by stepping, to add values from the events | all | yes | Stepping, View Data | | View | yes (attached tab) | `step_records` (part) |
| `processor_tasks` | Processing failures and why, backlogs, task lists and counts across the cluster | all | yes | Manage Processors | View Data | | workspace | `processing_status`, `summarise_streams` (errors) |
| `propose_code_change` | Proposes a change to the XSLT or converter being stepped, tried by stepping | all | yes | | | View | yes (stepping tab, button) | `summarise_fix`, `step_pipeline` (part) |
| `propose_dashboard` | Offers a button to create a dashboard from StroomQL | all | yes | | | Use | yes (button) | `verify_index` (part: it creates a dashboard) |
| `propose_dashboard_change` | Proposes changes to the attached dashboard's tables and components | all | yes | | | View | yes (attached tab, button) | |
| `propose_documentation_change` | Proposes a change to a document's Documentation tab, reviewed as a diff | all | yes | | | View | yes (button) | `write_documentation` (part: it writes a pipeline's or an index's) |
| `propose_query` | Offers a button to create a StroomQL Query document | all | yes | | | Use | yes (button) | |
| `propose_query_change` | Offers a button to change the query in the attached tab | all | yes | | | View, Use | yes (attached tab, button) | |
| `propose_visualisation` | Plans a chart of the attached dashboard's (or query's) table, checks its rows can make it, and offers a button to create it there | all | yes | | | | yes (attached tab, button) | |
| `read_document` | A document's content, or what a processor filter selects; a pipeline as Stroom runs it, an index's survey | all | yes | | Manage Processors | View | workspace | `describe_document` |
| `read_file` | Reads lines of a workspace file | all | yes | | | | yes (workspace) | |
| `read_turn` | Reads a whole earlier turn of the chat | all | yes | | | | yes (history) | |
| `run_stroomql` | Runs a StroomQL query into a workspace table, refusing one on an index whose answer would mislead | all | yes | | | | workspace | `find_events` (part) |
| `search_events` | XPath, text or jq search over fetched events, into a table; or, given an index, finds events in it as a dashboard would | all | yes | | | Use | workspace | `summarise_streams` (events, part), `find_events` |
| `search_history` | Searches the chat's history for text | all | yes | | | | yes (history) | |
| `server_tasks` | The tasks running on every node now (Monitoring > Server Tasks) | all | yes | Manage Tasks | | | workspace | |
| `str_replace` | Replaces text in a workspace file | Agent | no | | | | yes (workspace) | |
| `stream_meta` | Finds streams and their meta, grouped and aggregated if asked | all | yes | | View Data | | workspace | `find_streams`, `describe_stream` |
| `summarise_column` | Reduces a free-text column to patterns, and summarises them | all | yes | | | | yes (workspace) | |
| `table` | Queries a workspace table with Stroom expressions | all | yes | | | | yes (workspace) | |
| `task_list` | Keeps the task list the user sees | all | yes | | | | yes (chat) | |
| `validate_stroomql` | Checks a StroomQL query without running it | all | yes | | | Use | workspace | |
| `wc` | Counts a workspace file's lines and bytes | all | yes | | | | yes (workspace) | |
| `write_file` | Writes a file in the workspace's `/outputs/` | Agent | no | | | | yes (workspace) | |
| `xpath` | Runs XPath on a workspace XML file | all | yes | | | | yes (workspace) | |
| `xslt_lookups` | The reference data lookups XSLTs make, and which loaders write each map | all | yes | | | View | workspace | `find_reference_data` |

`run_stroomql` checks no permission itself: Stroom's query service checks Use on the data source when the search
runs. `read_document` and `xslt_lookups` also read the attached tab's unsaved content in a chat.

Only these tools say they read only trusted content (`StroomTool.readsUntrustedContent()` false, MCP's
`openWorldHint` false), so their results don't make a chat count as having read untrusted content (see Approvals in
[README.md](README.md)): `ask_user`, `delete_file`, `get_current_date`, `get_current_datetime`, `get_current_time`,
`get_utc_time_offset`, `memory`, `str_replace`, `task_list`, `wc` and `write_file`. Every other tool, the ported
tools included, reads untrusted content: `propose_query`, `propose_dashboard` and `validate_stroomql` too, as they
name the data source as Stroom holds it, and `read_turn` and `search_history`, as they copy the chat's stored tool
results, which outlive the rows they came from if the user deletes those.

## How the overlapping tools differ

The MCP server calls Stroom's REST API with the user's forwarded token, so Stroom checks the user's permissions; it
checks no app permissions itself. Its writes are limited to a workspace folder (`WriteGuard`) and need a
confirmation or approval (an elicitation form, or a pending id the user confirms; Stroom's port asks only in a
form, see `Consent` below). The built-in tools call Stroom's
services directly as the user, and those listed here only read.

An MCP tool merged into a built-in one is no longer a tool, so the hints, notes, refusals, `next` calls, guides and
server instructions that named it name the built-in that does it now, with the built-in's own arguments (e.g.
`stream_meta feeds=['ACME'] type='Events'` for `find_streams stream_type=Events`, `folder_contents name='*'
types=['ElasticIndex']` for `find_documents`, `find_in_content pattern=` for `find_documents content=`).
`TestBundledKnowledge` (`stroom-mcp-impl`), as the MCP server's `test_knowledge.py`, fails if a guide, the server
instructions or any tool's description names a tool that doesn't exist.

* **`get_instructions`**: same name and purpose, and the same answer in substance (the docs that apply, most general
  first, then where the others are). MCP takes `folders`, `feeds` and `docs` (`{type, name, uuid?}`); the built-in
  takes `folders` (UUID or path) and `uuids`, and has no `feeds`. MCP's doc name is configurable
  (`instructions_doc_name`); the built-in's is always `AGENTS`.
* **`folder_contents`, `find_in_content` and `find_documents`**: MCP's `find_documents` finds by name (the Explorer
  quick filter) or, with `content`, by text in content (case-insensitive contains, `types` filtered after the
  search). It is merged into three built-ins: by name, `folder_contents` given `name` (with `types` and `limit`)
  finds documents as the quick filter does, in the folder given and every folder below it, or across the whole tree
  with no `folder`, each with its type, UUID, name and folder path; by content, `find_in_content` (`pattern`,
  `regex`, `matchCase`, `uuids` and `types`, leaving out folders the AGENTS docs say to ignore); and a document
  given by its Explorer path rather than its UUID, `document_info` `path`.
* **`read_document`, `document_info`, `document_documentation` and `describe_document`**: MCP has one tool, keyed by
  `type` (required) and `uuid`, which adds analysis: a pipeline's effective properties per layer, where each XSLT
  output element comes from, an index survey, Documentation passages matching `find`, and what an event-logging
  schema element takes. The built-ins take `uuid` alone (`read_document` also `part`), split content, metadata and
  documentation into three tools, combine a dictionary with its imports, and save large content to the workspace.
  `read_document` has taken in two parts of `describe_document`: an XSLT is given with what its translation does
  (`describe_translation`, as a `## Translation` section, saved to the workspace when large; not with `part`), and
  for the event-logging XMLSchema `element` says what an element takes (`describe_event_element`), as JSON, with
  `uuid` optional when `type` is `XMLSchema`. Unlike `describe_document`, an event-logging XMLSchema read without
  `element` is still its XSD (as before, saved to the workspace when large) rather than EventDetail's description,
  and with no `uuid` the version is always the newest held (no build's chosen version yet).
  It has also taken in the rest: a pipeline is given as Stroom runs it (`describe_pipeline`, as a `## Pipeline`
  section of JSON: `pipeline`, `template_chain`, `chain`, `elements`, `links`, `properties` with the layer that set
  each as `from`, `references`, `removed_by_this_pipeline`, and also `own_elements` and `translation_docs`, from
  `MergedPipeline.description()`); an Elastic Index or Lucene Index doc is given with a survey of what the index
  holds (`survey_index` without the index's totals, its sampled documents left out and each feeding pipeline's
  `plan` a boolean, as a `## Survey` section, or `Could not survey the index through Stroom: ...` when it can't
  be); and an XSLT saved with a mapping that no longer reads says why (`lost_mapping`, as a `## Kept mapping`
  section `{"lost": ...}`). The pipeline, translation and survey sections are left out when `part` is given, and
  any section too large to show is saved to the workspace. The survey and the lost mapping come from the ported
  helpers (`IndexSurveys`, `XsltSaves`) through `DocumentAnalyses`, an interface in `stroom-ai-impl` that
  `stroom-mcp-impl` binds (`DocumentAnalysesImpl`); without that module `read_document` gives neither. Where
  `describe_document` gave a pipeline's `processing` as the feeds its filters read (`pipeline_feeds`),
  `read_document` lists its processor filters and what each selects (`document_links` follows them to the feeds).
* **`stream_meta` and `find_streams`, `describe_stream`**: MCP finds streams by `feed`, `stream_type`, `parent_id`
  or `pipeline_uuid` (`limit` up to 200, newest first) and describes one stream's family, attributes and processing.
  The built-in takes `feeds`, `pipeline`, `folder`, `type`, `from`/`to`, `status`, `attributes`, `parts`, `groupBy`
  and `aggregates`, writes a table to the workspace, and needs View Data for attributes from the stream store. The
  two are merged into `stream_meta`: it also takes `parentId` (the streams produced from a stream) or a `type`
  alone, finds newest first, and, given `streamId` alone, describes that stream as `describe_stream` did (its
  children with a count per type, its receipt headers and its processing counts, as JSON).
* **`fetch_events` and `read_stream`, `locate_event`**: MCP reads records by position (`stream_id`, `first_record`,
  `record_count`, `child_type`) and traces an event back to its raw record and steps it. The built-in fetches the
  stored events behind StreamId/EventId rows into one XML file for `search_events`, and requires View Data.
  `read_stream` is merged into `fetch_events`: given `streamId` (with `firstRecord`, `recordCount`, `childType`
  and `maxChars`) it reads that stream's records as JSON, splitting a raw stream held as one record into its
  records. `locate_event` is ported as a tool of its own (`stroom-mcp-impl`).
* **`processor_tasks` and `processing_status`, `summarise_streams`**: MCP's `processing_status` covers one pipeline's
  filters (what each selects, tracker and task counts, whether finished); `summarise_streams kind=errors` triages
  Error-stream markers with `error_rules.yaml`. The built-in covers the whole cluster with modes `failures`, `list`
  and `summary` and filters by pipeline, feed, node, filter, stream, status and time, groups Error-stream messages
  into failure modes (`FailureModes`), and requires Manage Processors. `processing_status` and `summarise_streams`
  are ported as tools of their own (`stroom-mcp-impl`): `processing_status` reads each filter's tasks with the paged
  reader `processor_tasks` uses (`ProcessorTaskFinder`), and `summarise_streams` triages with `ErrorTriage` (the
  bundled `error_rules.yaml`, after any the `stroom.ai.errorTriage.rules` property gives).
* **`xslt_lookups` and `find_reference_data`**: MCP lists every reference map in the environment, with no arguments;
  the built-in starts from given XSLTs or pipelines (`uuids`, `map`, `loaders`) and analyses their lookups.
* **`search_events`, `run_stroomql`, `describe_data_source` and `find_events`**: MCP's `find_events` searches one
  index with structured terms as a dashboard would, and returns StreamId/EventId hits; the built-ins search a file
  of fetched events (`search_events`), run any StroomQL `query` into a workspace table (`run_stroomql`), and
  describe a data source's fields (`describe_data_source`). `find_events` is merged into the three (see
  "find_events, merged" below).
* **`describe_data_source` and `describe_feed`**: MCP describes a feed by name (or one of its fields) from the
  generated documentation and index fields; the built-in describes a data source's fields, conditions,
  documentation and AGENTS docs, and checks View and Use.
* **`propose_code_change` and `summarise_fix`, `step_pipeline`**: the built-in offers the stepping user a button to
  apply a change it has tried by stepping; MCP steps by itself, proves a fix over every record and returns a diff
  with steps for applying it by hand.
* **`propose_documentation_change` and `write_documentation`**: the built-in offers a button to review a change to
  any document's Documentation tab (not a folder's or a Documentation document's) and apply it to the tab, unsaved;
  MCP writes a pipeline's or an index's documentation itself, with its field mapping.
* **`propose_dashboard` and `verify_index`**: the built-in offers a button and the user creates the dashboard; MCP
  creates a verification dashboard in its workspace folder after a confirmation.
* **`elastic_index_templates` and `check_index_template`, `propose_index_template`**: MCP reads no templates from
  Elasticsearch: it asks the user for the index template and its component templates (Dev Tools requests or GET
  output). The built-in reads them from the Elastic Index document's cluster, each by name with its body exactly
  as Elasticsearch returns it, which the model is told to show verbatim.
* **`ask_user`**: MCP has no general question tool. Each tool asks within itself, for a confirmation or approval
  (`confirmation_id`, `approval_id`, `reviewed`) or a choice (e.g. a feed name it couldn't find).

## Rename and alignment candidates

Proposals made before the MCP server's tools were brought into Stroom. Those marked done were made by merging the
MCP tool into the built-in one (its name gone); the others are still proposals.

* `get_instructions`: let both take `folders`, `feeds` and `uuids` (MCP's `docs` could become, or also accept,
  `uuids`).
* MCP's `propose_index_template` could become `draft_index_template` or `build_index_template`, so that `propose_*`
  keeps its built-in meaning: offer the user an action.
* Done: `find_documents` merged, finding by name and type into `folder_contents` (`name`, `types`) and content
  search into `find_in_content`.
* Done: `describe_document` split along the built-ins' lines (`read_document`, `document_info`,
  `document_documentation`) with its analysis as extra parts of `read_document`.
* Done: `find_streams` and `describe_stream` folded into `stream_meta` (with `parentId` and, given `streamId`
  alone, a child-stream view). Still a proposal: `processing_status` into `processor_tasks mode=summary`. Share one error triage between `summarise_streams` and
  `processor_tasks` (`error_rules.yaml` against `ErrorMessageNormaliser` and `FailureModes`).
* One reference data tool with two entry points: every map (`find_reference_data`) or given XSLTs (`xslt_lookups`).
* Align argument names: `pipeline_uuid` against `pipeline`/`pipelines`, `stream_id(s)` against `streams`,
  `time_from`/`time_to`/`created_after` against `from`/`to`/`since`/`until`. The two servers also name tools
  differently: MCP puts the verb first (`find_*`, `describe_*`, `read_*`), most built-ins the noun first
  (`document_info`, `stream_meta`, `processor_tasks`).
* Consent: map MCP's `confirmation_id`/`approval_id`/`reviewed` onto one consent model with the chat's approval
  forms (the roadmap's "one consent model"). Done in part: the ported tools ask only in a form (the chat's, or an
  MCP client's), and take no `confirmation_id` or `approval_id`.

## The validation core (`stroom.ai.impl.tools.validation`)

What `check_xslt` and `check_events` are made of, ported from the MCP server's `utils/saxon.py`, `utils/schemas.py`,
`utils/eventschema.py`, `utils/xpathcheck.py`, `utils/xsltversion.py` and the helpers of `tools/validation.py`,
before the tools themselves. Where it differs:

* **Compiling an XSLT** (`XsltCompileCheck`): with Stroom's own Saxon set-up, as its XSLT pool compiles for an XSLT
  filter: Stroom's XSLT function library registered and imports read by name through Stroom's resolver. The MCP
  server compiled with stubs for Stroom's functions and for whatever an import declares, and said an undeclared
  variable was "fine only if an import declares it"; here an import is read, so one that can't be read is the
  error (Saxon stops there), and there are no notes.
* **Validating Events** (`HeldSchemas`, `EventChecks`): against the XMLSchema documents the user can view, by
  system id, compiled and cached by Stroom's schema pool. lxml's one message for a bad value is two from the JDK's
  validator (the value, then the element); they are given as one error, so counts agree. An error's `path` is
  `/Events/Event[2]/EventTime/TimeCreated`, a position given where the parent holds more than one of that name.
* **Evaluating XPath on samples** (`LockedSaxon`, `XPathChecks`): Saxon in-process, locked down (no URI, unparsed
  text, collection or environment variable; no external entity). Every sample text is matched with Saxon's own
  regex engine, so there is no Python "twin" of a regex to agree with Saxon first, and nothing has to be started on
  the main thread. `XPathChecks` takes xpaths, extractions and record documents rather than the translation
  mapping, which the translation build ports. Where a regex stops matching is read with Java's regular
  expressions, as the MCP server read it with Python's.
* **Wording**: "the server doesn't run or save it" is "the tools don't run or save it", and the deadline warning no
  longer names the server's `sample_check_seconds` setting.
* **Not ported**: `agent_line` and `client_model` (the save's "by" text comes from the caller's session).
  `check_xslt`'s import lookup and `_record_validated` are ported with the tools themselves (below).

## Sample analysis

The MCP server's sample analysis is ported as plain Java in `stroom-ai-impl`, package `stroom.ai.impl.tools.sample`,
for `profile_sample`, `survey_feed` and `set_shape_handling` (and `fetch_events`, already) to use: `SampleProfiler`
(`utils/profile.py`), `RecordSplitter` (`survey.split_records`, replacing the partial splitter that was in
`stroom.ai.impl.tools`), `Shapes` (`survey.Shapes`), `SurveyDoc` (`utils/surveydoc.py`, its document format kept
exactly), `SampleTexts` (`utils/samples.py`), `FeedSampling` (`tools/sampling.py`'s helpers), `TimePatterns`
(`utils/timefmt.py`), and `SplitterSpec` and `SplitterDryRun` (`utils/dsgen.py`'s spec, inference and dry run, not
its generation of the Data Splitter XML). They read a sample as the Python server did (its regexes, `csv` sniffer,
`json` messages, float printing and `ipaddress`); a test runs them over every format sample and evaluation sample
of the MCP server, and the heads of each, against what the server's own code made of them.

Where they differ from the Python server:

* A raw stream's part is read through the stream store (`StreamReader.readHead`), not the data viewer's pages, so
  there is no paging, no UTF-16 counting and no HEX-view fallback: undecodable bytes are replaced as a pipeline
  replaces them, and `StreamReader.charsetReport` gives what was replaced.
* XML is read with the JDK's StAX reader (no DTDs, no external entities) rather than lxml; an element is written
  back as lxml writes it.
* The dry run runs a spec's regexes as Java regexes, as Data Splitter does; the Python server ran them as Python's.
* A directive log's or a quoted-newline CSV's profile no longer carries the internal `_records` list (the Python
  server's dict unpacking left it in).
* A delimited head whose last row was cut inside quotes no longer fails the survey with an index error: the row
  without columns is signed `row:all`.
* `quoted_csv`'s header sniff can still fail on odd text, as it did in Python (`csv.Error`); `ToolRefusal` is
  thrown where the server raised a `ToolError`.

## The sampling tools: `profile_sample`, `survey_feed` and `set_shape_handling`

Ported as tools of their own (`stroom-mcp-impl`, category processing), on the sample analysis above and the build
layer below. `profile_sample` shares start_onboarding's reading of samples (`SampleTexts`, `SampleStreamTexts`)
and its profiling (`SampleProfiling.profileAll`, which gives XML fragments their wrapper:
`SampleProfiling.withFragmentSetup`, the MCP server's `with_fragment_setup`, with `FragmentWrappers` finding the
environment's own XML_FRAGMENT converters and the event-logging version through `HeldSchemas`). `survey_feed` picks
streams with `FeedSampling.pickSpread` (through `StreamReader.find`), reads each part with `StreamReader.readHead`,
and keeps the survey with `FeedSampling.keep` in the build's survey doc (`SurveyDocuments`, through `WriteGuard`).

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `profile_sample` | yes | yes | no | no |
| `survey_feed` | no (yes for a call without `build`) | yes | no | yes |
| `set_shape_handling` | no | no | yes | no |

`survey_feed` asks the user only about reading fewer streams than the default, not before it writes the survey doc,
so over `/mcp` a call with `build` is approved as a whole; `set_shape_handling` confirms its one change itself.

Where they differ from the Python server:

* **`survey_feed` stops**: between streams (and parts) when its thread is interrupted (the run stopped, or the MCP
  client gave up), saying `The survey was stopped: the call was cancelled.`; and at the session's time limit for a
  call it stops reading and returns what it has read, not covered (`NOT COVERED YET: this session's time limit for
  a call (...) stopped the survey after N of the M streams picked; ...`), the survey doc keeping what was read. The
  Python tool had no time limit.
* **Reading a stream** is `StreamReader.readHead` (the stream store), not the data viewer's pages: see the sample
  analysis above. An unreadable stream's entry says `Stroom couldn't read it: Stroom could not read stream N: ...`.
* **Permissions**: `survey_feed` refuses a user without View Data, and so do `profile_sample` and start_onboarding
  for sample streams (`SampleStreamTexts`); the Python server left it to Stroom's data fetch. The survey doc is
  changed only when the tools made it and it is in the build (`WriteGuard.checkInBuild`), and the streams read are
  audited.
* **Arguments out of range** (`max_streams` 1 to 100, `max_chars_per_stream` 1,000 to 5,000,000,
  `max_parts_per_stream` 1 to 20, `max_records_per_stream` 10 to 10,000, `examples_per_shape` 1 to 10) are refused
  as `<name> must be a whole number from <min> to <max>, not '<value>'`, in place of pydantic's message.
* **The agreement to fewer streams** is kept per user and feed in this node's memory (`FewerStreams`), for four
  hours, as the Python server kept it in its own.
* **`profile_sample`'s description** says `nothing is saved in Stroom` where the Python one said `locally (nothing is
  sent to Stroom)`, which is wrong in-process. Its `samples` is declared a list of strings, as start_onboarding's
  is; a map by file name is still read.
* **The fragment wrapper**: a wrapper's `path` is Stroom's explorer path (`/Format Handling`), not the find index's
  (`System / Format Handling`). The version an `<Events>` wrapper is written in is the one passed to
  `withFragmentSetup(profiled, chosenVersion)`, else the newest held: the build's chosen version is kept by the
  translation tools, not ported yet, so profile_sample and start_onboarding pass none.

## The stepping tools: `step_pipeline`, `step_sample`, `step_records` and `compare_outputs`

Ported as tools of their own (`stroom-mcp-impl`, category processing) from the MCP server's `tools/stepping.py`.
What they share is `PipelineStepping` (reading the pipeline, the draft check through `CheckXsltTool.check`, the
reference data check, triage with `AcceptedErrors`, the EventDetail/Unknown check through `WriteGuard` and
`KeptMappings`, `remember_clean` through `BuildRecords.recordPassed`, `step_spread`, and the time limit),
`SteppedPipeline` (the MCP server's `_Pipeline`, on `MergedPipeline`) and `SteppingVerdicts` (`sends_on`,
`_empty_output`, `_no_events`, `_no_reference`, `_block`, `_nothing_stepped`, ...). Each stream is walked in one
stepping session (`SteppingWalks`), through Stroom's stepping service in-process, as the user: the session lives on
this node, so there is nothing for session affinity to do. `compare_outputs` finds an indexed event's record with
`EventLocations.eventLocations` and diffs with `EventXml.fieldValues`.

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `step_pipeline` | yes | yes | no | yes |
| `step_sample` | yes | yes | no | yes |
| `step_records` | yes | yes | no | yes |
| `compare_outputs` | yes | yes | no | yes |

Where they differ from the MCP server:

* **step_sample and step_records are read-only** (`readOnlyHint` true), although a clean verdict is recorded in the
  build record of the pipeline stepped (the MCP server said `readOnlyHint` false for that), for the reasons
  check_events is (above): the record is the tools' own bookkeeping, kept only for pipelines in a build, and as
  writes every step would need approving over `/mcp`, and none could be made while the user is away.
* **Permissions**: each refuses a user without the Stepping permission up front (`You don't have permission to step
  pipelines (Stepping), ...`), where the MCP server left it to Stroom's REST refusal. step_sample's check that a
  stream is one JSON array read as one record reads the stream's head only for a user with View Data, and is
  otherwise skipped. The streams stepped are audited as viewed (`AiAuditLog.streamsViewed`).
* **Time limits**: they step within the session's time limit for a long call (its query time limit), less 10 s for
  the work after stepping, where the MCP server stopped before the caller's access token ran out. step_sample
  stops with `stopped_early` and the hint `Stopped after N records before this session's time limit for a call (X
  s) ran out. Call again: each call has its own time, so there is nothing for the user to do. The verdict is of
  those records only.`, and records nothing, as the MCP server did; step_records does the same (the MCP server's
  had no time limit); compare_outputs compares only the records stepped through both sides by then, with
  `stopped_early` and a hint. A step that is still unfinished at the limit stops too (`SteppingWalks` checks
  `ToolDeadline` while it waits for a step). step_pipeline is refused if its one step outlasts the limit.
* **Cancelling**: each stops at once when its thread is interrupted (the client gave up on the call, or the run was
  stopped), saying `Stepping was stopped: the call was cancelled.`; the walk's stepping session is still ended.
* **Progress**: step_sample says when each stream starts and now and then while stepping it (`CallProgress`), as
  the MCP server did; step_pipeline, step_records and compare_outputs now say what they step too (the MCP
  server's said nothing, except compare_outputs' `step_spread`).
* **Records spread over streams** (`step_spread`, for compare_outputs) are stepped stream by stream, not four
  streams at once: the share of each stream and the order of the steps are the MCP server's.
* **step_sample stops at the record cap**: streams left once `max_records` is reached are not stepped at all (the
  MCP server still made one step of each). Empty `stream_ids` (and empty `locations` for step_records) are refused,
  where the MCP server stepped nothing and said none was found.
* **Arguments** out of range are refused in words (`max_records must be a whole number of at least 1`, compare's
  `from 1 to 500`), in place of pydantic's message; a location without `stream` and `record` is refused saying so.
  `draft_code`, `locations` and `events` may be given as JSON text too. `max_records`' description says `at most
  500` rather than naming the MCP server's `max_sample_records` setting. `record` is declared a string (`4`,
  `'first'` or `'last'`); a number is read as well.
* **Mistyped properties** are found against the types Stroom's element registry declares
  (`ElementRegistryFactory`), as the MCP server read them from `/pipeline/v1/propertyTypes`; a property with no
  value is not checked. A pipeline with neither an XSLT step nor a parser is refused (`... has no XSLT step and no
  parser, so there is no output of it to look at`), where the MCP server failed with an index error.

## summarise_fix

Ported as a tool of its own (`SummariseFixTool`, `stroom-mcp-impl`, category processing) from the MCP server's
`tools/diagnosis.py`, next to `locate_event`. It is made of the stepping tools rather than stepping itself:
`EventLocations.eventLocations` finds the indexed events' records once, `CompareOutputsTool.compareLocated` (the
MCP server's `compare` with `locations`) diffs the outputs, and `StepSampleTool.stepSample` and
`StepRecordsTool.stepRecords` give the verdicts with the fix and with the saved code (`_stepped`). The pipeline's
XSLTs and text converters are `MergedPipeline.translationDocs`; their saved code is read with `DocumentAccess` (View
checked); the code diff is `LineDiff`'s unified diff.

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `summarise_fix` | yes | yes | no | yes |

Where it differs from the MCP server:

* **Read-only**, as the MCP server said: it saves nothing. A clean verdict of step_sample or step_records it runs is
  recorded in the build record of the pipeline stepped, as those tools record it (see above).
* **Time limits**: the whole proof runs within the session's time limit for a long call, as the stepping tools'
  steps do, the tools it calls keeping that sooner limit. If the time runs out part way (the comparison, or a
  verdict, stops early, or a step finding the events' records outlasts it) nothing more is stepped, and a short
  result is given: `issue`, `pipeline`, `element`, `doc`, `ready` false, `stopped_early` true and the hint
  `Stopped while proving the fix before this session's time limit for a call (X s) ran out. ... The fix is not
  proven: give fewer stream_ids or events to prove it on.` The MCP server had no time limit of its own here.
* **Cancelling**: it stops at once when its thread is interrupted, saying `Stepping was stopped: the call was
  cancelled.`, as the stepping tools do.
* **Progress**: it says when it starts each part (finding the events' records, comparing, stepping with the fix,
  stepping with the saved code); the tools it calls say their own progress as they do. The MCP server's said
  nothing of its own.
* **Wording**: `events` (and the refusal for no streams or events) name `search_events with an index`, where the
  MCP server named `find_events`, merged into `search_events` here. A missing `pipeline_uuid` is refused in words.
  An XSLT or text converter the user can't read is refused saying so, where the MCP server failed with the REST
  error.
* **The code diff** has the MCP server's `--- <name> (saved)` / `+++ <name> (fixed)` header and hunks of three
  lines' context, from `LineDiff`, whose lines are split on any line ending: a draft differing from the saved code
  only in its line endings shows no changed lines (the MCP server's `difflib` showed every line changed).

## The translation generator (`stroom.ai.impl.tools.generation`)

The MCP server's translation XSLT generator (`utils/xsltgen.py`) and what it needs, ported as plain Java in
`stroom-ai-impl` before the tools that use it (`build_translation_xslt`, `draft_translation_mapping`, `save_xslt`,
`rebuild_mapping`):

* **The mapping** (`TranslationMapping`, `EventRule`, `FieldMapping`, `Condition`, `Lookup`, `Extraction`,
  `KeyValuePairs`, `DropRule`, `XsltStyle`, `SharedTemplate`, `SharedFunctions`): plain records (no Jackson
  annotations: their JSON is written and read by hand), read with `fromJson`/`parse`, which validate as pydantic did (lax types, defaults, the models' own checks and their
  messages; every error as a `MappingError` with its `loc`, `type`, `msg` and `input`, in a
  `MappingValidationException`), and turn pairs into extract entries, a rule's data list into Data entries, meta into
  the xpath reading it and Data Names into the style's `data_names`. `toJson(DumpMode)` writes them as `model_dump`
  did: `ALL`, `WITHOUT_NONE` (`exclude_none`) or `COMPACT` (`exclude_none` and `exclude_defaults`: the form kept in a
  document's description), keys in the models' field order. A mapping saved by either server reads in the other.
* **The generator** (`XsltGenerator.generate`, with `EventTrees` and `EventNode` for the rules' trees and checks):
  the same XSLT text as the Python generator, to the byte, and the same problems, warnings and events summary
  (`Generated.toMap()` is the Python dict). It builds the XSLT in a small XML tree (`XmlElement`, `XmlComment`, ...)
  that keeps what lxml's serialisation depends on: namespace declarations and where lxml puts them, attribute order,
  text and tails; `XmlSerialiser` writes it as lxml does (pretty-printed or not), and `XmlParser` reads XML as
  `etree.fromstring` does (with `remove_blank_text` and `remove_comments` where the Python code used them).
* **Its helpers**: `XPathText` (the expression writers the server's XSLT reader reuses), `MappingFacts` (action
  homes, kept Unknown rules, rooted paths, variable reads, function calls given as fields), `MappingCompaction`
  (`compact_rules`, `compact_pairs`, `grouped`), `XsltNames` (`style_name`, `unique_name`, `data_name_in`),
  `XsltComments` (the comment wrapper, Python's `textwrap`), `Pairs` (`utils/pairs.py`), `FieldHomes` (draftmap's
  `HOMES` and `_matches`, for the draft's port to use), `StyledXslt` (`utils/xsltstyle.py`, for the indexing and CEF
  generators), `HandEdits` (`utils/handedit.py`: outline, undone, lost, plan fields, collisions) and `CodeDiff`
  (`mappingstore.code_diff`, with Python's `difflib` in `LineDiff`). The write side of `mappingstore` is in
  `KeptMapping` (`withMapping`, `replaceSection`, `docDigest`, `withAgreedTemplate`, `readAgreedTemplate`);
  `normalise_xslt` and `digest` were ported before as `XsltCode` (stroom-mcp-impl).

Parity is tested against the Python server's own code: every mapping its test suite generated (236 of them, with
the evaluation cases' reference mappings) is a golden file under `golden/generation/` with what the Python generator
gave for it, XSLT included; and the helpers, the models' validation (errors included), the hand-edit checks, the
code diff and the description blocks have their Python results in `tools/generation/*.json`. The generator's own
tests run its XSLT with Saxon over the tests' records and validate the events against the event-logging XSDs.

Where it differs from the Python server:

* **Wording**: a refused function in an xpath says "the tools don't run or save it" (`XsltSafety`, ported before)
  where the server said "the server".
* **A regex an extraction gives** is checked with a port of Python's `re` parser (`PythonRegex`), for its group count
  and Python's messages (`bad escape \p at position 0`): an XPath regex Python can't compile is still reported. A
  repetition count too large for Python (`a{99999999999}`) is a problem ("the regex does not compile: the repetition
  number is too large") where the Python generator raised an `OverflowError`.
* **Kept blocks** in a description are written as the server's `json.dumps(ensure_ascii=False, separators=(',',
  ':'))` writes them (`PyJson.dumpsCompact`), for every block `KeptMapping.withBlock` writes: before, Jackson wrote
  them, leaving nulls out and escaping control characters in upper case.
* **Style numbers** (`variable_min_reads` and the others) are Java longs: a number past that is refused as pydantic
  refuses a float past it (`int_parsing_size`).
* **Not ported here**: the tools (`build_translation_xslt`, `draft_translation_mapping`, the save tools), the rest of
  `utils/draftmap.py`, `utils/fielddoc.py` (the documentation's Field mapping tables) and the indexing and CEF plans
  (`utils/fieldplan.py`, `utils/cef.py`, which use `StyledXslt`). The reader, the reference generator and the
  mapping changes are ported since (below).

## The reader, the reference generator and mapping changes (`stroom.ai.impl.tools.generation`)

Four more of the MCP server's generator modules, ported without their tools (`build_reference_xslt`,
`rebuild_mapping`, the save tools and `build_translation_xslt`'s `changes=` come later), on the translation
generator above:

* **`utils/refgen.py`**: `ReferenceMapping`, `ReferenceMap` and `ReferenceValue` (plain records, read and
  validated as pydantic did, written with `toJson(DumpMode)`: the server's JSON) and `ReferenceGenerator.generate`
  (`generate_reference`), writing the same reference-data XSLT, to the byte (`Generated.toMap()` is the Python dict).
* **`utils/xpathtree.py`**: `XPathTreeParser` (the server's `XPathParser`, which parsed with elementpath: a strict
  XPath 3.1 parser) gives the trees elementpath gives, as `XPathNode`s (symbol, value, children, and each node's text
  from the token positions elementpath gives, with its quirks: `a/text()`'s text is `a/text`); `XPathTrees` has the
  rest (`exprSlot`, `stringSlot`, `normal`, `items`, `unbracketed`, `terms`, `strings`, `firstOf`, `isNilCheck`,
  `unify` with `XPathMatch`, `splice`). `XPathFunctions` holds the functions elementpath knows, with their arities.
* **`utils/xsltread.py`**: `XsltReader.read` (an `XsltReading` of `RuleReading`s and `LeafReading`s, `SharedCall`s
  for an imported XSLT's templates), `XsltReader.rebuild` (a `RebuiltMapping`; `added()` is the Python `new`),
  `resolve` and `canonical`; the shapes the reader reads against are `ReadShapes` (`SHAPES`, `LOOKUP_BELOW`,
  `TRANSFORMS`, `FIELDS`), made from `XPathText` as the server made them; `ReadIdioms` and `MappingRebuild` hold the
  idioms and the rebuild.
* **`utils/mappingchanges.py`**: `MappingChanges.applyChanges` and `applyPlanChanges` (an `Applied` merged mapping
  and what each change did; a change that can't apply is a `ToolRefusal` with the server's message).

Parity is tested against the server's own code, run under a scratchpad Python with elementpath, lxml and pydantic:
every expression the server's tests parsed (and more forms a hand edit may write) with elementpath's tree or its
refusal, and per golden XSLT a digest of the trees of every expression in it and its parts (`xpath_cases.json`);
every read and rebuild the server's tests made, and per golden XSLT digests of its reading and of its rebuild with
the mapping lost and kept (`reader_cases.json`); the tests', evaluation cases' and end-to-end suites' reference
mappings, and more, with pydantic's errors or the XSLT (`reference_cases.json`); and every merge of changes the
tests made, and more (`changes_cases.json`). The server's reader tests are ported too, running both XSLTs with Saxon
over the tests' records (`TestXsltReader`, its cases in `rebuild_test_cases.json`).

Where it differs from the Python server:

* **Constant parts of an expression are not evaluated** as it is parsed: elementpath refuses `xs:integer('abc')`,
  `1 div 0` or `'a' + 1` as it parses them (so the reader kept such an expression as written); here they parse.
  Inline functions (`function($x) {...}`) and URI-qualified names (`Q{uri}name`), which elementpath reads, are
  refused here (the expression is kept as written); and the parser doesn't remember functions it was asked about
  before (elementpath's parsers, cached by namespaces, did).
* **An XSLT with no template matching its input** is refused with "The XSLT has no template matching its input:
  there is nothing to read" (an `IllegalArgumentException`), where the Python reader failed with a bare
  `StopIteration`.
* **A rebuilt mapping that doesn't validate** says so with the port's message for the errors
  (`MappingValidationException`'s, which starts as pydantic's does but leaves out its `input_value` and its link).
* **A for-each read back** is compared by its text, which `XmlSerialiser` now writes as lxml does for an element on
  its own: the namespace of its name and of its attributes declared first, then its ancestors' (before, the
  ancestors' came first).

## MCP server tools with no built-in counterpart

What would move in with each. These all need the MCP server's workspace folder and write guard (tags
`mcp-managed`, `mcp-generated`), its consent ids, `error_rules.yaml` and `conventions/*.yaml`, as well as its
guides, resources and prompts.

* **Explore**: `find_pipeline_templates` (templates ranked for a stage, from the folders `access_policy.yaml`
  names), `describe_template` (a template's children and the contract a child meets), `find_elastic_clusters`
  (clusters, the indexes on each, a connection test).
* **Onboarding**: `start_onboarding` and `build_status` (a build's plan and its steps), `start_build` (the build
  folder), `profile_sample` (a sample's format and fields), `create_feed`, `upload_sample` (data sent to the
  datafeed), `record_source_notes` (vendor documentation kept as Documentation), `survey_feed` and
  `set_shape_handling` (the kinds of event a feed holds, and those deliberately dropped).
* **Translation**: `build_data_splitter` and `save_text_converter`, `draft_translation_mapping`,
  `build_translation_xslt` and `rebuild_mapping` (event-logging XSLT made from a kept mapping), `save_xslt`,
  `save_dictionary`, `build_reference_xslt`, `check_xslt` (static checks, refusing unsafe functions).
* **Pipelines**: `create_pipeline`, `copy_pipeline`, `update_pipeline`, `step_pipeline`, `step_sample` and
  `step_records` (stepping by the agent, with errors triaged), `compare_outputs` (field-by-field diff of outputs),
  `check_events` (schema and quality checks).
* **Processing**: `create_processor_filter`, `set_processor_filter_enabled` (disable only) and `reprocess_streams`,
  under approval and within limits, and `wait_for_processing`.
* **Indexing**: `get_field_conventions`, `draft_index_mapping`, `propose_index_template` and
  `check_index_template` (Elasticsearch index templates for an administrator to apply), `create_index_doc`,
  `create_indexing_pipeline` and `verify_index` (`find_events` is merged into `search_events`, above).
* **CEF, diagnosis and builds**: `draft_cef_mapping`, `review_coverage` (records no rule matched across a feed),
  `write_documentation` (a pipeline's or index's Documentation, with its field mapping), `promote_build` (moving a
  build into place, with approval and backups).

## The build layer, for the ported write tools

`start_onboarding` and `build_status` (`stroom-mcp-impl`, category content) are ported with the layer every tool
that builds something stands on (the MCP server's `security/guard.py`, `security/record.py`, `utils/consent.py`,
`utils/progress.py` and `tools/plan.py`). Its classes are in `stroom.mcp.impl.tools`, and injectable:

* **`WriteGuard`**: everything a tool makes lives in the workspace folder (`stroom.ai.builds.workspaceFolder`, by
  default `MCP Workspace`, below System), in a folder per build, tagged `mcp-managed` and `mcp-generated`.
  * Make a document with `create(type, name, build, copyOf, false, role)`, or `createFilled(...)` to make it and
    write its content in one go (deleted again if writing fails). A name the build already has for the type is
    refused.
  * Before changing a document, call `checkManaged(session, ref)` (it must be the tools' own) or
    `checkInBuild(session, ref)` (and in a build folder, which it returns), or `checkBuilt` for one that may have
    been promoted. Each refusal is a `ToolRefusal` with the MCP server's wording, and is audited.
  * `buildOf(ref)`, `copyOf(ref)`, `folderContents(build)`, `roleFolder`, `moveToRole`, `resolveFolder(path)` and
    `removeBuildFolderIfEmpty(build)` are as the MCP server's.
  * Everything is done through Stroom's explorer and document services as the user, so Stroom's own checks (Create,
    Edit, Delete) apply too.
* **`BuildRecords`**: the build's record, the Documentation doc `Build record` in the build folder, in the MCP
  server's format (a record either one wrote reads in the other). `recordPassed(pipeline, kind, draftCode, extra)`
  records a clean step (`STEPPED`), a passed verification (`VERIFIED`) or Events that passed check_events
  (`VALIDATED`) for the pipeline's code; `steppedClean`, `verified` and `validated` say whether its saved code has.
  `update(build, ref, change)` changes a document's entry (`copy_of`, `version_of`, `stepped_streams`, ...).
* **`Consent`**: before a write, `consent.require(call, context, Request.confirmation(action, summary, details))`
  (or `Request.approval(...)`, with `.withEditable(key, title, proposed)` for a value the user may correct). It
  returns once the user has agreed in a form: go on, and read any correction with `consent.edited(context, key,
  proposed)`. A refusal (declined, a form closed unanswered, or a session that can't show a form now: an MCP
  client without elicitation, or a chat the user is away from, see `FormsRequired`) is a `ToolRefusal`, and
  nothing is to be done. In the chat the run stops while the form waits and the same call is made again with the
  answer, so a tool must be safe to re-run up to its question. `consent.choose(...)` asks the user to pick one of
  some options.
* **`BuildPlan`**: end every write tool's result with `plan.withNext(context, build, result)`, or
  `plan.nextFor(context, ref, result)` for a document whose build the call didn't name: it adds `next` (the plan's
  next step and the one call that does it), `done: false` until promotion, and what the user changed in the call's
  forms (`changed_by_user`). `plan.resolveBuild(context, build, tool)` gives the build a call names, or the one its
  session last worked on. `status(build, made)` is build_status's answer.
* **`CallProgress`** (`stroom-ai-impl`): `CallProgress.of(context).say(message)` for each stage of a long call, and
  `nowAndThen(message)` in its loops.

Where the port differs from the MCP server:

* **The guard works in-process**: Stroom's explorer tree is read directly, so the MCP server's waits and second
  looks for a folder or document its REST reads didn't show yet (and its search of the explorer index as a fallback)
  are not needed, and not ported. Only one folder of a name is made at a time on a node, as in the MCP server.
* **No consent ids**: the MCP server answered a session that couldn't show a form with a `needs_confirmation` or
  `needs_approval` round holding a `confirmation_id` or `approval_id`, which the agent passed back once the user
  agreed. Stroom's tools never do: a model holding such an id could approve its own call. Without a form the call is
  refused instead (`FormsRequired`), and over `/mcp` a call that changes something is refused before it runs for a
  client without elicitation. The tools take no `confirmation_id`, `approval_id`, `source_confirmation_id` or
  `template_confirmation_id`. write_documentation for an existing index, made again with the field descriptions
  after a `needs_descriptions` round, asks the user again (the Python tool reissued a confirmation id to skip it).
  The MCP server's renewal of an answer when the user's access token ran out during a form (`needs_retry`) is not
  ported.
* **The remembered build** (`remember_build`) is kept per session (a chat, or an MCP session) in this node's memory,
  rather than per user: a call it can't help is told to name the build.
* **What the build tools share** is injected directly: whether an XSLT is still what its mapping generates
  (`GeneratedDrift`, on the ported generators), the index plans draft_index_mapping keeps for a session
  (`IndexingMemory`), and sample profiling (`SampleProfiling`, bound to the ported profiler,
  `SampleProfiling.Profiled`).
* **Over `/mcp`, a write call is approved** by the endpoint before the tool runs (`McpApprovals`), and a tool's own
  confirmation is then asked as well.

## check_xslt, check_events and wait_for_processing

`check_xslt` and `check_events` (category content) and `wait_for_processing` (category processing) are ported in
`stroom-mcp-impl`, on the validation core and the build layer. The helpers of the MCP server's
`tools/processing_writes.py` that `wait_for_processing` needs are ported for its write tools to reuse:
`StreamProcessing` (`_outputs`, `_tasks_ended`, `_selects`, `_will_process`, `_waiting_on`, `_unselected`, `_iso`),
`PipelineOutputs` (`output_stream_type`, `_writes_output`, `_sends_to_kafka`, `_is_indexing`) and
`UserActionEntries` (`utils/useractions.py`'s `enable_filter`, `open_document` and `actions`; a result gives
`user_actions` as the MCP server's did, and the chat is given them as `ToolUserAction`s to show as buttons).
`wait_for_processing` reads each filter's status with `processing_status` (`ProcessingStatusTool.status`) and its
tasks with `ProcessorTaskFinder`. Where they differ from the MCP server:

* **check_xslt compiles the XSLT** as Stroom compiles it (`XsltCompileCheck`: Stroom's functions and its imports)
  once its other checks pass, where the MCP server's said "Full compilation happens when the pipeline is stepped";
  an undeclared variable or a wrong call is then found before stepping. An import is looked for as an XSLT
  document of exactly its name that the user may view (the MCP server searched the explorer by name, then matched
  the name exactly).
* **check_events is read-only** (`readOnlyHint` true), although Events that pass are recorded as validated in the
  build record of the pipeline that wrote them, as the MCP server's did (it said `readOnlyHint` false for that). The
  record is the tools' own bookkeeping, kept only for pipelines in a build, never for a document a person wrote or
  anything that runs, and promotion, which reads it, asks for its own approval. As a write, every check would need
  approving over `/mcp`, and none could be made while the user is away. It reads up to 500 records of the streams
  in all (the MCP server's `max_sample_records` default), and its `stream_ids` description says so rather than
  naming the setting.
* **wait_for_processing is read-only too**, and takes a run slot. Once indexing passes it restores the build's
  Elasticsearch indexing pipeline's batch size, as the MCP server's did (`IndexingRuns.developmentBatch(..., false)`,
  added with the processing write tools), which made the MCP server's `readOnlyHint` false. Reconsidered then and
  kept read-only, on check_events' grounds: it only takes off the small batch size `create_processor_filter` and
  `reprocess_streams` set, on a pipeline tagged as the tools' own in a build (never a document a person wrote), and
  as a write every wait would need approving over `/mcp` and none could run while the user is away. A failure to
  restore it never fails the wait.
* **wait_for_processing stops before the session's time limit for a long call** (its query time limit, less 10 s
  for its last reads), where the MCP server stopped before the caller's access token ran out: the hint says to call
  again, as the MCP server's `out_of_time` did, naming the limit instead of the sign-in. It stops at once when its
  thread is interrupted (the client gave up on the call) or the chat's run is stopped, and says where processing
  is (`CallProgress`) now and then while it waits. `timeout_seconds` outside 5 to 900 is brought within them, where
  the MCP server refused it; empty `stream_ids` are refused.

## The processing write tools: `create_processor_filter`, `set_processor_filter_enabled` and `reprocess_streams`

Ported as tools of their own (`stroom-mcp-impl`, category processing), on the build layer and the helpers above
(`StreamProcessing`, `PipelineOutputs`, `UserActionEntries`, `IndexTemplateAgreements`, `BuildFeeds.makeTestFeed`,
`BuildRecords.steppedClean`, `SampleStreamTexts.refuseOlderThanFeed`). The rest of `tools/processing_writes.py` is
ported as helpers, named for the builds' port (`start_build`, `promote_build`) to reuse:

* **`ProcessorFilterWrites`**: `_term` (`term`, `anyOf`, `allOf`), `_managed_pipeline` (`managedPipeline`),
  `_create_filter` (`createFilter`), `_selected_ids`, `_already_processed`, `_feed_term`, `_pipeline_term`,
  `feed_stream_type`, `_pipeline_terms` (and `pipelineUuids`), `_names_pipeline`, `_feed_bounds`
  (`feedBounds`, with `feed_created_ms` as `feedCreatedMs`), `_refuse_duplicate`, and the settings
  `max_reprocess_streams`, `reprocess_max_tasks`, `sample_max_tasks` and `max_feed_filter_tasks` as constants (10,
  1, 1 and 2, their defaults: not Stroom config, so expected.yaml is unchanged).
* **`ProcessingSources`**: `_events_source`, `_refuse_superseded` and `_build_feeds_only`.
* **`OutputFeeds`**: `_feed_taken`, `_free_feed_name`, `output_feed` and `restore_output_feed` (`TEST_FEED` is
  `-MCP-TEST`).
* **`IndexingRuns`**: `lucene_index`, `_own_index`, `_writes_refused`, `drop_request`, `_fresh_index`,
  `_delete_index_shards` and `development_batch` (`DEV_BATCH_SIZE`, `BATCH_NOTE`).
* **`ProcessingApprovals`**: `_reprocess_runs`, `_committed`, `_for_the_user`, `_before_actions`, `_drop_first` and
  `_output_note`.
* **`PromotionProcessing`**: `promotion_processing`, `_test_feed`, `create_promotion_filters`, `_covering_filter` and
  `_before_promoted_indexing`.
* **`PipelineDocs`**: a pipeline read and saved as the user, and `tools/pipeline_writes.py`'s `_set_property`
  (`withProperty`, with `addedProperties` and `withAddedProperties`).
* **`UserActionEntries`** gains `es_request` (`esRequest`, the same request shapes only) and `enable_filter`'s
  `after`.

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `create_processor_filter` | no | no | yes | no |
| `set_processor_filter_enabled` | no | no | no | no |
| `reprocess_streams` | no | no | yes | no |

`create_processor_filter` and `reprocess_streams` ask the user in their own forms, as the MCP server's did (the
approval of a filter that runs, the confirmation of a source pipeline the tools didn't build, of an index template
of the user's own); a filter on any other pipeline is made disabled, its enabling the user's.
`set_processor_filter_enabled` asks nothing (it only ever disables), so over `/mcp` each call is approved as a whole.

Where they differ from the Python server:

* **Filters are made through Stroom's `ProcessorFilterService`** as the user, as the Processors tab and the data
  browser's Process action make them (Manage Processors, and View on the pipeline); a new processor left disabled is
  enabled through `ProcessorService`, as the MCP server's `PUT /processor/v1/{id}/enabled` did. Reprocessing makes a
  new filter on the pipeline selecting the streams by id, as the MCP server's did, rather than Stroom's own
  reprocess filters (the data browser's Reprocess action, `ProcessorFilterService.reprocess`): those select the
  output streams a processor made, and an indexing pipeline makes none (only an Error stream when something went
  wrong), and they give no filter id for `wait_for_processing` to count the new outputs by.
* **A Lucene index's shards are deleted through Stroom's index service** (`IndexResource`, as the index's Shards tab
  deletes them, node by node: the user needs the Manage Index Shards permission). `stroom-index-impl`'s `IndexModule`
  now binds `IndexResource` to its implementation for it (as `TaskModule` binds `TaskResource`). Elasticsearch is
  never written to: dropping an index is a request for the cluster admin (`drop_first`, a button to copy).
* **`_writes_refused` reads an Error stream's text**, line by line, for Elasticsearch's refusal, where the MCP server
  read its stored error markers' messages; the same reasons are found.
* **`set_processor_filter_enabled`** refuses a filter the user can't see (no Manage Processors, or none of that id)
  saying so, where the MCP server gave the REST error.
* **Descriptions**: `reprocess_streams` says `At most 10 streams per call` where the Python one named its setting
  (`max_reprocess_streams (default 10)`); `priority` is described (`The filter's priority (1 to 100; default 10).`),
  and one outside 1 to 100 is refused, as pydantic refused it. `created_after` without an offset is taken as UTC
  (Python's naive time was the server's local time).
* **Consent actions** are the MCP server's (`index_events_from_other_pipeline` for the source confirmation, the tool's
  own name for the template confirmation), as the audit log records them; a refusal names the tool called.

## The feed tools: `create_feed`, `upload_sample` and `record_source_notes`

Ported as tools of their own (`stroom-mcp-impl`): `create_feed` and `record_source_notes` (category content) and
`upload_sample` (category processing, as it sends data into a feed), on the build layer above, with what they share:
`BuildFeeds` (`tools/feeds.py`'s `_existing_feed`, `_refuse_case_twin`, `_build_feed`, `_sample_files` and
`_works_from_message`, and `tools/processing_writes.py`'s `make_test_feed`, as `BuildFeeds.makeTestFeed`, for the
processing write tools to reuse), `FeedNames.exact` (the MCP server's `/feed/v1/getDocRefForName`), `FeedUploads`
(`utils/uploads.py`'s `send_to_feed` and `commands`), `SampleUploads` (in place of `UploadTickets`) and
`SourceNotes` (`utils/sourcenotes.py`'s `REFERENCE_INFIX`, `block`, `read_notes`, `outcome_map`, `detail_problem`,
`catalogue_problems` and `apply_to_draft`; now in `stroom-ai-impl`, `stroom.ai.impl.tools.draft`, beside the draft
mapping that calls `SourceNotes.applyToDraft`).
`McpUploadServlet` serves `POST /mcp/upload` (`utils/uploads.py`'s `handle_upload`).

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `create_feed` | no | no | yes | no |
| `upload_sample` | no | no | no | no |
| `record_source_notes` | no | no | no | no |

`create_feed` asks the user to confirm (or correct) the feed's name in a form, as the MCP server's did;
`upload_sample` and `record_source_notes` ask nothing themselves, so over `/mcp` each call is approved as a whole.

Where they differ from the Python server:

* **Sending text** (`upload_sample sample=`) is in-process: the data passes Stroom's receipt checks for the feed as
  data posted to the datafeed does (`AttributeMapFilterFactory`: the feed's status or the data receipt policy, as
  `stroom.receive.receiptCheckMode` says), then is stored as Stroom's own Upload on a feed's Data tab stores it
  (`DataService.upload`), as the user: they need Import Data and Edit on the feed (the tool checks Import Data
  first). A policy that drops the data is refused (`Stroom's receipt policy drops data for feed ...: nothing was
  stored`), as is one that rejects it. The receipt id is read from the stream's receipt headers after it is stored
  (Stroom's Upload gives none back); the stream is looked for once, not polled. The description says `as the real
  source's data is received (Stroom's receipt checks, then stored as the user, as Stroom's Upload stores it)` where
  the Python one said `through Stroom's datafeed receiver`.
* **Files** (`upload_sample files=`) are posted by the commands to `/mcp/upload/<id>?file=<name>`, signed in as any
  request to Stroom is (the user's API key or token, in the commands as `Authorization: Bearer $STROOM_API_KEY`, or
  `$env:STROOM_API_KEY` in PowerShell, which the user sets in their terminal), in place of the MCP server's signed
  upload tickets. What a ticket bound is held in the database (`SampleUploads`, `AiSampleUploadDao`, tables
  `ai_sample_upload` and `ai_sample_upload_file`): the user the commands were given to, the feed, its stream type and
  receipt headers, the build, each file once, for `stroom.ai.sampleUploads.commandsLast` (15 minutes, as
  `upload_ticket_minutes`); a file larger than `stroom.ai.sampleUploads.maxUploadMb` (100, as `max_upload_mb`) is
  refused with 413. So any node takes the post, not only the one that gave the commands (as a signed ticket could be
  checked anywhere), and a restart forgets nothing. A post first claims its file (a conditional update, so of two
  posts of one file, on one node or two, only one takes it), then marks it stored or releases it; a claim left by a
  node that stopped part way through a post may be taken again after 10 minutes (`SampleUploads.CLAIM_GOES_STALE`).
  `maxPendingPerUser` is counted in the database, across nodes. Sets that have run out, and those of deleted users,
  are deleted by the _AI Chat Retention_ job. A post that matches no waiting file (or one being posted already) is
  refused with 403 (the ticket's 401). As at `/mcp`, the servlet needs Use MCP Tools and is off with the endpoint;
  `upload_sample files=` says so up front, and tells the agent to have the user upload in Stroom instead. A file
  whose storing failed can be posted again.
  The `files` description and the hint describe the commands so (the user's key, never in the chat or the command),
  not a ticket. The upload is logged (who, feed, file, stream), where the MCP server audited it.
* **Results' JSON** leaves nulls out, as every ported tool's does: `upload_sample`'s reply for a stream not visible
  yet has no `stream_id`.
* **Paths** in refusals (`A feed named '...' (path) already exists`) are Stroom's explorer paths (`/Feeds/ACME`), not
  the find index's (`System / Feeds / ACME`). Feeds are found among those the user can view.
* **record_source_notes**: the event catalogue is checked against the event-logging schema of the version chosen for
  the build (`EventVersions.chosenOrLatest`, as the translation tools read it), else the newest held. Its notes are validated as the Python models
  were (a field needs `field` and `meaning`, an event `event` and `description`, a document `title` and its text or
  uuid), with messages such as `fields[0].meaning is required` in place of pydantic's; numbers given for strings are
  taken as text. The block's JSON escapes non-ASCII characters (`json.dumps` with `ensure_ascii`), where the MCP
  server's kept them; either reads the other's. An existing notes or reference doc is changed only when it is the
  tools' own and in the build (`WriteGuard.checkInBuild`).
* **apply_to_draft**: `not_in_catalogue` lists the catalogued fields in the catalogue's order (Python iterated a
  set).

## find_events, merged

The MCP server's `find_events` (`tools/indexing.py`, with its helpers `_search`, `_count`, `_column`, `_searchable`,
`_index_fields` and `_affected`) is merged into the built-in search tools (category search; there is no
`find_events` tool):

* **`search_events` given `index`** does what `find_events` did (`IndexEvents`): the index's fields are listed,
  a field it doesn't have is refused with the ones most like it ("Not fields of index ..."), a search whose answer
  would mislead is refused (`MisleadingSearches`, `_searchable`), IS_NULL and IS_NOT_NULL are searched on
  Elasticsearch as NOT_EQUALS `*` and EQUALS `*`, and the hits (newest first), the count and the Events streams
  holding them are found by three searches as an unsaved dashboard makes them (`IndexSearches`: Stroom's
  `DashboardService`, as the user, history not stored, each search stopped once read). `allStreams` adds
  `affected` (the raw streams to reprocess, and for Elasticsearch the `_delete_by_query` request). The result is
  `find_events`' JSON, with the same field names and nesting (`index`, `searched`, `total`, `hits`, `by_stream`,
  `affected`, `errors`, `next`), and the same refusals and hints.
* **`run_stroomql`** refuses a query on a Lucene or Elastic index that `find_events` would have refused (from the
  query's parsed expression: e.g. `is null` on Elasticsearch, a wildcard on an ip field, IN with a space in a
  value on Lucene, a field that isn't indexed), with the same wording and a note of StroomQL's `=` and `!=`.
* **`describe_data_source`** says, for a Lucene or Elastic index, which searches through Stroom would mislead
  and what to search instead (the knowledge `_searchable` refuses with), naming an Elastic index's ip fields.

Where it differs from the MCP server:

* The arguments are camelCase, as the built-in tools' are: `index` (the Elastic Index or Lucene Index doc's UUID,
  or its name), `where`, `timeFrom`, `timeTo`, `fields`, `limit` (1 to 20, default 10) and `allStreams`. `where` is
  a list of `{field, condition, value}`, one such object, or the list as JSON text; a condition Stroom's
  dashboards don't take is refused.
* The index is one of the data sources the user can Use (`DataSourceAccess`; one they can't is treated as one that
  doesn't exist), where the MCP server read the doc through the REST API (View). An Elastic Index doc's index name
  (for the label and the delete request) is read with View; without it, neither is given.
* The time field is the data source's own (`DataSourceProvider.getTimeField`), as the doc gives it.
* Each search is polled for up to 60 s as before, or for what the call's time limit (the query time limit) has left
  less 2 s, whichever is sooner (`ToolDeadline.remaining()`): the MCP server bounded it by the access token's time
  instead. What it has found by then is the answer.
* Stroom's search errors are its `errorMessages` (the MCP server read the deprecated `errors`, which Stroom leaves
  empty). A search Stroom gives no response to has the error "Stroom's dashboard search gave no response".
* `_searchable`'s `listed` check (a field Stroom doesn't list for an Elasticsearch index, with ECS mapping types)
  and `lucene_text` (CONTAINS on a keyword field, a range on a text field) are `verify_index`'s, and `find_events`
  passed neither, so they aren't used here; `MisleadingSearches` ports the `lucene_text` part, not `listed`
  (it needs the ECS component types), for `verify_index` when it is ported.
* `search_events` now declares Use (document) for the index; its file search is unchanged.

## The draft mapping core (`stroom.ai.impl.tools.draft`)

The MCP server's draft mapping is ported as plain Java in `stroom-ai-impl`, package `stroom.ai.impl.tools.draft`, for
`draft_translation_mapping` and `build_translation_xslt` to use when they are ported (no tool is offered yet):

* `DraftMapping` (`utils/draftmap.py`): `draft(samples, sourceName, systemName, environment, sourceNotes,
  detailCheck, splitter)` is `draft_mapping`, its result the same dict (`mapping`, `source_notes`, `splitter`,
  `format`, `records`, `kinds`, `notes`, `unmapped_fields`, `fields_seen`), the mapping as JSON maps and lists for
  `TranslationMapping.parse`; `records` is `_records`, `pairExtracts` and `withPairs` are `pair_extracts` and
  `with_pairs`. The field homes (`HOMES`, `_matches`, `_keys`) are the generator's `FieldHomes`.
* `LocalCheck` (`utils/localcheck.py`): `sampleRecords`, `checkMapping`, `checkSchemaValues`, `unknownCoverage`,
  `sharedTypeIds`, `ruleOf`, `extractedValues`, `traits`, `valueOf` (`_value_of`), `itemsOf`, `xmlValue`, `xmlFlat`,
  `withJsonFields` and `withXmlFields`. A record is a map, or an `XmlTree.Element` for XML input.
* `ActionRules` (`utils/actions.py`): `actionRules`, `described`, `read`, `severityFields` and `networkFields`.
* `SplitterXml` (`utils/dsgen.py`'s XML generation): `generateSplitter`, `recordRegex`, `recordEndRegex`, `DS_HEAD`,
  `DS_TAIL` and `EXAMPLES`. `SplitterSpec.fromMap` reads a spec's JSON (pydantic's `model_validate`).
* `SourceNotes`, moved here from `stroom-mcp-impl` (its callers there import it from here).

To be reused from other packages, `XmlTree` (with `Element.localName`, `namespace`, `parent`, `attrib`, `get` and
`itertext`), `PyText` (with `isUpper`), `SampleProfiler.cutDocument` and `SampleProfiler.xmlFragments` are now public.

A test (`TestDraftParity`) holds the port to the Python server's own results: `draft_mapping` over every evaluation
case's samples (the excerpt the user shows, with the case's own splitter where it has one, and the first 150 lines
of its files), `dev/format_samples.py`'s samples and the Python tests' samples; `sample_records`, `check_mapping` and
`check_schema_values` over each case's reference mapping and each draft; and `generate_splitter` for every spec
those infer or give. The fixtures were made by running the server's code over the same samples.

Where they differ from the Python server:

* A condition's `matches` and an extraction's regex are run with Java's regex engine (as Stroom's XSLT runs them
  in its own regex dialect); the Python server ran them with Python's `re`. A regex that doesn't compile is one that
  can't be told here, as before.
* A JSON record's value that isn't text (a number in a field the action rules read) is read as its text; the Python
  server failed with an `AttributeError` (seen in eval case 49's sample check).
* The values the source notes' catalogue doesn't list (`not_in_catalogue`) are in the catalogue's field order; the
  Python server listed them in the order of a set, which differed from run to run.
* `DraftMapping.records` refuses no samples with an `IllegalArgumentException`; the Python server failed with a
  `StopIteration`.

## The field-mapping documentation and the CEF mapping (`fielddoc`, `cef`)

Ported from the MCP server's `utils/fielddoc.py` and `utils/cef.py` (stroom-ai-impl), for `write_documentation`,
`draft_cef_mapping` and the coverage tools (all ported since, and using them). They build on the
translation generator (`stroom.ai.impl.tools.generation`: its mapping records, `XsltComments`, `StyledXslt`,
`MappingFacts`, the XML tree) and `KeptMapping.readable` / `conditionText`, which already were `fielddoc`'s.

* `stroom.ai.impl.tools.fielddoc`: `FieldDocs` (the layouts `BY_EVENT_TYPE` / `COMPACT`, `sourceText`,
  `conditionText`, `anyActionPath`, `similarGroups`, `combinedPath`, `dataKey`, `readDescriptions`,
  `descriptionsBlock`, `schemaDescription`, `fieldDescription`, `actionOf`), `Descriptions` (the agent's field
  descriptions, and those to ask for: `wanted`), `EventFieldDocs` (`field_mapping_markdown` with `eventValues`,
  `ruleOf`, `sampledEvents`), `IndexFieldDocs` (`index_documents`, `object_arrays`, `written_fields_markdown`,
  `index_field_mapping_markdown`, `index_tables`, `grouped_fields`, `existing_index_summary`,
  `existing_index_markdown`, `not_listed`, `discovery_field_markdown`, `shown_type`, `ecs_says`),
  `DocumentationText` (`titled`, `section_levels` and `_below_section` of `tools/builds.py`), on the shared
  Python behaviours (`PyText`: `str.strip`, `splitlines`, `{:g}`, `{:.0f}`; `JsonValues.truthy`; `PyJson.dumps`
  with `ensureAscii` off for `json.dumps(indent=1, ensure_ascii=False)`).
* `stroom.ai.impl.tools.cef`: `CefKeys` (ArcSight's dictionary, slots, header, `NOT_ACTION`, `HOMES`), the plan's
  records `CefPlan`, `CefValue`, `CefField`, `CefOverride` (JSON as the pydantic models: `fromJson`, with pydantic's
  errors as `MappingValidationException`, and `toJson(DumpMode)`: `ALL` is `model_dump()`, `COMPACT` the
  `model_dump(exclude_defaults=True)` a plan is kept in), `CefEvents` (`event_kind`, `event_values`, `events_of`,
  the types' checks), `CefDraft` (`draft`, `extend`, `label_for`), `CefXslt` (`render_xslt`, `FUNCTIONS`),
  `CefLines` (`lines_in`, `parse`, `review`, `examples_from`), `CefDocs` (`plan_markdown`, `description_key`) and
  `CefInstructions` (`from_instructions`).

Their outputs are checked against the Python's (golden files written by `utils/cef.py` and `utils/fielddoc.py`:
the drafted plans' JSON and notes, every XSLT's text, the reviews, every documentation table). The index tests are
in stroom-mcp-impl, beside the bundled ECS conventions the descriptions fall back on.

Where they differ from the MCP server:

* What they use of `utils/fieldplan.py`, `utils/templatecheck.py` and `utils/ecs.py` is the indexing core's
  (`FieldPlans`, `TemplateCheck`, `FieldPlan`, `PlannedField`, `Discovery`, `EcsSchema`). ECS's `short` for a field
  is read from the ECS reference bundled in stroom-mcp-impl's resources, from the class path, and is "" without it.
* `CefPlan.unsafe()` says why an expression is refused in `XsltSafety.refused`'s words ("the tools don't run or save
  it"), not the server's ("the server doesn't ...").
* A time with no offset is read in the server's time zone when a review matches it to milliseconds, as Python's
  `datetime.timestamp()` does; `datetime.fromisoformat` is approximated (the extended, basic and week dates, any
  separator, a fraction of up to six digits used, an offset).
* `parse` scans a CEF extension rather than matching the server's regular expression (the same pairs: a value runs
  to the next ` key=`), so a long value can't overflow Java's regex stack.
* A field mapping with neither a field, a lookup nor an xpath (a `meta` one) is documented as an empty xpath; the
  Python raised on it.

## The indexing core (`stroom.ai.impl.tools.indexing`)

What the indexing tools (`get_field_conventions`, `draft_index_mapping`, `propose_index_template`,
`check_index_template`, `create_index_doc`, `create_indexing_pipeline`, `rename_index`, `verify_index`) will be
made of, ported before the tools: `utils/ecs.py` (`EcsSchema`), `utils/fieldplan.py` (`FieldPlan`, `PlannedField`,
`Discovery`, `FieldPlans`; the XSLT in `IndexingXslt`, `ElasticDocument` and `DiscoveryXslt`, the template in
`PlanTemplates`), `utils/templatecheck.py` (`IndexTemplates`, `TemplateMappings`, `ExampleNames`,
`ExampleTemplates`, `TemplateCheck`) and the pure helpers of `tools/indexing.py` (`IndexingHelpers`). A plan's JSON
(`FieldPlan.toJson()`, which Jackson writes too) is the server's `model_dump`, and `FieldPlan.fromJson` (which Jackson
reads with) validates as pydantic does, with its messages, through the generator's `ModelReader`. Tests check the
port against the server's own code: 103 indexing XSLTs to the byte (`golden/indexing`), and some 2,900 calls recorded while the server's test suite ran
(`tools/indexing/recorded_calls.json`).

Where it differs from the Python server:

* The ECS files are bundled with the agent knowledge in `stroom-mcp-impl`; `AgentKnowledge.conventionsFile` (a
  default method, implemented by `BundledKnowledge`) gives them to `stroom-ai-impl`, and `EcsSchema.from(knowledge)`
  reads them once. The functions that need ECS take the `EcsSchema` as an argument rather than reading a module
  global.
* A text that can't be read is an `IllegalArgumentException` (the server's `ValueError`) with the same message;
  `components_wanted`'s `ToolError` is a `ToolRefusal`.
* A document value as read from an indexing XSLT's output is a `TemplateCheck.DocValue(kind, text)` (the server's
  `(kind, text)` tuple).
* What reads Stroom or the session (`with_cardinality`'s schema, `plan_given`'s kept and drafted plans,
  `other_index`'s build docs, `fields_not_in_index`'s looks at the index, `_replaced`'s documents) is left to the
  tools: the helpers take what the tools read. `writes_to`'s Elasticsearch part is `IndexingHelpers.writesToIndex`,
  which `IndexDocs.writesTo` now uses; `Globs` moved to `stroom.ai.impl.tools` for both modules.

## The pipeline write tools: `create_pipeline`, `copy_pipeline` and `update_pipeline`

Ported as tools of their own (`stroom-mcp-impl`, category content), on the build layer (`WriteGuard`, `BuildRecords`,
`Consent`, `BuildPlan`), `PipelineShapes`, `PipelineTemplates.templateReason`, `ReferenceData.resolveLoader` and
`PipelineDocs.withProperty`. Pipelines are read through Stroom's `PipelineService` (their layers, as the pipeline
editor reads them) and created, filled and saved as the user through the explorer and the pipeline store, as the
editor does. Templates are inherited (`parentPipeline`), never copied: `copy_pipeline` refuses one. The rest of
`tools/pipeline_writes.py` is ported as helpers, named for the indexing tools (`create_indexing_pipeline`,
`rename_index`) and the builds' port to reuse:

* **`PipelineWrites`**: `element_id` (`elementId`), `swap_parser` (`swapParser`, giving a `Swap`), `merge_layers`'
  view of a template as a child is written over it (`Layout`, with `chain()`: `Source` included where a layer adds
  it), `open_slots` (`openSlots`, which `StroomBuildFacts.openSlots` now uses), `fill_open_slots`
  (`fillOpenSlots`), `_own_documents` (`ownDocuments`), `_parser_reads_sample` (`parserReadsSample`, which for a
  build on an existing feed also checks the format its survey found, and for XML fragments under an XMLParser says
  to call again with `replace_parser='XMLFragmentParser'`),
  `_json_array_parser` (`jsonArrayParser`), `_sample_is_array` (`sampleIsArray`), `typed_properties`
  (`typedProperties`, giving `Typed`), `_value` (`value`), `_template_uuid` (`templateUuid`), `_doc_ref_by_name`
  (`docRefByName`), `reference_entries` (`referenceEntries`), `_keep_validation` (`keepValidation`),
  `set_pipeline_property` (`setPipelineProperty`) and `set_pipeline_references` (`setPipelineReferences`);
  `PipelineReference` is `PipelineWrites.ReferenceRequest`.
* **`PropertyValue`**: the `PropertyValue` model, with its reading of `translationFilter.xslt=<uuid>` and the other
  shapes agents send (`of`, `listOf`), and `PROPERTY_EXAMPLE` (`EXAMPLE`).
* **`PipelineArguments`**: the pipeline tools' text mappings (`textMap`); their lists and flags are read by
  `ToolArgs` (`utils/params.py`'s `ONE_OR_MORE` is `ToolArgs.oneOrMore`).
* **`PipelinePropertyTypes`**: the MCP server's `property_types` (`/pipeline/v1/propertyTypes`), read from Stroom's
  element registry.
* **`BuildIndexes`**: `tools/indexing.py`'s `_build_index_docs` (`indexDocs`), `_build_destinations`
  (`destinations`) and `other_index`'s reads (`otherIndex`, on `IndexingHelpers.otherIndex`).
* `_standalone_kafka` is `create_pipeline standalone='kafka'` (`CreatePipelineTool.KAFKA_CHAIN`).
* A production pipeline's next version, its translation written anew (0.18.41): `create_pipeline version_of=`
  `keep_references=`, with `_plain_settings`, `_carried_references` and `_not_carried` in `PipelineWrites`
  (`plainSettings`, `carriedReferences`, `notCarried`), `fill_open_slots` and `_own_documents` taking the previous
  version, and `_record_version` (`CreatePipelineTool.recordVersion`). `tools/plan.py`'s `current_translation`,
  `lookups_of`, `lookups_kept`, `lookups_note`, `own_references` and `reference_text` are `CurrentTranslations`,
  which `draft_translation_mapping` (`current_translation` and its note) and `build_translation_xslt` (the warning
  for a mapping that keeps none of the lookups) use; the plan's `pipeline:version` step is `BuildPlan`'s.

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `create_pipeline` | no | no | yes | no |
| `copy_pipeline` | no | no | yes | no |
| `update_pipeline` | no | no | no | no |

`create_pipeline` and `copy_pipeline` ask the user to confirm (the name editable in `create_pipeline`'s form), as the
MCP server's did; `update_pipeline` asks nothing (it changes only the tools' own pipelines in a build), so over `/mcp`
each call is approved as a whole.

Where they differ from the Python server:

* **A new pipeline is made and filled in one go** (`WriteGuard.createFilled`): if writing its content fails, the empty
  pipeline is deleted again rather than left in the build.
* **A document property's name** is read from the explorer (the document's node, as the user may view it), for any
  type; the MCP server read the document through its REST resource and fell back to the explorer for a type it
  didn't read (a KafkaConfig).
* **A pipeline found by name** (`template`, `loader_pipeline`) is one the user may view of exactly that name, found
  through Stroom's document finder; the MCP server searched the explorer. A feed is found by `FeedNames.exact`.
* **`references` given as a feed's name** is read as `{feed: <name>}`; pydantic refused it. A reference without a
  feed is refused as `references[0].feed is required: ...`, and a property that isn't one as
  `set_properties[0]: a property is {element, name} ...` (pydantic's `Value error, ...` wording otherwise kept).
* **A Python literal** given for a list (`"['Feed']"`) or `rename` (`"{'V1': 'V2'}"`) is read when it has no double
  quotes in it (single quotes for double, `True`/`False`/`None`), where Python used `ast.literal_eval`.
* **A whole number too large for an `int` property** is refused (`... is a whole number, not ...`); Python wrote it
  and Stroom refused it later.
* **Results' JSON** leaves nulls out, as every ported tool's does: `update_pipeline`'s `reference_data` and
  `create_pipeline standalone`'s `template` are absent rather than null.
* Not used by `tools/pipeline_writes.py`, so not ported here: `generation._required_for`, `sharedxslt.imports_of`,
  `translation.with_fragment_setup`, and `tools/indexing.py`'s `drafted_plans`, `plan_given` and `with_cardinality`
  (`IndexingHelpers` has the last three's pure parts for the indexing tools).

## The CEF and coverage tools: `draft_cef_mapping` and `review_coverage`

Ported as tools of their own (`stroom-mcp-impl`, category content) from the MCP server's `tools/cef.py` and
`tools/coverage.py`, on the CEF core (`stroom.ai.impl.tools.cef`), the stepping helpers (`PipelineStepping`,
`SteppingWalks`), `KeptMappings`, `EventStreams`, `ProcessorFilters.followOnPipelines`, `PipelineShapes`,
`IndexTemplateAgreements.elasticDestination`, the indexing core's `FieldPlan` and `FieldPlans.sourceMatches`, and the
draft core's `LocalCheck.sampleRecords`. What they read of Stroom is in helpers of their own: `CefSources`
(`_templates`, and the KafkaConfig documents) and `CoverageReads` (`_error_batch`, `_logged`, `parse_logged`,
`_example`, `_shown`, `_sampled_unknown`, `_old_events`, `_newest_raw`). `CefSample` (stroom-ai-impl) runs a plan's
XSLT over sample Events with the locked-down Saxon processor, `StreamSummaries.errorMarkers` reads an Error stream's
markers, `PipelineIndex.named` and `withContent` find documents by name and by content, and `XmlTree.toText` is now
public.

`XsltSaves` is the save path both CEF saves and the save tools (`save_xslt`, `build_translation_xslt`) take: the MCP
server's `tools/translation.py` `create_xslt` (`create`) and `update_xslt` (`update`), with `_checked`,
`xslt_role`, `_refuse_without_required_imports`, `_required_for` (`requiredFor`), `lost_mapping` (`lostMapping`),
`_described`, `_pending`, `_previewed`, `_tag_kept` and `hand_edit_gate` (with `_remembered`, `_kept_code` and
`_CARRY`). It is public, for the save tools' port to reuse.

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `draft_cef_mapping` | no | yes | no | yes |
| `review_coverage` | yes | yes | no | yes |

`draft_cef_mapping` is read-only for a call that saves nothing (no build and name, no uuid). It asks the user whether
keys outside ArcSight's dictionary may be sent (`consent.choose`, unless the standing instructions say or
`custom_keys` is given), and about a field a hand edit changed that its save would change too, but not before the
save itself, as the MCP server's `create_xslt` and `update_xslt` didn't: so over `/mcp` the call is approved as a
whole.

Where they differ from the MCP server:

* **Wording**: "the server" is "the tools" where it names what saved an XSLT (`pipeline_uuid`'s, `uuid`'s and
  `discard_hand_edit`'s descriptions, the hand-edit refusal and guidance, and the lost-mapping text, which still
  says the mapping was saved "by stroom-mcp", as the descriptions name it). `follow_on`'s description says
  `(default true)` and `max_streams`' says `(1 to 5000; default 500)`, where the Python's pydantic schema said so.
* **Arguments**: `overrides`, `header`, `style` and `hand_edit_choices` may be given as JSON text; one override may
  be given as an object. An `output` other than kafka or text, `max_streams` outside 1 to 5000 and a `continue_from`
  that isn't a number are refused in words, in place of pydantic's message.
* **Finding where a CEF pipeline comes from** (`CefSources`): pipelines are found by name with Stroom's document
  finder (`*CEF*`, `*ArcSight*`, `*Arcsight*`, as the user may view them), and a template the standing instructions
  name by its exact name, else `*<name>*` (the MCP server used the explorer's quick filter). XSLTs that write
  `CEF:0` are found with Stroom's content index; while it isn't ready, none are listed. Parents are read from each
  pipeline document.
* **The sample check** runs the plan's XSLT with Stroom's locked-down Saxon processor (no resource is read, so a plan
  with imports gives `sample_check_error`), where the MCP server used saxonche.
* **A review steps** the pipeline as the user, who needs the Stepping permission, within the session's time limit
  for a long call: stopped early, the result says `stopped_early` and reviews the lines stepped by then.
* **The save path** (`XsltSaves`): the XSLT is made with `WriteGuard.createFilled`, so one whose filling fails is
  deleted again rather than left empty. The version history's "by" is `Stroom AI tools, <client>` (the session's
  caller), where the MCP server wrote `stroom-mcp <version>, <client> <version>, model <model>`; the author is the
  user's display name. A version to check against (`update_xslt`'s `version`) is not taken. Left for the
  translation tools' port: a translation mapping's payload is kept as given, without regenerating it to say whether
  the code matches it (`matches_mapping`, `warning`), and what it generates now isn't regenerated for the hand-edit
  gate (a hand edit of a translation XSLT counts in whole); an index plan's `ecs_check` isn't given.
* **Error streams' markers** are read from each Error stream's text (`StreamSummaries.errorMarkers`, up to 5,000 a
  stream), as the data viewer's marker mode gave them to the MCP server. A count Stroom gives that isn't a number
  has the stream read, where the Python failed.
* **review_coverage stops reading Error streams** before the session's time limit for a long call, where the MCP
  server had no limit: `progress.continue_from` is then the last Error stream read and `progress.stopped_early` says
  why, so the next call carries on. It stops at once when its thread is interrupted (`Reading was stopped: the call
  was cancelled.`), and says where it is now and then. Stepping the changed copy (or the pipeline's own Events for
  affected records) needs the Stepping permission, and is refused without it, as Stroom's REST refusal failed the
  MCP server's call.
* **Unknown events sampled** from the newest Events streams are read as well-formed XML; a record that isn't is
  skipped (the MCP server read it with lxml's `recover=True`).
* **Tests** port `tests/test_cef.py`'s tool cases and `tests/test_coverage.py` (`TestDraftCefMappingTool`,
  `TestReviewCoverageTool`, `TestCoverageReads`, `TestXsltSaves`); `follow_on_pipelines` is tested on
  `ProcessorFilters.followOnPipelines`, and `cef.extend`'s case was already in `TestCefPlan`.

## The indexing tools

`get_field_conventions`, `draft_index_mapping`, `propose_index_template`, `check_index_template`,
`create_index_doc`, `rename_index` and `verify_index` are ported as tools of their own (`stroom-mcp-impl`, category
indexing) from the MCP server's `tools/indexing.py`, on the indexing core above and the build layer. What they share
is ported as helpers, named for the ports still to come (`create_indexing_pipeline`, `create_pipeline`, `save_xslt`,
`write_documentation`, `describe_document`):

* **`IndexingMemory`**: the session stores, kept per user in this node's memory as the MCP server kept them in its
  own: the naming the user chose in a form (`field_naming_chosen`), a discovery index's time field
  (`discovery_time_chosen`), the component templates given (`component_templates_given`), the templates shown for
  review (`templates_shown`), each for four hours; the plans drafted (`index_plans_drafted`); and the verification
  checks that passed (`verify_checks_passed`, an hour).
* **`IndexPlans`**: `plan_given` (`given`), `drafted_plans` (`drafted`, for save_xslt's port), `with_cardinality`,
  `_with_convention`, `_plan_reply` (`reply`) and `utils/mappingchanges.py`'s `apply_plan_changes`
  (`applyChanges`; to be replaced by the mapping-changes port when it lands).
* **`IndexingInputs`**: `require_events` (for create_indexing_pipeline's port), `_require_input`, `_is_discovery`,
  `_build_of_stream` and the Events' path population (`summarise_events`).
* **`BuildIndexes`**: `_build_destinations`, `_build_index_docs`, `other_index` (for create_pipeline's and
  create_indexing_pipeline's ports), `_same_index`, `_literal_names`, `_set_index_name` and `_the_cluster_in_use`.
* **`IndexExamples`**: `_index_doc`, `_template_from_index`, `_example_from_index`, `_keep_example`, `_kept_example`,
  `_given_components` and `_is_template`.
* **`IndexFields`**: `_index_fields`, `_unsearchable`, `fields_not_in_index` (`notInIndex`), `missing_fields_said`,
  `_fields_written_not_indexed`, `_columns_not_in_index`, `_unlisted` and `set_index_fields` (`addLuceneFields`, for
  create_indexing_pipeline's port).
* **`TemplatePipelines`** and **`TemplateReviews`**: `_destination`, `_documents`, `_component_choices`, `_agree`,
  `_template_summary`, `_asking`, `_not_theirs` and `_commit_action`.
* **`IndexChecks`** and **`IndexSurveys`**: verify_index's searches (`run_test_searches`, `_completeness`,
  `_traced`, `_trace_checks`, `_same_value`, `dashboard_config`, `window_start`, `SearchCheck`, `_as_searched`,
  `_searchable`), and `survey_index`, `index_profile` and `feeding_pipelines` (for read_document's survey of an
  index doc, describe_document's, and for write_documentation).
* **`IndexingArgs`**: the plan, fields, discovery and shared templates validated as pydantic validated them (the
  lists, flags and objects themselves are read by `ToolArgs`, as `utils/params.py`'s `ONE_OR_MORE` read them). `SharedXslt.jsonValues` is
  `utils/sharedxslt.py`'s `json_values`; `ExampleNames.derive` (`_derive`) is now public.

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `get_field_conventions` | yes | yes | no | no |
| `draft_index_mapping` | no | no | yes | no |
| `propose_index_template` | no | no | yes | yes |
| `check_index_template` | no | no | yes | yes |
| `create_index_doc` | no | no | yes | no |
| `rename_index` | no | no | yes | no |
| `verify_index` | no | no | yes | yes |

Elasticsearch is only ever read: an Elastic Index doc's templates through Stroom's own template code (as
`elastic_index_templates` reads them), its cluster's connection test through Stroom's. An agreed template is kept in
the pipeline's description (`KeptMapping.withAgreedTemplate`), and the cluster admin applies it. Index docs,
dashboards and pipelines are made and changed through Stroom's stores as the user, each change asked through
`Consent` first, as the MCP server's were (draft_index_mapping asks before drafting without an example or after an
existing index; keeping the user's example in the build is the tools' own bookkeeping, as in Python).

Where they differ from the MCP server:

* **No configured default convention**: the MCP server's `default_convention` setting has no Stroom counterpart, so
  `get_field_conventions` without a name always puts the choice to the user; its description no longer says "(and
  no configured default)".
* **Not during stage 1**: `get_field_conventions` with neither a name nor a backend, while the session's build is
  still on a step of its events pipeline, returns `status: not_yet` with the build's `next`, rather than asking the
  backend: the backend is stage 2's question (seen in testing: a CSV onboarding's first question was the backend).
  Given `backend=`, it goes on as before.
* **create_index_doc's test** is Stroom's connection test of the doc's cluster (`ElasticClusterTests`, as the
  cluster's Test Connection button runs it), as `{"result": {"ok", "message"}}`: the MCP server ran Stroom's
  `/elasticIndex/v1/testIndex`, which also looks for the index (not there yet for a new one); the index test is not
  bound for injection in Stroom.
* **A Lucene index's fields** are listed and added through Stroom's index service (`IndexResource.findFields` and
  `addField`), as the index's Fields tab does, the user needing Edit on the doc.
* **Reading an existing index's templates** (`like_index`): a doc the user can't read, or Stroom's `NOT_FOUND`, is
  treated as no template (its fields then serve); a doc not configured, or a cluster that can't be reached, is said
  as `Stroom couldn't read its index template: ...`.
* **Keeping the example**: component templates given as JSON objects are kept as their JSON text; the MCP server
  failed to keep such an example (it called `strip()` on a dict) and only logged it.
* **Arguments** are read as `ONE_OR_MORE` read them; `plan`, `discovery`, `extra_fields` and `shared` may be given as
  JSON text too, and one that doesn't validate is refused with the model's errors (`plan isn't a valid field plan:
  ...`) in place of pydantic's. `backend` other than `lucene` or `elasticsearch` is refused in words.
* **Paths** in the options and refusals are Stroom's explorer paths (`/Elastic Indices`), not the find index's.
* **propose_index_template and check_index_template step within the session's time limit** for a long call (the MCP
  server's had none): cut short, the result says `stopped_early: true` and `stepped` (what was stepped, and that the
  check is of the documents those records gave only), and the check and any agreement go ahead on those documents.
  An interrupted call is refused with `Stepping was stopped: the call was cancelled.`. They need the Stepping
  permission, checked first, and the streams stepped are audited as viewed. `max_records` (check) is described
  (`The most records stepped: 1 to 500 (default 50).`), and one outside it, a `priority` below 0 or not a whole
  number, or no `pipeline_uuid`, is refused in words. "Already agreed" compares the bodies as JSON with sorted keys.
  The kept-example note says "what was given as example_template is not an index template" only when one was given
  (the MCP server said it whenever an example was kept). `pipeline_link` is `StroomLinks.documentTarget`'s.
* **verify_index runs within the session's time limit** for a long call: where the MCP server stopped before the
  caller's access token ran out, it stops before the time limit (`PipelineStepping.timeIsUp()`), and `next` says
  `Stopped before this session's time limit for a call (N s) ran out, with N check(s) still to run: call
  verify_index again ...; each call has its own time, so there is nothing for the user to do.` (the checks that
  passed are kept, as before). An interrupted call is refused with `Verification was stopped: the call was
  cancelled.`. Every hit of a stream is traced in one stepping walk (`SteppingWalks`), the default path included;
  without the Stepping permission each traced check fails with its reason, as a failed step did. A stream's event
  count is read through `StreamReader.fetch`. `retries` outside 0 to 20, a bad `expected_documents`, and a search
  with no field or a condition dashboards don't take are refused in words. A time with no offset is read as UTC
  for the dashboard's window (the MCP server used its local zone). The dashboard is made through
  `WriteGuard.createFilled` (role Indexing) and written through Stroom's Dashboard store; the searches go through
  `IndexSearches` (unsorted), which gained an overload with an explicit wait for survey_index's sample.
* **survey_index** (`IndexSurveys`, used by write_documentation's index documentation and read_document's survey
  of an index doc) reads its further groups of columns one after another (the MCP
  server ran them at once); `feeding_pipelines` finds the pipelines with `PipelineIndex.containing` and a feed's
  description with `FeedNames.exact`.

### `create_indexing_pipeline`

Ported as a tool of its own (`CreateIndexingPipelineTool`, `stroom-mcp-impl`, category indexing) from
`tools/indexing.py` `create_indexing_pipeline`, with `_events_available` (`eventsAvailable`) and
`_missing_index_fields` (`missingIndexFields`). It stands on `PipelineShapes`, `IndexingInputs.requireEvents`,
`IndexFields` (`indexFields`, and `addLuceneFields` for `set_index_fields`), `KeptMapping.readMapping`,
`IndexingHelpers.writesToIndex` (`_writes`) and create_pipeline's own path: `CreatePipelineTool.fromTemplate`, which
`create_pipeline` now calls too, takes the arguments as a `CreatePipelineTool.FromTemplate` (the Python's
`create_pipeline(ctx, name, template_uuid, props, build=, confirmation_id=, accept_parser_mismatch=True)`, without
its `confirmation_id`: see `Consent`).

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `create_indexing_pipeline` | no | no | no | no |

The pipeline is confirmed in create_pipeline's form (its name editable), but the Lucene index fields added after it
from the XSLT's plan are not asked about, as the MCP server's tool didn't (its `confirms` was false): so over `/mcp`
the call is approved as a whole. The tool saves no XSLT: `xslt_uuid` is one saved before (save_xslt
`index_plan=...`).

Where it differs from the MCP server:

* **Its description** starts with its onboarding step (`Onboarding step N of M (indexing_pipeline): `), as the MCP
  server's `annotate_tools` labelled it; the other indexing tools' ports don't carry their labels yet.
* **A doc that can't be read** (the XSLT, a Lucene Index) is refused as `XSLT <uuid> can't be read: it doesn't
  exist, or you don't have permission to view it` (the MCP server failed with the REST error); a field plan kept
  with the XSLT that doesn't validate is refused as `The field plan kept with the indexing XSLT isn't valid: ...`
  (pydantic's error otherwise).
* **A Lucene template with no open index property** is refused (`This template sets its own Lucene index: ...`);
  the Python failed on it with a StopIteration.
* **The Lucene index's fields** are read through Stroom's index service (`IndexFields.indexFields`, up to 2,000), the
  MCP server's `/dataSource/v1/findFields` read 500.
* **The build** is required, as the Python signature had it, and remembered for the session.
* **Tests** (`TestCreateIndexingPipelineTool`) port `tests/test_indexing.py`'s create_indexing_pipeline and
  `_missing_index_fields` cases, `tests/test_index_rename.py`'s `test_an_indexing_pipeline_takes_the_index_its_doc_names`
  and `tests/test_authoring_guards.py`'s `test_an_indexing_pipeline_needs_events_first`, with
  `dev/e2e_lucene_indexing.py`'s pipeline (confirmed, then made on create_pipeline's path with the XSLT, the index
  and the plan's fields) as a test on the pipeline world.

## The translation tools: `save_text_converter`, `save_xslt`, `save_dictionary`, `draft_translation_mapping`, `build_translation_xslt`, `build_data_splitter`, `build_reference_xslt` and `rebuild_mapping`

Ported as tools of their own (`stroom-mcp-impl`, category content) from the MCP server's `tools/translation.py`,
`tools/generation.py` and `tools/rebuild.py`, on the generators, the draft mapping, the validation core and the build
layer above. What they share is injectable, for the other tools that save translation code to reuse:

* **`XsltSaves`** (public): the one save path for an XSLT: `create(context, build, name, code, source, change)`,
  `update(call, context, uuid, code, version, source, change, discardHandEdit, choices)`, `lostMapping(ref,
  description)`, `requiredFor(session, stage, build, feeds)`, `xsltRole(code)` and `handEditGate(...)`. `source` is a
  `TranslationDocs.Source`: `Source.of(mapping)`, `Source.of(indexPlan)`, `Source.of(cefPlan)` or `Source.NONE`.
* **`TranslationDocs`** (`create_text_converter`, `update_text_converter`, `create_xslt`, `update_xslt`,
  `create_dictionary`, `update_dictionary`, `lost_mapping`, `_check_converter`, `_checked`, `_described`,
  `_tag_kept`, `_summary`): every document is made through the write guard (`WriteGuard.createFilled`: deleted again
  if writing it fails) and saved through Stroom's own stores as the user; an update checks the document is the tools'
  own, and with `version` that it hasn't changed since it was read.
* **`HandEditGate`** (`hand_edit_gate`, `_remembered`, `_CARRY`), **`KeptCode`** (`_kept_code`), **`XsltHistory`**
  (`_pending`, `_previewed`), **`RequiredImports`** (`_required_for`, `_import_notes`,
  `_refuse_without_required_imports`, `xslt_role`), **`EventVersions`** (`event_schema`, `event_versions`,
  `_remember_version`, `version_settled`, `_version_chosen`, `chosen_or_latest`, `translation_version`,
  `_ask_version`, `target_version`), **`TranslationBuilds`** (`_mapping_to_change`, `_kept_mapping`, `_generated`,
  `_meta_not_sent`, `zone_not_kept`, `_unknown_markdown`, `_unknown_key`, `_unknown_agreed`,
  `_remember_unknown_agreed`, `_fix_hint`, `notes_in_build`), **`MappingSampleCheck`** (`_sample_check`, with
  `xpathcheck.py`'s `check_xpaths`, `check_extractions`, `record_documents` and `xpath_inputs` on `XPathChecks`),
  **`MappingProblems`** (`mapping_problems`, `_described`, `MAPPING_EXAMPLE`, `ESCAPING`), **`SentMappings`**
  (`_remember_mapping`, `_sent`), **`SampleStreams`** (`rebuild.py`'s `sample_streams`) and **`GeneratedDrift`**
  (`builds.py`'s `_drift`, `_plan_drift` and `lost_mappings`).
* `utils/sourcenotes.py`'s `merged` and `check_mapping` are added to `SourceNotes` (stroom-ai-impl);
  `SampleProfiler.inventory` and `XmlTree.toText` are now public.

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `save_text_converter` | no | no | no | no |
| `save_xslt` | no | no | no | no |
| `save_dictionary` | no | no | no | no |
| `draft_translation_mapping` | yes | yes | no | no |
| `build_translation_xslt` | no | yes (no build, no uuid) | no | yes |
| `build_data_splitter` | no | yes (no save_as) | no | no |
| `build_reference_xslt` | yes | yes | no | no |
| `rebuild_mapping` | no | no | yes | yes |

`rebuild_mapping` asks the user to confirm before its one save, as the MCP server's did. The others ask only in
some cases (rules kept as Unknown, a hand edit both the user and the change touched), so over `/mcp` each write is
approved as a whole.

Where they differ from the MCP server:

* **`build_data_splitter`'s read buffer**: the converter gets `bufferSize` on `<dataSplitter>` when the sample's
  longest record (its longest line, or a record over several lines whole: a stack trace, a block, a quoted value
  holding new lines) is more than half Stroom's default (20,000 characters), at four times that, and `buffer_size=`
  sets it outright (20,000 to 1,000,000,000); the result says `buffer_size` when set. A record longer than the
  buffer is lost, Stroom reporting that the buffer was exhausted (seen in testing: CSV lines of about 20,000
  characters). The MCP server never set it.
* **`build_data_splitter`'s `stroom_check`**: the converter is also run by Stroom's own Data Splitter parser
  (`StroomSplitterRun`: `ConfigFilter` and `DS3Parser`, without the schema check, as Stroom's `TestDS3` runs it) on
  each sample text, and the result gives its errors (with `error_hints` from the error triage rules), record counts
  that differ from the dry run, and up to 5 records written differently. Errors or a different count (records lost)
  keep save_as from saving; value differences are reported only. Lines ended by a carriage return alone are
  refused too (`line_endings`). The MCP server had only its dry run.
* **The converter `build_data_splitter` writes** is `SplitterXml.converter`: `generateSplitter`'s (still the MCP
  server's, for the parity fixtures) with an `id` on each part (`header`, `record`, `fields`, `blank_line`, `body`,
  `unparsed`), which Stroom's errors name, and a top-level regex passing over blank lines, which Stroom otherwise
  writes as records of empty fields.
* **Spec options the MCP server didn't have**: `escape` (delimited: the split's escape attribute, cells as `$2`;
  named columns as a split per column), `spaced_values` (key_value without quote: pairs read from the end, Data
  Splitter's `reverse`; inferred only when it parses every record the plain reading can't), `catch_all` (a record
  the spec doesn't match kept as `unparsed`), `blank_line_records` (a block of lines per record) and the `fields`
  kind (each field by its own regex, `matchOrder="any"`).
* **create_pipeline `standalone='events'`**: a last resort the MCP server didn't have, refused while
  find_pipeline_templates finds any translation template: an events pipeline of its own, made as Stroom's standard
  Event Data templates are (Source, the parser the build's sample needs, readRecordCountFilter, splitFilter of 100,
  translationFilter, schemaFilter on EVENTS, recordOutputFilter, writeRecordCountFilter, streamAppender writing
  Events), with translationFilter.xslt and the parser's textConverter given. Where no template has the parser the
  sample needs, start_onboarding and find_pipeline_templates offer any translation template with replace_parser
  (an XMLParser one first for XML fragments), rather than only asking the user.
* **`ignoreErrors` refused**: save_text_converter and build_data_splitter's save_as refuse a Data Splitter that adds
  `ignoreErrors="true"` (one the converter replaced already had is kept): it hides the errors for unmatched text.
  `error_rules.yaml` classes the Data Splitter's "exhausted the buffer" and unmatched-content errors as blocking,
  with what to do (buffer_size; spaced_values, escape, catch_all, blank_line_records).
* **The dry run follows Stroom where they differed**: split cells lose the white space round them, a cell past the
  heading is kept with no name (`(no name)`), and key=value text with something no pair matches is an unmatched
  line (the MCP server's dry run stopped there and kept the record). The parity fixtures are unchanged by it.
* **The event-logging version chosen for a build** is kept in the build's record (`event_logging_version`: `version`
  and `how`) once the build has a folder, and in this node's memory for the session, so a later session doesn't ask
  again; the MCP server kept it per user in its memory only. draft_translation_mapping stays read-only (as check_events
  does, the record is the tools' own bookkeeping). record_source_notes checks its catalogue against the same chosen
  version.
* **The mapping last sent and the session's draft** are kept in the session's workspace (`/outputs/mappings/`: the
  draft as `draft-<build>.json`, an XSLT's as `<uuid>.json`, a build and name by its digest), where the agent can read
  them too, falling back to the mapping saved with the XSLT; the MCP server kept them in the replica's memory. A
  session without a workspace remembers none.
* **Compiling a generated XSLT** is Stroom's own (`XsltCompileCheck`), with no compile notes to add as warnings.
* **The version history's `by`** is `Stroom <version>, <the session's caller>` (the chat, or the MCP client), where
  the MCP server wrote `stroom-mcp <version>, <client>, model <model>`; an XSLT saved by the MCP server reads as
  saved by an earlier version. The author is the user as Stroom records a document's updater.
* **`_pipeline_of`** finds the pipelines whose own layer names the XSLT by reading the pipelines the user can view
  (`PipelineIndex`), not Stroom's content search, so it needs no waits for an index that lags.
* **`_import_notes`' `template_rules`** counts the import's template rules; the MCP server read a key `describe`
  doesn't give, so it was always 0.
* **`mapping`, `changes`, `splitter`, `spec`, `index_plan` and `hand_edit_choices`** are declared as objects with
  their descriptions, not the models' whole JSON schemas; text that is JSON is read as the MCP server read it, with
  Python's `json` messages (`PyJson`). A missing key's description comes from the MCP server's models
  (`mapping-descriptions.json`, made from them).
* **Results' JSON leaves nulls out**, as every ported tool's does: `field_mapping` (null until it is previewed), a
  refused mapping's `saved` and a version question's `saved` are left out.
* **Refusals** for an argument that isn't what it should be (`max_records` outside 1 to 1000, a `hand_edit_choices`
  answer that isn't `keep`, `overwrite` or `per_field`, a `converter_type` that isn't one) say so in pydantic's place.
* **The drafted index plans** (`drafted_plans`) come from `IndexingMemory`; with none, save_xslt saves from
  `index_plan` or the plan kept with the XSLT.

## The builds' tools: `start_build`, `write_documentation` and `promote_build`

The last three of the MCP server's tools, ported as tools of their own (`stroom-mcp-impl`, category content) from
`tools/builds.py`, on the build layer and the helpers above: `WriteGuard`, `BuildRecords`, `BuildPlan`, `Consent`,
`BuildChecks` (whose `build_checks`, `mapping_digest` and `list_build`, in build_status, were ported before),
`GeneratedDrift` (`_drift`, `_plan_drift`, `lost_mappings`), `KeptMappings` (`kept_mapping`),
`IndexTemplateAgreements`, `PromotionProcessing`, `ProcessorFilterWrites`, `OutputFeeds.restoreOutputFeed`,
`IndexingRuns.developmentBatch`, `IndexSurveys`, `IndexFields`, `EventVersions`, `PipelineStepping`,
`DraftCefMappingTool.stepped` (cef's `_stepped`), `StreamSummaries`, `AcceptedErrors`, `TranslationBuilds.notesInBuild`,
the field documentation (`EventFieldDocs`, `IndexFieldDocs`, `FieldDocs`, `Descriptions`, `DocumentationText`), the
CEF core (`CefPlan`, `CefLines`) and `XsltVersionHistory`. The rest of `tools/builds.py` is ported as:

* **`FieldMappingSections`**: `field_mapping_section`, `written_fields_section`, `cef_written_section`, `_agreed_line`
  and `_no_events`.
* **`BuildDocumentation`**: `_documentation_of`, `_stepped_streams`, `_raw_parents`, `_vendor_notes`,
  `needs_descriptions`, `_layout`, `_errors_markdown`, `_errors_section`, `_with_change` and `_written`.
* **`IndexDocumentation`**: `_document_index` and `_own_indexing_pipeline`.
* **`PromotionLayout`**: `_version_folders`, `_joining` and `promotion_tree`; `consolidate_versions` is
  `PromoteBuildTool.consolidateVersions`.
* **`DocumentPlaces`**: `_folder_of`, `_path` and `_documentation_beside`, as the user (only what they can view).
* **`DocumentWrites`**: `get_doc`/`put_doc` of a Documentation doc, and of a working copy's fields
  (`data`, `converterType`, `documentation`, `description`) written over its original, for any type, through its
  JSON.
* **`DocumentationVersions`**: `utils/versionlog.py` (`body_of`, `rows_of`, `pending_of`, `block`, `with_pending`,
  `consolidate` and `code_of`); `utils/sourcenotes.py`'s `fields_markdown` is
  `WriteDocumentationTool.sourceFieldsMarkdown`.
* `WriteGuard` gains `moveTo`, `copyTo` and `delete` (the explorer's move, copy and delete, as the user);
  `UserActionEntries` gains `disable_filter` (`disableFilter`); `XsltHistory` gains `changedBy` (a doc row's `by`).

| Tool | Read-only | Has read-only calls | Confirms itself | Run slot |
|------|-----------|---------------------|-----------------|----------|
| `start_build` | no | no | no | no |
| `write_documentation` | no | no | no | yes |
| `promote_build` | no | no | yes | no |

`promote_build` asks before every change: the layout is shown in the chat (`needs_review`), then approved in a short
form bound to it, as the MCP server's was. `write_documentation` asks only to record errors as benign and to document
an existing index doc, so over `/mcp` a call is approved as a whole; `start_build` asks nothing.

Where they differ from the MCP server:

* **Promotion is made through Stroom's explorer and stores as the user**: documents are moved with
  `ExplorerService.move` (permissions the destination's), a production document is backed up with
  `ExplorerService.copy` into `<workspace>/backups` (named `<name> backup <yyyyMMddHHmmss>`, UTC) before its working
  copy's fields are written over it through its type's store, and working copies are deleted with
  `ExplorerService.delete`; the user needs the permissions each of those needs. The MCP server's waits for a build
  listed empty right after a write (`_LISTING_RETRIES`) are not ported: the explorer is read in-process.
* **Folders** in the layout, the plan and the result are paths from System (`System/Feeds/Acme`), as the MCP server
  wrote them; destinations are read in any form `WriteGuard.folderParts` takes (`/Feeds/Acme`, `System / Feeds`).
  `destinations` may also be given as JSON text.
* **write_documentation steps as the user** (the Stepping permission, checked first), within the session's time limit
  for a long call, and the streams stepped are audited; cut short, the section is of the records stepped (its
  "Counts are of N sample records" line says how many). It takes a run slot.
* **An existing doc in the build is changed only when it is the tools' own** (`WriteGuard.checkInBuild`), as the
  other ported tools change documents; the MCP server wrote any doc of the name in the build folder.
* **The index section's event-logging schema** is the build's chosen version (`EventVersions.chosenOrLatest` with the
  build), where the MCP server read the session's.
* **A row's `by`** is `<user> (agent: Stroom <version>, <the session's caller>)`, the user as Stroom records a
  document's updater, where the MCP server wrote `<preferred_username> (agent: stroom-mcp <version>, <client>, model
  <model>)`.
* **start_build's `not_found`** says the feed isn't `in this Stroom` (`FeedNames.notFound`), as the other ported tools
  do, where the MCP server named the cluster and its URL.
* **Arguments**: `build` is required by each, and a call without it is refused in words. `accept_errors` may be one
  object or JSON text, and one without `example` or `reason` is refused as `accept_errors[0].example is required`, in
  place of pydantic's message; `field_descriptions` may be JSON text (read with Python's `json`).
* **Results' JSON leaves nulls out**, as every ported tool's does: start_build's `next` is absent where the plan has
  none.

Tests port `tests/test_promotion_layout.py` (`TestPromotionLayout`, `TestPromoteBuildTool`),
`tests/test_surface.py`'s promotion cases, `tests/test_fielddoc.py`'s write_documentation, field_mapping_section,
`_document_index` and agreed-template cases (`TestWriteDocumentationTool`, `TestFieldMappingSections`),
`tests/test_choices_and_unknown.py`'s Unknown case, `tests/test_existing_data_plan.py`'s start_build case
(`TestStartBuildTool`), `tests/test_version_history.py`'s version control case (`TestDocumentationVersions`), and
what `dev/e2e_plan_walk.py` (promotion held up by the build's own stale documentation, a hand edit only warning) and
`dev/e2e_source_docs.py` (the source notes beside the feed) expect.

## Hardening after review (where the ported tools now differ from the Stroom MCP server)

* **Stroom functions that reach outside Stroom** (`XsltSafety.STROOM_REFUSED`): an XSLT the tools save, check
  (check_xslt), compile or step as a draft (step_pipeline, step_sample, step_records, compare_outputs) may not call
  `stroom:http-call()`, `stroom:fetch-json()` (both request a URL) or `stroom:ask-ai()` (sends its text to a chat
  model): Stroom would run them on its nodes for every record. Unless the XSLT being replaced (a save) or the
  element's saved XSLT (a step) already calls the same function: the user put it there (`XsltSafety.keptBy`, the
  one rule both use). A mapping's xpaths are not refused for these (they are only ever saved as XSLT, where they
  are). The Python server let them all through.
* **DNS lookups need the user's agreement** (`XsltSafety.STROOM_ASKED`, `LookupAgreements`): `stroom:host-name()`
  and `stroom:host-address()` look a name up in DNS when the pipeline runs, and a lookup can carry the looked-up
  value (a record's data) to the DNS server. They are not refused (`XsltSafety.unsafeInXslt` leaves them out, so
  XsltCompileCheck compiles them, and check_xslt passes them with a warning saying where each is called). A save
  (save_xslt, an indexing XSLT from its plan included, build_translation_xslt, rebuild_mapping and
  draft_cef_mapping's save: each goes through `TranslationDocs.createXslt` / `updateXslt`; build_reference_xslt's
  XSLT is saved with save_xslt) of an
  XSLT calling one the XSLT it replaces doesn't (`XsltSafety.newLookups`) asks the user first, in a form
  (`Consent.require`, an approval), after the other checks and gates and before anything is written: the form names
  the functions, where each is called (`template match='record' (line 12)`, as `XsltSafety.lookupsInXslt` finds it)
  and what they do. Agreed: saved. Declined: `XSLT not saved: the user declined stroom:host-name() (...)`. Only a
  form counts: where the session can't show one, the save is refused, saying so (the agent never holds the answer,
  and no pending id is handed out for it). Stepping a draft (step_pipeline, step_sample, step_records,
  compare_outputs, and summarise_fix's steps) asks the same way, as a step runs the XSLT on Stroom and so makes
  the lookups too; one the element's saved XSLT already calls isn't asked about. What the user agreed to is
  remembered for the session, by document (the XSLT's UUID; a new one's build and name; a step's element's saved
  XSLT, else the pipeline and element) and function: the same XSLT stepped or saved again isn't asked about again,
  an agreement given for a step covers saving the draft over the same XSLT, and a function not agreed to yet is
  asked about on its own. The outright refusals above are made first, whatever the user would agree to. The Python
  server let the lookups through.
* **system-property() and available-system-properties()** are refused like environment-variable(): with Saxon's
  external functions allowed, as Stroom runs XSLT, a name in no namespace reads the JVM's system properties. Saxon
  gives system-property() to XSLT (and use-when) only, not to XPath. `LockedSaxon` now turns external functions off
  (Stroom's functions are integrated ones, which Saxon still runs). environment-variable() and
  available-environment-variables() were already hidden by `LockedSaxon`'s empty resolver (Saxon reads the resolver
  only while external functions are on, so they are now hidden twice); XsltCompileCheck's processor hid neither.
  `XsltCompileCheck` compiles with a processor locked down as `LockedSaxon`'s is (`LockedSaxon.newLocked`: no URI,
  unparsed text, collection, environment variable or system property, external functions off, its source read
  without external entities), with Stroom's functions and import resolver added; a DOCTYPE was already refused.
* **Regular expressions the agent writes** (a splitter's pattern, record start and end; an extraction's regex; a
  condition's `matches`) are compiled at most `SafePattern.MAX_REGEX_LENGTH` (2,000) characters long and matched
  through `DeadlineCharSequence` within `SafePattern.REGEX_TIME_LIMIT` (10 s, or the call's own limit if sooner):
  one that backtracks badly is stopped, and the tool says "The pattern '...' took too long to match (more than
  10 s) and was stopped: it backtracks too much. Make it simpler: ...". build_data_splitter, build_translation_xslt
  and draft_translation_mapping give it as their refusal; XPathChecks gives it as the extraction's problem. Saxon's
  regex engine copies the text before matching and checks no time limit, so each text is matched first by the same
  regex in Java (close to XPath's) through the deadline, and `LockedSaxon` lowers Saxon's own backtracking limit
  (`REGEX_BACKTRACKING_LIMIT`, a million) for what Java can't read and for matches() in an agent's xpath.
* **Reading a stream's text needs View Data**, checked by `StreamReader` itself (raw text, its cache, and Error
  streams), whatever the caller checked: the stream store checks only the feed. Without it, create_processor_filter
  and reprocess_streams don't read why Elasticsearch refused earlier runs (the index is taken to hold what they
  sent), and the build plan (build_status and every tool's next step) goes without the sample's format, as before a
  sample is uploaded. Declared on create_processor_filter, reprocess_streams and build_status.
* **Shared state**: `EventSchema` works out everything it gives once, when it is made, into maps never changed after
  (a Xerces DOM isn't safe to read from several threads, and `HeldSchemas` shares one per version); `HeldSchemas`
  keeps the 8 most recently used schema models, `XPathTreeParser` the 64 most recently used parsers, and
  `WriteGuard` makes folders under a fixed set of 64 locks; `HandEditGate` reads its answers from a copy taken under
  their lock; `StreamReader` doesn't keep a text longer than its whole cache.
* **Searches and stepping sessions** are ended even when the first request fails after Stroom started them: a search
  by the owner made up for it alone (`AgentSearches.destroyOwnedBy`), and a stepping session by Stroom's stepping
  service itself, which now ends a session it made for a step that failed (its id never reached the client).
* **write_documentation's "no events" reason** steps each stream within the session's time limit for a long call;
  once it is up, the streams left are named, not stepped.
* **check_events takes a run slot**, as summarise_streams does: it reads and validates Events streams.
* **Feeds by pipeline** (find_reference_data, review_coverage's templates) are read by `PipelineFeeds.filterFeeds`,
  the one reader (IS_DOC_REF, IN_DICTIONARY, comma lists, terms at any depth, and an Id term's stream's feed), where
  only a top-level Feed term's value was read; `ProcessorFilterWrites.filterFeeds`, which nothing used, is gone, and
  `ProcessorFilters.canManageProcessors` is `ProcessorFilterAccess`'s.

## Shared helpers (one copy of each Python behaviour)

The tools were ported in parallel, and the helpers they each ported are now one copy each, in stroom-ai-impl
`stroom.ai.impl.tools`: `ToolRefusal` (the server's `ToolError`, for every tool and helper), `PyText` (Python's
`str` methods, `re` flags, `repr()`/`str()`, `PyText.list`, number formats), `JsonValues` (truthiness, `x or {}`,
the maps or strings a list holds, deep copies, `Counter`), `ToolArgs` (`ONE_OR_MORE`, flags, objects given as
JSON text, the "not JSON" refusal) and `IpAddresses`; `SampleTexts` reads samples for every tool, `XmlTrees` reads
XML (`tryParse`, `child`, `is`; `XmlTrees.XSL` is the one XSLT namespace), and `DocumentWrites` writes the build's
Documentation docs. Where the copies differed, the one closest to Python is kept, so a few replies change:

* **Field documentation and CEF regexes** take only `\n` for a line end (`$` doesn't match before a `\r`, and `.`
  matches one), as Python's `re` does; no reference output changed.
* **A value in a message** is written as Python's `repr()`/`str()` writes it everywhere: `'\x00'` for a NUL (it was
  left raw), `None` for null (some said `null`), `True`/`False`, `1e+16`, and `['a']` for a list in an f-string.
* **An IPv6 address with an IPv4 tail after `::`** (e.g. `64:ff9b::192.0.2.1`) is an address to the sample
  profiler, as Python's `ipaddress` has it (its own reader refused them; the index template check's didn't).
* **The "did you mean" ratio** (`CloseMatches.ratio`, and feed names) applies difflib's autojunk to a second string
  of 200 or more characters, as `difflib.SequenceMatcher` does.
* **A list argument given as text** is read the same by every tool: the indexing tools now also read Python's
  `True`/`False`/`None` in a list literal, and an object in single quotes (`{'a': 'b'}`); a flag given as a number
  is set when it isn't 0 (pydantic's `bool`).
* **Text that is not JSON** is refused in the server's words, `{name} is text that is not JSON ({msg} at character
  {pos}): ...`, for build_data_splitter's `spec`, the `hand_edit_choices` of save_xslt and build_translation_xslt,
  and draft_cef_mapping's `header`, `style` and `hand_edit_choices` too (they gave Jackson's message, or none);
  draft_cef_mapping's `overrides` gives Python's message rather than Jackson's.
* **A feed name that is no feed's** is refused by upload_sample, the pipeline writes and the processor filter writes
  (create_processor_filter, promote_build's processing) with the cluster advice `FeedNames.notFound` gives ("... in
  this Stroom. If the user named it, they may mean a different Stroom cluster: stop, ..."), as describe_feed and
  start_build do.
* **A Documentation doc that can't be read back** while it is written (a survey, source notes, an index example) is
  refused as `DocumentWrites` says it: "The Documentation doc ... can't be read: it doesn't exist, or you don't have
  permission to view it".
* **An error rule's element glob** (`stroom.ai.errorRules`) matches as `fnmatch.fnmatchcase` on lower-cased text
  (`Globs`): `*` and `?` as before, and `[...]` sets too, as the server matched them.
