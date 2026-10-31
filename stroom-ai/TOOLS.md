# Stroom AI tool inventory

The reference for every tool Stroom AI gives an agent, for maintainers extending it: what each tool does, its
contract (arguments, results, refusals), the checks it makes, the classes that implement it, and why it behaves as
it does where that isn't obvious. How to use Stroom AI is in the user and administrator documentation
(stroom-docs), not here.

There are 107 tools, in two modules:

* **Built-in tools** (56): `stroom-ai/stroom-ai-impl`, package `stroom.ai.impl.tools`, bound in
  `BuiltInToolsModule`. Documents, search, streams and processing diagnosis, administration, the chat's workspace,
  and proposals the user applies with a button.
* **Build tools** (51): `stroom-ai/stroom-mcp-impl`, package `stroom.mcp.impl.tools`, bound in `BuildToolsModule`.
  They explore the environment and build Stroom content: feeds, translations, pipelines, indexes, processor filters
  and their documentation, in a build folder, promoted once the user approves. Their shared helpers that don't
  depend on Stroom's services are in `stroom-ai-impl` (`stroom.ai.impl.tools.sample`, `validation`, `draft`,
  `generation`, `indexing`, `fielddoc`, `cef`).

Both sets are served the same way: to the "Ask Stroom AI" chat, and to MCP clients on Stroom's `/mcp` endpoint
(`stroom.mcp.impl.McpTools`), except the chat-only tools. Each runs as the signed-in user, with their permissions.

Contents:

* [How a tool is made and offered](#how-a-tool-is-made-and-offered)
* [Built-in tools](#built-in-tools)
* [Build tools](#build-tools)
* [Choosing between tools that overlap](#choosing-between-tools-that-overlap)
* [Documents, folders and instructions](#documents-folders-and-instructions)
* [The chat and its workspace](#the-chat-and-its-workspace)
* [Data tools on workspace files](#data-tools-on-workspace-files)
* [Date and time](#date-and-time)
* [Search: data sources, queries and events](#search-data-sources-queries-and-events)
* [Streams and processing](#streams-and-processing)
* [Administration](#administration)
* [Proposals: changes the user applies](#proposals-changes-the-user-applies)
* [Dashboards, extractions and visualisations](#dashboards-extractions-and-visualisations)
* [Exploring the environment](#exploring-the-environment)
* [Builds and onboarding](#builds-and-onboarding)
* [Samples, surveys and feeds](#samples-surveys-and-feeds)
* [Processing a build's pipelines](#processing-a-builds-pipelines)
* [Stepping and proving fixes](#stepping-and-proving-fixes)
* [Checking XSLT and events](#checking-xslt-and-events)
* [Pipelines](#pipelines)
* [Translations: mappings, XSLTs, text converters and dictionaries](#translations-mappings-xslts-text-converters-and-dictionaries)
* [Documentation, CEF and coverage](#documentation-cef-and-coverage)
* [Indexing](#indexing)
* [The build layer](#the-build-layer)
* [Sample analysis (`stroom.ai.impl.tools.sample`)](#sample-analysis-stroomaiimpltoolssample)
* [The validation core (`stroom.ai.impl.tools.validation`)](#the-validation-core-stroomaiimpltoolsvalidation)
* [Safety rules for XSLT, regexes and streams](#safety-rules-for-xslt-regexes-and-streams)
* [The draft mapping core (`stroom.ai.impl.tools.draft`)](#the-draft-mapping-core-stroomaiimpltoolsdraft)
* [The translation generator, the reader and mapping changes (`stroom.ai.impl.tools.generation`)](#the-translation-generator-the-reader-and-mapping-changes-stroomaiimpltoolsgeneration)
* [Field-mapping documentation and the CEF mapping (`fielddoc`, `cef`)](#field-mapping-documentation-and-the-cef-mapping-fielddoc-cef)
* [The indexing core (`stroom.ai.impl.tools.indexing`)](#the-indexing-core-stroomaiimpltoolsindexing)
* [Shared helpers](#shared-helpers)
* [Bundled knowledge: guides, conventions and the build instructions](#bundled-knowledge-guides-conventions-and-the-build-instructions)

## How a tool is made and offered

Every tool implements `stroom.ai.impl.tools.StroomTool`. Its contract:

* **`definition()`**: name, description and JSON schema of the arguments. A description is made of general and
  chat-only parts (`ToolDescription`): the chat's model is shown every part, and `ToolDefinition.general()` leaves
  out the chat-only ones (other tools' names, the workspace, `out` files, the attached tab) for a client over
  `/mcp`. `TestBuiltInToolDescriptions` fails if a general part mentions any of those.
* **`category()`** (`stroom.ai.shared.AiToolCategory`): core, search, content, indexing, processing or
  administration. Tool policies grant categories, each for reading only or for reading and writing
  (`ToolGrants`). A category not granted offers none of its tools; a read-only grant offers only the tools that
  only read, and those with calls that only read (whose writing calls are then refused).
* **`isReadOnly()`**: true unless the tool can change something. It maps to MCP's `readOnlyHint`. A tool with some
  calls that only read overrides `isReadOnly(ToolCall)` and `hasReadOnlyCalls()` (e.g. `memory`'s reads,
  `build_data_splitter` without `save_as`). Only read-only calls run while the user is away (an unattended run).
* **`permissions()`** (`ToolPermissions`): the app permissions without which the tool refuses (requires), those
  that unlock parts of its output (unlocked by), the document permissions it checks on what it reads or changes,
  and a note. This is metadata only: it is not shown to the model and enforces nothing. Each tool makes its own
  checks, and Stroom's services make theirs.
* **`readsUntrustedContent()`**: true (the default) if the output could hold instructions planted by someone other
  than the user. It maps to MCP's `openWorldHint`. Once a chat has read such content, a change that outlives the
  chat needs the user's approval on an approval card (`changesNeedApprovalAfterUntrustedContent()`). Only these
  return false: `ask_user`, `delete_file`, `get_current_date`, `get_current_datetime`, `get_current_time`,
  `get_utc_time_offset`, `list_visualisations`, `memory`, `str_replace`, `task_list`, `wc` and `write_file`.
  `propose_query`, `propose_dashboard` and `validate_stroomql` return true because they name the data source as
  Stroom holds it; `read_turn` and `search_history` because they copy the chat's stored tool results.
* **`confirmsItself()`**: the tool asks the user in a form before every change it makes (the build layer's
  `Consent`), so the call isn't also approved as a whole first.
* **`changesOnlyItsBuild()`**: the tool can only change what is in a build's folder (`WriteGuard`). Nothing outside
  it changes until `promote_build`, which asks the user itself, so such a call isn't approved as a whole either.
* **`usesRunSlot()`**: a heavy call (a search, stepping, reading many streams) takes one of the user's run slots
  over `/mcp`, as a chat run does, given back while it waits for the user.
* **`isChatOnly()`**: the tool works on the chat itself (the attached tab, its history, its task list, a button);
  `/mcp` never offers it.
* **`boundsItsOutput()`**: the tool limits what it returns itself (a page of a file), so its result is never
  saved to the workspace in its place.

Where a tool is offered:

* **Modes**: in Agent mode a chat is offered every granted tool; in Ask and Plan modes only those whose
  `isReadOnly()` is true (`McpAgentChat`). `golden/tools/catalogue.txt` (`stroom-ai-impl` test resources) lists
  the built-in tools offered in each mode, with each description and schema as the model sees it.
* **Groups**: tools only some questions need are offered as groups (`BuiltInToolGroups`, `ToolGroups`): the model
  is given one `activate_<group>` tool per group naming the group's tools, and once it calls it, the group's tools
  are offered from its next step. The 103 tools' definitions took about 51k tokens of a 131k context window
  before the question, so a long question had its steps shortened at every step. The groups are `build`,
  `processing`, `indexing`, `dashboards` and `administration` (the tables below give each tool's). A tool in no
  group is always offered. A dashboard tab's guidance turns on `dashboards` from the start
  (`BuiltInToolGroups.neededBy`). A group none of whose tools the user may use isn't offered. `activate_*` are
  not tools of their own: they are made by `ToolGroups` and are not in the tables.
* **Over `/mcp`** (`McpTools`): a session is offered the tools its user's policies grant, except the chat-only
  ones. A call that changes something needs a client that can show forms (MCP elicitation) and is refused
  otherwise (`FormsRequired`); it is approved by the user first (`McpApprovals`) unless the tool confirms itself
  or changes only its build. A call runs within the longer of the session's tool and query time limits
  (`ToolDeadline`), less any time it waits for the user. Every call is audited. An MCP session has a workspace of
  its own (`McpWorkspaces`), as a chat does, so the workspace tools work there.

Adding a tool: implement `StroomTool` (extend `AbstractWorkspaceTool` if it saves large results or `out` files),
bind it in `BuiltInToolsModule` or `BuildToolsModule`, give it a one-line summary in `ToolSummaries` (what an
administrator choosing a policy sees; `TestToolSummaries`), put it in a group in `BuiltInToolGroups` or leave it
always offered (`TestBuiltInToolGroups`), add its row to the right table below (`TestToolInventory` fails if the
built-in table and the registered built-in tools differ, by name or read-only flag), and accept the golden
catalogue's change (`GoldenFiles`: run the tests with `-DtestUpdateGoldenFiles=true`). `TestBundledKnowledge`
fails if a guide, the server instructions or a tool's description names a tool that doesn't exist (seen in testing:
hints still sent the agent to `find_documents`, `find_streams` and `describe_document`). It also keeps a list of names
Stroom's tools don't have, whose work built-in tools do, and fails if a tool takes one: `find_documents`
(`folder_contents name=`, or `find_in_content`), `describe_document` (`read_document`), `find_streams` and
`describe_stream` (`stream_meta`), `read_stream` (`fetch_events streamId=`), `find_events` (`search_events index=`),
`describe_pipeline` and `survey_index`.

## Built-in tools

Columns: **Category** (`category()`); **Read-only** (`isReadOnly()`); **Group** (`BuiltInToolGroups`; blank if
always offered); **Requires**, **Unlocked by**, **Documents** (`permissions()`); **Flags**: *chat* (chat only),
*slot* (takes a run slot), *trusted* (`readsUntrustedContent()` false), *ro calls* (some calls only read).

| Tool | What it does | Category | Read-only | Group | Requires | Unlocked by | Documents | Flags |
|------|--------------|----------|-----------|-------|----------|-------------|-----------|-------|
| `ask_user` | Asks the user questions in a form, with options to pick, and waits for the answers | core | yes | | | | | trusted |
| `config_properties` | Searches, gets and lists system properties, with overrides and their sources; secrets masked | administration | yes | administration | Manage Properties | | | |
| `current_results` | Snapshots the results table of the attached query or dashboard tab as a workspace table | search | yes | | | | Use | chat |
| `delete_file` | Deletes a file in the workspace's `/outputs/` | core | no | | | | | trusted |
| `describe_data_source` | A data source's fields, conditions, documentation and AGENTS docs, and an index's searches that would mislead | search | yes | | | | View, Use | |
| `document_documentation` | A document's Documentation tab | core | yes | | | | View | |
| `document_info` | A document's type, path, tags, history, version and dependencies | core | yes | | | | View | |
| `document_links` | Follows dependency links (dependants or dependencies) from documents | core | yes | | | Manage Processors | View | |
| `elastic_index_templates` | An Elastic Index's index template and its component templates, read from its cluster | indexing | yes | indexing | | | View | |
| `explain_processing` | Why processing isn't happening, or is slow, link by link from nodes and jobs to each filter's tasks and priority | processing | yes | processing | Manage Processors | Manage Jobs, Manage Nodes, View System Info | View | |
| `explain_retention` | How long data is kept and why: retention rules in order, the rule keeping a feed or stream, what would go now | processing | yes | processing | | Manage Policies, Manage Jobs | | |
| `fetch_events` | Fetches the events behind StreamId/EventId rows into an XML file, or reads a stream's records | search | yes | | View Data | | | slot |
| `find_in_content` | Finds text in documents' content (XSLTs, converters, dictionaries, ...) | core | yes | | | | View | |
| `folder_contents` | An Explorer folder's documents, streams, processor filters and task counts; finds documents by name | core | yes | | | Manage Processors | View | |
| `get_current_date` | Today's date in UTC | core | yes | | | | | trusted |
| `get_current_datetime` | The current date and time in UTC | core | yes | | | | | trusted |
| `get_current_time` | The current time of day in UTC | core | yes | | | | | trusted |
| `get_instructions` | The AGENTS docs that apply to folders or documents, or a bundled working guide | core | yes | | | | View | |
| `instruction_evidence` | What the user's own recent chats say about how they want things done: repeated and mixed form answers, and their messages | core | yes | build | | | | chat |
| `propose_instructions` | Offers standing instructions (an AGENTS doc) for a folder as a diff: applied to the doc's editor, or created and saved | content | yes | build | | | View | chat |
| `get_utc_time_offset` | A time zone's offset from UTC | core | yes | | | | | trusted |
| `grep` | Finds lines of a workspace file containing some text | core | yes | | | | | |
| `jq` | Runs a jq query on a workspace JSON file | core | yes | | | | | |
| `list_data_sources` | Lists the data sources the user can query | search | yes | | | | View, Use | |
| `list_visualisations` | The charting libraries installed, the chart kinds each draws, and the Visualisations in Stroom | content | yes | dashboards | | | View, Use | trusted |
| `ls` | Lists the workspace's files | core | yes | | | | | |
| `memory` | Reads and changes the user's and the chat's memory | core | no | | | | | chat, trusted, ro calls |
| `pipeline` | Chains data tools over workspace files | core | yes | | | | | |
| `preview_extraction` | Tries a draft search extraction for the attached dashboard's table on some of its events, by stepping | content | yes | dashboards | Stepping, View Data | | View | chat, slot |
| `processor_tasks` | Processing failures and why, backlogs, task lists and counts across the cluster | processing | yes | processing | Manage Processors | View Data | | |
| `propose_code_change` | Proposes a change to the XSLT or converter being stepped, tried by stepping | content | yes | | | | View | chat |
| `propose_dashboard` | Offers a button to create a dashboard from StroomQL | content | yes | dashboards | | | Use | chat |
| `propose_dashboard_change` | Proposes changes to the attached dashboard's tables and components | content | yes | dashboards | | | View | chat |
| `propose_documentation_change` | Proposes a change to a document's Documentation tab, reviewed as a diff | content | yes | | | | View | chat |
| `propose_extraction_pipeline` | Offers a button to create, or change, the search extraction for the attached dashboard's table | content | yes | dashboards | | | View | chat |
| `propose_query` | Offers a button to create a StroomQL Query document | content | yes | | | | Use | chat |
| `propose_query_change` | Offers a button to change the query in the attached tab | content | yes | | | | View, Use | chat |
| `propose_visualisation` | Plans a chart of the attached table, checks its rows can make it, and offers a button to create it | content | yes | dashboards | | | View, Use, Edit | chat |
| `read_document` | A document's content; a pipeline as Stroom runs it, an XSLT's translation, an index's survey | core | yes | | | Manage Processors | View | |
| `read_file` | Reads lines of a workspace file | core | yes | | | | | |
| `read_turn` | Reads a whole earlier turn of the chat | core | yes | | | | | chat |
| `run_stroomql` | Runs a StroomQL query into a workspace table, refusing a search on an index whose answer would mislead | search | yes | | | | | slot |
| `search_events` | XPath, text or jq search over fetched events into a table; or, given an index, finds events as a dashboard would | search | yes | | | | Use | slot |
| `search_history` | Searches the chat's history for text | search | yes | | | | | chat |
| `server_tasks` | The tasks running on every node now (Monitoring > Server Tasks) | administration | yes | administration | Manage Tasks | | | |
| `str_replace` | Replaces text in a workspace file | core | no | | | | | trusted |
| `stream_meta` | Finds streams and their meta, grouped and aggregated if asked; describes one stream's family | processing | yes | | | View Data | View | |
| `summarise_column` | Reduces a free-text column to patterns with counts | core | yes | | | | | |
| `table` | Queries a workspace table with Stroom expressions | core | yes | | | | | |
| `task_list` | Keeps the task list the user sees | core | yes | | | | | chat, trusted |
| `validate_stroomql` | Checks a StroomQL query without running it | search | yes | | | | Use | |
| `volumes` | The data and index volume groups, whether each can be written to, each volume's state and space | administration | yes | administration | | Manage Volumes | | |
| `wc` | Counts a workspace file's lines and bytes | core | yes | | | | | trusted |
| `write_file` | Writes a file in the workspace's `/outputs/` | core | no | | | | | trusted |
| `xpath` | Runs XPath on a workspace XML file | core | yes | | | | | |
| `xslt_lookups` | The reference data lookups XSLTs make, and which loaders write each map | content | yes | | | | View | |

`run_stroomql` checks no permission itself: Stroom's query service checks Use on the data source when the search
runs. `preview_extraction` and `propose_extraction_pipeline` work on the dashboard attached to the chat, so they are
chat only: offered over `/mcp`, they always failed there. `xslt_lookups` works over `/mcp` too, reading XSLTs as
saved.
`elastic_index_templates` is also checked by Stroom for Read on the Elastic Index document, and reads with
the document's cluster connection, whose credentials need the privileges to read templates.

## Build tools

The same columns, plus two flags: *confirms* (`confirmsItself()`) and *build* (`changesOnlyItsBuild()`). Every
document permission listed is checked as the user by Stroom's explorer and stores as well; a tool that creates a
document in a build folder also needs Create there.

| Tool | What it does | Category | Read-only | Group | Requires | Unlocked by | Documents | Flags |
|------|--------------|----------|-----------|-------|----------|-------------|-----------|-------|
| `build_data_splitter` | Writes a Data Splitter text converter from a spec or the sample, dry-run on the sample; saves it with `save_as` | content | no | build | | View Data | View, Edit | build, ro calls |
| `build_reference_xslt` | Writes the XSLT of a reference data pipeline from a mapping | content | yes | build | | | View | |
| `build_status` | A build's plan, step by step, and the next step | content | yes | build | | Manage Processors, View Data | View | |
| `build_translation_xslt` | Writes an event-logging translation XSLT from a field mapping; saves it with `build` and `name`, or `uuid` | content | no | build | | View Data | View, Edit | slot, build, ro calls |
| `check_events` | Validates Events against the event-logging schema, with quality checks | content | yes | build | | View Data | View | slot |
| `check_index_template` | Checks an Elasticsearch index template against what the indexing pipeline writes | indexing | no | indexing | Stepping | | View, Edit | slot, confirms |
| `check_xslt` | Static checks of an XSLT, then compiles it as Stroom does | content | yes | build | | | View | |
| `compare_outputs` | Steps records through a pipeline and a changed version of it, and compares the outputs field by field | processing | yes | processing | Stepping | | View | slot |
| `copy_pipeline` | Copies a pipeline and the documents it owns into the build, as a new version or working copy | content | no | build | | | View, Edit | confirms |
| `create_feed` | Creates a feed in the build folder | content | no | build | | | View, Edit | confirms |
| `create_index_doc` | Creates a Lucene or Elastic Index document | indexing | no | indexing | | | View, Edit | confirms |
| `create_indexing_pipeline` | Creates an indexing pipeline from a template, with an XSLT saved from the index plan | indexing | no | indexing | | | View, Edit | build |
| `create_pipeline` | Creates a pipeline from a template | content | no | build | | | View, Edit | confirms |
| `create_processor_filter` | Creates a processor filter on a build's pipeline to process streams | processing | no | processing | Manage Processors | View Data | View, Edit | confirms |
| `describe_feed` | What a feed holds: its pipelines, kinds of event, generated documentation and index fields | core | yes | | | Manage Processors | View | |
| `describe_template` | A pipeline template's children and the contract a child meets | content | yes | build | | Manage Processors | View | |
| `draft_cef_mapping` | Drafts, reviews or saves a mapping of Events to CEF | content | no | build | View Data | Stepping | View, Edit | slot, build, ro calls |
| `draft_index_mapping` | Drafts the fields of an index from Events, as a plan the indexing tools build from | indexing | no | indexing | | View Data | View, Edit | confirms |
| `draft_translation_mapping` | Drafts a translation mapping from the sample | content | yes | build | | View Data | View | |
| `evaluate_pipeline` | Gathers the facts for a consistent evaluation of an existing pipeline, by its stage, with the report's outline | processing | yes | processing | | View Data, Stepping, Manage Processors | View | slot |
| `find_elastic_clusters` | The Elastic Cluster documents and the indexes on each, with a connection test | indexing | yes | indexing | | | View | |
| `find_pipeline_templates` | Candidate parent pipelines for a stage, by what they are | content | yes | build | | | View | |
| `find_reference_data` | Every reference data map that can be looked up, and where it is loaded from | core | yes | | | Manage Processors | View | |
| `get_field_conventions` | The field naming conventions for indexes, and the environment's indexing templates | indexing | yes | indexing | | | View | |
| `locate_event` | Traces a stream or event back to its raw record, pipeline and XSLTs, and steps it | processing | yes | processing | View Data, Stepping | | View | slot |
| `processing_status` | A pipeline's processor filters and their progress | processing | yes | processing | Manage Processors | | View | |
| `profile_sample` | Profiles raw sample data: format, fields and timestamps | processing | yes | build | | View Data | View | |
| `promote_build` | Moves a build into place, with backups, once the user approves the layout | content | no | build | | Manage Processors | View, Edit, Delete | confirms |
| `propose_index_template` | Builds the final Elasticsearch index template for the cluster admin to apply | indexing | no | indexing | Stepping | | View, Edit | slot, confirms |
| `rebuild_mapping` | Rebuilds a translation's kept mapping from its XSLT | content | no | build | | Stepping | View, Edit | slot, confirms |
| `record_source_notes` | Keeps vendor reference documentation, and notes made from it, in the build | content | no | build | | | View, Edit | build |
| `rename_index` | Changes the Elasticsearch index name the build's Elastic Index and pipelines use | indexing | no | indexing | | | View, Edit | confirms |
| `reprocess_streams` | Processes streams again through a build's pipeline | processing | no | processing | Manage Processors | View Data | View, Edit | confirms |
| `review_coverage` | Finds the records across a feed that no rule of its translation matched | content | yes | build | View Data | Stepping | View | slot |
| `save_dictionary` | Saves a Dictionary document in the build | content | no | build | | | View, Edit | build |
| `save_text_converter` | Saves a text converter in the build | content | no | build | | | View, Edit | build |
| `save_xslt` | Saves an XSLT in the build, after the checks and the hand-edit gate | content | no | build | | | View, Edit | build |
| `set_processor_filter_enabled` | Disables a processor filter on a build's pipeline | processing | no | processing | Manage Processors | | View | build |
| `set_shape_handling` | Records that some kinds of event are left untranslated on purpose | processing | no | build | | | View, Edit | confirms |
| `start_build` | Creates, or finds, a build's folder, from feeds or a name | content | no | build | | | View | build |
| `start_onboarding` | Starts onboarding a source from sample files: build folder, profile and plan | content | no | build | | | View | build |
| `step_pipeline` | Steps one record through a pipeline, element by element, optionally with draft code | processing | yes | processing | Stepping | | View | slot |
| `step_records` | Steps chosen records of existing streams through a pipeline | processing | yes | processing | Stepping | | View | slot |
| `step_sample` | Steps every record of the sample streams, with errors triaged | processing | yes | processing | Stepping | View Data | View | slot |
| `summarise_fix` | Proves a fix by stepping streams with it, and sums it up as a diff | processing | yes | processing | Stepping | View Data | View | slot |
| `summarise_streams` | Summarises the errors or events of streams | processing | yes | build | View Data | | View | slot |
| `survey_feed` | Samples a feed's streams over its lifetime, grouping records by kind | processing | no | build | View Data | | View | slot, build, ro calls |
| `update_pipeline` | Changes a pipeline the build created | content | no | build | | | View, Edit | build |
| `upload_sample` | Sends sample data to a feed, as Stroom's Upload does | processing | no | build | Import Data | | View, Edit | build |
| `verify_index` | Verifies indexed events through Stroom, and makes a verification dashboard | indexing | no | indexing | | Stepping | View, Use, Edit | slot, confirms |
| `wait_for_processing` | Waits for a build's processing to finish, and reports the output | processing | yes | processing | Manage Processors | | View | slot |
| `write_documentation` | Writes a pipeline's or index's Documentation, with its field mapping | content | no | build | | Stepping | View, Edit | slot, build |

Read-only calls: `build_data_splitter` without `save_as`; `build_translation_xslt` without `build` and `uuid`;
`draft_cef_mapping` when it saves no XSLT; `survey_feed` without `build`. `create_processor_filter`,
`reprocess_streams` and `set_processor_filter_enabled` act only on a pipeline in a build; emptying a build's own
Lucene index needs Manage Index Shards. `upload_sample`'s files (`files=`) are posted to `/mcp/upload`, which
needs Use MCP Tools.

## Choosing between tools that overlap

Several tools cover neighbouring ground. Which does what:

* **Documents**: `document_info` gives metadata (type, path, tags, history, dependencies) and finds a document by
  Explorer path; `read_document` gives content, with analysis for a pipeline, an XSLT, the event-logging schema
  and an index; `document_documentation` gives the Documentation tab. `folder_contents` finds documents by name
  (as the Explorer quick filter does) and `find_in_content` by text in their content.
* **Streams**: `stream_meta` finds streams and their meta (and, given `streamId` alone, describes one stream's
  family); `fetch_events` reads the events behind result rows, or a stream's records by position;
  `locate_event` traces one event back to its raw record and steps it.
* **Processing**: `processing_status` reports one pipeline's filters and their progress without judging them (the
  build plan's quick check); `processor_tasks` covers the whole cluster: failures grouped into failure modes,
  backlogs and task lists; `explain_processing` diagnoses why processing isn't happening, link by link;
  `summarise_streams kind=errors` triages Error streams against the error rules (`ErrorTriage`).
* **Events in an index**: `search_events` with `index` searches an index as a dashboard does and gives
  StreamId/EventId hits and the streams holding them; `run_stroomql` runs any StroomQL into a table;
  `describe_data_source` says which searches on an index would mislead. There is no `find_events` tool.
* **Reference data**: `find_reference_data` lists every map in the environment; `xslt_lookups` starts from given
  XSLTs or pipelines and analyses their lookups.
* **Feeds**: `describe_feed` describes a feed by name from its generated documentation and its index fields;
  `describe_data_source` describes any data source's fields and conditions.
* **Evaluation**: `evaluate_pipeline` gathers an evaluation's facts for any existing pipeline, by its stage, and
  the report's outline; `review_coverage` goes further into the kinds of record a feed's translation misses;
  `check_index_template` and `verify_index` check an index template and an index, and are only pointed to.
* **Fixes**: `propose_code_change` offers the stepping user a button to apply a change it has tried by stepping;
  `summarise_fix` proves a fix over every record of some streams and returns a diff with the steps to apply it.
* **Documentation**: `propose_documentation_change` offers a button to review a change to any document's
  Documentation tab and apply it, unsaved; `write_documentation` writes a pipeline's or an index's Documentation
  itself, with its field mapping, in a build.
* **Dashboards**: `propose_dashboard` offers a button and the user creates the dashboard; `verify_index` creates a
  verification dashboard in the build folder.
* **Index templates**: `elastic_index_templates` reads an Elastic Index's templates from its cluster;
  `propose_index_template` and `check_index_template` work on templates the user gives (Dev Tools requests or GET
  output), as they are for a cluster admin to apply.
* **Questions**: `ask_user` asks the user anything in a form; the build tools ask within themselves, for a
  confirmation, an approval or a choice (`Consent`).

## Documents, folders and instructions

These tools read Stroom documents as the user, through `DocumentAccess`: every document is checked for View before
anything about it is given, a document the user can't view is treated exactly as one that doesn't exist ("You
don't have permission to view this document."), and every check fails closed. Each document read is recorded in the
audit log (`DocumentAccess.logView`, `AiAuditLog`). A processor filter isn't a document, so `ProcessorFilterAccess`
applies the Processors tab's rules instead: the user needs Manage Processors and View on the pipeline (or analytic
rule) the filter runs. None of these tools is in a chat group; all are always offered.

### `document_info`

Core · read-only · checks View on the document and on each document at either end of a dependency · Classes:
`DocumentInfoTool`, `DocumentAccess`

What a document is and where it sits: type, name, UUID, a link, Explorer path, tags, who created and last changed
it and when, its version, and its dependencies (documents it uses) and dependants (documents that use it), each as
a link. The dependencies come from `DocDependencyService`; the service lets an edge through when its own check
fails, so both ends of every edge are checked for View again here and an edge with either end hidden is dropped.

A document can be given by its Explorer path rather than its UUID (`DocumentAccess.findByPath`), following only
folders the user can view. Paths are taken with or without the System root, and in the `System / Feeds / Events`
form Stroom's explorer search gives, because an agent passes those on as given.

* **Arguments**: `uuid`, or `path` instead (e.g. `/Feeds/Events/Acme/ACME-EVENTS`); `type` (optional: with `path`
  it tells apart documents of one name in a folder, with `uuid` it is checked).
* **Returns**: text, one fact a line, then `Dependencies (documents it uses): N` and `Dependants (documents that
  use it): N`, each followed by up to 50 links (`- … and N more`); "none you can see" when empty.
* **Refuses**: neither `uuid` nor `path`; a document not found or not viewable (one message for both); a `type`
  that doesn't match ("Error: <uuid> is a Feed, not a Pipeline.").
* **Notes**: it doesn't use the workspace, so it works in any session.

### `read_document`

Core · read-only · checks View on the document (and on each dictionary import); unlocked by Manage Processors (a
processor filter, and a pipeline's `## Processing` section) · Classes: `ReadDocumentTool`, `DocumentAccess`,
`ProcessorFilterAccess`, `SecretRedactor`, `KeptMapping`, `DocumentationPassages`, `MergedPipeline`,
`TranslationDescriptions`, `EventElements`, `HeldSchemas`, `DocumentAnalyses` (bound by `DocumentAnalysesImpl` in
`stroom-mcp-impl`, over `IndexSurveys` and `XsltSaves`)

The content of a document, given its UUID. For any type whose handler is `ContentIndexable` it gives the text parts
by name (`## Part 'xsl'`, `## Part 'json'` and so on), with credentials redacted (`SecretRedactor`: the fields
`apiKeySecret`, `password` and `caCertificate` given as `<redacted>`, wherever they are in the JSON). A type with no
text parts says so and points at `document_info`. Content larger than the session's characters per read
(`AiWorkspaceConfig.maxCharsPerRead`, 20,000 by default) is saved part by part under
`/documents/<uuid>/<part>.<ext>` (`.xsl`, `.xml`, `.json`, `.js` or `.txt`), and the reply lists each file with its
size and line count and a preview of the first part (at most 30 lines or 1,500 characters).

Depending on the type, sections are added after the parts. Each JSON section is shown when it is at most half the
characters per read, and otherwise saved to `/documents/<uuid>/<name>.json` with a line saying where:

* **Pipeline**: `## Pipeline`, how Stroom runs it, from `MergedPipeline.description()`: `pipeline` (uuid, name,
  description), `template_chain` (root template first), `chain` (element ids in processing order, `Source` left
  out), `elements`, `links`, `properties` (each `element`, `name`, `value`, and `from`, the layer that set it),
  `references`, `removed_by_this_pipeline` (what its own layer removes from its template, by section),
  `own_elements` (elements it sets itself rather than inherits unchanged) and `translation_docs` (the XSLTs and
  text converters it runs, each with `set_by` and `inherited_from_template`). If the pipeline or a template it
  inherits from can't be read, or Stroom can't merge them, the section says so rather than guess. Then
  `## Processing`: its processor filters, each with its id, UUID, what it selects and its state
  (`ProcessorFilterAccess.selection` and `status`), or "No processor filters: this pipeline processes nothing on
  its own". This section is there because an agent asked about a pipeline otherwise searched by keyword for what
  it processes. Without Manage Processors the section says the filters need it.
* **XSLT**: `## Translation`, what it does (`TranslationDescriptions.describeTranslation`): for a translation,
  `mapping_counts` and `mappings` (each output element or attribute with its `source` and `kind`: value, attribute
  or constant), `input_fields`, `imports`, `dictionaries` and `lookups`; for an indexing XSLT, `kind: indexing`,
  `field_count` and `index_fields` (each field and its source). An XSLT that isn't well-formed gives "It couldn't be
  described: ...". Then the kept mapping (see below).
* **Elastic Index or Lucene Index**: `## Survey`, what the index holds, read through Stroom
  (`DocumentAnalyses.surveyIndex`): `index`, `backend`, `index_name`, `time_field`, `fields`, `surveyed_fields`,
  `documents_sampled`, `sampled_from`, `earliest`, `latest`, `populated` and `values` per field, `fed_by` (the
  pipelines that feed it, each `plan` reduced to whether it keeps an index plan) and `errors`. It samples up to 100
  documents, from the last 30 days when there are any, unsorted (a sorted search makes Stroom read every document:
  the newest 100 of a large Elasticsearch index took a minute a search), with the sample's searches waiting 10
  seconds in all. The sampled documents themselves are left out, and so are the whole index's totals, as counting
  them reads every document (three minutes on a large index before the agent could go on); `write_documentation`
  counts them. A cluster that can't be reached gives "Could not survey the index through Stroom: ..." and the doc
  itself is still returned, as its settings are worth having. Without `stroom-mcp-impl`, there is no survey.

Some types are read differently:

* **Dictionary**: its words as Stroom uses them, combined with those of the dictionaries it imports
  (`WordListProvider.getCombinedWordList`: each import's words first, duplicates removed), each import's words
  marked `(from Dictionary '...')`. The imports are walked first (up to 20 deep, repeats and cycles noted), because
  Stroom silently leaves out an import the user can't view: each one left out is listed, and the agent is told the
  list is incomplete and to say so. Each import read is logged as viewed. Too many words are saved as a table,
  `/documents/<uuid>/words.md`, with a Source column, for the `table` tool. `part='own'` gives only its own words.
  In a chat, while the dictionary is the tab the user attached with unsaved changes, its own words are as edited
  there (the imports as saved), unless the edited text was too large to share, which it says.
* **Documentation**: with `find`, only the passages that mention it (`DocumentationPassages.passages`, as JSON:
  `characters`, `find`, `matches`, `passages` each with `lines` and `text`, at most 30,000 characters, and a `note`
  if there are more). A Documentation doc too large to show is given with its outline (headings of levels 1 to 4,
  at most 300) and told to use `find`, because a vendor manual is too long to read whole.
* **Event-logging XMLSchema with `element`**: what the element takes (`EventElements.describe` over
  `HeldSchemas`), as JSON: `path`, `schema_version`, `description`, and for a leaf `type` and `values`, otherwise
  `children` (each with `required`, `repeatable`, `choice`, `type`, `values`, `description` as they apply) and
  `choices`. The XSD itself is over 100 KB: an agent once spent twelve minutes reading it with PowerShell. With
  `type='XMLSchema'` and no `uuid`, the newest event-logging version this Stroom holds is used. Without `element`,
  an XMLSchema is read as any document (its XSD, saved to the workspace when large).
* **Processor filter UUID**: when the UUID isn't a document, it is tried as a processor filter: the pipeline it
  runs (as a link, with its path), whether it is deleted, what it selects, its state (enabled, priority, progress)
  and its task limit.

An XSLT's kept mapping (`KeptMapping`) lives in its description, hidden in an HTML comment. It is taken out of the
parts and given as `## Pending changes` (the build's changes not yet applied) and `## Kept mapping`, a summary:
for a translation `kind`, `schema_version`, `input`, `rules` (a line a rule), `common_entries`, `extractions`,
`style` and `how` to change it; for other kinds, their fields with lists given as their sizes. `mapping=true`
gives the whole mapping instead. A summary is the default because a whole mapping (1,400 lines for one
translation) was spilled by a client to a file, read in parts and sent back whole to regenerate the XSLT. A
summary over half the characters per read (a CEF plan's 15 KB, mostly its not-sent list) is saved to
`/documents/<uuid>/mapping.json`. An XSLT saved by the build tools with a mapping that no longer reads (deleted from
its Documentation tab, or edited so it doesn't parse) gets `## Kept mapping` `{"lost": ...}` instead, saying the
XSLT is now treated as hand-written and that `rebuild_mapping` recovers it (`DocumentAnalyses.lostMapping`, which
checks the XSLT's kept-mapping tag through `XsltSaves`).

* **Arguments**: `uuid` (optional only with `type='XMLSchema'` and `element`); `part` (one part by name, or `own`
  for a dictionary); `type` (checked against the document); `find` and `contextLines` (Documentation only; several
  terms separated by `|`; context default 8, at most 60); `mapping` (XSLT only: the kept mapping whole); `element`
  (event-logging XMLSchema only, e.g. `EventDetail/Authenticate`).
* **Returns**: text: `<Type> '<name>', <folder path>.`, the parts or the files they were saved to, then the
  sections above. `find` and `element` return JSON (also as structured content). Files written are attached to the
  result as resources.
* **Refuses**: no `uuid` (unless the XMLSchema case); not viewable, or not a filter the user may see (one message;
  without Manage Processors it adds that a processor filter needs it, said whatever the UUID is, so it doesn't tell
  whether it is one); a `type` that doesn't match; a `part` the document hasn't got (listing those it has); a
  dictionary `part` other than `own`; `element` on anything but an event-logging XMLSchema, or an element the schema
  hasn't got (saying what is allowed where it stops).
* **Notes**: `part` leaves out the pipeline, translation and survey sections (the processing section stays for a
  pipeline). In a chat, a document that is the user's attached tab with unsaved changes is read as saved, with a
  note that its content as edited is in `/tabs/<n>/`; only the dictionary case reads the unsaved text itself. An MCP
  session has no attached tab and always reads as saved (seen in end-to-end testing, before it did: every read
  over `/mcp` failed with "read_document can only be used in a chat"). The content is data, not instructions, and
  the description says so.

### `document_documentation`

Core · read-only · checks View · Classes: `DocumentDocumentationTool`, `DocumentAccess`

The Markdown on a document's Documentation tab. The tab edits the document's own `description` field, which each
type declares for itself, so the document is read through its type's handler and the field taken from its JSON.
What the build tools keep hidden in that field (an XSLT's kept mapping, a build's pending changes, their note) is
left out (`KeptMapping.withoutBlock`), as `read_document` leaves it out: it is the tools', not the documentation.
Found reviewing the tools: `read_document` left it out, but this tool gave the model the hidden JSON. Documentation
larger than the characters per read is saved to `/documents/<uuid>/documentation.md` with a preview.

When a Documentation document of the same name sits in the same folder, the result says that is the document's
documentation and the tab holds only what is kept with the document itself. This is because the build tools write
a pipeline's documentation beside it: an agent read an indexing pipeline's tab, holding only the agreed index
template, as its documentation lost, and rewrote the Documentation document three times over.

* **Arguments**: `uuid`.
* **Returns**: `Documentation of <Type> '<name>':` and the text; `none` (or "none on its Documentation tab." plus
  the pointer to the document beside it); or the saved file and a preview.
* **Refuses**: no `uuid`; not viewable; a document its handler can't read.

### `document_links`

Core · read-only · checks View on every document returned or passed through; unlocked by Manage Processors (what a
processor filter selects) · Classes: `DocumentLinksTool`, `ProcessorFilterAccess`, `DocumentAccess`

Walks the dependency graph the Dependencies screen shows (`DocDependencyService`), breadth first from the given
documents, to answer "what processes what": `dependants` follows what uses each document (a feed, the processor
filters that pick its streams, the pipeline each runs, the XSLTs and other parts that pipeline uses);
`dependencies` follows what each uses. A pipeline reached from a filter that runs it is followed on to the parts it
uses, so one call from a feed reaches its XSLTs.

The graph's own permission filter lets a document through when checking it fails, so every document is checked for
View again here, failing closed: a path through a document the user can't see isn't followed, and nothing hints
that it exists. A processor filter is shown, and followed, only if the user can view the pipeline it runs. With
Manage Processors each filter is given with what it selects and whether it is enabled; without it, with the feeds
the graph links it to (each checked for View) and that what it selects needs Manage Processors.

* **Arguments**: `uuids` (required); `direction` (`dependants`, the default, or `dependencies`); `depth` (default
  2, 1 to 4); `types` (types to return; others are still followed); `limit` (default 100, at most 500); `out` (a
  file in `/outputs/` for the whole list).
* **Returns**: a summary line (direction, starting documents, depth, how many found, those not found), then one line
  per document: type, name, UUID, folder, how many links away, and the chain that reached it (e.g. `Feed 'X' → used
  by Processor filter → runs Pipeline 'P'`).
* **Refuses**: no `uuids`; none of them found or viewable; an unknown `direction`.
* **Notes**: edges are read 20 documents at a time, at most 10,000 a batch; the walk stops after 5,000 documents
  and says so. The graph holds only the links each document names (a filter that picks streams by type alone has no
  link to a feed), and the description tells the agent not to conclude from a missing link. The search is audited
  (`AiAuditLog.documentsSearched`).

### `folder_contents`

Core · read-only · checks View on the folder and everything listed; unlocked by Manage Processors (processors and
tasks) · Classes: `FolderContentsTool`, `DocumentAccess`, `ProcessorFilterAccess`, `FeedAccess`,
`AgentInstructions`

What is in an Explorer folder, as its tabs show it. `show` takes any of:

* `documents` (the default): the documents and folders in it, each as a link with its tags, folders first then by
  type and name, as the Explorer sorts them; `depth` looks in the folders below too. It stops at 200 entries and
  says so.
* `streams`: how many streams its feeds received and its pipelines produced (`MetaService.getSelectionSummary`,
  which only counts feeds the user may view), their types and age range, and the 20 most recent.
* `processors`: the processor filters of its pipelines and analytic rules (first 100): what each selects, enabled,
  priority, progress.
* `tasks`: counts of their processing tasks by status, by pipeline and feed (first 500 rows of counts).

Given `name`, it finds documents instead, by name and type in the folder and every folder below it (the whole tree
without `folder`), through the Explorer's quick filter (`ExplorerService.find`, asked for at most 5,000). Stroom
also returns the folders that contain a match, so only what was asked for is kept, and each is checked for View
again.

Folders an AGENTS doc's `## Ignore` section names (e.g. an Archive) are shown but not looked in, and documents in
them are left out of a name search, each with a count and the doc that says so; a folder the user asks about
directly is listed in full even if ignored. The AGENTS docs that apply to the folder are given with the listing.

* **Arguments**: `folder` (UUID or Explorer path; omit or `/` for the top); `show`; `depth` (1 to 5, default 1);
  `types` (documents of these types; folders are still followed); `name` (`*` for everything of `types`); `limit`
  (with `name`, default 100, at most 500); `includeIgnored`.
* **Returns**: text by section; with `name`, JSON `{total, returned, documents: [{type, uuid, name, path}], hint?,
  left_out?}`, the path without the System root, as the `folder` argument takes it.
* **Refuses**: a folder not found or not viewable; an unknown `show` value; a name search Stroom fails ("the
  documents couldn't be searched").
* **Notes**: without Manage Processors the processors and tasks sections say it is needed, as their tabs do. The
  listing, and any streams listed, are audited. It doesn't use the workspace.

### `find_in_content`

Core · read-only · checks View on every document read · Classes: `FindInContentTool`, `SafePattern`,
`AgentInstructions`, `SecretRedactor`

Finds text in documents' content (XSLTs, text converters, dictionaries, scripts and every other `ContentIndexable`
type), returning each matching document with up to three of its matching lines (part, line number, the line cut at
200 characters) and how many more matched.

* Given `uuids`, it reads just those documents (the quickest, e.g. those `document_links` found).
* Otherwise it searches every document of `types` (every type when none), through Find in Content's index
  (`ContentIndex.findInContent`) when it is ready. The index is built in full the first time it is used, which can
  take minutes, so when it isn't ready the documents are read directly and a background build is started (one at a
  time) for later searches. The result says which it did.

Every candidate, from the index or not, is checked for View and confirmed against its own text with `SafePattern`
(literal unless `regex`; a regular expression is stopped at the call's time limit if it backtracks badly).
Dictionaries are searched as their combined word list, imports included. Credentials are redacted before matching,
so a search for one doesn't give it away. Documents in folders an AGENTS doc says to ignore are left out and counted.

* **Arguments**: `pattern` (required); `regex`; `matchCase` (default false); `uuids` or `types`; `limit` (matching
  documents, default 20, at most 100); `out`; `includeIgnored`.
* **Returns**: a summary (how many matched, how it searched, how many documents it read, anything left out or not
  found), then the matches; `out` writes all the matching lines to a file.
* **Refuses**: a type that has no searchable content (listing those that have).
* **Notes**: it stops reading at three quarters of the call's time limit, to return what it found, and after
  5,000 documents; either is said. The search is audited with how it searched.

### `instruction_evidence`

Core · read-only · chat only · build group · reads only the calling user's own chats · Classes:
`InstructionEvidenceTool`, `InstructionEvidence`

Evidence for standing instructions: what the user's own Stroom AI chats say about how they want things done
(asked for by the user: choices made again and again in chats were lost when each chat ended). It reads the calling
user's chats last used within `stroom.ai.instructionSuggestions.evidenceWindow` (default 90 days), newest first, at
most 200, and from each takes the answers to forms the user accepted (`ELICITATION` messages, one answer per field
given a value, titled from the form's schema) and the user's own messages. `/mcp` sessions are left out by design:
their forms are answered in the client and kept nowhere in Stroom.

Answers are grouped by question: the asking tool and the field. A question answered the same way in at least
`minChats` chats (default 2) and never another way is **repeated**; one answered different ways is **mixed**, which
is no rule; one asked in fewer chats is left out. The user's messages are given newest first, at most 60, each cut
to 400 characters.

* **Arguments**: none.
* **Returns**: text: the repeated choices (each with its answer, the chats that gave it and what the latest form
  asked), the mixed ones with each answer's count, what the user wrote, and a hint: draft rules only from repeated
  choices or from what the user said they always want, citing the chats; a preference of the user's own belongs in
  their memory, a convention for a folder's content in its AGENTS doc (read `get_instructions` first).
* **Untrusted content**: yes. Forms can quote sample data and document names, so its result counts as untrusted.
  Nothing it gives is a rule until the user applies one.

### `propose_instructions`

Content · read-only (it only offers a button) · chat only · build group · checks View · Classes:
`ProposeInstructionsTool`, `InstructionRules.problems`, `UserActions` (`apply_documentation_change`,
`create_instructions`), `UserActionService.createInstructions`, `UserActionLookupImpl.createInstructions`; in the
UI `AiDocumentationChanges`, `DocumentationPresenter.documentationTab()`

Offers standing instructions for a folder: the text of the AGENTS doc directly in it (a Documentation doc named
`AGENTS`), drafted by the agent from `instruction_evidence` or the user's own words. Nothing changes until the user
applies it from the chat's 'Review and apply' button, as with `propose_documentation_change`:

* **The folder has an AGENTS doc**: an `apply_documentation_change` against the doc's saved content. The review
  opens the doc on its own Documentation tab (its content, which `DocumentationPresenter` gives as the tab
  proposed changes are applied to) and puts the change in its editor, unsaved, for the user to save; it can be
  undone. Applying needs Edit, and is refused if the doc has been saved with other text since
  (`UserActionLookupImpl.savedDocumentation` reads a Documentation doc's content, not its description).
* **The folder has none** ("create on apply", the user's choice): a `create_instructions` button. The review
  shows the whole doc as added; applying it creates the doc in the folder with the parts the user kept and saves
  it, as the user (`ExplorerService.create` checks Create in the folder and logs it; a doc that can't be filled is
  deleted again), then opens it. Refused if the folder has an AGENTS doc by then.

Lines of the sections the tools apply themselves (`## Required imports`, `## Component templates`,
`## Documentation`) that name nothing the tools know are pointed out in the result (`InstructionRules.problems`):
the tools leave such lines out without a word, which is right for a note but not for a slip, so the agent can
propose the corrected text again.

* **Arguments**: `folder` (UUID or Explorer path), `markdown` (the whole text) or `edits` (`oldText`/`newText`
  against the existing doc; a new doc needs `markdown`), `summary` (shown with the diff).
* **Returns**: text: what the user was shown, the diff, and any lines the tools will leave out; with the button.
* **Refuses**: a folder the user can't see; `edits` for a folder with no doc; text identical to the doc's.

### `get_instructions`

Core · read-only · checks View on the AGENTS docs and on the documents named · Classes: `GetInstructionsTool`,
`AgentInstructions`, `AgentKnowledge`

The standing instructions that apply to some work: Documentation docs named `AGENTS`, the equivalent of an
AGENTS.md (`AgentInstructions`). A doc applies to its folder and everything below it, one at the top of the tree
everywhere. The docs that apply are given most general first, with their text (at most 20,000 characters in all,
cut short with a pointer to `read_document`), then the other AGENTS docs by folder so the agent knows they exist,
then any that couldn't be read and why. The agent is told to follow them, the most specific last; the user's own
request takes precedence, and no instruction lifts an approval.

Given `guide`, it reads one of the working guides bundled with Stroom instead (`AgentKnowledge`, bound by
`stroom-mcp-impl`, which serves the same guides to MCP clients as `stroom://guide/<name>` resources); `guide='guides'`
lists them with their titles.

* **Arguments**: `folders` (UUIDs or Explorer paths), `uuids` (documents), `feeds` (feed names), `docs` (each
  `{type, name, uuid?}`; one given alone, not in a list, is taken too); the folders of the documents and feeds are
  used, and `/` always. Or `guide`.
* **Returns**: text, with the JSON as structured content (`AgentInstructions.applicableInstructions`): `doc_name`,
  `instructions` (each `folder`, `uuid`, `applies_to`, `instructions`), `other_instruction_docs`,
  `unreadable_instruction_docs` (only if any) and `hint`. "No AGENTS docs apply there" when none do.
* **Refuses**: a guide that doesn't exist (listing those that do), or no guides bundled. Names not found or not
  viewable are listed, not refused.
* **Notes**: one section of an AGENTS doc is applied by the tools themselves, not only handed to the agent:
  `## Ignore` with a `- folders:` line, which `folder_contents`, `find_in_content` and `list_data_sources` leave
  out unless `includeIgnored` is given. Other rules read from AGENTS docs for the build tools are in
  `InstructionRules`. It doesn't use the workspace.

### `xslt_lookups`

Content · read-only · checks View on every XSLT, pipeline and loader read · Classes: `XsltLookupsTool`,
`XsltLookupAnalyser`

The reference data lookups XSLTs make, worked out from the XSLT itself rather than by searching its text
(`XsltLookupAnalyser`). The XSLT is parsed as XML (a DOCTYPE refused, nothing loaded from outside it); calls to
`stroom:lookup` and `stroom:bitmap-lookup` are found in every XPath-bearing attribute and attribute value template;
and the map argument is resolved: a literal, a variable or parameter followed to its definition in scope (local,
then global, then through `xsl:include` and `xsl:import`), several values (from an `xsl:choose`, or a parameter's
default and its `xsl:with-param` values), or dynamic with its constant parts. It never guesses.

With `loaders` (the default), it also finds which reference loaders write each map: it follows each XSLT's
pipelines (through the dependency graph and inheritance, up to 10 levels, child pipelines 3 levels down, at most 50
pipelines) to their effective reference pipelines (the parents' references, plus those added, less those removed),
and reads the `<map>` elements each loader's XSLT writes. It flags lookups whose map no loader writes ("NO LOADER
writes map ..."), a likely cause of empty lookups, and maps a loader writes that no XSLT in the pipeline looks up.
A nested map name (`a/b`) is checked a segment at a time.

* **Arguments**: `uuids` (XSLTs, or pipelines whose XSLTs to analyse; required); `map` (only lookups that are, or
  may be, of this map); `loaders` (default true); `out`.
* **Returns**: a summary (XSLTs, pipelines and loaders analysed, lookup count, documents not visible, includes that
  couldn't be read), then per XSLT each lookup's line, column, template, key expression and map, the chain it was
  resolved through, and its loaders or a flag; then each pipeline's unused maps.
* **Refuses**: no `uuids`. Per UUID (reported, not refused): not found, a pipeline that can't be read, a type that
  is neither XSLT nor pipeline, an XSLT that isn't well-formed XML.
* **Notes**: structure only; it doesn't read the reference data. Loaders the user can't view are counted, not
  named. In a chat, an XSLT shown in the user's attached tab (as the tab's document, or the element selected while
  stepping) is read as edited there, unsaved, when shared in full, and the report says so. Outside a chat (over
  `/mcp`) there is no tab, so every XSLT is read as saved. Found reviewing the tools: it looked for the tab through
  the session's chat id, so over `/mcp` every call that read an XSLT failed with "xslt_lookups can only be used in
  a chat".

## The chat and its workspace

### `ask_user`

Core · read-only · reads only trusted content · Classes: `AskUserTool`, `UserQuestions` (`ChatUserQuestions` in
the chat, `McpUserQuestions` over MCP), `ElicitationSchema`

Asks the user questions and waits for the answers, rather than the agent listing them at the end of its answer and
stopping. The questions are built into an MCP form schema (`AskUserTool.form`): a text field per question, its
options as `examples` (or as an `enum` when `allowCustom` is false), the recommended option as its `default`, each
answer at most 2,000 characters. In the chat (`ChatUserQuestions`), the form is shown one question at a time with
options to click; the run stops until the user answers, however long they take, and the call is made again then
with the answer. Over MCP (`McpUserQuestions`) the form is sent as an elicitation and the call waits, up to the form
timeout, with its run slot given back and its time limit paused while it waits.

* **Arguments**: `questions` (1 to 8, each `question` up to 500 characters, `options` 2 to 6 up to 200 characters
  each, `recommended`, `allowCustom` default true); `intro` (default "Some questions before I carry on").
* **Returns**: "The user answered:" with each question and its answer, marked "(in their own words)" when not one of
  the options; or that the user declined (carry on with what was recommended, saying so, or stop) or didn't answer
  (the chat stopped, they sent a message instead, or it timed out: "Don't carry on as if they had").
* **Refuses**: questions malformed or over the limits; a form too large to show as one ("ask again shorter"); and,
  without asking, when the user can't be asked (an unattended chat run, or an MCP client that can't show forms):
  "Not asked: ... Put the questions in your answer instead".
* **Notes**: options given as objects (`{"label", "description"}`, as an MCP form's titled choices) are taken, the
  description shown under the option in the chat. Its result holds only the user's answers, so it doesn't count as
  untrusted content.

### `task_list`

Core · read-only (it changes only the chat's own state) · reads only trusted content · chat only · Classes:
`TaskListTool`, `TaskListReminder`

Keeps the task list the user sees above the chat's input. Each call gives the whole list, which is stored as a
`TASK_LIST` message of the chat. `TaskListReminder` reminds the model after every few steps in which it hasn't
updated a list with steps still to do, because models write the list at the start and then forget it, leaving the
first step in progress throughout.

* **Arguments**: `tasks` (1 to 20, each `title`, cut to 200 characters and to one line, and `status`:
  `not_started`, `in_progress` or `completed`).
* **Returns**: "Task list updated: N of M done. In progress: ...".
* **Refuses**: outside a chat; an empty or over-long list, a task without a title, an unknown status.

### `memory`

Core · write (`list` and `read` only read, so a read-only tool policy still offers it and an unattended run may use
them) · reads only trusted content · chat only · Classes: `MemoryTool`, `AiMemoryService`

Lists, reads and changes the agent's memory. `/memories/<name>` is the user's memory, loaded into every chat (at
most 16 KB and 200 lines); `/memories/session/<name>` is the chat's own (at most 64 KB), which survives the chat
being compacted; at most 20 names in each. Writing or deleting existing memory needs the version last read, so a
change made since isn't lost.

A change to the user's memory outlives the chat, so it is shown as a card; once the run has read untrusted content,
or the user is away, `AiMemoryService` waits for the user's approval instead. As the service asks itself,
`changesNeedApprovalAfterUntrustedContent()` is false, so the change isn't also put on the chat's approval card.
The chat's own memory only affects the chat and can always be changed.

* **Arguments**: `command` (`list`, `read`, `write`, `append`, `replace`, `delete`); `path`; `content`; `oldText`
  and `newText` (for `replace`, the old text appearing exactly once); `version` (for `write` of existing memory and
  `delete`).
* **Returns**: what the memory service says: the list, the content with its version, or what was done (or is
  waiting for approval).
* **Refuses**: outside a chat; an unknown command; whatever the service refuses (a bad path, over the limits, a
  stale version, `oldText` not found exactly once).
* **Notes**: a call whose command can't be read counts as a change.

### `read_turn`

Core · read-only · chat only · Classes: `ReadTurnTool`

Reads a whole turn of the chat again, from the stored messages: the question, anything the user added while the
agent worked, attached tables, each tool call with its arguments, each result (or that it failed, and the
workspace file holding the full result), and the answer. It is for exact detail from a turn the model only sees as
a digest since the chat was compacted, or a result of this turn removed to save space. Only the chat the call is
made in can be read.

* **Arguments**: `turnId` (as shown in its digest, "Turn 123"); leave out for the current turn.
* **Refuses**: outside a chat; a turn the chat doesn't have.
* **Notes**: it reads untrusted content, as it copies the chat's stored tool results, which outlive the rows they
  came from if the user deletes those.

### `search_history`

Search · read-only · chat only · Classes: `SearchHistoryTool`

Finds text in the chat's questions, additions, answers, tool calls (name and arguments) and tool results, matched
literally and ignoring case, and lists each place with its turn id, what it is, and a snippet of 80 characters
either side.

* **Arguments**: `text` (at most 200 characters); `maxResults` (default 10, at most 50).
* **Returns**: `Found "<text>" in N place(s):` and the list, with how many more weren't listed; or that nothing
  mentions it.
* **Refuses**: outside a chat; no `text`, or one too long.
* **Notes**: reads untrusted content, as `read_turn` does.

### The workspace

Each session has a workspace of files under virtual paths (`ChatWorkspace`, `SessionWorkspace`, `WorkspacePath`):
rows in the database, never files on disk, and only ever the workspace of the session the call is made in. A chat
holds its own; an MCP session's is held by a hidden chat of its user, made when a call first needs it and deleted
when the session ends (`McpWorkspaces`). The areas are:

* `/attachments/` - tables the user attached, each with a `.profile.md` summary; read-only.
* `/outputs/` - large tool results, files tools write with `out`, and the agent's own files; the only area the
  agent can write.
* `/documents/` - document content too large to give in full, saved by `read_document`,
  `document_documentation` and others under the document's UUID; read-only.
* `/tabs/` - what the user saw in their attached tab when they asked, and `current_results` snapshots; read-only.
* `/memories/` - memory, held separately by `AiMemoryService` and reached only through the `memory` tool.

`WorkspacePath` checks a path strictly rather than cleaning it up: it refuses an empty, `.` or `..` part, a
backslash, control characters, a percent sign (so nothing is ever decoded) and anything outside a small ASCII set
(so look-alike Unicode can't pass as a dot or slash); at most 512 characters and 8 levels deep. Each file records the
document its content came from, and `SourceDocAccess` checks the user may still use that document (Use for a data
source, View for anything else) every time the file is read, so a file whose source the user has lost access to is
"no longer available". Files are capped at 20 MB each, 100 MB a chat and 500 MB a user (`AiWorkspaceConfig`).

Every tool that works on workspace files extends `AbstractWorkspaceTool`. A call needs a workspace (else "can only
be used in a chat"), runs within the session's tool time limit (`ToolDeadline`, 30 seconds by default; tools that
run queries take the query time limit), and turns a bad path, missing file or exceeded limit into an `Error: ...`
the model can act on. Its results are bounded by the session's lines and characters per read (500 and 20,000 by
default; `boundedLines` says how many more there were), with any line over 2,000 characters cut short. An `out`
argument writes the whole result to a file instead, checked to be writable (only `/outputs/`), so a large result
stays out of the conversation. Every file the call wrote is attached to its result as a resource. These tools say
they bound their own output (`boundsItsOutput()`), so their results are never themselves saved to the workspace in
place of being returned. `ls`, `read_file`, `grep`, `wc`, `write_file`, `str_replace` and `delete_file` are not chat
only: an MCP session has a workspace too.

### `ls`

Core · read-only · Classes: `LsTool`

Lists the workspace's files with their sizes, or a directory's; a file whose source is no longer available is
listed with why. At most 200 entries, with a count of the rest.

* **Arguments**: `path` (a directory; omit for all).

### `read_file`

Core · read-only · Classes: `ReadFileTool`

Reads lines of a workspace file, numbered from 1, each in full so code can be copied exactly; only a line too long
for a whole read is cut short.

* **Arguments**: `path`; `startLine` (default 1, or negative to count back from the end: -20 reads the last 20);
  `lineCount` (default 100, at most the lines per read).
* **Returns**: `<path> (lines a-b of N):`, the numbered lines, and how many more there are and where to read on;
  or that there is nothing from that line.

### `grep`

Core · read-only · Classes: `GrepTool`, `SafePattern`

Finds the lines of a workspace file, or of every file in a directory (at most 100), that contain some text, matched
literally unless `regex` is true. When nothing matches literally, `regex` wasn't given and the text looks like a
regular expression (e.g. `a|b`), it is searched again as one and the result says so: agents sent such patterns
again and again without `regex`, each time matching nothing. With `regex=false` given, it isn't retried, but a miss
notes that the pattern looks like a regular expression.

* **Arguments**: `path`, `pattern` (required); `regex`; `ignoreCase`; `maxResults` (default 50, at most the lines
  per read); `out`.
* **Returns**: matching lines as `[file:]line: text` (a long line notes that `read_file` shows it whole), with how
  many matched in all; or with `out`, how many were written.

### `wc`

Core · read-only · reads only trusted content · Classes: `WcTool`

Says how many lines and bytes a workspace file has.

* **Arguments**: `path`.

### `write_file`

Core · write (only the workspace) · reads only trusted content · Classes: `WriteFileTool`

Writes a file in `/outputs/`, replacing any already there. It only changes the session's own workspace, so it
needs no approval after untrusted content (`changesNeedApprovalAfterUntrustedContent()` false). As it isn't
read-only, it isn't offered in Ask or Plan mode.

* **Arguments**: `path`, `content`.
* **Refuses**: a path outside `/outputs/`; no content; over the size limits.

### `str_replace`

Core · write (only the workspace) · reads only trusted content · Classes: `StrReplaceTool`

Replaces text in a file in `/outputs/`. The text must appear exactly once, so the change is never made in the
wrong place. The file keeps the source document it had.

* **Arguments**: `path`, `oldText` (non-empty), `newText`.
* **Refuses**: a read-only area (checked before reading, so the right error is given); text not found ("Read the
  file again"); text found more than once.

### `delete_file`

Core · write (only the workspace) · reads only trusted content · Classes: `DeleteFileTool`

Deletes a file in `/outputs/`.

* **Arguments**: `path`.
* **Refuses**: a path outside `/outputs/`, or a file that isn't there.

## Data tools on workspace files

These run on files in the workspace, in code, through locked-down engines, so figures are exact and nothing the
model writes can reach outside the file. Each takes `out` to write its result to a file, which keeps the source
document of the file it read.

### `jq`

Core · read-only · Classes: `JqTool`, `SafeJq`, `SafeJqRegex`

Runs a jq query on a JSON file (one value, or one per line); each result is one line of JSON, or of text with
`raw`. `SafeJq` blocks the environment (`env`, `$ENV`), other input (`input`, `inputs`), debugging output and
modules; every built-in checks the call's time limit and counts towards 5,000,000 operations, so a query that loops
or builds something huge is stopped; queries are at most 10,000 characters. The regular expression functions
(`test`, `match`, `capture`, `sub`, `gsub`...) take Java regular expressions of up to 200 characters, matched so a
backtracking pattern is stopped at the time limit.

* **Arguments**: `path`, `query` (required); `raw`; `out`.
* **Refuses**: a file that isn't JSON ("is not JSON that can be read: ..."); a blocked function, or a query too long.

### `xpath`

Core · read-only · Classes: `XPathTool`, `SafeXPath`

Runs an XPath 3.1 expression (Saxon) on an XML file. If the document element is in a namespace and no prefix is
given for it, it is bound to `d`. Each result is one line: its text, or its XML with `asXml`. `SafeXPath` refuses a
DOCTYPE (no external entities, DTDs or entity expansion) and XInclude, refuses `doc()`, `collection()`,
`unparsed-text()` and the like by name and by resolvers that refuse everything, has no environment variables or
extension functions, and has no regular expression functions, as their matching can't be stopped part way (the
description points at `grep` and `table`). Expressions are at most 2,000 characters.

* **Arguments**: `path`, `expression` (required); `namespaces` (prefix to URI); `asXml`; `out`.

### `table`

Core · read-only · Classes: `TableTool`, `TableEngine`, `TableExpression`, `ExpressionFunctions`,
`ColumnSummariser`

Queries a table (an attachment in `/attachments/`, or a `.csv` or `.tsv` file) exactly, in code: `eval` adds columns
worked out per row, `where` filters, `groupBy` with `aggregates` (or `distinct`, with counts) summarises, `orderBy`
sorts and `limit` cuts. `mask` with `distinct` or `groupBy` counts free text as patterns, with numbers, IPs, dates
and IDs masked (`ValueMasker`). The query is typed JSON, never code or a command. Expressions are Stroom's
expression language as dashboards use it (`${column}`, Stroom's function names, e.g. `average()` not `avg()`);
`help` with function names describes those functions instead of querying. The description lists the functions by
category, and its examples are checked to work.

* **Arguments**: `path`; `select`; `eval` (`as`, `expression`); `where` (`column`, `op`, `value`; ops `=`, `!=`,
  `<`, `<=`, `>`, `>=`, `contains`, `not_contains`, `starts_with`, `ends_with`, `matches`, `is_empty`,
  `not_empty`); `groupBy`; `aggregates` (`function` count, count_distinct, sum, min, max or avg, or an
  `expression`; `column`, `as`); `distinct`; `mask`; `orderBy` (`column`, `descending`); `limit` (at least 1);
  `out` (a `.csv`); `help`.
* **Returns**: "N of M rows matched; the result has K row(s)." (and, for a `current_results` snapshot, how much of
  the search it holds, which the answer must say), then a Markdown table; with `out`, the file and the first 10
  rows.
* **Refuses**: a file of a kind that is never a table (`json`, `jsonl`, `ndjson`, `xml`, `xsl`, `xslt`, `txt`,
  `log`, `yaml`, `yml`), pointing at `jq` or `read_file` (a survey's JSON was once read as CSV and refused for a
  stray quote); malformed arguments; an expression that doesn't parse.
* **Notes**: after two failed queries of the same table in a run, the next failure summarises the column the query
  was about (`ColumnSummariser`) rather than leaving the model trying query after query. An expression error is
  never turned into a summary, as the model fixes its expression from the message.

### `pipeline`

Core · read-only · Classes: `PipelineTool`

Runs up to 10 data tool steps one after another, each on the file the step before wrote, so intermediate results
never enter the conversation. A step is `{"tool", "args"}`, with tool one of `grep`, `jq`, `xpath`, `table`,
`fetch_events`, `search_events` or `current_results`; each step is run as that tool's own call would be, with the
same checks. A step's `path` defaults to (or `$prev` names) the previous step's output; intermediate files are
written under `/outputs/pipeline/<id>/step-N.<ext>` and deleted at the end. Only the last step takes the pipeline's
`out`.

* **Arguments**: `steps` (required); `out`.
* **Returns**: one line per step (its result's first line), then the last step's result.
* **Refuses**: no steps, more than 10, a step that isn't `{tool, args}`, a tool that can't be a step; a first step
  without a path; `current_results` other than first. A failing step stops the pipeline: "Step N (tool) failed:
  ...".
* **Notes**: the call's time limit is the longest of its steps' limits, so a pipeline that fetches events or takes a
  snapshot gets theirs. A `current_results` snapshot is a tab file kept with the chat, not an intermediate file. The
  tool declares no permissions of its own; `fetch_events` and `current_results` steps make their own checks.

### `summarise_column`

Core · read-only · Classes: `SummariseColumnTool`, `ColumnSummariser`, `TableSummariser`

Summarises a free-text column (e.g. log messages). It first reduces the column in code to its distinct patterns
(numbers, IPs, dates and IDs masked) with counts, most common first, which is usually small enough to return as it
is. Only when that table is larger than the characters per read does the run's own model summarise the patterns in
batches (`TableSummariser`), and that summary is saved to `/outputs/summaries/<file>-<column>.md` and returned again
on a later call without `focus`, rather than worked out again.

* **Arguments**: `path`, `column` (required); `focus` (what to look for).
* **Returns**: the column's pattern count and the patterns table, or the summary and where it was saved.
* **Refuses**: no column; summarising outside a chat run, which needs the run's model ("Summarising needs a model,
  so can only be done in a chat"). If the model can't summarise them, it says to use `table` instead.

## Date and time

### `get_current_date`, `get_current_datetime`, `get_current_time`, `get_utc_time_offset`

Core · read-only · reads only trusted content · Classes: `GetCurrentDateTool`, `GetCurrentDateTimeTool`,
`GetCurrentTimeTool`, `GetUtcTimeOffsetTool`

The current date (`2026-10-31`), date and time (`2026-10-31T09:15:02Z`) and time of day (`09:15:02Z`) in UTC,
to the second, so the model never guesses them. `get_utc_time_offset` gives a time zone's current offset from UTC,
daylight saving included ("Europe/London is currently +00:00 from UTC."), from `timeZone` (an IANA ID or a fixed
offset such as `+02:00`), or the Stroom server's default zone when none is given; an unknown zone is refused with
the forms it takes. None takes other arguments or touches the workspace.

## Search: data sources, queries and events

These tools find data sources, check and run StroomQL, take the results the user is looking at, and fetch and
search the events behind result rows. They are in no chat group, so they are always offered. All are in category
search. The workspace tools among them (every one here) run within the session's time limit and write only to the
session's workspace (`AbstractWorkspaceTool`). Over `/mcp` the session has a workspace of its own, so they work
there too; only `current_results` is chat-only.

Data sources are found through `DataSourceAccess`. A data source needs Use, as running a query on it does. One the
user can't use is treated exactly as one that doesn't exist (`DataSourceAccess.NOT_FOUND`: "There is no data source
with that name or uuid that you can use."), so nothing reveals whether it exists. Every check fails closed.

Lucene and Elastic indexes answer some searches wrongly, with no error. `MisleadingSearches` holds what is known
about this (found against Stroom 7.13). `describe_data_source` warns of these searches, `run_stroomql` refuses such a
query, and `search_events` with `index` refuses such a search. There is no separate `find_events` tool: finding an
event in an index is `search_events` given `index`.

### `list_data_sources`

search · read-only · checks View and Use on each data source · Classes: `ListDataSourcesTool`, `DataSourceAccess`,
`AgentInstructions`

Lists the data sources the user can query: views, Lucene and Elastic indexes, Solr indexes, statistics and so on,
from `DataSourceProviderRegistry`. Each is given as `<type> '<name>' (uuid ...)`, sorted by name. When 25 or fewer
are listed, each also gets the first line of its documentation (cut at 200 characters). At most 200 are listed;
past that the result says how many more there are and to give a filter.

Data sources in folders an AGENTS doc says to ignore (e.g. an Archive) are left out unless `includeIgnored` is
given. The result counts what was left out and names the AGENTS docs that ignore it. The AGENTS docs that apply to
the folders of the listed data sources are appended (e.g. which fields to query for what). When more than one is
listed, the model is told to ask the user which to use rather than choose one.

* **Arguments**: `filter` (optional; keeps those whose name or type contains it, ignoring case),
  `includeIgnored` (optional; list ignored folders too, only when the user asks).
* **Returns**: text: the list, the ignored note, the "choose with the user" note and the AGENTS docs.
* **Notes**: the listed documents are audit-logged as viewed. Reads untrusted content, as names and documentation
  are written by users.

### `describe_data_source`

search · read-only · checks View and Use on the data source · Classes: `DescribeDataSourceTool`,
`DataSourceAccess`, `DataSourceDocumentation`, `MisleadingSearches`, `AgentInstructions`

Gives a data source's fields from Stroom's query service (`QueryService.findFields`, up to 1,000). For each field it
gives the type, whether it can be queried, its condition set, and the document type it refers to (for a doc-ref
field). It also gives the data source's time field. A name matching several usable data sources is refused with
the list, asking for the uuid. A name is matched exactly first, then ignoring case.

The result also gives the documentation that says what the fields hold, which field names often don't (e.g. that
`document.description` is a tweet's text). `DataSourceDocumentation` gathers it from these places, as the user,
checking View on each document and both ends of each dependency link:

* the data source's own Documentation tab;
* Documentation docs in its folder named after it or after a pipeline that feeds it, or that mention it (at most
  3 of those);
* each feeding pipeline's Documentation tab, and the Documentation doc beside it with its name (at most 5
  pipelines);
* for a View, the same for the index or pipeline it reads.

The pipelines that feed an index are those that depend on it. For an Elastic index they also include the pipelines
that use its cluster and write to its Elasticsearch index by name (the indexing filter's `indexName`), which name
no index doc. The documentation is capped at 20,000 characters in all and 8,000 per document. The AGENTS docs that
apply to the data source's folder are appended.

For a Lucene or Elastic index, a paragraph says which searches through Stroom would mislead and what to use
instead (`DescribeDataSourceTool.searchingNote`):

* **Elasticsearch**: STARTS_WITH and CONTAINS find nothing (use EQUALS with a `*` wildcard, or MATCHES_REGEX).
  IS_NULL and IS_NOT_NULL match every document (use EQUALS `*` for any value, NOT_EQUALS `*` for none). A wildcard
  on an ip field finds nothing (use a CIDR range). The index's ip fields are named.
* **Lucene**: STARTS_WITH and ENDS_WITH find nothing. CONTAINS finds nothing on a keyword field, and a range finds
  nothing on a text field. IN finds nothing when a value holds a space.
* **Either**: IN takes values separated by commas, and a field that isn't queryable is found by no search.

* **Arguments**: `data_source` (required; name or uuid).
* **Returns**: text: what the data source is, its time field, its fields, the searching note, its documentation and
  its AGENTS docs. The documentation and AGENTS docs are given even when no fields can be seen.
* **Refuses**: no such usable data source (`NOT_FOUND`); an ambiguous name.
* **Notes**: audit-logged as a view of the data source.

### `validate_stroomql`

search · read-only · checks Use on the query's data source · Classes: `ValidateStroomQlTool`, `DataSourceAccess`

Checks a StroomQL query with `QueryService.validateQuery`, without running it. First it finds the data source the
query's `from` names (`QueryService.getReferencedDataSource`) and checks the user can use it. On success it says
"OK", names the data source, and says whether the query groups rows. Otherwise it gives the parser's error and asks
the model to fix the query and validate it again.

* **Arguments**: `query` (required).
* **Returns**: text, as above.
* **Refuses**: a blank query; a query over `stroom.ai.workspace.maxQueryLength` characters (default 10,000); a query
  naming no data source the user can use (`NO_DATA_SOURCE`, which tells the model to check `from` against
  `list_data_sources`).
* **Notes**: each validation is audit-logged as processed, with its outcome. Reads untrusted content: the data
  source's name is a document name, and the parser's errors are not known to quote only what the model wrote.

### `run_stroomql`

search · read-only · no permission of its own: Stroom's query service checks Use on the data source when the search
runs · takes a run slot · Classes: `RunStroomQlTool`, `StroomQlLimit`, `MisleadingSearches`, `AgentSearches`,
`MarkdownTables`, `TableProfiler`

Runs a StroomQL query as the user and writes the results to a Markdown table in the workspace. The default path is
`/outputs/queries/query-<yyyyMMdd-HHmmss>.md`. The model gets the path and a profile of the table (row count,
columns, common values).

The search is never unbounded. `StroomQlLimit` reads the query with Stroom's own tokeniser, so text in strings and
comments is never taken for a keyword. It adds `limit <max>` to a query with none, and lowers a larger limit, where
max is `stroom.ai.workspace.maxQueryRows` (default 10,000). Without sorting or grouping, Stroom stops a search as
soon as it has that many rows. The result says when the limit was added or lowered. When the rows reach the cap it
warns that the row count is not a total, and to count with `count()` and `group by`.

Before running, a query on a Lucene or Elastic index is checked by `RunStroomQlTool.misleading`. The check parses
the query (`QueryService.parseQuery`), takes every enabled term at any depth, and asks `MisleadingSearches.refusal`
with the index's ip fields, its unqueryable fields and, for a Lucene index, its text fields' analyzers
(`LuceneTextFields`). A query is refused for:

* STARTS_WITH or CONTAINS on Elasticsearch;
* IS_NULL or IS_NOT_NULL on Elasticsearch, which match every document;
* EQUALS with a wildcard on an Elasticsearch ip field, with the CIDR range to use (`MisleadingSearches.cidr`:
  `192.0.2.*` is `192.0.2.0/24`);
* STARTS_WITH or ENDS_WITH on Lucene;
* CONTAINS on a Lucene keyword field (a TEXT field with the KEYWORD analyzer), with the `*value*` wildcard to use;
* a range (BETWEEN, GREATER_THAN, LESS_THAN and their OR_EQUAL_TO forms) on a Lucene text field with an analyzer,
  with a wildcard or IN to use instead;
* IN with one value holding a space and no commas, or, on Lucene, any IN value holding a space;
* a field Stroom lists as not queryable ("the index doc's field isn't indexed" on Lucene).

The refusal starts "These searches would give a misleading answer: ", says what to use instead, and adds "In
StroomQL, EQUALS is = and NOT_EQUALS is !=". If the check itself fails, the query runs, because its own errors are
Stroom's to give.

The search is a non-incremental `QuerySearchRequest` with history not stored. It is polled every 500 ms until
complete or until Stroom reports errors. The rows are then fetched with `downloadSearchResults` as Markdown, asking
for every row the cap allows, with each row's stream and event ids. `StreamId` and `EventId` columns that are empty
(a data source without streams) are dropped. The workspace file records the data source's uuid as its source.

* **Arguments**: `query` (required), `out` (optional; a `.md` file in `/outputs/`).
* **Returns**: text: "Ran the query. The results are in <path>.", any limit note, and the table's profile.
* **Refuses**: a blank or over-long query (`maxQueryLength`); a misleading search, as above. Errors from Stroom are
  given as "The query failed: ..." or, once it had started, "The query started, but failed while it ran: ...".
* **Notes**: the time limit is the session's query time limit (`stroom.ai.workspace.queryTimeLimit`, default 2
  minutes), not the tool time limit. The search is always destroyed when the call ends (`AgentSearches.destroy`),
  including on a timeout, so it never runs on after the call.
* **Lucene analyzers**: a Lucene index's query fields (`QueryService.findFields`) don't say which analyzer a field
  has, so `LuceneTextFields` reads them from the index's own field list (`IndexResource.findFields`, as the user,
  up to 2,000 fields), the TEXT fields only. If they can't be read, the check goes on without them. Found reviewing
  the tools: none were passed, so a CONTAINS on a keyword field ran and found nothing.

### `current_results`

search · read-only · checks Use on the results' data source · chat only · Classes: `CurrentResultsTool`,
`TabSnapshots`, `TableDownloader`, `ResultsRequests`, `DataSourceAccess`, `AgentSearches`

Takes a snapshot of the rows of a results table in the tab the user attached: a query's results, or a table on a
dashboard. The model can then work on the rows without the user attaching them. The rows are taken with Stroom's
own download of search results, as the user. They are kept as a hidden, read-only workspace file under
`/tabs/results/`, which only Stroom writes, with a profile beside it and a JSON record (`ResultsMeta`). The record
holds:

* a hash of the search request (query or expression, parameters, time range) and the time range;
* when the snapshot was taken and the rows it holds;
* the rows the search found, where Stroom still knows;
* whether the search had finished, and whether the global row limit cut the snapshot;
* the tab's query key.

On every call, the snapshot is compared with the tab as it now stands (`CurrentResultsTool.assess`). Its state is
one of:

* **ok**;
* **stale**: the tab's query, expression, parameters or time range have changed (`ResultsRequests.changes` says
  which);
* **incomplete**: the search hadn't finished, the global row limit cut it, or it holds fewer rows than asked for
  while more exist.

The text tells the model what to offer (run it again, with the user's yes) and to say a figure is partial.

Asking again reuses the snapshot. It is taken again, without searching, when the user has run the search again in
the tab (the query key changed). It runs the search again only with `refresh`. The description tells the model to
pass `refresh` only after the user says yes, as a search can be slow or costly. The search is run with 75% of the
time limit to finish, leaving time for the download, and is stopped once its rows are taken
(`AgentSearches.terminate`).

`withEventIds` is for the events behind the rows. A query's table, or a dashboard table that already keeps each
row's ids, is snapshotted as usual, with a note saying whether the rows have `StreamId` and `EventId`. A dashboard
table that is grouped, or lacks those columns, can only give its events by running its search again, separately
and ungrouped, one row per event (`ResultsRequests.ungroupedWithEventIds`). The user's dashboard isn't changed.
This needs `refresh`. Without it, the model is told to ask the user first. The data source must store `StreamId`
and `EventId` fields; if it doesn't, the model is told to answer from the table's columns instead. This snapshot
is kept under the table's path with `-events` added.

* **Arguments**: `table` (the table's name, for a dashboard with several; matched ignoring case), `rows` (only the
  first n, copied with their own profile), `refresh`, `withEventIds`. All optional.
* **Returns**: text: what was taken (or reused), rows held of rows found, the time taken, the time range, the
  state with reasons and notes, the path, and the profile.
* **Refuses**: no attached tab with results tables (`NO_TAB`); a dashboard with several tables and no `table`
  (lists them); `rows` below 1, or over the global row limit (`stroom.askStroomAi.tableAnalysis.maxTotalRows`,
  default 50,000), when it offers that many instead; a data source the user can't use; a search not yet run
  (offers `refresh`); results Stroom no longer holds (offers `refresh`).
* **Notes**: time limit is the query time limit. Each snapshot is audit-logged as a search of the data source.
  `CurrentResultsTool.coverage` lets other tools that work on a snapshot (e.g. `fetch_events`) say an answer
  covers "the first N of M". It can be a pipeline's first step.

### `fetch_events`

search · read-only · requires View Data · takes a run slot · Classes: `FetchEventsTool`, `StreamReader`,
`EventsFile`, `StreamCharsets`

It has two modes.

**From a table.** Given `path`, a workspace table with `StreamId` and `EventId` columns, it fetches the stored
event behind each row into one XML file (default `/outputs/events.xml`), for `search_events`. The table can be an
attached or snapshotted results table that isn't grouped, a `run_stroomql` table, or a CSV made from one. Each
event is written as `<stroom-event streamId=".." eventId="..">`, holding the stream's header, the event's segment
and the stream's footer, so each is a whole document.

Rows are read in order without repeats, up to `limit` (capped by `stroom.ai.workspace.maxEventsPerFetch`, default
500). Within each stream, events are read in stored order. Stored data is compressed in blocks, so reading out of
order would decompress the same block again. Events are segments 1 to n-2 of the stream (0 is the header, the last
is the footer). Anything larger than `stroom.ai.workspace.maxEventBytes` (default 1 MiB) isn't fetched.

The summary counts what was left out: rows over the limit, rows without ids, events that couldn't be read (a feed
the user can't see, or deleted), ids not in their stream, streams that don't hold separate events, and events too
large. When the table is a `current_results` snapshot it also says how much of the search the rows cover.

**From a stream.** Given `streamId`, it reads records of that one stream through `StreamReader.readRecords`: the
raw input, Events XML, or a child part (`childType`, e.g. `Meta Data` for the receipt headers, or `Context`). It
reads whole records within a budget of characters, not of records. When fewer records fit than were asked for,
`next_first_record` says where to read on. When record data reaches the budget, `truncated` is set.

A raw stream Stroom holds as one record is split into its records from its text, as a survey splits them
(`StreamReader.splitRaw`). Without this, reads at different offsets of a one-record stream all returned its first
records. A stream with bytes its feed's encoding can't decode is read as a pipeline reads it: each bad byte is
replaced, and `undecodable_bytes` reports what was replaced (`StreamCharsets`). With `out`, the records are
written to that file, one after another, in place of the result's `records`. The budget is then the maximum.

* **Arguments**: from a table: `path`, `limit`, `out`. From a stream: `streamId`, `firstRecord` (default 0),
  `recordCount` (default 5, at most 100), `childType`, `maxChars` (default 60,000; 1,000 to 200,000), `out`.
* **Returns**: from a table: a text summary, plus the events file. From a stream: JSON (also structured content):
  `stream_id`, `feed`, `type`, `first_record`, `total_records`, `available_child_types`, and `records` (or
  `records_read`, `out` and `note` when written to a file). It may also include `undecodable_bytes`, `truncated`,
  `next_first_record`, `hint`, and the split's `header` and `records_are`.
* **Refuses**: neither `path` nor `streamId`; no View Data ("You don't have permission to view data, ..."); a table
  without `StreamId` and `EventId` columns ("A grouped table has none; ask for the table without grouping."); a
  stream id that isn't a whole number; a stream that can't be read (`StreamReader.StreamReadException`).
* **Notes**: time limit is the query time limit. Every stream read is audit-logged as viewed. When fewer
  records fit than asked, the hint names `survey_feed`, a build tool, for reading without the character limit.

### `search_events`

search · read-only · checks Use on the index (with `index`); the raw streams `allStreams` gives are only those of
feeds the user may view · takes a run slot · Classes: `SearchEventsTool`, `IndexEvents`, `IndexSearches`,
`MisleadingSearches`, `SafeXPath`, `SafePattern`, `SafeJq`, `TableEngine`

It has two modes.

**Over an events file.** Given `path`, a file from `fetch_events`, it evaluates each `<stroom-event>` on its own
and writes a CSV (default `/outputs/search_events.csv`) with `StreamId`, `EventId` and the result. The model sees
the first 10 rows.

* `xpath` gets a value from each event. The event is made a document of its own, so the expression sees only that
  event. The event's own namespace is bound to `d` unless `namespaces` binds it. Several values are joined with
  ` | `, white space is collapsed, and values are cut at 1,000 characters.
* `text` keeps only events containing it, in the XPath value if given, otherwise anywhere in the event. It is a
  literal unless `regex` is set, and `ignoreCase` defaults to true. Without `xpath`, a snippet of about 200
  characters around the match is the result.
* `jq`, with `xpath`, parses each XPath value as JSON and applies the filter, a row per output. Values that aren't
  JSON are counted, not failed, and so are values the filter fails on (with the first failure).
* `join`, a table with `StreamId` and `EventId`, adds the result as a column to that table instead (ids matched
  without commas, several results joined with ` | `).

XPath, regular expressions and jq run through the workspace's safe wrappers (`SafeXPath`, `SafePattern`,
`SafeJq`), which bound their cost. The deadline is checked between events.

**In an index.** Given `index`, it finds events in a Lucene or Elastic index as the user would on a dashboard
(`IndexEvents`), with their permissions and nothing saved. This is for an event the user describes but can't point
at, such as one rare event among many indexed streams, which sampling would miss.

1. The index is resolved among the data sources the user can Use. Only Elastic Index and Lucene Index docs count.
   An ambiguous name is refused with the list.
2. Up to 2,000 of its fields are read. A field in `where` or `fields` that the index doesn't have is refused as
   "Not fields of index '<name>': ...", with the closest names (`CloseMatches`) and up to 60 of its fields.
3. On Elasticsearch, IS_NOT_NULL and IS_NULL are searched as EQUALS `*` and NOT_EQUALS `*`, because Stroom matches
   every document with either.
4. The terms are checked with `MisleadingSearches.refusal` (as for `run_stroomql`, including fields that aren't
   queryable) and refused with what to search instead.
5. `timeFrom`/`timeTo` become a BETWEEN on the data source's own time field (`DataSourceProvider.getTimeField`).
   Missing ends default to the epoch and `day()+1d`. An index with no time field is refused when a time range is
   given. A call with neither `where` nor a time range is refused: this finds an event, it doesn't list the index.

`IndexSearches` then runs up to three searches through `DashboardService`, as an unsaved dashboard makes them, with
history not stored:

* **hits**: `StreamId`, `EventId`, the time field (sorted newest first), the searched fields and `fields`, at most
  8 columns, each value cut at 120 characters;
* **count**: `count()` grouped on a constant, so one row holds the total. Counting rows instead stops at the first
  page and the table's maxResults: 32,000 documents were once counted as 100;
* **by stream**: grouped by `StreamId` with a count, the most matches first. This runs only when the index has
  `StreamId`. It lists 20 streams, or up to 10,000 with `allStreams`.

Each search is polled for up to 60 s (`IndexSearches.SEARCH_WAIT`), or for what the call's time limit has left
less 2 s (`ToolDeadline.remaining()`), whichever is sooner. What it has found by then is the answer, said to be
partial. A count cut off is a floor. Every search is stopped once read (`AgentSearches`): a search left behind
runs to its end even when nobody reads it. Three whole-index scans of a large Elasticsearch index were once seen
carrying on after a survey gave up on them. Stroom's errors come from its `errorMessages`. A search Stroom gives
no response to has the error "Stroom's dashboard search gave no response".

With `allStreams`, `affected` says what a translation change to these events reaches (`IndexEvents.affected`):

* the Events streams holding them;
* the raw streams they were made from: their parents, looked up 500 at a time through `MetaService`, so only
  those of feeds the user may view;
* that reprocessing those raw streams, and only those, is the user's to do;
* for Elasticsearch, the `POST <index>/_delete_by_query` request with a `terms` query on `StreamId`, and that the
  cluster's admin runs it just before reprocessing, or the index holds the events twice.

The index name for the label and the delete request is read from the Elastic Index doc with View; without View,
neither is given.

* **Arguments**: events file: `path`, `xpath`, `namespaces`, `text`, `regex`, `ignoreCase`, `jq`, `join`, `column`
  (default `value` with xpath, else `match`), `out`. Index: `index`, `where` (a list of `{field, condition,
  value}`, one such object, or the list as JSON text; `condition` defaults to EQUALS; BETWEEN takes `from,to`; IN
  takes values separated by commas; EQUALS takes `*` wildcards), `timeFrom`, `timeTo` (ISO 8601 or Stroom date
  expressions), `fields`, `limit` (1 to 20, default 10), `allStreams`.
* **Returns**: events file: a text summary ("n of m event(s) matched", any jq errors), the CSV path and a preview.
  Index: JSON (also structured content): `index` (`type`, `uuid`, `name`, `backend`), `searched`, `total`,
  `hits`, `by_stream` (at most 20 even with `allStreams`), `affected` (with `allStreams`), `errors`, and `next`.
  `next` says what to do: confirm the hit with the user and trace it with `locate_event`; or, if nothing matched,
  check the values with the user or ask for the ids from the dashboard row; or, if the index has no ids, ask for
  them.
* **Refuses**: neither `path` nor `index`; neither `xpath` nor `text`; `jq` without `xpath`; a file that isn't
  from `fetch_events`; a `join` table without the id columns; a malformed `where` or a condition dashboards don't
  take; an index the user can't use; unknown fields; misleading searches; no conditions.
* **Notes**: with `index` the time limit is the query time limit; otherwise it is the tool time limit. As for
  `run_stroomql`, a Lucene index's text fields' analyzers come from `LuceneTextFields`, so a CONTAINS on a keyword
  field and a range on a text field are refused too.

## Streams and processing

These tools find streams, look at processing tasks, explain why processing is or isn't happening, and explain data
retention. All are in category processing and in the processing chat group. Each reads as the user: streams through
`MetaService`, which only finds those of feeds the user may view. None changes anything.

### `stream_meta`

processing · read-only · checks View on the folder, pipeline or feed it is given; unlocked by View Data (values read
from the stream store) · Classes: `StreamMetaTool`, `StreamReader`, `DocumentAccess`, `FeedAccess`, `ToolTables`

Finds streams, newest first (by id), and writes a CSV of their meta to the workspace (default
`/outputs/stream_meta.csv`) for the `table` tool. The search can combine:

* `feeds`, by name or uuid; a uuid must be a Feed the user can view;
* `pipeline` (uuid): the streams it produced;
* `folder` (uuid or Explorer path): an `IN_FOLDER` match on feed or pipeline, at any depth;
* `parentId`: the streams produced from that stream;
* `type`, `from`/`to` on create time (ISO 8601 date or date-time in UTC, or a period back from now such as `P7D`),
  and `status` (default Unlocked).

It reads up to `limit` streams (default 1,000, at most 10,000). It reads one more to tell whether there are more.

Each row is a stream with its core columns and attributes. With no `attributes` given, these are the attributes
the database holds for the streams found, up to 30. Values come from the database while it keeps them, otherwise
from the stream store, which also holds values the database never has, such as receipt headers like `RemoteHost`.
Reading the stream store needs View Data, checked once and failing closed. It is capped at
`stroom.ai.workspace.maxStreamStoreReads` (default 500) streams per call and half the call's time. The summary says
how many values came from each place and how many weren't read, and why.

`parts` gives a row per part (up to 100 parts per stream), with the headers each part was received with. `groupBy`
(`day`, `hour`, `feed`, `type`, `pipeline`) with `aggregates` (`count`, or `sum`/`min`/`max` of an attribute, e.g.
`sum(Raw Size)`) gives totals instead. Grouping with no aggregates counts. A table has at most 60 columns.

Given `streamId` alone, it describes that stream as JSON instead:

* `children`: the streams produced from it, up to 100, each with `id`, `feed`, `type`, `status`, `created`,
  `parent_id` and `pipeline_uuid`;
* `children_by_type`: a count per type;
* `attributes`: the receipt headers it carries, usable in XSLT as `stroom:meta('Name')`. These are read with View
  Data; otherwise, or for an Events or Error stream that has none, `attributes_note` says why;
* `processing`: for a stream a pipeline wrote, `records_read`, `records_written`, `errors`, `warnings` and
  `fatal_errors`. This tells how many events a large stream produced without reading it. Stroom records them a few
  seconds after the stream completes, which the description tells the model.

* **Arguments**: `feeds`, `pipeline`, `folder`, `parentId`, `type`, `from`, `to`, `status`, `attributes`, `parts`,
  `groupBy`, `aggregates`, `limit`, `out`; or `streamId` alone.
* **Returns**: text: the summary, the CSV path and a preview of 10 rows. Or, for `streamId`, JSON (also structured
  content).
* **Refuses**: none of feeds, pipeline, folder, parentId, type or streamId; a folder, pipeline or feed uuid the
  user can't view or that doesn't exist; a time, status, group or aggregate it can't read; "No stream with id n".
* **Notes**: a search that finds nothing for a feed name the user can't view adds "No feed named ... was found here
  (or you can't view it): ask the user ... Don't go on until they say." This stops the work rather than reading as
  a feed with no streams (it may be another cluster's feed). Time limit is the query time limit. Searches and
  stream-store reads are audit-logged.

### `processor_tasks`

processing · read-only · requires Manage Processors; unlocked by View Data (reading Error streams) · Classes:
`ProcessorTasksTool`, `ProcessorTaskFinder`, `ProcessorFilterAccess`, `TaskErrorStreams`, `ErrorStreamMarkers`,
`FailureModes`, `ErrorMessageNormaliser`

The tool for processing across the cluster: what is failing and why, backlogs and throughput, and individual
tasks. All three modes take the same filters: `pipelines` (pipeline or analytic rule uuids), `filters` (processor
filter ids or uuids), `feeds`, `streams`, `nodes`, `statuses`, `since` and `until`. Times are ISO 8601, a period
back (`P7D`, `PT6H`, `-7d`, `-6h`) or a Stroom date expression (`now()-1d`, `day()`), and apply to when the task's
status changed.

* **`failures`** (the default; window defaults to the last 24 hours) answers "what's failing in processing, and
  why?" in one call:
  * tasks in the window by status, pipeline, feed and node, with failure rates. A node with half or more of the
    FAILED tasks is called out;
  * FAILED tasks grouped by pipeline, feed and node, with first and last times and up to 3 example tasks. Stroom keeps no reason for a failed task, so the result says
    to look in that node's application log in the user's own log system. The tool never looks for it;
  * tasks still Assigned or Processing whose status time hasn't been renewed for `stuck_after` (default 1h). A
    node renews a running task's status as it goes, so these are often on a node that stopped or lost the
    database;
  * the errors inside processing that completed: Error streams created in the window for the pipelines asked
    about, or else those that had tasks in the window (most failures first, at most 20 pipelines, each queried on
    its own so the database uses its index of each processor's streams, up to 200 streams each). A sample (`sample`,
    default 30, at most 100) is read, taken from each pipeline and feed in turn, newest first, so one noisy pipeline
    doesn't take the whole sample. Up to 256 KiB of each is read and its markers parsed (`ErrorStreamMarkers`).
    Markers are grouped into failure modes (`FailureModes`): same severity, same element, same message pattern
    (`ErrorMessageNormaliser` replaces URLs, UUIDs, times, IPs, quoted values, paths, hex ids and numbers). Up to
    15 modes are shown, most severe then most met, with counts, pipelines, feeds, nodes and examples linked to
    their streams. A coverage line says what was read, truncated, unreadable or unparsed, and whether time ran out
    (reading stops at half the call's time).
* **`list`**: tasks newest first (id, status, node, stream, feed, pipeline, filter, times, duration), with the Error
  streams each wrote and their counts (`TaskErrorStreams`). `limit` defaults to 50, at most 500. At most 100 are
  shown inline; `out` writes all to a CSV with `TASK_COLUMNS`.
* **`summary`**: counts grouped by any of `pipeline`, `feed`, `node`, `status`, `filter` (`group_by`, default
  pipeline and status). Counts by node take a query per node; what is left over is "on no node yet". Counts by
  filter come from task rows, up to 10,000.

Tasks are read a page (1,000) at a time, newest first, through `ProcessorTaskFinder`, keeping only those whose
pipeline (or analytic rule) and feed the user can view. Nothing says how many others there are. Scans stop at
10,000 rows, and the result says so. Error streams are used only if the user can view their pipeline. Their
content is read only with View Data, as the data viewer needs, and each one read is audit-logged as viewed. Without
View Data, `failures` gives the counts only.

* **Arguments**: `mode`, the filters above, `group_by`, `limit`, `out`, `sample`, `stuck_after`.
* **Returns**: text (Markdown sections), plus the CSV for `list` with `out`.
* **Refuses**: without Manage Processors, "You need the Manage Processors permission to see processing tasks, as the
  Active Tasks tab does." and nothing else; an unknown mode, status or group; `since` not before `until`; a feed
  name the user can't view; a filter that isn't an id or uuid; a time it can't read.
* **Notes**: time limit is the query time limit. Reads untrusted content: error messages quote the data. Error
  triage by rules (`ErrorTriage`, `error_rules.yaml`) is not used here; the build tools use it.

### `explain_processing`

processing · read-only · requires Manage Processors; unlocked by Manage Jobs, Manage Nodes and View System Info;
checks View on the pipeline · Classes: `ExplainProcessingTool`, `ProcessingExplainer`, `ProcessingFacts`,
`StroomProcessingFacts`; Stroom APIs `JobStates` (`stroom-job-api`), `ProcessingDiagnostics`
(`stroom-processor-api`), `ClusterNodeManager`, `FsVolumeStates`, `IndexVolumeStates`

Explains why processing isn't happening, or is slow, and in what order filters get work. Give one of
`pipeline_uuid` (a pipeline or analytic rule the user can view), `feed` (explained through its newest stream) or
`stream_id`. Give none for the whole cluster.

`ProcessingExplainer` traces the chain and calls each link `ok`, `blocking` (nothing gets past it), `slowing` (work
gets through, late or partly) or `not checked` (the user lacks the permission that link needs, and "the answer may
lie here"). It reads state only. Stroom's own reasons for skipping a filter are only logged, and it never reads
logs, so they are worked out from what the database and nodes say. Both claim modes are explained, chosen by
`stroom.processor.claimTasksOnWorker`: worker claiming, or master queue. The cluster checks come first for every
subject:

* **nodes** (Manage Nodes): enabled, answering the cluster's pings, and the master. Master-queue mode with no master
  blocks.
* **settings**: in master-queue mode, `stroom.processor.assignTasks` or `fillTaskQueue` false blocks.
* **jobs** (Manage Jobs), each asked of every node:
  * Processor Task Creator and Data Processor block when disabled or enabled on no answering node. Stroom creates
    the Processor Task Creator disabled;
  * the task reaper and the heartbeat job only slow;
  * Data Processor blocks with Max Tasks 0 on every node, and slows when every slot is busy;
  * a job overdue by 5 minutes on every node slows.
* **volumes**: no data volumes, or a default data volume group with no writable volume, blocks. Other unwritable
  groups slow. Stroom deletes nothing to make room.
* **claiming** (worker mode, View System Info): nodes that didn't answer, or haven't renewed their heartbeats within
  the task lease.
* **dead tasks**: tasks past the task lease without a heartbeat and not reaped.

For each filter, `filterChecks`:

* **filter**: deleted, disabled, or its processor disabled.
* **tracker**: none yet (not polled); Error, which Stroom never retries; Complete; or already past the stream.
* **window**: the stream create time window.
* **dependencies**: waits for an Unlocked stream of each feed it depends on.
* **polling**: not yet polled, or the first poll only records where to start.
* **profile**: the processor profile is missing, its node group is missing or disabled (node groups are created
  disabled), it has no periods, or it gives no node threads now.
* **tasks**: at `maxProcessingTasks`; Created but none queued, with a sample of 20 checked for streams still
  Locked (a task isn't queued until its stream is unlocked).
* **priority**: the enabled filters ahead of it in the same profile, and whether they starve it.

Filters get work in `ProcessingExplainer.WORK_ORDER`, as Stroom's `ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR`
gives it:

1. highest priority first;
2. then, of the same priority, the filter whose tracker is furthest behind (lowest stream id, then event id);
3. a filter not yet polled goes last of its priority;
4. ties go to the lowest filter id.

For a stream, it first checks the stream itself (deleted streams are never processed), then whether it has a task
and where it waits, else which filters select it and what stops them. For the cluster it adds `priorities`: the
enabled filters (at most 200) in work order with their backlogs, the rule, and the starved filters.

* **Arguments**: `pipeline_uuid`, `feed`, `stream_id`; at most one.
* **Returns**: JSON with:
  * `subject`, and `answer` ("Blocked at ...", "Not blocked, but slowed at ...", or "Nothing found stopping
    processing");
  * `first_blocker`, `mode`, `stream` (for a stream or feed);
  * `checks`, each `{link, state, finding, evidence?, fix?}`;
  * `filters`, each with its own checks, whose blocking and slowing findings are also raised into the answer with
    the filter named;
  * `priorities`, `not_checked`, and `note`. `note` says a task that ran and failed has no stored reason: look in
    that node's log; `processor_tasks` lists failures by node and time.
* **Refuses**: more than one subject; a non-numeric `stream_id`; a pipeline or analytic rule the user can't view.
  A missing stream or a feed with no visible streams is a blocking finding, not a refusal.
* **Notes**: each fact is read by `StroomProcessingFacts` with the permission its screen needs, throwing
  `PermissionException` without it, which becomes `not checked`. Nodes have 5 s to answer. A known Stroom defect
  in `ProcessorProfileCache` means an overnight profile period (ending at or before its start) only applies until
  midnight: the start is taken on the current day, so after midnight the period hasn't "started". A profile with
  an overnight period is reported as slowing (or, if no node gets threads now, as blocking with that as a possible
  cause), with the fix of splitting the period in two (start to 23:59:59, then 00:00 to the end). The defect is
  still present in `ProcessorProfileCache.getProfile`. Audit-logged as a search of the pipeline, stream or
  cluster. The output isn't bounded by the workspace read limits (`boundsItsOutput` false).

### `explain_retention`

processing · read-only · unlocked by Manage Policies (the rules and impact) and Manage Jobs (the jobs); without
Manage Policies, only the rule that keeps each stream the user can view · Classes: `ExplainRetentionTool`,
`RetentionFacts`, `StroomRetentionFacts`

Explains how long Stroom keeps data and why. The result always holds `jobs` and `behaviour`:

* `jobs`: the state of Policy Based Data Retention and Data Delete: enabled, the nodes enabled on, schedule, last
  and next run, or "Not checked: it needs the Manage Jobs permission".
* `behaviour` (`ExplainRetentionTool.BEHAVIOUR`): how Stroom applies the rules, as its code does it:
  * retention is by create-time age alone, and disk space plays no part;
  * rules are tried in rule-number order and the first match applies; disabled rules, and rules with a disabled
    expression, are skipped;
  * a stream no rule matches is kept forever, and a Forever rule keeps what it matches even where a later rule
    would delete it;
  * the retention job logically deletes and leaves Locked streams; disabled, it deletes nothing;
  * Data Delete removes files after `stroom.data.store.deletePurgeAge`, and until then a stream can be restored;
  * after a run, only streams that have crossed a period since are looked at, until the rules change;
  * the rules cover streams only. Lucene shards have their own retention and Elasticsearch its own lifecycle
    policies.

  The description tells the model to answer from `behaviour`, not from how other products behave. It was added
  because the agent, asked what happens when volumes fill, said Stroom deletes the oldest streams.

With Manage Policies, `rules` lists each rule (`number`, `name`, `keep`, `matches`, `enabled`, `skipped` when its
expression is disabled). If there are no rules it says all data is kept forever. With `feed` (the newest stream of
each type, among the feed's newest 200) or `stream_id`, `streams` gives each stream's `rule`, `keep` and
`keep_until`, the Data screen's retention attributes. A stream past its time but not yet deleted is marked
`overdue`. With `impact`, `impact` counts what the rules would delete now, by rule, with each rule's 10 largest
feeds and types. This is as the Data Retention screen's Impact tab, and can be slow on a large store.

* **Arguments**: `feed` or `stream_id` (not both), `impact`.
* **Returns**: JSON: `view` (the reduced-view note, without Manage Policies), `rules`, `rules_note`, `streams`,
  `streams_note`, `impact`, `jobs`, `delete_purge_age`, `behaviour`.
* **Refuses**: both `feed` and `stream_id`; a non-numeric `stream_id`; "No stream n that you can view."
* **Notes**: the rules are read as the processing user (`StroomRetentionFacts`), as the Data screen does to show
  each stream's rule to users without Manage Policies. The tool decides what to show. `deletePurgeAge` is also read
  as the processing user: reading properties needs Manage Properties, but this one only says how long deleted data
  is kept. Streams come from `MetaService.findDecoratedRows`, which checks the user may view each feed. The impact
  needs Manage Policies. Audit-logged as a search, Full or Reduced view.

## Administration

These tools read Stroom's configuration, running tasks, volumes and Elasticsearch templates for an administrator.
`config_properties`, `server_tasks` and `volumes` are in category administration and the administration chat group.
`elastic_index_templates` is in category indexing and the indexing chat group, but is described here because it is
an administrator's read of a cluster. None changes anything.

### `config_properties`

administration · read-only · requires Manage Properties · Classes: `ConfigPropertiesTool`, `ConfigValueMasker`

Reads Stroom's system properties, as Administration > Properties shows them, to explain settings and limits, e.g.
why search results stop at a number and how to change that. It never changes a property.

* **`search`** scores properties by the words of `query` against their paths and descriptions, never their values.
  A word in the property's own name counts most, then elsewhere in its path, then in its description. Words with
  much the same meaning (e.g. limit for max) count less. The words run together in the path (e.g. `maxresults`)
  count extra. Words are split at punctuation and camel case, singularised, and stop words dropped. Optional
  filters: `prefix`, `overridden` (a database or YAML override), `differs_from_default`. `limit` defaults to 20,
  at most 100.
* **`get`** gives up to 10 properties in full by `paths`:
  * what the property means, its type and how its values are written;
  * its default;
  * its database override, with who set it and when;
  * this node's YAML override;
  * its effective value and where that comes from, with what a duration or size means, e.g. "PT2M is 2 minutes";
  * whether it is editable and whether it needs a restart;
  * up to 60 siblings.

  With `nodes` or `all_nodes`, each node's YAML value is asked of each node side by side, as the user, as the
  screen does, since YAML can differ by node. This is limited to 20 nodes and 60 property-node lookups, with 10 s
  for all of them.
* **`list`** lists the properties and branches directly under `prefix` (default `stroom`). `limit` defaults to 100,
  at most 500.

Every result says how a value is changed (`HOW_TO_CHANGE`, from `ConfigMapper`, `ConfigProperty.getEffectiveValue`,
`GlobalConfigService` and `AppConfigMonitor`). The description tells the model that a limit often has several
layers. For example, a dashboard table's Max Results or a StroomQL `limit` replaces `stroom.ui.defaultMaxResults`,
and sorted results are also capped by `stroom.search.resultStore.maxSortedItems`. So the model checks the document
the user means too, and says which layer applies.

Values are masked by `ConfigValueMasker`, which errs on the side of masking:

* every value of a property marked as a password;
* every value of a property whose name suggests a secret (`clientSecret`, `apiToken`, ...; a name only ending in
  `token`, so `tokenEndpoint` is not), unless its type can't hold one (a number, boolean, duration or size);
* in any other value, credentials written into it: a URL's `user:password@`, secret-named keys in `key=value`,
  `key: value`, JSON or XML forms, map values, and `Bearer`/`Basic` credentials.

Its patterns are linear, so they are safe on any value.

* **Arguments**: `mode` (required: `search`, `get`, `list`), `query`, `prefix`, `overridden`,
  `differs_from_default`, `paths`, `nodes`, `all_nodes`, `limit`, `out` (search and list: write them all to a
  file).
* **Returns**: text.
* **Refuses**: without Manage Properties (checked first, failing closed), `REFUSED` and nothing else; an unknown
  mode; a search with no words or filters; `get` with no paths or more than 10; "There are no properties under
  <prefix>", with suggestions.
* **Notes**: every call is audit-logged as a Search or View of the properties given, as the Properties screen's own
  calls are. Descriptions and values are data, not instructions, as the description says.

### `server_tasks`

administration · read-only · requires Manage Tasks · Classes: `ServerTasksTool`

The live view of Monitoring > Server Tasks: the tasks running on every node right now. It answers "what is
running?", "how many searches are users running?", "who is running the longest searches?", "what is node X busy
with?" and "is anything stuck?". It is not for stream processing; that is `processor_tasks`.

Every enabled node is asked for its tasks through `TaskResource`, as the screen asks, side by side. The wait is 15 s
or two-thirds of the tool time limit, whichever is less. Each result is a snapshot that names the nodes that didn't
answer and the disabled nodes. The tool's own read tasks ("Ask Stroom AI: reading the tasks of node ...") are left
out. It never stops a task.

* **`summary`** (the default) counts tasks by name, user and node, top-level tasks apart from sub-tasks. It gives
  the 10 oldest, and counts tasks running over an hour as long-running.
* **`summary` with `kind` `search`** counts users' searches, each once by its top-level task, not each node's
  sub-tasks. It counts them by user, type and the node running the top-level task, and gives the longest running.
  Searches are recognised by top-level task name (`SEARCH_TASK_NAMES`: federated, Elasticsearch, Solr, SQL
  Statistics searches and others), with Dashboard Search (a poll for results) excluded. Sub-task names
  (`SEARCH_SUB_TASK_NAMES`) catch searches whose top-level task wasn't seen. Searches whose info ends "staying alive
  for UI requests" have finished and are holding results for the UI; the summary says how many.
* **`list`** lists tasks (id, parent, name, user, node, thread, age, info, sub-tasks), oldest first, or newest with
  `order`. `limit` defaults to 50, at most 500. `out` writes all the matching tasks to a `.csv` or `.txt`. Info is
  cut at 200 characters in a result and 1,000 in a file.
* **`tree`** shows one task (`id`) with its sub-tasks, as the screen's tree does, up to 200 lines.

* **Arguments**: `mode`, `kind` (`any` default, `search`), `name`, `user` (contains, ignoring case), `node`,
  `min_age` (e.g. `10m`, `2h`, `PT30M`), `top_level_only`, `order`, `limit`, `id` (tree), `out`.
* **Returns**: text, plus the file for `list` with `out`.
* **Refuses**: without Manage Tasks, `NEEDS_MANAGE_TASKS`. Without it Stroom shows a user only the tasks of their
  own browser session (User Tasks), which the agent isn't in, so the tool says so and gives nothing. Each node's
  `TaskManagerImpl.find` checks the permission again. Also refused: an unknown mode, kind or order; `tree` without
  `id`; a `min_age` it can't read; nodes that can't be found.
* **Notes**: each call is audit-logged as a Search of the Task Manager.

### `volumes`

administration · read-only · unlocked by Manage Volumes; without it, only whether each volume group can be written
to · Classes: `VolumesTool`, `VolumeReport`; Stroom APIs `FsVolumeStates` (`stroom-data-store-api`),
`IndexVolumeStates` (`stroom-index-api`)

Stroom's data volumes (where streams are stored) and index volumes (where Lucene shards are stored), and how Stroom
treats them as they fill. It answers questions like "are the volumes full?" and "why is data no longer being
written?". For each group it gives `group`, `default` and `writable`. For an index group, writable means writable on
some node. With Manage Volumes it adds each volume:

* data volumes: path or S3 bucket, status, limit, total, used and free space, and whether it is full;
* index volumes: also the node, plus `writable_on` and `not_writable_on`.

Without Manage Volumes, `view` carries the reduced-view note and only counts are given. The volumes are read as the
processing user. The user's view is decided by the tool, not by the volume services.

`behaviour` (`VolumeReport.DATA_BEHAVIOUR`, `INDEX_BEHAVIOUR`) says what Stroom does, as its code does it:

* each new stream goes to an Active, non-full volume of its feed's group (or the default group), picked by
  `stroom.data.filesystemVolume.volumeSelector`;
* a volume is full at its limit. Without a limit, it is full when free space falls to 1% of its size or 10 GiB,
  whichever is smaller. The default volumes Stroom creates are limited to
  `defaultStreamVolumeFilesystemUtilisation` (90%) of their filesystem;
* space is as last measured, and an unmeasured volume is never full;
* Inactive, Closed and full volumes are still read;
* with no writable volume in a group, writes fail ("no writable volumes");
* index shards go to a volume on the indexing node only, and fail with "Unable to find any non-full index volumes"
  when there is none;
* nothing is ever deleted to make room. Only retention or a user deletes.

This tool was added, with `explain_retention`, because the agent once said Stroom deletes the oldest streams when
volumes fill, and nothing it could read said otherwise.

* **Arguments**: `kind` (`data`, `index`, `both` default), `group` (only this group).
* **Returns**: JSON: `view`, `data_volume_groups`, `index_volume_groups`, `behaviour`.
* **Refuses**: an unknown `kind`.
* **Notes**: audit-logged as a search, Full or Reduced view. `explain_processing` uses the same states for its
  volumes check.

### `elastic_index_templates`

indexing (indexing chat group) · read-only · checks View on the Elastic Index document; Stroom also checks Read on
it when reading the templates, and reads them with the document's cluster connection, whose credentials need the
privileges to read templates · Classes: `ElasticIndexTemplatesTool`; Stroom API `ElasticIndexTemplates`
(`stroom-ai-api`), implemented by `AiElasticIndexTemplates` (`stroom-search-elastic`)

For an administrator reviewing or reproducing an Elastic Index document's Elasticsearch templates. Given the
document's uuid, it reads from the document's cluster:

* the index name;
* what the name resolves to: the newest index or data stream it stands for. If none exists yet, or the name can't
  be resolved, the templates are matched against the name itself;
* the index template that applies: the matching template of highest priority, with any lower-priority templates
  that also match, which don't apply, listed with their patterns and priority;
* each component template the index template is composed of.

Each template is given by name with its JSON body exactly as Elasticsearch returns it, numbers and all, indented
as Kibana's Dev Tools console shows it, in a code block. The result tells the model to show each verbatim, with
its name just above it, and not to add, remove or change anything or say whether or how it can be applied. Without
this instruction, a model edited the bodies (taking out fields Elasticsearch sets itself) and told the user the
result could be applied as it was.

A part Elasticsearch didn't give is reported with its status and Elasticsearch's own reason. FORBIDDEN names the
privilege the connection's credentials lack.

* **Arguments**: `uuid` (required).
* **Returns**: text: the document link, index name, resolution, the template that applies and any overlapping
  ones, and each body, under `### Index template` and `### Component template` headings.
* **Refuses**: no `uuid`; a document the user can't view, or one that doesn't exist (not told apart,
  `DocumentAccess.NO_PERMISSION`); a document that isn't an Elastic Index; `NOT_FOUND` from the read (no Read, or
  gone; also not told apart); `NOT_CONFIGURED` (the document isn't fully set up); `CLUSTER_UNAVAILABLE`.
* **Notes**: each successful read is audit-logged as a View of the document. The build tools `check_index_template`
  and `propose_index_template` take templates the user gives. This tool reads the live ones.

## Proposals: changes the user applies

These tools change nothing. Each checks what the model proposes, then offers it to the user as a button on the
tool call's card. Only the user's click does anything, and the click is checked again. All of them are read-only
(`isReadOnly()` is true), so they run in unattended runs and under a read-only tool policy. All are in category
Content.

How a button works:

* A tool returns a `ToolResult` carrying one or more `ToolUserAction`s (`id`, `action`, `arguments`). The result's
  text describes the button too, so a caller that can't show buttons loses nothing, and the model is told to
  mention the button rather than claim the change was made.
* `McpAgentChat` passes the tool's actions, with any an MCP server sent in `structuredContent.user_actions`, to
  `UserActions.check`. Only the action kinds named in `UserActions` are kept, each with typed arguments. Those that
  create documents or change editors (`create_query`, `create_dashboard`, `create_visualisation(s)`,
  `create_extraction_pipeline`, `change_extraction_pipeline`, `apply_code_change`, `apply_query_change`,
  `apply_dashboard_change`, `apply_documentation_change`) are only kept when one of Stroom's own tools offers them.
  An MCP server can't make one. Each button's label is written by Stroom from its own state, never from the
  tool's text. The checked actions are stored as `UserActionRecord`s in the result's `ToolResultRecord`. A button
  expires after 24 hours (`UserActions.EXPIRY`). A result keeps at most 20 actions.
* Each kind is checked as it is offered. For example, `create_query` and `create_dashboard` need a data source
  that exists and that the user has Use on. The other kinds need a document that exists and that the user may view.
* The browser draws the buttons (`AskStroomAiPresenter`, labels and states in `UserActionsUi`). A click calls
  `AskStroomAiResource.doUserAction` (`/userAction/{chatId}/{messageId}/{actionId}`), which `UserActionService`
  handles. It works only for the chat's owner, within the expiry, and only if the action can still be done. Each
  click goes in the audit log and is noted in the chat, so the model knows about it on its next turn.
* Most buttons only let the browser go ahead. For `create_query`, `create_dashboard` and `create_visualisation`
  the server sends back the settings, built again now, and the browser opens Stroom's own New dialog
  (`ShowCreateDocumentDialogEvent`). That dialog checks the user may create in the folder they pick, and logs the
  new document as Stroom logs any other. Nothing is created until the user confirms the dialog, so the button can
  be used again.
* Editor changes are applied in the browser as unsaved edits, through the editor's own code:
  `SteppingCodeChangeEvent` (stepping code), `QueryCodeChangeEvent` (a Query's editor), `ApplyDashboardChangeEvent`
  (a dashboard, through the code its column menus use) and the Documentation tab (`AiDocumentationChanges`). Edit
  on the document is checked when the user applies the change, not when it is proposed. The button is claimed
  first, so it applies once. It is released if the change is undone (`undoUserAction`). Every editor change carries
  a hash of what it was proposed against (`base_hash`, `snapshot_hash`), so it only applies while the editor or
  dashboard still holds that.
* Extractions are written on the server (`writeExtraction`), but only once the user has confirmed (see
  `propose_extraction_pipeline`).
* A proposal needs a chat run. Outside one (`context.runContext()` empty) a tool returns "Error: a button can only
  be offered while answering a question in a chat."
* `/mcp` doesn't send user actions. The tools in this group are all chat only, so `/mcp` doesn't offer them anyway
  (`McpTools` filters out `isChatOnly()` tools).

### `propose_query`

Content · read-only · checks Use on the data source the query reads · chat only · Classes: `ProposeQueryTool`,
`UserActions` (`create_query`), `UserActionService`

Offers a "Create query…" button for a new StroomQL Query document. The query is laid out with
`StroomQlFormatter`, because the new document will hold it as written. It must name a data source the user can
use, and `QueryService.validateQuery` must accept it. Clicking the button opens Stroom's New Query dialog with the
suggested name. Once the user creates the query, it is saved with the StroomQL, opened, and attached to the chat,
so the user can refine it with `propose_query_change`.

* **Arguments**: `name` (suggested name; at most 200 characters, one line), `query` (the StroomQL; at most the
  workspace's `maxQueryLength`), optional `description` (shown with the button).
* **Returns**: text naming the button and the data source it reads, plus a `create_query` action
  (`create-query-N`) holding `name`, `query` and `description`.
* **Refuses**: a missing or over-long name or query; no data source the user can use
  (`ValidateStroomQlTool.NO_DATA_SOURCE`); an invalid query ("The query is not valid, so no button was offered: …",
  with the parser's reason).
* **Notes**: it reads untrusted content, because the result names the data source as Stroom holds it and the
  parser's errors may quote stored text.

### `propose_query_change`

Content · read-only · checks View on the Query and Use on its data source · chat only · Classes:
`ProposeQueryChangeTool`, `TabSnapshots`, `LineDiff`, `QueryCodeChangeEvent` (client)

Proposes a revised query for the StroomQL Query in the tab the user attached. The button ("Apply to query") puts it
in the tab's editor, unsaved, and the user can undo it. The proposal is diffed against the query as the user has
it, unsaved edits included, taken from the `QUERY` part of the latest tab snapshot. It is formatted a clause per
line and given the editor's line ending (`ProposeCodeChangeTool.withLineEndingOf`), so a missing final line break
doesn't show as a change. It is validated as `propose_query`'s is.

* **Arguments**: `query` (the whole revised StroomQL), `summary` (at most 500 characters).
* **Returns**: text with the query as it will be applied, plus an `apply_query_change` action holding `doc`,
  `request` (the query), `base`, `base_hash` (SHA-256), `snapshot_hash`, and `added`/`removed` line counts.
* **Refuses**: no Query tab attached (`NOT_A_QUERY`: give it as a code block, or use `propose_query`); no View on
  the Query; a query cut short in the snapshot; no usable data source; an invalid query; a query identical to the
  tab's.
* **Notes**: it logs a view of the Query (`DocumentAccess.logView`).

### `propose_code_change`

Content · read-only · checks View on the element's document (Edit is checked when the user applies the change) ·
chat only · Classes: `ProposeCodeChangeTool`, `CodeChangeStepper`, `TabSnapshots`, `AgentInstructions`,
`SteppingCodeChangeEvent` (client)

While the user is stepping a pipeline, this proposes a change to the code of the selected element: an XSLT or a
text converter (`CODE_TYPES`: `XSLT`, `TextConverter`). The user sees a diff with a "Review and apply" button. The
button opens a side by side review. From there the user can apply the change to the element's editor, unsaved, or
reject parts of it (`rejectedChanges`), and undo it later. Nothing is ever saved by it. The change is diffed
against the element's code in the latest snapshot (`STEP_CODE` part), unsaved edits included. It is only applied
while the editor still holds that code.

Before the change is offered, it is tried. `CodeChangeStepper` steps the stepping screen's record through the
pipeline twice, as the user: once with the current code and once with the proposed code. This is Stroom's own
stepping, so later elements run as they will (for example a schema filter validating against the event-logging
schema). A problem is new if no element reports the same severity and message with the current code. Line numbers
are left out of that comparison, because lines move with the change. New errors (`ERROR`, `FATAL_ERROR`) refuse
the change unless `allowErrors` is set. New warnings, and how many of the current errors the change clears, are
reported. If the record can't be stepped, the change is still offered, with a note saying why it wasn't tried.

* **Arguments**: `element` (the element id, as named in the pipeline), `summary` (at most 500 characters), and one
  of `code` (the whole code; at most 500,000 characters, `UserActions.MAX_CODE_CHARS`) or `edits` (a list of
  `{oldText, newText}`, applied in order; each `oldText` must appear exactly once in the code as the earlier edits
  left it). Optional `allowErrors` (true only when the user has said to offer the change despite its errors).
* **Returns**: text with the unified diff, the trial's outcome, and the element's output with the proposed code for
  that record (at most 3,000 characters). Up to 20 problems are listed. The standing AGENTS instructions that apply
  to the code's and the pipeline's folders are appended, so the model can check the change follows them. Also an
  `apply_code_change` action holding `doc`, `pipeline`, `element`, `request`, `base`, `base_hash`,
  `snapshot_hash` and the line counts.
* **Refuses**: no stepping tab attached (`NOT_STEPPING`); an unknown element (the element ids are listed); an
  element that isn't the selected one, because only the selected element's code is shared; no View on the document;
  a type other than XSLT or text converter; code missing from the snapshot (still loading) or cut short; code that
  isn't well-formed XML (`xmlProblem`, using `SafeXPath.secureReader`, which refuses a DOCTYPE and never loads
  anything external); code identical to the current code; new errors on stepping.
* **Notes**: the time limit is the session's query time limit, because the record is stepped twice. The trial gets
  three quarters of it.

### `propose_documentation_change`

Content · read-only · checks View on the document (Edit is checked when the user applies the change) · chat only ·
Classes: `ProposeDocumentationChangeTool`, `TabSnapshots`, `DocumentDocumentationTool`, `AiDocumentationChanges`
(client)

Proposes a change to the Markdown on a document's Documentation tab, found by UUID. It is shown as a diff with a
"Review and apply" button. The user applies the change to the tab's editor, unsaved, saves it themselves, and can
undo it. If the attached tab is that document's, the diff is against the Documentation tab as the user has it,
unsaved edits included. Otherwise it is against the documentation as saved. The tab edits the document's own
`description` field, so only types with a `String getDescription()` can be given documentation
(`hasDocumentation`).

* **Arguments**: `uuid`, `summary` (at most 500 characters), and one of `markdown` (the whole text; at most
  500,000 characters) or `edits` (as `propose_code_change`'s).
* **Returns**: text with the diff, saying whether it was taken against the tab or the saved text, plus an
  `apply_documentation_change` action holding `doc`, `request`, `base`, `base_hash`, `snapshot_hash` (from the tab
  only) and the line counts.
* **Refuses**: an unknown document or one the user can't view; a folder, a Documentation document (its text is
  its content) or Data Receipt Rules (`EXCLUDED_TYPES`); a type without the field (`NO_DOCUMENTATION`); a tab whose
  documentation was cut short in the snapshot; no change.
* **Notes**: if the documentation was saved after the proposal, the change is refused when the user applies it.

## Dashboards, extractions and visualisations

These six tools are in the chat's dashboards tool group. They are offered once the group is active, or from the
start when the attached tab is a dashboard (`BuiltInToolGroups`). All are in category Content and read-only, and
all but `list_visualisations` are chat only.

### `propose_dashboard`

Content · read-only · checks Use on the data source · chat only · Classes: `ProposeDashboardTool`,
`DashboardBuilder`, `UserActions` (`create_dashboard`)

Offers a "Create dashboard…" button for a new dashboard built from a StroomQL query. The dashboard holds a query, a
table of the columns the query's `select` gives (with its grouping, sorting and limit), and, if asked for, a data
preview: a Text widget showing the event of the selected row, laid out below the table. `DashboardBuilder` builds
it in one of two forms:

* `stroomql` keeps the query in an Embedded Query, which shows its own table.
* `classic` turns the query's `where` clause into an expression Query and its `select` into a separate Table.

A classic dashboard can't hold `window`, `filter`, `having` or `show` clauses. The query is formatted and
validated as `propose_query`'s is. The button opens Stroom's New Dashboard dialog. The server builds the settings
again from the query when the button is clicked. The new dashboard then opens, ready to run, attached to the chat.

* **Arguments**: `name` (at most 200 characters, one line), `query`, `query_type` (`stroomql` or `classic`),
  `preview` (boolean, required: the tool tells the model to ask the user with `ask_user` if they haven't said),
  optional `description`.
* **Returns**: text describing what the dashboard holds (`Built.describe()`), plus a `create_dashboard` action
  holding `name`, `query`, `query_type`, `preview`, and for classic an `outline`: the Query and Table in plain
  words, which the user sees in place of the StroomQL.
* **Refuses**: no usable data source; an invalid query; the builder's refusals ("The query has no select clause…",
  a data preview on a grouped query, which can't trace rows to events, "A classic dashboard can't hold …").

### `propose_dashboard_change`

Content · read-only · checks View on the dashboard (Edit is checked when the user applies the change), and View on
any Visualisation it adds or switches to · chat only · Classes: `ProposeDashboardChangeTool`, `DashboardChanges`,
`DashboardComponents`, `DashboardSettings`, `DashboardBuilder`, `ApplyDashboardChangeEvent` (client)

Proposes changes to the dashboard in the attached tab. They are shown as a plain-language list with an "Apply to
dashboard" button. The browser applies them to the open dashboard as an unsaved change, on top of any unsaved
changes it already has, through the code the column menus use. The user can save the result or use Undo apply.
The changes are applied in order. At most 100 go in one proposal (`DashboardChanges.MAX_CHANGES`). The operations
are:

* columns: `group`, `ungroup`, `add_column`, `edit_column`, `move_column`, `hide_column`, `show_column`, `sort`,
  `filter`;
* `set_expression`: replaces the expression of the classic query feeding the table, from StroomQL-style `where`
  conditions checked against the data source's fields (`DashboardBuilder`);
* `add_table`: adds a table as a tab beside it, fed by the same query (at most 10 per proposal). Later changes set
  it up by naming it as their `table`;
* `add_text`: a data preview below a table;
* `add_vis` / `set_vis_settings`: a Visualisation component showing a Visualisation by UUID, with its controls;
* `add_selection_filter` / `remove_selection_filter`: filters a query's search or a table's rows by a chart's
  click;
* `add_format_rule` / `remove_format_rule`: conditional formatting;
* `rename`: any component;
* `set_setting`: a setting of any table, query or text component, as its Settings dialog has it
  (`DashboardSettings`).

Everything is checked against the dashboard as the user has it: the `DASHBOARD` part of the latest snapshot, read
as components by `DashboardComponents`.

* The table and every column named must exist. Columns are named as the user sees them.
* Each expression must parse with the parser the table tool's eval uses, against the data source's fields (at most
  10,000 are read). If the table extracts values, its extraction pipeline's fields count too.
* A Visualisation must be one the user can view. Its settings must be controls it declares (at most 50,
  `MAX_VIS_SETTINGS`), and each `field` control's value must be a column of the linked table.
* A selection filter's mode must suit its target: `query` for a Query, `table` for a Table. Its selection keys must
  be ones the chart gives (at most 10 terms, `MAX_FILTER_TERMS`).

A change that fails any check is refused with every reason, numbered by change. The `component` may also be a
Visualisation pane's name; the pane's linked table is then taken (`linkedTable`), as changes to a chart are
proposed for its table.

* **Arguments**: `component` (the table, by its name as the user sees it; or an embedded query, for `set_setting`,
  `rename` and the selection filter ops only), `changes` (list of `{op, ...}`; the per-op fields are in the golden
  catalogue), `summary` (at most 500 characters).
* **Returns**: a plain-language list of the changes, plus an `apply_dashboard_change` action holding `doc`,
  `component`, `component_id`, `changes` (as the browser applies them), `snapshot_hash` and `summary`. If the
  proposal reads a chart Stroom AI generated before a setting was added, the action also holds `update_charts`
  (UUIDs). Those Visualisations get the new settings as the change is applied (at most 50).
* **Refuses**: no dashboard attached (`TabSnapshots.notAttached`, which points at the latest dashboard tab if there
  is one); no View; an unknown table (the tables are listed); dashboard settings cut short or still loading; on an
  embedded query, any op other than `set_setting`, `rename` and the selection filter ops ("whose StroomQL makes its
  results"); any failed check.
* **Notes**:
  * The hash is taken with the Visualisations' render reports removed
    (`DashboardComponents.withoutRenderReports`), because those change as charts draw.
  * A chart Stroom AI generated can't be made another kind through its pane's settings. Its Visualisation's Data tab
    has the old kind's columns, so the chart would ask for columns it can't be given (seen in testing: a line chart
    made a bar chart asked for x and y). The model is pointed to `propose_visualisation`'s `replace`.
  * A choice a generated chart doesn't offer (for example a typo) is refused with the choices it does offer. The
    choices are read as the template has them now, so one added since the chart was generated is accepted.
  * Selection filters:
    * Stroom ANDs the terms in one operator that name the same component's selection. A match-any filter therefore
      puts each term in an operator of its own. Seen in testing: a filter on sender or recipient being the clicked
      node matched only messages to oneself.
    * A match-any filter on a network's link ends also matches a clicked node at either end, because a node
      selects `node`, not `source` or `target`.
    * A term comparing a date with a clicked date is made a range over the selected period. Otherwise it matches
      only the instant the period starts (seen in testing: a line chart of days filtered by its x matched nothing).
  * If a change adds or changes a Text widget while the table's rows are grouped (so they can't be traced to
    events), the result says so (`eventNote`).
  * It writes an audit "document viewed" record listing the changes.

### `preview_extraction`

Content · read-only · requires Stepping and View Data; checks View on the dashboard, its extraction pipeline and
XSLT · takes a run slot · chat only · Classes: `PreviewExtractionTool`, `ExtractionField`,
`ExtractionXslt`, `ExtractionChange`, `ExtractionPipelines`, `ExtractionPreview`, `ExtractionEvents`,
`PreviewRecord`, `SteppingWalks`

Tries a draft search extraction for a table on the attached dashboard, on some of the table's own events, before
anything is made. The model gives only the fields. Each field has a name, an XPath from the event element
(unprefixed, because names are in the events' namespace) and a type. `ExtractionXslt` writes the XSLT from them:
for each event, a `records:2` record holding `StreamId`, `EventId` and the first value each XPath finds. The fields
are kept in a comment at the top of the XSLT, so it can be written again from them. The model never writes the
XSLT.

The extraction is stepped (`SteppingWalks`) as the user, in one of two places:

* If the table extracts already, it is tried in the table's own extraction pipeline. The output XSLT element's
  code is replaced, as a change to it (`ExtractionChange`). An XSLT Stroom AI wrote is written again from the
  fields given, which are all it is to give, so fields can be removed or renamed. An XSLT written by hand is kept,
  with a `data` element added for each new field before the end of its one record.
* Otherwise it is tried in a pipeline as the Search Extraction template makes it, unsaved
  (`ExtractionPipelines.trial`). The first XSLT the user can view, by name, stands in for the code.

The first event is first stepped with a probe XSLT (`ExtractionXslt.NAMESPACE_PROBE`) to find the namespace the
events reach the XSLT in, and the XSLT is written for that namespace.

`ExtractionPreview` checks the result:

* Refused: a column of the table whose field the extraction doesn't give. Turning extraction on fills every value
  in the table from it, the indexed values included, so that column would be blank.
* Flagged:
  * a field the data source has too. Its extracted value replaces the indexed one in the table, while queries still
    filter on the indexed value;
  * a field found in few of the events;
  * values not of the field's type;
  * values that differ from those the table shows now;
  * errors from stepping.

* **Arguments**: `table` (by its name as the user sees it), `path` (a snapshot of the table's rows with `StreamId`
  and `EventId`, from `current_results` with `withEventIds`), `fields` (list of `{name, xpath, type}`; `type` is
  `text`, `number` or `date`, default text; at most 100 fields, names up to 100 characters, XPaths up to 500), and
  optional `limit` (events, default 10, clamped to 1–50).
* **Returns**: text saying where it was tried and how (rewritten or added to), a table of the values extracted per
  event, "It can't be used as it is" problems, and "To check" notes. The XSLT is written to
  `/outputs/extraction.xsl`. If the preview can be used, a `PreviewRecord` is written under `/tabs/`, which only
  Stroom writes. If it can't, the record is marked unusable and the reason is appended ("It can't be proposed: …").
* **Refuses**:
  * no Stepping or View Data permission;
  * no dashboard attached, or no View on it;
  * no such table, or an embedded query (its StroomQL makes its results, so it has no extraction);
  * a snapshot without `StreamId`/`EventId` columns or rows;
  * no XSLT the user can view to step with;
  * a table pipeline that no longer exists, has no XSLT element naming an XSLT before its search result output, or
    whose XSLT element finds its XSLT by name pattern (`ExtractionPipelines.unchangeable`: the pattern takes
    precedence, so changing the named XSLT would do nothing);
  * an XSLT the user can't view;
  * a hand-written XSLT that already gives a field being added, has no xsl prefix, or has no record element or
    more than one;
  * an XPath outside the allowed functions (`ExtractionField.ALLOWED_FUNCTIONS`: plain string and number
    functions, no prefixed functions or variables).
* **Notes**:
  * XPaths are compiled with the locked-down engine of `SafeXPath`. They run in Stroom's own pipeline engine on
    every search that extracts, where extension functions such as `stroom:http-call`, and functions that read other
    documents, are available.
  * Each stream's walk is limited to 60 seconds. The streams stepped are audited (`AiAuditLog.streamsViewed`).
  * A preview is usable only if it has no problems, at least one event stepped, the snapshot was taken of this table
    by `current_results` (checked from the snapshot's metadata, so the model can't supply other events), and the
    events' stored namespace (`ExtractionEvents.namespace`) matches the one stepping found. If they differ, an
    earlier pipeline element changes the events, and an XSLT written for them couldn't be checked.
  * It needs a chat (`requireChatId`) and the attached tab, so it is chat only (`isChatOnly()`): `/mcp` doesn't
    offer it. Found reviewing the tools: offered over `/mcp`, every call failed with "preview_extraction can only be
    used in a chat."

### `propose_extraction_pipeline`

Content · read-only · checks View on the dashboard and on the table's extraction pipeline and XSLT (Edit on the
XSLT decides whether a change in place is offered) · chat only · Classes:
`ProposeExtractionPipelineTool`, `ExtractionPlan`, `ExtractionActions`, `ExtractionChange`, `ExtractionEvents`,
`PreviewRecord`, `UserActionService.writeExtraction`

Offers a button to make the search extraction last tried with `preview_extraction` for a table on the attached
dashboard. It applies the same checks as the preview, but doesn't step. The fields must hash to the
`PreviewRecord`'s. The table's extraction pipeline, and the hash of its XSLT's code, must be as they were at the
preview. So only fields that were tried, on the table's own events, can be offered.

* A table with no extraction gets one button, "Create extraction pipeline" (`create_extraction_pipeline`).
* A table that extracts already gets two buttons, if the user can edit its XSLT:
  * "Change extraction pipeline" (`change_extraction_pipeline`) changes that XSLT in place. The user first sees a
    diff of it, and the other dashboards and queries that use the pipeline;
  * "Create extraction pipeline" saves a new pipeline with its own (changed) XSLT, for this table only.

  Without Edit on the XSLT, only "Create extraction pipeline" is offered.

When a button is clicked, `ExtractionActions.prepare` says exactly what would be written. The events' namespace is
read then (`ExtractionEvents`), not taken from the model. The new pipeline is a child of the content pack's Search
Extraction template that sets only its XSLT, if the user can use the template. Otherwise it is a pipeline of its own,
as the template makes. Once the user has chosen the name and folder in Stroom's New dialog (or confirmed the diff),
`writeExtraction` writes it as the user. It refuses anything whose hash differs from what was prepared. The button
is claimed first so the extraction is written once, and released if the write fails. The browser then sets the
table up to use it, as unsaved changes, with a column for each new field.

* **Arguments**: `table`, `fields` (exactly as tried), `name` (suggested name for the pipeline and its XSLT; at
  most 100 characters), optional `summary` (at most 500 characters).
* **Returns**: text describing the button(s), what the XSLT gives, any fields it no longer gives (which anything
  else using the pipeline loses) and the columns added. The actions hold `doc`, `name`, `plan` (`ExtractionPlan`
  as JSON: table, component id, the sample event, fields, new columns, and the existing pipeline/element/XSLT and
  its code hash) and `summary`.
* **Refuses**:
  * no dashboard attached, or no View;
  * no such table, or an embedded query;
  * no usable preview of this table ("Try it with preview_extraction first");
  * fields or pipeline changed since the preview;
  * the pipeline's XSLT changed since the preview;
  * the same pipeline refusals as `preview_extraction`;
  * a column of the table whose field isn't given;
  * a new field whose name matches an existing column that shows something else.
* **Notes**:
  * An XSLT that names fields as it runs has its blank-column check left to the preview, which stepped it.
  * It writes an audit "document viewed" record.
  * Chat only (`isChatOnly()`), as it works on the dashboard attached to the chat and offers a button: `/mcp`
    doesn't offer it. Found reviewing the tools: offered there, every call returned "Error: a button can only be
    offered while answering a question in a chat."

### `list_visualisations`

Content · read-only · checks View (the Visualisations listed are those the user can view) and Use (reports when the
user lacks Use on a library's Script doc) · reads no untrusted content · Classes: `ListVisualisationsTool`,
`ChartLibraries`, `ChartKind`, `ChartRole`,
`StyleSetting`, `D3Visualisation`, `AdapterScripts`

Reports, as Markdown:

* the charting libraries installed:
  * ECharts: a Script doc holding `echarts.min.js`, recognised by its content (the zrender dependency and version
    each build declares), version 6 or later (`MIN_ECHARTS_MAJOR`);
  * D3: a Script doc holding a D3 build, version 3 or later, the newest used;
  * the visualisations content pack's own D3 Visualisations, found by their fixed UUIDs, with function names as a
    fallback for a copy;
* every chart kind (`ChartKind`: bar, line, pie, scatter, graph, heatmap, calendar, histogram, sankey, chord, gauge,
  sunburst), with its columns (roles: required or optional, the values each needs, and whether the content pack's
  D3 version has the role) and its style settings (values and defaults);
* how clicks select, and the ECharts option keys allowed (`VisualisationPlan.OPTION_KEYS`; for D3 only `color`);
* the Visualisation docs the user can see, at most 100, each marked as generated by Stroom AI (and with which
  library) or as a content pack D3 chart.

It also tells the model to ask the user which library to use when both are installed. A network graph also gets
its force-layout tuning options (`TUNING`).

How generated charts are served:

* Stroom ships an adapter for each library: `echarts-generic.js` and `d3-generic.js`, in
  `stroom/ai/impl/vis/`. The first time a chart is generated with a library, the adapter is put beside it as a
  Script doc depending on the library, with the library doc's permissions (`ChartLibraries`).
* Whatever an adapter doc holds, `AdapterScripts` (a `ScriptContentProvider`) serves this release's adapter for it.
  So each release's adapter draws every chart generated before it, with no chart generated again, no doc changed,
  and nothing done as Stroom starts. Docs from versions that named the adapter for its content are served the same.
* Whoever may edit the library's Script doc controls code that runs, unsandboxed, in every generated chart. That
  is a matter for the doc's permissions.

* **Arguments**: none.
* **Returns**: Markdown sections "Charting libraries", "Chart kinds", "Visualisations in Stroom".
* **Notes**: it is not chat only. The library search runs as the processing user, as Stroom loads a
  visualisation's scripts with elevated read. Whether the user may use what was found is reported separately.

### `propose_visualisation`

Content · read-only · checks View on the attached dashboard or Query, Use on the library's Script doc, and Edit on
a Visualisation it replaces · chat only · Classes: `ProposeVisualisationTool`,
`VisualisationPlan`, `ChartCheck`, `ChartConversions`, `ChartLibraries`, `ChartLibrary`, `EChartsTemplate`,
`VisualisationActions`, `D3Visualisation`, `ProposeDashboardChangeTool` (to check table and filter changes)

Plans a chart of a table on the attached dashboard, or of the attached StroomQL Query, and checks that the table's
data can make it. What it offers depends on the library:

* `echarts` or `d3`: a "Create visualisation" button. Stroom builds the Visualisation's settings from a fixed
  template (`EChartsTemplate`), never from code or JSON written by the model. Clicking the button opens Stroom's
  New Visualisation dialog with the suggested name in the dashboard's or query's folder. `VisualisationActions` puts
  the library's adapter in place if it isn't there yet. Once the user creates the Visualisation, the browser adds it
  to the dashboard (placed by `placement`, linked to the table), or to the query's `show` clause by UUID, unsaved.
  An embedded query on a dashboard is charted through its StroomQL's `show` clause too.
* `content-pack`: nothing is generated or offered. The result gives the `add_vis` change, or the `show` clause, that
  wires the pack's existing D3 Visualisation to the table, for the model to propose with `propose_dashboard_change`
  or `propose_query_change`.

`ChartCheck` checks the data:

* every required role is mapped to a column the table has;
* each column holds the values its role needs;
* the sample isn't too large or too sparse to read: for example at most 100 readable categories, 500 time points,
  and the kind's `maxSeries`, `maxValues` and `maxNodes` settings.

The sample is the table's `current_results` snapshot, at most 5,000 rows. A snapshot is taken if there isn't one,
and the search is never run again. If the check fails, nothing is offered. The result lists the problems and, from
`ChartConversions`, what the table lacks and the kinds it can make as it is, for the model to recommend. The check
also reports statistics and warnings from the sample.

Analyses are style settings, drawn by the adapters in the browser:

* **Trend lines** (`trendLine`: `none`, `linear` or `moving average`; `trendWindow`): on line, bar and scatter
  charts. Each series gets a dashed trend line labelled with its slope (for example +12/day) and R². A weak fit is
  drawn fainter. `ChartCheck.checkTrendLine` refuses a trend across text categories, a bar chart sorted by value,
  and a scatter plot whose y is a Date Time column, because the chart would draw no trend.
* **Leiden clusters** (`nodeColours` = `cluster`, with `clusterLinks`): on network graphs. The adapter finds
  clusters with the Leiden method. It merges node by node, taking the best merge each time rather than a random
  one, so the same network always gives the same clusters. It runs over the most connected 5,000 nodes, before Max
  Nodes leaves any out, so the clusters don't change with Max Nodes. Each cluster is named after its most connected
  member (for example "mchen +11"). The twelve largest get their own colours, and the rest are grey.

Each chart's render report (what it drew, rows it left out, trends, clusters, errors) shows in the chat and in the
dashboard's description. The model reads it there.

* **Arguments**:
  * `library` (`echarts`, `d3`, `content-pack`), `summary` (at most 500 characters);
  * for one chart: `kind`, `columns` (role to column name), optional `style`, `options` (ECharts option overrides
    as JSON: plain JSON only; for d3 only `color`), `name` (required for echarts/d3; at most 100 characters),
    `componentName`, `placement` (`charts` default, `bottom`, `below`, `tab`), `table` (when the dashboard has
    several);
  * dashboard and generated only: `replace` (a pane showing a chart Stroom AI generated of the same table) or
    `addNew`; `newTable` (`add_table` and column changes for a table the button adds beside this one) or
    `tableChanges` (changes to the charted table made with the chart), not both; `filter` (the selection filter
    its clicks drive, as `add_selection_filter` without `chart`);
  * several charts: `charts` (1–12, each `kind`, `columns`, `name`, optional `style`, `options`, `table`,
    `componentName`) with `layout` (`row`, `column`, `grid` of up to 3, `tabs`).
* **Returns**: text describing the chart, the library and version, sample notes, warnings, and what a replacement
  loses. Actions:
  * `create_visualisation` holding `doc`, `name`, `plan` and `summary`. The `plan` is a `VisualisationPlan` as
    JSON: kind, mappings, style, options, table, component id and name, placement, snapshot hash, `query`/`baseQuery`
    for a show clause, `replace`, `library`, `tableChanges`, `filterChanges`;
  * for `charts`, one `create_visualisations` action ("Create visualisations"): the user picks one folder and each
    chart is created there. Charts that can't be drawn are left out, with the reasons;
  * for `content-pack`, no action, only the change to propose.
* **Refuses**:
  * a bad library, kind, placement, layout, name or summary;
  * no dashboard or Query attached, or no View on it;
  * no table, or the table not named when there are several;
  * a snapshot cut short;
  * an embedded query shown by reference (its chart goes in the referenced Query's `show` clause), or charted with
    `content-pack`;
  * `newTable`, `tableChanges` or `filter` on anything but a generated dashboard chart; `newTable` and
    `tableChanges` together; `replace` and `addNew` together; `filter` with `replace` (a replaced chart keeps the
    filters it can, renamed through `ChartConversions`);
  * a library not installed, or no Use on its Script doc ("the chart wouldn't load for them");
  * a failed data check;
  * a `content-pack` kind the pack lacks;
  * `charts` with `content-pack`.

  It also stops and asks first in two cases. If the table already has a chart Stroom AI generated and neither
  `replace` nor `addNew` is given, it tells the model to ask the user whether to replace it or add a new one
  (`askReplaceOrAdd`). If several charts are given without `layout`, it tells the model to ask how to arrange them
  and recommends a layout.
* **Notes**:
  * `replace` is refused for a Query's chart, a content-pack chart, a pane of another table, a Visualisation Stroom
    AI didn't generate, or one the user can't edit.
  * On click, `replace` rewrites the replaced Visualisation for the new chart and renames it, saved at once. It
    stays in its pane, keeps its style unless given another (`ChartConversions.styleKept`), and its selection
    filters follow where the new kind selects the same column under another key.
  * With `newTable`, a `table` naming the new table is read as the one it is added beside. Seen in testing: given
    the new table's name, the chart was refused.
  * For a date column grouped by `floorDay()` and similar functions, a click's filter covers the whole period.
  * It writes an audit "document viewed" record.

## Exploring the environment

### `find_pipeline_templates`

Content · read-only · checks View on every pipeline it reads (only pipelines the user can view are searched); each
candidate's layers are read as the pipeline editor reads them, which needs Use on the pipelines it inherits from ·
chat group build · Classes: `FindPipelineTemplatesTool`, `PipelineTemplates`, `PipelineShapes`, `PipelineIndex`,
`AiPipelineStagesConfig`

Finds Stroom pipeline templates (pipelines a new pipeline inherits from, not Elasticsearch index templates) for one
stage, by what each pipeline does rather than what it is called or where it is. Every pipeline the user can view is
ranked by why it counts as a template: `configured` (in one of the stage's template folders and matching its name
globs, `stroom.ai.pipelineStages.<stage>.templateFolders` / `templateNames`), `inherited_by_others` (two or more
children), `standard` (under `System/Template Pipelines`), then `template_like` (a parentless pipeline that leaves
what a child supplies, its XSLT or text converter, unset). Ties are broken by child count. Each candidate is merged
with its template chain and its shape worked out (`PipelineShapes.shape`): it is kept only when its stage matches,
and a `template_like` one only when it really leaves something for a child. For `forwarding`, a candidate that
would otherwise be `other` has its XSLTs (and those they import) read for CEF lines written as text (backend
`file`, see `PipelineShapes.writesCef`).

* **Arguments**: `stage` (required: `translation`, `indexing`, `discovery`, `reference`, `records` or
  `forwarding`).
* **Returns**: `{stage, candidates, unreadable?, hint?, xml_fragments?}`. Each candidate has `uuid`, `name`, `path`,
  `source` (why it is one), `children`, and its shape: `stage`, `backend`, `chain`, `parser`, `child_must_supply`,
  `shared` (what it already configures, e.g. a decoration XSLT or Elastic cluster) and `reference_data`.
* **Refuses**: a missing or unknown `stage` (`Give the stage: one of ...`).
* **Notes**: at most 10 candidates and 5 unreadable pipelines are given. A pipeline Stroom can't build (seen: an
  element type the Stroom lacks, `StateFilter`) is listed under `unreadable` rather than failing the search, because
  one such pipeline once failed the whole search and the agent fell back to a template by its name. With no
  candidate, `hint` says to ask the user, and names `create_pipeline standalone='events'` (translation) or
  `create_indexing_pipeline standalone='discovery'` (discovery) as a last resort. For `translation` with no
  `XMLFragmentParser` or `CombinedParser` template, `xml_fragments` says how to read XML fragments: a template whose
  parser is the XMLParser, else any whose parser can be swapped (seen: a Stroom with only JSON and text templates was
  told only to ask the user), with `replace_parser='XMLFragmentParser'` and an XML_FRAGMENT converter.

### `describe_template`

Content · read-only · checks View on the template and the documents it reads; unlocked by Manage Processors (the
feeds each child covers come from its processor filters; without it they are empty) · chat group build · Classes:
`DescribeTemplateTool`, `PipelineTemplates`, `SharedXslt`, `ProcessorFilters`

How the environment uses one template, before making a child of it. Three parts:

1. **Children**: every pipeline inheriting directly from it, each with what it overrides (`sets` properties,
   `adds_elements`, `removes`, `adds_links`, `reference_data` added) and the `feeds` its processor filters cover. A
   child Stroom can't load is listed with `unreadable` and the reason, so it doesn't hide the others.
2. **Contract**: what a child's output must contain for the template's own shared elements (a decoration XSLT, say)
   to work: per shared XSLT the element names and XPath expressions it reads (`select`, `match`, `test`,
   `group-by`; at most 60 expressions), plus `stage`, `backend`, `child_must_supply`, `schema_group`,
   `output_stream_type` and `reference_data`. A shared XSLT that is gone, empty or not well-formed is marked
   `unreadable` for that element; the rest of the contract stands.
3. **Shared XSLTs** (`SharedXslt`, read from up to 10 children): the documents the children's XSLTs pull in with
   `xsl:import` / `xsl:include`, each resolved as Stroom resolves an import (the XSLT document of that name). For
   each named template called: where (the element it writes, below `Event` or an indexing document's map), what it
   writes in full, the `stroom:meta` it reads, its parameters and those passed, and by which pipelines. Each
   `xsl:function` with its prefix's namespace, parameters and return type, and how the children call it. Also the
   template rules each shared document supplies, called or not.

* **Arguments**: `template_uuid` (required).
* **Returns**: `template_uuid`, `children`, the contract's keys, and `shared_xslt` with `shared_xslt_hint` (use the
  same named templates and functions, and don't map the elements they write: an element written twice fails schema
  validation, a JSON key twice is a duplicate field), or `shared_xslt_error` when they couldn't be read.
* **Refuses**: no `template_uuid`; a UUID the user can't view (`DocumentAccess.NO_PERMISSION`); a document that is not
  a pipeline (names its type).
* **Notes**: the read of the template is audited (`logView`). The shared XSLTs are an extra: failing to read them
  never fails the call.

### `describe_feed`

Core · read-only · checks View on what it reads; unlocked by Manage Processors (the feed's pipelines are found from
their processor filters; without it, only generated documentation named after the feed is read); an index's fields
are listed as a query lists them, which needs Use on the index · in no chat group (always offered) · Classes:
`DescribeFeedTool`, `FeedNames`, `ProcessorFilters`, `PipelineShapes`, `KeptMappings`, `GeneratedDocs`, `IndexDocs`,
`FeedDocumentation`

For questions about the data rather than building it (e.g. "what is User.DomainName, and how do I find domains
ending in example.com?"). The feed name is resolved without regard to case (`FeedNames.resolve`): a name that isn't
here offers up to three close matches in a form, plus a choice to correct it; none close, or no form, is refused
naming this Stroom, so a feed of another cluster stops the work rather than leading the agent to another. The feed's
pipelines are those whose processor filters read it (Feed `EQUALS`, `IS_DOC_REF`, a Dictionary through
`IN_DICTIONARY` with its imports, or the feed of the stream an Id term picks), then those reading their Events
(indexing, CEF output). For each: its kind (from the mapping kept with its XSLT, else its elements), its kinds of
event (from the translation mapping), and its generated documentation (`GeneratedDocs.generatedDoc`: the
`mcp-generated` doc of its name in its folder first, then any generated doc of that name, then one written by hand
beside it; never a draft still in a build). Indexing pipelines lead to their index docs, whose fields are listed as
Stroom has them from Elasticsearch or Lucene.

With `field`, the docs' rows about the field are found (index docs first: their `From` column gives the event-logging
path, which then finds the events doc's rows), with the index field's type, analyzer, keyword sub-fields and
`how_to_search` advice from its type (`FeedDocumentation.searchAdvice`: wildcards, CIDR for an IP field, what quietly
finds nothing).

* **Arguments**: `feed` (required, any case), `field` (optional: an index field, an event-logging path or a source
  field; omitted for an overview).
* **Returns**: `feed` (name, uuid, description, classification, streamType, status), `pipelines` (each `pipeline`,
  `uuid`, `kind`, `documentation` link), `event_kinds`, `purpose` (each doc's "Purpose and data", up to 2,000
  characters), `indexes` (name, uuid, backend, index_name, documentation, field count, `similar_fields`), and with a
  field `field`: `in_indexes`, `event_logging_paths`, `documented` (up to 12 rows), `mentioned` (up to 6 lines a
  doc), or `found: false` with a hint. `processor_filters` says when they couldn't be read; `note` when nothing
  documents the feed. Always a `hint` on how to answer.
* **Refuses**: no `feed`; a feed not in this Stroom (`FeedNames.notFound`).
* **Notes**: a pipeline Stroom can no longer load (seen in production: a stored property its element type lost) is
  listed with `kind: unknown` and the reason, rather than failing the whole description. With no filter naming the
  feed, up to 10 generated docs whose names hold the feed's name are read (pipelines are named after their feed). The
  feed read is audited.

### `find_reference_data`

Core · read-only · checks View on the XSLTs and pipelines it reads; unlocked by Manage Processors (the feeds each map
is loaded from come from processor filters; without it they are empty) · in no chat group (always offered) · Classes:
`FindReferenceDataTool`, `ReferenceData`, `PipelineIndex`, `PipelineShapes`, `PipelineTemplates`, `ProcessorFilters`

The reference maps the environment loads, for `stroom:lookup()` in a translation. It reads every XSLT the user can
view that writes `reference-data:2`, and from each `<reference>` takes the map name (literal, or the expression it
comes from), the key expression, the value's shape (text, or its element names) and whether it is a range. Each map
is tied to the pipelines whose own properties name that XSLT (`loaded_by`) and the feeds their filters read. Then the
pipeline references in use (`references_in_use`: feed, loader, and which pipelines use it), the loader pipelines
themselves (a complete pipeline with a `ReferenceDataFilter`, whatever it is called), and the template for new
reference-data pipelines (the first `find_pipeline_templates stage=reference` candidate). `xslt_lookups` works from
the other end: the lookups an XSLT makes.

* **Arguments**: none.
* **Returns**: `maps` (each `map`, `key`, `value`, `ranges`, `xslt`, `loaded_by`, `feeds`, `used_by`, `loader`),
  `references_in_use`, `loaders`, `reference_data_template` (`uuid`, `name`, `path`, `child_must_supply`), any
  `unreadable`, and a `hint` on wiring a lookup or building new reference data (or a Dictionary for a small static
  table).
* **Notes**: one document Stroom can't give (deleted since it was listed, a pipeline it can no longer load) is listed
  under `unreadable`, not the whole answer lost. No loader or template is assumed by name.

### `find_elastic_clusters`

Indexing · read-only · checks View on the cluster and index docs; with `test`, each cluster is reached with its own
connection settings and credentials, as its Test Connection button does · chat group indexing · Classes:
`FindElasticClustersTool`, `ElasticClusterTests`, `PipelineIndex`

Lists the Elastic Cluster docs the user can view with their connection URLs (never credentials), and the Elastic
Index docs that use each with `index_name`, `time_field` and `path`. It never creates or changes a cluster doc; the
description tells the agent to pick the cluster sibling sources use and confirm it with the user.

* **Arguments**: `test` (optional boolean: also run Stroom's connection test on each).
* **Returns**: `clusters` (each `name`, `uuid`, `urls`, `index_docs`, and with `test` a `test` of `ok` and `message`),
  any `unreadable`.
* **Notes**: at most 50 cluster docs and 500 index docs are read. A cluster that can't be reached is that cluster's
  `test` answer, not a failed call. A doc deleted since it was listed is named under `unreadable`.

## Builds and onboarding

### `start_onboarding`

Content · write (makes the build's folder) · checks View; the build folder is made in the workspace folder
(`stroom.ai.builds.workspaceFolder`) as the user, who needs permission to create folders there; sample streams are
read as the user (View Data, View on their feeds) · changes only its build · chat group build · Classes:
`StartOnboardingTool`, `WriteGuard`, `BuildPlan`, `BuildRecords`, `SampleProfiling`, `SampleStreamTexts`,
`SampleTexts`, `PipelineTemplates`, `AgentInstructions`

Starts onboarding a new source: profiles every sample, creates (or finds) the build's folder, remembers the build for
the session, and returns the plan with its first step and the standing instructions (AGENTS docs) for the folders
given. It has two paths:

* **Files first** (`files` given, no `samples` or `stream_ids`): the build is made now and the profile later. The
  result names the next calls: `create_feed`, `upload_sample files=<the paths>` (run in the user's terminal), then
  `start_onboarding` again with `build` and the `stream_ids`, which profiles the uploaded streams. This exists
  because a slow local model writing sample text into tool calls (seen: Gemma 4 31B at about 22 tokens a second took
  77 s per call, and 100 lines would have taken over ten minutes) is the bottleneck; files never pass through the
  model.
* **Profile now** (`samples` text, or `stream_ids`): sample streams are read whole up to 1,000,000 characters each
  (`StreamReader.MAX_SAMPLE_CHARS`) through `SampleStreamTexts`; text is read with `SampleTexts.asNamedSamples`. All
  samples are profiled together (`SampleProfiling.profileAll`, XML fragments with the environment's own wrapper).
  The result gives the format's parser (`BuildPlan.PARSER_FOR_FORMAT`), which templates here read it
  (`templatesReading`: the environment's own translation templates with that parser by name, else one whose parser
  can be swapped, else "ask the user", with `create_pipeline standalone='events'` as a last resort when there are
  none), and whether a text converter is needed.

* **Arguments**: `source_name` (required; names the build), `samples` (pasted text only: a list, or a map by file
  name; about 10 records and at most 8,000 characters a file), `files` (paths on the user's disk), `stream_ids`,
  `build` (default `onboard-<source name, lower-case, non-alphanumerics as hyphens, up to 40 characters>`),
  `folders` (promotion destinations, for the standing instructions), `kind` (`events`, `discovery` or `reference`;
  kept in the build record so the plan's steps follow it from the start).
* **Returns**: `build`, `folder`, `source`, `profile`, `parser`, `template`, `text_converter`, `plan` (the
  onboarding checklist, or the build's own steps once `kind` is given), `task_list`, `next`, `done: false`,
  `standing_instructions`, `hint`, `tools` (`BuildPlan.HIDDEN_TOOLS`), and `read` notes (a stream cut short, bytes
  its encoding couldn't decode) or `sample_note` (a pasted sample over 20,000 characters, for next time). The files
  path gives `files` and `then` in place of the profile.
* **Refuses**: no `source_name`; a `kind` outside the three; no sample at all (`ask the user for every file they
  have`); a sample that is a file path, an attachment reference, a file a reader cut short, or one text broken up by
  the tool call (`SampleTexts`); stream ids without View Data, or older than their feed (an earlier, deleted feed's
  streams).
* **Notes**: never call `profile_sample` as well; the description says so. With `kind` or `stream_ids`, `next` comes
  from the build itself (`BuildPlan.nextStep`), as the feed and samples may already exist.

### `start_build`

Content · write (makes the build's folder, records the feeds it works from) · checks View; folder made as the user in
`stroom.ai.builds.workspaceFolder`; feeds are found among those the user can view · changes only its build · chat
group build · Classes: `StartBuildTool`, `WriteGuard`, `BuildRecords`, `FeedNames`, `BuildPlan`, `AgentInstructions`

Creates (or finds) the build's workspace folder, where every write tool creates documents, and returns the standing
instructions that apply to the `feeds` and `folders` given. Any feed given that exists outside the build is recorded
in the build record as what the build works from (`BuildRecords.addSourceFeeds`, `source_feeds`): the plan then
doesn't send the agent to create a feed and upload samples, and promotion's filters name those feeds. Seen in VS
Code: without this, a test feed was made and a workspace file uploaded though the user had chosen production streams.
The build is remembered for the session (`BuildPlan.rememberBuild`), as `start_onboarding`'s is, so later calls that
name no build work on it. Found reviewing the tools: it remembered none, so a build started here wasn't the one
those calls fell back to.

* **Arguments**: `build` (required), `feeds`, `folders`, `kind` (`events`, `discovery`, `reference`; given again it
  changes the build's).
* **Returns**: `build`, `folder`, `uuid`, `works_from` (existing feeds), `not_found` (names not in this Stroom, "A feed
  this build is to create is fine"), `standing_instructions`, `kind`, `plan` (the onboarding checklist, or
  `BuildPlan.plannedSteps` once the kind is known), `task_list`, `next` (when the plan has one), `done: false`.
* **Refuses**: no `build`; a `kind` outside the three.
* **Notes**: asks nothing; over `/mcp` it isn't approved as a whole, as it changes only its build.

### `build_status`

Content · read-only · checks View (the build's documents are those the user can view); unlocked by Manage Processors
(whether the build's filters are made and running) and View Data (the sample's format, from its text; without it the
plan is made as before a sample is uploaded) · chat group build · Classes: `BuildStatusTool`, `BuildPlan`,
`BuildFacts` / `StroomBuildFacts`, `BuildChecks`

Where a build stands: every step of its plan, each as `done`, `to do` or `not needed`, read from what the build holds
(`BuildPlan.status`), with `next` (the first step to do and the one call that does it). See "The build layer" for how
the steps and `next` are worked out. `before_promotion` lists what `BuildChecks.checks` finds still missing: a
clean step of each pipeline's current code, documentation, an agreed Elasticsearch template and a verified index for
indexing pipelines, a Field mapping section that matches the current mapping and XSLT, and an XSLT that is still what
its mapping generates (`GeneratedDrift`). A check ending `(promotion waits for this)` blocks `promote_build`.

* **Arguments**: `build` (required), `full` (default false).
* **Returns**: by default compact (`BuildStatusTool.compact`): `next`, `before_promotion` and `steps` first, each step
  by its state only, each document on one line (`type 'name' (role) uuid, a working copy of ...`), then the rest:
  `build`, `sample` (format), `feeds`, `sample_streams` by type, `xslts`, `pipelines`, `events_streams` (up to 20),
  `works_from`. With `full: true`, each step's description and tools and each document's path.
* **Refuses**: no `build`.
* **Notes**: compact is the default because the full result (about 10 KB) was saved to the workspace and fetched again
  and again, as the agent couldn't keep it in view.

### `promote_build`

Content · write · checks View, Edit and Delete on what it moves, writes back and deletes; documents are moved, copied,
written back and deleted through Stroom's explorer and stores as the user, who also needs to create folders in the
destinations; unlocked by Manage Processors (the promoted pipelines' filters are made as the user, only once they
approve) · confirms itself · chat group build · Classes: `PromoteBuildTool`, `PromotionLayout`, `PromotionProcessing`,
`DocumentPlaces`, `DocumentWrites`, `WriteGuard`, `BuildRecords`, `BuildChecks`, `Consent`, `IndexingRuns`,
`OutputFeeds`, `XsltHistory`, `DocumentationVersions`

Promotes a build out of the workspace in two calls. The first works out the whole layout and returns it without
changing anything (`status: needs_review`, a `layout` tree from `PromotionLayout.promotionTree`, and a `review_id`,
the first 12 hex characters of the tree's SHA-256). The agent shows the tree in the chat; once the user confirms it,
the second call passes `reviewed=<review_id>` and the user approves a short form bound to that layout (build, document
count with the review id, folders, role subfolders, warnings, processing after promotion). `reviewed=true`, or an id
for another layout, gets the review again (seen: `reviewed=true` on the first call, and the user approved a form whose
layout was never shown). Any change of destinations changes the tree and so the id.

Where each document goes (`plan`):

* A working copy (`working_copy_of`) of a pipeline is deleted after its documents are written back (the original
  keeps running); a working copy of anything else is **written back**: the original is copied into
  `<workspace>/backups` as `<name> backup <yyyyMMddHHmmss>` (UTC, tagged `mcp-generated`), then the copy's `data`,
  `converterType`, `documentation` and `description` are written over it, and the copy deleted. An unchanged copy is
  just deleted. A copy whose original was changed since it was taken is put to the user first (below).
* `keep` for a document UUID leaves it in the workspace (seen: a test feed made while fixing had nowhere to go but
  production, and the agent never promoted the fix). A build on existing feeds keeps its feeds there unless given a
  folder, as does any `-MCP-TEST` feed.
* Otherwise a destination by document UUID, then the version folder proposed for a new version, then by type. Source
  notes and reference docs go beside the build's only feed; a pipeline's documentation goes with that pipeline; a
  verification dashboard goes where the index it searches goes, or with the build's indexing pipeline when the index
  is from outside the build (seen in VS Code: promotion stopped for want of the dashboard's destination); the
  documentation of a production pipeline or existing index goes beside it.
* A new version of a production document (`version_of`, from `copy_pipeline`) goes in a version subfolder beside its
  predecessor named by its version (`…-V1.1-Events` → `V1.1`), and the version's other new documents headed for that
  place join it (`PromotionLayout.versionFolders`, `joining`). Documents given a destination of their own keep it,
  as does a version whose type is given a destination elsewhere than beside its predecessor, so a version and its
  other documents aren't split (seen in testing: with the version always put beside its predecessor, TWITTER's V1.1
  pipeline went under /Feeds/Twitter while its XSLT and documentation went to System/MCP Testing/Twitter, the folder
  given for their type).
* Role subfolders (`Events`/`Records`, `Indexing`, `CEF`, `Reference`) are kept under each destination only when
  documents of more than one role move, unless `role_subfolders=false`.

**Production changed since the copy.** `copy_pipeline` records in each working copy's build record entry the version
its original had when copied (`BuildRecords.COPY_OF_VERSION`, `copy_of_version`). Before the layout is built (on the
first call, so the reviewed tree shows the choice), each document to be written back whose original's version is now
different (`changedSinceCopied`; e.g. edited by hand in production) is put to the user in a form of its own
(`askAboutChangedOriginals`, through `Consent.choose`): who changed production and when (the original's
`updateUser` and `updateTimeMs`), then, for a document with code, what overwriting would lose from production and
what the working copy brings (`CodeDiff`, at most six lines of each, at most 1,000 characters, so the form can
always be shown). The choices:

* "Overwrite production with the working copy": written back as below; the production change is then only in the
  backup.
* "Keep production as it is": the step becomes a keep, so production isn't touched; the working copy, and the
  working-copy pipelines that would have been discarded, stay in the build, for the change to be made again on a
  fresh working copy.

The choice is kept in the copy's build record entry (`original_choice`) against the original's version, so the
approval call doesn't ask again; it is cleared once the promotion is carried out, so promoting the build again asks
afresh, and a further change in production asks again. Promotion already needs a client that can show forms (its
approval), so a client without them is refused. A copy taken before the version was recorded has none, and is
written back without asking. Asked for by the user: before, the copy was written over a production edit made after
it was taken, and the edit survived only in the backup; they chose a question that explains the difference over a
refusal.

Every destination is resolved before approval (`WriteGuard.resolveFolder`), and the folders to create are listed in
the tree, so a missing folder can't stop a promotion half way. On approval, in order: pending changes are consolidated
(`consolidateVersions`: one new line in each XSLT's version history, one new row in each doc's version control);
missing folders are created (one made since the review is used, never twinned; tagged `mcp-generated`, not
`mcp-managed`); each promoted pipeline has its development batch size taken off (`IndexingRuns.developmentBatch`)
and its StreamAppender `feed` property removed where it named a build feed (`OutputFeeds.restoreOutputFeed`: in
production its Events go into the input's feed); then write-backs, moves (untagged `mcp-managed`, so the agent may no
longer change them directly), discards and keeps. The build's record entries go, the build folder is removed if empty,
and `PromotionProcessing.createPromotionFilters` makes a feed-wide filter per promoted pipeline, **disabled**, with no
minimum creation time.

* **Arguments**: `build` (required), `destinations` (object, or JSON text: type or document UUID → explorer path, in
  any form `WriteGuard.folderParts` takes, or `keep`), `reviewed` (a `review_id`), `role_subfolders` (default true).
* **Returns**: first call `{status: needs_review, build, layout, review_id, warnings?, processing_after_promotion?,
  hint}`. After approval: `build`, `layout`, `folders` (each with a link to open it), `updated_in_place`, `promoted`
  (what was done), `promoted_with_warnings`, `processing_filters` (each with `pipeline_link`, and `before_enabling`
  for an index that must start fresh) with a `next` telling the user to review and enable them, and `user_actions`
  (buttons: the filters, and disabling an earlier version's enabled filters "if you choose").
* **Refuses**: no `build`; a build with no documents; `destinations` that isn't an object; every document without a
  destination, named in one refusal with the destinations already placed (seen: Delinea SecretServer, one failed call
  per type); a blocking check (`Not promoted: ... once promoted, the pipeline's documentation can only change through a
  working copy`), because an agent once promoted with stale documentation and then made a second doc trying to fix
  it. A hand edit to an XSLT only warns, so a user's own edits never hold a promotion up. A working copy whose
  original changed since it was taken, over a client that can't show forms (or when the form would be too large to
  show): `Not promoted: <what changed, the difference and the choice> Promotion needs a client that can show forms to
  ask the user.`; no choice made in the form: `The user made no choice: ...`. A failure part way
  is refused with what was done before it and what is still in the build (each step is its own write).
* **Notes**: `PromotionProcessing.promotionProcessing` decides what each pipeline goes on processing: the feeds and
  stream types of its sample filters (the feed's own stream type, e.g. Raw Reference), or for a build on existing data
  its source feeds, never a test feed (seen in VS Code: the promoted filter selected the test feed). An indexing
  pipeline reading Events is filtered by its source pipeline (`Pipeline IS_DOC_REF`), not a feed. A pipeline with an
  enabled filter already selecting what arrives gets no second one (it would process everything twice). An index read
  by a pipeline promoted with its events pipeline has the events pipeline's filter made first (seen in e2e: both
  enabled together, the index held the sample twice). An index starts fresh: `before_enabling` says to drop (or empty)
  it first, with a button.

## Samples, surveys and feeds

### `profile_sample`

Processing · read-only · checks View; unlocked by View Data (sample streams given as `stream_ids` are read as the
user, who needs View Data and View on their feeds); text is profiled as given · chat group build · Classes:
`ProfileSampleTool`, `SampleProfiling`, `SampleStreamTexts`, `SampleTexts`, `SampleProfiler`, `FragmentWrappers`

Profiles raw data without saving anything: format, record structure, each field's fill rate, inferred type and
examples, timestamp patterns, the parser and template to use, whether a text converter is needed, and for JSON the
parser setting (see "Sample analysis"). Several files are profiled each and together, reporting fields and timestamp
shapes only some files have. XML fragments come with the environment's own wrapper (`SampleProfiling.withFragmentSetup`
with `FragmentWrappers`): an existing XML_FRAGMENT converter outside the workspace (an event-logging:3 one for
fragments that are Events already, else the most used), else the standard one, an `<Events>` wrapper in the build's
chosen event-logging version or the newest held (seen: a records:2 wrapper proposed where every wrapper in the
environment was an event-logging:3 one).

* **Arguments**: `sample` (one file's text), `samples` (several files: a list, or a map by file name), `stream_ids`
  (read instead of text). Text is about 10 records and at most 8,000 characters a file.
* **Returns**: the profile (`format`, `records`, `fields`, `suggested_parser`, `text_converter`, ...; with several
  files, `files` and `differences`), `read` notes for streams, `sample_note` for pasted text over 20,000 characters.
* **Refuses**: nothing given; a sample that is a path, attachment reference, cut-short file or broken-up text; stream
  ids without View Data or older than their feed; text that starts as JSON but isn't valid JSON, with where and why
  (seen: a FortiOS file retyped by the agent with `\+`, profiled as key=value).
* **Notes**: onboarding a source, `start_onboarding` profiles itself; each `profile_sample` call sends the text again,
  so the description says not to call it first.

### `survey_feed`

Processing · write with a `build` (keeps the survey doc), read-only without one · requires View Data; checks View on
the feed; the survey doc is made or changed in the build's folder as the user · changes only its build · takes a run
slot · chat group build · Classes: `SurveyFeedTool`, `FeedSampling`, `RecordSplitter`, `Shapes`, `SurveyDoc`,
`SurveyDocuments`, `FewerStreams`, `StreamReader`, `Consent`

Samples an existing feed's streams spread over its lifetime and groups their records into shapes (kinds of event),
saying plainly whether the feed is covered yet. Streams are picked in bisection order (newest, oldest, middle,
quarters ...) by `FeedSampling.pickSpread`, skipping those already read; with `stream_ids` exactly those streams are
read. For each stream up to `max_parts_per_stream` parts are read with `StreamReader.readHead`, split into records by
`RecordSplitter` (same detection as the profile), and added to `Shapes` (see "Sample analysis" for how a shape is
signed). Where a part is longer than its head and its format is one record a line (delimited, JSON lines, key=value,
CEF, CRI, unknown text), up to 8 windows of 250,000 characters spread to the part's end are read too
(`StreamReader.readWindows`), their records placed where they are, so a kind of event deep in a big stream can still be
found. A part in a different format from the first is skipped and said.

**Coverage.** A call is `saturated` when a call with known shapes reads a full `max_streams` with nothing new, when
fewer streams were left to pick than asked (the feed's last ones), or when the given `stream_ids` were all read. The
feed's last streams and given streams are read whole up to 2,000,000 characters and 10,000 records; a feed's other
streams up to 250,000 characters and 1,000 records a part. A given stream, or a last stream, read only in part leaves
the result `NOT COVERED`, never "covered" (seen in Ask Stroom AI: 334 of a stream's 1,000 records read, reported
covered; in production a 750 KB sample read to its 108th record hid most of its kinds of event). When the cap was
already the maximum (5,000,000), the advice is to step records further in and rely on `review_coverage` once
processed, rather than survey again at the same size (seen: the advice looped).

**Fewer streams.** Fewer than 10 streams a call (a smaller `max_streams`, or fewer than 10 of an existing feed's
streams by id) is confirmed by the user in a form, but only when the feed holds more streams than that (seen: a
one-stream feed's user asked to agree to survey that one). The agreement holds for that user, feed and count for four
hours in this node's memory (`FewerStreams`). Streams of a feed a build made (its uploaded sample) are read as they are.

**The survey doc.** With `build`, the survey is kept in the build's Documentation doc `<feed> - Survey` (`SurveyDoc`
format, `SurveyDocuments` through `WriteGuard`), and an existing one is continued: its streams are skipped and its
shapes known. Kinds the user chose to leave untranslated (`set_shape_handling`) are marked `handling: drop` and their
locations carry `expect: none`. The doc's format is stored data, so it changes only in ways a survey doc already
kept still reads (see `SurveyDoc` under "Sample analysis").

* **Arguments**: `feed` (or `stream_ids`), `stream_type` (default Raw Events), `stream_ids`, `max_streams` (1 to 100,
  default 10), `skip_stream_ids`, `known_signatures`, `max_chars_per_stream` (1,000 to 5,000,000),
  `max_parts_per_stream` (1 to 20, default 3), `max_records_per_stream` (10 to 10,000), `examples_per_shape` (1 to 10,
  default 2), `build`.
* **Returns**: `feed`, `format`, `time_range`, `streams_read`, `records_read`, `records_in` (an XML document's root
  with its namespace, which the examples inherit), `saturated`, `coverage`, `per_stream` (each stream's records,
  parts, `head_only`, `new_shapes`, `windows`, and any undecodable bytes), `quoted_newlines`, `shapes` (up to 60:
  `signature`, `new`, `count`, `share_percent`, `streams`, `example` up to 500 characters, `locations`), `new_shapes`,
  `locations` (stream, part, record, shape, for `step_records`; only new shapes once some are known), `hint`. With a
  build, the survey doc's details too.
* **Refuses**: no View Data; neither `feed` nor `stream_ids`; an argument out of range (`<name> must be a whole number
  from <min> to <max>, not '<value>'`); a stream id that doesn't exist; no streams of the type in the feed; nothing
  readable in the streams picked; the fewer-streams form declined.
* **Notes**: an unreadable stream (the oldest are likeliest to be on a volume Stroom can no longer read) is noted in
  `per_stream` and the survey goes on. Delimited records with quoted values over several lines are reported in
  `quoted_newlines`, as a Data Splitter built from lines without them split each such record in processing. Stops
  between streams and parts when its thread is interrupted (`The survey was stopped: the call was cancelled.`), and
  at the session's time limit for a call returns what it has read as `NOT COVERED YET: this session's time limit for a
  call (...) stopped the survey after N of the M streams picked ...`, the survey doc keeping it. Every stream read is
  audited. A delimited field may be any length (seen: one 250,000-character URL query stopped a whole part being
  surveyed).

### `set_shape_handling`

Processing · write · checks View and Edit on the build's survey doc · confirms itself · chat group build · Classes:
`SetShapeHandlingTool`, `SurveyDoc`, `SurveyDocuments`, `Consent`, `BuildPlan`

Records in the build's survey doc that some kinds of event are left untranslated on purpose (`drop`, with the user's
reason) or no longer are (`translate`). The user confirms in a form listing each kind with its record count and share.
Afterwards `survey_feed` marks those kinds and gives their locations `expect: none`, and `step_records` counts such a
record clean when it produces no Event (and flags it if it does). The mapping still needs a drop rule for them
(`build_translation_xslt`), so they don't reach the "no mapping matched" warning.

* **Arguments**: `build`, `feed`, `shapes` (signatures, or text found in exactly one signature, e.g.
  `loggerName=org.jgroups`), `handling` (`drop` or `translate`), all required; `reason` (required for `drop`).
* **Returns**: `feed`, `handling`, `shapes` (the signatures resolved), `left_untranslated` (every dropped kind with its
  reason), `locations` (the kinds' examples to step, with `expect: none` for drop), `hint`, and `next`.
* **Refuses**: a missing argument; `handling` other than the two; no survey of the feed in the build (`run survey_feed
  with build first`); a shape that matches no signature or several (`SurveyDoc.resolve`); `drop` without a reason; the
  form declined.

### `create_feed`

Content · write · checks View and Edit; the feed is created in the build folder as the user, who needs Create there
and Edit on the feed · confirms itself · chat group build · Classes: `CreateFeedTool`, `BuildFeeds`, `WriteGuard`,
`BuildRecords`, `Consent`, `BuildPlan`, `OutputFeeds`

Creates a feed in the build folder under the name the agent proposes; the user confirms or corrects the name in the
form itself (`Consent.Request.withEditable`), so the agent should call this rather than ask for the name in the chat.
A corrected name is checked as the proposed one was. Before anything is asked, a feed of the name already in this
build is returned as it is (`existing: true`: an agent's retry carries on with it), and a feed of the name anywhere
else is refused saying where (seen: Stroom refused the duplicate only after the user had confirmed, as a bare 500).
A feed differing only in case is refused too (`BuildFeeds.refuseCaseTwin`; seen: a sample filed under an earlier
spelling, and the agent spent the session looking for a second feed). Stroom's feed-name rule is applied at create; a
rejected name comes back with the rule (`Stroom rejected the feed name: ... Propose a name that matches the pattern
shown.`).

A build on existing feeds (`start_build feeds=`) makes no feed of its own, with two exceptions: `user_samples` (the
sample files the user chose to use instead of that feed's data, kept in the build record so `upload_sample` takes
samples for this feed only), or `test_feed=true` (or `stream_type` Events) for the test feed its Events are written
to, under the user's name (`BuildFeeds.makeTestFeed`, the source feed's settings; nothing is uploaded to it).

* **Arguments**: `build`, `name` (required), `encoding` (default UTF-8), `stream_type` (default Raw Events),
  `description`, `user_samples`, `test_feed`.
* **Returns**: `type`, `uuid`, `name`, `stream_type`, `encoding`, `link`, a button to open the feed (upload sample files
  on its Data tab), `large_files` (how sample files get there: `upload_sample files=`, or the Data tab's Upload when
  there's no terminal), `note` when Stroom still holds streams of an earlier, deleted feed of the name (the UI doesn't
  show them; they are never the build's samples), and `next`. A test feed gives `test_feed: true`, `works_from` and a
  hint.
* **Refuses**: a missing `build` or `name`; a name taken elsewhere or a case twin; on existing data without
  `user_samples` or `test_feed` (`BuildFeeds.worksFromMessage`; asked for by the user after an agent onboarding an
  existing feed uploaded workspace files instead); `test_feed` in a build that works from no existing feed; the form
  declined.
* **Notes**: up to 50 of a name's earlier streams are looked at for the note.

### `upload_sample`

Processing · write (a stream in the build's feed) · requires Import Data; checks View and Edit on the feed; data is
stored as the user, as Stroom's own Upload stores it, and must pass Stroom's receipt checks; `files` are posted to
`/mcp/upload`, which needs Use MCP Tools · changes only its build · chat group build · Classes: `UploadSampleTool`,
`BuildFeeds`, `FeedUploads`, `SampleUploads`, `McpUploadServlet`, `AiSampleUploadDao`

Sends sample data into a build's feed as the real source's data is received. Only a build's feeds take samples
(`BuildFeeds.buildFeed`), never a production feed whose filters would pick the data up; in a build on existing data,
only a feed made for the user's own sample files. Two ways:

* **Text** (`sample`, only text the user pasted into the chat): checked as sample data, then sent at once through
  `FeedUploads.send`: Stroom's receipt checks for the feed as for data posted to the datafeed
  (`AttributeMapFilterFactory`: the feed's status or the data receipt policy, per `stroom.receive.receiptCheckMode`),
  then stored as the data browser's Upload stores it (`DataService.upload`). The receipt id is read back from the
  stream's receipt headers.
* **Files** (`files`, paths on the user's disk, any size): nothing is sent now. Each file gets a curl command (bash and
  PowerShell) for the user's terminal that posts it whole to `/mcp/upload/<id>?file=<name>` as the user, with their
  API key from `$STROOM_API_KEY` (never in the chat or the command). What is waiting is held in the database
  (`SampleUploads`, tables `ai_sample_upload` and `ai_sample_upload_file`), so any node takes the post and a restart
  forgets nothing. Commands last `stroom.ai.sampleUploads.commandsLast` (15 minutes), each file once; a file over
  `maxUploadMb` (100) is refused with 413; `maxPendingPerUser` (20) is counted across nodes. A post claims its file (a
  conditional update, so of two posts only one stores it); a claim left by a node that stopped part way may be taken
  again after 10 minutes (`SampleUploads.CLAIM_GOES_STALE`). A post matching no waiting file is refused with 403.
  Spent sets are deleted by the AI Chat Retention job. The feed is marked in the build record as having file samples
  (`sample_files`), after which text is refused for it.

Files exist because a model's file reader cuts text (VS Code's `read_file` cuts a line at 2,000 characters: a 755 KB
one-line JSON file arrived as three records, the last completed with made-up values; another agent uploaded 2 of each
file's 985 records).

* **Arguments**: `feed` (required), `sample` or `files`, `headers` (extra receipt headers, readable with
  `stroom:meta()`), `stream_type` (Raw Events, or Raw Reference), `effective_time` (reference data; default
  `2000-01-01T00:00:00.000Z`, so it covers sample events uploaded before it; seen: without one a reference upload took
  effect after the events, whose lookups then found nothing).
* **Returns**: text: `feed`, `receipt_id`, `stream_id`, and what was sent, with `next`; or `stream_id` absent and a
  hint when the stream isn't visible yet (it is looked for once, not polled). Files: `feed`, `expires_in_minutes`,
  `max_mb`, `commands` (per file `file`, `bash`, `powershell`), and a hint (run each exactly as given; on Windows
  `--ssl-no-revoke` for `CRYPT_E_NO_REVOCATION_CHECK`, never `--insecure`).
* **Refuses**: no `feed`; neither `sample` nor `files`; no Import Data (checked first); a sample that isn't sample
  data; a path no command can carry safely (`FeedUploads.checkPaths`); a feed not in a build, or not one for samples;
  text for a feed whose samples are files (seen: the terminal command failed and the agent uploaded two records of
  each file as text); `files` while the MCP endpoint is off or the user lacks Use MCP Tools (the user then uploads in
  Stroom); too many command sets waiting; Stroom's receipt policy rejecting or dropping the data (`Stroom's receipt
  policy drops data for feed ...: nothing was stored`).
* **Notes**: no command for Stroom's datafeed is ever given, as the agent has no token for it (seen: an agent ran
  such a command three times from its own terminal). Each file post is logged on the node that takes it (who, file,
  feed, stream).

### `record_source_notes`

Content · write (Documentation docs in the build) · checks View and Edit; docs created and changed as the user; a
document given by uuid is read as the user · changes only its build · chat group build · Classes:
`RecordSourceNotesTool`, `SourceNotes` (`stroom.ai.impl.tools.draft`), `WriteGuard`, `DocumentWrites`,
`EventVersions`

Keeps vendor or event reference documentation the user gave in Stroom, and the notes condensed from it, as
Documentation docs in the build: each document verbatim as `<source> reference - <title>` (or, given a `uuid`, the
document already in Stroom is named, not copied), and `<source> source notes` holding a markdown field dictionary,
event catalogue and sources, then the notes as a JSON block (an HTML comment) the tools read back. The notes feed
`draft_translation_mapping build=` (fields where the dictionary puts them, a rule per catalogued event that gives the
field and value showing it), `build_translation_xslt build=` (a mapping checked against the catalogue) and
`write_documentation` (source fields with their meanings). Promotion puts them beside the feed. Called again for the
same source (a long manual a part at a time), the docs are updated, not made twice: earlier fields and events stay
unless given again.

Only for documentation the user gave; notes guessed from the sample aren't documentation, and the draft would follow
them over the sample's own values.

* **Arguments**: `build`, `source`, `summary` (required); `fields` (each `field` and `meaning` required, then `type`,
  `example`, `event_logging_path`, `values`); `events` (each `event` and `description` required, then
  `event_detail`, `type_id`, `field`, `value`, `action`, `success`); `references`; `documents` (each `title` and its
  `text`, or a `uuid`; `source` optional). Lists may be one object, or JSON text.
* **Returns**: `type`, `uuid`, `name` of the notes doc, `fields` and `events` counts, `documents_kept`, a `hint`
  (events without a field and value can't become rules), and `next`.
* **Refuses**: a missing required argument; a note missing a required key (`fields[0].meaning is required`), not an
  object, or a list given as text that isn't JSON; a document without `title`, or without text or uuid; a uuid not in
  this Stroom; an event catalogue that can't be drafted from (`SourceNotes.catalogueProblems`): an `event_detail` the
  event-logging schema lacks as an action element taking Data (e.g. `Allow`; Network needs its action,
  `Network/Permit`), or two events with the same field and value. The schema is the build's chosen version, else the
  newest held (`EventVersions.chosenOrLatest`); when it can't be read, only the twins are checked.
* **Notes**: an existing notes or reference doc is changed only when it is the tools' own and in the build
  (`WriteGuard.checkInBuild`). Names are cut to 200 characters.

## Processing a build's pipelines

### `create_processor_filter`

Processing · write · requires Manage Processors; checks View and Edit; only for a pipeline in a build; emptying a
build's own Lucene index needs Manage Index Shards; unlocked by View Data (reading why Elasticsearch refused earlier
runs; without it the index is taken to hold what they sent) · confirms itself · chat group processing · Classes:
`CreateProcessorFilterTool`, `ProcessorFilterWrites`, `ProcessingSources`, `ProcessingApprovals`, `IndexingRuns`,
`IndexTemplateAgreements`, `OutputFeeds`, `PipelineOutputs`, `UserActionEntries`

Creates a processor filter on a pipeline in a build (`ProcessorFilterWrites.managedPipeline`), through Stroom's
`ProcessorFilterService` as the user, as the data browser's Process action makes one. Feeds and pipelines are named
by reference (`IS_DOC_REF`), so Stroom keeps the dependency and a renamed feed is still the one selected. Two scopes:

* **Sample** (`stream_ids`): exactly those streams, one task at a time. Streams the pipeline already processed (it
  has output for them, or a filter already selects them) are refused: use `reprocess_streams`.
* **Feed** (`feed`, `stream_type`, `created_after`): the feed's streams of the type created after the bound, at most
  2 tasks at a time (`ProcessorFilterWrites.MAX_FEED_FILTER_TASKS`, which the description states). `stream_type`
  defaults to Events for an indexing pipeline, else the feed's own type. The bound is raised to the feed's creation
  (seen: one agent guessed midnight after its sample arrived and the filter selected nothing; another chose a date
  before the feed existed, which selects an earlier, deleted namesake's streams). A translation pipeline's feed
  filter that selects none of the feed's streams still to process is refused, as is the same
  feed-wide filter twice (seen: made twice, neither selecting anything).

Gates, in order: the pipeline's current code has a recorded clean step (`BuildRecords.steppedClean`); a translation
pipeline processes only the build's own feeds or the existing feeds it works from (`ProcessingSources.buildFeedsOnly`),
and then writes its Events to the build's test feed (`OutputFeeds`: the StreamAppender's `feed` property, the user's
named test feed or `<FEED>-MCP-TEST`, the next free number if taken); stream ids older than their feed are refused; an
indexing pipeline reading Events needs `source_pipeline_uuid` (`ProcessingSources.eventsSource`: an events pipeline
the tools built, or another the user confirms in a form, consent action `index_events_from_other_pipeline`), every
given stream must be an Events stream it produced, superseded Events are refused naming those now in their place
(seen in production: indexing given earlier Events after the parent was updated), and the filter gets `Pipeline
IS_DOC_REF <source>` (a feed-wide one then has no feed condition).

Then how it runs (`ProcessingApprovals.reprocessRuns`): an events, records, indexing or discovery pipeline runs on the
user's approval: the form shows the scope, priority, max tasks, where Events go, what a Kafka pipeline sends (stepping
sends nothing; processing does), and the index template; once approved the filter is created **enabled** (asked for by
the user: filters left disabled had to be enabled by hand between steps). Any other pipeline (CEF output, reference
data) gets a **disabled** filter and no form: enabling it in Stroom is the user's approval, and the reply says where,
what it will do and what comes first (`before_enabling`, with buttons). A new processor is enabled with it, as a
disabled processor runs none of its filters (seen: the user enabled the filter and nothing ran).

For Elasticsearch, the index template agreed with the user for the pipeline's current code must exist
(`ProcessingApprovals.committed`; the refusal says what is missing); with `template_committed_by_user=true` the user
instead confirms in a form that their own is committed, and that is kept with the pipeline. Without an Elasticsearch
template on the cluster first, the index is made with dynamic mappings the template can't change later. A sample into
an index this pipeline already wrote to (`IndexingRuns.freshIndex`) needs it to start fresh: a `drop_first` reply
comes before the approval, with a button (the `DELETE _data_stream/<index>` or `DELETE <index>` request for the
cluster admin), and the agent calls again with `index_dropped=true` once the user says it is done. A build's own Lucene
index is emptied by the tool once approved (`IndexingRuns.deleteIndexShards`, node by node through `IndexResource`).
An index Elasticsearch refused every earlier write to holds nothing of the pipeline's (read from its Error streams'
text), so it isn't dropped. A build's Elasticsearch indexing pipeline gets a batch size of 10 while developed
(`IndexingRuns.developmentBatch`), so a rejected document is reported with Elasticsearch's full reason.

* **Arguments**: `pipeline_uuid` (required), `stream_ids` or `feed` (exactly one), `stream_type`, `created_after`
  (ISO; without an offset taken as UTC), `priority` (1 to 100, default 10), `source_pipeline_uuid`, `index_dropped`,
  `template_committed_by_user`.
* **Returns**: `filter_id`, `pipeline`, `scope`, `events_from_pipeline`, `output_feed` with `output_note`,
  `batch_size`, `enabled`, a hint (`wait_for_processing with filter_id=`), and `next`; a disabled filter adds
  `pipeline_link`, what enabling does, `before_enabling` and `user_actions`; a `drop_first` reply in place of the
  approval when the index must start fresh.
* **Refuses**: no `pipeline_uuid`; a pipeline not the tools' own in a build; both or neither scope; `priority` out of
  range; no clean step (`step_sample (or step_records) ... until the verdict is clean`); a feed outside the build
  (`start_build feeds=`); streams older than their feed; a feed scope without `created_after`; streams already
  processed; the source checks; no agreed template; a duplicate or empty feed filter; the form declined; Stroom
  refusing to list or create the filter.

### `set_processor_filter_enabled`

Processing · write · requires Manage Processors; checks View; only a filter on a pipeline in a build · changes only
its build · chat group processing · Classes: `SetProcessorFilterEnabledTool`, `ProcessorFilterWrites`,
`ProcessorFilterAccess`

Disables a processor filter on a pipeline in a build, through `ProcessorFilterService.setEnabled` as the user.
Enabling is the user's, in Stroom: `enabled=true` is refused with the pipeline's link, telling the agent to ask the
user to enable it on the Processors tab and then `wait_for_processing`.

* **Arguments**: `filter_id` (required), `enabled` (false; true is refused).
* **Returns**: `filter_id`, `enabled: false`.
* **Refuses**: no `filter_id`; a filter the user can't see (no Manage Processors, deleted, or none of that id); a
  pipeline not the tools' own in a build; `enabled=true`; Stroom refusing the change.
* **Notes**: asks nothing; over `/mcp` it isn't approved as a whole, as it changes only its build.

### `reprocess_streams`

Processing · write · requires Manage Processors; checks View and Edit; only for a pipeline in a build; emptying a
build's own Lucene index needs Manage Index Shards; unlocked by View Data (as `create_processor_filter`) · confirms
itself · chat group processing · Classes: `ReprocessStreamsTool`, `ProcessorFilterWrites`, `ProcessingSources`,
`ProcessingApprovals`, `IndexingRuns`, `IndexTemplateAgreements`

Processes up to 10 streams again through a pipeline in a build after a change, one task at a time, with a new filter
selecting them by id (made as `create_processor_filter` makes one). As for `create_processor_filter`, the
pipeline's current code must have a recorded clean step (`CreateProcessorFilterTool.steppedClean`, over
`BuildRecords.steppedClean`; code that can't be read counts as not stepped): a change to its XSLT or converter since
the last clean step needs stepping again first. The description says so. Asked for by the user, after streams were
found reprocessed through code never stepped.

Stroom marks the pipeline's earlier outputs for those streams deleted once the new ones are written;
`wait_for_processing filter_id=` then counts only the new ones.
Stroom's own reprocess filters (the data browser's Reprocess action) aren't used: they select the output streams a
processor made, which an indexing pipeline doesn't have, and give no filter id to count by.

It shares `create_processor_filter`'s source, test-feed and template checks and its approval rules: an events, records,
indexing or discovery pipeline runs on the user's approval (created enabled); any other is created disabled for the
user. An index this pipeline wrote to starts fresh, because Stroom's purge on reprocess doesn't apply to a second filter on the same
streams nor remove a Lucene index's documents (seen: an index held each event twice after its XSLT was fixed and the
stream reprocessed): a `drop_first` reply comes before the approval, then `index_dropped=true`; a build's own Lucene
index is emptied once approved.

* **Arguments**: `pipeline_uuid`, `stream_ids` (required; 1 to 10), `source_pipeline_uuid`, `index_dropped`,
  `template_committed_by_user`.
* **Returns**: `filter_id`, `pipeline`, `streams`, `max_tasks`, `enabled`, `batch_size`, a hint, `next`; for a disabled
  filter what the user does first, with buttons; or a `drop_first` reply.
* **Refuses**: no `pipeline_uuid`; a pipeline not the tools' own in a build (promoted pipelines too: reprocessing
  production data is the user's); 0 or more than 10 streams (`Give 1 to 10 streams per call`); streams the pipeline
  hasn't processed (`use create_processor_filter`); no clean step of the current code (`... has no clean step of its
  current code recorded: step_sample (or step_records) over the streams until the verdict is clean, then
  reprocess`); feeds outside the build; streams older than their feed; the source and template checks; the form
  declined.

### `wait_for_processing`

Processing · read-only (see Notes) · requires Manage Processors; checks View; streams are seen only where the user may
view their feeds · takes a run slot · chat group processing · Classes: `WaitForProcessingTool`, `ProcessingStatusTool`,
`StreamProcessing`, `PipelineOutputs`, `StreamSummaries`, `ErrorTriage`, `IndexingRuns`, `BuildRecords`,
`UserActionEntries`

The gate before the next stage: waits until each input stream is done, then reports per input its output (Events, or
Reference, or what the pipeline's StreamAppender writes) and Error streams. Each poll (every 3 s) reads the filters'
status (`ProcessingStatusTool.status`), each input's tasks (`StreamProcessing.tasks`, by `filter_id` when given) and
outputs. An input is done once its own task has ended (complete or failed) or its output is there (Stroom deletes old
tasks), but never while one of its outputs is still locked (being written) or a task for it is still to end. Seen: a
"finished" with a 100,000-event stream still being written, unreadable, its Error stream not yet made; and in e2e a
promoted pipeline's earlier tasks were all complete before its filter made one for the new stream, so the gate passed
with nothing indexed.

It returns at once, without waiting, when nothing will process the streams: no filter (`gate: fail`, create one
first); only disabled filters would (`gate: waiting`, `waiting_for` the user to enable it, with the pipeline link and a
button); or no enabled filter selects a stream still without output (`StreamProcessing.unselected`, naming which
filter misses it and why: feed or type, time bound, disabled).

Whether output is expected comes from the pipeline (`PipelineOutputs.writesOutput`): an indexing pipeline, or one
with no StreamAppender (sending to Kafka), writes only Error streams, whatever `expect_events` says (seen: such
pipelines each waited out two timeouts with every task complete).

* **Arguments**: `pipeline_uuid`, `stream_ids` (required), `timeout_seconds` (5 to 900, default 180; outside is
  brought within), `expect_events`, `filter_id` (only count this filter's outputs, e.g. after `reprocess_streams`),
  `output_type`.
* **Returns**: `pipeline`, `finished`, `streams` (each `input`, `events`, `errors`, `output_type`), `output` (when none
  is expected), `gate` (`pass`, `fail` or `waiting`), `problems`, `look_into` with `look_into_hint`, `hint`,
  `batch_size` (when restored), `next`.
* **Problems** that fail the gate: an input still pending; no output and its task ended (`a failed task, a fatal error,
  or every record filtered out`); more than one output (processed more than once: pass `filter_id`); Error streams
  where no output is expected; blocking error groups beside an output; and, for a pipeline in a build, current code
  that hasn't stepped clean (seen: a filter kept processing after the XSLT was replaced, and the agent documented and
  indexed its output).
* **`look_into`**: the warnings and errors written beside an output, triaged with the build's accepted errors left
  out; the agent must fix each or tell the user why it stays (asked for by the user: a warning that invalid XML
  characters were removed went unread).
* **Refuses**: no `pipeline_uuid`; no `stream_ids`; Stroom refusing to list the filters.
* **Notes**: stops before the session's time limit for a call (less 10 s for its last reads), the hint saying to call
  again, with nothing for the user to do. When nothing was processed by the timeout it hands over to
  `explain_processing` (asked for by the user); when every filter's tasks ended with none for the streams, it says
  waiting again won't help. It says where processing is now and then (`CallProgress`; seen: three minutes of silence
  until the timeout) and stops at once when its thread is interrupted or the chat's run stopped. Once the gate passes
  it takes the development batch size off the build's Elasticsearch indexing pipeline; that write is why it might be
  called a write, but it is kept read-only: it only undoes what `create_processor_filter` and `reprocess_streams` set,
  on a pipeline tagged as the tools' own in a build, and as a write every wait would need approving over `/mcp` and
  none could run while the user is away. Failing to restore it never fails the wait.

### `processing_status`

Processing · read-only · requires Manage Processors; checks View on the pipeline; only tasks of feeds the user may view
are counted · chat group processing · Classes: `ProcessingStatusTool`, `ProcessorFilterAccess`, `ProcessorTaskFinder`,
`PipelineFeeds`

A pipeline's processor filters and their progress, without judging them: each filter's `id`, `enabled`, `priority`,
`selects` (its terms), `feeds` (through dictionaries and stream ids too: `feeds_from_dictionaries`, `output_of`),
`tracker` (`status`, `streams_matched`, `events`, `message`), `tasks` counted by status, and `finished` (tasks exist and
none is outstanding). Each filter's own tasks are read by its id with the paged reader `processor_tasks` uses (up to
1,000 a filter), because on a busy instance a page of every task in the system may not include them. It is the build
plan's quick progress check, and `wait_for_processing` reads it each poll; `explain_processing` diagnoses why.

* **Arguments**: `pipeline_uuid` (required).
* **Returns**: `pipeline_uuid`, `filters`, `feeds`, `tasks_unreadable` (the filters still given without tasks), and a
  hint: none when there are no filters; when no filter has finished or has a task processing, to call
  `explain_processing pipeline_uuid=`.
* **Refuses**: no `pipeline_uuid`; Stroom refusing to list the pipeline's filters.

### `summarise_streams`

Processing · read-only · requires View Data; checks View; only streams of feeds the user may view are read · takes a
run slot · chat group build · Classes: `SummariseStreamsTool`, `StreamSummaries`, `ErrorTriage`, `AcceptedErrors`

Summarises streams in one of two ways:

* **`errors`**: for an Error stream, its markers; for a raw or Events stream, those of its Error children (up to 20
  Error streams, 1,000 markers and 4 MB each). Markers are grouped by severity, element and message, and each group is
  triaged `blocking` (must fix), `review` (may be caused by the pipeline's own content) or `benign` by `ErrorTriage`:
  the environment's rules (`stroom.ai.errorTriage.rules`) first, then the bundled `error_rules.yaml`; the first match
  decides. Errors the user accepted as benign for the pipeline (`AcceptedErrors`, kept in its Documentation doc) are
  benign with the reason. The pipeline's own elements are told from inherited ones. An Error stream that can't be
  read is listed under `unread`, and a verdict that would be `clean` becomes `unread` (seen: an Error stream still
  being written was summarised as clean).
* **`events`**: up to `max_events` events of Events streams: counts per EventDetail type, TypeId and Action, how often
  each event-logging path (and named Data entry) is populated, and the Data names.

* **Arguments**: `stream_ids` (required: a list, one number, or numbers between commas), `kind` (required),
  `max_events` (default 200, at most 2,000).
* **Returns**: errors: `streams` by id, each `stream_id`, `error_streams`, `verdict`, `groups_by_class`, `groups`,
  `unread`, or `error` for a stream that couldn't be summarised. Events: `events`, `event_types`, `type_ids`,
  `actions`, `path_population`, `data_names`, `hint`.
* **Refuses**: no stream ids or one that isn't a whole number; another `kind`; no View Data; an events summary
  failing.
* **Notes**: `StreamSummaries.summariseEvents` also works out shares by action and each path's values (up to 500 a
  path, `values_by_path`) for the tools' own use; the reply leaves them out. The shares by action
  (`path_population_by_action`, `type_ids_by_action`) let an index's Field mapping document a field that only some
  kinds of event have under each kind that has it (`IndexFieldDocs`): seen in testing, `source.ip` (from
  EventSource) was in 40% of the events and could be put under no event type. The indexing tools read `values_by_path`
  for stand-ins for none (`-`, `N/A`) to ask the user about (see `Placeholders` under "Sample analysis"). The streams
  read are audited.

### `locate_event`

Processing · read-only · requires View Data and Stepping; checks View; streams are found only where the user may view
their feeds · takes a run slot · chat group processing · Classes: `LocateEventTool`, `EventLocations`, `SteppingWalks`,
`StreamReader`, `EventXml`

Traces a reported stream, and optionally one event in it, back to where it came from (`EventLocations.resolve`): for an
output stream (Events, Error ...) its parent raw stream and the pipeline that wrote it, and that pipeline's Events
stream from the raw one; for a raw stream, the pipeline of its Events child. It gives the raw stream's feed and part
count, the pipeline and its code docs (`translation_docs`, marking any inherited from a template). With `event_id`,
it steps the raw stream with the pipeline from its head, counting the events each record gives (a record can give none
or several), until it reaches the event; then it gives the record's location, its input, the stored event, the event
stepping gives now, whether they hold the same values, and the record's errors now.

* **Arguments**: `stream_id` (required: Events, Error or Raw Events), `event_id` (1-based, as dashboards show EventId),
  `pipeline_uuid` (only when a raw stream was processed by more than one pipeline).
* **Returns**: `reported`, `raw_stream`, `feed`, `raw_parts`, `pipeline`, `events_stream`, `translation_docs`; with an
  event: `event_id`, `location` (`part`, `record`, `event_within_record`, `events_from_record`), `record_input`,
  `stored_event`, `stepped_event_now` (each up to 5,000 characters), `same_as_stored`, `errors_now`, a hint for
  `step_pipeline` and `summarise_fix`, and a `note` when stepping now differs (the translation or reference data
  changed since).
* **Refuses**: no `stream_id`; `event_id` below 1; no View Data; an output with no parent; a raw stream processed by
  several pipelines without `pipeline_uuid`, or with no Events output; no Events stream to find the event in; an
  `event_id` past the stream's events; the event not found in the first 500 records (the pipeline may have changed).
* **Notes**: each step may take up to 2 minutes. Streams read are audited.

## Stepping and proving fixes

These five tools step records through a pipeline with Stroom's stepping service, in-process and as the user, and
judge what the steps wrote. They are in the chat's processing group. What they share lives in `PipelineStepping`
(checks before stepping, triage, the Unknown check, the build record, spreading records over streams, time limits),
`SteppedPipeline` (the pipeline merged with its templates, as stepping needs it) and `SteppingVerdicts` (what a
step's output says: empty output, Events with no Event, referenceData with no reference, EventDetail/Unknown).
`SteppingWalks` (in `stroom-ai-impl`) makes the steps.

Shared behaviour:

* **One stepping session per walk.** Stroom's stepping engine captures the whole stream in a session and keeps it
  until the session is ended or has been idle for a while; a step with no session starts another capture. 500
  records stepped with a fresh session each ran 500 captures of the same stream at once and held Stroom's CPU at
  100%. So every step of one stream's walk (`SteppingWalks.Walk`) is in one session, and the session is ended when
  the walk closes, however it ends. A session lives on the node the call runs on, so there is no affinity to manage.
  Each step request waits 2 s (`SteppingWalks.REQUEST_WAIT_MS`) and is asked again until the step finishes or
  `PipelineStepping.STEP_TIME_LIMIT` (2 minutes) passes.
* **Permissions.** Each refuses up front a user without the Stepping permission: `You don't have permission to step
  pipelines (Stepping), so nothing can be stepped.` Pipelines (and every template they inherit from) must be
  viewable; Stroom checks the streams' feeds. The streams stepped are audited as viewed
  (`AiAuditLog.streamsViewed`).
* **Draft code** (`draft_code`, or `draft` for `summarise_fix`) is unsaved code by element id, e.g.
  `{"translationFilter": "<xsl:stylesheet ...>"}`; JSON text of that object is read too. Nothing is saved. Before
  stepping, `PipelineStepping.checkDrafts` runs `check_xslt`'s checks (`CheckXsltTool.check`) on every draft that is
  XSLT and refuses one that fails (`draft_code['x'] is not stepped: ...`), as stepping would otherwise show only the
  symptom: an unknown `stroom:` function as a compile error, a missing namespace as empty output. A refusal the
  element's saved XSLT has too (a `stroom:http-call()` a person put there) is let through (`XsltSafety.keptBy`). A
  DNS lookup the saved XSLT doesn't call is put to the user first (`LookupAgreements`, see the safety rules below),
  because stepping runs the XSLT on Stroom and so makes the lookup. `compare_outputs` makes only the safety part of
  this check (`PipelineStepping.refuseUnsafeDrafts`).
* **Mistyped properties.** A pipeline with a property Stroom can't take as written is refused before any step,
  naming it (`SteppedPipeline.refuseMistyped`): a boolean or integer property is checked against the type Stroom's
  element registry declares (`ElementRegistryFactory`). `jsonParser.addRootObject` written as the string `'false'`
  made every step of every stream outlast Stroom's wait, which on several nodes looked like a load-balancing
  failure. The refusal says to set it again with `update_pipeline`, which writes the declared type.
* **Output element.** The element whose output is judged is the pipeline's own last XSLT step; a pipeline with no
  XSLT (writing its parser's records as Records) is judged at its parser. One with neither is refused (`... has no
  XSLT step and no parser, so there is no output of it to look at`).
* **Triage.** Errors and warnings from every element are triaged by `ErrorTriage` (the environment's error rules
  and Stroom's own) into blocking, review and benign groups, with up to three examples each, the errors the user
  accepted for the pipeline (`AcceptedErrors`) counted as benign. Output with no XML elements is a marker of its own
  (`SteppingVerdicts.emptyOutput`): when no template matches (usually a wrong `xpath-default-namespace`), XSLT's
  built-in rules copy text through, nothing errors, and processing silently writes nothing. CEF lines and Kafka
  records are not empty output (`SteppingVerdicts.sendsOn`).
* **Unknown nobody agreed to.** On a build's own new pipeline (tagged managed, not a copy) whose XSLT was saved
  without a mapping, records written as `EventDetail/Unknown` are a blocking group
  (`PipelineStepping.unagreedUnknown`), with what the Unknown events hold (their TypeIds and Data values). A
  hand-written XSLT once wrote every TRAFFIC record of a firewall sample as an empty Unknown, past both
  `build_translation_xslt`'s check and the user. Not applied to draft code. A failure of the check is logged and
  never fails the step.
* **Recording a clean step.** A clean `step_sample` or `step_records` records the code stepped (the drafts' digest,
  or the saved code's) in the build record of the pipeline (`BuildRecords.recordPassed(..., STEPPED, ...)`), with up
  to 20 of the streams stepped for a saved-code run (`write_documentation`'s default sample). Only pipelines in a
  build are recorded. A record that couldn't be saved is said in the result's `record`. These tools stay read-only
  all the same: the record is the tools' own bookkeeping, never touches data, document content or processing, and
  promotion, which reads it, asks for its own approval. As writes, every step would need approving over `/mcp` and
  none could run while the user is away.
* **Time limits.** Stepping runs within the session's time limit for a long call (the workspace's query time
  limit) less 10 s (`PipelineStepping.MARGIN`) kept for triage, the record and the plan (`withinTimeLimit`, on
  `ToolDeadline`). `SteppingWalks` checks the deadline while waiting on a step, so an unfinished step stops too. A
  multi-record tool that runs out stops with `stopped_early: true` and the hint `Stopped after N records before this
  session's time limit for a call (X s) ran out. Call again: each call has its own time, so there is nothing for the
  user to do.`; its verdict is of those records only and is never recorded, as it isn't the full step promotion
  relies on.
* **Cancelling.** When the call's thread is interrupted (the client gave up, or the chat's run was stopped), each
  stops at once and says `Stepping was stopped: the call was cancelled.`; the walk's session is still ended.
* **Progress.** Each says what it is stepping as it starts, and now and then while stepping (`CallProgress`), never
  at every record.
* **Caps.** At most 500 records a call (`PipelineStepping.MAX_SAMPLE_RECORDS`, `EventLocations.MAX_RECORDS`) and
  20,000 characters of element output in a result (`MAX_STREAM_CHARS`). Every tool takes a run slot over `/mcp`.

### `step_pipeline`

Processing · read-only · requires Stepping; checks View on the pipeline and its templates · takes a run slot ·
Classes: `StepPipelineTool`, `PipelineStepping`, `SteppedPipeline`, `SteppingVerdicts`, `SteppingWalks`

Steps one record through a pipeline in a session of its own, ended once it answers, and shows the chosen elements'
input and output and every element's errors and warnings, triaged. The record is `first`, `last`, or an index within
a part. With `draft_code`, it tries a change without saving it.

* **Arguments**: `pipeline_uuid`, `stream_id` (required); `record` (zero-based index within the part, or
  `'first'` / `'last'`; default first; a number is read too); `part` (zero-based, default 0, as `locate_event`
  gives it); `draft_code`; `show` (element ids whose input and output to return; default the pipeline's own XSLT
  steps).
* **Returns**: `pipeline`, `stream_id`, `part`, `record` (where the step landed), `draft_code_used` (element ids,
  sorted), `elements` (each shown element's `type`, `input`, `output`, cut so all of them share 20,000 characters),
  and the triage: `verdict`, `groups_by_class`, `groups`.
* **Refuses**: no `pipeline_uuid` or `stream_id`; a `record` or `part` that isn't a whole number from 0; no record
  at that place (`No record found in stream N for 'first'`); a step that outlasts the call's time limit (`The step
  ran past this session's time limit for a call (X s): step a smaller stream, or call again.`); plus the shared
  refusals above.
* **Notes**: unlike `step_sample`, it doesn't check reference data is loaded or that the stream is the feed's own,
  and records nothing.

### `step_sample`

Processing · read-only (records a clean verdict in the build record) · requires Stepping; unlocked further by View
Data; checks View on the pipeline · takes a run slot · Classes: `StepSampleTool`, `PipelineStepping`,
`SteppingVerdicts`, `BuildRecords`

The correctness check before processing. Steps every record of the sample streams, stream by stream, each stream
walked in one session, and returns one verdict for the whole sample: error groups triaged with the records they
affect, and per-record counts. A blocking group means fix and step again. A clean run of saved code is recorded for
promotion and returns the plan's `next`.

* **Arguments**: `pipeline_uuid`, `stream_ids` (required, at least one); `draft_code`; `max_records` (at least 1;
  at most 500, the default); `records_per_stream` (at least 1). With neither `max_records` nor
  `records_per_stream`, the first 50 records of each stream are stepped (`StepSampleTool.PER_STREAM`): sample files
  of 985 records each were stepped a record per request on a remote Stroom, and the head of each stream already
  says whether the translation is right; processing reads the rest.
* **Returns**: `pipeline`, `records_stepped`, `records_with_errors`, `draft_code_used`, the triage (`verdict`,
  `groups_by_class`, `groups`, each group with up to 20 `records`, keys such as `7:3` or `7:1:3` past the first
  part), `first_record_output` (each default output element's output for the first record, up to 5,000
  characters), and as they apply `hint`, `stopped_early`, `record` and `next`.
* **Refuses**: no `pipeline_uuid` or empty `stream_ids`; `max_records` or `records_per_stream` below 1 or not a
  whole number; a stream older than its feed (it belongs to an earlier, deleted feed of the same name:
  `SampleStreamTexts.refuseOlderThanFeed`); a pipeline that looks up reference data from a feed with no stream of
  that type yet (`PipelineStepping.refuseUnloadedReferences`): a step then finds no reference data, and Stroom keeps
  that empty answer for 10 minutes, so steps made after the reference data is processed still fail.
* **Notes**:
  * Blocking groups beyond the triage: Unknown nobody agreed to (above); every record wrote Events but none an Event
    (`SteppingVerdicts.noEvents`: XML fragments read with no namespace came out as empty Events and stepping said
    clean); every record wrote referenceData but no reference (`noReference`: a header line read as data named the
    columns `col1`..., and processing then wrote no Reference stream with nothing saying why). No record stepped at
    all is never clean (`nothingStepped`), and an element that fails before the first record (an XSLT that doesn't
    compile) is reported.
  * A JSON parser with its root map (`addRootObject` not false), every stream stepped as one record, and the first
    stream's head a JSON array: a review group says each array is one record (a large one outlasts stepping) and to
    set `addRootObject` false with `update_pipeline` and match `/array/map`. The head is read only for a user with
    View Data; without it the check is skipped.
  * At the cap, the hint (`Stopped at 500 records, the server's limit for one step_sample ...`) says calling again
    steps the same records and that processing checks the rest:
    `max_records=1000` once stepped 500 and said only that the sample had more, and the agent repeated it for three
    more minutes. Streams left once the cap is reached are not stepped at all.
  * Clean with draft code: the hint says to save it (`build_translation_xslt uuid=` for a generated XSLT, else
    `save_xslt uuid=`) and step the saved code once more; only then does the plan move on.

### `step_records`

Processing · read-only (records a clean verdict in the build record) · requires Stepping; checks View on the
pipeline · takes a run slot · Classes: `StepRecordsTool`, `PipelineStepping`, `SteppingVerdicts`

Steps chosen records of existing streams in place (typically `survey_feed`'s locations) and returns one verdict
like `step_sample`'s, with per-record events and errors and which survey shapes didn't step clean. It checks a
translation against every kind of event a feed holds without copying or processing anything. Consecutive locations
in one stream share a session.

* **Arguments**: `pipeline_uuid`, `locations` (required): each `{stream, part, record, shape, expect}`, `stream`
  and `record` required, `part` default 0; `shape` names the survey shape the record is an example of; `expect` is
  `event` (default) or `none` for a kind the user chose to leave untranslated. A single location, or the list as
  JSON text, is read too. `draft_code`. Only the first 500 locations are stepped.
* **Returns**: `pipeline`, `records_stepped`, `records_with_errors`, `records_not_found`, `shapes_not_clean`,
  `left_untranslated_as_intended`, `draft_code_used`, the triage, `records` (the first 100, each `record`, `shape`,
  `events`, `errors`, and `found: false` with `why` for one not found), and as they apply `hint`, `stopped_early`
  and `record`.
* **Refuses**: no `pipeline_uuid`; empty `locations`; a location that isn't one, or without whole-number `stream`
  and `record` (`locations[0] needs stream and record (and part, if any) as whole numbers: ...`); an `expect` other
  than `event` or `none`; a stream older than its feed.
* **Notes**:
  * A record that writes no Event (and sends nothing on) is a warning. With `expect: none` the empty-output marker
    doesn't apply, and an Event is the warning instead.
  * A record not found (past a stream's end) is listed in `records_not_found` and not judged, unless no record was
    found at all (then stepping's own complaints are triaged): a record past the end once turned a clean verdict
    into review over stepping's complaint that it had nothing to replay.
  * A shape is not clean if any of its records has errors or (expected to) writes no Event. The build record is
    written only when every shape is clean. The plan's `next` is not added.
  * It doesn't check that reference data is loaded, as `step_sample` does.

### `compare_outputs`

Processing · read-only · requires Stepping; checks View on both pipelines · takes a run slot · Classes:
`CompareOutputsTool`, `PipelineStepping`, `EventLocations`, `EventXml`

Steps the same records through the current pipeline and a candidate (draft code, or another pipeline such as a new
version) and diffs each record's output field by field (`EventXml.fieldValues`: values keyed by a readable path
such as `Event/EventSource/User/Id`; an element named by an attribute, like `Data/@Name`, keeps its name in the
path, so reordering doesn't show as a change). It proves a change touches only the fields it should.

Records come from `stream_ids`, spread over the streams (`PipelineStepping.stepSpread`): an even share from each
stream, then what a short stream left over taken from those with more, each stream's share stepped in one session.
On Stroom before stepping kept the capture, a step cost more the further into the stream its record was (0.15 s near
the start, 1.1 s at the 151st record), and 200 records from one stream took 132 s; four streams of fifty are faster
and a better sample. With `events`, exactly those indexed events' raw records are found (`EventLocations`, which
steps each raw stream with the pipeline that produced the event and counts events per record) and each is stepped
on both sides in a session of its own.

* **Arguments**: `pipeline_uuid` (required); `draft_code` or `other_pipeline_uuid` (one is required);
  `stream_ids` and/or `events` (each `{stream_id, event_id}` as `search_events` with an index gives them, `event_id`
  from 1; at least one of the two); `element` (default the pipeline's first XSLT step; on the other pipeline, the
  same id if it has one, else its first XSLT step); `max_records` (1 to 500, default 50) for the stream records.
* **Returns**: `records_compared`, `records_changed`, `element`, `fields_changed` (per `path`: counts of `added`,
  `removed`, `changed`, and one `example` with `record`, `before`, `after`; most changed first), `unchanged`; and
  `stopped_early` with a hint when time ran out.
* **Refuses**: neither `draft_code` nor `other_pipeline_uuid`; neither `stream_ids` nor `events`; `max_records`
  out of range; an event that isn't `{stream_id, event_id}` of whole numbers; an event that can't be traced (its
  stream isn't Events, has no parent, was processed by several pipelines, or the event isn't in it: the
  `EventLocations.LocateException` message); a draft that calls what the tools won't run (only the safety part of
  `check_xslt`).
* **Notes**: when time runs out, only records stepped through both sides are compared. It neither checks
  mistyped properties nor records anything.

### `evaluate_pipeline`

Processing · read-only (its step may record a clean verdict) · unlocked by View Data, Stepping and Manage Processors
(none required) · checks View on the pipeline, its XSLTs and the index docs it writes to · takes a run slot ·
Classes: `EvaluatePipelineTool`, `IndexEvaluation`, `CefEvaluation`, `ReferenceEvaluation`, `StepSampleTool`,
`CheckEventsTool`, `StreamSummaries`, `CoverageReads`, `PipelineShapes`

The facts for an evaluation of an existing pipeline, covering the same ground every time, so evaluations are
consistent and the feature is found. It reads the pipeline's stage as `PipelineShapes` reads it (the stage
write_documentation uses), gives the general sections every pipeline gets, then the checks for its stage, then the
report's outline. The agent writes the report from it, shows it to the user, and saves it with
`write_documentation pipeline_uuid=<uuid> evaluation=true` only if the user wants it kept. It is made of the tools
that cover each part, reused, not reimplemented: `StreamSummaries.summariseErrorStreams` (summarise_streams'
triage, with the errors the user accepted as benign reported as benign), `StepSampleTool.sampled` (step_sample's
verdict, keeping each record's output when the stage's checks read it), `CheckEventsTool.checkRead` and
`StreamSummaries.summariseEvents` (check_events and summarise_streams kind=events, recording nothing),
`CoverageReads.parseLogged` (review_coverage's reading of missed records), `IndexDocs` and `TemplateCheck.fits`
(the index's fields and value types), `DraftCefMappingTool.stepped` and `CefLines.review` (draft_cef_mapping's
review), and `ReferenceData` (find_reference_data's maps and pipeline references).

| Stage (`PipelineShapes`) | Checks | Sections, in order |
|---|---|---|
| translation | events | `pipeline`, `processing`, `stepping`, `events`, `coverage`, `report` |
| indexing, discovery | index | `pipeline`, `processing`, `stepping`, `index`, `report` |
| forwarding | cef | `pipeline`, `processing`, `stepping`, `cef`, `report` |
| reference | reference | `pipeline`, `processing`, `stepping`, `reference`, `report` |
| records, loader, other | general | `pipeline`, `processing`, `stepping`, `checks`, `report` |

* **Arguments**: `pipeline_uuid` (required); `stream_ids` (input streams to step: raw streams for an events
  pipeline, Events streams for an indexing or CEF one, Raw Reference for a reference one); `max_records` (at least
  1, at most 500; by default the first 50 of each stream, as step_sample); `schema_version` (an events pipeline's
  event-logging version, as check_events).
* **Returns**: the sections for its stage, always all of them and in order; a section that can't be given has
  `status` and `reason`.
  * `pipeline`: `name`, `uuid`, `stage`, `backend` (indexing: lucene or elasticsearch; forwarding: kafka, or file
    for CEF lines written as text),
    `description`, `templates` (its template chain, root first), `parser`, `translation_docs` (each XSLT and text
    converter, its element, and whether its template sets it), `element_chain`.
  * `processing`: from the newest Error streams it wrote (up to 10; up to 500 Error and Events streams counted,
    `500+` beyond): `status` (`processed`; an events pipeline that wrote nothing, `never_processed`; any other with
    no Error streams, `no_error_streams`, as it writes no Events stream to tell nothing processed from processed
    clean), `events_streams`, `error_streams`, `error_streams_read`, `verdict`, `groups_by_class`,
    `markers_by_severity`, `groups` (up to 8: `class`, `reason`, `severity`, `element`, `count`, `records_affected`,
    `accepted`, `example`), `groups_not_shown`, any `unread` and `hint`.
  * `stepping`: step_sample's `verdict`, `streams`, `streams_from` (`stream_ids`, or `the pipeline's newest
    inputs`), `records_stepped`, `records_with_errors`, `groups_by_class`, `groups` (up to 8, each with up to 5
    `records`), any `stopped_early` and `hint`. By default it steps the inputs of the pipeline's newest output
    (Events, else Error streams' parents), else the newest streams of the type its stage reads on the feeds its
    processor filters read, up to 3; with none, `not_stepped` asking for `stream_ids`.
  * `events` (translation): the newest Events stream checked as check_events does (`ok`, `schema` with up to 5
    errors, `quality` with one example a failed rule, `read`) and profiled as summarise_streams kind=events does
    (`events_profiled`, `event_types`, `type_ids` (up to 10), `field_population`: each path's share of the events,
    least filled first, up to 40, with `field_population_note` for the rest). With no Events stream, the first
    record stepped is checked instead, and the population is not profiled.
  * `coverage` (translation): `status` (`records_missed`, `none_logged`, `not_checked`),
    `records_no_rule_matched`, `records_kept_as_unknown` and `rules_keeping_unknown`, read from the processing
    groups' log lines, and `next` pointing to review_coverage, which it does not run.
  * `index` (indexing, discovery): `status` (`checked`, or `partial` when the fields written or the index's fields
    couldn't be read, with `notes`), `index`, `backend`, the Elasticsearch `cluster` and `index_name`,
    `index_fields`, `documents_stepped`, `fields_written`, `written_not_in_index`, `index_fields_not_written` (up to
    40), `type_mismatches` (up to 20: a value its field's type can't take, as `TemplateCheck.fits` judges an index
    template; or free text of several words in a keyword field), `hint`, and for Elasticsearch `index_template`
    (`agreed`, and `next`: check_index_template when one is agreed, which it does not run, else why there is
    none). It never runs verify_index.
  * `cef` (forwarding): its lines stepped with the Events they came from (up to 100 records) and reviewed by
    `CefLines.review`: `status`, `output` (kafka and its topic, or text), `lines_stepped`, `custom_keys` (from the
    plan kept with its XSLT, else not known), `header_todo` (header fields still holding a TODO constant),
    `problems`, `not_sent` and `implied_mapping` (each cut to 15, up to 10 kinds of event), `notes` (for Kafka,
    delivery is not tested by stepping; for a file, what its appender does with the lines is not).
  * `reference` (reference): `status`, `maps` (each map its XSLT writes, as find_reference_data reads it, with
    `seen`: the entries stepping wrote, an example key and value), `maps_seen_only`, `feeds` (its processor
    filters', needing Manage Processors), `looked_up_by` (pipelines whose reference names one of those feeds or
    this pipeline as loader, up to 20), `notes`.
  * `checks` (any other stage): `status: not_applicable` and why.
  * `report`: `outline` (`# <name> - Evaluation`, then `## Summary`, `## Pipeline`, `## Processing errors`,
    `## Stepping`, the stage's own heading or headings (`## Event validity` and `## Coverage`; `## Index fields`;
    `## CEF output`; `## Reference maps`), `## Recommendations`), `sections` (what fills each heading) and `next`
    (show the report; save it with write_documentation pipeline_uuid=<uuid> evaluation=true only if the user wants
    it kept).
* **Refuses**: no `pipeline_uuid`; `stream_ids` that aren't stream ids; `max_records` below 1; a pipeline that
  doesn't exist, that the user can't view, or that Stroom can't load. A missing permission never refuses the call:
  the sections it affects say so.
* **Notes**: it says where it is (reading Error streams, stepping, the stage's checks); the streams it reads are
  audited as summarise_streams and check_events audit theirs, and the stepped ones as step_sample does. A cancelled
  call is refused with stepping's cancellation message.

### `summarise_fix`

Processing · read-only (its steps may record a clean verdict) · requires Stepping; unlocked further by View Data;
checks View on the pipeline and its XSLTs and text converters · takes a run slot · Classes: `SummariseFixTool`,
`CompareOutputsTool`, `StepSampleTool`, `StepRecordsTool`, `EventLocations`, `LineDiff`

Proves a fix to an XSLT or text converter on real records and packages it for the user. It is made of the other
stepping tools: it finds the indexed events' records once (`EventLocations.eventLocations`), compares outputs with
the fix against the saved code (`CompareOutputsTool.compareLocated`, up to 50 stream records plus the located
ones), then steps with the fix and again with the saved code (`StepSampleTool.stepSample` over the streams,
`StepRecordsTool.stepRecords` over the located records). Stepping the saved code too means a blocking error the
pipeline already has doesn't hold back a fix for something else; only errors the fix introduces do. It saves
nothing: the user chooses whether the fix is applied (a copy in a build, changed, then promoted) or done by hand.

The fix is either `draft` for `element`, or a change already saved on a copy (`fixed_pipeline_uuid`, from
`copy_pipeline`), whose code is the draft, proven against the pipeline's own saved code; `element` is then found as
the one element whose code differs.

* **Arguments**: `pipeline_uuid` (required); `element` and `draft` (the complete fixed code), or
  `fixed_pipeline_uuid`; `stream_ids` (raw streams: the reported one plus recent production streams) and/or
  `events`; `expected_paths` (required: output field paths the fix should change; a path also covers those below
  it); `issue` (the problem in the user's words, for the write-up).
* **Returns**: `issue`, `pipeline` (`uuid`, `name`), `element`, `doc`, `inherited_from_template`, `set_by`,
  `ready`, `problems`, `records_compared`, `records_changed`, `fields_changed` (up to 20), `step_verdict` (the worse
  of the two steps' verdicts with the fix), `step_groups` (up to 10, benign left out), `errors_resolved`,
  `errors_before_too` with `errors_before_too_note`, `diff` (a unified diff headed `--- <name> (saved)` / `+++
  <name> (fixed)`, three lines of context, up to 20,000 characters), `draft`, `manual_steps` (open the document,
  apply the diff, step the streams, reprocess if wanted; with a warning first when the code is set by a template,
  so the change reaches every pipeline built from it), and `hint`.
* **Refuses**: no `pipeline_uuid`; neither `element` and `draft` nor `fixed_pipeline_uuid`; neither `stream_ids`
  nor `events`; no `expected_paths`; an element with no XSLT or text converter (naming those that have one); a
  draft the same as the saved code; a copy with no code on `element`, the same code, or more than one element
  differing (`Give element: ...`); an XSLT or text converter the user can't view; an event that can't be traced.
* **Notes**:
  * `ready` is true only when there are no `problems`: the draft changes some output; no field outside
    `expected_paths` changes; stepping with the draft has no blocking group the saved code lacks. Groups are matched
    on element, severity and the normalised first example message.
  * The whole proof runs within the session's time limit for a long call. If any part stops early, nothing more is
    stepped and a short result is given: `issue`, `pipeline`, `element`, `doc`, `ready: false`, `stopped_early:
    true` and the hint `... The fix is not proven: give fewer stream_ids or events to prove it on.`
  * It says when it starts each part (finding records, comparing, stepping with the fix, stepping with the saved
    code); the tools it calls report their own progress.
  * Line endings: `LineDiff` splits on any line ending, so a draft differing only in line endings shows no changed
    lines.

## Checking XSLT and events

Both are in the chat's build group, on the validation core below.

### `check_xslt`

Content · read-only · checks View on the XML schemas and imported XSLTs it looks up · Classes: `CheckXsltTool`,
`XsltChecks`, `XsltSafety`, `HeldSchemas`, `XsltCompileCheck`

Checks an XSLT before it is saved or stepped. Every XSLT the tools save goes through it, and so does draft code
before it is stepped (`PipelineStepping.checkDrafts`). In order:

1. XML sent HTML-escaped (`&lt;xsl:...`) is unescaped first, with a warning (`XsltChecks.unescaped`).
2. Well-formed (else `ok: false` with the line, and nothing more is checked); root `xsl:stylesheet` or
   `xsl:transform`; a `version` attribute.
3. `stroom:` functions: the namespace declared and only real functions called; an unknown one comes back with the
   nearest real name (`XsltChecks.functionProblems`, `CloseMatches`).
4. Safety (`XsltSafety.unsafeInXslt`): refused functions, instructions and imports by URL, and Stroom's
   `http-call()`, `fetch-json()` and `ask-ai()`, are errors. DNS lookups (`stroom:host-name()`,
   `stroom:host-address()`) are only warnings, each saying where it is called and that saving or stepping asks the
   user first (`CheckXsltTool.lookupWarning`). See the safety rules below.
5. Match and select expressions that would select nothing for want of the input's namespace
   (`XsltChecks.namespaceProblems`); `input_namespace: ''` says the input is a source's own XML in no namespace, so
   its own `records`/`record` elements aren't taken for a Data Splitter's `records:2`.
6. An `xsi:schemaLocation` naming a system id this Stroom doesn't hold in a namespace it holds schemas for (e.g.
   `file:///` with three slashes: Stroom would validate against another schema of the namespace)
   (`HeldSchemas.locationProblems`). If the schemas can't be read, a warning.
7. Warnings for `json-to-xml()` used unguarded (`XsltChecks.unguardedJson`).
8. Event-logging elements written where the schema has no place for them (e.g. `EventDetail/ServerEvent`), against
   the version given, else the `Version` the XSLT writes on `Events`, else the newest held
   (`XsltChecks.eventElementProblems`, `EventSchema`). If no schema can be read, a warning.
9. `xsl:import` / `xsl:include` targets: each must be an XSLT document of exactly that name that the user may view
   (`DocFinder`), as Stroom imports by name.
10. Only when all of that passes: compiled as Stroom compiles it (`XsltCompileCheck`), with Stroom's own functions and
    its imports read through Stroom's resolver, so an undeclared variable, a wrong call, or XPath Stroom's Saxon
    can't run is found before stepping. Compiling last means a fault (a missing import, say) isn't reported twice.
    Compile errors come back as `Does not compile: <code> <message>`.

* **Arguments**: `xslt` (required, the full text); `schema_version` (an event-logging version this Stroom holds);
  `input_namespace`.
* **Returns**: `ok`, `errors`, `warnings`, and for a well-formed XSLT `imports` (hrefs in document order) and
  `stroom_functions` (the `stroom:` functions it calls).
* **Refuses**: an empty `xslt`. Everything else is an error in the result, not a refusal.

### `check_events`

Content · read-only (records Events that pass as validated in the build record) · unlocked by View Data (for
`stream_ids`); checks View on the schemas and the streams' feeds · takes a run slot · Classes: `CheckEventsTool`,
`EventChecks`, `EventStreams`, `HeldSchemas`, `SchemaErrors`, `BuildRecords`

Checks event XML two ways: against the event-logging XSD held in this Stroom (`EventChecks.validateEvents`, each
error with its line, element path and message), and against quality rules beyond the schema
(`EventChecks.checkEventQuality`: `TimeCreated` a full UTC timestamp; `EventSource/System/Name`, `Environment`,
`Generator`, `Device` and `EventDetail/TypeId` present; exactly one action under `EventDetail`; no empty elements).
Given `stream_ids`, it reads the Events streams itself (`EventStreams`) rather than have the agent read them back:
an agent once read 50 events back 23 at a time, 47 s a read, to check them.

* **Arguments**: `events_xml` (an `<Events>` document, e.g. step output) or `stream_ids` (Events streams processing
  wrote, as `wait_for_processing` lists them); exactly one of the two. `schema_version` (default the version the
  document declares in `xsi:schemaLocation` or its `Version`, else the newest held).
* **Returns**: `ok`, `schema` (`valid`, `schema` system id, `error_count`, `errors`: up to 50, each `line`, `path`
  such as `/Events/Event[2]/EventTime/TimeCreated`, `message`), `quality` (`events_checked`, `ok`, `rules`: each
  failed rule with `events_failing` and up to three `examples`; `notes` when events are EventDetail/Unknown), `note`
  when the XML came HTML-escaped. With `stream_ids` also `read` (per stream, records checked and held),
  `recorded_for` (the pipelines recorded) and the plan's `next`.
* **Refuses**: both or neither of `events_xml` and `stream_ids`; XML that isn't well-formed; a schema version this
  Stroom doesn't hold; a stream that can't be read, isn't Events, or has no Events records; a stream the user may
  not read (no View Data).
* **Notes**:
  * A records:2 document (a Data Splitter's output) is validated against the records schema; any other XML that
    isn't Events is said not to be Events, and the quality rules are skipped, rather than every element failing.
  * The schema's two messages for one bad value (the value, then the element holding it) are given as one error
    (`SchemaErrors`), so counts match faults.
  * Up to 500 records of the streams in all.
  * When streams pass, each pipeline that wrote them (from the stream's meta) is recorded as validated
    (`BuildRecords.VALIDATED`), only for pipelines in a build, and the plan moves on (its checklist had stayed on
    processing). It stays read-only for the same reasons as the stepping tools' record.

## Pipelines

The pipeline write tools work on the build layer (`WriteGuard`, `BuildRecords`, `Consent`, `BuildPlan`) and
`PipelineWrites`. They are in the chat's build group. Pipelines are read through Stroom's `PipelineService` layers
(`PipelineShapes`, as the pipeline editor reads them) and created, filled and saved as the user through the
explorer and the pipeline store. Templates are inherited (`parentPipeline`), never copied.

Shared behaviour:

* **Properties** (`set_properties`) are objects `{element, name, doc_uuid, doc_type}` for a document or
  `{element, name, value}` for a plain value (`PropertyValue`). Agents guess other shapes from how descriptions
  name a property, so `translationFilter.xslt=<uuid>`, `{"translationFilter.xslt": "<uuid>"}` and
  `{"element": "translationFilter.xslt", ...}` are read as the property they name, a UUID given as an XSLT's or text
  converter's value is read as that document, and a single object or the list as JSON text is read too
  (`PropertyValue.listOf`). A document property's name is read from the explorer as the user may view it.
* **Typed values** (`PipelineWrites.typedProperties`, on `PipelinePropertyTypes` from Stroom's element registry):
  each plain value is written as the type Stroom declares for the property, so `"false"` for a boolean is false. A
  property the element doesn't have, a value not of its type, or a whole number too large for an `int` property is
  refused before anything is written. `jsonParser.addRootObject` written as a string made every step of that
  pipeline outlast Stroom's wait.
* **Validation is the template's** (`PipelineWrites.keepValidation`): a property on a schema filter is refused
  (`... validate the pipeline's output against its schema; their settings are not changed here. Fix the output
  instead ...`); output that fails validation is what to fix. Checked by `create_pipeline` and `update_pipeline`.
* **References** (`references`) are `{feed, loader_pipeline, element}` (`PipelineWrites.ReferenceRequest`): `feed`
  required, the loader by name or UUID (default the one other pipelines load that feed with, else the environment's
  only loader: `ReferenceData.resolveLoader`), the element default the pipeline's translation XSLT step. A feed's
  name alone is read as `{feed: <name>}`. A reference without a feed is refused (`references[0].feed is required:
  ...`); a pipeline with no XSLT step to attach it to too. A feed is found by `FeedNames.exact`; a feed name that is
  no feed's here gets `FeedNames.notFound`'s advice that the user may mean another Stroom cluster.
* **JSON arrays**: when the build's sample is a JSON array, or the translation XSLT's mapping reads one,
  `jsonParser.addRootObject` is set false unless already set (`PipelineWrites.jsonArrayParser`), so each item is a
  record. Four 755 KB arrays were once stepped as four records of 985 events each, every step outlasting Stroom's
  wait. Never fails the write.
* **Pipelines found by name** (`template`, `loader_pipeline`) are ones the user may view of exactly that name,
  through Stroom's document finder; a name matching several is refused.
* **Results** carry the plan's `next` where the pipeline is in a build (`BuildPlan.withNext`), except
  `update_pipeline`'s. Nulls are left out of every result's JSON.

### `create_pipeline`

Content · write · checks View (or Use) on the template and the documents it names, Create in the build folder, Edit
on the new pipeline · confirms itself · Classes: `CreatePipelineTool`, `PipelineWrites`, `PipelineShapes`,
`PipelineTemplates`, `WriteGuard`, `Consent`, `BuildPlan`, `BuildRecords`

Creates a pipeline in the build as a child of a template, setting only what the child supplies (the translation
XSLT, the parser's text converter, open properties). It keeps the template's structure and defaults, including
optional steps such as an empty decoration XSLT. The user confirms the template and name in a form first; the name
is editable there. `create_indexing_pipeline` uses the same path (`CreatePipelineTool.fromTemplate`).
A template whose stage gives no role subfolder (stage `other`; seen: a CEF pipeline written by hand that writes lines
of text, XMLParser, XSLTFilter, TextWriter, before such a pipeline was told by its XSLT as `forwarding`) is used all
the same: the child is made in the build folder itself (seen in `TestE2eCef`: create_pipeline failed with a
NullPointerException on such a template, which an AGENTS doc named).

Making it, in order (`fromTemplate`):

1. The template's layers merged as a child is written over them (`PipelineWrites.layout`). With `replace_parser`,
   the template's parser is swapped (`PipelineWrites.swapParser`): the new parser takes the old one's place and
   links, with the id of its type (`xmlFragmentParser`), fed from Source (added if the template stores none), as
   Stroom's editor draws it. A parser type that isn't one, or a template with no parser, is refused.
2. Every element in `set_properties` must exist (`The pipeline has no element(s) [...]; its elements are [...]`);
   validation properties refused; values typed.
3. **The parser must read the sample** (`PipelineWrites.parserReadsSample`, checked first, as a template whose parser
   can't read the sample is the wrong one whatever else it lacks: an XML source was once refused the Data Splitter
   template for want of a text converter, not for its parser). The build's sample format (`BuildPlan.sampleFormat`,
   which for a build on an existing feed reads the survey's format) must be one the parser reads
   (`BuildPlan.PARSER_FOR_FORMAT`). XML fragments under an `XMLParser` are refused saying outright to call again
   with `replace_parser='XMLFragmentParser'`, as the general advice was passed over in testing. Overridden by
   `accept_parser_mismatch=true` when the user insists.
4. **Open slots filled from the build** (`PipelineWrites.openSlots`, `fillOpenSlots`): the parser's text converter
   (DSParser, XMLFragmentParser, CombinedParser) and the first XSLT step's `xslt`, where the template leaves them
   unset. Each is filled with the build's only document of that type (XSLTs with a kept mapping preferred); several
   candidates are refused (`pass the right one in set_properties`). A converter slot with none in the build is
   refused (`build_data_splitter ... or save_text_converter`); an XSLT slot with none is left open, reported in
   `still_to_set` with a hint to save the XSLT and `update_pipeline`.
5. **The build's own documents** (`PipelineWrites.ownDocuments`): an XSLT or text converter set on the child must be
   one this build made (tagged managed, in this build). Another source's or a library's is refused: a new
   pipeline's translation is written for its own source, and shared libraries are imported or inherited. Overridden
   by `reuse_existing_docs=true` when the user says so.
6. References attached; the confirmation form; then `WriteGuard.createFilled` makes and fills the pipeline in one
   go, deleting it again if filling fails, so no empty pipeline is left in the build. The build's XSLT and text
   converter the pipeline runs are moved into its role subfolder (Events or Records) with it.

**A production pipeline's next version** (`version_of`): a child of that pipeline's template (unless `template` is
given), with the build's new XSLT (its translation written anew; to keep the XSLT and change only parts, use
`copy_pipeline`). It takes the previous version's text converter where the build has none, and its plain settings
on elements the new one has that nothing else sets (`PipelineWrites.plainSettings`, e.g. `addRootObject`). Whether
it loads the previous version's reference data is the user's choice: a version of a pipeline that loads any is
refused until `keep_references` is given (`... Ask the user whether the new version keeps them ..., then call
again with keep_references=true or false`); kept ones are carried (`PipelineWrites.carriedReferences`). What the
previous version sets that the new one doesn't is shown in the form and the result (`notCarried`). The new pipeline
is recorded as the next version of the old, and each build document it runs in place of one the old runs
(`CreatePipelineTool.recordVersion`), so promotion puts them beside their predecessors. A previous version that
inherits from no template is refused (copy it instead).

**Standalone pipelines** (`standalone`), last resorts that inherit from no template, confirmed with their chain:

* `kafka`, only when no forwarding template exists: Source -> xmlParser -> splitFilter -> xsltFilter -> schemaFilter
  -> standardKafkaProducer (`CreatePipelineTool.KAFKA_CHAIN`). `splitCount` is fixed at 1 and the schema filter on
  `kafka-records:1`, because kafka-records v1.1 takes a single `kafkaRecord` a document, so the environment's own
  split of 100 failed the schema filter whenever a chunk held more than one. `set_properties` must give
  `xsltFilter.xslt` and `standardKafkaProducer.kafkaConfig`. Stepping sends nothing.
* `events`, only when this Stroom has no translation template at all (refused otherwise, naming them): built as
  Stroom's standard Event Data templates are (`EVENTS_CHAIN`: the parser the build's sample needs, record counts,
  one Event a split, the translation XSLT, the schema filter on the EVENTS group, Events written to the stream's
  feed). Refused until a sample is uploaded or the feed surveyed; `set_properties` must give
  `translationFilter.xslt` (and the parser's `textConverter` where it has one).
* `standalone` with `template` or `version_of` is refused.

* **Arguments**: `name` (required); `template_uuid` or `template` (UUID or exact name); `build` (default the
  session's current build); `set_properties`; `description` (kept on the pipeline doc); `replace_parser`;
  `references`; `reuse_existing_docs`; `accept_parser_mismatch`; `standalone` (`kafka` | `events`); `version_of`;
  `keep_references` (only with `version_of`).
* **Returns**: `type`, `uuid`, `name`, `template`, `version_of`, `set_in_<previous>_not_here`, `sets`,
  `filled_from_build`, `still_to_set` with `hint`, `reference_data`, and `next`; a standalone pipeline adds
  `note` (what to tell the user).
* **Refuses**: no `name`; no template; the refusals in the steps above; `keep_references` without `version_of`;
  the user declining the form, or a session that can't show one (`Consent`).
* **Notes**: Stroom can't load a template: `Stroom can't load the pipeline: ...` (`PipelineUnreadableException`).

### `copy_pipeline`

Content · write · checks View on the pipeline and the XSLTs and text converters it owns, Create in the build
folder, Edit on the copies · confirms itself · Classes: `CopyPipelineTool`, `PipelineWrites`, `PipelineTemplates`,
`WriteGuard`, `BuildRecords`, `Consent`

Copies an existing source's pipeline into the build with exactly the original's structure and settings: the same
parent template, the same element changes (removed or re-linked steps, extra XSLT steps), reference loaders and
property values, and its description. The XSLTs and text converters its own layer names
(`PipelineWrites.OWNED_TYPES`) are copied too, with `rename` applied to their names, and the copy is rewired to
them; other documents (shared libraries, indexes, clusters) stay shared. The user confirms the names in a form
first. It is the route for a new version that keeps the XSLT and changes parts, and for a working copy of a
production pipeline.

* **Arguments**: `build`, `source_uuid`, `new_name` (required); `rename` (text replacements for the copied
  documents' names, e.g. `{"V1.2": "V1.3"}`; JSON text or a single-quoted literal is read too:
  `PipelineArguments.textMap`); `set_properties` (changes on the copy, e.g. a new `indexName`); `working_copy` (true
  when promotion will write the copy back over the original; false, the default, for a new version promoted
  alongside it).
* **Returns**: `type`, `uuid`, `name`, `copy_of`, `working_copy`, `copied_documents`, and `next`.
* **Refuses**: a missing required argument; a template (`PipelineTemplates.templateReason`: `... is a template
  (...): templates are inherited, not copied ...`, pointing at `create_pipeline`); an owned document the user can't
  read; the user declining the form, or no form.
* **Notes**:
  * A document that `set_properties` replaces is not copied: the copy would be unused, and its renamed name could
    clash with the replacement's (v2's XSLT made first, then V1's copied and renamed to the same name, which the
    build refused).
  * A working copy is tagged as a copy of its original (`WriteGuard.create` with the source's UUID), as are its
    documents; a new version is recorded as `version_of` its source in the build record. Each working copy also
    records the version its original had when copied (`BuildRecords.COPY_OF_VERSION`), so `promote_build` can ask
    the user before writing it back over an original changed since.
  * Unlike `create_pipeline`, the copy is created and then written in two steps (`WriteGuard.create`, then the
    pipeline store), and `set_properties` is not checked against validation elements.

### `update_pipeline`

Content · write · checks View and Edit on the pipeline · changes only its build · Classes: `UpdatePipelineTool`,
`PipelineWrites`, `WriteGuard`, `BuildIndexes`

Changes a pipeline the tools created, in its build folder: sets element properties and attaches reference data so
its XSLT's `stroom:lookup()` calls find the maps. It asks nothing itself; as it changes only what is in the build
(`WriteGuard.checkInBuild`), it isn't approved as a whole either, over `/mcp` or on the chat's approval card.

* **Arguments**: `pipeline_uuid` (required); `set_properties` and/or `references` (at least one). References are
  added to any the pipeline already has; an identical one isn't added twice.
* **Returns**: `uuid`, `name`, `type`, `set` (each `element.name` set), `reference_data` (all the pipeline's
  references now, when references were given), and `note` when `addRootObject` was also set for a JSON array.
* **Refuses**: no `pipeline_uuid`; neither argument; a pipeline that isn't the tools' own, or not in a build folder
  (`... is not in a build folder: it is changed only in a build. Make a working copy in one (copy_pipeline) ...`,
  audited); an element the pipeline doesn't have; a validation property; a mistyped value; an Elasticsearch
  indexing filter's `indexName` that differs from the build's Elastic Index doc (`BuildIndexes.otherIndex`:
  `rename_index` changes both, and the index template follows).
* **Notes**:
  * Each property is a separate save (`PipelineWrites.setPipelineProperty`), so a refusal part way leaves the earlier
    ones set.
  * A pipeline made before its XSLT, or with one written by hand, never had `addRootObject` set for a JSON array:
    the update sets it when needed (a convenience; a failure is logged and never fails the update).

## Translations: mappings, XSLTs, text converters and dictionaries

These tools write a build's translation documents. Every document is made through the write guard
(`WriteGuard.createFilled`: a document whose filling fails is deleted again, not left empty) and saved through
Stroom's own stores as the user; an update first checks the document is the tools' own (`WriteGuard.checkManaged`),
and, with `version`, that it hasn't changed since it was read ("changed since it was read (version N); read it
again and reapply the change"). Results are JSON with the build's next step added (`BuildPlan.withNext` /
`nextFor`). All are in the build chat group, category content.

What they share, all in `stroom-mcp-impl`:

* **`TranslationDocs`**: create and update of text converters, XSLTs and dictionaries; the converter checks
  (`checkConverter`); the XSLT checks before a save (`checked`: check_xslt, with the code being replaced allowed to
  keep the refused calls it already had, `XsltSafety.keptBy`); the kept mapping or plan and whether the code is
  what it generates (`described`: `matches_mapping`, and `warning` when not); the version history; the kept-mapping
  tag; `lostMapping`; and `sharedWarnings` (below). `TranslationDocs.Source` says what an XSLT was generated from:
  `Source.of(mapping)`, `Source.of(indexPlan)`, `Source.of(cefPlan)` or `Source.NONE` (`TranslationDocs.Source`; written by hand).
* **`XsltSaves`**: the public face of the XSLT save path for the tools outside this group (draft_cef_mapping, the
  index tools): `create`, `update`, `lostMapping`, `requiredFor`. Same checks.
* **`HandEditGate`**: keeps an edit made by hand in Stroom's editor since the tools saved an XSLT (see save_xslt).
  `KeptCode` regenerates what the kept mapping or plan generates now, the base the edit is measured from.
* **`RequiredImports`**: the shared XSLTs standing instructions require a stage's XSLTs to import
  (`requiredFor`, `importNotes`, `refuseWithout`, `uncalled`, `xsltRole`).
* **`SiblingSharedCalls`**: the named templates of shared XSLTs that the stage's existing pipelines call, and
  where (read from the children of the stage's templates, at most 5 templates, via
  `PipelineTemplates.sharedXsltUsage`). A call counts only when at least two pipelines make it, and per place the
  call most pipelines make wins. Used before drafting (`intoMapping` for a translation mapping, `forPlan` for an index plan) and at save
  (`notFollowed`).
* **`EventVersions`**: the event-logging versions this Stroom holds, and the one a build's translation writes: the
  one given, else the one chosen for the build (kept in the build's record, `event_logging_version`, and in this
  node's memory for the session), else the only one held, else the user's pick in a form, else `needs_guidance`.
  Never a version this Stroom doesn't hold.
* **`TranslationBuilds`**: the mapping `changes=` apply to, the kept mapping, `generated` (the generator's result
  compiled as Stroom compiles it), stream meta no sample carries, a named time zone the event never records, the
  Unknown, domain and stand-in choices kept with the XSLT, and the build's source notes.
* **`SentMappings`** and **`UnsavedChoices`**: the mapping last sent for an XSLT (by uuid, or `build/name`) and the
  session's draft, under `/outputs/mappings/` in the session's workspace (where the agent can read them too), and
  the user's form answers for an XSLT not yet saved with them, under `/outputs/choices/`. A session without a
  workspace keeps neither.
* **`MappingSampleCheck`**, **`MappingProblems`**, **`DomainSplits`**, **`PlaceholderChoices`**,
  **`CurrentTranslations`**, **`LookupAgreements`**, **`XsltHistory`**, **`TranslationArgs`** (the shared argument
  descriptions and parsers): see the tools below.

The version history `XsltHistory` keeps in an XSLT's description records each save's author (the user, as Stroom
records a document's updater) and `by`: `Stroom <version>, <caller>`, where the caller is the external MCP client
by its own name, or the chat by the model it runs on.

### `draft_translation_mapping`

content · read-only · unlocked by View Data (sample streams are read as the user); checks View on the source notes
and streams it reads · Classes: `DraftTranslationMappingTool`, `DraftMapping`, `SiblingSharedCalls`,
`CurrentTranslations`, `EventVersions`, `SentMappings`, `MappingCompaction`

A starting translation mapping drafted from the sample, so the agent edits a valid mapping rather than writing one
from nothing (a model asked to write one tends to send back the field inventory). `DraftMapping.draft` gives the
input kind and layout from the profile, the obvious event-logging homes for fields by name (time with its pattern,
host, client and server addresses and ports, user, event type, message: `FieldHomes`), one rule per kind of event
the naming field shows, a text field's key/value pairs read as one extract entry a key, every other field carried
as Data, and notes on what is left to decide (the action element per kind, System Name and Environment, a time
zone). `ActionRules` reads each kind's own records for its action: connections allowed or denied become
Network/Permit or Deny, logons and logoffs Authenticate, configuration changes Update, a service started or
stopped Process, health and state records Alert. With `build`, the build's source notes (record_source_notes) are
followed: catalogued events become rules with their action element, checked against the schema
(`SourceNotes.detailProblem`; unchecked rather than ignored when the schema can't be read).

The user chooses the event-logging version first (`EventVersions.target`), before anything is read. The draft is
compacted (`MappingCompaction.compactRules`, `compactPairs`) so it fits a reply: a rule's leftover fields as its
`data` list, pairs as `pairs` entries.

Then the environment's common elements: `SiblingSharedCalls.intoMapping` adds the shared templates the event
pipelines call (by two or more pipelines, writing one element, with no parameters) as `shared` entries, removing
what the draft mapped below that element; a call with parameters, or writing several elements, is left as a note
for the agent. This is done before drafting, not at the pipeline step, because the XSLT had otherwise been
generated without them.

* **Arguments**: `samples` (pasted text, by name or a list) or `stream_ids` (uploaded sample streams, read by the
  tool); `source_name`, `system_name`, `environment`; `splitter` (a build_data_splitter spec, else inferred);
  `build` (for source notes, the chosen version, the current translation); `schema_version`.
* **Returns**: `mapping`, `source_notes`, `splitter`, `format`, `records`, `kinds`, `notes`, `unmapped_fields`,
  `fields_seen`; `schema_check` (`ok`, each problem's first sentence cut near 220 characters, at most 6 warnings;
  `ok: null` and `note` when the schema can't be read); `schema_version` and `schema_version_settled`;
  `current_translation` when the build's feed is already translated by a pipeline whose XSLT looks values up; for
  XML fragments, `xml_namespace` set to the namespace of the environment's wrapper; `hint`. Or the version question
  (`needs_guidance`).
* **Refuses**: no samples and no stream_ids; a pattern of the agent's that took too long on the sample
  (`PatternTimeoutException`, returned as the refusal).
* **Notes**:
  * The draft is kept for the session (`SentMappings`, as `draft:<build>` and, with a build, `draft:`), so the
    first save sends `changes=` only: a 50 KB draft written out whole in one call never reached the tool in
    testing.
  * `current_translation` lists what the current XSLT looks up (its `stroom:lookup()` maps, the imported functions
    and named templates it calls, the reference data the pipeline loads) with a note asking whether the next version
    keeps them: in testing a V1.3 drafted from the sample lost V1.2's IP lookups.
  * It stays read-only although it records the chosen version in the build's record: that record is the tools' own
    bookkeeping.

### `build_translation_xslt`

content · write; read-only when neither `build` nor `uuid` is given (the code comes back) · unlocked by View Data
(sample streams); checks View and Edit; the field-mapping preview steps as the user (Stepping); the XSLT is created
in the build folder (Create) or changed (Edit) · changes only its build · takes a run slot · Classes:
`BuildTranslationXsltTool`, `TranslationBuilds`, `TranslationDocs`, `MappingSampleCheck`, `MappingProblems`,
`DomainSplits`, `PlaceholderChoices`, `UnsavedChoices`, `SentMappings`, `RequiredImports`, `CurrentTranslations`,
`Consent`, `XsltGenerator`, `MappingChanges`

Writes the event-logging translation XSLT from a field mapping, and with `build` and `name` (or `uuid`) saves it
with the mapping kept in its description, returning the document rather than the code, so the XSLT need not pass
through the model. A call runs in this order:

1. **The mapping**: given whole (`mapping`), or `changes` merged (`MappingChanges.applyChanges`) into the mapping
   last sent for this XSLT (`SentMappings`), else the one kept with it (by uuid, or the build's XSLT of that name),
   else, for a build and name not saved yet, the session's draft (`changes_applied_to` says so); or neither, with
   `uuid`, to regenerate from the kept mapping as the generator writes it now (an agent asked to restyle an XSLT
   once sent its 1,400-line kept mapping back whole). Text that is JSON is parsed, its error located with the text
   around it. A list of fields given as the mapping with a sample comes back as a draft (`needs_mapping`) rather
   than an error, as small models send an inventory.
2. **The version**: a saved XSLT's own (kept with it), else `EventVersions.target`.
3. **Generation** (`TranslationBuilds.generated`): `XsltGenerator.generate` with the imports standing instructions
   require added, then compiled as Stroom compiles it (`XsltCompileCheck`): one that doesn't compile is a problem
   and is not returned or saved (seen: an XSLT saved `ok` whose every record failed with "Variable kv has not been
   declared"). Problems come back at once, before the sample is read, with the rules' `events` left out so the
   whole list fits one reply (seen: 20-70 KB results each round, fixed a part at a time).
4. **The sample check** (`MappingSampleCheck.check`, below), over `sample` or the `stream_ids` streams' text.
5. **The user's choices** (only when saving): a user's Id whose values carry a domain (`DomainSplits`: split into
   Id and Domain, or keep whole; one question for all fields not answered before), and values that stand for none
   (`PlaceholderChoices`: leave each out, as `nil_values`, or keep as written; one form, at most 20 questions, 19
   one by one and one for the rest when more). If either changed the mapping it is generated and checked again.
   Each answer is kept at once (`UnsavedChoices`), then with the saved XSLT (`TranslationBuilds.rememberAgreed`),
   so it is asked once: before, a later form failing lost the earlier answer and the user was asked on every call.
   Warnings the user has answered are dropped, including those the second check brings back.
6. **Checks against the build**: rules contradicting the source notes' event catalogue (`source_notes_check`);
   a mapping that keeps none of the current translation's lookups (warning).
7. **The field-mapping preview** (`field_mapping=true` with `pipeline_uuid` and `stream_ids`): the pipeline is
   stepped with a marked copy of the XSLT (up to `max_records`) and the section rendered by `EventFieldDocs`.
   Without it `field_mapping` is null and `field_mapping_needs` says why: a table from the mapping alone showed how
   values are computed, not what they are, and was copied into documentation as it was.
8. **The save**, through `TranslationDocs.createXslt` / `updateXslt` (see save_xslt for the checks). Rules kept as
   Unknown are agreed with the user first (below).

* **Arguments**: `mapping` or `changes` (objects, or JSON text); `schema_version`; `feeds` (for standing
  instructions); `sample` (text or list) or `stream_ids`; `splitter`; `build` and `name`, or `uuid`; `change` (the
  version history line); `include_xslt` (also return the code when saving); `reviewed` (a `review_id`);
  `field_mapping`, `pipeline_uuid`, `max_records` (1 to 1000, default 50); `discard_hand_edit`,
  `hand_edit_choices`.
* **Returns**: the generator's result (`ok`, `xslt` unless saved, `problems`, `warnings` grouped, `events`),
  `schema_version` (and `schema_version_settled`), `required_imports`, `sample_check` (`records`, `problems`,
  `warnings`, `fields_seen`, `not_checked`, `kept_unknown`, `note`), `changes_applied`, `changes_applied_to`,
  `source_notes_check`, `set_properties` and `set_properties_note` (JSON input: the JSON parser's `addRootObject`,
  true for JSON lines, for create_pipeline), `field_mapping` / `field_mapping_sample`, `saved` (`type`, `uuid`,
  `name`, `version`) with `next`, `follow_on` (on a replace: review_coverage reviews the pipelines reading its
  Events), `standing_instructions`, `hint`. Or `needs_review`, `needs_guidance` (version, hand edit) or
  `needs_mapping`.
* **Refuses**: `mapping` and `changes` both given; neither, with no `uuid`; `changes` with nothing to apply to;
  a mapping that doesn't validate (each error with where it is and the value given, a whole example mapping when
  its shape is wrong: `MappingProblems`); `max_records` out of range; a `splitter` that isn't a spec; saving with
  `build` but no `name`; keeping a rule Unknown with no sample records to show (see below); and every save
  refusal of save_xslt.
* **Notes**:
  * **Unknown agreed per rule** (`agreeUnknown`). A rule kept as Unknown (`allow_unknown`) is put to the user with
    the sample records it catches: first `needs_review` with `show_user` (markdown, per rule: how many records,
    what they hold, what their values suggest instead) and a `review_id`; the agent shows it and calls again with
    `reviewed=<review_id>`, and a short confirmation form opens (`Consent.require`). The form was kept short because
    the detail in it was too much small text. What was agreed is kept with the XSLT by rule and by its conditions and
    reason (`TranslationBuilds.unknownKey`), so only a new or changed rule is asked about again (seen in production:
    asked on every save); an earlier agreement is audited. A rule that catches none of the sample isn't asked
    about. Without sample records the call is refused: the form had said "no sample records checked" and logoffs
    went to Unknown unseen.
  * **The sample check** reads the records as the pipeline's parser would (`LocalCheck.sampleRecords`; a
    data_splitter sample with no spec is read with the one inferred from it, as build_data_splitter infers it, and
    a field that guess doesn't name is then only a warning). It reports fields no record has, time formats the
    values don't fit (a problem: it would fail every record), schema values a constant or map gives, xpaths that
    select nothing, an xpath without `repeat` giving several values, Data a rule's records never hold, required
    elements the sample leaves empty, domain-qualified Ids, stand-ins for none, and each extraction regex run with
    XPath's regex rules on the text it reads (`XPathChecks`). It stops after 60 seconds (`CHECK_TIME`), naming the
    extractions left unchecked: a Fortigate sample (10 streams of 1 MB, 60 extract entries) held the call for over
    a minute.
  * Stream meta the mapping reads (`stroom:meta('Name')`) that no sample stream carries is a warning, as is a
    time read in a named zone that the event doesn't record.
  * **Shared templates** (`TranslationDocs.sharedWarnings`, on an `ok` result): a warning for each shared template
    the event pipelines call that this XSLT doesn't, saying whether its mapping writes that element itself or
    leaves it out; and for a required import imported but none of whose named templates is called.
  * In the chat a form stops the call, which is made again with the answer: the mapping sent is then not kept, or
    the changes would be merged twice (seen: "changes.common removes 'EventDetail/Purpose', which the mapping does
    not have"). A form that can never be shown (too large, say) fails the call with a refusal telling the model not
    to repeat it, rather than reporting that the user declined.
  * After a save, what was saved is what the next `changes=` start from (`SentMappings.remember` by uuid).

### `rebuild_mapping`

content · write · unlocked by Stepping; checks View and Edit (the XSLT, its pipeline and the XSLTs it imports are
read as the user; the save is the user's Edit) · confirms itself · takes a run slot · Classes:
`RebuildMappingTool`, `XsltReader`, `MappingRebuild`, `RebuiltMapping`, `SampleStreams`, `PipelineStepping`,
`TranslationDocs`, `Consent`

Rebuilds the mapping kept with a translation XSLT from the XSLT itself: when it was lost (the Documentation tab
cleared; build_status says so) or no longer matches the XSLT (edited by hand in Stroom). The XSLT is read back as
the generator writes it (`XsltReader.rebuild`). With the kept mapping, every element that still reads as it
generates keeps its entry exactly and what differs becomes new or removed entries; without one, the mapping is read
from the XSLT whole: fields, key=value extractions, maps, time formats, defaults, reference-data lookups,
dictionaries, and the shared templates and functions of the XSLTs it imports (fetched by name as Stroom resolves
the import, only when exactly one such XSLT is visible to the user); anything else is kept as an xpath entry.

The XSLT regenerated from the rebuilt mapping is then stepped beside the XSLT over the sample (up to 200 records
each way, `PROVE_RECORDS`) and the outputs compared path by path (`EventXml.fieldValues`). Only when every record's
output is the same (or the user accepts the differences) and the user confirms in a form are the mapping and the
regenerated XSLT saved.

* **Arguments**: `uuid` (required); `stream_ids` (else `SampleStreams`: the stream ids of the sample processor
  filters the tools made, those still there, else the feed's newest raw streams; the reply says which);
  `pipeline_uuid` (else the one pipeline whose own layer runs the XSLT); `change`; `accept_differences`;
  `accept_unread`.
* **Returns**: `xslt`; `status: in_step` when the XSLT is already what its kept mapping generates; else `mode`
  (`written by an earlier generator`, `out of step with the kept mapping`, `mapping lost`), `rebuilt` (the summary:
  kept, new, removed, kept as xpath, calling imported functions), `proven_on` (`records`, `streams`, `which`),
  `differences` (at most 20 paths, most records first, each with an example record), `unread`, `problems`, `saved`
  and `next`.
* **Refuses**: no `uuid`; an XSLT that isn't the tools' own; one keeping a CEF or index plan (rebuild is for
  translations); no pipeline found or several (`pipeline_uuid` needed); a `pipeline_uuid` that doesn't run the
  XSLT; no Stepping permission.
* **Notes**:
  * **Unread parts stop it** (`accept_unread`): anything inside an event the reader couldn't read back (an
    `xsl:iterate` added by hand, a variable built from its content rather than a `select`) is listed as `unread`
    and nothing is saved unless the user accepts losing it. Stepping alone missed such an edit when no sample record
    reached it, and the regenerated XSLT would lose it for the whole feed.
  * An XSLT not laid out as the generator writes one (no record template) reads back as nothing: the hint then says
    the kept mapping is still there and to carry the edit in with build_translation_xslt `changes=` instead (seen:
    the agent took the empty read for an empty mapping).
  * Mapping problems from the read, or a rebuilt mapping that doesn't generate, save nothing and say so.
  * The save goes through `TranslationDocs.updateXslt` with the hand-edit gate bypassed (the edit is what the
    rebuilt mapping carries, proven on the sample), and the saved mapping becomes what the next `changes=` start from:
    in testing, changes= started from the copy sent before the edit, and the edit was lost.
  * Stepping is within the session's time limit for a long call, and audited.
  * It saves only an XSLT in a build (`checkManaged`); a promoted one is rebuilt through a working copy.

### `save_xslt`

content · write · checks View and Edit; the XSLT is created in the build folder (Create) or changed (Edit); the
XSLTs it imports are read as the user · changes only its build · Classes: `SaveXsltTool`, `TranslationDocs`,
`HandEditGate`, `KeptCode`, `RequiredImports`, `SiblingSharedCalls`, `LookupAgreements`, `XsltHistory`,
`IndexingMemory`

Saves an XSLT written by hand, or an indexing XSLT generated from its index plan (`index_plan`, or with no code the
plan kept for the session): creates it in the build, or with `uuid` replaces the code of one the tools created
(including working copies of production XSLTs). A translation generated from a mapping is saved by
build_translation_xslt; code saved here over one keeps the mapping, which then no longer says what the XSLT writes.

The checks every XSLT save makes (`TranslationDocs.createXslt` / `updateXslt`), in order:

1. **check_xslt** (`checked`): not saved if it fails. With the build's XML sample, its namespace is the input's.
   On an update, refused calls and instructions the replaced code already had stay allowed.
2. **Required imports** (`RequiredImports.refuseWithout`): an XSLT of a stage whose standing instructions require
   shared imports, without them, is refused.
3. **The hand-edit gate** (updates only; `HandEditGate.check`, below).
4. **The version** (`version`).
5. **DNS lookups** (`LookupAgreements.require`): code calling `stroom:host-name()` or `stroom:host-address()` that
   the replaced code doesn't is saved only once the user agrees in a form, as the lookup sends record data to the DNS
   server whenever the pipeline runs. Without a form the save is refused. Agreements are remembered for the session
   by document and function, and cover stepping too.
6. **The description**: the history moves out of the code into the description (`XsltVersionHistory.adopt`), the
   mapping or plan is kept (`KeptMapping.withMapping`, compact), the change is recorded pending, the build's changes
   are previewed, and an XSLT saved with a mapping is tagged (`WriteGuard.KEPT_MAPPING`) so a mapping later deleted
   from its Documentation tab is noticed (`lostMapping`). For a translation, `matches_mapping` says whether the code
   is what its mapping generates; for an index plan, `ecs_check` lists its convention problems (saved all the same).

* **Arguments**: none required by the schema (it required `build` and `name`, which an update by `uuid` doesn't
  give; found reviewing the tools, as were `save_text_converter`'s and `save_dictionary`'s): `build` and `name` for a
  new XSLT, or `uuid` to replace one; `code`, or `index_plan` (object or JSON text), or neither (the session's plan);
  `version`; `change` (default `Created by hand`, `Created from its index plan`, `Saved again ...`);
  `discard_hand_edit`; `hand_edit_choices`.
* **Returns**: `type`, `uuid`, `name`, `version`, and `matches_mapping`, `warning`, `ecs_check`, `warnings`
  (shared templates) as they apply, with the build's `next`. Or the hand-edit question (`needs_guidance`, no
  `uuid`, nothing saved).
* **Refuses**: no code and no plan to take ("draft_index_mapping drafted several, or none"); an index plan kept
  with the XSLT that no longer reads; an `index_plan` that isn't one; a new XSLT without `build` and `name`; any
  check above; a `hand_edit_choices` answer other than `keep`, `overwrite` or `per_field`.
* **Notes**:
  * **The hand-edit gate**. Only when the XSLT's description says its code differs from what the tools last saved
    (`XsltVersionHistory.untouched` is false). `HandEdits.undone` compares the code the kept mapping or plan
    generates now (`KeptCode`), the current code and the new code: what the edit wrote and read (element and key
    names, constants, XPath) that the new code drops is "undone". If nothing is undone, the save goes ahead. Where
    the agent's change is to a field the edit changed too (`HandEdits.collisions`), the user decides: first one
    question (overwrite the XSLT, every hand edit dropped, or decide field by field), then per field (keep my hand
    edit, or use the proposed field). The first question names at most 5 fields (a wide mapping made a question too
    long for a form) and quotes at most 150 characters. Without forms the questions go to the agent as
    `needs_guidance` (`hand_edit`, `hand_edit_collisions`) for `hand_edit_choices` (`{'*': 'overwrite'}` or per
    field `keep` / `overwrite`). Answers are remembered for the session, by XSLT and its current code (at most
    10,000). Whatever is still undone is refused, with the edit's diff against what the mapping generates and how to
    carry it in (`HandEditGate.carry`: rebuild_mapping for a translation, the index plan's fields, or the CEF plan's
    overrides); `discard_hand_edit=true` only when the user says to drop it.
  * **An edit no mapping can hold**: when the XSLT holds what the reader can't read back (an `xsl:iterate` making
    users distinct by login name, inline or building a variable: `HandEditGate.unexpressible`), the refusal says it
    can't be carried into the mapping and the choice is the user's: keep the XSLT as it is (and make no generated
    change to it), or drop the edit.
  * **Shared templates** (once saved): warnings, as in build_translation_xslt; for an index plan, whether the plan
    writes the key itself or leaves it out.
  * An indexing XSLT from a plan gets the imports standing instructions require of indexing XSLTs added, and fields
    that can hold several values in an event marked from the schema (`IndexingHelpers.withCardinality`; a plan kept
    before they were marked).

### `build_data_splitter`

content · write; read-only without `save_as` · unlocked by View Data (sample streams); with `save_as` the converter
is created in the build folder as the user (Create) · changes only its build · Classes: `BuildDataSplitterTool`,
`SplitterDryRun`, `SplitterXml`, `StroomSplitterRun`, `SampleProfiling`, `TranslationDocs`, `ErrorTriage`

Writes a Data Splitter from the sample: the format is profiled and a spec inferred (`SplitterDryRun.inferSpec`)
unless one is given; the spec is run on the sample locally (a dry run, up to 2,000 records spread over a long
sample) and the records, the lines that match nothing and the field names come back. With several sample streams
the spec comes from the first and is run on each. Supported specs: delimited (with or without a header, `escape`),
key_value (with `spaced_values`: pairs read from the end, for unquoted values holding spaces), regex with named
groups, syslog (RFC 5424 or 3164) with a parsed body, and `fields` (each field by its own regex, any order); options
`record_start`, `record_end`, `quoted_newlines`, `comment_prefix`, `header_directive`, `directives`,
`blank_line_records` and `catch_all` (a record the spec doesn't match kept whole as `unparsed`, to agree with the
user). The converter (`SplitterXml.converter`) gives each part an `id` Stroom's errors name and passes over blank
lines, which Stroom otherwise writes as records of empty fields.

The converter is also run by Stroom's own Data Splitter parser on each sample text (`StroomSplitterRun`), as
`stroom_check`, because the dry run once parsed lines Stroom lost (over its read buffer, a key=value value holding
a space) and kept white space Stroom trims.

* **Arguments**: `sample` (text) or `stream_ids`; `spec` (object); `save_as` and `build` (defaults to the session's
  build); `buffer_size`.
* **Returns**: `spec`, `inferred`, `converter`, `converter_type` (`DATA_SPLITTER`), `format`, `records`, `fields`
  (inventory), `first_records` (5), `unmatched_lines` (10), `unmatched_count`, `buffer_size` when set,
  `stroom_check` (`matches`, `records`, `errors` with `error_hints`, `record_counts`, up to 5 `differences` of
  `dry_run` against `stroom` values, `loses_records`, `summary`), `line_endings`, `hint`, and `saved` or
  `not_saved`. For XML fragments: the wrapper converter (`XML_FRAGMENT`, the environment's own where it has one),
  `xslt_input`, and the namespace the mapping reads the fragments in.
* **Refuses**: JSON or single-document XML ("needs no Data Splitter", naming the parser and build_translation_xslt's
  input); a format it can't infer (asks for a regex spec, with an example written for the sample's lines); a `spec`
  that isn't one (with an example for the format); `buffer_size` outside 20,000 to 1,000,000,000; no sample and no
  spec.
* **Notes**:
  * `save_as` doesn't save while any line matches nothing, while the sample's lines end in a carriage return alone
    (Stroom's Data Splitter takes carriage returns out, so the stream is one line and writes no records, with no
    error; fix at the source or with a FindReplaceFilter), or while `stroom_check` loses records (errors, or a
    record count different from the dry run's). Value differences alone are reported, not blocking.
  * **The read buffer**: Stroom reads a Data Splitter's input through a buffer of `bufferSize` characters (20,000 by
    default) refilled only once half is read, so only a record up to half of it is sure to fit; a longer one matches
    nothing and is lost. When the sample's longest record (its longest line, or a multi-line record whole) is more
    than half the default, `bufferSize` is written at four times it, rounded, at most 1,000,000,000
    (`SplitterXml.bufferSizeFor`); `buffer_size` sets it outright. Seen in testing: CSV lines of about 20,000
    characters lost in processing, and a 751,000-character line with a buffer of 1,000,000.
  * The `stroom_check` summary says how many characters of sample it covered: "parses exactly as Stroom does" was
    taken for the whole feed in testing, where longer records further in were lost. Only a dry run of fewer than
    2,000 records is compared record by record.
  * `error_hints` come from the bundled error triage rules, for errors classed as blocking (the buffer exhausted,
    unmatched content).
  * The save goes through `TranslationDocs.createTextConverter`, with save_text_converter's checks.

### `save_text_converter`

content · write · checks View and Edit; the converter is created in the build folder (Create) or changed (Edit) ·
changes only its build · Classes: `SaveTextConverterTool`, `TranslationDocs`

Creates a text converter in the build (in its Events folder, until create_pipeline moves it), or with `uuid`
replaces the code of one the tools created. For templates whose parser needs one (`DSParser.textConverter`,
`xmlFragmentParser.textConverter`).

* **Arguments**: `code` (the only one required by the schema); `build` and `name` for a new converter, or `uuid` to
  replace one; `converter_type` (`DATA_SPLITTER` or `XML_FRAGMENT`; default `DATA_SPLITTER`); `version`.
* **Returns**: `type`, `uuid`, `name`, `version`, with the build's next step.
* **Refuses** (`TranslationDocs.checkConverter`): blank code; an XML_FRAGMENT that isn't a wrapper (no `<!ENTITY
  fragment SYSTEM "fragment">` and `&fragment;`); a Data Splitter holding a JSON element ("a text converter cannot
  parse JSON": the template's JSONParser does, with no converter); one that isn't well-formed, or whose root isn't
  `<dataSplitter>`; one that adds `ignoreErrors="true"`, which hides the errors for text no expression matches so
  that text is lost without a word (one the replaced converter already had is kept); a new converter without
  `build` and `name`; an update of a converter that isn't the tools' own, or that changed since `version`.

### `save_dictionary`

content · write · checks View and Edit; created in the build folder (Create) or changed (Edit) · changes only its
build · Classes: `SaveDictionaryTool`, `TranslationDocs`

A Dictionary doc a translation reads at run time with `stroom:dictionary()`: key=value lines for a mapping's
`dictionary`, plain lines for `in_dictionary`. Creates it in the build, or with `uuid` replaces the entries of one
the tools created. Data that changes or is large belongs in reference data instead.

* **Arguments**: `text` (the only one required by the schema); `build` and `name` for a new dictionary, or `uuid` to
  replace one; `description`; `version`.
* **Returns**: `type`, `uuid`, `name`, `version`, `entries` (non-blank lines).
* **Refuses**: a new dictionary without `build` and `name`; the build already has one of the name; an update of
  one that isn't the tools' own, or that changed since `version`.

### `build_reference_xslt`

content · read-only · checks View (the XML schemas, standing instructions and required XSLTs are read as the user)
· Classes: `BuildReferenceXsltTool`, `ReferenceGenerator`, `ReferenceMapping`, `RequiredImports`

Writes the XSLT of a reference-data pipeline (a child of the reference-data template) from a mapping of maps: for
each record and map, a `<reference>` with the map name, key and value in reference-data:2
(`ReferenceGenerator.generate`). Saves nothing: save_xslt saves it. The events pipeline then names the feed as a
pipeline reference and its mapping reads the map with `lookup`.

* **Arguments**: `mapping` (required; object or JSON text: input, root, record, maps with name, key and value
  parts, imports, drop rules); `schema_version` (default: the highest version of the reference-data schemas this
  Stroom holds, compared as versions (`stroom.util.shared.Version`) whatever order they are listed in, else the
  generator's default; found reviewing the tools: the last listed was taken); `build` (the imports standing
  instructions require of reference XSLTs there are added).
* **Returns**: the generator's result (`ok`, `xslt`, `problems`, `warnings`), `schema_version`, `required_imports`,
  and a `hint` with the steps after (save_xslt, create_pipeline from the reference template, a processor filter on
  the Raw Reference stream, wait_for_processing, then the events pipeline's `references`).
* **Refuses**: no mapping; one that isn't a reference mapping.

## Documentation, CEF and coverage

### `write_documentation`

content · write · unlocked by Stepping (the pipeline is stepped as the user for its Field mapping section);
checks View and Edit; the Documentation doc is created in the build folder or saved as the user · changes only its
build · takes a run slot · Classes: `WriteDocumentationTool`, `BuildDocumentation`, `FieldMappingSections`,
`IndexDocumentation`, `GeneratedDrift`, `KeptMappings`, `DocumentationVersions`, `DocumentWrites`, `DocumentPlaces`,
`AcceptedErrors`, `EventFieldDocs`, `Descriptions`, `ReferenceEvaluation`

Creates or updates the Documentation doc of a pipeline in the build (named as the pipeline, in the same role
subfolder), or with `index_uuid` of an existing index (named as its index doc). The agent writes the markdown; the
tool generates two sections in it and keeps the version control:

* **Field mapping** is never taken from the markdown when the XSLT keeps a mapping or plan: it is generated
  (`FieldMappingSections.fieldMappingSection`) from the mapping (or index or CEF plan) kept with the pipeline's
  XSLT, stepped over the sample (at most 200 records, `RECORDS`), and put in place of whatever the markdown has
  there, so it always agrees with the XSLT. For a translation the pipeline is stepped with the mapping's XSLT
  generated with rule markers (processing instructions, `XsltGenerator.RULE_INSTRUCTION`, which survive stepping
  where comments don't), so each Event is matched to the rule that wrote it. The section opens with "Counts are of
  N sample records (n of stream ...)" so a reader knows which records were counted (seen: counts from a different
  sample taken for a change in routing). An index XSLT written by hand is documented from the documents it writes
  (`writtenFieldsSection`), a CEF one from the lines it writes (`cefWrittenSection`), to Kafka or as text to a file
  alike (`CefLines.linesIn` reads Kafka records' values, a text XSLT's lines, or the text of XML a TextWriter
  writes). A hand-written discovery
  pipeline (stage `discovery`, no plan) is documented the same way from the documents it writes from its raw
  streams, with none of a planned discovery index's framing (its settings aren't known without the plan).
* **Reference maps** is generated for a reference data pipeline (`PipelineShapes` stage `reference`; its XSLT, made
  by build_reference_xslt or by hand, keeps no mapping) in place of the agent's own section of that name
  (`FieldMappingSections.referenceMapsSection`): the pipeline is stepped over `stream_ids` (its Raw Reference
  streams), and the section opens with "Counts are of N sample records (n of stream ...)", then a row for each map:
  its key and value as its XSLT writes them, how many entries the sample gave, and an example key and value; then
  the feed(s) its processor filters read and the pipelines that look the maps up (a pipeline reference naming one
  of those feeds, or the pipeline as loader). The facts are evaluate_pipeline's `reference` section's
  (`ReferenceEvaluation`), shared, not duplicated. Nothing is kept with it, so no digest closes it.
* **Errors** is generated from the pipeline's Error streams: every kind of error with counts and an example, and
  the errors the user accepted as benign with their reasons (`AcceptedErrors`), kept with the doc so triage doesn't
  raise them again. The agent's own sections on errors stay as written.

* **Arguments**: `build` (required); `pipeline_uuid` or `index_uuid`; `markdown`; `change`; `stream_ids`;
  `accept_errors` (a list of `{element, example, reason, matches}`, one object, or JSON text); `field_descriptions`
  (`{field: description}`, or JSON text); `field_mapping_layout` (`by_event_type` or `compact`; else an AGENTS
  doc's Documentation section, else by event type); `index_itself`; `document_missing_fields`; `evaluation`.
* **Returns**: `type`, `uuid`, `name`, `written` (`created` or `updated`: seen, `updated: false` on a new doc sent
  the agent to check it was written), `link` and an open-document action, `field_mapping` (the generated section;
  `reference_maps` instead for a reference data pipeline),
  `note`, the build's `next`; for a promoted pipeline `working_copy_of` and a `next` about promote_build. Or
  `needs_descriptions` (nothing written yet): the fields not yet described, each with where it comes from, sample
  values, `schemas_say` (the vendor notes and the schemas), `under` for a Data name, `was` for one whose source
  changed.
* **Refuses**: no `build`; empty markdown; neither `pipeline_uuid` nor `index_uuid`; an unknown layout; an update
  without `change` ("give change, one line for its version control"); accepting `EventDetail/Unknown` events as a
  benign error (Unknown is agreed per rule in build_translation_xslt); `accept_errors` with `index_uuid`; an
  accepted error without `example` or `reason` (`accept_errors[0].example is required`); a kept mapping with no
  streams to step (`streamsNeeded` says which: sample raw streams, the Events streams indexed or sent); an events
  pipeline whose XSLT keeps no mapping and whose markdown has no `## Field mapping` (or the lost-mapping text when
  it was lost); an index or CEF XSLT written by hand without `stream_ids` (for a discovery pipeline, asking for the
  raw streams it indexes); a reference data pipeline without `stream_ids` (its Raw Reference streams); a section that steps to no events
  (`FieldMappingSections.noEvents` says why, per stream: missing, of another type, or stopped by an error); no
  Stepping permission; an existing doc in the build that isn't the tools' own (`WriteGuard.checkInBuild`).
* **Notes**:
  * **An XSLT edited by hand is documented as it runs** (`GeneratedDrift.editDrift`): the XSLT as saved is stepped
    too, and its own events are documented, each given the rule the marked XSLT gave the same record's event in the
    same place (`EventFieldDocs.runningEvents`; a record where the two give different numbers of events is left
    unmarked, for the rule its elements fit). A note says it was edited by hand, or that it holds what no mapping can
    hold (`HandEditGate.unexpressible`), so it differs from its mapping for good; values and counts are of the XSLT
    as it runs, where they are read from is the mapping's. Seen: a hand edit making users distinct documented from
    what the mapping generates, duplicates and all; before that, TWITTER's links left out. A Field mapping section of
    the agent's own (a table) for such an XSLT is kept as written.
  * **Which streams**: `stream_ids` left out are the streams the pipeline's current code last stepped clean on, kept
    in the build's record (prompts and agents left them out, and every call was refused once first). An events
    pipeline's Events streams given in place of its raw sample stand for the raw streams they were made from
    (`BuildDocumentation.rawParents`).
  * **How the Field mapping is laid out**: event types grouped on what they write besides their Data, an object's
    side ignored, Data every type writes alike as common fields, extractions left to the XSLT; an index's common
    fields only those every event has, each section naming its TypeIds. See `EventFieldDocs` and `IndexFieldDocs`
    under "Field-mapping documentation and the CEF mapping".
  * **Field descriptions**: each field is described by what it means for this source (`Descriptions`): a
    `needs_descriptions` round asks for those not yet described. A Data name read from the same source under
    several elements is asked once (seen: 224 asked for one feed, its 18 Data names once per action element); a
    full path still overrides it. Descriptions are kept with the doc with where each field came from, and one is
    asked for again when its source changes (seen: a description outlived the values it described).
  * The build's source notes add a "Source fields" table (`sourceFieldsMarkdown`).
  * `accept_errors` are recorded only after the user confirms in a form.
  * **Version control**: the title is the doc's name (`DocumentationText.titled`), sections written at `# ` are put
    at `## ` (seen with Haiku: a survey section above the agent's read as empty); an update replaces the body and
    keeps the version-control rows and accepted errors; the change waits, with the build's others, for one row at
    promotion (`DocumentationVersions`). A row's `by` is `<user> (agent: Stroom <version>, <caller>)`.
  * **A promoted pipeline's** documentation is changed through a working copy in the build, which promote_build
    writes back after a backup, not a second doc of its name.
  * `evaluation=true` keeps the markdown as its own doc, `<pipeline> - Evaluation`, beside the pipeline once
    promoted (an earlier one updated through a working copy), nothing generated, the pipeline's documentation left
    as it is: saved as the documentation it would have replaced it at promotion.
  * **An existing index** (`IndexDocumentation`): the user confirms the index doc in a form; the section comes
    from a survey of the index through Stroom; an index this build made is documented through its indexing pipeline
    unless `index_itself`; fields its feeding pipelines write that the index lacks refuse the call unless
    `document_missing_fields`. `stream_ids` given are ignored (and said to be).
  * Stepping is within the session's time limit for a long call, and audited; cut short, the section is of the
    records stepped by then.

### `draft_cef_mapping`

content · write; read-only for a call that saves nothing (no `build` and `name`, no `uuid`) · requires View Data;
unlocked by Stepping (a review steps the pipeline); checks View and Edit; a save creates or changes the XSLT in the
build folder · changes only its build · takes a run slot · Classes: `DraftCefMappingTool`, `CefDraft`, `CefPlan`,
`CefXslt`, `CefLines`, `CefSample`, `CefDocs`, `CefInstructions`, `CefSources`, `CefChoices`, `XsltSaves`,
`EventStreams`, `PipelineStepping`

CEF (ArcSight Common Event Format) output from Events: one flattened CEF line per Event
(`CEF:0|vendor|product|version|class id|name|severity|` then key=value pairs), not CEF fields as XML elements.
Three modes:

* **Draft** (stream_ids only): up to 500 sample Events read (`EventStreams`) and a plan inferred (`CefDraft.draft`): the
  header (vendor from System/Organisation, product System/Name, version System/Version, class id TypeId and name
  Description, each falling back to the action; severity 3 unless given; the version read from System/Version however
  few of the sample's Events have it, as an empty constant stayed empty for the Events that do, asked for by the user;
  vendor and product from their path only when at least half the Events have it), the fields every event shares, and per
  kind of event (its action element) the rest: ArcSight's standard keys first with a value's type checked against its
  key (an IPv4 address for `src`), then its custom slots (cs1-cs6, cn1-cn3, cfp1-cfp4, deviceCustomDate1-2,
  flexString1-2, flexDate1) with a label each, then keys outside the dictionary only where allowed; what nothing takes
  is listed as not sent. The plan's XSLT is run over the sample (`CefSample`, with the locked-down Saxon processor, no
  resource read) for an example line and a review of its own lines. * **Save** (with `build` and `name`, or `uuid` of an
  XSLT saved with a CEF plan, whose plan is the starting point):
  the XSLT is saved with its plan kept in its description, so the plan never passes through the model again: a
  kafka-records:1 record per Event, its value the CEF line (`kafka`), or lines of text (`text`).
* **Review** (`pipeline_uuid`): an existing CEF pipeline is stepped over the Events streams (records spread over
  them), its lines parsed and checked (`CefLines.review`: header fields, severity, keys outside the dictionary, a
  custom slot without a label, values longer or of another type than their key takes, duplicate keys), each key
  matched to the Event value it carries, and what each event sends nowhere. Its kept plan, or the plan its lines
  imply (header as the first line has it), comes back to change.

Mappings in the standing instructions (`<Event path> -> <CEF key>`, with a label) and the user's `overrides` take
precedence, as do a topic, vendor and pipeline template the instructions name (`CefInstructions`).

* **Arguments**: `stream_ids` (required: Events streams); `custom_keys`; `overrides` (list of `{path, key, label,
  event_type}` or `{path, drop: true}`; one object or JSON text also taken); `header` (vendor, product, version,
  signature, name, severity: a constant or `{source, map, default}`); `output` (`kafka` or `text`; default the
  template's kind, else kafka); `topic`, `kafka_key`; `pipeline_uuid`; `build`, `name`, `uuid`, `change`; `feeds`,
  `folders` (for standing instructions and templates); `style`; `discard_hand_edit`, `hand_edit_choices`.
* **Returns**: `custom_keys`, `applied`, `plan` (compact, without its not-sent list), `problems`, `not_sent`
  (grouped by why, then by event type: seen, 125 entries each repeating the same reason, in results over 100 KB),
  `documentation` (the Field mapping tables; for a pipeline's administrators, asked for by the user, they also give
  an example for each field the sampled lines gave none of, from the sample Events of its kind, the TypeIds each
  event type's Events have, and below each event type the Event values not sent, with a sample of each:
  `CefDocs`), `example_line`, `sample_check` / `sample_check_error`, `notes`,
  `templates` (forwarding templates, existing CEF pipelines with the templates they inherit from, a template the
  instructions name), `kafka_configs`, `from_standing_instructions`, `standing_instructions`, `review` and
  `plan_from` (review), `stopped_early`, `required_imports`, `saved`, `next`, `hint`. Or `needs_guidance` with the
  custom-keys question.
* **Refuses**: `output` other than kafka or text; overrides, header or style that don't read; a review that steps
  to no CEF lines; `uuid` of an XSLT keeping no CEF plan (or whose plan was lost: the lost-mapping text); no
  Stepping permission for a review; a cancelled call ("Reading was stopped: the call was cancelled."). A save is
  not made (`saved: null`) while the plan has problems, or while its XSLT doesn't compile as Stroom compiles it
  (`XsltCompileCheck`; seen: a record key's undeclared prefix found only by Stroom's log when the pipeline ran).
* **Notes**:
  * **Keys outside the dictionary**: asked first (ArcSight usually indexes none of them), unless `custom_keys` is
    given, the standing instructions say, the plan the call works on (by `uuid` or the reviewed pipeline's) says,
    or the user answered for this build before (`CefChoices`, per user, in this node's memory). Before that last,
    the user was asked on every call of one pipeline's building.
  * The save path is `XsltSaves` (save_xslt's checks, hand-edit gate included, carrying an edit into the plan's
    overrides); the imports standing instructions require of CEF XSLTs are added. It asks nothing else itself, so
    over `/mcp` the call is approved as a whole.
  * **Where the pipeline comes from** (`CefSources.templates`): pipelines found by name with Stroom's document
    finder (`*CEF*`, `*ArcSight*`, `*Arcsight*`) as the user may view them, a template the instructions name by its
    exact name else `*<name>*`, and XSLTs that write `CEF:0` found with Stroom's content index (none while it isn't
    ready). The hint's next steps follow: a template the instructions name, else a forwarding template, else
    `create_pipeline standalone='kafka'`.
  * A sample check failure (a plan with imports can't run locally, as no resource is read) is advice only:
    `sample_check_error`.
  * A review steps within the session's time limit for a long call and is audited; cut short it says
    `stopped_early` and reviews the lines stepped by then.

### `review_coverage`

content · read-only · requires View Data; unlocked by Stepping (records are stepped); checks View (only streams of
feeds the user may view) · takes a run slot · Classes: `ReviewCoverageTool`, `CoverageReads`, `ProcessorFilters`,
`IndexTemplateAgreements`, `CefDraft`, `PipelineStepping`, `EventStreams`

Whole-feed coverage of an events pipeline once its feed is processed: the kinds of event the sample missed. The
generated translation logs, into the Error stream of the raw stream a record came from, each record no rule
matched (`No event mapping matched record N (...)`, WARN, `XsltGenerator.UNMATCHED`) and each a rule keeps as
Unknown (`Kept as Unknown by rule 'r': record N (...)`, INFO, benign in triage), with the values its rules test
(up to 4, `LOGGED_FIELDS`). So the kinds are grouped from the Error streams alone, however large the feed, with a
few raw records read only for examples (`CoverageReads.example`).

A feed of hundreds of thousands of streams is read in bounded batches by Id (`max_streams` a call,
`continue_from` carries on; clean streams skipped where Stroom counts them), and what is kept is bounded: counts
per kind, two examples a kind, up to 50 raw streams a kind, the affected streams' count, Id range and time range.
Nothing is processed: reprocessing is the user's to approve.

Then the pipelines that follow it (`follow_on`, default true): those whose processor filters name it, or its
Events feed with type Events (by name, doc reference or in a Dictionary: `ProcessorFilters.followOnPipelines`),
each checked against Events: those given (`events_stream_ids`), or a changed copy's stepped over `raw_stream_ids`
(or the affected or newest raw streams: a copy in a build can't process production streams), or the pipeline's own
for some affected records, at most 200 records stepped. For a CEF plan, its additions for new kinds and values
(`CefDraft.extend`: existing keys kept, clashes moved to free slots) as overrides, and a warning that reprocessing
resends whole streams; for an index plan, the Event paths no field takes (the 25 most common) and, for
Elasticsearch, the template change through check_index_template and the `_delete_by_query` the cluster's admin runs
before the streams are indexed again (on the old Events streams' StreamIds when few, by the reprocessing window when
many); a pipeline whose XSLT keeps no plan is to be stepped and checked by hand. One unreadable follow-on pipeline
is reported with its error, not allowed to hide the others.

* **Arguments**: `pipeline_uuid` (required); `continue_from`; `max_streams` (1 to 5,000; default 500);
  `events_stream_ids`; `follow_on`; `changed_pipeline_uuid`; `raw_stream_ids`.
* **Returns**: `pipeline`; `progress` (`error_streams_read`, `after`, `continue_from`, `done`, `note`,
  `stopped_early`); `missed` (groups, most records first: `found_as`, `kind` (the logged values), `records`,
  `raw_streams`, `examples` with `raw_stream`, `record` and `holds`); `affected_raw_streams` (`count`, `first_id`,
  `last_id`, `from`, `to`) and `affected_raw_stream_ids` (at most 200); `tell_user`; `next` (show the kinds, add
  rules with build_translation_xslt `changes=`, write_documentation with affected streams, how to reprocess);
  `follow_on` and `follow_on_events`.
* **Refuses**: no `pipeline_uuid`; a pipeline that can't be read; one that has processed nothing (no Error and no
  Events streams, on a first call); `max_streams` out of range; a cancelled call; no Stepping permission where it
  steps.
* **Notes**:
  * **Record numbers**: the translation reads `stroom:record-no()` once for every record
    (`XsltGenerator.READ_RECORD_NO`), so every unmatched record is logged with its number. Stroom's split filter
    queues a location per record and only moves on when a location function is called, so reading it only for
    logged records gave a later record's number, then none (seen in testing: every unmatched record of a 160 MB
    stream logged without a number, and none found). The log's numbers count from 1; examples are given from 0, as
    step_records takes them.
  * **Reprocessing advice**, by where the pipeline is and how many streams: in a build and at most 200 affected,
    reprocess_streams (at most 10 a call, each approved); in a build with more, the user processes them in
    production after promotion; in production, exactly the affected streams when few, else a processor filter on
    the feed's Raw Events in the affected time window (every stream in it, affected or not; Stroom supersedes their
    Events), which every follow-on pipeline then takes again.
  * An XSLT saved before Unknown records were logged has its newest Events sampled instead (at most 200 streams,
    200 Events), its Unknown kinds added as groups, and is told to save again from its mapping.
  * Error streams are read until the session's time limit for a long call: `progress.continue_from` is then the
    last stream read and `stopped_early` says why. It reports progress as it goes and stops at once when its thread
    is interrupted.

## Indexing

The indexing tools are build tools (`stroom-mcp-impl`, category indexing, chat group indexing). They stand on the
indexing core (below) and the build layer (`WriteGuard`, `Consent`, `BuildPlan`). Elasticsearch is never written
to: index templates and component templates are read through Stroom's own template code with the Elastic Index
doc's cluster connection, a cluster's connection is tested through Stroom's test, and every change on the cluster
(committing an agreed template, dropping an old index) is a Dev Tools request handed to the user for their cluster
admin. Index docs, dashboards and pipelines are made and changed through Stroom's stores as the user. Each tool's
description starts with its onboarding step (`BuildPlan.onboardingLabel`, e.g. `Onboarding step N of M
(index): `).

Two kinds of work reach these tools besides an Events index. A build started with `kind=discovery` (or known as one
by a discovery-stage pipeline, or a discovery plan kept with its XSLT) skips the translation steps, and its `next`
leads straight to `draft_index_mapping discovery=`; its index doc is made at verification, once documents are in
it. A reference-data build (`kind=reference`) has no index steps at all.

| Tool | Read-only | Confirms itself | Changes only its build | Run slot |
|------|-----------|-----------------|------------------------|----------|
| `get_field_conventions` | yes | no | no | no |
| `draft_index_mapping` | no | yes | no | no |
| `create_index_doc` | no | yes | no | no |
| `create_indexing_pipeline` | no | no | yes | no |
| `rename_index` | no | yes | no | no |
| `propose_index_template` | no | yes | no | yes |
| `check_index_template` | no | yes | no | yes |
| `verify_index` | no | yes | no | yes |

### `get_field_conventions`

Indexing · read-only · checks View on the indexing templates, Elastic Index docs and reference index docs it lists
(fields listed as Stroom lists them for the user) · Classes: `GetFieldConventionsTool`, `IndexingHelpers.conventions`,
`EcsSchema`, `PipelineTemplates`, `IndexingMemory`

How the new index's fields are named. Without `name` it never picks a convention itself: it puts the choice to the
user, in a form where the session can show one (`Consent.choose`), and otherwise returns `needs_guidance` for the
agent to ask. With `name` it returns that profile.

The choice goes in order. With neither `name` nor `backend`, while the session's build is still on a step of its
events pipeline up to processing (`BuildPlan.nextBeforeEvents`), it returns `status: not_yet` with the build's
`next` and asks nothing: the backend, cluster and naming are stage 2's questions (seen in testing: a CSV
onboarding's first question was the indexing backend). Once the build has Events: when every indexing template
(`find_pipeline_templates stage=indexing`) has the same backend, that is the backend, and the templates come back
as `indexing_templates`; when there are both Lucene and Elasticsearch templates, the backend is asked in a form
first, then the naming in the same call (seen in Ask Stroom AI: given the profiles before the backend, the agent
asked both in the chat, and the draft asked the naming again). Without forms, only the backend is returned as
`needs_guidance`, the agent told not to ask about naming yet.

For Elasticsearch the naming choices are, in this order and with these labels: "From an index template" (the user
pastes it into the chat; a form can't carry it), "Follow an existing index in Stroom" (with `existing_indexes`, up
to 60 Elastic Index docs with name, uuid and path), then one "<profile> convention" per profile, only for a user
with no example. The hint says to offer exactly these and recommend none (seen: an agent offered "follow an
existing index", ECS and Stroom flat, and dropped the template the user had). For Lucene, the profiles are put in
a form. An answer given in a form is remembered for the session (`IndexingMemory.chooseNaming`, four hours), so
`draft_index_mapping` with that `convention` or `like_index` neither asks nor confirms again. Following an existing
index with several to choose from asks which in a second form.

* **Arguments**: `name` (profile: `ecs` or `stroom-flat` as bundled); `backend` (`lucene` or `elasticsearch`);
  `ecs_fields` (with `name=ecs`: a field set or prefix, e.g. `process`, `user.target`, listing its fields with type
  and description, up to 200).
* **Returns**: `status: not_yet` with `next` and `hint`; or `needs_guidance` with `options` (Elasticsearch) or
  `profiles` (descriptions), `indexing_templates` where known, and `hint`; or `status: chosen` with `choice`,
  `backend`, `how` or `convention`, `like_index` (when one index was picked), `settled` (what the user decided in
  forms and must not be asked again, and what is still open, e.g. the cluster) and `hint`. With `name`: `name`,
  `profile` (its YAML as JSON: description, structure, field_map, reference_index_docs), `reference_fields` (each
  reference index doc's fields and Stroom types, up to 500 each; one Stroom can't list says why), and for `ecs` the
  schema's `version` and `field_sets`, plus `ecs_fields` when asked.
* **Refuses**: a `backend` other than the two (`backend is 'lucene' or 'elasticsearch', not '...'`); an unknown
  profile (`No convention profile 'x'. Profiles: ...`).
* **Notes**: there is no configured default convention: without a name the choice is always the user's. The ECS
  files and profiles are bundled with the agent knowledge (`AgentKnowledge.conventionsFile`). `conventions(...)` is
  also called by `draft_index_mapping`, which puts the same choice to the user when asked to draft for
  Elasticsearch with no example.

### `draft_index_mapping`

Indexing · write (keeps the user's example index template in the build as a Documentation doc; nothing else is
saved) · unlocked by View Data (reads the Events, and a discovery index's raw streams, as the user); checks View and
Edit · confirms itself · Classes: `DraftIndexMappingTool`, `IndexPlans`, `IndexExamples`, `IndexingInputs`,
`IndexingMemory`, `SiblingSharedCalls`, `PlaceholderChoices`, `MappingChanges`, and the core's `FieldPlan`,
`PlannedField`, `ExampleNames`, `TemplateMappings`, `IndexingHelpers`

Drafts the index for a build as a field plan (`FieldPlan`): name, logical type and source XPath per field, limited
to the paths the sample Events actually populate. It has three modes: a new plan from Events, a discovery plan
(`discovery=`), and an amendment of a kept plan (`changes=`). The reply never holds an indexing XSLT for an Events
plan: `save_xslt index_plan=` (or with no code, from the plan kept for the session) generates it, as half the reply
used to be code the agent never read. Every drafted or amended plan is kept for the session (`IndexingMemory`),
and `save_xslt`, `create_index_doc` and `propose_index_template` take it from there when not given (seen in Ask
Stroom AI: a 50-field plan pasted inline five times, and extra fields lost after the client's step cap).

**A plan from Events.** The streams must be Events (`IndexingInputs.requireEvents`, checked first). For
Elasticsearch with no example, the naming is settled first: a convention the user chose in `get_field_conventions`'
form goes ahead; otherwise the choice is put to the user here (the same form), and drafted as they answer; without
forms the reply is `drafted: false` with the options. `without_example=true` is confirmed by the user in a form
("Index '...' into Elasticsearch without an example index template"). `like_index` reads the index template that
index's indices take, and its component templates, through Stroom (`IndexExamples.templateFromIndex`), else its
field names and Elasticsearch types only (`exampleFromIndex`), and the user confirms what was read and what
couldn't be in a form, unless they picked that index in `get_field_conventions`' form. A component Stroom couldn't
read is asked for alone.

The plan starts with `StreamId` (`@StreamId`) and `EventId` (`@EventId`), then the profile's `field_map` entries
whose path the sample populates (a name once, from the first populated of its paths, so a Network source address
comes from whichever action the event records). The event's action element (Alert, Authenticate, Process, Update;
not Network, not Data) adds up to 20 fields of its own by default, as users asked for them every time; under ECS
these go under the first `custom_namespaces` entry (default: one named after the index), not a top-level group per
action (seen: `authenticate.*`, `copy.*` drafted and passed). Under ECS a Data element whose name is an ECS field's
written another way (`source_ip`, `SourceIp` as `source.ip`) is planned under that name (`ecs_data_elements`);
abbreviations are not guessed. With an example, fields take the example's names for the same data and the rest are
named in its style (`ExampleNames.namesFromExample`), its components composed as Elasticsearch composes them;
`subobjects: false` in the example is kept in the plan. `extra_fields` are then added, and for Elasticsearch
`@timestamp` from `EventTime/TimeCreated` if not planned. The plan is marked with the fields whose source can hold
several values (`IndexPlans.withCardinality`, from the event-logging schema).

**Shared templates.** Left out, `shared` defaults to the shared templates the environment's indexing pipelines call
(`SiblingSharedCalls.forStage("indexing")`, read from the children of the indexing templates, a call counted only
when at least two pipelines make it; `forPlan` takes those writing one key with no parameters, and notes the
others for the agent in `shared_from_pipelines`). `shared=[]` says this index differs. Each shared entry's fields
stay in the plan (the index maps them) but the XSLT calls the template instead of writing them; which fields, and
their types, are read from the shared XSLT itself, found by name as Stroom resolves the import, else typed as
keyword with a note.

**Stand-ins for none.** For Elasticsearch, values in planned fields that look like stand-ins for none (`-`, `N/A`;
`Placeholders`) are put to the user in one form: index each as missing, as null, or as written. Answers are kept
for the session per index and go into the plan's `stand_ins`; without forms, `warnings` say to ask. Seen: W3C logs'
`-` for a missing address was refused by an Elasticsearch ip field. Lucene takes any text, so isn't asked.

**A discovery plan.** `discovery={input, timestamp_field, ...}` drafts a discovery index: raw records indexed as they
are, mapped dynamically by Elasticsearch, with only `StreamId`, `EventId` and `@timestamp` mapped explicitly
(`FieldPlan.forDiscovery`). Elasticsearch only. Given the raw streams, it reads up to three of them (100,000
characters each) to find the fields holding times (`SampleProfiler`): with more than one, or none matching the
proposed field, the user picks in a form, kept for the session, and that field becomes `@timestamp` (and decides
whether the index is a data stream); without forms the reply lists `timestamp_candidates`. JSON and records:2 rows
are also checked for stand-ins for none, with four treatments (missing, null, empty string, as written). A Records
stream (records:2) must be drafted with `input: delimited`, which reads each data element as a field by its name
(seen: profiled as XML it offered only `data@value`, and the agent indexed name/value lists).

* **Arguments**: `backend` and `index_name` (required); `convention` (none for discovery); `events_stream_ids`
  (Events streams, or a discovery index's raw streams; required to draft from Events); `extra_fields` (planned
  fields, as objects); `drop_when` (XPath tests on an Event that keep it out of the index); `like_index` (Elastic
  Index doc uuid or exact name); `example_template` (index template or mapping, as pasted); `component_templates`
  (those the example composes; kept for the session once given); `without_components` (names the user can't give);
  `shared` (shared-template entries `{href, template, at}`; default above); `discovery` (object: `input` `json` |
  `delimited` | `xml`, `record` for XML, `timestamp_field`, `timestamp_chosen_by_user`, `timestamp_format`,
  `meta`, `drop`, `unpack_json`, `ignore_above` 1024, `total_fields_limit` 2000, `stand_ins`); `without_example`
  (Elasticsearch, user has no example); `replaces` (the old indexing XSLT's uuid, or its pipeline's);
  `custom_namespaces` (ECS); `changes` (amend instead of drafting); `pipeline_uuid` (with `changes`, the pipeline
  whose kept plan to amend). `plan`, `discovery`, `extra_fields` and `shared` may be given as JSON text.
* **Returns**: `plan`, `problems` (`FieldPlan.required()`), `rendered` (Lucene field list, or Elasticsearch index
  template), `convention_paths_not_in_sample`, `populated_paths_not_mapped`, `fields_not_in_sample`,
  `field_mapping` (the Field mapping section's Markdown), `ecs_check` where it departs from ECS, `from_example`
  (notes on naming, shared fields and the kept example), `shared_from_pipelines`, `ecs_data_elements`, `replaced`
  (with `replaces=`: `{xslt, fields, not_in_draft, hint}`, the fields the old index wrote that the draft doesn't
  cover by source, each with its source and how often the sample populates it), `warnings`, `hint`. A discovery
  draft returns `plan`, `xslt`, `rendered`, `warnings`, `hint`. An amendment returns `changed` plus the plan reply.
  An amendment is checked against the sample the plan was drafted from, kept for the session, as a draft is:
  `fields_not_in_sample` and `populated_paths_not_mapped` (seen: a field added by `changes=` from a path no event
  has, and nothing said).
  Other rounds: `needs_components` (the example's components not given: `composed_of`, `given`, `missing`,
  `request`), `needs_guidance` (discovery time field), or the naming options with `drafted: false`.
* **Refuses**: a bad `backend`; no `events_stream_ids` for an Events draft; a stream that isn't Events (a Records
  stream is told to use `discovery={'input': 'delimited', ...}`; any other type to process the sample through the
  events pipeline first); no convention profile and no example (`... ask the user for an example index template
  (or an existing index to follow), or a convention`); `custom_namespaces` that are ECS top-level names; a discovery
  plan for Lucene; a records:2 sample drafted as other than delimited; `like_index` matching no single doc the user
  can view; a `replaces` that is neither an XSLT nor a pipeline running one; `changes` with no plan drafted for
  `index_name` (and no `pipeline_uuid` whose XSLT keeps one); an amended plan that doesn't validate (`The amended
  plan isn't valid: ...`); argument objects that don't validate (`plan isn't a valid field plan: ...`).
* **Notes**:
  * An amendment (`changes=`) merges only what changes (`MappingChanges.applyPlanChanges`): each field replaces the
    one of that name or is added, `{"name": ..., "remove": true}` removes one, any other key replaces it. Without
    streams it is checked against the population the plan was last drafted from (kept per index), so a field added
    from a path no event has is still caught.
  * Fields of objects: an Elasticsearch plan may hold a field of `type: object` with `items` (each item's source
    read from each element the field's `source` selects, which may be a union such as `EventSource/User |
    EventDetail//User`) and optionally `distinct_by` (the key, from each element, that makes objects distinct). It
    is written with `xsl:for-each-group`: one object per key, from the first element with it, in the order first
    met; an element without the key is left out (e.g. every user an event names, once each by login name). The
    index template maps it as its items (`users.login`), and searches use those names (`FieldPlan.leafFields`). An
    item can't have items, names must be unique, and `object` goes with items both ways. A Lucene plan with one is
    a problem: plan a field of values per item instead.
  * The example the user pasted, or the index they chose to follow, is kept in the build as a Documentation doc
    `<index> example index template` (role folder Indexing, `IndexExamples.keepExample`), verbatim with its
    components, so `propose_index_template` follows it even once the conversation no longer holds it (seen: a
    summarised conversation lost it, and the agent proposed a template of its own as the user's example). Failing
    to keep it is logged; the plan stands.
  * A field read from one named Data entry (`.../Data[@Name='x']/@Value`) is counted by that entry's population,
    so it isn't reported as not in the sample.
  * `replaces` exists because an agent comparing its draft with the old XSLT by reading it from a spilled file fell
    into a loop.

### `create_index_doc`

Indexing · write · checks View and Edit; the user needs Create in the build folder; an Elastic Index doc's cluster
must be one the user may view · confirms itself · Classes: `CreateIndexDocTool`, `IndexFields.addLuceneFields`,
`BuildIndexes`, `ElasticClusterTests`, `IndexPlans`

Makes the build's index doc in its folder (role Indexing): a Lucene Index in a volume group (partitioned by month,
one shard per partition) with the plan's fields, or an Elastic Index doc on an existing Elastic Cluster doc naming
the index or data stream, its cluster then tested. The user confirms the backend, names and target in a form, and
may correct the doc name and (Elasticsearch) the index name there; the reply carries the names as made (seen in Ask
Stroom AI: the reply kept the name from before the user's edit, and the agent offered it back to them).

Given the name of the build's existing Lucene index doc again with a plan, it adds the plan's fields the index lacks
instead, once the user confirms, and says to reprocess the Events through the indexing pipeline and verify again
(seen: a field wanted after indexing couldn't be added, and the agent asked the user to add it in Stroom, or made a
second index).

* **Arguments**: `build`, `backend`, `name` (doc name), `time_field` (required); `index_name` and `cluster_uuid`
  (Elasticsearch); `volume_group` (Lucene, default `Default Volume Group`); `plan` (Lucene: its fields are added,
  keywords as TEXT with the KEYWORD analyzer; omitted, the plan drafted for this index in the session, if its
  backend matches).
* **Returns**: `type`, `uuid`, `name`, the target (`cluster`, `index name`, or `volume group`); Elasticsearch:
  `test` (`{"result": {"ok", "message"}}` or `{"error"}`) and `pipelines_renamed` when the user changed the index
  name; Lucene: `fields` (`{index, added, already_present}`, or a note that the index indexes nothing until it has
  the plan's fields). With the build's `next`.
* **Refuses**: a bad `backend`; Elasticsearch with no index name (from `index_name` or the plan) or no cluster (none
  given, and the existing Elastic Index docs use more than one, or none); a `cluster_uuid` that is an Elastic Index
  doc (`... is the Elastic Index doc 'x', not an Elastic Cluster: its cluster is 'y' (cluster_uuid=...)`, seen when
  the index offered to follow was given as the cluster) or no cluster at all; while one of the build's indexing
  pipelines writes to an index no doc names, an `index_name` none of them writes to (seen in testing: the doc named
  one way, the pipeline another; rename later with `rename_index`); renaming a pipeline whose index name is built
  from values (`{...}`); the build's Lucene doc again without a plan.
* **Notes**: the cluster defaults to the one every existing Elastic Index doc uses, said in the form (seen in VS
  Code: refused for want of both though the plan named the index and every doc used one cluster). A name the user
  changes in the form goes to the build's indexing pipelines writing to the one proposed (`BuildIndexes.setIndexName`).
  The test is Stroom's connection test of the cluster, as its Test Connection button runs it; it doesn't look for
  the index, which a new one won't have yet. Lucene fields are listed and added through Stroom's index service
  (`IndexResource.findFields`, `addField`), only on the build's own index (`WriteGuard.checkInBuild`).

### `create_indexing_pipeline`

Indexing · write · checks View and Edit: the user needs Create in the build folder, Edit on the pipeline, View (or
Use) on the template, XSLT and index doc, and Edit on the build's Lucene index for fields added · changes only its
build · Classes: `CreateIndexingPipelineTool`, `CreatePipelineTool.fromTemplate` / `withoutTemplate`,
`PipelineShapes`, `IndexingInputs`, `IndexFields`, `KeptMapping`

Stage 2: creates an indexing pipeline as a child of an indexing (or discovery) template, on `create_pipeline`'s
path (`CreatePipelineTool.fromTemplate`, which `create_pipeline` uses too), so the user confirms the template and
name in that form, the name editable. It sets the XSLT on the template's open XSLTFilter, and where it indexes: the
Lucene Index doc on the open index property, or the Elasticsearch index name (and the cluster where the template
leaves it open, or where the Elastic Index doc's cluster differs from the template's: seen in Ask Stroom AI, the doc
on ES_DEV and the pipeline left on the template's ES_PROD, so its index template was drafted for production). The
tool saves no XSLT: `xslt_uuid` is one saved before (`save_xslt index_plan=...`).

For Lucene, the fields the XSLT writes that the index lacks are added from the plan kept with the XSLT, and are not
asked about. Stroom drops a value for a field the index lacks with only a warning ("Attempt to index unknown
field"), so the pipeline would run and index nothing searchable; an XSLT written by hand (no kept plan) that writes
such fields (its `<data name="...">`) is refused instead.

An indexing pipeline reads Events: an indexing template needs the build's own events pipeline, or
`events_stream_ids` that are Events. A pipeline whose XSLT keeps a discovery plan reads raw data, so this isn't
checked: an XML discovery pipeline has an indexing template's shape (XMLParser, XSLT, indexing filter), and the kept
plan tells them apart.

`standalone='discovery'`, in place of `template_uuid`, is the last resort: a discovery pipeline of its own into
Elasticsearch, only when this Stroom has no discovery template at all. Its parser follows the discovery plan kept
with the XSLT (`json` JSONParser with `addRootObject` false, `delimited` a DSParser on the build's Data Splitter,
`xml` XMLParser), then a RecordCountFilter, a SplitFilter of one record a split (so `EventId` finds exactly one
record again), the XSLT, a SchemaFilter on the JSON group, and the ElasticIndexingFilter with the index and cluster.
It is made through `CreatePipelineTool.withoutTemplate`, confirmed by the user, and the result says it inherits
nothing, so a change to the environment's templates never reaches it.

* **Arguments**: `build`, `name`, `xslt_uuid` (required); `template_uuid` (indexing template, or a discovery one);
  `index_uuid` (Lucene Index doc, or the Elastic Index doc, which names the index and cluster); `index_name` and
  `cluster_uuid` (Elasticsearch); `events_stream_ids` (needed when the build has no events pipeline);
  `standalone` (`discovery` only); `text_converter_uuid` (standalone delimited, when the build has several Data
  Splitters).
* **Returns**: `create_pipeline`'s result (the pipeline made, with the build's `next`), plus `backend`, and
  `index_fields_added` for Lucene when fields were added.
* **Refuses**: neither `template_uuid` nor `standalone` (`Give template_uuid: an indexing template
  (find_pipeline_templates stage=indexing), or a discovery one for raw data indexed as it is (stage=discovery)`:
  the fallback isn't named, so an agent isn't steered to it); both given; a template of another stage; no events
  pipeline and no `events_stream_ids` (with how to get Events, or a discovery template for raw data), or streams
  that aren't Events; Lucene without `index_uuid`, or a template that sets its own Lucene index; a hand-written XSLT
  writing fields the index lacks; an XSLT or index doc that can't be read (`XSLT <uuid> can't be read: it doesn't
  exist, or you don't have permission to view it`); a kept plan that doesn't validate; Elasticsearch with no index
  name; an `index_name` the Elastic Index doc doesn't write to (leave it out, or `rename_index` first: seen in
  testing, the doc named as the user said, the pipeline the plan's name, and the template made for an index Stroom
  doesn't search); a template leaving the cluster open with no cluster; `index_uuid` that isn't an Elastic Index
  doc for an Elasticsearch template; `standalone` while any discovery template exists, or with no discovery plan
  kept with the XSLT; a delimited standalone with no Data Splitter in the build, or several and none named.
* **Notes**: not confirmed as a whole over `/mcp` or on an approval card: the pipeline is confirmed in
  `create_pipeline`'s form and every change stays in the build (`changesOnlyItsBuild`; `IndexFields` refuses any
  index but the build's own). The build is remembered for the session. A Lucene index's fields are read through
  Stroom's index service.

### `rename_index`

Indexing · write · checks View and Edit; the user needs Edit on the build's Elastic Index doc and each pipeline
changed · confirms itself · Classes: `RenameIndexTool`, `BuildIndexes`, `IndexingRuns.dropRequest`, `KeptMapping`

Another Elasticsearch index name wherever the build uses one, when the user asks: the build's Elastic Index doc and
each of the build's indexing pipelines writing to the old name, together, once the user confirms in a form (asked
for by the user: a name changed reached only some of the places it was used). The template agreed for the old name
doesn't apply to the new one, so the reply says to `propose_index_template` again. Nothing is written to
Elasticsearch: the reply lists what the old name left on the cluster for the admin, the template the tools agreed
for it (`DELETE _index_template/<name>`, not one the user brought) and, where a pipeline already had processor
filters, the index or data stream it wrote to (`DELETE _data_stream/<name>` or `DELETE <name>`, as the agreed
template made it), each also as a button.

* **Arguments**: `index_uuid` (the build's Elastic Index doc), `index_name` (the new name); both required.
* **Returns**: `type`, `uuid`, `name`, `index name`, `was`, `pipelines`, `for_the_cluster_admin`, user actions
  (`remove-old-N` buttons), `hint`, and the build's `next`. The same name again changes nothing and says so.
* **Refuses**: a uuid that isn't an Elastic Index doc (a Lucene index is named by its doc: nothing to rename); a doc
  outside a build (`WriteGuard.checkInBuild`); a name Elasticsearch won't take (`lowercase letters, digits, '-',
  '_' and '.', not starting with '-', '_' or '+'; no wildcard, space or comma`); a pipeline whose index name is
  built from values (`{...}`), as another name would lose them.

### `propose_index_template`

Indexing · write (keeps the agreed template in the pipeline's description) · requires Stepping; checks View and
Edit (the build's own pipeline only) · confirms itself · takes a run slot · Classes: `ProposeIndexTemplateTool`,
`TemplatePipelines`, `TemplateReviews`, `IndexExamples`, `ExampleTemplates`, `TemplateCheck`, `TemplateMappings`,
`IndexTemplates`, `PlanTemplates`

Builds the final Elasticsearch index template (not a Stroom pipeline template) for the indexing pipeline's own
destination index, for the cluster admin to apply. It follows the user's example index template or mapping and the
component templates it is composed of (`ExampleTemplates.fromExample`): their settings and `composed_of`, the new
index's own pattern, fields they map left as they map them, new fields mapped in their style for the type (keyword
`ignore_above`, text sub-fields, date formats), and names unlike theirs reported to rename in the plan. Given no
example (or text that isn't a template), it follows the one kept in the build when the plan was drafted. With
neither, nothing is built: the reply (`agreed: false`, `needs: example_template`) says to ask the user to paste one
and end the turn, as a template handed out unagreed would be applied without the user's conventions (seen: a
plan-only template committed to the cluster unagreed, and indexing still refused). Only `without_example=true`,
when the user has said they have none, builds it from the plan alone.

The template is then checked against the documents the pipeline writes: up to 50 records stepped as the user, the
JSON XML its XSLT sends to the ElasticIndexingFilter (`TemplatePipelines.documents`, audited as viewed), within the
session's time limit for a long call. A template that doesn't fit (`self_check`) is shown, not put for
confirmation. One that fits goes to the agreement (`TemplateReviews.agree`): first a `needs_review` round whose
`dev_tools` the agent shows in the chat, then, called again with `reviewed=<review_id>` in the same turn, a short
form where the user confirms it (seen: an agent ended its turn after showing it, and the user committed the
template before it was agreed). Agreed, it is kept with the pipeline (`KeptMapping.withAgreedTemplate`) with its
index, cluster, component names, the digest of the indexing XSLT, the time and the request; the reply carries a
`commit-template` button. `create_processor_filter` refuses an Elasticsearch pipeline without an agreed template
for its current index and code.

* **Arguments**: `pipeline_uuid` (required); `events_stream_ids` (required); `plan` (omitted: the one kept with the
  pipeline's XSLT, else the one drafted in the session); `example_template`; `component_templates` (kept for the
  session once given); `without_components`; `include_components` (components to compose besides the example's,
  whatever they map; `ecs@mappings` is known); `template_name` (default from the index name); `priority` (only when
  the user asks; none by default); `without_example`; `reviewed` (the review id shown).
* **Returns**: `template_name`, `index`, `cluster`, `template`, `dev_tools`, `self_check`, `from_example` (or
  `notes` from the plan alone), `data_stream` (discovery), `pipeline_link`, `stopped_early` and `stepped` when the
  time limit cut stepping short; then `needs_review` (`dev_tools`, `review_id`, with the key parts of the result),
  or `agreed: true` with the user action. `needs_components` while the example's components aren't given.
* **Refuses**: no `pipeline_uuid` or streams; no plan to go by; a Lucene plan (`Index templates are for
  Elasticsearch; Lucene fields are set with create_index_doc`); a pipeline that isn't an Elasticsearch indexing
  pipeline with an `indexName` (an example given earlier is for `draft_index_mapping`); an index other than the one
  the build's Elastic Index doc names (`rename_index` sets both); a template name that is the example's own or a
  component's, as committed it would replace the sibling source's (seen in testing); `priority` below 0 or not a
  whole number; streams that aren't Events, for a pipeline that isn't a discovery one; no Stepping permission;
  stepping that gives no documents; an `ecs@` component Elasticsearch doesn't have; a cancelled call
  (`Stepping was stopped: the call was cancelled.`).
* **Notes**:
  * The template's name and pattern follow the pipeline's index name (`IndexingHelpers.templateTarget`): values the
    name is built from (`{_suffix}`) are wildcards in the pattern and left out of the name (seen: a literal pattern
    `ecs-windows{_suffix}v1*` matched no index).
  * An ECS plan's template composes `ecs@mappings` and leaves the standard ECS fields to it, with dynamic mapping
    on. The plan's convention is taken from the kept or drafted plan when the agent's copy left it out
    (`IndexPlans.withConvention`), as it decides that.
  * From the plan alone, the template makes a data stream; a discovery index is one when every sampled record has
    its time.
  * The user's standing instructions (AGENTS docs' Component templates) include or exclude components; the user's
    own nomination wins over an exclusion.
  * A template already agreed for the same index, cluster, components and XSLT digest, its body equal as JSON with
    sorted keys, stays agreed without another review (seen in Ask Stroom AI: a committed template checked again,
    and the user asked to confirm it a second time).
  * The confirmation form is a few lines (name, patterns, priority, data stream, components, fields mapped, what
    unmapped fields do), bound to what was shown by its review id: with everything in it, a form ran to 2,000
    characters and the client declined it.
  * Cut short by the time limit, the check and any agreement go ahead on the documents stepped, and `stepped` says
    so.

### `check_index_template`

Indexing · write (keeps an agreed template in the pipeline's description) · requires Stepping; checks View and Edit
(the build's own pipeline only) · confirms itself · takes a run slot · Classes: `CheckIndexTemplateTool`,
`TemplatePipelines`, `TemplateReviews`, `TemplateCheck`, `TemplateMappings`, `ExampleTemplates.ecsDuplicates`

Checks a user's Elasticsearch index template (or an existing index's mapping), with its component templates composed
as Elasticsearch composes them, against the documents the candidate indexing pipeline writes
(`TemplateCheck.compare`): does it apply to the pipeline's index, and can it take every field written (types, date
formats, IPs, the `dynamic` setting, value/object clashes, `@timestamp` for data streams, fields renamed or dropped).
Each mismatch comes back as a change to make, mostly to the indexing XSLT, to put to the user before anything is
changed. A compatible index template (one with `index_patterns`) is agreed as `propose_index_template`'s is: shown,
then confirmed, then kept. A compatible mapping is not agreed: the hint says to give it to `propose_index_template`
as the example.

* **Arguments**: `pipeline_uuid` (required); `template` (Dev Tools request, JSON body, or GET output; left out with
  `reviewed`, the template that review showed, kept for the session so it is never retyped); `events_stream_ids`
  (required); `component_templates`; `without_components`; `max_records` (1 to 500, default 50); `reviewed`.
* **Returns**: `compatible`, `blocking`, `pipeline_changes`, `notes` (standing instructions' components missing or
  excluded, components not given and so unchecked, ECS fields mapped twice), `component_templates`,
  `template_name`, `index`, `cluster`, `pipeline_link`, `stopped_early`/`stepped`; then `needs_review` or `agreed:
  true` with the `commit-template` button, as for `propose_index_template`.
* **Refuses**: no `pipeline_uuid`, streams or template; a template that can't be read; `max_records` out of range;
  an index other than the build's doc names; the kept example's own name; the same input, stepping and cancellation
  refusals as `propose_index_template`.
* **Notes**: a template composing `ecs@mappings` that also maps the same ECS fields explicitly is noted as mapping
  them twice (up to 30 named; seen in testing: asked to use `ecs@mappings`, the agent composed it and still mapped
  every ECS field).

### `verify_index`

Indexing · write (makes or changes the build's verification dashboard; records the build's pipelines as verified) ·
unlocked by Stepping (tracing hits); checks View, Use (searching the index) and Edit; Create in the build folder ·
confirms itself · takes a run slot · Classes: `VerifyIndexTool`, `IndexChecks`, `IndexFields`, `IndexSearches`,
`MisleadingSearches`, `BuildRecords`

Verifies indexed events through Stroom, not by querying the backend. It makes a verification dashboard on the index
doc (`<index>-VERIFY` by default, role folder Indexing, `WriteGuard.createFilled`) once the user confirms its
columns: a query on the time field from the sample's earliest event (rounded back to a 30-day boundary) through the
end of today, run on open; a table of the user's fields, newest first, with `StreamId` and `EventId` as hidden
columns; and a text pane on the selected row's record, with stepping and no extraction pipeline. It is laid out as
Stroom's UI lays one out (sized panes, the config's own size, constraints, model version and time range): without
them the search API ran the dashboard but the UI showed it empty. An existing `-VERIFY` dashboard is laid out again
if needed, and its columns changed once the user confirms. The dashboard is the build's when the index is, or when
`pipeline_uuid` is the build's own pipeline writing to it (seen in production: a new version's verification left no
dashboard to promote); any other index is searched through an unsaved dashboard.

Then the test searches (`IndexChecks.runTestSearches`), each of the sample's streams only and unsorted, as the
index may hold millions of other documents (a check of the whole index answered for them, and sorting made Stroom
read every match): every document of the streams (the count must match `expected_documents`, retried every 5 s
while the index catches up, stopping early when two counts agree or Stroom couldn't finish counting); an exact
match per `exact` item; the time range; and any further `searches`. A stream of more than 10,000 events is checked
by its first and last 1,000 and none past its last (asked for by the user: a 100,000-record stream was counted
seven times over); a count Stroom hadn't finished in time is unproven, not a failure. Given `pipeline_uuid`, each
check's lowest-EventId hit is traced back to its record by stepping the pipeline at the hit's StreamId and EventId
(record index EventId - 1), every hit of a stream in one stepping session (each in its own captured the stream
again: verifying a long stream took over five minutes); the trace passes when the document there holds the value
searched and is the hit's, matching on the hit's other columns when stepping numbers the record otherwise (stepped
alone, every record was numbered 1). With `pipeline_uuid`, a field the pipeline's plan writes for these Events that
the index lacks fails the run.

Passing records the build's pipelines writing to the index as verified (`BuildRecords.VERIFIED`) and returns the
build's `next`. `checks=false`, when the user asks to skip verification, makes the dashboard and returns its link
with no searches; nothing is marked verified.

* **Arguments**: `build`, `index_uuid`, `backend`, `stream_ids`, `expected_documents`, `fields` (the table's
  columns, as the index names them) (required); `exact` (`{field, value}` each); `time_range` (`{field, from, to,
  expected}`); `dashboard_name`; `retries` (0 to 20, default 6); `searches` (`{field, condition, value, expected}`,
  conditions as a dashboard's, default EQUALS); `pipeline_uuid`; `checks` (default true).
* **Returns**: `dashboard` (`uuid`, `name`, `saved`, `link` when saved, and the design), `passed`, `checks` (each
  with `check`, `pass`, `returned` or `parts`, `errors`, `sample`, `trace`, or `checked: earlier in this session`),
  `unfinished` and `next` when time ran out, `verified` (pipelines recorded); or `checks_skipped`.
* **Refuses**: missing arguments; `backend` other than the two; `expected_documents` or `retries` not a whole number
  in range; malformed `exact` or `time_range`; a search with no field or a condition dashboards don't take; an
  index doc that can't be read; searches that would mislead (`MisleadingSearches`: on Elasticsearch, Stroom finds
  nothing for STARTS_WITH and CONTAINS, nor a wildcard on an ip field, nor a field it doesn't list, and IS_NULL /
  IS_NOT_NULL match every document, so those are searched as EQUALS / NOT_EQUALS `*`; on Lucene, STARTS_WITH,
  ENDS_WITH, CONTAINS on a keyword and a range on a text field find nothing); a field no search finds (not indexed,
  not queryable, or not indexed by the agreed template); a column Stroom doesn't list for the index, with the field
  an Events path became (seen in Ask Stroom AI: `EventSource.User.Id` chosen in place of `user.name`, verification
  passing with the column empty); `checks=false` for an index that isn't the build's; a cancelled call
  (`Verification was stopped: the call was cancelled.`).
* **Notes**: it runs within the session's time limit for a long call: checks not reached are listed as
  `unfinished`, and `next` says to call again with the same arguments, as the checks that passed are kept for the
  session (an hour, keyed by index, streams, expected count and the indexing XSLT's digest), so only the rest run.
  Without the Stepping permission each traced check fails with its reason. An exact value showing several values
  (`retry, timeout`) that finds nothing is explained, as the index holds each on its own.

## The build layer

Every tool that builds something stands on a shared layer in `stroom.mcp.impl.tools`, bound in `BuildToolsModule`.
A write tool makes documents with `WriteGuard`, remembers what it learned in `BuildRecords`, asks the user through
`Consent`, and ends its result with `BuildPlan.withNext` (or `nextFor`).

**`WriteGuard`**: everything the tools make lives in the workspace folder (`stroom.ai.builds.workspaceFolder`, default
`MCP Workspace`, below System), in a folder per build, with role subfolders (`Events` or `Records`, `Indexing`, `CEF`,
`Reference`; feeds, the build record and notes stay at the build's top). Documents carry the tags `mcp-managed` (what
the agent may change; removed at promotion) and `mcp-generated` (everything the tools made, for good); an XSLT saved
with its mapping also carries `mcp-kept-mapping`.

* `create(type, name, build, copyOf, ..., role)` makes an empty tagged document; `createFilled(...)` makes and fills
  one, deleting it again if filling fails. A name the build already has for the type is refused, naming the one there
  (seen: after a long conversation was summarised, an agent made the same indexing pipeline twice).
* Before changing a document: `checkManaged` (it must be tagged `mcp-managed`, else "Make a working copy in a build
  (copy_pipeline)"), `checkInBuild` (and in a build folder, which it returns: index docs and pipelines change only
  there, whatever their tags, as a doc moved out in Stroom keeps them) or `checkBuilt` (generated, promoted or not).
  Each refusal is a `ToolRefusal` and is audited (`access_denied`).
* `createFolder` looks for a folder of the name first and uses it (Stroom allows twins under one parent), under a lock
  per parent and name. `resolveFolder(path)` gives the deepest existing folder and the names still to make, creating
  nothing. `folderParts` takes `System / Feeds`, `System/Feeds/`, `/Feeds` and `Feeds` alike (seen: paths refused for
  want of `System`). `buildOf`, `copyOf`, `folderContents`, `roleFolder`, `moveToRole`, `moveTo`, `copyTo`, `delete`,
  `tag`, `untag` and `removeBuildFolderIfEmpty` (counting every child, whoever may view it, as Stroom deletes a folder
  with everything in it) complete it.
* Everything is done through Stroom's explorer and document services as the user, so Stroom's own checks (Create,
  Edit, Delete) apply as well.

**`BuildRecords`**: the build's record, the Documentation doc `Build record` in its folder: a readable table, then
fenced JSON (```` ```json build-record ````). It is `{"docs": {uuid: entry}}` with build-level `source_feeds`,
`event_logging_version` (`version`, `how`) and `kind` (`events`, `discovery`, `reference`). An entry may hold `type`,
`name`, `copy_of`, `version_of`, `copy_of_version` (a working copy's original's version when copied: `COPY_OF_VERSION`),
`stepped`, `verified`, `validated` (lists of code digests), `stepped_streams`, `sample_files` and `user_samples`.
`recordPassed(pipeline, kind, draftCode, extra)` records a clean step (`STEPPED`), a passed verification (`VERIFIED`) or
Events that passed `check_events` (`VALIDATED`), only for pipelines in a build. The digest is per element the pipeline
sets its own code for: the draft stepped, else the saved XSLT or converter, never the version history, so a draft
stepped clean is still clean once saved: each element's code as SHA-256, then the first 16 hex characters of the SHA-256
of those as JSON with sorted keys (`fingerprintDigest`). The digest and the record's format stay as they are, so records
already in Stroom read and are written back unchanged, and a clean step recorded earlier still matches its code
(`TestBuildRecords` holds a record in that format). The record is read from the doc's body, else its Documentation tab,
where earlier versions wrote it (`bodyText`). `steppedClean`, `verified` and `validated` say whether the saved code has
passed. Earlier versions kept these as explorer tags (`mcp-build-<build>`, `mcp-copy-of-<uuid>`, `mcp-stepped-<digest>`
...), which grew Stroom's tag list with every build and change; a build's old tags are folded into its record the first
time it is read, then removed. Stroom refuses a save of a doc changed since it was read, so two writers lose nothing:
the change is made again on the newer doc.

**`Consent`**: before a change, `consent.require(call, context, Request.confirmation(action, summary, details))` (or
`Request.approval(...)`, with `.withEditable(key, title, proposed)` for a value the user may correct in the form, and
`.withDocuments(...)` for links). It returns once the user agrees; read a correction with `consent.edited(context, key,
proposed)`, and `consent.changes(context)` gives every changed value (which `BuildPlan.withNext` reports as
`changed_by_user`). The user is always asked directly in a form, so the model never holds the answer: in the chat the
run stops while the form waits and the same call is made again with the answer (a tool must be safe to re-run up to
its question); over `/mcp` the call waits on the client's elicitation. No pending id is ever handed to the model to
pass back, as a model holding one could approve its own call. A form closed or run out unanswered is "no answer yet,
call again when the user is back", not a refusal (asked for by the user: an agent took a cancel for "did not agree").
`consent.choose(...)` and `chooseEach(...)` ask the user to pick options. Forms are kept short: the message is cut to
600 characters (each detail to 160) with the rest left to the chat, because a 2,000-character template confirmation
was declined by the client as too long. A choice form too large to show at all is not shown: `choose` returns empty
and the agent asks in the chat (seen: a stand-ins form with a question for each of about 150
values, and a hand-edit question naming every field of a wide mapping). `Consent.reviewId(shown)` and `wasReviewed`
bind a `needs_review` reply to what it showed (promotion layouts, index templates, records kept as Unknown). Every
ask and answer is audited.

**`FormsRequired`**: what a call that changes something is told when the user can't be asked in a form: an MCP client
that didn't say it can show forms (over `/mcp` it can't make changes at all; its read-only calls still work), or a chat
the user is away from (nothing needing agreement is done unattended).

**`BuildPlan`** and `BuildMemory`: the onboarding plan with its state read from the build.

* The checklist's stages: `1 events` (feed, samples, converter, translation, pipeline, stepped, covered, processed,
  validated, documented), `1 reference` (reference_pipeline, reference_stepped, reference_processed,
  reference_documented, between the converter and the translation, as a lookup made before its Reference stream exists
  is remembered for 10 minutes), `2 indexing` (index, indexing_pipeline, index_template, indexed, index_documented),
  `3 output` (cef_mapping, cef_pipeline, cef_stepped, cef_documented) and `finish` (promoted). `status` marks each step
  `done`, `to do` or `not needed` from what the build holds (`BuildFacts`, read by `StroomBuildFacts`): its kind,
  whether it works from existing data (source feeds or working copies: then no feed to create or samples to upload),
  a discovery, reference-only or CEF build, its survey, its sample's format (from the newest raw sample's head, else
  the survey; a converter isn't needed once the events pipeline reads the data with a JSON or XML parser).
  `plannedSteps(build)` leaves out what the build's kind doesn't need; `next.progress` doesn't count those.
* A build on existing data does the events stage once it has the work for it: a translation pipeline, a survey, or a
  translation XSLT saved from its mapping. So with such an XSLT saved, with or without a survey first, the
  translation step is `done` and `next` is the pipeline (seen in an end-to-end run: the XSLT was saved, the plan still
  said to choose the work, `stream_meta`, and never reached the pipeline).
* `next` is the first step to do and **one** call that does it (`nextCall`), with the arguments the build already
  gives and `<...>` placeholders for the rest, plus `then`. One call rather than a list, because a small model given
  several options deliberated until its context ran out. The call depends on how processing stands (a filter disabled
  or running, an indexing pipeline not yet stepped, a hand-edited XSLT, the next version of a feed already translated,
  survey locations, reference feeds not yet loaded).
* `withNext(context, build, result)` adds `next` (with `if_missing`, telling a client that hides tools behind
  `activate_*` groups how to find it: seen, 360 calls to made-up `activate_*` names), `done: false` until promotion,
  and `changed_by_user` with a note; a confirmation or review reply (a result with `status`) passes through untouched.
  `nextFor(context, ref, result)` does the same for the build a document is in, when the call named none (seen: such
  results carried no `next`, and the agent lost its place). The plan is advice: it never fails a write.
* `onboardingLabel(tool)` starts each step tool's description (`Onboarding step 5 of 15 (pipeline): `), so a client
  picking tools by similarity pulls the whole chain for an "onboard these logs" request. `TASK_LIST` asks the agent to
  keep the plan in its task list and go on from stage to stage (seen: no task list, and a stop to report after each
  stage); `HIDDEN_TOOLS` explains tools a client may hide.
* `resolveBuild(context, build, tool)` gives the build a call names, else the one the session last worked on
  (`BuildMemory`: per session, a chat or an MCP session, in this node's memory, up to 10,000 sessions); a call it can't
  help is told to name the build.

**`CallProgress`** (`stroom-ai-impl`): `CallProgress.of(context).say(message)` for each stage of a long call, and
`nowAndThen(message)` in its loops (at most every 5 seconds). There is one for the whole call, so `nowAndThen`
counts from whatever was said last in it. Only `/mcp` sends the messages, to a client that asked for progress; it
never fails the call. Asked for by the user: a survey of an index ran for minutes with nothing to show what it was
doing.

**Over `/mcp`** (`McpTools`, `McpApprovals`): a call that changes something is refused at once for a client that
can't show forms. It is approved as a whole in a yes/no form (the arguments that say what it acts on first, then all of
them, cut short) unless the tool confirms itself (`confirmsItself`: its own form is the approval) or changes only its
build (`changesOnlyItsBuild`: nothing outside the build changes until `promote_build`, which asks). The same rule
decides whether a chat call needs an approval card after the chat has read untrusted content
(`BuiltInTools.changeNeedsApprovalAfterUntrustedContent`). A tool with `usesRunSlot` takes one of the user's run slots.

**`ToolRefusal`** (`stroom-ai-impl`) and **`JsonResults`**: a tool, or a helper it calls, throws `ToolRefusal` with
wording the agent can act on; `JsonResults.refused` gives it as an error result. `JsonResults.of` gives a result map as
JSON text (null values left out) and as structured content. A result's `user_actions` (`UserActionEntries`: open a
document, enable or disable a filter, copy an Elasticsearch request of the few shapes allowed) are given to the chat as
`ToolUserAction` buttons, checked before they are shown.

## Sample analysis (`stroom.ai.impl.tools.sample`)

Plain Java in `stroom-ai-impl` that reads raw samples, for `start_onboarding`, `profile_sample`, `survey_feed`,
`set_shape_handling`, `build_status` (the sample's format) and the drafting tools. Bound for the build tools through
`SampleProfiling` (`stroom-mcp-impl`, implemented by `SampleProfiling.Profiled`), which adds the environment's XML
fragment wrapper (`FragmentWrappers`).

* **`SampleProfiler`**: a sample's profile: `characters`, `lines`, `format` (`xml`, `xml fragments`, `json array`,
  `json object`, `json lines`, `delimited`, `cef`, `syslog rfc5424`, `syslog rfc3164`, `cri`, `multi-line text`,
  `key=value` or `unknown text`), `records`, `fields` (each `field`, `fill_rate`, `type`, `examples`; types include
  `ip`, `integer`, `decimal`, `embedded json` and `embedded json after prefix`), `suggested_parser`, and what the format
  needs (`text_converter`, `parser_properties`, `xslt_input`, `delimiter`, `has_header`, `columns`, `malformed_lines`,
  `note`). Up to 200 records a sample are profiled. Lines that are not records are named from up to 5,000 lines (a JSON
  line cut short, a CSV row short of columns or with an unclosed quote, a key=value line with no pairs); a file mostly
  of JSON lines is still JSON lines, read through a Data Splitter with `json_fields`, as Stroom's JSONParser stops at
  the first bad line. Kubernetes CRI logs get `record_end`. Several files are profiled together with `files` and
  `differences`. Text that starts as JSON and doesn't parse is refused with where and why.
* **`TimePatterns`**: a `stroom:format-date` pattern inferred from a value, read left to right into pattern letters,
  and any pattern (inferred or the model's) turned back into a regex and tried on the sample's values before the XSLT
  is stepped, because a wrong pattern makes `stroom:format-date` fail silently and leaves TimeCreated empty.
* **`RecordSplitter`**: splits the start of a stream part into records with the profiler's detection, each with its
  structured view (a JSON record's flattened fields, an XML record's leaf texts and attributes, a delimited row's
  columns, a key=value line's pairs). A text cut off at the end loses only its cut-off last record: JSON arrays and XML
  are read incrementally.
* **`Shapes`**: groups records into kinds of event. JSON, XML and key=value are signed by their fields plus the values
  of fields that usually name the event (action, event, type, category, logger...); a message-like field (body,
  message, msg) is unwrapped (JSON inside it after any prefix or byte order mark, naming `key=value` and `Key: [Value]`
  pairs), and a free-text field by another name (a Windows event's `Data`) is read for naming pairs, so a wrapper such
  as syslog shipped as JSON doesn't hide the kinds. Delimited rows by their naming (or low-variety) columns; CEF by the
  header's vendor, product, signature id and name; syslog and other text by the message with variable parts masked,
  merged with messages differing in a few words.
* **`SurveyDoc`** and **`FeedSampling`**: the survey doc (counts, shares, streams, examples with locations, then a
  fenced JSON state block: `feed`, `format`, `time_range`, `streams_read`, `saturated`, `surveys`, `shapes`,
  `handling`) and the sampling helpers (`pickSpread`, `keep`, `hint`, `charsetEntry`, and the limits `SURVEY_STREAMS`
  10, `FEED_CHARS` 250,000, `SAMPLE_CHARS` 2,000,000, `FEED_RECORDS` 1,000, `SAMPLE_RECORDS` 10,000). Example records
  are stored as they are; who can read them is down to the build folder's permissions, as for the feed's data. The
  survey doc's format, state block included (JSON indented by one space), is stored data: a later survey carries on
  from it, so the format changes only in ways a survey doc already kept still reads.
* **`SampleTexts`**: what counts as sample data. A sample that is a file path, a list of paths, an attachment
  reference or a file a reader cut short (e.g. `[... truncated at 2000 characters]`) is refused with what to do
  instead, because a model that doesn't read attachments passes the path on. `samples` also takes the shapes small
  models send (a map by file name, a list of texts or `{name: text}` objects, one text, or one file's text cut into an
  object's keys and values), saying plainly what arrived broken, so a client's schema check doesn't reject them with a
  bare "must be string".
* **`SplitterSpec`**, **`SplitterDryRun`** and **`StroomSplitterRun`**: how a record divides into named fields
  (delimited, regex, key_value, syslog, cef, fields), run on the sample as the generated Data Splitter would, with
  Java regexes as Data Splitter runs them; and the same converter run by Stroom's own DSParser, as the dry run differs
  from Stroom where it hasn't followed it (seen: records over the read buffer lost in processing, cells Stroom trims).
* **`DelimitedText`**, **`ToolJson`**, **`XmlTree`**: delimited text (cells split as the generated Data Splitter splits them: a quote
  opens a quoted stretch wherever it is, a doubled quote stays doubled; a sniffer guesses the delimiter and header; no
  field length limit),
  JSON (which texts parse, error messages with positions, writing back with non-ASCII escaped) and XML (StAX, no DTDs or
  external entities; a document, or the complete elements before a cut-off end). `ToolJson`'s messages for text that
  isn't JSON (`Expecting value: line 1 column 1 (char 0)`, `Invalid \escape`) are what agents are shown (a profile's
  malformed lines, a tool argument that doesn't parse), and tests pin them. Its written forms are fixed too: the
  compact form (`dumpsCompact`, no spaces after `,` and `:`, characters past ASCII as they are) is the JSON kept in a
  document's description, and the sorted-keys form (`dumpsSortedKeys`) is the text of a build record's JSON and of
  every JSON digest (build record code digests, the mapping digest of a documented Field mapping, consent
  `review_id`s, index template reviews). A digest kept in a build record is compared with one made later, so a change
  to that form would make clean steps and verifications look stale.
* **`Placeholders`**: stand-ins for none (`-`, `N/A` and the like) in sample or Events values: a value that recurs in a
  field (two records or more) and is one of the usual stand-ins, or a short value of another kind in a field nearly all
  addresses, numbers, times or booleans. Seen: W3C logs' `-` for a missing address went into an Elasticsearch ip field,
  which refused every document holding it. Used by the translation draft's local check and by `draft_index_mapping`
  (its stand-in questions, through `PlaceholderChoices`), not by the tools above.

Raw stream text is read through the stream store (`StreamReader.readHead`, `readWindows`, `cachedRawText`), decoded as
a pipeline decodes it: undecodable bytes are replaced and `StreamReader.charsetReport` says what was replaced, which
the tools pass on (`undecodable_bytes`, with advice).

## The validation core (`stroom.ai.impl.tools.validation`)

The checks `check_xslt`, `check_events`, the translation build and the stepping tools are made of, in
`stroom-ai-impl`. They read only what the user may view, and run nothing outside Stroom.

* **`XsltChecks`**: the checks of an XSLT that need no Stroom: XML sent HTML-escaped (`unescaped`), `stroom:`
  functions that don't exist, element names that select nothing for want of the input's namespace, event-logging
  elements the schema has no place for, and `json-to-xml()` unguarded.
* **`XsltCompileCheck`**: compiles an XSLT as Stroom's XSLT pool compiles one for an XSLT filter: its own Saxon
  processor with Stroom's XSLT function library registered (`stroom:lookup()`, `stroom:format-date()` and the rest)
  and `xsl:import` / `xsl:include` resolved by name against Stroom's XSLT documents. Nothing is stood in for, so an
  import that can't be read is the error, as it would be when the pipeline runs. An XSLT saved with "Variable kv has
  not been declared", which only stepping found, is why it exists. Compiling runs an XSLT's static expressions
  (`use-when`, static parameters), so it refuses what `XsltSafety.unsafeInXslt` refuses before compiling, whatever
  its caller checked, and compiles with a processor locked down as `LockedSaxon`'s (`LockedSaxon.newLocked`).
  Compile errors are given as Saxon's code and message; one saying a feature needs Saxon-PE is explained in Stroom's
  terms (`StroomXPath.explained`).
* **`StroomXPath`**: what Stroom's engine can't run. Stroom runs Saxon-HE 9.9: XPath 3.1 and XSLT 3.0 (`let`, `!`,
  `=>`, maps and arrays, `xsl:iterate`, `xsl:for-each-group`), but no higher-order functions. Inline functions,
  function references (`upper-case#1`), functions that take a function (`fold-left`, `for-each`, `filter`,
  `for-each-pair`, `apply`, `map:for-each` ...) and string constructors fail to compile there. Each was checked
  against Stroom's own Saxon. `StroomXPath.unsupported` finds them in a mapping's xpaths, so the translation build
  reports them as a problem, with what to use instead, rather than generating an XSLT that doesn't compile (a
  `fold-left` with an inline function was once checked as valid XPath and then refused by Stroom).
* **`HeldSchemas`**: the XMLSchema documents this Stroom holds that the user may view, found by system id (e.g.
  `file://event-logging-v4.1.0.xsd`) and compiled and cached by Stroom's schema pool, so validation is against
  exactly what the instance has installed. It gives the event-logging versions held (newest first), resolves the
  version for a check (given, declared, else newest), keeps the 8 most recently used schema models
  (`MAX_EVENT_SCHEMAS`), and reports schema locations Stroom doesn't hold. Errors are capped at 50 (`MAX_ERRORS`).
* **`EventSchema`**: what the event-logging XSD allows below `Event` (element order, required elements, choices,
  leaf types, patterns, enumerations, descriptions), read once from the XSD's text. Used by the checks and by the
  translation generator (schema order, paths that don't exist with suggestions, constants against enumerations).
* **`EventChecks`** and **`SchemaErrors`**: `check_events`' two checks. `SchemaErrors` validates against a compiled
  schema, giving each error's line, a path such as `/Events/Event[2]/EventTime/TimeCreated` (a position where the
  parent holds more than one of that name) and the message, the validator's two messages for one bad value given as
  one error. **`EventStreams`** reads Events streams as one `<Events>` document for it.
* **`EventElements`**: what an event-logging element takes, from the schema in this Stroom, for describing it to the
  agent.
* **`XPathChecks`**: evaluates a mapping's xpaths and extraction regexes against the sample before anything is
  created or stepped. An xpath that selects nothing in any record (`json-to-xml(event)/timestamp` where the JSON
  parser gives `string[@key='event']`) generates valid XSLT that steps clean and writes empty elements. Each record
  is rebuilt as the pipeline's parser presents it (JSONParser map/string elements, Data Splitter `records:2`, or the
  XML record) and evaluated with `LockedSaxon`. Expressions needing Stroom (`stroom:` functions) or variables are not
  evaluated. An xpath without `repeat` that gives several values in a sample record is warned of, as the element is
  written once with the values joined (seen: an agent dropped `repeat: true` from a mentions entry, the mentions came
  out joined in one Data, and it took that for something the generator can't do and wrote the XSLT by hand).
* **`LockedSaxon`**: the locked-down Saxon processor used for XPath on samples. See the safety rules below.
* **`XmlTrees`**: reads XML text as namespace-aware DOM trees; a DOCTYPE is refused and nothing outside the text is
  loaded. `XmlTrees.XSL` is the one XSLT namespace constant.
* **`CloseMatches`**: "did you mean" suggestions (up to 3, similarity cutoff 0.6) for `stroom:` functions,
  event-logging elements and feed names. Scoring is gestalt pattern matching (twice the characters in matching
  blocks over the total length), as Python's `difflib` computes it, autojunk included, and is kept exactly: the
  suggestions the recorded cases hold depend on it.
* **`XsltVersionHistory`** and **`TranslationDescriptions`**: an XSLT's version history kept in its description, and
  a plain account of what a translation XSLT writes from what; used by the save and documentation tools. A place in
  a template with neither a match nor a name is described as `[None]`, the descriptions' established wording, which
  the tests pin.

## Safety rules for XSLT, regexes and streams

What the tools run is not their own: expressions come from the model, and documents and samples from whoever the
logs record. What they save runs on Stroom's nodes, with Stroom's access, for every record. These rules hold
whatever tool is calling.

* **Functions that read the environment, files or network are refused** (`XsltSafety.REFUSED`, in the XPath
  functions' and Saxon's namespaces): `doc()`, `document()`, `doc-available()`, `collection()`,
  `uri-collection()`, `unparsed-text*()`, `json-doc()`, `environment-variable()`,
  `available-environment-variables()`, `system-property()`, `available-system-properties()`, `function-lookup()`,
  `transform()`, `load-xquery-module()`. Why: Saxon as it comes reads whatever the process can, and looking a
  function up by a name made at run time gets round any list. A user-defined function of the same name (defined in
  the XSLT or an XSLT it imports by name, e.g. `geo:document()`) is the XSLT's business and is allowed. Calls are
  read as XPath reads them (`XsltSafety.called`), so a name in a string isn't a call and a comment between a name and
  its `(` doesn't hide one.
* **Instructions and imports that reach elsewhere are refused** in an XSLT: `xsl:evaluate`, `xsl:result-document`,
  `xsl:source-document`, `xsl:use-package` (`REFUSED_INSTRUCTIONS`), and `xsl:import`/`xsl:include` by URL. Why:
  they write files, read documents by URI, run XPath made at run time, or bring in code from outside Stroom.
* **Stroom functions that reach outside Stroom are refused** in an XSLT the tools save, check or step as a draft
  (`XsltSafety.STROOM_REFUSED`): `stroom:http-call()`, `stroom:fetch-json()` (both request a URL) and
  `stroom:ask-ai()` (sends its text to a chat model). Why: Stroom would make the request on its nodes, with a
  record's data, for every record. A mapping's xpaths aren't checked for these, as they are only ever saved as XSLT,
  where they are.
* **What a person put there stays** (`XsltSafety.keptBy`): a refusal is let through when the XSLT being replaced (a
  save) or the element's saved XSLT (a step) has the same function, instruction or import. A working copy may keep
  doing what its production XSLT already did. The outright refusals are made before anything is asked of the user.
* **DNS lookups need the user's agreement** (`XsltSafety.STROOM_ASKED`, `LookupAgreements`):
  `stroom:host-name()` and `stroom:host-address()` look a name up when the pipeline runs, and the lookup carries the
  looked-up value (a record's data) to whichever DNS server answers. They aren't refused (`unsafeInXslt` leaves them
  out, so they compile, and `check_xslt` warns, naming where each is called). A save of an XSLT calling one the XSLT
  it replaces doesn't (`XsltSafety.newLookups`; saves through `TranslationDocs.createXslt` / `updateXslt`, which
  covers `save_xslt`, `build_translation_xslt`, `rebuild_mapping`, `draft_cef_mapping` and indexing XSLTs from a
  plan) asks the user first in a form (`Consent.require`, an approval), after the other checks and before anything is
  written, naming each function, where it is called (`template match='record' (line 12)`, from
  `XsltSafety.lookupsInXslt`) and what it does. Declined: `XSLT not saved: the user declined stroom:host-name()
  (...)`. Stepping a draft asks the same way, as a step runs the XSLT on Stroom. Only a form counts: where the
  session can't show one the call is refused; no pending id is ever handed to the agent, which could otherwise
  approve its own call. Agreements are remembered for the session by document (the XSLT's UUID; a new one's build
  and name; for a step, the element's saved XSLT, else pipeline and element) and function: the same XSLT isn't asked
  about again, an agreement for a step covers saving the draft over the same XSLT, and a function not yet agreed is
  asked about on its own.
* **XPath Stroom's Saxon can't run is refused** (`StroomXPath`): higher-order functions, inline functions, function
  references and string constructors in a mapping's xpaths are problems when the XSLT is built, and a compile error
  saying Saxon-PE is needed is explained with what to use instead. Why: Stroom runs Saxon-HE 9.9, which has none of
  them, and a generated XSLT using one wouldn't compile.
* **The XPath processor reads nothing of the server's** (`LockedSaxon`): it refuses every URI (no file, network,
  collection or unparsed text: `Reading a resource is prohibited here`), sees no environment variable, has Saxon's
  external functions off (with them on, `system-property()` of a name in no namespace reads the JVM's system
  properties; Stroom's functions are integrated ones and still run), and parses documents with no external entity or
  DTD (an XML sample's external entity would pull a local file into the record). `XsltCompileCheck` compiles with a
  processor locked down the same way (`LockedSaxon.newLocked`), with only Stroom's functions and import resolver
  added. `LockedSaxon.processor()` is one shared instance, safe across threads.
* **Regexes the agent writes are bounded** (`SafePattern`, `DeadlineCharSequence`, in `stroom.ai.impl.workspace`): a
  splitter's pattern, record start and end, an extraction's regex and a condition's `matches` are compiled at most
  2,000 characters long (`SafePattern.MAX_REGEX_LENGTH`) and matched through a `DeadlineCharSequence` within 10 s
  (`SafePattern.REGEX_TIME_LIMIT`, or the call's own limit if sooner). Why: one that backtracks catastrophically reads
  the same characters for ever. The tool says `The pattern '...' took too long to match (more than 10 s) and was
  stopped: it backtracks too much. Make it simpler: ...`; `build_data_splitter`, `build_translation_xslt` and
  `draft_translation_mapping` give it as their refusal, `XPathChecks` as the extraction's problem. Saxon's regex
  engine copies the text before matching and checks no time limit, so each text is first matched by the same regex
  in Java (close to XPath's flavour) through the deadline, and `LockedSaxon` lowers Saxon's own backtracking limit to
  a million (`REGEX_BACKTRACKING_LIMIT`, Saxon's default being ten million) for what Java can't read and for
  `matches()` in an agent's xpath. Search patterns (literal unless a regex is asked for) are at most 200 characters
  (`MAX_PATTERN_LENGTH`).
* **Reading a stream's text needs View Data**, checked by `StreamReader` itself (raw text, its cache, and Error
  streams), whatever the caller checked. Why: Stroom's stream store checks only the feed. Without it,
  `create_processor_filter` and `reprocess_streams` don't read why Elasticsearch refused earlier runs, the build
  plan goes without the sample's format, and `step_sample` skips its JSON-array check.
* **Shared state is safe across threads**: `EventSchema` works out everything once, when made, into maps never
  changed after (a Xerces DOM, expanded lazily, isn't safe to read from several threads, and `HeldSchemas` shares one
  per version); `HeldSchemas` keeps the 8 most recently used schema models and `XPathTreeParser` the 64 most recently
  used parsers; `WriteGuard` makes folders under a fixed set of 64 locks (`FOLDER_LOCKS`); `HandEditGate` reads its
  answers from a copy taken under their lock; `StreamReader` doesn't cache a text longer than its whole cache.
* **Searches and stepping sessions are always ended**, even when the first request fails after Stroom started them:
  a search made for the agent alone is destroyed by its owner (`AgentSearches.destroyOwnedBy`), and Stroom's stepping
  service ends a session it made for a step that failed, whose id never reached the client. Why: an abandoned
  search or stepping capture holds resources until it times out.

## The draft mapping core (`stroom.ai.impl.tools.draft`)

The machinery draft_translation_mapping and build_translation_xslt use to draft a mapping and read a sample as the
pipeline would, in `stroom-ai-impl`:

* **`DraftMapping`**: `draft(samples, sourceName, systemName, environment, sourceNotes, detailCheck, splitter)`
  returns `mapping` (JSON maps and lists, for `TranslationMapping.parse`), `source_notes`, `splitter`, `format`,
  `records`, `kinds`, `notes`, `unmapped_fields` and `fields_seen`. `records` refuses an empty sample
  (`IllegalArgumentException`); `pairExtracts` and `withPairs` turn a text field's key/value pairs (found by the
  values' shape, never by names) into one extract entry a key, named in snake case. The field homes are the
  generator's `FieldHomes`. Each rule's Data carries only the remaining fields its own records hold (`held`; seen:
  every rule given the whole rest, 23 SecretServer rules each with the same 63 Data entries, kept by the agent beside
  the elements it then mapped them to). The notes say which field reads as each event's message, so it goes to the
  event's Description, or, if there is none, which free-text field may be it (`descriptionNote`; seen in eval cases
  22, 23 and 42, with neither said: the message, a CEF msg or a ticket's comment, went into a Document's Description
  or a Data entry).
* **`LocalCheck`**: reads the sample into records as the pipeline's parser would (`sampleRecords`: JSON keys with
  dots, Data Splitter names through the spec's dry run, XML paths by local name; a record is a map, or an
  `XmlTree.Element`) and checks a mapping against them: `checkMapping` (fields no record has, with the nearest
  names; time formats the values don't fit), `checkSchemaValues`, `unknownCoverage`, `domainQualifiedIds`,
  `placeholders` (values that stand for none: `Placeholders` finds values that recur in a field and are either a
  usual stand-in such as `-`, `N/A` or `null`, or in a field of addresses, numbers, times or booleans a short
  value not of that kind), `dataNotHeld`, `requiredLeftEmpty`, `traits`, `valueOf`. A condition's `matches` and an
  extraction's regex run with Java's regex engine, as Stroom's XSLT runs them. A JSON value that isn't text (a
  number) is read as its text. Every record of the sample is read, not the first 200 (seen in production: a VPN
  rule's keys first appeared at the 557th of 3,933 records, and the check said they matched nothing). It also
  reports:
  * a TypeId the same for events of different kinds (`sharedTypeIds`), as a problem, not a warning (seen as a
    warning: two runs left TypeId the category alone, 'User' for logons, logoffs and lockouts, the warning one among
    many);
  * required elements a rule writes only when their inputs have a value, whose inputs are empty in its records
    (`requiredLeftEmpty`; seen: the schema's error named the element after the one left out, read as Data in the
    wrong place, two rounds of stepping before the Command entry got a default);
  * Data entries reading fields none of the rule's records hold, as a warning (`dataNotHeld`; seen: a SecretServer
    launch rule's rpc_account, target and duration_ms, in none of its records);
  * fields mapped as they are into a user's Id whose sample values are domain-qualified, pointing to `strip_domain`
    (`domainQualifiedIds`; seen in eval cases 35, 36 and 43: the domain kept in every Id).

  Schema values (`checkSchemaValues`) are checked on what the mapping's extractions give as well as the record's own
  fields (seen: SecretServer's fields, read from its message's key/value pairs, were never checked). Where a field's
  values fit the schema's once lower- or upper-cased, it names the entry transform to give (`transform: lower` or
  `upper`) rather than only saying to map the values that fit.
* **`ActionRules`**: the action elements a sample's own values show, as rules (`actionRules`, `described`, `read`,
  `severityFields`, `networkFields`). An agent left with Unknown placeholders kept marking firewall traffic and
  administrator logons Unknown; for kinds plain from their values, the draft writes the rule, and a rule that keeps
  them Unknown is given the rules to use instead.
* **`SplitterXml`**: a `SplitterSpec` written as Data Splitter XML (`converter`, `generateSplitter`, `recordRegex`,
  `recordEndRegex`, `bufferSizeFor`, `withBufferSize`, `longestLine`, `EXAMPLES`). It writes the subset of the
  Data Splitter language the dry run (`SplitterDryRun`) follows. `SplitterSpec.fromMap` reads a spec's JSON.
* **`SourceNotes`**: the user's vendor documentation condensed into a field dictionary and an event catalogue,
  kept as a block in the notes doc: drafted from (`applyToDraft`), checked against (`checkMapping`, `merged`,
  `detailProblem`), and listed in the documentation.

The values the source notes' catalogue doesn't list (`not_in_catalogue`) are in the catalogue's field order, so the
result is the same from run to run.

## The translation generator, the reader and mapping changes (`stroom.ai.impl.tools.generation`)

The translation XSLT generator and what works with it, in `stroom-ai-impl`, used by build_translation_xslt,
draft_translation_mapping, save_xslt, rebuild_mapping, write_documentation and the build checks.

**The mapping** (`TranslationMapping`, `EventRule`, `FieldMapping`, `Condition`, `Lookup`, `Extraction`,
`KeyValuePairs`, `DropRule`, `XsltStyle`, `SharedTemplate`, `SharedFunctions`): plain records whose JSON is read and
written by hand (`ModelReader`), so the stored form is exactly the one kept in XSLT descriptions. `fromJson` /
`parse` validate with lax types, defaults and each model's own checks; every error is a `MappingError` (`loc`,
`type`, `msg`, `input`) in a `MappingValidationException`, which `MappingProblems` turns into the problems the agent
reads. Reading also normalises: `pairs` become extract entries, a rule's `data` list Data entries, a `meta` entry the
xpath reading it, Data Names the style's `data_names`. `toJson(DumpMode)` writes `ALL`, `WITHOUT_NONE` or `COMPACT`
(no nulls, no defaults: the form kept in a document's description), keys in the models' field order. Style numbers
(`variable_min_reads` and the like) are longs, and a number past that is refused.

A `MappingError`'s type and message come from a fixed vocabulary (`missing` / `Field required`, `string_type`,
`literal_error`, `value_error` / `Value error, ...`), located with dots in a message (`events.0.name`) and with
brackets in the problems the agent reads (`events[2].fields[0].path`); an exception's message starts with the count
and the model (`1 validation error for TranslationMapping`). The agent acts on this wording, and the model fixtures
(`model_cases.json`) pin it, so a change to it is a decision, not a fix. Reading is lax: a boolean may be `0`, `1` or
a word such as `yes` or `off`, an integer a whole number, or a string or float holding one, and unknown keys are
ignored. A field added to a model after mappings were first kept (`data_parent`, `join`) is written only when set,
whatever the dump mode, so a mapping that doesn't use it is written exactly as before and every kept mapping and
fixture still reads and compares unchanged; the reader likewise gives `data_parent` in a leaf's reading only for a
Data inside Data. `mapping-descriptions.json` (a resource of stroom-mcp-impl, beside `MappingProblems`) holds a
description of every key of the mapping's models (`TranslationMapping`, `EventRule`, `FieldMapping` and the rest),
from which `MappingProblems` says what a key left out is. Nothing generates it: a key added to a model, or changed,
needs its description added or changed there by hand.

**The generator** (`XsltGenerator.generate`, with `EventTrees` and `EventNode` for each rule's element tree and the
checks): an XSLT with the right input namespace, a template per record that picks the event kind, elements in
schema order, times converted with `stroom:format-date`, value maps, and guards so empty inputs leave elements out.
Each event kind is its own template (by default a template rule with its own mode; `style.layout` also takes
`named` or `inline`); parts several rules write alike are written once in a template of their own (the action
element above them allowed to differ), as is a run of Data entries (`style.data_run_min`); a conversion several
elements use is the XSLT's own `xsl:function` (`style.function_min_uses`, 2); a field read several times is a
variable of its non-blank values declared just before its first use (`style.variables`); value maps shared or long
are `xsl:map` variables read with `?`. Shared XSLTs are imported for their named templates (`shared`: called in the
element's place, with the parameters siblings pass; anything also mapped below that element is a problem, as it
would be written twice) and functions (`functions`: the prefix bound). It is built in a small XML tree (`XmlElement`,
`XmlComment`, `XmlInstruction`, `XmlNamespace`) and written by `XmlSerialiser`; `XmlParser` reads XML back.
`Generated` holds `ok`, `xslt`, `problems`, `warnings`, the events summary and `requiredOnInput`.

The XSLT text is stable to the byte, and the golden XSLTs pin it. The XML tree and its serialiser fix where namespace
declarations go and which prefixes they take (`xsl`, `xs`, `xsi`, else `ns0`, `ns1`, ... per document), attribute
order, what is escaped in attributes and text, and the two-space pretty printing; comments are wrapped at spaces,
never breaking a word or at a hyphen (`XsltComments`); the XSLT's own functions are written in the order of their
conversion's key (`Conversion.repr`). A change to any of these shows in every golden XSLT. The reference generator's
XSLT is held to the same by its fixtures.

What the generator checks (problems, per mapping entry, before any XSLT is returned): unknown paths with
suggestions, disallowed constants, alternatives of a choice used together, missing required elements, unquoted
pattern letters, an xpath reading `$name` for an extract's name (it wouldn't compile), a path from the document root
(`//x`: steps clean one record at a time but fails processing a whole stream), a refused function (`XsltSafety`: "the
tools don't run or save it"), and an xpath Stroom's Saxon can't run (`StroomXPath`). Stroom runs Saxon-HE 9.9: XPath
3.1 and XSLT 3.0, but no higher-order functions, so inline functions, function references (`upper-case#1`),
functions that take a function (`fold-left`, `for-each`, `filter`, `sort` with a key) and string constructors fail to
compile, Saxon saying they need Saxon-PE. This was checked case by case against Stroom's own Saxon (seen: `fold-left`
with an inline function passed as valid XPath and was refused by Stroom). `XsltCompileCheck` says the same in
Stroom's terms when the compile fails. An extraction's regex is compiled by `XPathRegex` with Saxon's own regex
engine, in XPath's dialect, as the generated XSLT's `analyze-string()` compiles it: one that doesn't compile is a
problem, with Saxon's reason, so `\p{L}+` is accepted and lookahead, named groups and inline flags are refused. Its
capture groups are counted from it (every `(` outside a character class that isn't escaped or `(?:`).
`TestXPathRegex` pins both; the reader counts groups the same way.

A rule no record can reach is a problem too: an earlier rule's conditions take every record it could (told from the
conditions alone, so a sample with none of a kind doesn't hide it; seen in testing: a draft's `user_logon`, for Login
or Login Failed, kept above the agent's own `user_login` and `user_login_failed`, which then never wrote an event,
and the documentation said "none in the sample" of them). A field a rule writes as it is both to an element and as
Data is a warning, as a copy may be kept on purpose (seen in testing: `item_id` as View/Object/Id and as Data in 13
SecretServer rules, the drafted Data kept beside the elements the agent mapped); a TypeId joining fields is left out
(`EventTrees.writtenTwice`, `checkReachable`).

What the generated XSLT does at run time that other tools rely on:

* **Unmatched and Unknown records are logged** with `stroom:log` into the Error stream (`UNMATCHED`, WARN;
  `KEPT_UNKNOWN`, INFO), with up to 4 of the values the rules test, so review_coverage can group a whole feed's
  missed records from its Error streams.
* **The record number is read for every record** (`RECORD_NO`, `READ_RECORD_NO`): a variable holding
  `stroom:record-no()`, declared first in the record template and read at once with a predicate Saxon can't settle
  without it (it selects nothing), so the split filter's location queue moves on per record. With `for_each`, the
  number is handed to the items' template as a tunnel parameter.
* **Rule markers for documentation**: `generate(..., marked=true)` writes a processing instruction
  (`RULE_INSTRUCTION`) in each Event naming its rule, never in saved XSLT. Stroom's stepping keeps processing
  instructions; it dropped the comments used before, so every event was left to a guess by its elements.

**Its helpers**: `XPathText` (the expression writers the reader reuses), `MappingFacts` (action homes, kept-Unknown
rules, rooted paths, variable reads, the input namespace), `MappingCompaction` (`compactRules`, `compactPairs`,
`grouped`), `XsltNames`, `XsltComments`, `Pairs`, `FieldHomes`, `StyledXslt` (the indexing and CEF XSLTs written in the
translation's style, with the same helpers, so the three can't drift apart), `HandEdits` (`undone`, `collisions`,
outline, plan fields) and `CodeDiff` (with `LineDiff`). The kept blocks are in `stroom.ai.impl.tools.KeptMapping`
(`withMapping`, `readMapping`, `replaceSection`, `docDigest`, `withBlock`): compact JSON in an HTML comment after a note
asking people to leave it be, because shown as it was (1,400 lines of JSON for one translation) it buried the
Documentation tab's own text. A block is written as `ToolJson.dumpsCompact` writes it (no spaces after `,` or `:`,
characters beyond ASCII as they are), the form blocks already in documents hold; `--`, which an HTML comment can't hold,
is written `-\u002d`, which JSON reads back as `--` (it only occurs inside a string). Blocks written before as shown
text, between `--- stroom-mcp <name> ---` markers, are still read. The blocks' markers keep the name `stroom-mcp`, as
they are part of what is stored. `XsltCode` (stroom-mcp-impl) normalises code for comparison.

**The reference generator**: `ReferenceMapping`, `ReferenceMap` and `ReferenceValue`, read and written like the
translation mapping, and `ReferenceGenerator.generate`, writing one `<reference>` per record and map in
reference-data:2.

**The reader** (`XsltReader.read` and `rebuild`, with `MappingRebuild`, `XsltReading`, `RuleReading`,
`LeafReading`, `SharedCall`, `RebuiltMapping`): reads a translation XSLT back into a mapping, per rule its condition
and each element, Data entry or attribute with its constant or expression, variables replaced by what they select,
shared templates and Data runs followed, and a named template of the XSLT's own (added by hand) followed in place
with its parameters bound. `rebuild` then keeps every entry of a kept mapping that still reads as it generates, or
reads each element for the generator's idioms (`ReadShapes`, `ReadIdioms`: a field, an extracted value, a time
format, a transform, a value map inline or as an `xsl:map`, a default, the first of several fields, a lookup, a
dictionary value, an `in_dictionary` condition), else keeps it as an xpath entry. The shapes are made by calling the
generator's own `XPathText` writers with slots, so the reader follows whatever the generator writes. `RebuiltMapping`
gives `mapping` (null if it doesn't validate), `reused`, `added`, `removed`, `raw`, `importedCalls`, `problems`,
`notes` and `lost`: what inside an event wasn't read (an `xsl:iterate`, or a variable built from its content rather
than a `select`), which rebuild_mapping and the hand-edit gate act on. The record-number read and `stroom:log`
calls are known and not reported as unread. An XSLT with no template matching its input is refused ("there is
nothing to read"). The reader also knows the time conversions earlier generators wrote (a seconds' fraction
truncated; before 2026-10-24, digits finer than a millisecond rounded rather than cut), so an XSLT saved then still
reads as its format. A reading's maps (`XsltReading`, `RuleReading`, `LeafReading`, `SharedCall`) and the entries
`ReadIdioms` builds have their keys in a fixed order, which the reader fixtures digest (`TestXsltReaderCases`).
`RebuiltMapping.summary` is what rebuild_mapping reports: `entries_kept`, then `entries_new`, `entries_removed`,
`kept_as_xpath`, `calls_imported_functions`, `not_read` and `problems` where not empty.

**XPath parsing** (`XPathTreeParser`, `XPathNode`, `XPathTrees`, `XPathMatch`, `XPathFunctions`): expressions are
parsed into trees, not split with regular expressions; a pattern and an expression are compared as trees, slots
binding the subtrees that fill them, and what is read back is cut from the expression by its tokens' positions, as
written. Parsing only, nothing is evaluated: Stroom's functions, the XSLT's own and an imported XSLT's are functions
of any arity as long as the stylesheet binds their prefix (an unbound prefix is XPST0081, an unknown standard
function or wrong arity XPST0017, as in Stroom). Constant parts aren't evaluated (`1 div 0` parses), and inline
functions and URI-qualified names (`Q{uri}name`) are errors, so such an expression is kept as written.

**Mapping changes** (`MappingChanges.applyChanges`, `applyPlanChanges`): a fix sends only what changes, merged into
the whole mapping (or index plan): a rule replaced whole by name, a common entry by its path (and Data name), any
other key whole, `{"remove": true}` to remove one. The result is an `Applied` (the merged mapping and what each
change did); a change that can't apply is a `ToolRefusal`. Seen with a small local model at about 22 tokens a
second: each fix resent the whole mapping, some 800 tokens and 40 seconds of writing, five times over.

Tests: every mapping in the golden set is generated and compared with its golden XSLT, problems and warnings
(`TestXsltGeneratorGolden`); the generator's tests run its XSLT with Saxon over the tests' records and validate the
events against the event-logging XSDs; the reader is tested by reading and rebuilding golden XSLTs and running both
XSLTs over records (`TestXsltReader`); `TestStroomXPath` and `TestXsltCompileCheck` hold the Saxon-HE limits.

## Field-mapping documentation and the CEF mapping (`fielddoc`, `cef`)

The Field mapping tables of every kind of pipeline's documentation, and the CEF mapping, in `stroom-ai-impl`, used
by write_documentation, build_translation_xslt's preview, draft_cef_mapping and review_coverage. They build on the
translation generator (its mapping records, `XsltComments`, `StyledXslt`, `MappingFacts`, the XML tree) and
`KeptMapping`.

* **`stroom.ai.impl.tools.fielddoc`**:
  * `FieldDocs`: the layouts (`BY_EVENT_TYPE`, `COMPACT`), a field's source and conditions in words
    (`sourceText`, `conditionText`), the event-logging schema's words for a path, grouping of event types with
    roughly the same fields (`similarGroups`), `dataKey` (a Data name asked once under every element), and the kept
    descriptions blocks (`readDescriptions`, `descriptionsBlock`, `descriptionSourcesBlock`).
  * `Descriptions`: the agent's descriptions, where each field came from when described, and those to ask for
    (`wanted`). A description is asked for again when its field's source changes.
  * `EventFieldDocs`: the translation's tables (`fieldMappingMarkdown`), both halves of each mapping (where a value
    comes from, and what the sample's events got). Events are matched to rules by the generator's processing
    instruction (`ruleOf`); events no rule wrote are reported rather than dropped. `sampledEvents` reads Events
    from stepped output; `runningEvents` takes a hand-edited XSLT's own events and marks each with the rule the
    marked XSLT gave the same record's event in the same place, leaving a record unmarked where the two give
    different numbers of events. How event types are grouped into sections:
    * On what they write besides TypeId, Description and their Data, as Data is what a kind of event carries, not
      what it is (seen with Data counted: SecretServer rules with the same Data entries, an Alert's and Process
      ones, in one section). A rule that writes only Data (an Unknown one) is judged on its Data.
    * Paths are compared without the side an object is on (`FieldDocs.comparedPath`: Source/Destination,
      Before/After, AddGroups/RemoveGroups, a Network's Connect/Close; seen: a folder's create and move shared
      nothing, each in a section of its own).
    * Data every event type writes alike (same Name, same source) is one row of the Common fields table, and its
      description is asked for once (seen: a Windows event's System/EventID in each of 23 SecretServer rules,
      repeated in every table and putting them all in one section).

    Extractions (each field a regex parses) are left to the XSLT, not listed, as a long list of regexes is more
    than documentation needs; the fields they give are still named where they are read (asked for by the user). A
    Data holding Data (`data_parent`) has its own keyed under it, as the mapping keys them. A rule kept as Unknown
    is only one that writes Unknown: `allow_unknown` on a rule that writes an action element means nothing, so it
    isn't listed. The events no rule wrote are counted by action element, written `{'Kind': n}`.
  * `IndexFieldDocs`: an indexing pipeline's tables (from its plan or the documents it wrote, including object
    arrays), an existing index's (from its survey), a discovery pipeline's; each field with a description, its
    type, the path it comes from, and how often the sample populated it. By event type, common fields are only
    those every event of the sample has, shown without the In sample column; a field only some events have (or read
    from whichever action element the event has) is shown under each event type whose events have it, from
    `summarise_streams`' shares by action (seen: `source.ip`, in 40% of the events, and the object's and folder's
    fields shown as common to all). Each section names its events' TypeIds, most first, with each one's share of
    all events (up to 15; seen: nothing said which kinds of event a section's fields came from). Fields the
    sample's documents have none of are given example values from the index, shown as from the index (asked for by
    the user). Percentages are whole numbers, a half rounded to the even number. `TestIndexFieldDocs` pins the text
    against golden files of what is written for the same plans, documents and surveys; a change to them is a
    decision, not a fix.
  * `DocumentationText`: the doc's shape (`titled`, `sectionLevels`, text added below an agent's section).
* **`stroom.ai.impl.tools.cef`**:
  * `CefKeys`: ArcSight's dictionary (each key's full name, type, length, meaning), the custom slots with their
    Label keys, the header fields, where Event values go by default.
  * The plan: `CefPlan` (output, topic, kafka key, custom keys, header, common, events by kind, not sent, imports,
    style), `CefValue` (a header constant, or an XPath with a map and default), `CefField` (path, key by short
    name, label, transform: `text`, `epoch_ms`, `outcome`), `CefOverride`. Read with `fromJson` (errors as
    `MappingValidationException`), written with `toJson(DumpMode)`; `COMPACT` is the form kept with the XSLT.
    `CefPlan.problems()` and `unsafe()` say what's wrong (a refused expression in `XsltSafety`'s words).
  * `CefEvents` (each Event's kind and values by path; type checks), `CefDraft` (`draft`, `extend`: what a plan
    lacks for more Events, placed where the draft would place it), `CefXslt` (the XSLT: a template a part, in the
    style's layout and naming), `CefSample` (runs the plan's XSLT over sample Events with the locked-down Saxon
    processor; no resource is read, so a plan with imports can't run here), `CefLines` (`linesIn`, `parse`,
    `review`, `examplesFrom`), `CefDocs` (the tables, by event type or compact, and the values not sent) and
    `CefInstructions` (what standing instructions say: mappings, custom keys, topic, vendor, template).
  * `CefLines.parse` scans a CEF extension rather than matching one regular expression (a value runs to the next
    ` key=`), so a long value can't overflow Java's regex stack.
ECS short descriptions for index fields come from the ECS reference bundled in stroom-mcp-impl's resources (empty
without it); the indexing core's `FieldPlans`, `TemplateCheck`, `FieldPlan`, `PlannedField`, `Discovery` and
`EcsSchema` supply the index side.

## The indexing core (`stroom.ai.impl.tools.indexing`)

The pure model and generators the indexing tools are made of, in `stroom-ai-impl` so other tools (`save_xslt`,
`write_documentation`, the field documentation in `fielddoc`) can use them. Nothing here reads Stroom or the
session: the tools read the Events, the kept and drafted plans, the build's docs and the index's fields, and pass
them in.

* **`FieldPlan`**, **`PlannedField`**, **`Discovery`**: the plan. A `FieldPlan` has `backend`, `index_name`,
  `time_field`, `fields`, `drop_when`, `discovery`, `imports`, `shared`, `subobjects`, `convention`,
  `custom_namespaces`, `style` (the XSLT style from an AGENTS doc's style section, else defaults) and `stand_ins`. A
  `PlannedField` has `name`, `type` (`id`, `keyword`, `text`, `date`, `long`, `double`, `boolean`, `ip`, or `object`),
  `source`, `description`, `transform` (`outcome`), `multiple`, and for a field of objects `items` and `distinct_by`
  (written to JSON only when set, so a plan without objects is unchanged). `FieldPlan.toJson()` is what Jackson writes
  and what is kept in an XSLT doc's description, field for field, so plans already kept still read: a plan's and a
  discovery's `stand_ins` are written only when there are any, as a field's `items` and `distinct_by` are.
  `FieldPlan.fromJson` validates with `ModelReader`, as the translation mapping does, and refuses with every error's
  location, type and message in the same fixed wording (`Input should be a valid string`, `Field required` and the like;
  a bad `type` lists the logical types alone, `object` only when that is what was given), which
  `TestFieldPlanValidation` pins: an agent acts on these messages. `required()` gives the plan's problems: StreamId,
  EventId, the time field, `@timestamp` for Elasticsearch, objects on Lucene, names starting `_` (Stroom's Elasticsearch
  filter drops them silently), and a name that is both a value and an object while `subobjects` is true. `leafFields()`
  gives a field of objects as its items (`users.login`), as templates and searches see them. Example plans are in
  `stroom-mcp-impl` test resources `golden/index/` (`plan.json`, `discovery-plan.json`).
* **`FieldPlans`**: what a source means (`*` one element, `//` any between, `|` a union, split by `branches`
  outside predicates and quotes), how often a source is populated, the logical types and each backend's rendering
  (a Lucene keyword is TEXT with the KEYWORD analyzer, as Lucene has no working KEYWORD type).
* **`IndexingXslt`**, **`ElasticDocument`**, **`DiscoveryXslt`**: the indexing XSLT a plan generates, reading Events
  (`event-logging:3` as the default namespace) in the Events translation's style: each part a template of its own
  with a comment, an input read often held in a variable, never one read inside a loop. Elasticsearch documents are
  nested by dotted name, each object written only when one of its fields is present; an object of the same shape
  under one element in several places (an action's Outcome, a Resource) is written by one template applied wherever
  it occurs; a field of objects by `xsl:for-each` or `xsl:for-each-group`. Stand-ins left out are passed over in each
  field's (and each item's) source. A discovery plan's XSLT copies each raw record as it is, adding `StreamId`
  (`stroom:stream-id()`), `EventId` (`stroom:record-no()`), `@timestamp` and stream meta; it is written from texts
  kept as resources beside `DiscoveryXslt` (`discovery-json.xslt` and the rest), placeholders replaced in turn, and
  the golden discovery plans pin it to the byte. 103 golden plan/XSLT pairs
  (`stroom-ai-impl` test resources `golden/indexing`) must be written to the byte (`TestFieldPlanGolden`).
* **`PlanTemplates`**: a plan's Elasticsearch index template (`FieldPlan.elasticTemplate`), mapping a field of
  objects as its items.
* **`EcsSchema`**: the Elastic Common Schema as Elastic publishes it (`conventions/ecs_fields.json`) and what
  `ecs@mappings` maps otherwise (`ecs_component.json`, measured against a real Elasticsearch), bundled with the agent
  knowledge in `stroom-mcp-impl` and given to `stroom-ai-impl` through `AgentKnowledge.conventionsFile`
  (`BundledKnowledge`); `EcsSchema.from(knowledge)` reads them once. It checks an ECS plan field by field, names
  Data elements as ECS fields, and says which fields a template leaves to `ecs@mappings`.
* **`IndexTemplates`**, **`TemplateMappings`**, **`ExampleNames`**, **`ExampleTemplates`**, **`FromExample`**,
  **`TemplateCheck`**: index and component templates as users paste them (Dev Tools requests, GET output, Stroom's
  `elastic_index_templates` output, a mapping), read and written back as a Dev Tools request; mappings read as
  dotted fields and composed as Elasticsearch composes them; a plan's fields named after an example
  (PascalCase, camelCase or ECS-style, nested where the example nests); the final template built from an example or
  from the plan with nominated components; and the check of a template against the documents stepped. A text that
  can't be read is an `IllegalArgumentException` saying why; a document value read from an indexing XSLT's output is
  a `TemplateCheck.DocValue(kind, text)`.
* **`IndexingHelpers`**: the pure parts the tools share: marking fields that hold several values
  (`withCardinality`, given the event-logging schema), choosing the plan a tool was given (`planGiven`), the
  convention profiles, which index a pipeline's index name writes to (`writesToIndex`, which `IndexDocs.writesTo`
  uses too), the template target for an index name, the `needs_components` reply, what to tell the user of fields
  an index lacks, and what a draft leaves out of the index it replaces (`replaced`).

`recorded_calls.json` (`stroom-ai-impl` test resources, `tools/indexing/`) holds calls to the core with the result
each must give, or the refusal; `Globs` lives in `stroom.ai.impl.tools` for both modules.

## Shared helpers

Small classes every tool uses, so behaviour is the same everywhere. Most are in `stroom-ai-impl`
(`stroom.ai.impl.tools` and subpackages); `JsonResults`, `FeedNames` and `DocumentWrites` are in `stroom-mcp-impl`.

* **`ToolRefusal`**: what a tool or helper throws when it can't go on, worded for the agent to act on. The tool
  returns its message as an error result, not an exception.
* **`JsonResults`**: how the build tools return results: the map as JSON text (nulls left out) for the model, and
  the same map as the result's structured content.
* **`ToolArgs`**: arguments in the shapes models send them: a list given one value, or as text (JSON, or a
  single-quoted literal with `True`/`False`/`None`), read as the list it plainly is; a flag given as text or a
  number (set when not 0); an object given as JSON text; text that isn't JSON refused as `{name} is text that is not
  JSON ({msg} at character {pos}): ...`. `PipelineArguments.textMap` reads the pipeline tools' text mappings the same
  way.
* **`JsonValues`**: helpers for JSON-like maps and lists (truthiness, the maps or strings a list holds, numbers
  compared across types, deep copies, counting).
* **`ToolText`**: text handling the sample analysis depends on: Unicode `\w`, `\d`, `\s` in its regexes, only `\n`
  ending a line (so `$` doesn't match before a `\r`), lengths and slices in code points, and how values are written
  in messages (`repr`, `list`, float formatting). Changing it changes field documentation, CEF regexes and many
  messages.
* **`XmlTrees`**: XML parsing for the checks (`parse`, `tryParse`, `child`, `isXsl`, `attribute`); refuses a
  DOCTYPE.
* **`SampleTexts`** (`stroom.ai.impl.tools.sample`): what counts as sample data. A sample that is a file path, a
  list of paths or an attachment reference is refused with an instruction to pass the file's text (the tools can't
  read the client's files). Broken-up shapes small models send (a `{name: text}` map, a list of texts, a file cut
  into an object's keys and values) are read where possible and said plainly where not.
* **`FeedNames`**: feeds by the names users give them. `exact` finds a feed of exactly that name; `feedsNamed` finds
  those whose names match in any case (they share one set of streams); `resolve` offers up to three close matches in
  a form, with a choice to correct the name; `notFound` / `noFeedNamed` refuse a name that is no feed's here, saying
  the user may mean another Stroom cluster and the work must stop (asked for by the user: a name or UUID from another
  cluster must not lead the agent to use or make something else).
* **`DocumentWrites`**: documents read and written through Stroom's stores as the user (View to read, Edit to
  write): a build's Documentation docs, and a working copy's content written back over its original. A Documentation
  doc that can't be read back while it is written is refused: `The Documentation doc ... can't be read: it doesn't
  exist, or you don't have permission to view it`.
* **`Globs`**: glob matching (`*`, `?`, `[abc]`, `[!abc]`), case-sensitive; the error rules
  (`error_rules.yaml` and `stroom.ai.errorTriage.rules`) match element globs on lower-cased text.
* **`CloseMatches`**: similarity ratio and close matches for "did you mean" suggestions; a second string of 200 or
  more characters has its most common characters ignored when scoring, so long texts don't match on filler.
* **`IpAddresses`**: whether a value is an IPv4 address (four decimal octets, no leading zeros) or IPv6 (at most one
  `::`, an optional IPv4 tail, an optional `%scope`), used by the sample profiler and the index template check.

## Bundled knowledge: guides, conventions and the build instructions

The tools' working knowledge is bundled with Stroom in `stroom-mcp-impl`
(`src/main/resources/stroom/mcp/impl/knowledge/`, read by `BundledKnowledge`, which binds
`stroom.ai.impl.tools.AgentKnowledge`). Without that module there is none, and the tools that use it say so.

* **Working guides** (`guides/<name>.md`): `agent-instructions`, `cef`, `data-splitter`, `documentation`,
  `event-logging`, `indexing`, `json-input`, `reference-data`, `visualisation` and `xslt`. Short, hand-written
  summaries of the Stroom documentation for the work the tools do. The chat reads them with `get_instructions
  guide=<name>` (`guide=guides` lists them); `/mcp` serves them as resources.
* **Field convention profiles** (`conventions/<name>.yaml`): `ecs` and `stroom-flat`, read by
  `get_field_conventions` and the indexing tools. Beside them, `ecs_fields.json` (the Elastic Common Schema as
  Elastic publishes it) and `ecs_component.json` (what Elasticsearch's `ecs@mappings` component template maps),
  read by `EcsSchema`.
* **Build instructions** (`server-instructions.md`): the order of work and the rules every build follows (which
  kind of work fits what the user has, how to ask the user, the plan's `next`). `/mcp` sends them to every client
  when it connects (`StroomMcpServer.INSTRUCTIONS`, after a short note on how Stroom's tools run); the chat puts
  them in its system prompt once the build tools are offered (`BuiltInTools.buildInstructions`, keyed on
  `start_onboarding` being offered).

The documents are served on `/mcp` as MCP resources (`McpResources`):

| URI | Content |
| --- | --- |
| `stroom://guides` | The guides' index: each guide's URI and title |
| `stroom://guide/{name}` | A working guide (also listed one resource per guide, for clients that don't read templates) |
| `stroom://conventions/{name}` | A field convention profile |

`/mcp` serves no MCP prompts. The workflows a client would have run from prompts are carried by the build
instructions and by each build tool's result: `next` names the plan's next step and the one call that does it
(`BuildPlan`). The standing instructions a user writes (Documentation documents named `AGENTS`, applying to their
folder and below) are read with `get_instructions`.
