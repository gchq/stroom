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
| `describe_data_source` | A data source's fields, conditions, documentation and AGENTS docs | all | yes | | | View, Use | workspace | `describe_feed` |
| `document_documentation` | A document's Documentation tab | all | yes | | | View | workspace | `describe_document` (type Documentation) |
| `document_info` | A document's type, path, tags, history, version and dependencies | all | yes | | | View | no | `describe_document` (part) |
| `document_links` | Follows dependency links (dependants or dependencies) from documents | all | yes | | Manage Processors | View | workspace | |
| `elastic_index_templates` | An Elastic Index's index template and its component templates, read from its cluster, each by name with its JSON body | all | yes | | | View | no | none (`check_index_template` and `propose_index_template` take templates the user gives) |
| `fetch_events` | Fetches the events behind StreamId/EventId rows into an XML file | all | yes | View Data | | | yes (workspace) | `read_stream`, `locate_event` (part) |
| `find_in_content` | Finds text in documents' content (XSLTs, converters, dictionaries) | all | yes | | | View | workspace | `find_documents` (with `content`) |
| `folder_contents` | An Explorer folder's documents, streams, processor filters and task counts | all | yes | | Manage Processors | View | no | `processing_status` (part) |
| `get_current_date` | Today's date in UTC | all | yes | | | | no | |
| `get_current_datetime` | The current date and time in UTC | all | yes | | | | no | |
| `get_current_time` | The current time of day in UTC | all | yes | | | | no | |
| `get_instructions` | The AGENTS docs that apply to folders or documents | all | yes | | | View | no | `get_instructions` |
| `get_utc_time_offset` | A time zone's offset from UTC | all | yes | | | | no | |
| `grep` | Finds lines of a workspace file containing some text | all | yes | | | | yes (workspace) | |
| `jq` | Runs a jq query on a workspace JSON file | all | yes | | | | yes (workspace) | |
| `list_data_sources` | Lists the data sources the user can query | all | yes | | | View, Use | workspace | |
| `ls` | Lists the chat's workspace files | all | yes | | | | yes (workspace) | |
| `memory` | Reads and changes the user's and the chat's memory | Agent | no | | | | yes (chat) | |
| `pipeline` | Chains data tools over workspace files | all | yes | | | | yes (workspace) | |
| `processor_tasks` | Processing failures and why, backlogs, task lists and counts across the cluster | all | yes | Manage Processors | View Data | | workspace | `processing_status`, `summarise_streams` (errors) |
| `propose_code_change` | Proposes a change to the XSLT or converter being stepped, tried by stepping | all | yes | | | View | yes (stepping tab, button) | `summarise_fix`, `step_pipeline` (part) |
| `propose_dashboard` | Offers a button to create a dashboard from StroomQL | all | yes | | | Use | yes (button) | `verify_index` (part: it creates a dashboard) |
| `propose_dashboard_change` | Proposes changes to the attached dashboard's tables and components | all | yes | | | View | yes (attached tab, button) | |
| `propose_documentation_change` | Proposes a change to a document's Documentation tab, reviewed as a diff | all | yes | | | View | yes (button) | `write_documentation` (part: it writes a pipeline's or an index's) |
| `propose_query` | Offers a button to create a StroomQL Query document | all | yes | | | Use | yes (button) | |
| `propose_query_change` | Offers a button to change the query in the attached tab | all | yes | | | View, Use | yes (attached tab, button) | |
| `read_document` | A document's content, or what a processor filter selects | all | yes | | Manage Processors | View | workspace | `describe_document` |
| `read_file` | Reads lines of a workspace file | all | yes | | | | yes (workspace) | |
| `read_turn` | Reads a whole earlier turn of the chat | all | yes | | | | yes (history) | |
| `run_stroomql` | Runs a StroomQL query into a workspace table | all | yes | | | | workspace | `find_events` |
| `search_events` | XPath, text or jq search over fetched events, into a table | all | yes | | | | yes (workspace) | `summarise_streams` (events, part) |
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

## How the overlapping tools differ

The MCP server calls Stroom's REST API with the user's forwarded token, so Stroom checks the user's permissions; it
checks no app permissions itself. Its writes are limited to a workspace folder (`WriteGuard`) and need a
confirmation or approval (an elicitation form, or a pending id the user confirms). The built-in tools call Stroom's
services directly as the user, and those listed here only read.

* **`get_instructions`**: same name and purpose, and the same answer in substance (the docs that apply, most general
  first, then where the others are). MCP takes `folders`, `feeds` and `docs` (`{type, name, uuid?}`); the built-in
  takes `folders` (UUID or path) and `uuids`, and has no `feeds`. MCP's doc name is configurable
  (`instructions_doc_name`); the built-in's is always `AGENTS`.
* **`find_in_content` and `find_documents`**: MCP's `find_documents` finds by name (the Explorer quick filter) or,
  with `content`, by text in content (case-insensitive contains, `types` filtered after the search). The built-in
  takes `pattern`, `regex`, `matchCase`, `uuids` and `types`, and leaves out folders the AGENTS docs say to ignore.
  No built-in finds documents by name across Stroom.
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
* **`run_stroomql` and `find_events`**: MCP searches one index with structured terms (`where: [{field, value}]`,
  `time_from`/`time_to`, `fields`, `limit` up to 20) as a dashboard would, and returns StreamId/EventId hits. The
  built-in runs any StroomQL `query` into a workspace table.
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

Proposals only: nothing has been renamed.

* `get_instructions`: let both take `folders`, `feeds` and `uuids` (MCP's `docs` could become, or also accept,
  `uuids`).
* MCP's `propose_index_template` could become `draft_index_template` or `build_index_template`, so that `propose_*`
  keeps its built-in meaning: offer the user an action.
* Keep `find_documents` for finding by name and type (which the built-ins lack), and move content search to
  `find_in_content`.
* Make `describe_document`'s `type` optional, as the built-ins need only a UUID, or split it along the built-ins'
  lines (`read_document`, `document_info`, `document_documentation`) with its analysis as extra parts.
* Fold `find_streams` and `describe_stream` into `stream_meta` (adding `parent_id` and a child-stream view), and
  `processing_status` into `processor_tasks mode=summary`. Share one error triage between `summarise_streams` and
  `processor_tasks` (`error_rules.yaml` against `ErrorMessageNormaliser` and `FailureModes`).
* One reference data tool with two entry points: every map (`find_reference_data`) or given XSLTs (`xslt_lookups`).
* Align argument names: `pipeline_uuid` against `pipeline`/`pipelines`, `stream_id(s)` against `streams`,
  `time_from`/`time_to`/`created_after` against `from`/`to`/`since`/`until`. The two servers also name tools
  differently: MCP puts the verb first (`find_*`, `describe_*`, `read_*`), most built-ins the noun first
  (`document_info`, `stream_meta`, `processor_tasks`).
* Consent: map MCP's `confirmation_id`/`approval_id`/`reviewed` onto one consent model with the chat's approval
  forms (the roadmap's "one consent model").

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
* **Not ported**: `agent_line` and `client_model` (the save's "by" text comes from the caller's session),
  `check_xslt`'s import lookup and `_record_validated` (the tools' own wiring, with the plan layer).

## Sample analysis (ported, tools not yet wired)

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
* `quoted_csv`'s header sniff can still fail on odd text, as it did in Python (`csv.Error`); `SampleRefusal` is
  thrown where the server raised a `ToolError`.

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
  `create_indexing_pipeline`, `verify_index`, and `find_events` (overlaps `run_stroomql`, above).
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
* **`Consent`**: before a write, `consent.require(call, context, Request.confirmation(action, summary, details,
  token))` (or `Request.approval(...)`, with `.withEditable(key, title, proposed)` for a value the user may correct,
  and `.keepingId()` when a later gate in the same call may still stop it). Empty means the user agreed: go on, and
  read any correction with `consent.edited(context, key, proposed)`. Otherwise return the round it gives (a
  `needs_confirmation` or `needs_approval` result) as the tool's result. A refusal (declined, or a form closed
  unanswered) is a `ToolRefusal`. Where the session can ask, the user answers a form: in the chat the run stops
  while it waits and the same call is made again with the answer, so a tool must be safe to re-run up to its
  question. `consent.choose(...)` asks the user to pick one of some options.
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
* **Consent ids** are not the MCP server's signed request-state tokens: an id is random, kept by this node for the
  session it was given in, bound to the action, the exact details and the user, single use, for an hour. Over
  `/mcp`, a session's calls go to one node (see the affinity notes). The MCP server's renewal of an answer when the
  user's access token ran out during a form (`needs_retry`) is not ported.
* **The remembered build** (`remember_build`) is kept per session (a chat, or an MCP session) in this node's memory,
  rather than per user: a call it can't help is told to name the build.
* **Parts ported later** are bound with defaults until they are: `SampleProfiler` (sample profiling: until then
  start_onboarding with sample text refuses, and the plan has no sample format, so its converter step reads as not
  needed) and `MappingDrift` (whether an XSLT is still what its mapping generates: until then the checks before
  promotion find no drift). A port binds its own with
  `OptionalBinder.newOptionalBinder(binder(), X.class).setBinding()`.
* **Over `/mcp`, a write call is approved** by the endpoint before the tool runs (`McpApprovals`), and a tool's own
  confirmation is then asked as well.
