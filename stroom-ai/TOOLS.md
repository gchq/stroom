# Stroom AI tool inventory

The reference for every tool Stroom AI gives an agent, for maintainers extending it: what each tool does, its
contract (arguments, results, refusals), the checks it makes, the classes that implement it, and why it behaves as
it does where that isn't obvious. How to use Stroom AI is in the user and administrator documentation
(stroom-docs), not here.

There are 107 tools, in two modules:

* **Built-in tools** (56): `stroom-ai/stroom-ai-impl`, package `stroom.ai.impl.tools` (the tool framework) and its
  subpackages by area (`chat`, `config`, `documents`, `instructions`, `processing`, `propose`, `query`, `sequence`,
  `skills`, `streams`, `time`, `timeline`, `workspace`), bound in `BuiltInToolsModule`. Documents, search, streams
  and processing diagnosis, administration, the chat's workspace, and proposals the user applies with a button.
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
  `processing`, `indexing`, `dashboards`, `administration`, `sequences` and `skills` (the tables below give each
  tool's). A group's name may hold `_`: it is only MCP tool names that are split at the first `_`, and an MCP
  group of the same name as a built-in one is `mcp_<name>`. A tool in no
  group is always offered. A dashboard tab's guidance turns on `dashboards` from the start
  (`BuiltInToolGroups.neededBy`), and a skill run by name (the chat's `/skill:` command) turns on `skills`. A group none of whose tools the user may use, or that are usable (see
  "Usable"), isn't offered: `activate_sequences` only to a user who can Use a data source. `activate_*` are
  not tools of their own: they are made by `ToolGroups` and are not in the tables.
* **Usable**: a tool can say it has nothing to work on for the user (`StroomTool.isUsable`), and the chat then
  doesn't offer it, nor the guidance that names it (`ContextAssembler.withoutGuidanceForToolsNotOffered`). Asked as
  the user when a run starts; a yes is kept for the rest of the chat (`BuiltInTools.isUsableInChat`), so its tools
  stay the same from question to question, and a no is asked again at the next run. Only `sequence_query` uses it:
  offered to a user who can Use at least one data source (Elastic and Lucene indexes looked at first, then the
  whole list; the stores' lists and the permission check, no cluster call), and so its group, `sequences`,
  is too. Over `/mcp` it is offered whatever it says, as the
  server instructions are the same for everyone, and `/mcp` has no groups: a client hides tools itself.
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
| `activity_timeline` | One user's (or entity's, or person's: a person id or several logon ids) activity timeline from any Elastic Index sources (asked, or all, where not given), worked out in code: detail spans (focus sessions where a source records window focus; pairs from a start to its end, the schema's, a skill's or inferred; spans bounded by a key such as a session id; activity periods), events without roles classified from their Events XML (else the index's fields or codes), each index's person or user field found and said (by person id or by logon id), daily totals, missing events flagged, each source's first and last events where the period has none; activities rolled up (`characterise`, or `view` activities); what was learned offered for the skill's Event chains | search | yes | timelines | | | Use | slot |
| `ask_user` | Asks the user questions in a form, with options to pick, and waits for the answers | core | yes | | | | | trusted |
| `check_skill` | A skill's problems and suggestions, each with a fix: structure, parameters, example calls against the tools' schemas and policies, data sources and fields, hand edits, vague steps, values that should be parameters, charts, near-duplicates, use | core | yes | skills | | | View, Use | |
| `compare_activity` | Compares one person's activity in a window with their own baseline (and, asked for, their peers': the same UserDetails Organisation, read from one event each) from index aggregates only (ESQL, else StroomQL, in monthly slices): volume, rhythm, breadth, novelty, and optionally categories, an access graph and location; coverage, leave, exceptions and source-wide shifts checked first; scored in code (robust z-scores, change points, drifts, values started and stopped); at most 20 ranked findings, each an indicator with an alternative explanation, a line a month, CSV files | search | yes | timelines | | | Use | slot |
| `config_properties` | Searches, gets and lists system properties, with overrides and their sources; secrets masked | administration | yes | administration | Manage Properties | | | |
| `current_results` | Snapshots the results table of the attached query or dashboard tab as a workspace table | search | yes | | | | Use | chat |
| `delete_file` | Deletes a file in the workspace's `/outputs/` | core | no | | | | | trusted |
| `describe_data_source` | A data source's fields, conditions, documentation and AGENTS docs, and an index's searches that would mislead; or one field, named loosely | search | yes | | | | View, Use | |
| `document_documentation` | A document's Documentation tab | core | yes | | | | View | |
| `document_info` | A document's type, path, tags, history, version and dependencies | core | yes | | | | View | |
| `document_links` | Follows dependency links (dependants or dependencies) from documents | core | yes | | | Manage Processors | View | |
| `elastic_index_templates` | An Elastic Index's index template and its component templates, read from its cluster | indexing | yes | indexing | | | View | |
| `esql_query` | Totals, top N and grouped answers on an Elastic Index's index, worked out by Elasticsearch (ESQL) | search | yes | | | | Use | slot |
| `explain_processing` | Why processing isn't happening, or is slow, link by link from nodes and jobs to each filter's tasks and priority | processing | yes | processing | Manage Processors | Manage Jobs, Manage Nodes, View System Info | View | |
| `explain_retention` | How long data is kept and why: retention rules in order, the rule keeping a feed or stream, what would go now | processing | yes | processing | | Manage Policies, Manage Jobs | | |
| `fetch_events` | Fetches the events behind StreamId/EventId rows into an XML file, or reads a stream's records | search | yes | | View Data | | | slot |
| `find_in_content` | Finds text in documents' content (XSLTs, converters, dictionaries, ...) | core | yes | | | | View | |
| `find_skills` | Searches the skills the user can read, or reads one by name, checked; with arguments, its parameters bound and the missing ones asked in a form | core | yes | skills | | | View | |
| `folder_contents` | An Explorer folder's documents, streams, processor filters and task counts; finds documents by name | core | yes | | | Manage Processors | View | |
| `gantt_chart` | Draws a workspace table of times as a timeline: a Mermaid gantt block the chat draws with its own renderer | core | yes | dashboards | | | | chat |
| `get_current_date` | Today's date in the user's time zone | core | yes | | | | | trusted |
| `get_current_datetime` | The current date and time in the user's time zone, and in UTC | core | yes | | | | | trusted |
| `get_current_time` | The current time of day in UTC | core | yes | | | | | trusted |
| `get_instructions` | The AGENTS docs that apply to folders or documents, or a bundled working guide | core | yes | | | | View | |
| `instruction_evidence` | What the user's own recent chats say about how they want things done: repeated and mixed form answers, and their messages | core | yes | build | | | | chat |
| `propose_instructions` | Offers standing instructions (an AGENTS doc) for a folder as a diff: applied to the doc's editor, or created and saved | content | yes | build | | | View, Use | chat |
| `propose_skill` | Drafts (from the chat or a description, a form for what only the author knows), offers templates, trial-runs and proposes skills for a SKILLS doc as a diff: applied to its editor, or created and saved; remove, undo, record a hand edit | content | yes | skills | | | View | chat |
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
| `propose_timeline_dashboard` | Offers one button that writes a timeline the chat built (its spans or activities) as a CSV Data Connection with its timeline chart in a folder the user chooses, then opens the New Dashboard dialog for a dashboard of them | content | yes | timelines | | | View | chat |
| `propose_visualisation` | Plans a chart of the attached table, checks its rows can make it, and offers a button to create it | content | yes | dashboards | | | View, Use, Edit | chat |
| `read_document` | A document's content; a pipeline as Stroom runs it, an XSLT's translation, an index's survey | core | yes | | | Manage Processors | View | |
| `read_file` | Reads lines of a workspace file | core | yes | | | | | |
| `read_turn` | Reads a whole earlier turn of the chat | core | yes | | | | | chat |
| `run_stroomql` | Runs a StroomQL query into a workspace table, refusing a search on an index whose answer would mislead | search | yes | | | | | slot |
| `search_events` | XPath, text or jq search over fetched events into a table; or, given an index, finds events as a dashboard would | search | yes | | | | Use | slot |
| `search_history` | Searches the chat's history for text | search | yes | | | | | chat |
| `sequence_query` | Ordered sequences of events (X then Y by the same entity within N): EQL on Elastic Indexes (steps, join fields, missing, runs, until), or a streaming key-ordered join in Stroom where EQL can't (keysOnly entity steps, join-field transforms, sides too big for EQL); a StroomQL windowed join of two steps on other data sources; optionally one person's only (a person id or logon ids, each step restricted, said in `scope`) | search | yes | sequences | | | Use | slot |
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
| `catalogue_event_types` | The source's event types: evidence, the agent's proposals checked against every sampled record, confirmed by the user a set at a time, then the catch-all | content | no | build | View Data | | View, Edit | build |
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
| `propose_context_lookup` | On the user's request: how records are enriched from their parts' context data (the key, its match rate, a preview), then the pieces that build it | content | no | build | View Data | | | confirms |
| `propose_index_template` | Builds the final Elasticsearch index template for the cluster admin to apply | indexing | no | indexing | Stepping | | View, Edit | slot, confirms |
| `rebuild_mapping` | Rebuilds a translation's kept mapping from its XSLT | content | no | build | | Stepping | View, Edit | slot, confirms |
| `record_source_notes` | Keeps vendor reference documentation, and the field dictionary made from it, in the build (event types go through `catalogue_event_types`) | content | no | build | | | View, Edit | build |
| `record_source_types` | Profiles an aggregated feed's source types and writes its registry, with the build's scope | processing | no | build | | | View, Edit | build, confirms |
| `rename_index` | Changes the Elasticsearch index name the build's Elastic Index and pipelines use | indexing | no | indexing | | | View, Edit | confirms |
| `reprocess_streams` | Processes streams again through a build's pipeline | processing | no | processing | Manage Processors | View Data | View, Edit | confirms |
| `review_coverage` | Finds the records across a feed that no rule of its translation matched | content | no | build | View Data | Stepping | View, Edit | build, slot |
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

**Large results, outside Stroom** (`LargeResults`, 2026-11-20; seen in the Haiku evals: results of 50k–110k
characters that Claude Code saved to files of its own, which the agent can't read through Stroom's tools). For a client
outside Stroom with a session workspace, a result of `survey_feed`, `catalogue_event_types`,
`draft_translation_mapping`, `build_translation_xslt`, `write_documentation` or `fetch_events` over 40,000 characters is saved whole
to `/outputs/<tool>/<build, else a stable key>.json` (a list's entries one a line, a long text a line a list entry)
and given with its large parts outlined, each tool's own steps in order while it is still too large, then the parts
not kept cut (a list says how many more, `(N more in the saved file ...)`). `whole_result` says where (`saved`), what
was `outlined` or `cut`, `text_lines`, and how to read a part with jq. Never outlined: status, hint, next, problems
and refusals, review ids and forms, saved documents, and `user_chose` (what the user chose in a form; seen in chat
183: told it only in part, the agent asked again). Stroom AI's chat, a result within the limit, no workspace or a
failed write: the result whole, as before. Test clients read an outlined result back whole (`WholeResults`).
`fetch_events` reads of a stream are outlined the same way (`FetchEventsOutline`). A result nothing in which can be
shortened (all of it what the agent acts on) is given whole, still with `whole_result` first, saying plainly so: it
is past the client's limit, so the client shows only its start, and the note there says where Stroom saved it.

**Large results, in Stroom AI's chat** (`ToolOutputs`, `LargeResults.chatOutline`, 2026-11-22; seen in chat 183: 60
workspace calls fishing out a draft's notes, a catalogue check's problems, unplaced fields and typing, and
write_documentation's fields). The chat saves any result over 4,000 characters whole to
`/outputs/turn-<t>/call-<m>.txt` and gives the model a stub. The stub gives, before anything else, `next` and `hint`
and the parts that say what to act on, in full but capped (a draft's notes at 8,000 characters, as they hold the
decisions to take, seen in chat 184; a `needs_descriptions` round's fields at 8,000 and a check's typing at 12,000,
2026-11-22; each other part at 4,000; 24,000 in all, room for a check's problems, unplaced and typing together; a
list an entry a line, then `… N more` and the jq query for the rest; `ToolOutputs.LONG_PART_CHARS`), in this order:
error, refused, notes, user_chose, problems, `failed` (a failed verify_index's checks that didn't pass, 8,000
characters, 2026-11-23), unplaced, `check.problems`, `check.unplaced`, `schema_check.problems`,
`schema_check.unfilled` (a draft's schema check, 2026-11-22; seen in chats 184 to 186: read from the saved draft 17
times), `check.could_type_all`, `check.typing` (with `could_type`), warnings, `schema_check.warnings`, user_actions
and review_id (`LargeResults.ACT_ON`, the same list an outline never cuts, but for notes, warnings, the check's
typing and the schema check's warnings), and a `needs_descriptions` round's `fields`. A part given in full is left
out of the outline below it, a part within a part too (`schema_check.problems` from the outline's `schema_check`). Where the tool outlines its large results (the six tools above), it also
outlines a copy for the chat against 10,000 characters (`LargeResults.CHAT_OUTLINE_CHARS`), saving its own copy to
`/outputs/<tool>/<key>.json` as for a client outside Stroom, and the stub gives that outline (less what it says above
it) in place of the preview of the result's first 30 lines. A result the user must be shown as it is (`needs_review`,
`needs_confirmation`, `needs_approval`, `needs_showing`, promote_build's final result among them) is given whole up
to 64,000 characters and stubbed past that as before, with neither.

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
  `esql_query` answers totals, top N and grouped questions on an Elastic Index with ES|QL, in Elasticsearch;
  `sequence_query` finds ordered "X then Y" sequences: with EQL across Elastic Indexes on one cluster, as a
  streaming key-ordered join in Stroom on Elastic Indexes where EQL can't, and as a StroomQL windowed join of two
  steps on any other data source;
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
  `choices` (each `one_of` for a choice an element holds one member of, or `any_of` for one whose members may be
  mixed and repeated, such as View's objects, and `one_required`). The XSD itself is over 100 KB: an agent once spent twelve minutes reading it with PowerShell. With
  `type='XMLSchema'` (or no `type`) and no `uuid`, the newest event-logging version this Stroom holds is used (seen
  in chats 182 and 183: `element` alone refused for want of a uuid); `elements` is read as `element`. Without `element`,
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
`/documents/<uuid>/mapping.json`, the reply saying the saved file's layout (`SavedJsonLayout`, from the object
saved: its top-level keys, each nested object's parts by path, each list's size and its items' keys) and a jq query
that works on it (e.g. `.mapping.events[] | {name, fields: [.fields[] | .path]}`), as the whole mapping is under
`.mapping` there, not at the top level as in a draft's file (seen in chats 184-186: some 15 jq queries of
`.events[]` and `.common[]`, eight failing or empty). A translation, pipeline or survey section saved as too large
says its layout the same way. An XSLT saved by the build tools with a mapping that no longer reads (deleted from
its Documentation tab, or edited so it doesn't parse) gets `## Kept mapping` `{"lost": ...}` instead, saying the
XSLT is now treated as hand-written and that `rebuild_mapping` recovers it (`DocumentAnalyses.lostMapping`, which
checks the XSLT's kept-mapping tag through `XsltSaves`).

* **Arguments**: `uuid` (optional only with `element`, and `type` `XMLSchema` or none); `part` (one part by name, or `own`
  for a dictionary); `type` (checked against the document); `find` and `contextLines` (Documentation only; several
  terms separated by `|`; context default 8, at most 60); `mapping` (XSLT only: the kept mapping whole); `element`
  (event-logging XMLSchema only, e.g. `EventDetail/Authenticate`).
* **Returns**: text: `<Type> '<name>', <folder path>.`, the parts or the files they were saved to, then the
  sections above. `find` and `element` return JSON (also as structured content). Files written are attached to the
  result as resources.
* **Refuses**: no `uuid` (unless `element` is given with `type` `XMLSchema` or none); not viewable, or not a filter the user may see (one message;
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

Content · read-only (it only offers a button) · chat only · build group · checks View, and Use on the data sources
whose fields a `## Fields` line is checked against · Classes: `ProposeInstructionsTool`, `InstructionRules.problems`, `UserActions` (`apply_documentation_change`,
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
`## Documentation`, `## Fields`) that name nothing the tools know are pointed out in the result
(`InstructionRules.problems`): the tools leave such lines out without a word, which is right for a note but not for
a slip, so the agent can propose the corrected text again. So are `## Fields` lines (`- user: Initiator.Id`,
`- person: user.id`, read by `activity_timeline`, `sequence_query` and `compare_activity`; `- category: Category` and
`- country: Country`, read by `compare_activity` for its default categories and location) naming no field of the
data sources in the folder (and below) the user can Use (`InstructionRules.fieldProblems`, `IndexFieldNames`).

* **Arguments**: `folder` (UUID or Explorer path), `markdown` (the whole text) or `edits` (`oldText`/`newText`
  against the existing doc; a new doc needs `markdown`), `summary` (shown with the diff).
* **Returns**: text: what the user was shown (naming the folder itself by its whole path, `DocumentAccess.folderPath`,
  as `propose_skill` does, not the folder holding it), the diff, and any lines the tools will leave out; with the
  button.
* **Refuses**: a folder the user can't see; `edits` for a folder with no doc; text identical to the doc's.

### Skills: `find_skills`, `check_skill`, `propose_skill`

Skills group · Classes: `SkillDoc`, `SkillCatalogue`, `SkillChecks`, `SkillFacts`, `SkillParameters`,
`SkillInvocations`, `SkillTemplates`, `SkillUsage`, `FindSkillsTool`, `CheckSkillTool`, `ProposeSkillTool`;
`UserActions` (`apply_documentation_change`, `create_instructions` with `name` SKILLS),
`UserActionService.createInstructions`, `UserActionLookupImpl.createSkills`; in the UI `AiSlashCommands`,
`AiDocumentationChanges`; the guide `skills`

A skill is a saved, parameterised procedure (a hunt, a timeline), Markdown in a Documentation doc named `SKILLS` (one a
folder), several skills to a doc, a `#` heading each (`SkillDoc`). Only the name makes a skills doc: front matter
(`stroom: skills`) holds the history, and a doc of another name with it is not a skills doc. By convention: the first paragraph under the heading is its description; `## When
to use` how people ask for it; `## Parameters` a list (`- name (required): ...`, `- name (default 7): ...`); `##
Steps` numbered, `{name}` where a parameter goes, example calls in fenced JSON (`{"tool": ..., "arguments":
{...}}`); `## Output`. The front matter's `history` (each entry `version`, `date`, `author`, `how`: `agent` or
`user`, `skill`, `change`, and `body`, a 12-character hash of the text below the front matter as the change left it)
is written by `propose_skill`; a body that no longer matches its latest entry's hash is an edit by hand. It shows as
YAML text: no change to the shared Markdown rendering. The model follows the steps; nothing is replayed.

* **Discovery** (`SkillCatalogue`): the Documentation docs named `SKILLS`, found by name (`DocFinder`), View
  checked for each; no other doc is read. Each is read again after 15 s, and parsed again only for a new version.
  When `find_skills` is offered, `McpAgentChat.withSkills` appends the list to the system prompt (its stable
  part): a line a skill, `- name (folder) [near this context]: description` (descriptions cut at 200 characters),
  those in the folder of the attached tab's document and the folders above it first, the most specific first;
  two of one name are told apart by folder, the nearer first. Over 50 skills or 6,000 characters it lists the near
  ones and those sharing words with the question (name, description, "When to use"), up to the size, then "… N
  more: search them with find_skills words=". A skill the user can't view is never listed.
* **Learning from use** (`SkillUsage`): the listed skills whose description or "When to use" share words with the
  question (or are near it) are recorded as offered, once a question, in a `SKILLS_OFFERED` message (never shown
  or sent to the model); chosen is a `find_skills` call naming it in the same question; asked by name is a
  `/skill:` command or the whole name in the question. `check_skill` reads the user's chats in the
  `instruction_evidence` window.
* **`find_skills`** (core, read-only): `words` (search: name, description, "When to use"; at most 20 listed),
  `name` (or the command's name; the nearest of two first, with `folder` to choose), `arguments` (an object: run
  it). Read by name, the skill is checked first and its problems said, to tell the user before it runs. Given
  `arguments`, its parameters are bound (`SkillParameters`: given values, then defaults), any required one missing
  is asked in a form (the form mechanism every tool uses, `UserQuestions`), and the steps and output come back with
  the values in. Where no form can be shown (over `/mcp`, or the user away) it says which are needed.
* **`check_skill`** (core, read-only): `name` (and `folder`) for a saved skill, `doc` for every skill of a doc (one
  not named `SKILLS` is told to be renamed), or `markdown` for a draft.
  Problems: no description; no steps; a parameter used and not listed, or listed and unused; a template
  placeholder (`[[...]]`) unfilled; an example call that can't be read; a tool that doesn't exist (the nearest
  name offered) or the user's tool policies don't allow; an example call off the tool's schema (unknown or
  missing arguments, wrong types, enum values; `"{name}"` and `[[...]]` fit any type); a data source (`index`,
  `data_source`, a StroomQL `from`) that doesn't exist or the user can't Use; a field (`fields`, `by`, `field`,
  `where[].field`) the data source doesn't have; a timeline skill's `## Event chains` line that isn't an entry or
  names an index the user can't Use, or that recent data contradicts (`TimelineChainCheck`: the index's last 7
  days, its types not seen, a pair's key not held, a pair no longer alternating for 90% of keys); a hand edit since
  the latest history entry (fix:
  `record`); front matter that can't be read. Suggestions: a vague step (no tool named and no call; the tool whose
  description shares most of its words proposed, with its required arguments); a date or time written in, or a
  value the "When to use" phrasings ask about, that should be a parameter; a chart in the chat other than a
  timeline (`gantt`) (a dashboard's); a thin "When to use" (fewer than 2 phrasings or 6 words); a near-duplicate
  (half or more of the name and description words shared); offered 5 or more times and never chosen; asked for by
  name when it wasn't offered.
* **`propose_skill`** (content, read-only: it only offers a button; chat only): `action` is
  * `propose` (default): `markdown` (one skill) or `draft` (a trial's file); `folder` (its `SKILLS` doc) or `doc`
    (any skills doc); `change` (the history entry and the diff's summary). The skill is put in place of the one of
    its name, or added; the front matter is written with the new entry (its hash of the new body); checked, its
    problems said. An existing doc gets an `apply_documentation_change` against its saved text (applied to its
    editor unsaved, refused if saved with other text since); a folder with none a `create_instructions` with
    `name` SKILLS (created and saved on apply, refused if the folder has one by then).
  * `draft`: from this chat's tool calls (`from=chat`, the default; calls that only look around are left out) or
    a `description` (`from=description`); a form asks the name, how people ask, which of the calls' concrete values
    vary (each offered as a parameter named after its argument or the field it is compared with) and where the
    results go; the draft has those values as `{name}` and `[[...]]` for what the model must write.
  * `template`: the built-in starting points (`SkillTemplates`, resources `skill-templates/*.md`): a user activity
    timeline, X then Y by the same entity, who of a group did X, and (2026-11-24) an activity comparison, a leaver
    review, a privileged user review and an alert triage (`compare_activity` and `activity_timeline`; the
    leaving date, leave and exceptions as parameters; findings worded as indicators); each the procedure with `[[...]]`
    placeholders for
    a site's sources, fields and role conditions; no product-specific content.
  * `trial`: the draft run once before saving: kept at `/outputs/skills/<name>.md`, checked, bound to
    `parameters` (refused if a required one is missing), and its steps come back for the model to follow now, as
    a saved skill is followed; the result is shown before the save button.
  * `remove` (`name`), `record` (an entry with `how: user`, its author the doc's last updater), `undo`:
    the latest change proposed with this tool in the chat that the user applied (and saved since), reversed from
    the text before it kept in the chat (the action's base), with a history entry "Undid version N"; refused if
    it wasn't saved, or the doc was saved with other text since.
* **Slash commands** (`AiSlashCommands`): `/skills [words]` lists the skills (from `listSkills`, the same
  readable skills) in the command reference; each skill is a command `/skill:<name>` with its parameters, offered
  in the command list (the picker). Sent as typed; the model is given it (`SkillInvocations.expand`) as the request
  to read the skill with `find_skills` and those `arguments`, which asks for the missing ones in a form.

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

* **Arguments**: `questions` (1 to 8, each `question` up to 500 characters, `options` 2 to 6, each a label of up to
  120 characters (the model is asked for under 80) with an optional description of up to 500, `recommended`,
  `allowCustom` default true); `intro` (default "Some questions before I carry on").
* **Returns**: "The user answered:" with each question and its answer, marked "(in their own words)" when not one of
  the options; or that the user declined (carry on with what was recommended, saying so, or stop), sent a message
  instead (quoted: reply to it first, don't ask again until they've said what they want), or didn't answer (the chat
  stopped, or it timed out: "Don't carry on as if they had").
* **Refuses**: questions malformed or over the limits (an over-long option label or description is refused, never
  cut: a cut label read wrongly and no longer matched `recommended`; the model is told to give a short label and put
  the detail in the option's description); a form too large to show as one ("ask again shorter"); and,
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

* `/attachments/` - tables the user attached, each with a `.profile.md` summary; read-only. A model with tools is
  given a small table (at most 200 rows and 20,000 characters) in full with its path, and a larger one as its path
  and profile (`ContextAssembler.profileMessage`).
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
for a whole read is cut short. A path with files below it is answered as a directory, to list with `ls`, not as no
file (`ChatWorkspace.fileId`; so for every tool reading a workspace file).

* **Arguments**: `path`; `startLine` (default 1, or negative to count back from the end: -20 reads the last 20);
  `lineCount` (default 100, at most the lines per read).
* **Returns**: `<path> (lines a-b of N):`, the numbered lines, and how many more there are and where to read on;
  or that there is nothing from that line.

### `grep`

Core · read-only · Classes: `GrepTool`, `SafePattern`

Finds the lines of a workspace file, or of every file in a directory (at most 100; named with or without its
trailing slash: seen in chat 177, `/outputs/turn-49169` answered "There is no file"), that contain some text, matched
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

* **Arguments**: `path`, `query` (required); `raw`; `slurp` (as jq -s: every JSON value in the file read into one
  array and the query run once on it); `out`.
* **Fails**: a query failing on one value of a file holding several (without `slurp`; not one stopped for its
  work) says how many the file holds, which one it failed on, that the query ran on each on its own, and to give
  `slurp: true` (2026-11-23, chat 187: `group_by` on a JSON lines file of fetched records failed per record, and the
  agent fell back to grep).
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

Core · read-only · Classes: `TableTool`, `TableEngine`, `TableJoin`, `TableExpression`, `ExpressionFunctions`,
`ColumnSummariser`

Queries a table (an attachment in `/attachments/`, a tab's results that `current_results` saved in
`/tabs/results/`, or a `.csv` or `.tsv` file) exactly, in code: `eval` adds columns
worked out per row, `where` filters, `groupBy` with `aggregates` (or `distinct`, with counts) summarises, `orderBy`
sorts and `limit` cuts. `mask` with `distinct` or `groupBy` counts free text as patterns, with numbers, IPs, dates
and IDs masked (`ValueMasker`). The query is typed JSON, never code or a command. Expressions are Stroom's
expression language as dashboards use it (`${column}`, Stroom's function names, e.g. `average()` not `avg()`);
`help` with function names describes those functions instead of querying. The description lists the functions by
category, and its examples are checked to work.

* **Arguments**: `path`; `select`; `eval` (`as`, `expression`); `where` (`column`, `op`, `value`; ops `=`, `!=`,
  `<`, `<=`, `>`, `>=`, `contains`, `not_contains`, `starts_with`, `ends_with`, `matches`, `is_empty`,
  `not_empty`, `in`, `not_in`, the last two with their values as a list or separated by commas); `groupBy`; `aggregates` (`function` count, count_distinct, sum, min, max or avg, or an
  `expression`; `column`, `as`); `distinct`; `mask`; `orderBy` (`column`, `descending`); `limit` (at least 1);
  `timeZone` (the zone its expressions' dates are in; see Time zones);
  `out` (`.csv`, `.tsv` or `.md`, written in the format its name gives, as for every tool that writes a table);
  `join` (`path`, `on`, `rightOn`, `keep` both or unmatched, `within` with `column`, `rightColumn`, `seconds` and
  `after`); `help`. `join` (`TableJoin`) joins another table first and the rest of the query is of the joined rows:
  rows whose `on` cells are the same text (an empty cell matches nothing), optionally only where the other table's
  time is within `seconds` (at or after this one's with `after`; times ISO 8601, UTC without a zone, or epoch
  milliseconds); the other table's columns follow, bar its key columns, one this table has too named
  `right.<name>`; `keep: unmatched` gives this table's rows nothing matches. The description, in the chat and over
  `/mcp`, says a StroomQL join is better for two Stroom data sources. Seen: a chat matching mail items read
  then deleted by user and message id took some 15 calls of `grep`, `jq` and `table`, the `jq` join failing. Its
  rows are held in memory, at most 1,000,000 a table and joined. Loose shapes are taken as meant: a list given as JSON text (surrounding whitespace and
  one stray trailing comma ignored) is read as that list, a single object where a list of objects is expected is a
  list of one, and an `orderBy` item may be a column's name, `-` before it for largest first (unless a column of
  the result is called that, `-` and all).
* **Returns**: "N of M rows matched; the result has K row(s)." (with `join`, first "Joined N rows of … with M of …
  on …: K of the N rows matched, making J joined rows."), and, for a `current_results` snapshot, how much of
  the search it holds, which the answer must say; then a Markdown table; with `out`, the file and the first 10
  rows.
* **Refuses**: a file of a kind that is never a table (`json`, `jsonl`, `ndjson`, `xml`, `xsl`, `xslt`, `txt`,
  `log`, `yaml`, `yml`), pointing at `jq` or `read_file` (a survey's JSON was once read as CSV and refused for a
  stray quote); arguments in a shape that can't be read, quoting what was given (up to 80 characters), e.g.
  "aggregates was text that isn't a JSON list of objects: '…'"; an expression that doesn't parse.
* **Notes**: after two failed queries of the same table in a run, the next failure summarises the column the query
  was about (`ColumnSummariser`) rather than leaving the model trying query after query. Only queries that could be
  read count (e.g. an unknown column or operator): an argument in the wrong shape is refused before the query runs,
  and an expression error is never turned into a summary, as the model fixes either from the message.

### `pipeline`

Core · read-only · Classes: `PipelineTool`

Runs up to 10 data tool steps one after another, each on the file the step before wrote, so intermediate results
never enter the conversation. A step is `{"tool", "args"}`, with tool one of `grep`, `jq`, `xpath`, `table`,
`fetch_events`, `search_events` or `current_results`; each step is run as that tool's own call would be, with the
same checks. A step's `path` defaults to (or `$prev` names) the previous step's output; intermediate files are
written under `/outputs/pipeline/<id>/step-N.<ext>` and deleted at the end (the log says "step N's input", not
their names); a step that gives its own `out` keeps that file, which the next step reads. Only the last step takes the
pipeline's
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

The current date and the current date and time in the call's time zone (see Time zones, below), with the zone:
`2026-11-21 (Australia/Canberra)`, and `2026-11-21T10:42:00+11:00 (Australia/Canberra); UTC 2026-11-20T23:42:00Z`
(in UTC, `2026-11-20T23:42:00Z (UTC)`), to the second, so the model never guesses them; both take `timeZone`.
`get_current_time` gives the time of day in UTC (`09:15:02Z`). `get_utc_time_offset` gives a time zone's current
offset from UTC, daylight saving included ("Europe/London is currently +00:00 from UTC."), from `timeZone` (an IANA
ID or a fixed offset such as `+02:00`), or the Stroom server's default zone when none is given; an unknown zone is
refused with the forms it takes. None touches the workspace.

### Time zones

Classes: `UserTimeZones`, `ToolTimeZone`, `StroomQlDatePoints`, `ChatBrowserZones`

`run_stroomql`, `sequence_query`, `esql_query`, `table`, `get_current_datetime` and `get_current_date` take `timeZone`
("Time zone for the times and results: local, UTC, a zone id or an offset; default the user's."): `local`, `UTC` or
`GMT` (`Z`), a zone id (`Australia/Canberra`, in any case) or an offset (`+11:00`, `+11`, `UTC+11:00`). It decides
what `day()` and a time without an offset mean in the call, and the zone its result's times are shown in. Without
it (or given `local`), the zone is the user's device's: the zone the chat's browser sends with each question
(`AskStroomAiRequest.localZoneId`, kept per chat in memory by `ChatBrowserZones`). The user's Stroom time zone
preference isn't used (decided by the user, 2026-11-21; its default is UTC). Where the device's zone isn't known
(over `/mcp`, whose clients send none, or a chat run resumed after a restart before its next question), the zone is
UTC, with "The user's time zone isn't known here, so times are in UTC: give timeZone to use another." An unknown
zone is refused: "unknown time zone 'X': give local, UTC, a zone id such as Europe/London, or an offset such as
+01:00".

Every result with times says the zone: "Times are in Australia/Canberra (+11:00), the user's device's time zone."
(nothing after the zone when the call gave it); JSON results carry `time_zone`, `utc_offset`, `time_zone_source`
(`given`, `browser`, `fallback`) and any `time_zone_note`. Every result also says what its relative times stood
for, as the Stroom UI works `day()` out in UTC and the agent's "today" may cover other hours: `run_stroomql` adds
"In its conditions day() = 2026-11-21T00:00:00+11:00." (each date point in `where`, `filter` and `having`, once,
`StroomQlDatePoints.used`), and `sequence_query` and `esql_query` give `timeFrom`/`timeTo` resolved to ISO times
(in UTC too, e.g. `day() - 7d` as `2026-11-14T00:00:00Z`). A tool's time given as an ISO 8601 duration (e.g. `P30D`,
`PT12H`, seen given to `esql_query` in chat 190) is that long before now, in the zone
(`StroomQlDatePoints.timeInZone`).

How the zone is applied, with no change to Stroom's core (as the user decided, 2026-11-21):

* A query's `DateTimeSettings` carry the zone and the ISO pattern `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, so a time without
  an offset in a condition, and the expression functions (`eval`, `select`), are in the zone.
* Stroom works a condition's date points (`day()`, `week()`, `hour()` and so on, in `where`, `filter` and
  `having`) out in UTC whatever the query's zone (`DateExpressionParser`), so `StroomQlDatePoints.inZone` puts each
  in the zone before the query runs, quoted, with the zone so durations after it keep daylight saving:
  `EventTime > day() - 1d` in Canberra runs as `EventTime > "2026-11-21T00:00:00+11:00[Australia/Canberra]" - 1d`.
  It reads the query with Stroom's tokeniser (strings and comments untouched), leaves `now()` (the same instant in
  every zone) and `eval`/`select` (already in the zone), and does nothing in UTC. A failure of a query it changed
  gives the parser's error for the query as written.
* A StroomQL column with no format gives a date field's value in UTC whatever the zone, so a result's cells written
  as Stroom writes a UTC time (`2026-11-20T14:00:00.000Z`) are shown in the zone (`2026-11-21T01:00:00.000+11:00`,
  `ToolTimeZone.inZone`): run_stroomql's table, sequence_query's sequences and entities, esql_query's rows.
* `timeFrom`/`timeTo` of sequence_query and esql_query are put in the zone by the tool
  (`StroomQlDatePoints.timeInZone`): a date expression or a time without an offset becomes an ISO time with the
  zone's offset (`day() - 7d` is `2026-11-14T00:00:00+11:00`, in UTC too); one with an offset is sent as given.

ES|QL's own date work in the pipeline (`BUCKET`, `DATE_TRUNC`, `DATE_EXTRACT`, `DATE_FORMAT`, a date without an
offset, and the rows' dates) is in the zone too: esql_query gives `ElasticEsql.Request.timeZone`, and
`ElasticEsqlService` (core, decided by the user 2026-11-21) sends it as Elasticsearch's `time_zone` (taken by
9.5.3). A cluster that refuses it (an error naming `time_zone`) runs the query again without it, in UTC
(`Result.timeZone` null), and esql_query's result then says so. A `where` condition's time on a date field
(esql_query, and sequence_query's Elastic steps; the StroomQL engine's run in the query's zone) is put in the zone
as timeFrom is (`StroomQlDatePoints.conditionInZone`: each of a between's or an in's values), as Stroom's query
builders read one in UTC. The table tool takes `timeZone` too, and its expressions run in the call's zone
(`TableEngine.Query.dateTimeSettings`: date rounding such as `floorDay`, and `day()`); a result with expressions
says so. Stroom's `formatDate` and `parseDate` work in UTC unless given a zone as their third argument (in StroomQL
and dashboards too), so run_stroomql and the table tool give each call without one the call's zone before it runs
(`DateFunctionZones`, decided by the user 2026-11-21): `formatDate(EventTime, 'HH')` runs as
`formatDate(EventTime, 'HH', 'Australia/Canberra')`, and `formatDate(EventTime)` with Stroom's default pattern too;
strings and comments are left alone, and nothing changes in UTC.

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

* **Elasticsearch**: STARTS_WITH and CONTAINS find nothing, or are refused by later Stroom versions (use EQUALS with
  a `*` wildcard, or MATCHES_REGEX). IS_NULL and IS_NOT_NULL match every document. EQUALS `*` finds any document
  holding the field, an empty string included (Stroom searches it as Elasticsearch's `exists`), and NOT_EQUALS `*`
  none; MATCHES_REGEX `.+` finds a non-empty value on a string field. A wildcard on an ip field finds nothing (use a
  CIDR range). The index's ip fields are named.
* **Lucene**: STARTS_WITH and ENDS_WITH find nothing. CONTAINS finds nothing on a keyword field, and a range finds
  nothing on a text field. IN finds nothing when a value holds a space.
* **Either**: IN takes values separated by commas, and a field that isn't queryable is found by no search.

With `field`, one field is described alone, as the user names it (`FieldMatches`, loosely: "Recipient ID" finds
`Email/Recipient/Id` or `email.recipient.id`). The words given and each field name are split into words ignoring
case, separators (`/ . _ -`, spaces) and camelCase, in any order; a field naming only some of the words ranks by how
many it covers; a misspelt word (4 letters or more) matches a close one; words run together or apart match
(`username`, `user name`); where no name matches, the words that say which (not "the", "who"...) are looked for in
the fields' descriptions ("who received it"). One clear match is described: its type, conditions, whether it can be
queried, its source path (from the index plan kept with a feeding pipeline's indexing XSLT, else the documentation's
From column), its description and sample values (from the documentation's field table row) and the AGENTS lines
naming it. Several as close are listed, ranked, each with why it matched, for the client to ask the user: never a
silent guess. None gives the closest few by name.

* **Arguments**: `data_source` (required; name or uuid); `field` (optional; the user's words for one field).
* **Returns**: text: what the data source is, its time field, its fields, the searching note, its documentation and
  its AGENTS docs. The documentation and AGENTS docs are given even when no fields can be seen. With `field`, the
  one field, the several that could be meant, or the closest by name.
* **Refuses**: no such usable data source (`NOT_FOUND`); an ambiguous name.
* **Notes**: audit-logged as a view of the data source.

### `validate_stroomql`

search · read-only · checks Use on the query's data sources · Classes: `ValidateStroomQlTool`, `DataSourceAccess`,
`StroomQlSources`

Checks a StroomQL query with `QueryService.validateQuery`, without running it. First it finds the data source the
query's `from` names (`QueryService.getReferencedDataSource`) and checks the user can use it. Then it reads every
data source the query joins (`StroomQlSources`: join lines, and the `exists` and `in (from …)` subqueries of
`where`, which Stroom runs as joins) and checks Use on each; a joined source given by uuid is otherwise found
without one. On success it says "OK", names the data sources (", joined with …"), and says whether the query groups
rows. Otherwise it gives the parser's error and asks
the model to fix the query and validate it again.

* **Arguments**: `query` (required).
* **Returns**: text, as above.
* **Refuses**: a blank query; a query over `stroom.ai.workspace.maxQueryLength` characters (default 10,000); a query
  naming no data source the user can use (`NO_DATA_SOURCE`, which tells the model to check `from` against
  `list_data_sources`); a query joining one the user can't use (`NO_JOINED_DATA_SOURCE`, the same for its join lines
  and `where` subqueries).
* **Notes**: each validation is audit-logged as processed, with its outcome, naming the first data source as before
  and each joined one as a `JoinedDocument` (`AiAuditLog.processedDocuments`). propose_query, propose_query_change and
  propose_dashboard make the same check (`StroomQlUseCheck`) and log what they offer, or refuse, the same way. Reads untrusted content: the data
  source's name is a document name, and the parser's errors are not known to quote only what the model wrote.

### `run_stroomql`

search · read-only · no permission of its own: Stroom's query service checks Use on the data source when the search
runs · takes a run slot · Classes: `RunStroomQlTool`, `StroomQlLimit`, `MisleadingSearches`, `AgentSearches`,
`MarkdownTables`, `TableProfiler`

Runs a StroomQL query as the user and writes the results to a Markdown table in the workspace. The default path is
`/outputs/queries/query-<yyyyMMdd-HHmmss>.md`. The model gets the path and a profile of the table (row count,
columns, common values).

A field named in `select` in double quotes (`select "Create Time"`) is that text in every row to StroomQL, not the
field, and the query runs without an error; when the text is one of the data source's fields (ignoring case),
`QuotedSelectFields` adds a note to the result saying to write `${Create Time}`, as `validate_stroomql` does to its
OK. A column name after `as`, a function's argument and `where` (where Stroom takes a quoted name as a field) are left
alone.

The search is never unbounded. `StroomQlLimit` reads the query with Stroom's own tokeniser, so text in strings and
comments is never taken for a keyword. It adds `limit <max>` to a query with none, and lowers a larger limit, where
max is `stroom.ai.workspace.maxQueryRows` (default 10,000). Only the query's own `select` and `limit` count, outside
brackets: a joined bracketed query or a `where` subquery keeps its own (Stroom refuses a `limit` inside an `in`
subquery, and a joined query's limit is not the query's). Without sorting or grouping, Stroom stops a search as
soon as it has that many rows. The result says when the limit was added or lowered. When the rows reach the cap it
warns that the row count is not a total, and to count with `count()` and `group by`.

Before running, a query searching a Lucene or Elastic index is checked by `RunStroomQlTool.misleading`. The check
reads the query's data sources with `StroomQlSources` (the first source, then each joined one, each with the
conditions Stroom pushes down to its own search), takes every enabled term of each at any depth, and asks
`MisleadingSearches.refusal` with that index's ip fields, its unqueryable fields and, for a Lucene index, its text fields' analyzers
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

The refusal starts "These searches would give a misleading answer: ", says what to use instead, names a joined data
source ("They are in the search of the joined data source …"), and adds "In StroomQL, EQUALS is = and NOT_EQUALS is
!=". Conditions Stroom runs on the joined rows, after the join, are not searches of an index, so are not checked. If the check itself fails, the query runs, because its own errors are
Stroom's to give.

The search is a non-incremental `QuerySearchRequest` with history not stored. It is polled every 500 ms until
complete or until Stroom reports errors. The rows are then fetched with `downloadSearchResults` as Markdown, asking
for every row the cap allows, with each row's stream and event ids. `StreamId` and `EventId` columns that are empty
(a data source without streams) are dropped. The workspace file records the data source's uuid as its source.

* **Arguments**: `query` (required), `timeZone` (optional, see Time zones), `out` (optional; a `.md` file in
  `/outputs/`).
* **Returns**: text: "Ran the query. The results are in <path>. Times are in <zone>.", any limit note, Stroom's warnings ("Stroom warned:
  …", a join's included, e.g. a joined source's own warnings or a row matching over 1,000 joined rows), and the
  table's profile. Joins (join lines, `where` subqueries) run in Stroom, as the Query pane runs them. The
  description names the joins guide (`stroom://guide/joins`; in the chat, `get_instructions guide=joins`).
* **Refuses**: a blank or over-long query (`maxQueryLength`); a misleading search, as above. Errors from Stroom are
  given as "The query failed: ..." or, once it had started, "The query started, but failed while it ran: ...".
  When the query failed before it started and the tool had added or lowered its limit, the error is the parser's
  for the query as written (`validateQuery`), not one naming the limit the agent never wrote. A failed query, and
  validate_stroomql's "not valid", get `StroomQlErrorHints`: "In StroomQL, every clause but show goes before
  select: …" (with the clause order) when one follows the query's own `select` (Sonnet wrote `where` after it in
  case 72), and "StroomQL uses sort by, not order by." for an
  unquoted `order by` (both seen with Haiku in eval cases 71 to 73, where Stroom's error named neither), and "Join
  lines go straight after from." for a join line after another clause, e.g. `where` (seen with Sonnet in case 71:
  "Unexpected token JOIN after WHERE"), and "between takes two values joined by and, e.g. …" for a `between` with
  no `and` before the next clause (seen with Sonnet in cases 72 and 73: `between "a,b"`, "Expected between and"), and "sort by and having take a
  field or a name given in eval or select, not a function, …" for a function call in either (case 75: "Syntax
  exception, expected string", "Expected field string").
* **Joins hint** (`ToolCallGuard`, `QueriedSources`): a successful run_stroomql, esql_query or search_events of a data
  source other than one queried before (in the chat, in the current question; over `/mcp`, in the session within
  the last 10 minutes) ends with one line, "Joining these rows with those of an earlier query (<sources>)?
  run_stroomql can join them in one query: from "<first>" join "<this>" as <alias> on <key> = <alias>.<key>, or
  from "<this>" join "<first>" as <alias> on <key> = <alias>.<key>. The joined source is read in full first, so
  join the smaller one. See stroom://guide/joins." (both orders, as the agent took the order its queries happened
  to run in, eval case 72) (`get_instructions guide=joins` in the chat; `join_hint` inside a JSON result), once for
  each pair of sources. The join line (`QueriedSources.joinLine`) quotes both names and makes the alias from the
  joined name (its initials, or one word cut to 8 letters, never a StroomQL keyword); the keys stay placeholders,
  and a source given by uuid is named from its result's `index` or `data_source` (`QueriedSources.named`), so the
  same data source given both ways counts once; the join lines are left out only when a uuid's result names no
  document (decided by the user, 2026-11-21: one fact, not steps; name for a uuid, 2026-11-21).
* **Notes**: the time limit is the session's query time limit (`stroom.ai.workspace.queryTimeLimit`, default 2
  minutes), not the tool time limit. The search is always destroyed when the call ends (`AgentSearches.destroy`),
  including on a timeout, so it never runs on after the call.
* **Lucene analyzers**: a Lucene index's query fields (`QueryService.findFields`) don't say which analyzer a field
  has, so `LuceneTextFields` reads them from the index's own field list (`IndexResource.findFields`, as the user,
  up to 2,000 fields), the TEXT fields only. If they can't be read, the check goes on without them. Found reviewing
  the tools: none were passed, so a CONTAINS on a keyword field ran and found nothing.

### `esql_query`

search · read-only · Use on the Elastic Index doc (`DataSourceAccess`, and again by `ElasticEsqlService`) · takes a
run slot · Classes: `EsqlQueryTool`, `stroom.ai.api.ElasticEsql`, `ElasticEsqlService`, `EsqlPipeline`

Runs an ES|QL query on an Elastic Index doc's index, as the user, through Stroom's Elasticsearch module, so a total,
top N or grouped question is aggregated by Elasticsearch rather than by scrolling every document through Stroom's
coprocessors, as `run_stroomql` does. The model gives `pipeline`, the commands after `FROM`; `FROM <index name>` is
written from the doc, so a query never names another index.

* **Commands**: `EsqlPipeline` reads the pipeline with a small lexer (strings, triple-quoted strings, backtick names,
  `//` and nested `/* */` comments, brackets) and splits it on top-level `|` only. Each stage's first word is its
  command: WHERE, EVAL, STATS, SORT, LIMIT, KEEP, DROP, RENAME, DISSECT, GROK and MV_EXPAND are allowed; FROM,
  ROW, SHOW, TS, ENRICH, LOOKUP JOIN, FORK, INLINESTATS, COMPLETION, RERANK and anything unknown are refused, as
  are a top-level `;`, an unterminated string or comment and unbalanced brackets. Each stage is sent on its own
  line, so a trailing `//` comment can't hide what is added.
* **Rows**: the lowest LIMIT after the last STATS or MV_EXPAND bounds the rows; with none, or one over the cap,
  `LIMIT <cap>` is added. The cap is `limit`, at most 10,000; the tool asks for 10,000 when none is given (only 100
  are shown, the rest are in the table), and a cut at 10,000 says to split by time window or narrow the query, as
  ES|QL has no offset (`stroom.elastic.search.esqlRowLimit` is the service's default for other callers).
  The rows are truncated when they reach it; then a second query (the pipeline without its trailing SORT and
  LIMIT, then `STATS stroom_total_rows = COUNT(*)`) counts the rows, or groups, in all.
* **Filter**: `where` (as `search_events` takes it, checked against the index's fields and `MisleadingSearches`)
  and `timeFrom`/`timeTo` (on the doc's time field) become Query DSL through `SearchExpressionQueryBuilder`, as for
  a search, sent as the ES|QL request's `filter`.
* **Streams**: not looked up, for speed (agents ask many queries in turn; the user's choice). A Stroom search
  drops hits whose stream the user can't see (View on its feed) or that is no longer in Stroom; `esql_query`
  doesn't, so its description says counts and sums come straight from Elasticsearch and may include documents from
  streams the user can't see or that have been deleted (results carry no note of it, the user's choice). Use on the
  Elastic Index doc is still checked, and the query audited.
* **Returns**: in the chat, Markdown: the query run, the first 100 rows, the zone the times are in ("Times are in
  …", and when the cluster refused the zone, "The cluster didn't take the time zone, so the pipeline's own date
  functions and dates without an offset (BUCKET, DATE_TRUNC, DATE_EXTRACT, DATE_FORMAT) work in UTC."), and a note of how many rows (groups) there are and whether they were cut short; over
  `/mcp`, the same as JSON (`columns`, `rows`, `row_count`, `limit`, `truncated`, `grouped`, `total_groups` or
  `total_rows`, `note`, the zone's fields and `time_zone_esql_note`). `timeZone` (see Time zones) sets the zone of
  `timeFrom`/`timeTo` and of the rows' times. Either way the rows are written to
  `/outputs/esql/esql-<time>.md` for the table tool. Audited as a Search event (`AiAuditLog.searched`).
* **Steered to**: the description, the server instructions and the chat prompt tell the agent to work out derived
  values and cut-offs in the query (EVAL for a conversion, WHERE after STATS for a threshold), not in its reply, so
  the rows are the answer (seen: Haiku summing bytes per user, then converting to MB and cutting at 150 MB in
  prose). `describe_data_source` gives an Elastic Index on a cluster taking ES|QL a line with an example
  pipeline from its own fields (`EsqlHints`); `run_stroomql` gives, after an aggregation on an Elastic Index that
  translates exactly (`StroomQlToEsql`), the equivalent pipeline and how many documents Stroom read; the table tool
  notes when 1,000 rows or more of an Elastic Index were read only to be grouped.
* **Refuses**: a doc that isn't an Elastic Index (a Lucene index or another data source: use `run_stroomql`); a
  command not allowed; an unknown field (with the index's fields most like it, and `describe_data_source`); a text
  field grouped by when it has a keyword sub-field (group by the sub-field); a cluster older than 8.14 (use
  `run_stroomql`); a query over the time limit (`esqlTimeout`, and the session's query time limit), which is
  cancelled; partial results (`allow_partial_results=false` from 9.1; 9.0 refuses it).
* **Stop and cluster errors**: a stop of the chat's run cancels the query in Elasticsearch within about 250 ms and
  ends the run stopped (not a tool error). A transient circuit breaker or a rejection (429) says the cluster is
  short of memory or overloaded, try again shortly (transient to the tool call guard); a permanent breaker, or the
  request breaker, says the query needs too much memory and what to ask for less of. Elasticsearch's own reason
  follows "Elasticsearch said:".
* **Steered away**: its description says ordered sequences (X then Y within N) are for `sequence_query`, not joins of
  STATS results.

* **Hints** (`EsqlQueryTool.refusal`, checked on 9.5.3): a keyword as a column name ("no viable alternative at
  input 'first ='", "mismatched input 'by' expecting") says it is reserved (rename or backquote it); "Unknown
  function [IF]" says to use `CASE(condition, then, else)`; a backslash in a "..." string, as before. An unescaped
  `@` in an RLIKE pattern (Lucene's "any string": `RLIKE "C@"` matched "Copy") adds a note to the result
  (`unescapedAtInRlike`), and the description warns of it and gives four short analysis patterns (baseline vs
  recent, first seen, COUNT_DISTINCT for spraying or scans, a cold-start guard). The description no longer says
  what the refusals and warnings say (a Lucene index or a cluster older than 8.14; a comparison on a multi-valued
  field giving null).

### `sequence_query`

search · read-only · Use on every data source (`DataSourceAccess`, and again by `ElasticEqlService` for EQL,
`ElasticKeyStreamsService` on every page of the streaming join, or the query service for StroomQL) · takes a run
slot · Classes: `SequenceQueryTool`, `EqlQueryWriter`, `EqlSequenceResults`, `StroomQlSequence`, `SequenceStroomQl`,
`KeyStreamSequence`, `KeyStreamJoin`, `StreamSequences`, `KeyGroupSource`, `KeyRanges`, `JoinKeys`, `JoinBudget`,
`StreamFilters`, `stroom.ai.api.ElasticEql`, `ElasticEqlService`, `stroom.ai.api.ElasticKeyStreams`,
`ElasticKeyStreamsService`, `ElasticDocs`

One tool for ordered sequences (it was `eql_sequence` until 2026-11-20). The model gives structured steps once; the
engine follows the data sources, and the result says which ran and why (`engine`, `engine_reason`):

* **Every step on an Elastic Index** (on one cluster): one EQL sequence in Elasticsearch, with everything below;
  or a streaming key-ordered join run in Stroom (see "Streaming join" below), where EQL can't run it: asked for
  (`engine=stream`), a step EQL can't take (`keysOnly`, `transform`), docs with different time fields, or a first
  step the pre-flight count says is too big for EQL (past 1,000,000 events, without `runAnyway`). `engine=eql`
  keeps EQL's refusals.
* **Any step on another data source** (a Lucene index, a view, any Stroom data source): a StroomQL windowed join
  run in Stroom (see "StroomQL windowed join" below), which takes two steps joined by their `by` fields only.

Finds ordered sequences of events across one or more Elastic Index docs on one cluster, as one EQL sequence in
Elasticsearch: "a device plugged in, then a file copied to it", "a clipboard copy then PowerShell within 2 minutes
by the same user on the same host", "20 failed logons then a success". Structured steps only, never raw EQL.

* **Offered** in the chat in the `sequences` group, and only to a user who can Use a data source (see
  "Usable" above): the chat sends `activate_sequences` (about 290 characters) until the model turns it on,
  and the full definition (about 9,800 characters as sent, 8,400 before the streaming join's `engine`,
  `keysOnly` and `transform` of 2026-11-23) only from then, while the chat's latest two questions
  use it. The guidance bullet names it "in the sequences group". The definition carries the rules and
  examples, and `until` and the `any_of` items list their properties (a condition's, a step's index, where, any_of
  and by), as strict providers may refuse an object schema with none. What a call can get wrong is also said by
  the refusal or result that meets it (a `?` in a pattern, nested and text fields, the versions for missing and
  runs, a Lucene index, docs on different clusters, the counts and `runAnyway`, the limits on `runs`, `fields` and
  `limit`, the caveat and which doc to review).
* **Arguments**: `steps` (2 or more, in order), each `{index, where, any_of, by, missing, runs, fields, keysOnly,
  transform}` (the last two for the streaming join only): the doc
  (uuid or name), conditions as `search_events` takes them plus `caseInsensitive`, `NOT_IN` and `length`, an
  optional `any_of` group (2 or more plain conditions, at least one must hold), its own join fields (same number and
  order in every step), `missing=true` for "and then NOT", `runs=N` (1 to 100), and up to 5 fields to show;
  `until` (`{index, where, any_of, by}`); `maxspan` (at most 24h); `timeFrom`/`timeTo`; `limit` (default 1,000, at most
  10,000, and for EQL at most as many sequences as fit 10,000 fetched events: 5,000 two-step ones, said in the
  result, as Elasticsearch refuses more ids than its max result window, seen in the 2026-11-20 benchmark; the
  streaming join: default 10,000, at most 100,000, all counted); `runAnyway`; `engine` (`auto`, the default, `eql`
  or `stream`); `out` (chat); `person`, `logonIds`, `personField`, `userField` (one person's sequences only, below);
  `timeZone` (see Time zones: the zone of `timeFrom`/`timeTo`, of the windowed join's conditions' date points, and
  of the result's times, which both engines' results state, `time_zone` in JSON).
* **One person's sequences** (`person` or `logonIds`, built 2026-11-24, `PersonScope` as `activity_timeline`'s):
  each step that isn't `keysOnly`, and `until`, gets an `IN` condition on its doc's person field (`personField`, the
  AGENTS doc's `## Fields` `person:`, the ECS convention `user.id`), else on its user field (`userField`, likewise)
  holding the logon ids; a doc that can't be restricted is refused (give `personField` or `userField`, or leave the
  step's doc out). The result adds `scope` (per doc: `index`, `by`, `field`, `field_from`, `values`) and, by logon
  id, `other_accounts`; in text, "Restricted to the person:". Close variants of logon ids are offered by
  `activity_timeline` only.
* **Acts done to the person** (a step's `role`: `actor`, the default, or `target`, built 2026-11-24): a `target`
  step is restricted by its doc's target field instead (`PersonScope.target`: the AGENTS doc's `## Fields`
  `target person:` or `target person id:`, else `target user:` for the logon ids, else the ECS convention's
  `user.target.id` or `user.target.name`), with the person's own acts on themselves left out (`NOT_IN` on the field
  they are read by as the actor, where the doc has it), e.g. a privileged group granted to them, then their own
  bulk download. `scope` lists the doc with `role: target` and its `by` ("done to them, by target person id
  (...)"). Refused: `target` without `person` or `logonIds`, on a `keysOnly` step or `until`, a role that isn't
  one, a doc with no target field.
* **EQL** (`EqlQueryWriter`): `sequence with maxspan=2m [any where stroom_eql_doc == "1" and ...] by a, b ...`,
  `![...]` for missing, `with runs=N`, `until [...]`. EQUALS is `==` (`:` case-insensitive), `like`/`like~` with
  `*`, `cidrMatch` for a CIDR on an IP field; IN `in`/`in~` or `cidrMatch`; CONTAINS/STARTS_WITH/ENDS_WITH like
  patterns; MATCHES_REGEX `regex`/`regex~`; ranges and BETWEEN; IS_NULL/IS_NOT_NULL `== null`/`!= null`; NOT_IN
  `not f in (...)` (`in~`, `not cidrMatch`); `length=true` as `length(f) > N`; `any_of` as `( a or b )`. A value with
  `?` and no `*` is matched exactly (`==`, or `in~` case-insensitive, as `:` reads `?` as a wildcard); a pattern
  holding a `?` is refused (EQL can't escape it: use the whole value or MATCHES_REGEX with `\?`).
  `stroom_eql_doc` is a runtime field `ElasticEqlService` defines from each doc's concrete indices (`_resolve/index`),
  as EQL can't name `_index`; it is left out with one doc.
* **maxspan**: from the user's words. Without it, in the chat, a plain-text form (`MAXSPAN_QUESTION`, one line that
  a wider window finds more pairs, mostly harmless, and runs slower), the configured default first
  (`stroom.ai.workspace.eqlDefaultMaxspan`, 5 minutes), then 1 minute, 15 minutes, 1 hour and another interval,
  typed; over MCP (or with the user away) refused with that question and the suggestions. The result says the
  window used and where it came from.
* **Checks and refusals**: every field (conditions, any_of, join fields, fields to show) against the doc's fields,
  with the closest names; a `text` field searched or joined on is swapped for its keyword subfield found in the
  mapping (not assumed `.keyword`; one without a normaliser first; the result's `field_notes` say so), or refused
  with its subfields and their types when it has none; a field in a `nested` field; a value its field can't take (a number field); join lists of different lengths; missing with runs;
  a doc the user can't use, named; docs on different clusters or with
  different time fields, naming each doc's (`ElasticEqlService.target`); a cluster older than 7.10, or than 8.8 for a
  missing step or 8.7 for runs.
* **Pre-flight** (`SequenceQueryTool.assess`): each step that must happen is counted with ES|QL up to 1,000,000
  (`| WHERE ... | LIMIT 1000001 | STATS n = COUNT(*)`) on 8.14 or later, else skipped saying so; skipped when the
  time range is 1 hour or less. A first step past the cap is refused before it runs ("narrow the window or the
  first step", or `runAnyway`); a later one past it is noted; a first step of 1,000 or more events and 10 times a
  later one's, with no missing step or runs, gets "put step k first if the order doesn't matter"; a step with fewer
  events than its runs is noted.
* **Doc condition**: with several docs, `stroom_eql_doc == "n"` (and its runtime script) only on a step whose
  conditions could match another doc's events (none of its positive conditions is on a field only its doc maps);
  each event's doc comes from its `_index`.
* **Running**: async in Elasticsearch within the query time limit: submitted with a 250 ms
  `wait_for_completion_timeout` (`keep_on_completion=false`), then polled in 250 ms slices; still running at the
  limit, it is deleted and refused with "narrow the window or the first step", adding that EQL is slowest with very
  many distinct join values (benchmark: 100,000 first-step events by 63,000 users timed out at 119 s, where a
  StroomQL windowed join of the same took 13 s; the engine isn't switched on Elastic); a stop of the chat's run deletes it
  and ends the run stopped (a stop during the counts doesn't run it). Cluster errors as `esql_query`'s: busy or
  overloaded, try again shortly; the sequence breaker or a permanent breaker, needs too much memory (narrow the
  window, shorten maxspan, put the more selective step first). `filter_path` keeps only the ids, `missing` and the fields fetched.
  A search that completes during a poll is read once more from its stored result when an event that happened has
  no fields: Elasticsearch 9.5.3 leaves every event's `fields` out of the answer to that poll (seen: 13 of 38 calls
  over windows wider than about a day had no times, and failed).
* **Returns**: the EQL, the window, the counts and advice, a summary per entity (sequences, first, last, shortest
  gap), the sequences (first 20 shown) written to `/outputs/eql/eql-<time>.md` (join keys; each step's time,
  fields, StreamId and EventId; the gaps; the span), a line that these are at least the sequences (EQL doesn't find
  every overlapping match), the "straight from Elasticsearch" caveat, how many sequences had no time on their
  events if any (`untimed_sequence_count`; their times and gaps are empty), a note when the limit
  cut them (the latest are kept), and a validated StroomQL review query per doc. Markdown in the chat, JSON over
  `/mcp`. Audited per doc as a Search event.
* **Review query** (`EqlSequenceResults.review`): the doc's hits by stream, `(StreamId = x and EventId in (a, b))
  or (StreamId = y and EventId = d)`, `sort by` the time field, then `select`; the join-key values in the window
  instead only when that won't fit 100 characters inside `maxQueryLength`, or the doc's index has no
  StreamId/EventId (filled from outside Stroom), saying which and that it shows those entities' events in the
  window, not only the hits. With several docs the model asks the user which to review (`ask_user`), then offers
  that one with `propose_query` or `propose_dashboard` (its event preview). For two steps with no until, missing,
  runs or conditions StroomQL can't write, the result also gives the same sequence as a StroomQL windowed join
  (`stroomql`, validated: `stroomql_valid`), which the user can save as a Query.
* **StroomQL windowed join** (`StroomQlSequence`, `SequenceStroomQl`): `from S as a join T as e on a.k = e.k
  [and ...] within <maxspan> after a.<time field> where <step 1 on a.> and <step 2 on e.> [and the time range on
  both] limit N select a.k, each step's time, StreamId, EventId and fields`, one row per pair (a step 1 event
  followed by several step 2 events gives a pair for each). Run through the query service as `run_stroomql` runs a
  query (`SourceType.API`, polled, `SearchProblems` for errors and warnings, each search removed by
  `AgentSearches`). Refused before anything runs, saying it needs Elastic Indexes: 3 or more steps, `until`,
  `missing`, `runs`; also `caseInsensitive`, `length`, IS_NULL/IS_NOT_NULL and MATCHES_REGEX (no exact StroomQL
  form), unknown fields, a data source with no time field, and a second step with no condition of its own (it would
  read every event in the windows: 14 to 400 s over 1 million events). Then one grouped count of the first step
  (`eval Sequence = 'all' group by Sequence select Sequence, count() as Anchors, countUnique(k) as Keys`): no events, the join
  isn't run; more than `MAX_ANCHORS` (100,000) first-step events, or more than `DENSE_MAX_ANCHORS` (10,000) when
  they have more than `DENSE_ANCHORS_PER_KEY` (100) per distinct join value, refused ("narrow the first step")
  with the join it would have run. The thresholds come from the 2026-11-20 benchmark (1 million synthetic events,
  one node): about 11 µs per joined row read plus 0.37 µs per joined row read per first-step event of the same
  entity. The result is as EQL's (per entity, the pairs table in `/outputs/sequences/`), with `step_counts` and
  `first_step_keys`, and the join itself is the review query, validated, to offer with `propose_query`.

* **Streaming join** (`KeyStreamSequence`, built 2026-11-23): neither side joined in Elasticsearch; each step read
  in join-key order through `stroom.ai.api.ElasticKeyStreams` (`ElasticKeyStreamsService`, beside
  `ElasticEqlService`; Use on the doc checked on every page, the call's query time limit and the chat's stop on
  every request, each request waited for in 250 ms slices and cancelled on a stop), and the sides merged in Stroom
  holding only the current key's events (`KeyStreamJoin`), so no side is bounded by the 10,000-hit window, the
  buckets limit, ES|QL's 10,000 rows or EQL's fetches. A step `keysOnly=true` (an entity condition at any time, e.g.
  a directory's users of team A; not limited by the time range; with `missing=true` the entities not in it) is
  read as keys: a composite aggregation on the join key paged by `after_key`. Every other step is read as events: a
  point-in-time search sorted by the key, then the doc's own time field (`numeric_type: date`), paged by
  `search_after`, with `StreamId`, `EventId` and the step's fields only, the point in time closed at the end. A
  step's conditions are a Stroom expression (`StreamFilters`: CONTAINS/STARTS_WITH/ENDS_WITH as `*` patterns,
  IS_NULL as `NOT = *`, NOT_IN as `NOT (IN)`, `caseInsensitive` the term's option; `length` refused, EQL takes it)
  made into Query DSL as a search's is.
  * **Steps in time** (`StreamSequences`): a sequence starts at a step 1 event (its `runs` events) and takes for
    each later step its earliest events at or after the step before's, never one event twice, all within `maxspan`
    of the first; a missing step has no event between its neighbours (to the window's end when last); an `until`
    event between the first and last ends it. As in EQL a later step 1 event before the next step's takes the
    sequence over, and one entity's sequences don't overlap; the count is exact for these rules (`COUNTED`), not
    "at least" as EQL's. `maxspan` is asked only when two or more steps are in time (or there is an `until`).
  * **Keys compare as Elasticsearch sorts them** (`JoinKeys`): text by code point (its UTF-8 bytes), numbers and
    dates by value, IPs by their 16 bytes; join fields of different kinds (text against a number) refused.
  * **Transforms** (`transform`: `lowercase`, `strip_domain` (`CORP\jsmith`, `jsmith@corp` are `jsmith`),
    `file_name` (a path's last part), applied in order, on a keyword join field): computed by Elasticsearch as a
    runtime keyword field (a Painless script of the same transform as `ElasticKeyStreams.Transform.apply`), which
    the query, the sort and the aggregation use, so the events stream in the order of the key that is compared
    (transforming in Stroom after a sort by the raw value would break the merge's order).
  * **Estimate first**: each step's events (`EqlQueryWriter.countPipeline`, up to the rows cap) and keys (`| STATS
    n = COUNT(*) BY <join fields> | SORT n DESC | LIMIT 3` with `countTotal`, the 3 commonest kept as examples) by
    `ElasticEsql.query`; a join whose estimate is 80% or more of a cap (10,000,000 rows read, 1,000,000 keys merged:
    `stroom.ai.workspace.joinMaxRows`, `joinMaxKeys`) is asked about in the chat (a form, "about N rows to read ...,
    W workers, about T (W workers put W times the search load on the cluster)", Run it / Don't run it), refused
    with the estimate over MCP unless `runAnyway`. Not asked when a step is pushed down (the estimate is then an
    upper bound). Past a cap, or at the time limit, every worker stops at its next page, keeping what was found,
    and the result says why (`ended_early`).
  * **Time limit**: the query time limit, unless the estimate (at `ROWS_PER_SECOND`, 20,000 rows a second a
    worker) says the join won't fit it: then in the chat the form offers "Run it, for up to <joinTimeLimit>", "Run
    it within <query time limit>" or "Don't run it"; over MCP it runs within the query time limit saying so
    (`time_note`), and `runAnyway=true` runs it up to `stroom.ai.workspace.joinTimeLimit` (15 minutes). One
    estimated past even the join time limit says it will likely end early. The result gives `time_limit_s`. The
    call's own deadline is the longer of the two limits; EQL, the counts and the StroomQL join keep the query time
    limit.
  * **Push-down**: the step that must happen with the fewest keys, if at most `joinPushDownKeys` (50,000), is read
    first (all its keys) and its keys become terms on every other step's sides, per worker range in batches under
    Elasticsearch's 65,536 terms limit (each batch a key range of its own, so keys stay in order); a `keysOnly`
    pushed step is then merged from those keys.
  * **Workers** (`joinWorkers`, 4; at most 16): the key space of the first join field cut into ranges
    (`KeyRanges`): from the pushed-down keys, else, past 50,000 estimated rows, from `sampleKeys` on the step with
    the most events (the key at which the running count passes each multiple of rows / workers, so ranges hold
    about the same rows, not an even cut of the alphabet); each worker reads every step restricted to its range and
    merges alone, as the user (`SecurityContext.asUserResult`), on its own threads; the results put together in key
    order. One call, one run slot; the workers share its query time limit; progress (`ToolProgress`) every 2 s
    across them all: rows read, keys merged, ranges done.
  * **Result**: `engine: stream`, `engine_reason`, each step (`read_as` keys or events, its estimate, keys read,
    `transform`), the estimate, `workers`, `key_ranges`, `pushed_down`, `rows_read`, `keys_merged`, `took_ms`,
    `sequence_count` (or `entity_match_count` when every step is `keysOnly`), `entity_count`, the table (as EQL's,
    in key order, the first `limit`; `/outputs/sequences/join-<time>.md`), the per-entity summary of those, the
    caveat, the review queries per doc (each doc's own time field). Audited per doc as a Search event.
  * **Diagnosis when nothing is found** (`diagnosis`): each step alone (its events and keys); for each later step
    sharing no key with step 1, a few of each side's commonest values (`No shared values between step 1 (Proxy:
    user) and step 2 (Endpoint: UserName), e.g. CORP\jsmith vs jsmith`) and the transform that would make them meet
    where one does (`KeyStreamSequence.bridge`), else a check with esql_query; when the entities meet but no
    sequence is in order, widen `maxspan`. Reporting only: the indexes are never changed (decided 2026-11-23).
  * **Data quality** (`data_quality`): the keys whose first timed step happened and whose last never did in the
    period, with both steps' indexes and a few examples: "it didn't happen" told from "the source may be missing
    events"; estimated (from the first step's keys) when the last step was pushed down.
  * **Limits held**: one key's events past 100,000 a step are counted, not held (`keys_cut`); a multi-valued join
    field is read by its smallest value in a scan (a sort's `min`), by every value as keys.
  * **Benchmark** (`TestBenchStreamingJoin`, opt-in): see "Streaming join benchmark" in DESIGN.md.

### `activity_timeline`

One user's (or entity's, or person's) activity timeline for a period from any sources (`ActivityTimelineTool`): two
levels, the detail spans (focus sessions of an application on a host where a source records window focus; pairs, from
a start to its end; spans bounded by a key; activity periods, the actions in none) and the activities rolled up from
them (what the user was working on), each a table `gantt_chart` draws as it is; the actions with where each was
attached, each day's active time, the window lifetimes, the events by their stream and event ids and how each was
classified, and data-quality flags (parts 2, 2b and 2c of DESIGN.md's "Activity timelines, streaming joins and
skills"). Group `timelines`; offered to a user who can use an Elastic Index.

* **Sources** (`TimelineSourceChoice`): `sources` as objects (`index` and, optionally, the fields and `roles`), index
  names, `all`, or `ask`/omitted. Omitted in a chat, a form asks: with a dashboard attached, only its data source,
  all supported, or the user's own words; without, all supported (the default) or their own words. Their words are
  matched to each Elastic Index's name and documentation (a word more than half the indexes have lists them but
  doesn't choose them) and shown to confirm as a multi-choice. Over MCP, all supported, said in `sources_chosen`.
  Supported: every Elastic Index the user can use.
* **Automatic sources** (no `roles`): each index's user field is the source's `user`, else `userField`, else the
  AGENTS doc's `## Fields` (`- user: Initiator.Id, user.name`, read by the tool itself, `InstructionRules.fields`;
  `- person: user.id` for the person field, below),
  else the bundled field conventions' (`user.name`, `UserId`), else a field its documentation names on a line about
  the user, else asked in a chat form; the host, element, Action and event code fields the same way (`host`,
  `elementField`, `actionField`, `eventTypeField`; `## Fields` keys `host`, `element`, `action`, `event type`;
  `person` or `person id` for the person field).
  Each is probed first (its first 200 events of the user): none, also without the domain and in lower case for a
  text field (said as `user_transform`), and it is listed in `left_out` and left out (where more than one source
  was asked for); the probe's keyword fields also find an element field (90% of its values EventDetail elements).
  With `entity` alone this is as before.
* **Whose events** (`PersonScope`, built 2026-11-24): `person` (a person id, the same for all of a person's accounts,
  as processing decorates each event with it) is read in each index by its person field: `personField` (the
  skill's), else the AGENTS doc's `## Fields` (`- person: user.id`; the key `person id` too), else the convention
  (ECS `EventSource/User/UserDetails/Id` to `user.id`; the flat convention has none); an index without one falls back
  to `entity` and `logonIds` by its user field. `logonIds` are more logon ids of the person (forms differ by source:
  `alex.chen`, `CORP\achen`, `adm-achen`), read together, each account counted (`accounts`). Each source says
  `by` (by person id or by logon id); by logon id, the result says other accounts of the person (an admin or service
  account) aren't included unless named (`other_accounts`). For a source with none of the logon ids, close variants
  the index holds (the domain added or removed, another case) are offered in `left_out`'s text, never merged
  without the user. A source with `roles` is read by its `user` field, so by logon ids only (`entity` or
  `logonIds`); with only `person` it is refused.
* **What was done to them** (`includeTargets`, default false; `TimelineTargets`, built 2026-11-24): one more read of
  each source (and of those left out for holding none of the person's own events), its events whose target field
  holds them (`PersonScope.target`: `targetPersonField`, else the AGENTS doc's `## Fields` `target person:`, else
  ECS `user.target.id`; else, with logon ids, `targetUserField`, `target user:`, ECS `user.target.name`), the
  person's own acts on themselves left out (`NOT` the actor field holding them; an event with no actor, a failed
  logon, is kept): a group or access granted, a password reset, MFA changed, a failed logon against the account.
  Each is labelled by its type (the Action, else the element, else the event code) and who did it (the actor's
  logon id, else person id, else "no account"), and listed in a lane of its own per source (`done to them:
  <source>`) in `spans.csv` (an instant each, kind `target`) and `actions.csv`; both tables' `role` column says
  `actor` or `target`. Never in the person's activity: their sessions, spans, activity periods, pairs,
  characterisation, counts and `rows` are the same with or without it. Audited as a search of each source. A source
  with no target field is said in `left_out`, not read.
* **Classification** (`TimelineClassification`, `EventXmlClassifier`): an automatic source's events by their Events
  XML (`TimelineEventXml`: by StreamId and EventId, as `fetch_events` reads them; View Data checked; audited as
  streams viewed; at most `timelineMaxXmlEvents`, 50,000, in time order): Authenticate Logon, ScreenUnlock, Connect,
  Reconnect make the user active (`idle_end`); Logoff, ScreenLock, Disconnect idle (`idle_start`); Process
  Execute and Startup open, Terminate and Shutdown close; any other element an action of that kind. The type is the
  TypeId, else the element and Action; the keys (correlation keys, widened 2026-11-24) are every leaf of the event:
  under the element by its path below it (`ProcessId`, a `Data` entry by its name as `Data[SessionId]`,
  `Open/Data[Category]`), the EventSource's and EventDetail's other leaves by their whole paths
  (`EventSource/Client/IPAddress`), never the time, user or type; and the index's fields returned with the event, by
  their names (`Classified.withKeys`). The label is the application (a Process's Command), else the object (Title,
  Subject, URL, Path, Name). A Network event's object is its destination device's HostName, else its IPAddress; Send
  and Receive keep a more telling leaf (a subject, a URL) first, then the destination (`TO_DESTINATION`); a Source or
  Destination device's name is never otherwise the object. The user and time always come from the
  index. Where the XML is gone or there is none: the element and Action fields; else the event codes, asked of the
  model once (`EventCodes`, a side call of up to 100 codes, roles checked in code), confirmed in a chat form and
  kept per index (`/outputs/timelines/event-codes/<uuid>.json`, the user's workspace where there is one); over MCP
  used unconfirmed, not kept; with no model, actions named by their code. A skill's Event chains roles and a
  source's `typeRoles` override a type's role; `ignore` leaves its events out. The result counts each source's
  events by route (`classified_by`) and `events_xml` (read, gone, past the cap with a narrower period asked for).
* **Reading** (`TimelineReader`, the streaming join's machinery): each role of a source with roles, or the whole of
  an automatic source, is an `ElasticKeyStreams.Side` of its Elastic Index (a role's conditions a Stroom
  expression, `StreamFilters`, as `sequence_query`'s steps' are), restricted to the one entity by a terms condition
  on the source's user field (`transform` applied to it and the entity, so `CORP\jsmith` and `jsmith` are one), read
  in time order by a point-in-time scan through `KeyGroupSource` (one key), within a `JoinBudget`:
  `timelineMaxEvents` (200,000), the query time limit and the user's stop. Each source audited as a search. Elastic
  Indexes only, each with a time field.
* **Focus sessions** (`TimelineSessions`, in code): per host, an interval on a process starts at a `focus` on it and
  ends at the next focus (any process), an `idle_start`, or the idle cap (no event of any source on the host for
  `idleCap`, default `timelineIdleCap`, 30 minutes), marked `end_mark` ("ended by the idle cap") and `ended_by cap`;
  an `idle_end` resumes the process focused before (`start_mark` "resumed after idle"); an action on a process with
  no interval running starts one (`start_mark` "no focus event seen"); one still running at the period's end ends
  there, marked. Window lifetimes from `open` to `close`, paired by the `window` field, else the process.
* **Pairs** (`TimelineSpans`, `PairRule`), applied in order, each event used by the first that takes it: a skill's
  Event chains, a source's `pairs` (`{start, end, by, label}`), the schema's (Process Execute to Terminate and Startup
  to Shutdown by ProcessId, else Command; Authenticate Logon to Logoff, "logged on", and ScreenLock to ScreenUnlock,
  "locked", by the host), and those inferred (`PairInference`): per source, two of its 40 commonest types that, for
  the same key value (a key path 80% of both hold that is informative, `PairInference.informative`: some value held
  by both types and not fixed by the lane; or the host alone), run A, B, A, B from an A, each B within 24 hours,
  for 90% of keys, the counts within 0.8 of each other, over 3 keys (or 3 spans of one key); borderline (50% and 2
  keys, or clear but lopsided) listed with their figures in `borderline_pairs`, not used; a type in one pair at
  most. An end closes the latest open start of its key; a start with no end runs to the lane's first gap past the
  cap, an idle start or the period's end, marked; `unpaired_start` and `unpaired_end` flagged for a pair of actions.
  A pair's start and end, and an action in its span with its label (`by pair`), are the pair's. Each pair's origin,
  alternation, spans and unpaired starts and ends are in `pairs`.
* **Spans bounded by a key** (`KeySpans`, kind `key`, built 2026-11-24): from the first to the last of the user's
  events sharing a key's value, even where no event starts or ends it, split where the user is idle past the cap
  across all sources; no events taken from other spans; left out of the daily totals. Automatic only for a
  session-like key: held by events of two or more types, repeating on 3 or more values (`PairInference.MIN_KEYS`),
  saying more than the lane, 80% or more of its values making one stretch only (`ONE_RUN_SHARE`), and not a used
  pair's key (a VPN session's id, where a domain or an application recurs all day). Other keys (a destination domain,
  an application) only when a source's `spanBy` names them (an index field, or a key path such as
  `Data[SessionId]`), labelled by the value; a found key's spans are labelled by the key and value (`SessionId
  8614`).
* **Activity periods**: the actions in no focus session or pair, in a lane (the host, else the source's name),
  labelled by the application, else the object's place (a URL's host, a path's last part), else the action's kind;
  one ends at the next action where the label changes, at its last action where the next is past the idle cap, or at
  an idle start on its host; a lone action is a marker. They show when the user was active in a source, not what had
  focus (`activity_periods_show`). Their actions are attached `by activity`; they count in the daily totals and the
  active time (`rows`), pairs don't.
* **Actions attached**: an action on a host to the focus session it falls in, `by process` where it names that
  session's process, else `by time`; an action naming no host to the session of any host active then (the latest
  started), `by time`; else to a pair or an activity period, as above.
* **Data-quality flags** (counted, three examples each; the spans are still made): `open_without_close`,
  `close_without_open`, `idle_start_without_end` (activity after an idle start with no idle end), `idle_end_without_start`
  (after activity; one before any activity is the period's start), `focus_without_open` (a source with `open` only),
  `ended_by_cap`, `source_silence` (a source of window focus with no events on a host for longer than the cap, less
  the host's idle periods, while 3 or more events of other sources show the user active there), `out_of_order`
  (more than a minute before an earlier event id of its stream), `future_dated` (over 5 minutes after now),
  `clock_disagreement` (two sources' events naming the same object on a host within 10 minutes, the middle of 3 or
  more differences past 2 minutes), `two_focused` (two focuses on a host within a second), `unpaired_start`,
  `unpaired_end`.
* **Characterisation** (`ActivityCharacterisation`, with `characterise=true` or `view` activities or both), over the
  focus sessions and activity periods, and (2026-11-24) pair spans that aren't session-level (a session-level pair
  starts or ends with an Authenticate action that makes the user active or idle, e.g. logged on) and key spans, each
  an item of its own (`p<n>`, `Span.itemId`, apart from the sessions' `s<n>`) placed by terms or by the model and
  rolled up in the activities: pass 1 in code, a graph of the spans linked by an artifact they share (a
  title's parts, its application's suffix such as " - Word" dropped where every title of the application has it; a
  file's name; a URL's domain and first path segment; normalised so "Report v2.docx" and "Report.docx" are one), by
  significant terms they share (in 2 or more spans and at most half), and by being close in time (2 minutes, each
  next span to start); its clusters by `Leiden` (the chart analytics' algorithm, ported from the generic
  visualisations, deterministic) of 2 or more spans with an artifact or term link are the anchor activities, with a
  profile (top artifacts, key terms, applications) and a label from it. Pass 2, the model: free-text items (an
  action's `text`) and spans the graph left out with words, unless they name an anchor's artifact or two of its
  terms (placed in code), go in batches of `characteriseBatchItems` (50), each item's text cut to
  `characteriseExcerptChars` (500), with its time, host, source, span and the one before, and the activities'
  profiles; the answer, a JSON list of `{item, activity, name, confidence, reason}`, is checked in code (an activity
  not offered, a missing item or a confidence that isn't high, medium or low is `unclear`; low is `unclear` with its
  reason; `new` makes an activity where two or more items share its name); a batch that can't be read is asked once
  more. Batches run in parallel up to `joinWorkers`; at most `characteriseMaxItems` (1,000) a run unless the user
  agrees (a form in the chat, `runAnyway` over MCP). One more call names the activities from their evidence. Answers
  and names are cached in the workspace by item text and profiles (`characterisation-cache.json`), so a re-run asks
  only for new items. The activities roll up from their spans: an activity's spans within the idle cap of each other,
  across sources and hosts, merge into one interval, split at midnight in the user's zone.
* **Side calls** (`ModelSideCalls`, from the call's context): in a chat, the run's model through
  `ChatModelSideCalls` (`ModelStreamer.callUnseen`, as step and compaction summaries; tokens added to the run's
  usage; stopped with the run); over `/mcp`, the client's model by MCP sampling (`McpSampling`, where the client says
  it can; the wait not counted against the call's time limit, at most the form timeout); otherwise none, and the
  loose items are left unclassified (and event codes unasked), the result saying so. The chat's model sees only the
  outcome.
* **Corrections**: `{move, to}`, `{merge: [...]}`, `{rename, label}`, applied last and kept in the workspace
  (`corrections.json` beside the tables), so a re-run keeps them; one naming what isn't there is said and left out.
* **Views**: `view` `detail` (the default: `spans.csv`, label `label`, lane `lane`, or `source` with `lanes`
  source), `activities` (`activities.csv`, label `activity_label`, lane `day`, or `activity_label` with `lanes`
  activity), `both`. The result's `view` gives each chart's gantt_chart arguments, `too_dense` where a view has more
  than 200 bars or 20 lanes (draw one day, or one activity by filtering the table), and the dashboard `timeline`
  kind's columns.
* **Learning** (`TimelineLearning`, `EventChains`): `skill` names the skill being run (the readable one nearest the
  chat's context); its `## Event chains` lines (`- <index>: pair <start> -> <end> by <key> label <path>`, `-
  <index>: <type> is active|idle|open|close|action|ignore`, `any` for every source) are applied first. What was
  learned (the clear inferred pairs, and the source's own `pairs` and `typeRoles`) that the skill lacks is in
  `learned`: with Edit on the skill's doc, the skill with the lines added is written
  (`skill-event-chains.md`) and the agent offers `propose_skill action=propose doc=<uuid> draft=<path>`; with no
  skill, where the user can create a Documentation doc in a context folder or a source index's folder, a new skill
  from the template with these lines; otherwise kept for the chat (`event-chains.md`), the result saying who could
  record them. Borderline pairs are listed for the user to confirm. `check_skill` checks the lines (an entry each, a
  usable index) and, by `TimelineChainCheck`, against the index's last 7 days (any user, 5,000 events and their
  XML): types seen, the key held, still alternating (90%) per user and host.
* **Arguments**: `entity` (no longer required: `entity`, `person` or `logonIds`), `person`, `logonIds`,
  `personField`, `sources` (each `index`, `name`, `user`, `transform`, `host`, `process`, `title`,
  `window`, `object`, `text`, `elementField`, `actionField`, `eventTypeField`, `roles` with `focus`, `open`,
  `close`, `idle_start`, `idle_end` as `{where, any_of}` and `actions` as `[{kind, where, any_of}]`, `pairs`,
  `spanBy`, `typeRoles`), `userField`, `timeFrom` (default `now()-7d`), `timeTo` (default `now()`), `idleCap`, `view`,
  `lanes`, `skill`, `characterise`, `corrections`, `runAnyway`, `includeTargets`, `targetPersonField`,
  `targetUserField`, `timeZone`.
* **Returns**: in a chat, Markdown (the sources with their user fields and how their events were classified, the
  spans by kind, the pairs and borderline pairs, active time by host or source and application or place, the flags
  with examples, the activities with their evidence, the tables, the view's gantt_chart calls, what was learned);
  over MCP, JSON (also structured content): `entity`, `time`, `idle_cap_minutes`, `sources_chosen`, `sources`
  (events, `user_field`, `by` (by person id or by logon id), `accounts` (by logon id: events per account),
  `user_field_from`, `user_transform`, `classified_by` each), `other_accounts` (by logon id), `left_out` (with the
  close variants of the logon ids found), `events_read`, `none_in_period` (where no source had the user's events in
  the period: each source's `events`, `first` and `last` overall, from one ES|QL `MIN`/`MAX`/`COUNT` each, or
  `not_known` with why; also in the refusal's text, so the agent can move the window),
  `events_ignored`, `events_xml`, `ended_early`, `session_count` (focus sessions), `hosts`, `uncertain_sessions`,
  `actions`, `unattached_actions` (in no session or span), `spans` (by kind), `activity_periods_show`, `pairs`,
  `borderline_pairs`, `columns`/`rows` (host, process, sessions, active minutes, the most first, of focus sessions
  and activity periods), `data_quality`, `files`, `gantt` (the sessions table), `view`, `characterisation`
  (`activities` each with `label_is: inferred` and `found_by`, `sessions_in_no_activity`, `items`, `items_placed`,
  `model_calls`, `items_asked`, `items_not_classified`, `unclear` with reasons, `modularity`, `notes`, `gantt`),
  `learned` (`event_chains`, `borderline_to_confirm`, `can_record`, `file`, `offer`), `done_to_them` (with
  `includeTargets`: `events`, `note`, `sources` each with `by`, `events`, `kinds` and `done_by`, `left_out`,
  `ended_early`, `listed`: the first 20 with time, type, by, host, object and ids; in a chat a "Done to them"
  section), `time_zone`, `derived`, `caveat`. Tables under `/outputs/timelines/<entity>/`, every run: `spans.csv`
  (span, start, end, label, lane, kind focus/pair/key/activity/target, activity, activity_label, host, source,
  events, how, uncertain, role), `activities.csv` (activity,
  activity_label, day, start, end, active_minutes, spans, lanes, evidence; header only when not characterised),
  `sessions.csv` (the focus sessions; `gantt_chart` with start, end, label `process`, lane `host` draws it as it
  is), `actions.csv`, `events.csv` (with `type` and `classified_by`), `daily.csv`, `windows.csv`, `quality.csv`,
  and with characterisation `items.csv`. Times in the tables are UTC. `timeline.json` (`TimelineSnapshot`: entity,
  period, zone, view, lanes, sources, whether characterised, when built) is written beside them, for
  `propose_timeline_dashboard`.
* **Refuses**: none of `entity`, `person` or `logonIds`; no source with the user's events and a user field ("No
  source holds ...": each left out is said, with each source's first and last events and count); a source with
  `roles` and only `person`; a source that isn't an Elastic Index, or whose index has no time field; a field the
  index doesn't have; a user field that can't be one key (a text field with no keyword subfield, a
  transform on a number); a role that isn't one, or with
  no conditions; a `typeRoles` role that isn't one; a bad period, `idleCap` (1 minute to 24 hours), `view` or
  `lanes`; the user declining the sources form.

### `propose_timeline_dashboard`

Content · read-only · chat only · timelines group · Classes: `ProposeTimelineDashboardTool`,
`TimelineDataConnection`, `TimelineDashboardConfig`, `TimelineDashboardPlan`, `TimelineDashboards`, `UserActions`
(`create_timeline_dashboard`)

Puts a timeline the chat built on a dashboard, which can't read the chat's workspace: one "Create Data Connection
and dashboard…" button. When the user clicks it, Stroom, as the user, writes the table as it is as a CSV Data
Connection (stroom#6010) in the folder the user chose, and beside it the timeline chart as a Visualisation drawn
with ECharts (the generated `timeline` kind, so `propose_visualisation` can later replace it); then it opens Stroom's
New Dashboard dialog in that folder for a dashboard of them. Writing again (a second click, e.g. after the dialog was
cancelled) rewrites the same documents, found by name in the folder.

A tool of its own, in the timelines group: a new action of `activity_timeline` would run the whole timeline again
(its searches and side calls) for what its tables already hold, and `propose_dashboard` reads data sources, never
the workspace.

* **The Data Connection** (`TimelineDataConnection`): the table's CSV held in the document, its header the
  columns, typed: `start` and `end` Date (ISO 8601 instants, UTC), `events` Long (spans), `active_minutes` Double
  (activities), the rest Text (`day` stays text, as it is a lane's name). Its time field is `start`, so a
  dashboard's time range keeps the rows starting in it. Named `Timeline <entity> <first day> to <last day>
  (<table>)` in the user's zone (a period ending at midnight ends the day before; one day named once; characters a
  name can't hold made `_`), e.g. `Timeline sam.patel 2026-10-20 to 2026-10-26 (spans)`. Its description says it is
  a dated snapshot: whose, which table, the period and zone, when it was built and from which sources, and that it
  isn't updated. A Data Connection in the folder of an earlier run for the same entity and table (any period) is
  replaced: rewritten and renamed, with its generated chart.
* **The dashboard** (`TimelineDashboardConfig`): a query of the Data Connection (runs when opened) over the
  timeline's period (the dashboard's time range); the timeline chart of the table: start, end, and for the spans
  label `label`, lane `lane` (host; `source` if the run's lanes were by source), colour `activity_label` where the
  run found activities, else `kind`; for the activities label `activity_label`, lane `day` (`activity_label` if by
  activity), colour `activity_label`; and the table, each column formatted as typed (Date Time, Number, Text), in
  `start` order, with a table selection filter on the chart: a click on a bar keeps the rows starting within its
  interval (`start` between the selection's `from` and `to`), every row while nothing is selected.
* **Permissions and privacy**: offered only if the user may create a Data Connection and a Visualisation in the
  folder, and edit a Data Connection it replaces; the result says when they can't create a dashboard there (they
  choose another folder in the dialog). The Data Connection holds the user's activity with the folder's
  permissions: the result, and the button's warning, say anyone who can view the folder can read it.
* **Arguments**: `entity` (as `activity_timeline` was given it), `table` (`spans` or `activities`), optional
  `folder` (UUID or Explorer path; omitted, a form asks, the attached dashboard's folder suggested).
* **Returns**: text saying what the button writes, where, the chart's columns and the privacy note, plus a
  `create_timeline_dashboard` action holding the folder (`doc`), `name`, the `plan` (the table's workspace path and
  SHA-256, its rows, the period, the description, the chart's columns, the Data Connection replaced) and a summary.
  The click is refused if the table has changed since (the timeline was built again).
* **Refuses**: outside a chat's run; no `timeline.json` for the entity (no timeline built in the chat, or one
  built before it was written); no table, or one of other columns; no rows (activities: found only with view
  activities, both, or characterise); more rows than a Data Connection reads (100,000); a folder the user can't
  see, or declines to choose; no Create in the folder; no Edit on the Data Connection it would replace; ECharts not
  installed, or no Use on it; a chart the table can't draw (`ChartCheck`).

### `compare_activity`

search · read-only · timelines group · Use on every index (`DataSourceAccess`, and again by Stroom as each
aggregate runs); the peers' groups read as the data viewer reads events (View Data, and the stream store's check on
each stream's feed) · takes a run slot · Classes: `CompareActivityTool`, `ActivityProfiles`, `ActivityScoring`,
`ActivityStatistics`, `AggregateQuery`, `AggregateText`, `AggregateRunner`, `IndexAggregates`, `CachedAggregates`,
`PeerGroups`, `ActivityCategories`, `ActivityAccess`, `ActivityLocation`, `CompareResult` (package
`stroom.ai.impl.tools.compare`); `PersonScope`, `TimelineFields`, `TimelineEventXml` and `Leiden` from the timelines

Compares one person's activity in a window with their own baseline and their peers' (built 2026-11-24; DESIGN.md's
"Insider risk"), from index aggregates only, never the events, so 12 months is a few thousand numbers. Findings are
indicators with their evidence and an alternative explanation, never a conclusion about the person: every result
says so first (`CompareResult.INDICATORS`). Offered to a user who can use an Elastic or Lucene index. No permission
of its own: the tool policies decide who may compare a person's activity.

* **Whose events** (`PersonScope`, as `activity_timeline`'s): `person` (the person id) by each index's person field
  (`personField`, the AGENTS doc's `## Fields` `person:`, the ECS convention `user.id`), else `logonIds` by its user
  field (`userField`, likewise); each source says by which, with its field and where it came from. One of `person`
  or `logonIds` is needed.
* **Periods**: `windowFrom`/`windowTo` (a date, ISO 8601 time or date expression such as `now()-30d`; default today
  included; `windowFrom` default 30 days before), `baselineFrom`/`baselineTo` (default the 180 days before the
  window's start). A date alone (`yyyy-MM-dd`) is a whole day in `timeZone`: a start from the start of that day, an
  end included, to the start of the next day, so `2026-06-01` to `2026-06-30` is all of June and a one-day window is
  the same date twice; a time or expression is an exclusive end (since 2026-11-24, after Haiku's date-only calls:
  before, an end date was exclusive). A `baselineTo` date of the window's first day is read as the baseline ending
  the day before, said in the notes (models pass the window's start as the baseline's end). The baseline ends by the
  window's start, and the two together are at most 3 × 366 days. `unit` (`day`, `week`, the default, or `month`) is what the window is compared in. Days and hours
  are in `timeZone` (the person's).
* **Sources**: `sources` (names or uuids), default every Elastic and Lucene index the user can use; each one with no
  time field, or with no person or user field for the person, is listed as left out with why, and refused only if
  none is left. `sumFields` are numeric fields summed a day (bytes sent); `breadthFields` are fields whose distinct
  and new values are counted a day (default the host, application and object fields `TimelineFields` finds, by the
  AGENTS doc's `## Fields` or convention, countable: not a date, nor analysed text on Elasticsearch).
* **What it asks** (`ActivityProfiles`, run by `IndexAggregates` as the user): per source, the person's events by
  day (events, first and last time, sums, distinct counts of each breadth field), by hour (active hours, the
  hour-of-week profile a month), each breadth field's values with their first and last times (novelty, a row a
  value), and everyone's events and people by day (coverage and source-wide shifts). ES|QL `STATS` on an Elastic
  Index, StroomQL on any other (`AggregateText`: every name and value quoted); a query bucketed by time is cut into
  calendar months in the person's zone, run in parallel up to `joinWorkers`, each search Stroom runs removed once
  read. Hour buckets come back in UTC and are put in the zone in code; day buckets use ES|QL's `time_zone`, else (a
  cluster that refuses it) a fixed offset, said (`ZONE_NOT_APPLIED`); StroomQL formats its days in the zone. At most
  10,000 rows a query slice (`ActivityProfiles.MAX_ROWS`): one cut short is said and its figures are "approximate".
  Each source audited as a search, with its first aggregate and how many more.
* **Cost**: per source over N months about 3N queries, plus 1 a breadth field, N with peers (plus 1 for the people
  active, and up to 200 event reads, cached), N for categories and N for location (each on by default where the
  source has the field: the description says about 1 more query a month each), and 2 for an access field. 12 months
  of one source with 2 breadth fields, peers, categories and location is about 75 queries, in parallel up to
  `joinWorkers`; 5 sources about 375. The time limit is 4 times the query time limit.
* **Re-runs** (`CachedAggregates`): the time-bucketed aggregates are kept in `/outputs/compare/<person>/
  aggregates.json`; the same query asked again is answered from it for the days it holds, the last held day asked
  again (it may have been part of a day), and only the days since run (about 1 or 2 slices a query). Aggregates not
  bucketed by time (values' first and last times, the people active) always run.
* **Checked first** (`ActivityScoring`): the days the source holds any data at all (a gap, e.g. retention, isn't
  scored); `leave` (each `yyyy-MM-dd..yyyy-MM-dd` or a date, from the question, skill or AGENTS doc: not scored);
  `exceptions` (each `yyyy-MM-dd..yyyy-MM-dd: reason`, e.g. an approved migration: not scored, and said with the
  reason); quiet runs of 7 days or more (reported, not scored); and the whole source's volume, everyone else's
  events per person a day with the person's own left out, so a shift everyone shares is told as the source's.
* **Scoring** (`ActivityScoring`, `ActivityStatistics`; each a pure function checked by hand-computed cases):
  * Features a day: events, active hours, first and last activity (minutes in the zone), each sum, each breadth
    field's distinct and new values (none counted new in the first 28 days), each category's events.
  * **Windows**: a day (`unit` day) against the 8 same weekdays before it; a week or month against the baseline's
    windows of the same length (each needing 3 scored days). A finding at robust |z| ≥ 3.5 (median and MAD; the
    mean absolute deviation when the MAD is 0; a constant baseline scores ±10; no score on fewer than 5 baseline
    values) whose difference is at least half the baseline's level, or 60 minutes for a time. z is capped at 50 in
    ranking and shown as "over 50". Runs of consecutive window findings of a feature the same way are merged into
    one.
  * **Change points**: the split of the feature's weekly means leaving the least absolute deviation from each
    side's median (2 weeks or more each side), sized in the sides' pooled spread (3.5 or more) and at least half
    the level before (an hour for a time); dated to the day within a week either side where the shift shows, the
    first day already at the new level (not a quiet weekend before it).
  * **Drifts**: a Theil-Sen slope of monthly means (medians for times) over 4 or more months of 5 or more days,
    at least half the starting level (an hour for a time); a drift from zero, with no least change, isn't sized.
  * **Rhythm**: each window month's hour-of-week profile against the baseline months' summed (total variation
    distance), scored against how far each baseline month was from the rest; needs 5 baseline months.
  * **Values started and stopped**: a breadth field's values first seen in the window, and those of the baseline
    not seen since; listed where the field has at most 50 values.
  * **Source-wide**: a window finding or change point of events, active hours or a sum is the source's when
    everyone else's moved the same way past the threshold (a change point within 7 days) by at least half the
    person's change relative to its level; listed apart ("Explained by the source", at most 5 shown), not ranked
    with the person's. Other features (times, breadth, novelty, categories) aren't checked against the source.
* **Peers** (`peers`, default false since 2026-11-24, asked for when the question compares the person with their team; on in the leaver and privileged user review templates; off, the notes say how to turn it on; `PeerGroups`): the people active in the source (one aggregate by the person or
  user field), then each one's group read from one of their events' Event XML
  (`EventSource/User/UserDetails/<peerElement>`, `Organisation` by default, as processing decorates it there and
  not in the index; `Unit`, `Group` or `Role` allowed; `Nationality` or any other personal characteristic
  refused). At most 200 people a call (`MAX_PEOPLE`): past it, a chat form asks to read the first 200 or compare
  without peers; over MCP the first 200 are read and the result says the group may be missing members. The map of
  person to group is kept in `/outputs/compare/peer-groups.json` and reused. Then the group's days in a second
  round (`IN` their ids), each day's median taken in code; a finding where a window week's events a day, as a
  multiple of the peers' median, part from that multiple in the baseline weeks (z ≥ 3.5 and 0.5 apart), and where
  the whole window's events a day are at least twice the peers' median and z ≥ 3.5 against their spread (a curious
  viewer's volume above the team's). Fewer than 5 peers
  with events: not compared, said. Needs View Data and StreamId and EventId fields; without either, no peers, said.
* **Categories** (`categories`; `ActivityCategories`): each category's events a day. On by default (since
  2026-11-24, after models left `categoryField` out and missed the job-site and personal cloud signals) where the
  source has a category field: `categoryField`, else the AGENTS doc's `## Fields` `category:`, else the flat
  profile's `Category` or an ECS-style `*.category` field (not ECS's `event.category`, the event's own kind; neither
  field convention maps a category, so these names are in `CompareActivityTool`); a countable field. The result's
  notes say it was on by default, by which field and where it came from, and that `categories: false` turns it off.
  From the field, events are counted row by row by the first breadth field's value and the field's category (a value
  of more than one category counted in each; by the category field alone where there is no breadth field), the
  site's list and corrections overriding a value's. `categories: true` on a source with no category field: the first
  breadth field's values (personal cloud storage, webmail, job search, anonymiser, archive or encryption tool, remote
  access, file sharing, social media, else other) classified by the model in side calls; never by default (`ModelSideCalls`, as `activity_timeline`'s; batches of 100, at most 1,000 values a call, the
  commonest first; answers checked against the fixed categories; values treated as data), cached in
  `/outputs/compare/categories.json` and marked inferred. `categoryList` (the site's own, `value=category`, from the
  AGENTS doc or a skill) overrides the rest, then `categoryCorrections` (kept for re-runs). Each value's category
  and where it came from is in `<source>-categories.csv`; each category's events a day is one more feature.
* **Access graph** (`accessField`, a field of the resources people reach: shares, repositories, sites, record types;
  `ActivityAccess`): the baseline's people and resources (one aggregate, capped) clustered by `Leiden`; the
  person's window access outside their cluster (at least 5 events and a tenth of the window's), and resources no
  one in their cluster reached before, are a finding naming up to 10; the people of the person's cluster are noted
  as peers learned from access.
* **Location** (`location`; `ActivityLocation`): on by default where the source has a country field, said in the
  notes as categories are (`location: false` turns it off): `countryField`, else the AGENTS doc's `## Fields`
  `country:`, else a `*.geo.country_iso_code` field (`source.*` first), else the flat profile's `Country`; and on an
  Elastic Index a `*.geo.location` point (ES|QL `ST_CENTROID_AGG`), in one hourly aggregate by country: countries new
  in the window; impossible travel (over 900 km/h between two places at least 500 km apart; with countries only, two
  countries within 2 hours, said as such); activity from abroad on leave. `location: true` on a source with no
  country field: "location isn't available", said. No reference data lookups.
* **What was done to them** (`includeTargets`, default false; `TargetScoring`, built 2026-11-24): per source with a
  target field (found as `activity_timeline`'s: `targetPersonField`, `targetUserField`, AGENTS `target person:` /
  `target user:`, ECS `user.target.id` / `user.target.name`), the events done to the person by others (their own
  acts on themselves left out: `AggregateQuery.excludes`, ES|QL `(f IS NULL OR f NOT IN (...))`, StroomQL
  `not (...)`) by day and by type (the Action, element or event code field), and the accounts acting on them (each
  one's first time, after the 28-day burn-in, is a new actor). Scored like the person's own features (the same
  weekdays for `unit` day, else the window's total against the baseline's windows, at least 5; robust z ≥ 3.5, a
  rise of at least 1 and half the baseline; runs merged), rises only; leave is scored (things done to an account
  while its owner is away are the point), exceptions and days with no source data aren't. The source-wide check is
  its own: everyone's events done to them by someone else per person (ES|QL only, `AggregateQuery.differing`: the
  target held and the actor none or another; a non-Elastic index isn't checked, said). Its own findings group
  (`done_to_them`, features `done_to_them:events`, `done_to_them:type:<type>`, `done_to_them:new_actors`), never
  ranked with the person's findings, nor in their volume, rhythm, breadth, profile or peers; in `findings.csv`
  after the rest. Cost: 2 more time-bucketed queries a month per source with a target field (daily by type; the
  population on Elastic) and 1 for the actors.
* **Arguments**: `person`, `logonIds`, `personField`, `userField`, `windowFrom`, `windowTo`, `baselineFrom`,
  `baselineTo`, `unit`, `timeZone`, `sources`, `sumFields`, `breadthFields`, `leave`, `exceptions`, `peers`,
  `peerElement`, `categories`, `categoryField`, `categoryList`, `categoryCorrections`, `accessField`, `location`,
  `countryField`, `includeTargets`, `targetPersonField`, `targetUserField`.
* **Returns**: in a chat, Markdown (the note, the sources by what, the ranked findings, those explained by the
  source, values started and stopped, a line a month, notes, files, and the `activity_timeline` call for the flagged
  days); over MCP, JSON (also
  structured content): `person`, `logon_ids`, `baseline` and `window` (`from`, `to`), `unit`, `time_zone`, `note`,
  `sources` (`index`, `by`, `values`, `breadth_fields`, `sum_fields`, or `left_out`), `findings` (at most 20,
  ranked: `source`, `kind` day/week/month/change/drift/rhythm/peers/access/location, `feature`, `window`,
  `baseline`, `value`, `score`, `evidence`, `alternative`), `findings_total`, `source_wide`, `months`, `notes`,
  `files`, `flagged_days` (up to 5, for `activity_timeline`), `done_to_them` (with `includeTargets`: `note`,
  `sources` each with `by`, `in_window` by type, `new_actors` and `notes`, `findings` as above, `source_wide`; in a
  chat a "Done to them" section), `queries`. Files under `/outputs/compare/<person>/`
  (the person id, else the logon ids, made safe): `profile.csv` (the daily features), `monthly.csv`, `findings.csv`
  (every finding), `values.csv` (each field's values started and stopped), and with categories
  `<source>-categories.csv`; charted by the table tool, or a CSV Data Connection by hand.
* **Refuses**: neither `person` nor `logonIds`; a period that ends before it starts, a baseline after the window's
  start, or more than 3 × 366 days in all; a `unit`, leave or exception that can't be read; a `peerElement` other
  than Organisation, Unit, Group or Role; a `categoryList` or `categoryCorrections` entry that isn't
  `value=category`; a source the user can't use; no Elastic or Lucene index the user can use; no source that can be
  compared (each with why).
* **Not built**: speed from numeric latitude and longitude fields on a non-Elastic index (countries only there); a
  `check_skill` rule against stating intent or guilt (the tool's own wording and the templates only); a tool policy
  of its own for these tools; `propose_timeline_dashboard` for these CSV files (its charts need it extended).
* **Tests**: JUnit `TestActivityStatistics`, `TestActivityScoring`, `TestAggregateText`, `TestPeerGroups`,
  `TestActivityCategories`, `TestActivityAccessAndLocation`, `TestCompareActivityTool`, `TestCompareActivityTargets`
  (over `InMemoryAggregates`); e2e `TestE2eCompareActivity` (ES|QL and StroomQL agree) and
  `TestE2eInsiderCompareActivity` (the insider dataset's plants found, its decoys not flagged, and what was done to
  the leaver and the compromised account found apart, the person's own findings the same with or without it).

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

* **Arguments**: `table` (the table's name, for a dashboard with several; matched ignoring case; a name that matches
  nothing when the tab has only one table uses that table, the result starting with a note saying so), `rows`
  (only the first n, copied with their own profile), `refresh`, `withEventIds`. All optional.
* **Returns**: text: what was taken (or reused), rows held of rows found, the time taken, the time range, the
  state with reasons and notes, the path, and the profile.
* **Refuses**: no attached tab with results tables (`NO_TAB`); a dashboard with several tables and no `table`, or
  a `table` that matches none of them (lists them); `rows` below 1, or over the global row limit (`stroom.askStroomAi.tableAnalysis.maxTotalRows`,
  default 50,000), when it offers that many instead; a data source the user can't use; a search not yet run
  (offers `refresh`); results Stroom no longer holds (offers `refresh`).
* **Notes**: time limit is the query time limit. Each snapshot is audit-logged as a search of the data source.
  The attached tab's note (`TabSnapshots.note`) lists its tables of results and tells the model their rows aren't
  in the tab's snapshot (`searches.json` holds only the search requests), and to call this tool for them, naming
  `table` when there are several.
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

The file is XML 1.1 (`EventsFile.start`), as Stroom writes its Events streams, so an event well-formed in the version
it declares is kept as XML, without its declaration, a control character as Stroom stored it (`&#xc;`). Seen in chat
181: in an XML 1.0 file a Teams message's form feed made the event escaped text (`format="text"`, `&amp;#xc;`), and
every grep for `&#xc` missed it. An event of another version has the characters XML 1.1 reads differently (C1
controls, DEL and U+2028) written as character references, so its values read the same. Anything else (text, XML
that isn't well-formed, a DOCTYPE) is kept as escaped text, with `format="text"`; its control characters other than
tab, line feed and carriage return (and NEL and U+2028, which XML 1.1 would read as a new line) are written as
character references, NUL, surrogates, U+FFFE and U+FFFF left out.

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
records. Records are numbered within a part, as a pipeline numbers them (`step_records`' and `review_coverage`'s
`part` and `record`); `part` picks the part of a stream of several. A record past the part's first million
characters, held a record a line, is read through the part from its start (`StreamReader.rawRecords`,
`RecordStream`), a delimited record ending at a new line outside quotes. Seen: record 9,000 of a two-part 211 MB CSV
stream was read as Stroom's record 9,000, and the agent was given the part's start, header and all, twice. A part or
record the stream doesn't have says so in `hint`. A stream with bytes its feed's encoding can't decode is read as a
pipeline reads it: each bad byte is
replaced, and `undecodable_bytes` reports what was replaced (`StreamCharsets`). With `out`, the records are
written to that file in place of the result's `records`, and the budget is then the maximum. A segmented stream's
XML records (e.g. Events, each a document with its own XML declaration) are written as the table form writes events
(`EventsFile`), record index `n` as `eventId` `n + 1`, so `search_events` reads the file; anything else (raw input,
a child type, text) is written one record after another.

* **Arguments**: from a table: `path`, `limit`, `out`. From a stream: `streamId`, `part` (default 0),
  `firstRecord` (default 0), `recordCount` (default 5, at most 100; with `out`, at most 1,000), `childType`,
  `maxChars` (default 60,000; 1,000 to 200,000; with `out`, default and most 2,000,000), `out`. Asked for more
  records than a call reads, the result says so in `record_count_capped`, with `next_first_record` (seen: 170 asked
  for, 100 given without a word, and the agent read on from 170, leaving gaps; a 6,000-record scan took 83 calls).
* **Returns**: from a table: a text summary, plus the events file. From a stream: JSON (also structured content):
  `stream_id`, `feed`, `type`, `first_record`, `total_records`, `available_child_types`, and `records` (or
  `records_read`, `out` and `note` when written to a file). It may also include `undecodable_bytes`, `truncated`,
  `next_first_record`, `hint`, the split's `header` and `records_are`, and `parts` and `part` for a stream of several.
  For a client outside Stroom, a read past 40,000 characters is given in outline (`FetchEventsOutline`, 2026-11-20;
  seen in eval case 36: reads of 60k–127k characters): saved whole to
  `/outputs/fetch_events/stream-<id>[-part-<p>][-<childType>]-from-<first>.json` (a record a JSON string a line,
  `LargeResults.wholeTexts`), `records` the first records whole while they fit, `records_not_given` each of the rest
  by its keys (`stream`, `part` of a stream of several, `record`, and `event_id` = record + 1 for Events; a child read
  a record a part, `part`), and `read_the_rest` the `fetch_events` call that reads them again (`recordCount` = the
  records shown) or the jq slice of the saved file. Everything else is kept whole. A record too large to show alone
  is read from the saved file a slice at a time.
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
3. On Elasticsearch, IS_NOT_NULL and IS_NULL are searched for what they mean, because Stroom matches every document
   with either (`MisleadingSearches.elasticsearchNullAs`, by the field's type): IS_NOT_NULL as MATCHES_REGEX `.+` (a
   non-empty value) on a string field, and EQUALS `*` on any other (which can't hold an empty string) or one whose
   type isn't known; IS_NULL as NOT_EQUALS `*`. EQUALS `*` is Elasticsearch's `exists`, which an empty string
   meets.
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

These seven tools are in the chat's dashboards tool group. They are offered once the group is active, or from the
start when the attached tab is a dashboard (`BuiltInToolGroups`). All are read-only and in category Content, but
`gantt_chart` (Core); all but `list_visualisations` are chat only.

### `gantt_chart`

Core · read-only · chat only · Classes: `GanttChartTool`, `GanttCharts`; `stroom.ai.shared.MermaidGantt`,
`MermaidGanttParser` (`stroom-core-shared`); in the chat, `AiMermaidCharts` and `AiGanttChart` (`stroom.ai.client`)

Draws a workspace table of times as a timeline in the chat: it writes a ```` ```mermaid ```` `gantt` block for the
agent to put in its reply as it is, and the chat draws it. The agent never writes chart code. It is the only chart
the chat draws: for any other kind (a heat map, a network, a pie, bars or lines, a map) the result and the
description say to offer only a dashboard (`propose_dashboard`, `propose_visualisation`), never a chat chart that
approximates it; where a timeline is asked for and the user hasn't said where, the agent asks (chat or dashboard).

* **The block** (`GanttCharts`), the same layout every time: `title` (the call's, or "Timeline", with the time zone
  named after it: "(times in Europe/London)"), `dateFormat YYYY-MM-DD HH:mm:ss`, an `axisFormat` by the span
  (`%H:%M` up to a day, `%d %b %H:%M` up to a week, else `%d %b`), then a `section` per lane (the `lane` column's
  values, in the order they start), or per day in the zone (`2026-11-23 Mon`) without one. A row is a bar from
  `start` to `end`, or a marker (`milestone`, `0d`) where it has no end or ends as it starts. Times are written in
  the call's zone (`timeZone`, else the user's device's, else UTC, saying so), across a change of clocks too.
* **Escaping**: every value from the data (labels, lanes, the title) has Mermaid's syntax characters taken out
  (`:`, `,`, `;`, `#`, `%`, quotes, backticks, braces, angle brackets, backslashes), its line breaks (and Unicode
  line separators and control characters) made spaces, and is cut to 40 characters; a label Mermaid would read as
  a statement or a date (starting `click`, `section`, `excludes`, ... or `2026-11-23`) gets `· ` before it. Never
  `click`, `call`, callbacks, comments or `%%{init}%%`.
* **Folding**, each said in the result's "Folded" list: rows with no start time that can be read, or ending before
  they start, left out; the labels kept (`top`, at most 8, the chat's colours): those with the most time, and up to
  half of them labels of markers by their rows, the rest "Other"; at most 20 lanes (the rest in a section "Other");
  a bar following one of its label in its lane (no other bar between) within `mergeSeconds` (default 300) merged
  into it; bars shorter than `minSeconds` (default 60) left out; at most 200 bars and markers, the shortest bars
  left out first, then the latest markers.
* **Round trip**: every block written is read back by `MermaidGanttParser`, with as many sections and tasks as
  written, or the call fails (a bug, never the data's doing).
* **Arguments**: `path` (a .csv, .tsv or .md table), `start` and `label` (columns), optional `end`, `lane`, `title`,
  `top`, `minSeconds`, `mergeSeconds`, `timeZone`. Times are read as the table tool's join reads them (ISO 8601, UTC
  without a zone, or epoch milliseconds).
* **Returns**: the block, its counts (bars, markers, sections, rows of the table, named as the whole table), what
  was folded, the zone, and what the chat draws.
* **Refuses**: a file that isn't a table (JSON, XML, text), a column the table doesn't have (naming those it has),
  a table with no rows or no start time that can be read, and a zone that isn't one.

**The subset** (`MermaidGanttParser`, in `stroom-core-shared` so the server and the browser share it, plain Java
with no regular expressions or `java.time`): `gantt` first; `title`, `dateFormat` (dayjs `YYYY`, `MM`, `DD`, `HH`,
`mm`, `ss`, `SSS`), `axisFormat` (d3 `%Y %y %m %d %e %b %a %H %M %S`), `section`; tasks `<name> : [tags,] [id,]
<start>, <end or duration>` with tags `done`, `active`, `crit`, `milestone` and durations `ms`, `s`, `m`, `h`, `d`,
`w`. Anything else is outside it and refused: comments and directives, `click`/`call`/`href`, `after`/`until`,
`excludes` and the other statements, front matter, Mermaid's syntax characters, a task whose name starts like a
statement or a date; and over 200,000 characters, 1,000 tasks or 200 sections. Times are wall-clock, held as
milliseconds since 1970-01-01 on that clock (`MermaidGantt.wallClock`), so the browser needs no time zones.

**In the chat** (`AiMermaidCharts`, called from `AskStroomAiPresenter` for answers only): after the shared
`MarkdownConverter.convertUntrustedMarkdownToHtml` has made the answer sanitised HTML, each `<pre><code
class="... language-mermaid">` block of text only whose content (character references read) parses is replaced by
`AiGanttChart`'s SVG; any other block stays as code, never run. `MarkdownConverter`, `MarkdownPreviewPresenter`
and Documentation docs are unchanged (a ```` ```mermaid ```` block there stays code). `AiGanttChart` draws a lane
per section (tasks packed into as few rows as don't overlap, as Mermaid's compact display does), bars, diamonds for
markers, a dashed grid and ticks (steps from a second to a year, at most 8), a legend (eight colours in a fixed
order by first appearance; "Other" and names past the eighth grey), and a `<title>` tooltip per bar; every text
escaped, colours and lines by CSS classes (`ai-gantt-*` in `ask-stroom-ai.css`, the hues stepped for the dark
theme). No third-party script. Dragging across the plot or axis zooms to that span (bars cut to it, tasks outside
it left out, ticks recomputed and written finer when the axis format would repeat; a drag under 5 px is a click),
again within a zoom; "Reset zoom" (shown only when zoomed) or a double-click shows the whole period; "Maximise"
opens it in a resizable Stroom dialog drawn at the dialog's width, with the same zoom (`AiGanttZoom`,
`AiGanttInteraction`, `AiGanttPopupPresenter`).

* **Examples**: `golden/gantt/*.mmd` (a day of window focus on two hosts, a week by day, hostile labels), each read
  by the parser and, line by line, by a port of Mermaid's gantt lexer rules (`TestGanttChartExamples`), as there is
  no Mermaid offline; paste one into the Mermaid Live Editor to check it against Mermaid itself.

### `propose_dashboard`

Content · read-only · checks Use on the data source · chat only · Classes: `ProposeDashboardTool`,
`DashboardBuilder`, `UserActions` (`create_dashboard`)

Offers a "Create dashboard…" button for a new dashboard built from a StroomQL query. The dashboard holds a query, a
table of the columns the query's `select` gives (with its grouping, sorting and limit), and, if asked for, a data
preview: a Text widget showing the event of the selected row, laid out below the table. `DashboardBuilder` builds
it in one of two forms:

* `stroomql` keeps the query in an Embedded Query, which shows its own table.
* `classic` turns the query's `where` clause into an expression Query and its `select` into a separate Table.

A classic dashboard can't hold joins (nor `where` subqueries, bracketed queries or a `from` alias), or `window`,
`filter`, `having` or `show` clauses. The query is formatted and validated as `propose_query`'s is. The button
opens Stroom's New Dashboard dialog. The server builds the settings again from the query when the button is
clicked. The new dashboard then opens, ready to run, attached to the chat.

* **Arguments**: `name` (at most 200 characters, one line), `query`, `query_type` (`stroomql` or `classic`),
  `preview` (boolean, required: the tool tells the model to ask the user with `ask_user` if they haven't said),
  optional `description`.
* **Returns**: text describing what the dashboard holds (`Built.describe()`), plus a `create_dashboard` action
  holding `name`, `query`, `query_type`, `preview`, and for classic an `outline`: the Query and Table in plain
  words, which the user sees in place of the StroomQL.
* **Refuses**: no usable data source; an invalid query; the builder's refusals ("The query has no select clause…",
  a data preview on a grouped query, which can't trace rows to events, "A classic dashboard can't hold …"); while
  the session's build (the one it last worked on) has an indexing pipeline whose indexing isn't verified (its plan's
  `indexed` step to do: `BuildProgress`, bound by `stroom-mcp-impl` as `BuildProgressImpl` over
  `BuildPlan.unverifiedIndexing`), "No dashboard was proposed: build '…' has an indexing pipeline whose indexing
  isn't verified yet…", naming the plan's next step (`create_processor_filter`, `wait_for_processing` or
  `verify_index`, which makes the build's -VERIFY dashboard over the data's real time window). Seen in testing: the
  agent skipped those steps and proposed a dashboard over the last 24 hours that showed none of the sample's
  months-old events. Outside a build, or once indexed, unchanged. Not done: telling the user the data source's
  earliest and latest event times when the query's time filter matches nothing, as the tool runs no search and
  there is no cheap way to read them for any data source.

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
`StyleSetting`, `D3Visualisation`, `AdapterScripts`, `TimelineRows`

Reports, as Markdown:

* the charting libraries installed:
  * ECharts: a Script doc holding `echarts.min.js`, recognised by its content (the zrender dependency and version
    each build declares), version 6 or later (`MIN_ECHARTS_MAJOR`);
  * D3: a Script doc holding a D3 build, version 3 or later, the newest used;
  * the visualisations content pack's own D3 Visualisations, found by their fixed UUIDs, with function names as a
    fallback for a copy;
* every chart kind (`ChartKind`: bar, line, pie, scatter, graph, heatmap, calendar, histogram, sankey, chord, gauge,
  sunburst, timeline), with its columns (roles: required or optional, the values each needs, and whether the content pack's
  D3 version has the role) and its style settings (values and defaults);
* how clicks select, and the ECharts option keys allowed (`VisualisationPlan.OPTION_KEYS`; for D3 only `color`);
* the Visualisation docs the user can see, at most 100, each marked as generated by Stroom AI (and with which
  library) or as a content pack D3 chart. One of its own (not generated) whose name or vis class says timeline or
  Gantt is left out (`InstalledVisualisation.isOwnTimeline`): timelines are only ever the generated `timeline` kind.

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
  and the kind's `maxSeries`, `maxValues` and `maxNodes` settings;
* for a timeline: start and end are dates (read as ISO 8601, UTC without a zone, or epoch milliseconds); rows
  ending before they start, or without a label, are left out with a warning (all of them: a problem), the render
  report counting them (`droppedRows`: `end before start`, `label empty`, `start or end not a date`); stats
  `bars`, `markers` (rows ending as they start, drawn as diamonds; a bar is drawn at least 2 px wide), `lanes`,
  `mostRowsInALane` (each lane's bars packed into rows by `TimelineRows`, as both adapters pack them) and
  `endBeforeStart`; a warning when start and end are one column (markers only) or over 100 lanes.

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

**Timelines** (kind `timeline`, built 2026-11-24): columns `start`, `end`, `label` (required), `lane` and `colour`
(optional), taking `activity_timeline`'s and `gantt_chart`'s tables as they are (start, end, label process, lane
host). Both adapters draw it alike: ECharts as a `custom` series per colour (a rectangle per row from `renderItem`,
a diamond for a row ending as it starts), D3 by hand; lanes on a category y axis in order of their first start (or
their own, for lanes of dates or numbers), time along x with `dataZoom` (inside and a slider, `zoomSlider` on by
default; `weakFilter`, so a bar partly in view stays) and the range tool; bars overlapping in a lane packed into
rows, by time (`timelinePack` in both adapters, `TimelineRows` in Java); colours by `colour` (else `label`), the
`maxCategories` with the most time each their own and the rest grey ("Other groups"); a tooltip with the label,
lane, colour, start, end and duration. A click on a bar selects `label`, `lane`, `start` and `end` (and the
columns' names), with `from` and `to` its start and end, so a term on a date field with selection `start` or `end`
(made a range, as for other dates) keeps it within the bar's time; the legend selects a label when the bars are
coloured by label. A content pack's own timeline or Gantt chart is never offered.

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
  `references_in_use`, `loaders`, `reference_data_template` (`uuid`, `name`, `path`, `child_must_supply`),
  `context_loader_template` (`ReferenceData.contextLoaderTemplates`: a pipeline adding its own `ReferenceDataFilter`
  whose XSLT a child supplies, Stroom's standard `Context Data`; `uuid`, `name`, `child_must_supply`; null if none), any
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
`SampleTexts`, `PipelineTemplates`, `AgentInstructions`, `SourceTypeRegistries`

Starts onboarding a new source: profiles every sample, creates (or finds) the build's folder, remembers the build for
the session, and returns the plan with its first step and the standing instructions (AGENTS docs) for the folders
given. It has two paths:

* **Files first** (`files` given, no `samples` or `stream_ids`): the build is made now and the profile later. The
  result names the next calls: `create_feed`, `upload_sample files=<the paths>` (run in the user's terminal), then
  `start_onboarding` again with `build` and the `stream_ids`, which profiles the uploaded streams. This exists
  because a slow local model writing sample text into tool calls (seen: Gemma 4 31B at about 22 tokens a second took
  77 s per call, and 100 lines would have taken over ten minutes) is the bottleneck; files never pass through the
  model.
* **Profile now** (`samples` text, or `stream_ids`): sample streams are read as whole events through
  `SampleStreamTexts` (see "Sample streams' sampling" under "Sample analysis"); text is read with
  `SampleTexts.asNamedSamples`. All
  samples are profiled together (`SampleProfiling.profileAll`, XML fragments with the environment's own wrapper).
  The result gives the format's parser (`BuildPlan.PARSER_FOR_FORMAT`), which templates here read it
  (`templatesReading`: the environment's own translation templates with that parser by name, else one whose parser
  can be swapped, else "ask the user", with `create_pipeline standalone='events'` as a last resort when there are
  none), and whether a text converter is needed.

* **Arguments**: `source_name` (required; names the build), `samples` (pasted text only: a list, or a map by file
  name; about 10 records and at most 8,000 characters a file), `files` (paths on the user's disk), `stream_ids`,
  `build` (default `onboard-<source name, lower-case, non-alphanumerics as hyphens, up to 40 characters>`),
  `folders` (promotion destinations, for the standing instructions), `kind` (`events`, `discovery`, `reference` or
  `assessment`; kept in the build record so the plan's steps follow it from the start), `feed` (with
  `kind=assessment`).
* **Returns**: `build`, `folder`, `source`, `profile`, `parser`, `template`, `text_converter`, `plan` (the
  onboarding checklist, or the build's own steps once `kind` is given), `task_list`, `next`, `done: false`,
  `standing_instructions`, `hint`, `tools` (`BuildPlan.HIDDEN_TOOLS`), and `read` notes (a stream cut short, bytes
  its encoding couldn't decode) or `sample_note` (a pasted sample over 20,000 characters, for next time). The files
  path gives `files` and `then` in place of the profile; an assessment gives `kind`, `feed` and the plan in its
  place.
* **Refuses**: no `source_name`; a `kind` outside the four; no sample at all (`ask the user for every file they
  have`); a sample that is a file path, an attachment reference, a file a reader cut short, or one text broken up by
  the tool call (`SampleTexts`); stream ids without View Data, or older than their feed (an earlier, deleted feed's
  streams); an assessment without `feed`, or of a feed another build is open on (below).
* **Notes**: never call `profile_sample` as well; the description says so. With `kind` or `stream_ids`, `next` comes
  from the build itself (`BuildPlan.nextStep`), as the feed and samples may already exist.

**An assessment** (`kind=assessment feed=<an existing feed>`): nothing is onboarded. Given `stream_ids` and no
`feed`, it assesses the one feed they are in; samples, stream ids or files given are not profiled (said in
`samples`): the survey reads the feed itself. The build records the feed as what it works from, and its plan is the survey (`survey_feed`, which finds
and confirms a source type field), `source_types` (`record_source_types`, which writes the registry at once, every
source type `parked`) and promotion, the registry going beside the feed (its lasting output, where later builds
find it; the survey doc stays in the build). The user may then onboard its source types, one source type group (any
number of source types) a build.

**One open build per aggregated feed** (`SourceTypeRegistries.refuseOpenBuild`, decided 2026-11-12), checked by an
assessment here (given `feed`, or the one feed its `stream_ids` are in), by an onboarding given `stream_ids` (for
their feeds), by `start_build` for each of its `feeds`, by `record_source_types`, by `build_translation_xslt group=`
building from the registry beside the feed, and wherever a build makes a working copy of a feed's registry
(`review_coverage` adding new source types, say): a feed with a registry (beside it, or in another build; for
`record_source_types`, any feed) is refused while another build is open on it: one whose folder holds the feed's
registry, or that works from or holds the feed and holds an XSLT or pipeline (made and neither promoted nor
abandoned; the workspace's `backups` aside). What a build's promotion kept in the workspace (marked
`kept_at_promotion` in its record: a test feed, a survey doc, a document given `keep`) doesn't count while its
version is still the one kept (`kept_version`); changed since, by the tools or by hand, it is work again. `Feed '<feed>' aggregates source types, and
build '<other>' is open on it: one build at a time per feed ... Finish build '<other>' (promote_build) or abandon it
(the user deletes its folder) first, or carry on in it.` So the registry, the group pipelines and the feed's
documents change in one build at a time, and promotion needs no merging.

### `start_build`

Content · write (makes the build's folder, records the feeds it works from) · checks View; folder made as the user in
`stroom.ai.builds.workspaceFolder`; feeds are found among those the user can view · changes only its build · chat
group build · Classes: `StartBuildTool`, `WriteGuard`, `BuildRecords`, `FeedNames`, `BuildPlan`, `AgentInstructions`,
`SourceTypeRegistries`

Creates (or finds) the build's workspace folder, where every write tool creates documents, and returns the standing
instructions that apply to the `feeds` and `folders` given. Any feed given that exists outside the build is recorded
in the build record as what the build works from (`BuildRecords.addSourceFeeds`, `source_feeds`): the plan then
doesn't send the agent to create a feed and upload samples, and promotion's filters name those feeds. Seen in VS
Code: without this, a test feed was made and a workspace file uploaded though the user had chosen production streams.
The build is remembered for the session (`BuildPlan.rememberBuild`), as `start_onboarding`'s is, so later calls that
name no build work on it. Found reviewing the tools: it remembered none, so a build started here wasn't the one
those calls fell back to.

* **Arguments**: `build` (required), `feeds`, `folders`, `kind` (`events`, `discovery`, `reference`, `assessment`;
  given again it changes the build's).
* **Returns**: `build`, `folder`, `uuid`, `works_from` (existing feeds), `not_found` (names not in this Stroom, "A feed
  this build is to create is fine"), `standing_instructions`, `kind`, `plan` (the onboarding checklist, or
  `BuildPlan.plannedSteps` once the kind is known), `task_list`, `next` (when the plan has one), `done: false`.
* **Refuses**: no `build`; a `kind` outside the four; a feed given that aggregates source types while another build
  is open on it (see `start_onboarding`: one open build per aggregated feed).
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
its mapping generates (`GeneratedDrift`). An aggregated feed's group pipeline is checked as any events pipeline: its
one XSLT is the group's, which keeps its mapping; the feed's modules it imports (its common module, and the helpers
that imports) are checked too (`GeneratedDrift.moduleEdits`, decided by the user 2026-11-24): one edited by hand since
the tools saved it is said once, naming what changed, as a warning (it runs as edited, and a later build that adds to
it asks whether to keep the edit). A check ending `(promotion waits for this)` blocks `promote_build`.

* **Arguments**: `build` (required), `full` (default false).
* **Returns**: by default compact (`BuildStatusTool.compact`): `next`, `before_promotion` and `steps` first, each step
  by its state only, each document on one line (`type 'name' (role) uuid, a working copy of ...`), then the rest:
  `build`, `sample` (format), `feeds`, `sample_streams` by type, `xslts`, `pipelines`, `events_streams` (up to 20),
  `unmapped_fields` (each field processing reported as read by no rule and not yet mapped or in `ignored_fields`,
  with its raw streams; the `unmapped` step stays to do until there are none: asked for by the user, so a build
  doesn't go on with fields silently unmapped), `works_from`. With `full: true`, each step's description and tools and each document's path.
* **Refuses**: no `build`.
* **Notes**: compact is the default because the full result (about 10 KB) was saved to the workspace and fetched again
  and again, as the agent couldn't keep it in view. For an aggregated feed whose groups import different versions of
  its common module or helpers (a promoted module changes only by a new version), `module_versions` says so in one
  informational line, naming the groups and the versions (`SourceTypeGroups.mixedModuleVersions`: a group the build
  changes as the build has it, the others as production has them).
  For each new version of an events (or records) pipeline in the build whose predecessor's Events promoted pipelines
  read (their filters name it: `ProcessorFilters.followOnPipelines`), `follow_on_review` names them and says
  `review_coverage pipeline_uuid=<predecessor> changed_pipeline_uuid=<new version>` checks them against the new
  version's Events before promotion, which gives each a new filter, disabled, reading them
  (`BuildStatusTool.followOnReview`, 2026-11-17).

Since 2026-11-24 (chat 192): an events pipeline documented before its XSLT was saved again has `stepped` to do, and
`next.then` adds "then update its documentation (write_documentation change=...): it was written before the XSLT was
saved again" (`BuildPlan.documentationAfterStep`); `documented` reopens as before, through the Field mapping digest.
`before_promotion` names a production pipeline a new version replaces whose own XSLTs import a module that no longer
exists (`BuildChecks.brokenPredecessor`, `PipelineShapes.brokenImports`: hrefs by `ModuleChecks.hrefs`, resolved as
Stroom's URI resolver does by `XsltImports.resolve`): "it fails until it is restored"; informational, never blocking.

### `promote_build`

Content · write · checks View, Edit and Delete on what it moves, writes back and deletes; documents are moved, copied,
written back and deleted through Stroom's explorer and stores as the user, who also needs to create folders in the
destinations; unlocked by Manage Processors (the promoted pipelines' filters are made as the user, only once they
approve) · confirms itself · chat group build · Classes: `PromoteBuildTool`, `PromotionLayout`, `PromotionProcessing`,
`DocumentPlaces`, `DocumentWrites`, `WriteGuard`, `BuildRecords`, `BuildChecks`, `Consent`, `IndexingRuns`,
`OutputFeeds`, `XsltHistory`, `DocumentationVersions`, `SurveyDocuments`, `SourceTypeGroups`, `GroupReferences`,
`FeedIndexDecisions`, `SourceFolders`, `PromotionTidyUp`, `PromotionUses`, `ChangedOriginals`

An aggregated feed's indexes (`FeedIndexDecisions.changes`, `apply`): each indexing plan kept in the build is
matched to its source type group and output feed (as `draft_index_mapping` recorded them in the build record's
`index_groups`, else the build's scope group and its output feed). Each group has an index of its own (decided
2026-11-14): a plan naming another group's index that `draft_index_mapping` didn't share at the user's word
(`share_index_with`) holds the promotion, as does a plan with a field its index has otherwise (another type, or
another meaning), refused with each clash and to resolve them through `draft_index_mapping`'s form. The review and
the approval form list `index_changes` (`index <name> (group <g>, feed <f>): ...`: the fields each index gains,
forward only, or an index recorded or replaced by a new version); once approved, the registry records each group's
index (`indexes`, by index name: `index`, `backend`, `index_doc`, `fields`, `added_by`, `groups`, `output_feeds`,
`replaces`; a group leaving an index for a new version or a shared one is taken off it, and an index left with no
group dropped) and the feed's shared backend and naming where none were recorded, and a Lucene index doc beside the
feed gains the fields it lacks (`IndexFields.addPromotedLuceneFields`). An Elasticsearch index's fields come from its
template, the cluster admin's to commit. Nothing is reindexed. A registry recorded before 2026-11-14 (indexes by
output feed) is read by each entry's `index` and `groups`, and rewritten by index name.

The promoted indexing pipelines' filters keep their sample filters' `Pipeline` condition (the Events of the source
pipeline only, whatever feed): one naming a working copy of a pipeline (deleted at promotion, its original going on)
names the original instead (`PromotionProcessing.toOriginals`), so a group's index goes on reading only its group's
Events.

A feed's survey doc (`besideFeeds`) always stays in the build (`keep`, decided 2026-11-13: nobody is asked, and the
review doesn't offer to move it); its source type registry always goes beside the feed (`keep` for it is refused).
Each document left in the workspace is marked `kept_at_promotion` in the build's record, with its version then
(`kept_version`), so it doesn't hold the build open on its feed until it is changed.
An aggregated feed's modules (`modulesBeside`: helpers, common module and source type groups, by their `mcp:module`
marker) and a group's output feed of its own go under the feed too, unless given a destination or a working copy's
(written back): the common module in `<feed>/Common/V<x.y>` and the helpers in `<feed>/Helpers/V<x.y>` (roles of their
own, the version after the module's name, none being `V1.0`: `VersionNames.moduleVersion`; a new version, `<feed>
common V1.1`, beside its predecessor the same way), a group's XSLT with its pipeline in `<feed>/<group>/Events/<the
pipeline's version>` whatever its own name says (seen in e2e: `PEGA security-V2`, named before the user settled the
pipeline's `v2`, went in a `V2` folder apart from its pipeline), a group's output feed in `<feed>/<group>/`; nothing
promoted is written. The
reference data each group pipeline lacks for its group's lookups (`GroupReferences.changes`, from the registry rows'
`references`) is listed in the review and the approval form as `pipeline_changes` and added once approved, after the
moves (forward only: none is removed): the build's pipelines (a new one, a new version, or a working copy's
original), and the production pipelines running a group's XSLT the build changes through a working copy (found
through the content index, `PipelineIndex.containing`). After approval and before anything is written back or moved
(`SourceTypeGroups.toOriginals`), every module of the build imports the originals its working copies are written back
over (imports rewritten by text, in the code and the kept mapping) and the registry names the originals.

Promotes a build out of the workspace in two calls. The first works out the whole layout and returns it without
changing anything (`status: needs_review`, a `layout` tree from `PromotionLayout.promotionTree`, and a `review_id`,
the first 12 hex characters of the tree's SHA-256). The agent shows the tree in the chat; once the user confirms it,
the second call passes `reviewed=<review_id>` and the user approves a short form bound to that layout (build, document
count, folders, role locations, warnings, processing after promotion, follow-on filters; the review id isn't shown,
but the answer is
bound to it through `Consent.Request.boundTo`). `reviewed=true`, or an id
for another layout, gets the review again (seen: `reviewed=true` on the first call, and the user approved a form whose
layout was never shown). Any change of destinations changes the tree and so the id.

Where each document goes (`plan`):

* A working copy of a pipeline, or of its code (an XSLT or text converter, an aggregated feed's common module or
  helpers too), from a build opened before new versions became the only way, is refused (`refuseCodeWorkingCopies`:
  `Not promoted: XSLT '...' is a working copy of a promoted pipeline's code, which changes only by a new version ...
  Nothing was changed.`; for a shared module, that build_translation_xslt of the build's group makes the new
  version the group imports); an unchanged one is only discarded. A working copy of Documentation (the promoted
  source notes a next version's build carried in, with its catalogue of event types) is **written back**: the original
  is copied into `<workspace>/backups` as `<name> backup <yyyyMMddHHmmss>` (UTC, tagged `mcp-generated`), then the
  copy's `data`, `converterType`, `documentation` and `description` are written over it, and the copy deleted. No
  backup made (the explorer copy gives back no document), nothing is written over the original: the promotion stops there, saying
  what was done before it. An unchanged copy is just deleted. A copy whose original was changed since it was taken is
  written back all the same, the approval saying so (below).
* `keep` for a document UUID leaves it in the workspace (seen: a test feed made while fixing had nowhere to go but
  production, and the agent never promoted the fix). A build on existing feeds keeps its feeds there unless given a
  folder. The build's test feed (named `-MCP-TEST`, or recorded `output_for` the feeds it works from), its test
  indexes (`test_index_for`) and the `-VERIFY` dashboards searching only them are never promoted, whatever the
  destinations say (`PromotionTidyUp.testDocs`): seen in chat 177, the test feed listed under Events and the agent
  asked the user whether to promote it. The layout ends with the tidy-up (below), or, for a test feed not tidied up,
  saying it stays in the workspace.
* Otherwise a destination by document UUID (exactly that folder), else by type. Source notes (with the catalogue of
  event types) and reference docs go beside the build's only feed; a pipeline's (or index's) documentation, unless
  given a destination by its own UUID, goes wherever that document's step goes (`besideWhatItDocuments`); a
  verification dashboard goes where the index it searches goes (`withWhatItSearches`), or with the build's indexing
  pipeline when the index is from outside the build (seen in VS Code: promotion stopped for want of the dashboard's
  destination); the documentation of a production pipeline or existing index goes beside it.
* **Role first, then version** (`layOut`, `PromotionLayout.rolePath`; decided by the user 2026-11-17): a document of
  a role (its build role subfolder, or a pipeline's stage: a CEF flattener, Events in and CEF to Kafka, is the
  forwarding stage, `CEF`; a reference loader `Reference`) goes in `<source's own folder>/<Role>/<version>` where its
  role's root is the feed's own, else `<role root>/<source's folder name>/<version>` with no role folder, whatever
  folder its type was given (so with the source's folder known no destination is needed). A pipeline's version is its
  name's version token; a pipeline with none goes in `V1.0` and the layout warns `Pipeline '...' has no version in
  its name (e.g. -V1.0-): placed in V1.0.` The role's other documents go with their pipeline: the one whose version
  their name holds, else the one sharing most of their name (of their source type group first). Replaced role
  subfolders made only when more than one role moved (and `role_subfolders`).
* **A new version** (`version_of`, from `copy_pipeline`) goes in a sibling version folder of its predecessor's
  (`PromotionLayout.predecessor`, `nextVersionPath`): `<feed>/Events/V1.0` gives `<feed>/Events/V1.1`,
  `<root>/<feed>/V1.0` gives `<root>/<feed>/V1.1`; a predecessor laid out the old way (`<feed>/<Role>`,
  `<feed>/V1.0/<Role>`, `<feed>/<Role>/v2`, `<feed>` alone) keeps its place, the new version beside it, with a role
  folder where the predecessor sits in the feed's (or a group's) folder. Unless its role's root was changed in the
  review: then it is laid out afresh under that root. A new version with no other place is laid out beside its
  predecessor.
* **Each role's root** (`roleRoots`, `RoleRoot`): a destination keyed by the role (`{"Indexing": "/Elastic
  Indices"}`), else, for a feed already in production, the feed's own folder given under `Feed` (or the feed's name
  or production uuid) for every role, else the user's earlier answer (`promotion_roots` in the build record), else
  the proposal: a new version's predecessor's root, else where the sibling feeds' production pipelines of that role
  are (`SourceFolders.siblings`: feeds in the feed's root and the folders there holding a feed;
  `PromotionLayout.siblingRoots` reads, from `PipelineIndex.read()`, pipelines in a folder named for a sibling, not
  templates others inherit from nor in the workspace, at most 200, by stage role, and takes the root most are under),
  else the feed's own root. A destination given (by role, or the feed's own folder under `Feed`) replaces the choice
  recorded, and is recorded (chat 194: the user's correction, passed as `{"Feed": "/Feeds/Endpoint"}`, left the
  recorded choice in place, so no layout could change). A feed the build makes is placed by `Feed`, so there `Feed`
  says nothing of the roles: role keys do. A folder given for a role is read by `PromotionLayout.givenRoot`: the
  feed's folder or one in it (a role folder the layout showed) is the feed's own location; a folder ending in the
  feed's folder's name, with or without its version folder (`/Elastic Indices/Endpoint/V1.0`), is the folder holding
  it; else the folder as given. The feed's own folder given (or chosen) as a role's root is the feed's own location
  (`PromotionLayout.ownRoot`, in `rolePath`, `layOut`'s predecessor check and `roleRoots`): seen in a user test
  (2026-11-18), the feed in a folder named after it (`.../ABC/ABC-FEED`), its own folder given back was read as a root
  elsewhere, and every role went in `ABC-FEED/ABC-FEED/V1.0` with no role folders. With forms, all roles in one form
  on the review's call (`Consent.chooseOrTypeEach`): per role, a choice of the folders each option gives, as the
  layout would put the role's pipeline (`System/Feeds/Endpoint/Events/V1.0 (the feed's own folder, where the sibling
  feeds' Events pipelines are)`, then the feed's own folder where the proposal differs), ending in `another folder
  (type it)`, with a text field under it (`a<n>_typed`) for the folder to hold `<feed folder>/<version>`; the user
  never sees a root. Seen in chat 194: the old form offered `System/Feeds` (the folder holding the feed's) as a
  suggested answer with a free box, which read as the wrong place; the user typed "feed's location", which was
  recorded and laid out as `System/feed's location/Endpoint/V1.0`. A typed answer must be a folder path (with a `/`)
  or the name of a folder in System; otherwise nothing is recorded and the form is asked again saying why (at most
  3 times in the call, then refused, naming the keys that change roles). Answers are recorded so the approval call
  lays out what was reviewed; without forms, the reply's `role_locations` (`role`, `folder`: the folder the role's
  pipeline goes in, `why`, `options`: the folders each option gives) for the agent to ask in the chat. The tree ends
  with `Where each role goes ...: - <Role>: <folder> (<why>)`, so the review id and the approval cover them. The
  review's hint, and the reply when the user sends a message instead of approving, name the keys that change roles
  (each role, and `Feed` for a feed in production: the feed's own folder for all of them).
* **Only what is used** (`PromotionUses`; decided by the user 2026-11-18, seen in a user test: an older Data Splitter
  the build had replaced was promoted with the rest): promotion moves only what the build's pipelines need. Walked
  from everything always promoted (pipelines, the parent pipelines they inherit from, feeds, indexes, working copies,
  an aggregated feed's modules, an indexing XSLT holding its index plan, documentation of its own) through each
  pipeline's own properties' documents and its reference lookups' pipelines and feeds, each XSLT's imports, includes
  and dictionary lookups (a build XSLT's or dictionary's name quoted, or its UUID, in the code), and each dictionary's
  imports. A build XSLT, text converter or dictionary nothing reaches (one used only by another unused one included),
  and the Documentation named after only those, stay in the build untouched (a `keep` step, target `not promoted:
  nothing uses it`: no version consolidation, marked `kept_at_promotion` like any document left, never tidied up).
  The tree ends `Not promoted, left in the build (nothing uses them): ...`, the review lists `not_promoted`, the
  approval `not promoted (nothing uses them)`. A folder given by the document's UUID promotes it all the same. Where
  the walk can't be sure, nothing is left out: a build of no pipeline (seen in e2e: a converter and XSLT promoted on
  their own), a build pipeline (or XSLT, dictionary) unreadable, or an element picking its document by a name
  pattern (`xsltNamePattern`, `namePattern`). The registry, source notes, reference documents and survey doc are
  placed as before. A Lucene index doc whose name marks it as one to test with (a word `TEST`, numbered or not:
  `-TEST`, `-TEST2`, `-MCP-TEST`) is walked too: nothing promoted writing to it, and no index plan of the build (by
  its `index_name`) nor the feed's registry (by UUID) naming it, it is the agent's own test index, kept and tidied up
  with the build's test indexes, and so are the `-VERIFY` dashboards searching only such indexes (seen in an eval,
  case 61: an agent's `-TEST` and `-TEST2` indexes and their dashboards were promoted beside the new version's
  pipeline). Any other index is promoted, whatever writes to it.
* **A folder of the source's own** (`SourceFolders`): the source is the feed the build works from, else its
  registry's feed, else its one new feed. Where that feed's folder is a parent of sources (a subfolder of it, not a
  role or version subfolder, holds a feed: the siblings' convention), the build's documents go in
  `<that folder>/<feed name>`: its registry, modules, group output feeds and source notes, and any document placed
  by type (or by a pipeline's, an index's) rather than by its UUID, a version folder or beside a production
  document. So no role subfolder is made directly under a parent of other sources. Seen in chat 177: the Azure feed
  directly in `/Feeds`, Netskope and Delinea each in a folder of their own; the registry went to
  `/Feeds/Azure-Audit-V1.0-EVENTS`, the output feed and notes to `/Feeds`, the rest to `/Feeds/Events` and
  `/Feeds/Indexing`. A refusal for documents with no destination suggests the source's own folder.
* **An aggregated feed's group** (`group`, `GroupPlace`; asked for by the user 2026-11-15): a build of a source
  type group (named by its XSLT's `mcp:module kind="group"` marker, a working copy's too, else the build's scope)
  puts the group's documents in `<source's own folder>/<group>`: its new XSLT and output feed of its own, and every
  document with a role placed in the source's own folder (pipelines, index, indexing XSLT, documentation, dashboard),
  under their role subfolders: e.g. `/Feeds/Azure-Audit-V1.0-EVENTS/M365 Messaging/Events/` (the pipeline, the
  group's XSLT, their documentation) and `.../M365 Messaging/Indexing/`. The feed's shared documents (registry,
  common module, helpers, source notes) stay in the source's own folder; a document given another folder keeps it, a
  version goes in its version folder, a working copy is written back in place. A build of no group (and an
  assessment) is placed as before. Seen in chat 178: the group's XSLT at the feed's folder beside the common module
  and helpers, apart from its pipeline. Since 2026-11-17 with version folders: `<feed>/<group>/<Role>/<version>`, or
  `<role root>/<feed>/<group>/<version>` for a role whose root is elsewhere.

**Production changed since the copy.** `copy_pipeline` records in each working copy's build record entry the version
its original had when copied (`BuildRecords.COPY_OF_VERSION`, `copy_of_version`). Each document to be written back
whose original's version is now different (`ChangedOriginals.said`; e.g. edited by hand in production) is overwritten
all the same, after its backup (decided by the user, 2026-11-18): never a question of its own. The review lists each
as `changed_in_production` (its hint says to tell the user), and the approval form the user confirms anyway shows
them in full under `changed in production since copied` (a `Consent.Listed` detail), e.g. `Documentation 'ACME
notes' was changed in production by john.smith on 2026-10-21 09:30 UTC since it was copied: it is overwritten, its
earlier version kept as a backup in System/MCP Workspace/backups ('ACME notes backup <date and time>')` (who and when
from the original's `updateUser` and `updateTimeMs`, each left out when unknown). Declined, nothing is promoted. A
copy taken before the version was recorded has none, and is written back without saying so. Before, the user was
asked to overwrite or keep production in a form of its own, answered in the chat through a `changed_originals`
argument where it couldn't be shown; both are gone (an old `changed_originals` is dropped as an unknown argument).

Every destination is resolved before approval (`WriteGuard.resolveFolder`), and the folders to create are listed in
the tree, so a missing folder can't stop a promotion half way. On approval, in order: pending changes are consolidated
(`consolidateVersions`: one new line in each XSLT's version history, one new row in each doc's version control);
missing folders are created (one made since the review is used, never twinned; tagged `mcp-generated`, not
`mcp-managed`); each promoted pipeline has its StreamAppender `feed` property removed where it named a build feed
other than a group's output feed of its own (`OutputFeeds.restoreOutputFeed`: in production its Events go into the
input's feed); then write-backs, moves (untagged `mcp-managed`, so the agent may no longer change them directly),
discards and keeps. The build's record entries go, the tidy-up is carried out (below), the build folder is removed
if empty, and `PromotionProcessing.createPromotionFilters` makes a feed-wide filter per promoted pipeline,
**disabled**, with no minimum creation time.

**Tidy-up** (`PromotionTidyUp`; decided by the user 2026-11-17), on by default. What it will do is read on both calls,
before anything moves (`plan`), and listed in the review's `tidy_up` and at the end of the layout tree (`Tidy-up after
promotion (the user can untick it in the approval):`), so the approval, bound to the tree, is bound to it too. The
review id and the approval are bound to each item's identity, kind and action, not its live counts (the tree shown
gives the stream counts, sizes and shard counts; the one digested doesn't): a test feed still being written to keeps
its review id, and only another set of items is reviewed again (found in review, 2026-11-18: bound to the counts, the
approval never came). The approval form refers to it (`tidy-up after promotion: <n> test document(s), as listed in
the chat (stream counts and sizes as of the review)`) and has it as a
choice, `Tidy up the build's test data after promotion (as listed in the chat)`, ticked to start
(`Consent.Request.withOption`). Unticked, or `tidy_up=false` (no choice is shown then, and the layout is another to
review), nothing is tidied. Items, in the order carried out after promotion: (1) each test feed's unlocked streams
marked deleted (`MetaService.updateStatus` with the feed's Data tab expression, `MetaExpressionUtil.createFeedExpression`,
as the Data screen's Delete marks them: logical, data retention purges them; the review gives their count and total
`File Size`), (2) each Lucene test index's shards deleted on every node (`IndexResource.reset`, as the Index Shards
screen deletes them; the review gives their count), (3) the documents deleted as the user: dashboards, then index
docs (an Elastic Index doc recorded as a test index is deleted as a doc only, never its index on the cluster), then
feeds. A failure is kept to its item: its later steps are skipped and it stays (a dashboard stays while an index it
searches does); the others carry on, and the promotion, already done, never fails. Only what the build provably
made is tidied: in the build's own workspace folder, tagged `mcp-managed`, left there by the plan, not a feed the build
works from, used by nothing outside the build (Stroom's dependencies: a pipeline or dashboard naming it; for a feed,
any pipeline whose processor filter reads it, `ProcessorFilters.pipelinesReading`, but a filter selecting stream ids
with no Feed term whose streams are all deleted or gone, which reads nothing: seen in an eval, case 61, an earlier
build's sample filter left on its promoted pipeline picked a stream of that build's test feed, deleted by its tidy-up,
and a later build's test feed of the same name was never tidied up; without Manage Processors that check fails
closed), nor by a document of the build the plan moves to production other than a pipeline (a dashboard
not named `-VERIFY` searching the test index, say: `used outside the build, by Dashboard '<name>' (the promotion moves
it to production)`). What uses each item is checked again once the build is promoted, before its first step: a
promoted document naming it now (a pipeline property naming the test feed that `restoreOutputFeed` doesn't rewrite,
say) keeps it, `skipped: left in the workspace: used outside the build since it was promoted, by ...`, its later
steps not run; a promoted pipeline's own processor filters, made in the build, don't keep a test feed (found in
review, 2026-11-18: checked only before promotion, what the build promoted was still the build's, and production then
used what was deleted). Each test document passed over is listed with why (`Not tidied up, left in the workspace:`). The
build record forgets what was deleted. Never touched: the promoted documents, production feeds and their streams, a
feed the build works from, the survey doc and the build record.

* **Arguments**: `build` (required), `destinations` (object, or JSON text: type, role or document UUID → explorer
  path, in any form `WriteGuard.folderParts` takes, or `keep`), `reviewed` (a `review_id`), `tidy_up` (boolean,
  default true; false keeps the test data).
* **Returns**: first call `{status: needs_review, build, layout, review_id, warnings?, processing_after_promotion?,
  role_locations?, follow_on_filters?, follow_on_filters_passed_over?, pipeline_changes?, index_changes?, changed_in_production?, not_promoted?,
  tidy_up?, hint}`. After approval: `status: needs_showing` (2026-11-23: its layout and tidy-up are for the user
  to see as they are, so Stroom AI's chat gives the result whole up to 64,000 characters; seen in chat 188, a final
  result of 9,856 characters stubbed without `layout` or `tidied_up`, read from the saved file twice), `build`,
  `tidied_up` (per item: `document`, `streams_marked_deleted` or `shards_deleted`, `deleted`, and `failed` or
  `skipped` where it was left; or `tidy_up: not done ...` when the user unticked it), `layout`,
  `folders` (each with a link to open it), `updated_in_place`, `promoted` (what was done), `promoted_with_warnings`,
  `processing_filters` (each with `pipeline_link`, and `before_enabling` for an index that must start fresh, or for a production index what the user decides instead) with a
  `next` telling the user to review and enable them, `follow_on_filters`, `follow_on_filters_passed_over`, `next_steps` and `user_actions` (buttons:
  enabling the new filters; none disables a filter).
* **Follow-on filters** (`PromotionProcessing.followOns`, `createFollowOnFilters`; decided by the user 2026-11-17): for
  each new events (or records) version promoted, every filter outside the build and every other build with a
  `Pipeline IS_DOC_REF <predecessor>` term (a CEF flattener's, an indexing pipeline's), not one selecting stream ids,
  gets a sibling on its pipeline: the same selection, priority, task limit and minimum creation time, the Pipeline
  term naming the new version, created **disabled** (`copied_from`, `reads`, `in_place_of`; its enable button after
  the new version's own filter). One already made (promoted again) isn't made twice. The filter it follows is never
  changed, disabled or removed. A follow-on pipeline with a new version of its own in the build (`version_of`) is
  passed over, and so is one writing to the index (a test index taken as the production index it stands in for; an
  Elasticsearch index by name) that a pipeline the build promotes indexes the new version's Events into already, or
  an earlier follow-on does (`withoutDoubleIndexing`): never two filters handed over indexing the same Events into
  the same index (seen in an eval, case 61: the build's indexing pipeline, made afresh, and a follow-on on the
  production one both indexed the V1.1 Events into the group's index). Those are said in
  `follow_on_filters_passed_over`, in the review, the approval form and the result. The review lists the follow-ons
  made (`follow_on_filters`) and so does the approval form. The
  result's `next_steps`: `1. Enable the new version's filters when ready: filter <id> ('<pipeline>'); ...` then `2.
  Once the new version's output is right, the admin disables or removes the earlier version's filters (the tools
  never do): '<old>': filter <ids>; '<follow-on>': filter <id> (reading the Events of '<old>').` The buttons that
  disabled a predecessor's enabled filters are gone (the user: that is the admin's).
* **Refuses**: no `build`; a build with no documents; `destinations` that isn't an object; every document without a
  destination, named in one refusal with the destinations already placed (seen: Delinea SecretServer, one failed call
  per type); a blocking check (`Not promoted: ... once promoted, the pipeline's documentation can only change through a
  working copy`), because an agent once promoted with stale documentation and then made a second doc trying to fix
  it. A hand edit to an XSLT only warns, so a user's own edits never hold a promotion up. A working copy whose
  original changed since it was taken, over a client that can't show forms (or when the form would be too large to
  show): `Not promoted: <what changed, the difference and the choice> Promotion needs a client that can show forms to
  ask the user.`; no choice made in the form: `The user made no choice: ...`. A survey doc's place left until
  promotion, over a client that can't show forms. An aggregated feed's index clash still open (above). A failure part
  way is refused with what was done before it and what is still in the build (each step is its own write),
  pointing the modules at the originals or recording the indexes in the registry included (`Not promoted: ...`).
* **Notes**: `PromotionProcessing.promotionProcessing` decides what each pipeline goes on processing: the feeds and
  stream types of its sample filters (the feed's own stream type, e.g. Raw Reference), or for a build on existing data
  its source feeds, never a test feed (seen in VS Code: the promoted filter selected the test feed). An indexing
  pipeline reading Events is filtered by its source pipeline (`Pipeline IS_DOC_REF`), not a feed. A pipeline with an
  enabled filter already selecting what arrives gets no second one (it would process everything twice). An index read
  by a pipeline promoted with its events pipeline has the events pipeline's filter made first (seen in e2e: both
  enabled together, the index held the sample twice). An index starts fresh: `before_enabling` says to drop (or empty)
  it first, with a button. A production index is never put up for dropping (`PromotionProcessing`): an original a
  working copy was written back over keeps its live index (no drop or empty), and a new version's own Elasticsearch
  index is dropped only while nothing but this promotion's documents writes to it or names it
  (`ProductionIndexes.claimsBesides`); otherwise `before_enabling` says it is a production index, not dropped, and
  why. A Lucene index is emptied (its shards deleted, with a button) only when it is an index doc this promotion
  moved out of the build, the build's own; a production Lucene index (any other: a joined group's, say) is never put
  up for emptying (seen in an eval, case 61: the handover asked the user to delete the shards of the group's
  production index, its V1.0 history with them, before the V1.1 indexing filter). Its `before_enabling` says instead
  (`PromotionProcessing.productionLucene`) that it is a production index, never emptied or dropped for a promotion
  (the build indexed only into its test index); that the filter indexes only the new version's Events, none of which
  it holds yet, beside what it holds; and that where the new version's own filter translates streams an earlier
  version translated and the index holds already, their events are indexed again: the user decides with the admin
  whether that is meant, or limits that filter in Stroom to the streams arriving from now on. No button.

Since 2026-11-24 (chat 192):

* A document going with a pipeline of one version but named for another (its version token, the feed's and group's
  names held, isn't the pipeline's) is promoted under the name for the pipeline's version: the layout shows the new
  name and warns `XSLT '<name V1.0>' is named for V1.0 but goes with pipeline '<V1.1>' (V1.1): promoted as '<name
  V1.1>'` (`PromotionLayout.forVersion`, `renamed_from`); the rename (`WriteGuard.rename`) is made as it is moved, so
  the review id doesn't change between the review and the approval.
* `reviewed` left out after a `needs_review` in the same chat (its review id and a digest of everything it showed but
  its hint kept in the chat's workspace, `UnsavedChoices`, `promotion:<build>`): where nothing shown has changed
  (layout, warnings, processing, index and pipeline changes, follow-ons, tidy-up), the approval form comes as if that
  id were given, the result's `reviewed` saying so; otherwise the review is shown again, its hint saying what was
  shown before has changed.
* A new version replacing a promoted pipeline: before the approval form, the user chooses which streams its new filter
  reads: "From now on (recommended)" (a minimum stream create time of the moment the filter is made), "Everything
  (backfill)" (none, as before), or "From a date" (asked next, yyyy-MM-dd, UTC midnight); the filter is still made
  disabled, its `reads_from` and the enable button saying the bound, the approval form listing the choice and the
  result's `new_version_reads` saying it. Without a form: from now on, said so. A first version's filter is unchanged
  (`PromoteBuildTool.backfill`, `PromotionProcessing.createPromotionFilters(..., since)`).

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
  files, `files` and `differences`), `read` notes for streams, `sample_note` for pasted text over 20,000 characters;
  for JSON read by the JSONParser, `long_strings`/`long_strings_note` and `nul_strings`/`nul_strings_note`
  (`JsonStringValues`).
* **Refuses**: nothing given; a sample that is a path, attachment reference, cut-short file or broken-up text; stream
  ids without View Data or older than their feed; text that starts as JSON but isn't valid JSON, with where and why
  (seen: a FortiOS file retyped by the agent with `\+`, profiled as key=value).
* **Notes**: onboarding a source, `start_onboarding` profiles itself; each `profile_sample` call sends the text again,
  so the description says not to call it first.

### `survey_feed`

Processing · write with a `build` (keeps the survey doc), read-only without one · requires View Data; checks View on
the feed; the survey doc is made or changed in the build's folder as the user · changes only its build · takes a run
slot · chat group build · Classes: `SurveyFeedTool`, `FeedSampling`, `RecordSplitter`, `Shapes`, `SurveyDoc`,
`SurveyDocuments`, `SurveyDepth`, `FewerStreams`, `StreamReader`, `Consent`

Samples an existing feed's streams spread over its lifetime and groups their records into shapes (kinds of event),
saying plainly whether the feed is covered yet.

**Kind fields.** A hint for the survey's own coverage only (its shapes, saturation and depth): the source's event
types are catalogued by `catalogue_event_types`, which never takes them as its conditions, and the draft no longer
makes its rules by them. For structured records (delimited, JSON, key=value) the fields that tell the shapes apart are
chosen once for the survey (`KindFields`), from the first stream's head, and kept in the survey state as `kind_fields`
(`fields`, `by`: `user` or `tool`); every part is then signed by them, rather than by the columns each part's names or
values suggest (seen in testing: a CSV mail log's `msg_act`, chosen afresh in each part, 16 kinds from 3, never
covered). A field named like a kind field (`type`, `action`, `event_id`...) whose values aren't one a record, nor
addresses or times, and which has a value in most records, is chosen without asking; one on fewer records is a
candidate with the rest. A qualified name (three or more identifiers joined by `.`, `::`, `/` or `$`, such as a Java
logger's class) is a code at any length up to 200 characters, its last part shown in the form's label. Fields naming
where a record was handled (hosts and nodes, levels and severities, a shipper's `collector` or `agent`) are never
offered, though the user may name one. Where no field is chosen, but fields with a few short values that repeat could
be, they are put to the user in a short form (each as `field (value, value...)`, or "All one kind of event", or a
field they name); without forms the call reads nothing and returns `status: needs_kind_field` with the candidates,
and the agent asks in the chat and calls again with `kind_field`. A `kind_field` other than the one kept starts the
survey afresh (`kind_field_changed`). The head read to choose is the one the survey reads on from. An aggregated
feed (a source type field of any reading: a field, a held value, a combination, a meta attribute) is asked no kind
field and has none chosen over its records together (2026-11-18: 65 source types, a held reading, and only the epoch
times they shared put to the user): `kind_fields` is `{fields: [], by: "source_types", within: <field>}`, the survey
doc says the event types are told apart within each source type, by catalogue_event_types over the build's
scope. A `kind_field` the user names is still kept (`by: user`). `KindFields` never offers a field named as a
time (`time`, `timestamp`, `ts`, `date`, `*_time`, `*_ts`, `*_rt`, `eventTime`...) nor one whose values are all
epoch times (seconds to nanoseconds, 1990 to 2100, as `TimePatterns` reads them; refused as a given kind field too).

**Settling the survey.** A stream read only in part that a call can't read deeper (past the parts a call reads,
records over several lines, or the most a call reads), once the survey has had to read it whole (given by id, or
the feed's last), is put to the user: "Stop and accept them as read" or "Step records further in first"
(`read_deeper` without forms); a stop is kept as `stopped_streams` and settles it, and no hint says to raise
`max_chars_per_stream` for it. `accept_as_is` (asked for by the user: a survey that couldn't be covered and a user
who wanted to go ahead with a limited sample) reads nothing: the user confirms going ahead with the survey as it is
(in a form, or as they said in the chat: where no form can be shown, or its form couldn't be, `accept_as_is` is
their answer, and `true` their yes once the agent was told to ask them; seen: `true` sent back for the user's yes
asked for it again, for ever), and the state is covered as far as they chose (`accepted`) until the survey reads on. The gate on create_processor_filter (`BuildPlan.uncovered(build, streamIds)`) lets a filter over
stream_ids the survey read whole (or stopped at) go ahead uncovered, and a build on an existing feed with no survey
record has its `covered` step still to do (seen: the agent deleted the record to get past the gate). Streams are picked in bisection order (newest, oldest, middle,
quarters ...) by `FeedSampling.pickSpread`, skipping those already read; with `stream_ids` exactly those streams are
read. For each stream up to `max_parts_per_stream` parts are read with `StreamReader.readHead` (a stream of more parts
has them chosen spread through it, its first and last among them, `PartSampler.spread`; seen in user testing: an
OpenWebUI audit stream of 787 parts read through its first 20 only), every part chosen read to
`max_chars_per_stream`, its own budget (never shared by a stream's parts: different parts can carry different
sources, read breadth first); a head that is one JSON array cut by the read is read again as its whole events up to
the same budget (`StreamReader.samplePart`), one too long to sample left out and said in `per_stream`. Each part is split into records by
`RecordSplitter` (same detection as the profile), and added to `Shapes` (see "Sample analysis" for how a shape is
signed). Where a part is longer than its head and its format is one record a line (delimited, JSON lines, key=value,
CEF, CRI, unknown text, and mixed lines whose records are each one line), up to 8 windows of 250,000 characters spread to the part's end are read too
(`StreamReader.readWindows`), their records placed where they are, so a kind of event deep in a big stream can still be
found. A part in a different format from the first is skipped and said.

**Coverage.** A call is `saturated` when a call with known shapes reads a full `max_streams` with nothing new, when
fewer streams were left to pick than asked (the feed's last ones), or when the given `stream_ids` were all read. The
feed's last streams and given streams are read whole up to `ai.sampling.maxCharsPerStream` (2,000,000 characters)
and 10,000 records a part; a feed's other streams up to 250,000 characters and 1,000 records a part. A stream read only in part is said with the parts read (`per_stream` `parts_read`), not "can't read further
into it". A given stream, or a last stream, read only in part leaves
the result `NOT COVERED`, never "covered" (seen in Ask Stroom AI: 334 of a stream's 1,000 records read, reported
covered; in production a 750 KB sample read to its 108th record hid most of its kinds of event). When the cap was
already the maximum (5,000,000), the advice is to step records further in and rely on `review_coverage` once
processed, rather than survey again at the same size (seen: the advice looped).

**Reading deeper.** The record budget is each part's own, not shared by a stream's parts (asked for by the user: a
source can write different data to different parts). A part of a stream to be read whole (a given stream, or a
feed's last) is read in order from its start, three quarters of its budget, its windows the rest as a first look
further in. A part of a line format left only in part is put to the user in a short form (`SurveyDepth`; seen in
chat 163: a 25,000-record stream surveyed to its 10,000th record, its two rarest kinds, near its end, never seen nor
classified): how far each part was read and about how many records it holds (from its size, `StreamReader.partSize`),
with Stop here, Read 10,000 more, Read to the end (offered while the parts have at most 500,000 records left), or
the user's own words ("another 5,000", "up to 30,000"). Read deeper, a part goes on from where it stopped, in order
(`StreamReader.rawRecords`), skipping the records its windows read, so none is counted twice. Each part's depth and
the user's choice are kept in the survey doc (`depth`, by `stream:part`): a part they chose to stop isn't asked
about again, and one a call's time cut short is read on by the next call, not from its start. The result's `depth`
says each part's progress; the survey is covered once every such part is read whole (`covered: every part read to its
end`) or the user chose to stop it (`covered as far as the user chose: ...`). Where the client shows no forms, the
agent asks in the chat and passes the answer as `read_deeper`. A window or a deeper read of a mixed-lines part is
split a record a line whatever formats it holds (`RecordSplitter.splitAs`), as a run of one format's lines would
otherwise read as that format and be skipped. The streams read only in part are kept in the survey doc
(`streams_in_part`) until a later call reads them whole: a call with no streams left to pick says the survey is
complete only once each is settled (read to its end, or the user chose to stop); otherwise it is `NOT COVERED` and
the hint names the `stream_ids` to read further (seen in testing: a mixed-lines stream read to its 10,000th record was
never offered deeper, a later call said the survey was complete, and its rarest kinds were found only at processing).

**Fewer streams.** Fewer than 10 streams a call (a smaller `max_streams`, or fewer than 10 of an existing feed's
streams by id) is confirmed by the user in a form, but only when the feed holds more streams than that (seen: a
one-stream feed's user asked to agree to survey that one). With a build, the agreement is kept in its survey state
(`fewer_streams`) and holds for the build, restarts included; without one, for that user, feed and count for four
hours in this node's memory (`FewerStreams`). Streams of a feed a build made (its uploaded sample) are read as they are.

**The survey doc.** With `build`, the survey is kept in the build's Documentation doc `<feed> - Survey` (`SurveyDoc`
format, `SurveyDocuments` through `WriteGuard`), and an existing one is continued: its streams are skipped and its
shapes known. Streams the doc names that are gone since (deleted, or no longer there) are dropped first
(`GoneStreams`, `SurveyDoc.withoutStreams`): no longer counted as read, their examples no longer locations, and a shape
seen only in them is surveyed again so it gets locations where the data is now (`gone_streams`, `gone_note`); the
survey is then not covered until read again. The plan reads the doc the same way. Seen in testing: a feed's streams
deleted part-way through a build and uploaded again; the shapes the survey knew kept locations only in the deleted
streams and couldn't be stepped. Kinds the user chose to leave untranslated (`set_shape_handling`) are marked `handling: drop` and their
locations carry `expect: none`. The doc's format is stored data, so it changes only in ways a survey doc already
kept still reads (see `SurveyDoc` under "Sample analysis").

* **Arguments**: `feed` (or `stream_ids`), `stream_type` (default Raw Events), `stream_ids`, `max_streams` (1 to 100,
  default 10), `skip_stream_ids`, `known_signatures`, `max_chars_per_stream` (1,000 to 5,000,000; each part's
  head, its own), `max_parts_per_stream` (1 to 20; default every part, up to 20 spread through the stream, for
  `stream_ids` and a feed's last streams, which are read whole, else 3: seen in testing, a feed's latest streams
  uploaded again as one stream of five parts was surveyed through three), `max_records_per_stream` (10 to 10,000), `examples_per_shape` (1 to 10,
  default 2), `build`; as the user said: `kind_field`, `read_deeper` (no forms), `accept_as_is`,
  `source_type_field` (`none` for one source; `meta:<Attribute>` for a stream meta attribute, below; a reading into
  held values, below), `aggregated`
  (below); `find` (a registry row's name, below).
* **Returns**: `feed`, `format`, `time_range`, `streams_read`, `records_read`, `records_in` (an XML document's root
  with its namespace, which the examples inherit), `saturated`, `coverage`, `per_stream` (each stream's records,
  parts, `parts_read` (a stream of several), `head_only`, `new_shapes`, `windows`, and any undecodable bytes), `quoted_newlines`, `kinds` (each field
  naming the kind of event, up to 6, with how many records hold each of its values, up to 25, over every shape;
  seen: a 790 KB survey regrouped by x-type and x-action with jq), `shapes` (up to 60:
  `signature`, `new`, `count`, `share_percent`, `streams`, `example` up to 500 characters, `more_examples` (up to
  two more records whose text differs, 300 characters each, for the first 20 shapes), `locations`), `new_shapes`,
  `locations` (stream, part, record, shape, for `step_records`; only new shapes once some are known), `hint`. On an
  aggregated feed each shape and location also gives `source_types`: the source types its records are of (their
  source type field's values named by the scope's or registry's rows, the values themselves before there are any;
  seen in chat 177, a SharePoint record's address used to check a messaging group's translation). With a
  build, the survey doc's details too, and on an aggregated feed `source_types` (`field`, `by`,
  `records_read_by_value` (the 20 commonest), `registry`: the call that writes it; or `aggregated: false`) and, with
  a scope, `scope` (`build`, `source_types`, `share_percent`, `records_passed_over`, `note`) and `read_again`. Or
  `status: needs_kind_field` or `needs_source_type_field` (no forms; nothing surveyed), or `needs_user`
  (`accept_as_is` without forms).
* **Large, outside Stroom** (`SurveyOutline`, 2026-11-20; cases 36, 56, 61, 65, 66: 52k–109k): `locations` counted by
  stream with `step_them` (`step_records locations_from=<saved>`), then each shape without its locations and further
  examples, its example cut short, then left out; signatures whole. Saved to `/outputs/survey_feed/<build, else feed>.json`.
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

**Aggregated feeds.** With a build, the first stream's head is also read for a source type field
(`SourceTypeFields`): one field (at any depth, e.g. `event.Workload`) whose values tell apart records that differ in
their fields or their message's format or template, each value holding kinds of event of its own (a kind field's
values are each one shape, so a kind field isn't taken); hosts, users, sessions and messages never, addresses and
times by their values. Chosen by values and shapes alone, never by a product's names. A candidate is put to the user
in a form (`Which field says which source a record came from?`, the candidates by label, and `Not an aggregated
feed: one source`); without forms, `status: needs_source_type_field` with the candidates, nothing surveyed.
`source_type_field` gives the user's answer (`none` for one source); `aggregated: true` says the user called the feed
aggregated (weaker separation is enough, and a field of one shape a value is offered). The choice is kept in the
survey state (`source_types`); no kind field is then chosen or asked for feed-wide (see **Kind fields**). A field named for
kinds of event (an event type, action, operation, `SourceTypeFields.mayName`) is never a candidate. Each record read
is counted by its value (`SourceTypeProfiles`: records, bytes, days, streams, message formats and parse failures,
fields, shapes, kinds, levels, signals); the result's `source_types` gives the field and the records read of each
value, and names `record_source_types`. A survey with no build notes a likely field (`aggregated_feed`) and offers an
assessment build (`no_build`). A build that then translates the feed whole, with no source type group, keeps the values
the survey read with its mapping, and its XSLT logs a value outside them once a stream (`build_translation_xslt`, a
feed translated whole).

**A source type read inside a held value** (decided 2026-11-17, built 2026-11-18). The source type field may be a
reading (`SourceTypeReading`): a field, then `>` into the structured data its string value holds (`HeldText`: JSON,
encoded once or twice; XML; an RFC 5424 or RFC 3164 syslog header's `host`, `app`, `message`; two or more key=value
pairs; else text, never read), its format told per record, then `.` into its objects or elements
(`event>payload.logger_name`), at most three held values deep (`event>payload>inner>@n`). A step's names read whichever
format the record holds (a JSON key, an XML element below the root by local name, a key=value key, a syslog field);
`xml:`, `json:`, `kv:` or `syslog:` names one format; `@name` an XML attribute, `#root` the root's name; `#format` the
held format itself (a last resort, the user's only, never offered). Readings tried in turn are joined by ` | `, the
first with a value giving it. The head's candidates include the names inside held values (`HeldText.expand`,
`HeldCandidates`), each judged over the records whose held field is in a format it is seen in (most must hold it, and
a quarter of all records), with the records' shapes taken with the held field's structure; a held field whose formats
name the source differently is offered first a reading joining them (the best name, then for each format it misses a
name of that format whose values are like its own, a value in common or both structured names, qualified by its
format: `event>logger_name | event>xml:@logger`); a record's own field comes first unless a held reading separates
better by 0.1. The form says
each held candidate's formats and its records with no value; without forms each candidate gives `formats` and
`no_value`, and `held` says how readings work. A reading the user gives is checked (`SourceTypeReadings`): its syntax
(refused saying why), its fields spelt as the records spell them, and refused when it reads no value from any record
read (naming the held formats seen). Records with no value are the `(no value)` row's (`record_source_types`).

**A combination of readings.** Where only two fields together tell the source (the wrapper's `system` and, inside
the held `event`, `logger_name`), the field is their readings joined by ` + ` (`system + event>logger_name`, at most
four parts, none a meta attribute, nor one among a part's readings tried in turn), its value a key of the parts'
values joined by ` ‖ ` (`Teams ‖ System`, a part
of no value empty), counted, profiled and scoped by the key. A pair is offered first (`SourceTypeCombinations`) only
where every single candidate offered has a value another reading splits into families (that reading's values with
shapes of their own, several shapes each: a kind is one shape, never a family) and each part so splits a value of
the other; kind-named, free-text and address or time fields are never parts, and three or more parts only as the
user names them (`source_type_field=a + b + c`). The form's line says its pairs of values, the evidence (`within
system = Teams, event>logger_name System and Application have shapes of their own`, and the other way) and the source
types proposed; without forms the candidate gives `combination`, `evidence` and `rows_proposed`, and `held` says how
combinations work. The rows proposed (a row each for a next part's values that are families, `*` for the rest or
where they share their shapes) are kept in the survey state (`rows_proposed`) for `record_source_types`; a
combination the user names has its rows proposed from the head the same way. Each part the user gives is spelt as the
records spell it, and refused where it reads no value from any record read.

**A stream meta attribute as the source type field.** Only where the user names it, `source_type_field=meta:<Attribute>`
(never found unasked; on its own, never among readings tried in turn or a combination's part, which a group's XSLT
can't read): the attribute each part of a stream carries in its meta (its receipt headers, what
`stroom:meta()` reads while the part is processed or stepped; `SourceTypeMeta`), for a feed whose source is said by
a header, not a record's field (a proxy's aggregate of many hosts' parts, each with its own headers). The parts of
the streams to be read are checked, up to 200 (`PartAttributes`): the attribute is spelt as the first part carrying
it spells it (any case given); none carrying it is refused, naming the attributes they do carry, commonest first,
with how many parts carry each. Each part's meta is read once (`StreamReader.partAttributes`) and laid over each of
its records under the field's name (`Shapes.usePartFields`, never in their signatures), so the profiles, scope,
`find=`, `record_source_types` and the registry work on meta values unchanged; a part without the attribute is of no
source type. A feed's registry has one field: all meta or all a record's. A scope by a meta field keeps or leaves out
a whole part by its value, whatever its format, XML too (`SourceTypeScope.filter(text, partFields)`;
`SampleStreamTexts.scoped` for catalogue_event_types, draft_translation_mapping and build_translation_xslt's sample
check); `review_coverage` tells a kind's source type by the meta of the raw stream's first part of the pipeline's
source types (a record's part isn't logged).

**A build's scope.** Once the build's scope is chosen (`source_types.scope`, kept by `record_source_types` with the
rows it was chosen by: `SourceTypeScope`), only the scope's records make shapes (`Shapes.scope`): a record is the
scope's when its source type field's value, by the rows in order (the first match winning), names a source type of
the scope; a record of another source type, of a value no row matches, or with none is passed over, counted by
source type for the registry's profiles, and nothing more. So the kinds, saturation, coverage and the depth asked
about are the scope's, and `records_read` counts the scope's records. The budget a part is of the scope's records: a
head (or window) is split to as many as ten times as many records to find them (`SCOPE_SPREAD`), read in order to the
budget's worth of the scope's (whole streams, windows) or spread over as many records as the scope's share of the
head asks (sampled heads), so a scope of rare source types is read deeper than a whole-feed survey would read it.
Each stream read is signed by the scope (`scope_read` in the state: its source types, or `*` for every one; a stream
read before scopes were kept is `*`); a stream read for other source types only (a narrower scope, another build's)
is read again for the scope's source types it wasn't read for (`read_again`), its records not counted for the
profiles twice. Shapes keep the source type field's values their records had (`source_values`). The result's `scope`
gives the build, the source types, their share of the records the survey counted (an estimate) and the records
passed over; `coverage` ends with the scope and its share. A scope chosen by another build is not read through.

**Finding a source type (`find`).** With a build, `find=<row>` looks for one registry row's records (the build's
registry, else the one beside the feed; refused for a row it lacks, with no build, or with no source type field, and
with `kind_field` or `source_type_field`, as it reads with the survey's own). It reads the feed's streams the build's
survey hasn't read, newest first (up to 500 a call), or just `stream_ids`, through a scope of that row alone matched
as the registry matches (the first row winning), until the row has `examples_per_shape` records (streams named are
all read), the streams run out, or the call's time does. Its records count into the build's survey as any survey
reading's (shapes, source type counts); each stream read is signed for the row alone, so a later survey reads it
again for the others; saturation and acceptance are left as they were, and nothing is asked. The result's `find`
gives `source_type`, `match`, `found`, `records_found`, `streams_read`, `records_of_other_source_types`,
`streams_holding` (each `stream`, `created`, `records`), `values_held` (the five commonest values, when none was
found) and the row's `profile` now; the hint says, plainly, where they were found, that the streams given held none,
to call again to carry on (time out, or older streams left), or that none was found and the source type may not have
arrived yet. `record_source_types` offers it for rows the survey hasn't met (`unseen_source_types`).

**The survey doc stays in its build.** Promotion leaves a build's survey doc in the workspace (decided 2026-11-13;
nothing asks where it goes), so a later build or survey of the feed starts its survey afresh; a later build still
finds the source type registry beside the feed. A survey doc an earlier version promoted beside a feed is ignored.

### `record_source_types`

Processing · write · checks View and Edit · confirms itself · chat group build · Classes: `RecordSourceTypesTool`,
`SourceTypeRegistries`, `SourceTypeRegistry`, `SourceTypeRows`, `SourceTypeProfiles`, `SurveyDocuments`, `Consent`,
`BuildPlan`

The plan's `source_types` step for an aggregated feed. Rows come from the values the survey counted: a value each,
or, where the values are many structured ones or share containers (paths, URLs, dotted names), anchored patterns a
container, narrower before wider, a common root's own values kept as rows of their own; patterns are put to the user
(`Use these patterns` / `One source type per value`). Where the values are lists (at least two, and 30% of them, hold
one of `,` `;` `|`, the commonest; their items repeat across values; at most 40 items, each at most 80 characters),
a list-member row an item is proposed instead, the rarest item first (a list holding a rare and a common item is the
rare one's), an item no value reaches first left out, and put to the user (`Use these list items` / `One source type
per value`). Rows given (`name = value`, `name ~ ^pattern`, `name ∈ item`, or in ASCII `name in item`: the first of
` = `, ` ~ `, ` ∈ ` divides name from match, ` in ` only where none is there) are checked: anchored, at most 200
characters, no repeated group of repeats, names once, an item without the separator or white space at its ends, and
none that an earlier row always matches first (an item after the same item, a whole value after an item it holds, a
row every value of which an earlier row takes). A list-member row (`match: member`, `SourceTypeRows.MEMBER`) matches a
value split on its `separator` (inferred from the values seen, kept on the row), each item stripped of XML's white
space and compared exactly (case and all). A registry already in the build, or beside the feed, is carried on from:
its rows and statuses kept, values it lacks added as `new` (a row a value; where its rows are a list's items, a row for
the value's rarest item no row has). Where the survey read records with no value (a held value of plain text, the
held field or the field missing), the `(no value)` row (`match: none`, `SourceTypeRows.NONE`, matching only records
with no value) is added last, unless the rows are given (`(no value)` gives it); it may be in a scope like any other
source type, and a group translating it selects records whose reading finds nothing. Rows given again keep every
row of the registry they leave out (parked ones too), each where it was in the order; only `remove` drops rows, by
name. Removing a translated or
placed row, or a changed value or pattern on a translated row, is confirmed by the user in one form (plain text,
listed as `to_confirm` in the review), and refused for a translated row where no form can be shown; a translated row
renamed (a new name with its match) is refused, saying to add the new row and remove the old one. For a combination
of readings (a field joining readings by ` + `), rows match its key part by part (`match: tuple`, its parts in
`value`: `= Teams + *`), written `name = part + part`, each part `value` (exactly), `~ ^pattern`, `∈ item` (`in
item`) or `*` (any value, none too), e.g. `Teams System = Teams + System`, `Skype ~ ^Sk + *`; a part of no value is
matched only by `*`, else the record is the `(no value)` row's. Refused: parts not the combination's count, an
operator none of these, an empty part, `*` in every part, a one-value row on a combination, and a row an earlier row
covers part by part (or, without a `*` part, every key seen of which an earlier row takes). Rows start from those the
survey kept with the combination, a key they miss given a row matching each part exactly; with none kept, a row a
key whose parts all have a value. The review's `rows` give every row back as it is written (`(no value)` alone; a
combination's `name = part + part`). Each row's profile is the sum of its values' (`SourceTypeProfiles.summarise`):
records and bytes read and their shares, an estimate scaled to the streams read, per day, streams seen, formats and
failures, fields (the wrapper's aside) with fill rates, shapes and the commonest's share, top kinds, levels, signals
each with what it rests on, a suggested category (security audit, operational, network, diagnostic noise) with a
confidence, examples; and `rests_on` (`not seen in the N records read`, `from only N of ...`). The first call returns
`needs_review` with the table, its `review_id` and the rows as confirmed; called again with `reviewed`, those rows
and `group` (the name the agent suggests), the user chooses in a form which source types go in the build's source type
group and what it is called (decided 2026-11-13: one source type group a build, any number of source types). The form
(plain text): "Which of feed 'x''s source types go in this build's source type group? A build makes one group,
translated by one pipeline. ... Those you leave out are parked for a later build." (and, where the registry has
groups, "Naming an existing group (...) adds them to it."), one list to tick, "Source types to put in the group"
(`Consent.pick`: a multi-select of every source type offered, each with its share and formats under it, none ticked
to start; one field however many source types, so 65 of them fit, where a yes/no field each could never be shown),
and one text field, "What is the group called?", filled with the suggested name, which the user keeps or changes
(left empty, the suggestion stands). A scope form that still can't be shown (too large, or the client couldn't show
it) says so in the `needs_user` hint, and the next call takes `scope=` and `group=` as the user said them in the chat
(`Consent.takesChatAnswer`), never asking the form again (decided by the user, 2026-11-18). The patterns' and list
items' forms take their answer whole (`Use these patterns`, `One source type per value`, or `use`/`exact`, any
case): other words, such as a question, are put to the agent to answer, the question then answered in the chat
(`patterns=`), never by a word they hold (seen in testing: a question saying "pattern" taken for using the patterns).
The patterns' and list items' forms name the first five rows and how many more ("And 60 more (all are in the table
shown in the chat)."). A call with forms and no `group` is refused before
anything is asked. The name matching an existing group's (any case) is that group's: the source types join it (its
pipeline and output feed, when `build_translation_xslt` builds it; a promoted one through a working copy of its XSLT),
said in `joins`; with none chosen, the build changes that group alone (without forms, `scope=[]`, an explicit empty
list, with the group's name; ignored where forms are shown). A new group with no source type chosen is
refused. The scope's `source_types` are the group's: those it translates already, and those chosen. Every source type offered is then `parked` (the
scope's saying so, naming its group; none is `translated` until its group is built; the rest with the user's reason,
else "left for a later build"), `dropped` only by `dropped` with `drop_reason`; the scope (`build`, `group`,
`source_types`, `by`, `at`) is kept in the registry and the survey state, the survey's with the
rows it was chosen by (`SourceTypeScope`), and the build's tools then read the feed through it (survey_feed,
catalogue_event_types, draft_translation_mapping, build_translation_xslt's sample check, review_coverage). A scope
that adds source types some stream the survey read wasn't read for makes the survey not covered again
(`coverage_restarted`): survey_feed reads those streams again for them. An assessment writes the registry at once,
every source type
parked, with no review or form. The registry, `<feed> source types` (`SourceTypeRegistry`), has an account
of the feed, a table a row a source type, and a machine block `<!-- stroom-mcp source types {...} -->` holding the
field, the feed's Environment and default System Name (as given, else the registry's, else the build record's
`system`), the feed's `modules` (helpers, common module), its shared `indexing` and `indexes` (`draft_index_mapping`,
`promote_build`), the build's `scope`, and each row's match, System Name, status, decision, group, `group_xslt`,
pipeline, output feed, `references` and profile. A registry recorded again (a later build) carries all of these
over. The review is bound to the table (`Consent.wasReviewed`): other rows, or changed profiles, show it again.
Once the build's scope is recorded, a call that leaves it as it is shows neither the table nor the scope form, but
writes the call's settings (System Names, Environment) and says the scope stands: its rows come to the registry's
(rows it leaves out kept; exact-value rows in any order, as their order matches no record differently), it names the
recorded group or none (any case: no rename to confirm), no `remove`, `dropped` naming only source types dropped
already, and `scope`, if given, coming to the same source types (seen in chats 178 and 182: the scope form asked again
and answered the same, for calls recording the System Names and Environment). Another group name is a rename the user
confirms; other rows, or pattern and list rows in another order, are reviewed again; other source types ask the
scope form again.

* **Arguments**: `feed` (required), `build`, `reviewed`, `rows` (the `rows` a `needs_review` reply gave back, or as
  the user says), `remove` (rows to remove, by name, on the user's word), `patterns` (no forms, or its form couldn't
  be shown: `use` or `exact`), `group` (the group's name: the suggestion the form shows, or, without forms, the
  user's), `scope` and `reason` (no forms, or the scope form couldn't be shown; `scope=[]` with an existing group's
  name changes that group alone), `dropped`, `drop_reason`, `system_name`, `system_names` (by source type), `environment`.
* **Returns**: `status: needs_review` (`feed`, `field`, `table`, `review_id`, `rows`, `to_confirm`, `hint`); `status: needs_user`
  with `proposed_rows` (each `name`, `match`, `values`, `records`: the patterns, without forms) or `offered` and
  `groups` (the source types for the scope and the registry's groups, without forms); or `feed`, `field`, `registry`
  (`type`, `uuid`, `name`), `rows` (each `name`, `match`, `status`, `reason`, `records_read`, `share_percent`,
  `suggested_category`, `confidence`), `table`, `scope`, `group`, `joins` (an existing group named), `coverage_restarted`,
  `unseen_source_types` (rows nominated, or in the group, with no record read and not dropped: the hint offers the
  user a stream id they know, or a search, both `survey_feed find=`), `hint`, `next`. A scope just chosen also gives
  `chosen_by: user` and, first in the result and first in its hint, `user_chose`: what the user chose, said plainly,
  with how it differs from the agent's suggestion ("The user chose in the form: source types [ExchangeOnline,
  MicrosoftTeams] in group 'Messaging' (you suggested group 'Azure Audit' and source types [...]). Don't ask them
  again."), or "as you gave it from the chat" without a form; seen in chat 183, where the agent, given `chosen_by`
  and a hint in a result saved to the workspace, said it saw no form and asked the user again.
* **Refuses**: no `feed`; another build open on the feed (one open build per aggregated feed, under
  `start_onboarding`); no source type field known (`survey_feed` first); no values counted; a row that is neither
  `name = value`, `name ~ ^pattern`, `name ∈ item` (`name in item`) nor `(no value)`; rows that can't be used (`These rows can't be used: ...`); a patterns answer
  that is neither; a scope naming a source type not offered; `scope` without `group`; with forms, no `group` to
  suggest; a group with no name, or a new one with no source type chosen (`scope=[]` naming a new group, or no group); `dropped` without `drop_reason`, or naming no source type
  of the registry.

### `set_shape_handling`

Processing · write · checks View and Edit on the build's survey doc · confirms itself · chat group build · Classes:
`SetShapeHandlingTool`, `SurveyDoc`, `SurveyDocuments`, `Consent`, `BuildPlan`

Records in the build's survey doc that some kinds of event are left untranslated on purpose (`drop`, with the user's
reason) or no longer are (`translate`). Called first, it gives back the kinds as a table (`# | Kind of event |
Records | Share`, status `needs_showing`: Stroom AI's chat shows it itself, another client's agent shows it); called
again with `confirm: true`, the user confirms in a short plain form that refers to it (asked for by the user: the
kinds had run together in the form's message, cut short).
Afterwards `survey_feed` marks those kinds and gives their locations `expect: none`, and `step_records` counts such a
record clean when it produces no Event (and flags it if it does). The mapping still needs a drop rule for them
(`build_translation_xslt`), so they don't reach the "no mapping matched" warning.

* **Arguments**: `build`, `feed`, `shapes` (signatures, or text found in exactly one signature, e.g.
  `loggerName=org.jgroups`), `handling` (`drop` or `translate`), all required; `reason` (required for `drop`);
  `confirm` (true once the table is shown).
* **Returns**: without `confirm`, `status`, `table` and `hint`; with it, `feed`, `handling`, `shapes` (the signatures
  resolved), `left_untranslated` (every dropped kind with its
  reason), `locations` (the kinds' examples to step, with `expect: none` for drop), `hint`, and `next`.
* **Refuses**: a missing argument; `handling` other than the two; no survey of the feed in the build (`run survey_feed
  with build first`); a shape that matches no signature or several (`SurveyDoc.resolve`); `drop` without a reason; the
  form declined.

### `create_feed`

Content · write · checks View and Edit; the feed is created in the build folder as the user, who needs Create there
and Edit on the feed · confirms itself · chat group build · Classes: `CreateFeedTool`, `BuildFeeds`, `WriteGuard`,
`BuildRecords`, `Consent`, `BuildPlan`, `OutputFeeds`, `SourceTypeGroups`

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

**A source type group's output feed** (`group`; `SourceTypeGroups.checkOwnOutputFeed`, `outputFeed`): for an
aggregated feed's group whose Events the user sent to an output feed of its own (asked when
`build_translation_xslt group=` first saved it), an Events feed (whatever `stream_type` says) made in the build under the
name the user confirms in the form, in a build on existing feeds too. The build record marks it the group's output
(`BuildFeeds.GROUP_OUTPUT`), and the registry's rows for the group name it as their `output_feed`. It is no test
feed: `create_pipeline` sets the group's pipeline's stream appender to it, and promotion moves it beside the feed
and leaves the appender pointing at it. A feed of the name (proposed, or as the user corrected it in the form) is
checked as any other: elsewhere, refused; already in the build, it is taken as the group's output feed, marked and
named in the registry, with nothing made (refused if it holds anything but Events).

* **Arguments**: `build`, `name` (required), `encoding` (default UTF-8), `stream_type` (default Raw Events),
  `description`, `user_samples`, `test_feed`, `group`.
* **Returns**: `type`, `uuid`, `name`, `stream_type`, `encoding`, `link`, a button to open the feed (upload sample files
  on its Data tab), `large_files` (how sample files get there: `upload_sample files=`, or the Data tab's Upload when
  there's no terminal), `note` when Stroom still holds streams of an earlier, deleted feed of the name (the UI doesn't
  show them; they are never the build's samples), and `next`. A test feed gives `test_feed: true`, `works_from` and a
  hint; a group's output feed `group`, `source_types` (the rows now naming it) and a hint.
* **Refuses**: a missing `build` or `name`; a name taken elsewhere or a case twin; on existing data without
  `user_samples` or `test_feed` (`BuildFeeds.worksFromMessage`; asked for by the user after an agent onboarding an
  existing feed uploaded workspace files instead); `test_feed` in a build that works from no existing feed; `group`
  in a build with no source type registry, naming no group of it, or a group whose Events go into the feed's (`only
  a group the user gave an output feed of its own has one`); the form declined.
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

* **Context data** (`context` with `sample`, or `parts` = `[{sample, context}]`; only when the user gives it or asks
  to enrich from it): the parts are packed as Stroom's receipt takes data with child streams, a zip of `NNN.dat` and
  `NNN.ctx` pairs (`ContextSamples`), and sent as `sample-with-context.zip` through `FeedUploads.send`: one stream, a
  part per pair, each with its `Context` child. A file on the user's disk with context data is such a zip, sent with
  `files`. The reply has `parts` and `context: true`, and points to `fetch_events childType='Context'` and
  `propose_context_lookup`.
* **Arguments**: `feed` (required), `sample` or `files`, `context`, `parts`, `headers` (extra receipt headers,
  readable with `stroom:meta()`), `stream_type` (Raw Events, or Raw Reference), `effective_time` (reference data; default
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

### `catalogue_event_types`

Content · write (the build's source notes doc) · checks View and Edit; reads the sample streams (View Data) · changes
only its build · chat group build · Classes: `CatalogueEventTypesTool`, `CatalogueEvidence`, `CatalogueProposals`,
`CatalogueTables`, `CatalogueNotes`, `RecordSourceNotesTool`, `Consent`, and the core in
`stroom.ai.impl.tools.catalogue` (`Catalogue`, `EventType`, `Way`, `CatalogueCheck`, `CatalogueValidation`,
`CatalogueSample`, `CatalogueSeed`, `CatalogueOrder`, `TellingFields`, `TypeIds`, `CatalogueDiff`)

Replaces classify_kinds (DESIGN.md, Event type catalogue, built 2026-11-19). Before the translation is drafted, the
source's **event types** are catalogued: what types of event it writes, what each means, and how a **record** of each
is **recognised**. The agent proposes them from the evidence and what it knows of the product; the tool checks every
proposal mechanically against every sampled record of the build's scope; the user confirms them. The tool holds no
product knowledge: what a product writes is the agent's, kept as data in the source notes.

**Survey first.** A build catalogues only once `survey_feed` has run for it (the survey alone finds an aggregated
feed's source type field): refused until then, naming the `survey_feed` call (its feed, or the build's own sample
streams). A survey refused for nothing readable lets cataloguing go on from the sample streams alone, and the result's
`survey` says so, for the agent to tell the user.

**The records.** The build's scope's records (its source type group: `SampleStreamTexts.scoped`, `scope` in the
result), from `stream_ids`, else the streams the catalogue was last checked against, else the build's newest five raw
sample streams, else (a build on an existing feed) the streams its survey read; with the `splitter` given, else the
build's saved Data Splitter (`read_with`). They are read as the build's latest mapping reads them (the draft last sent,
else the mapping kept with the XSLT: its input, held fields, nil values and extractions), and as processing will read
them: strings cut at the JSONParser's `stringTruncateLength` (10,000 by default), and read whole too, so a condition
that depends on the cut is found. The reading, in order: the `reading` given; else a `reading` an earlier call gave (kept in the notes' confirming block, so confirm calls read the same records); else the build's latest mapping; else the draft's own (2026-11-20: an earlier draft reading whole batches no longer overrides a `for_each` given). draft_translation_mapping reads the records the same way. A source with no mapping yet is read as the draft reads it, or as `reading` says
(`input`, `json_fields`, `xml_fields`, `extract`, `pairs`). Conditions are evaluated by the one evaluator the
generated XSLT agrees with (`LocalCheck`; parity suites `TestConditionParity`, `TestE2eConditionParityProcessed`).

**Evidence** (called without proposals, `status: listed`): `fields` that would tell records apart (`KindFields`' value
checks, with their exclusions: hosts, nodes, severities, shippers, times; never the build's source type field), each
with its values and counts (`more_values` past those shown); record `shapes` (the fields present together, `Shapes`'
signatures, with counts); `examples` per shape, those that differ most, rare records always among them; and the
`seed`: the draft's own rules as proposed event types (`basis: draft`), for the agent to correct rather than start
from nothing. The seed is evidence only: nothing enters the catalogue unless the agent adds it. A draft of one rule
alone seeds a sole event type recognised by every record; within a source type group the seed is never told by the
source type field (it is derived instead from the field naming what happened among the records' other fields). With a
catalogue already, its `check` and `listing` come too.

**Proposals** (`proposals=`, `status: checked`) add, change or remove event types by TypeId (compared ignoring case);
the catalogue is never sent whole. `change` gives only the keys that change. An event type has `type_id`, `title`,
`description`, `event_detail`, `action`, `success`, `actor`, `affected`, `affected_as` (written out to its leaf,
`User` as `User/Id`; read as one placement), `placements` (2026-11-21: each a mapping fields entry whose `path` is
below `event_detail`, or (2026-11-22) from `EventSource/`, e.g. `{path: "EventSource/Client/Name", field:
"event.ClientInfoString"}`, written in the event type's rule, not in common, with `field`, a constant `value`, `any_of`, `join`, an `xpath` or a `lookup` (2026-11-22: an
element the schema requires is often read by an xpath, e.g. a value out of an array of name/value pairs; `data_name`
and `data_parent` stay refused), or (2026-11-23) a `pair` of a name/value list read by its name, `{path:
"Configuration/Name", pair: {list: "event.Parameters", name: "Name"}}` (the list told by shape, `NameValueLists`;
the tool fills the items' `name_key` and `value_key` from the sample, and refuses a list that isn't one, a key that
isn't the items', or a name no pair has, naming the names it has; the pair's values are checked as a field's are;
a sample too small to tell the list by shape reads it as the pair's name says, and an event type with no sampled
record has only the pair's form checked; an old/new list's one side by `value_key` where the action element has no
Before and After, 2026-11-23), or (2026-11-23) a `change` of an old/new property-change list read by its name for an
Update's both snapshots, `{path: "User/Name", change: {list: "event.ModifiedProperties", name: "DisplayName"}}`
writing `Before/User/Name` (old) and `After/User/Name` (new) (the list told by shape, `NameValueLists.changes`; the
tool fills `name_key`, `old_key` and `new_key`, old the first value key in the items' order, keys given kept; refused
on an action element without Before and After, with a one-sided `pair` suggested, for a list that isn't one, keys
that aren't the items' or a name no property has; both sides' values checked at the After element); a group's
fields may read a change of each item's list (targets: `{path: "User", for_each: {array: "targetResources"},
fields: [{path: "Id", field: "id"}, {path: "Name", change: {list: "modifiedProperties", name: "DisplayName"}}]}`),
the group then written in both snapshots, each item's other properties Data of its object (2026-11-23: a group
below one snapshot over such targets is written so without any change field, its copy in the other snapshot where
the event type places none there, each snapshot's own group keeping its fields; a group's field writing an item's
old/new list whole at a typed element is refused with the change form),
the mapping's `transform`, `time_format`, `timezone`, `map`, `default`, `repeat`, or
a `for_each` group with `fields` (refused over a name/value list, 2026-11-23: each setting would be one element, its
value in an element meaning something else, as chat 187 wrote View/Object Name=MailAccessType, Description=Bind;
the pair form is suggested; over an old/new list, the change form); `also_data: true` keeps the field (a pair: as a Data of its name) as Data too; a `change`
giving `placements` replaces the list), `recognised_by` (`[{when: [conditions], names: []}]`, each way an AND of conditions; a second way
when the type is written two ways), `drop`, `basis` (`draft`, `agent_knowledge`, `documentation`), `confidence`
(high, medium, low), `one_type_because` and, optionally, `doubt` (why the agent is unsure of it: said to the user
among those worth a look; 2026-11-20). A proposal with no TypeId gets the default (`TypeIds.defaultTypeId`: the
event detail as the verb, `affected_as`, else the first `equals` value's last segment, else the title's first word;
e.g. `Process-SOAPService`). A proposed or changed event type is `proposed` whatever the proposal says, until the user
confirms it; a change of any event type puts a confirmed catch-all back to the user. `basis: user_confirmed` is
refused (only the user's form gives it), but for `drop: true`, the user's word relayed by the agent, which is still
put to them in the form. Each proposal that can't be applied is in `refused` (its place, TypeId and why: not
Verb-Object, an add of a TypeId there already, a change or remove of one not there); the rest are applied. The event
types are kept more specific first (more conditions before fewer, `CatalogueOrder`), the catch-all last. Then the
whole catalogue is checked; problems the proposals bring (not those it had already) record nothing
(`recorded: false`), and the hint says to correct and send again.

**The check** (`CatalogueCheck`, `check` in the result, compact; the full listing, the catalogue and its whole check,
saved to the workspace as `/outputs/catalogue/<build>.json`, `listing`):

* `counts`: records per event type and per way of recognising it (`by_way`); `not_in_sample` names a type no sampled
  record has (one from documentation, kept and said in the review).
* `overlaps`: a record two event types recognise, refused: the record, its text, both TypeIds and `telling_fields`,
  the fields whose values tell their other records apart (`TellingFields`), so one can be tightened. Two ways of one
  type may overlap.
* `uncatalogued`: records no event type recognises, by shape with examples; not refused (the catch-all translates
  them as Unknown), but counted and shown in the review.
* `unparsed`: records whose held values, read by a condition, don't parse (a bad escape, a value cut short): counted
  with why, not called uncatalogued.
* `several`: an event type whose records split by a field of their own (its conditions' fields aside) or come in
  clearly different shapes; blocking for the seed's (`basis: draft`) until it is split or says `one_type_because`.
* `not_checked`: conditions the check can't evaluate here (an `xpath`, a dictionary, a record-scope test, a field an
  extraction gives), and placements whose values it doesn't read (an `xpath`'s, a `lookup`'s; 2026-11-22): said, not
  refused, never skipped silently.
* `problems`: the catalogue's own (`CatalogueValidation`): a TypeId not Verb-Object
  (`^[A-Z][A-Za-z0-9]*-[A-Z][A-Za-z0-9]*(?:-[A-Z][A-Za-z0-9]*)*$`, at most 100 characters) or given twice; a title
  given twice, ignoring case; a way with no conditions, but for a sole event type of its source type or line format; a
  condition on the build's source type field, in any spelling; capture `names` not unique in the build or not as many
  as the regex's groups; a regex not in XPath's dialect (`XPathRegex.problem`: no lookaround) or over 500 characters;
  what the type means against the event-logging schema (an action element taking Data, its Action enumeration naming
  the values allowed, its outcome); a drop not the user's; and what the records show: a field no record has (with the
  nearest names); the **regex guard**, a regex over its time budget (timed in Saxon's engine, as processing runs it),
  and a regex or `contains` over values past 10,000 characters where `starts_with` or `contains` recognises the same
  records (refused with that suggestion); a condition that depends on processing's **string cut**, naming the setting;
  an event type recognised by every record of a line format that shares it; an `equals` or `one_of` naming a value of a
  field with at least 3 values written alike, the named one among them, differing only in a part that varies (a message holding an object's id; masked
  as the survey's shapes are), refused with `starts_with` or `matches` with a named group suggested; a code (`Login`,
  `4624`) never (2026-11-20); and each placement's (`PlacementCheck`, 2026-11-21): a path the schema hasn't below
  the action element (what it allows named), one under Data or to an element holding others (its leaves named); a
  field no record has (the record fields it may have meant, by last name then by spelling); several values in a
  record where the element takes one, without `repeat` or `for_each`, or a `for_each` on an element the schema has
  once; values that, converted as the XSLT converts them (transform, map, default, time_format), the element's
  enumeration, pattern or XSD type refuses, naming up to three and the fix. An `xpath`'s or a `lookup`'s values
  aren't read here (said in `not_checked`, 2026-11-22); its path, repetition and a lookup's key field are checked.
  A path from `EventSource/` (2026-11-22, chat 183: `event.ClientInfoString`, "Client=OWA", kept as Data while the
  user expected EventSource/Client) is checked the same way from the event's root, and refused where the draft's
  common entries already write that element for every event (leave it to them, or change the common entry: a rule's
  entry would take the common one's place for this type's records only, so refused rather than replaced). A field
  whose values are items with fields of their own (objects, as the records read them) placed at a leaf is refused,
  naming their keys, with a `for_each` group with `fields` and (JSON input) an `xpath` picking one item suggested
  (2026-11-22, chat 183: an array of `{Name, Value}` at `Configuration/Name`, each item written as
  `NameFW to personal`); `build_translation_xslt`'s mapping check warns of a typed entry (not Data, not a group)
  reading such a field. A `for_each` group over such an array whose field reads the item whole (`item: true`, no key
  of it) is refused alike, naming the keys and suggesting `{path: <leaf>, field: <key>}` (the key of the leaf's name,
  else the first; 2026-11-22, chat 184: `{path: 'Configuration', for_each: {array: 'event.Parameters'}, fields:
  [{path: 'Name', item: true}]}` wrote `NameFW to personal` again); the mapping check (`LocalCheck.checkGroupItems`)
  warns of it.
* `unplaced` (2026-11-21): elements the schema requires of an event type's action element (and of each element
  written below it: required children and required choices) that nothing the catalogue gives writes (placements, the
  Action, the outcome, Authenticate's user from the actor or the draft's common user, the draft's common entries
  written below the action element (2026-11-22), Data). Recorded, but
  `confirm=true` refuses to put a set to the user (or the catch-all) until they are placed. Where the element left
  empty is below a member of a schema choice the placements took (2026-11-22, seen in chat 185 on Haiku: a Move's
  folder placed at `Source/Association/Objects/Association/...`, whose required `Objects` was said to be empty one
  level deeper each round for 4 rounds, when `Move/Source` offers `Folder` itself), the message names that choice's
  members, at the shallowest such choice below the action element and once a check, at most 20 (`… and N more`):
  `Source takes one of: Association, Banner, ...; a placement there may go straight to the one the value is (e.g.
  Source/<that element>/Id), not through Association` (no member is given as the example: the first needing nothing
  more, Banner, would suggest a banner for a folder). The schema's model gives the members (`EventSchema.Child.choice`), nothing is listed by hand. An example
  path into an element now goes through a child needing nothing more of its own first (not Association, the first
  of a multi-object choice, whose own `Objects` an example through it left empty again).
* `typing` (2026-11-21): per event type in the sample, the fields typed and kept as Data (those every event shares
  aside), each placed field's fill rate in its records (`fill`), and `worth_a_look` when at least 5 and 80% of its
  fields are Data: said among the set's worth-a-look reasons, never refused (compact: only types with placements or
  a note). With the note, `could_type` (2026-11-22) tells the agent, not the user, what it could type: `data`, the
  fields kept as Data (the vendor's code aside), the event type's own first (filled in its records far more than in
  the source's at large), those every record carries last, at most 12, each with its fill rate and a short
  example value; `schema`, the elements below the action element, those under each object the type writes first
  (e.g. `Email: Type, Id, Name, Subject, ...`), then the action element's other children (`Outcome: Success, ...`),
  the objects of a collection by name, then (2026-11-22) EventSource's elements nothing writes yet for the event type
  (the common entries, its placements there, the draft's rule): each object's open leaves, those partly written first
  (`EventSource/Client: Id, Name, HostName, ...` beside a Client/IPAddress written), objects with the same open leaves
  together (`EventSource/{Device,Server}: ...`), at most 4, then EventSource's own leaves; not System (the draft's);
  `arrays` (2026-11-22, chat 184: TeamMemberAdded's members, each with UPN and DisplayName, stayed Data), the fields
  of `data` whose values are arrays of objects, each with its items' keys (at most 4, 6 keys each), the first saying a
  `for_each` group on an element the schema repeats types each (`event.Members: items with UPN, DisplayName, Role —
  a for_each group on an element the schema repeats types each`); a name/value list (2026-11-23) listed after them,
  apart, with its pairs' names and how many of the type's items hold each (at most 10, `… N more`; those the type's
  placements read left out), the first saying its pairs are Data named by their names, a pair whose name means an
  element placed by name (`event.Parameters: a name/value list; names: Name (30), SubjectContainsWords (30), … —
  its pairs are Data, each named by its pair's name; a pair whose name means an element goes there by name, e.g.
  {path: 'Configuration/Name', pair: {list: '<list>', name: '<name>'}}`); an old/new list (2026-11-23) listed among
  them with its keys and properties' names (`event.ModifiedProperties: an old/new list (old values in OldValue, new
  in NewValue, by the items' order: give old_key and new_key where the keys say otherwise); properties: DisplayName
  (4), … — written as Before and After snapshots, each property Data of the object; a property whose name means an
  element goes there in both by name, e.g. {path: 'User/Name', change: {...}}`, or a one-sided pair where the action
  element has no snapshots), and an array of objects whose items each hold one says so with the group writing each
  item in both snapshots. A list field placed at a leaf is refused with the pair form suggested where it is a
  name/value list, the change form where it is an old/new list (else a group or an xpath, as before). Where two or more event types have a
  `could_type`, the `schema` entries every one of them has (e.g. EventSource's and the Outcome's) are said once, in
  the compact check's `could_type_all` (`schema`, and `said`), and left out of each one's `schema` (dropped where
  nothing else is left); the listing saved whole keeps each event type's as it is (2026-11-22, seen in chats 184 to
  186: some 370 characters of the same homes in each of 7 event types, and the typing cut in the chat's stub 29
  times). The hint says these fields should be typed (2026-11-23, one test everywhere: each one whose meaning an
  element carries belongs in a placement at the element that means it, from what the agent knows of the source; the
  rest stay as Data; never a value in an element that means something else, e.g. a setting's value in a
  Description), with an example call for the first such event type (and the pair and change forms); the note itself says
  "place each whose meaning an element carries at the element that means it"; nothing is refused or paused, and no
  field is matched to an element by its name.
* `order`: the catalogue's.

**Storage.** The catalogue is the source notes' JSON block (`event_types`, `catch_all` with its `status` and
`allow_unknown`, `scope` with the source type field and source types), with the confirmation in progress (the set last
shown and when, a set the user amended, what proposals changed since the set was shown, the streams it was checked
against), and the notes doc's "Event types" part, for people (2026-11-20): a section per action element (`### Authenticate`,
the summaries' groups in their order), each a table `| Event type | What happened | Recognised by | Records | Translated as | Status |`
(TypeId and title; the description, or `-`; conditions in words; the records as last checked, kept in the
confirmation, left blank after a hand edit read back; the event detail, action, who acted and acted on, as the set's
table says it, the column back by the user's decision of 2026-11-20, then `; typed: ` and each placement in words,
`event.Subject -> Email/Subject`, 2026-11-21; `confirmed` or `proposed`, the confidence and
where it came from), the uncatalogued records (the catch-all) last in a section of their own; written together in one place (`RecordSourceNotesTool.textWithCatalogue`) so the table never goes stale. The
field dictionary, documents and references stay as record_source_notes keeps them. The build's one notes doc is
used, else `<source> source notes` is made. Every result carries `catalogue`, compact (each `type_id`, `title`,
`status`, the catch-all's `status`).

**Confirmation** (`confirm: true`, again after each answer). Refused while the check has overlaps, a blocking
`several` or problems, saying which. The sets, in the catalogue's order, at most three (decided by the user 2026-11-20:
never many forms; 300 doubtful types were 30 forms): every high-confidence event type in one, every medium in one,
every low in one, a confidence with none passed over, then the catch-all. Each set is shown as a summary
(`CatalogueSummary`), whatever its size and confidence: grouped by action element, each group's count, records and
commonest three TypeIds, then the event types worth a look in a table with why, up to 10 rows, the rest counted
(decided by the user 2026-11-20). Worth a look: those changed since the user last saw the set, whoever changed
them (`changed since shown: title, recognised by`, decided by the user 2026-11-20, so the user can check their
amendment landed; those removed are named, `Removed since shown:`), no record in the sample, no action
element, an action of Other or Unknown, or an Unknown verb, the only one under its element in a set of 10 or more, no
description of its own, dropped, a `doubt` given, or what the check lists in `worth_a_look` (a TypeId whose verb is
itself an action element of the build's schema, other than the event_detail it is written as: `Move-MailToDeletedItems`
as Update, chat 182; never a refusal, `CatalogueCheck.verbsOtherThanWritten`, 2026-11-22); in a medium or low set every one (each is doubtful by its
confidence), with its `doubt` and `one_type_because` where given; listed changed first, then those a rule or a doubt
picks out, then the rest. The summary links the source notes (its Event types part holds every row), says the user
can ask about any of them in the chat, and the form links the notes too (`every row`).
Each comes first with `status: needs_showing`: Stroom AI's chat
shows it itself, as it is, and the agent is told not to repeat it; another client's agent is told to show it whole.
The next call (within 90 seconds of the summary, or the same call made again with the answer) asks one short plain
form bound to that summary: "Accept all N event types, or type your amendments, or ask about any of them". A summary
shown longer ago is shown again before its form. Accepted, the set is `confirmed` (the seed's basis becoming `user_confirmed`; documentation's and the
agent's kept) and the next set's table comes back (`confirmed_now`). Amended, nothing of the set is confirmed:
`status: needs_confirmation` with the user's `amendments` and the `amended` TypeIds; the agent proposes them
(`op: change`, or add and remove; `drop: true` only where the user said so), and the set is put to the user again; a
confirm before the amendments are proposed is refused, quoting them. Proposals that leave the catalogue as it was (an
amendment the check refuses, the user then choosing the rule as it was; eval case 61, 2026-11-20, where every confirm
replayed the amendment) take the amendments as sent: the set is put to the user again. The confirmation is only ever the
form's (decided
2026-11-18): amendments may come from the form's text or the chat, acceptance never from the chat. **Questions**
(decided by the user 2026-11-20): a message the user types instead of answering the form (the form put aside,
`Consent.SentInstead`), or words in the form's text, are the agent's to read: a question about an item (examples, what
it means, how it is recognised, why it is grouped so) it answers in the chat from `explain=` and puts the set to the
user again (`confirm: true`; after words in the form, `confirm: true answered: true`, which only shows the set again,
never accepts it); only a change asked for is proposed; if it can't tell, it asks the user. Last, the catch-all: the uncatalogued records' count
and shapes shown, then a form to keep them as Unknown events, their fields kept as Data, with the agreement given as
`catch_all: {allow_unknown: <why>}` on any confirm call (used at its turn). Declined, the result says the user wants
something else for those records: the agent asks them in the chat, then proposes event types for them (`op: add`,
`drop: true` where they say to drop them) or another event for all of them (`catch_all: {translation: {type_id,
event_detail, action, description}}`), and the catch-all is put to them again in its form; build_translation_xslt
refuses to save while it is unconfirmed (2026-11-20). Once every event type and the catch-all
are confirmed, `status: confirmed` with the plan's next step: the build plan's `catalogue` step is done, and
`draft_translation_mapping` follows the catalogue.

**Explain** (`explain=`, alone: with proposals or confirm it is refused; `status: explained`): TypeIds (ignoring
case), action elements (a group of the summary: the set put to the user's, else the catalogue's) or `Unknown` (the
uncatalogued records). Each event type (at most 20 a call, the rest named): title, description, recognised by in
words, translated as, records, confidence, `doubt`, `one_type_because`, status, and three of its records from the
sample (`CatalogueCheck.examples`: the text shortened, the values its conditions test as read); a group: its members
(TypeId, title, recognised by, records); the uncatalogued records: what they become and their shapes; `every_row`,
the source notes doc linked as the caller opens documents (the chat's open-doc link, or Stroom's URL outside it; the
doc opens at its top, no section). A large result
is outlined for clients outside Stroom (`LargeResults`). It changes nothing (catalogue, confirmation marks, listing);
a name it doesn't know is refused with the three nearest TypeIds or groups. The hint says to answer in the chat, then
`confirm: true` (with `answered: true` after words in the form).

**Clients without forms**: `status: needs_review` with the set's `table` and a `review_id`; the agent shows the table
and, once the user agrees, calls with `confirm: true reviewed=<review_id>`; amendments go back as proposals. In Stroom
AI's chat with the user away, nothing is confirmed for them (refused).

**A next version** (`since_promoted`): a build of a feed's next version starts from the promoted catalogue (its source
notes carried in by `copy_pipeline` or `create_pipeline version_of=`, `CatalogueNotes.carryInto`; or, catalogued
before either, by catalogue_event_types itself from the promoted notes beside the feed the build works from,
`CatalogueNotes.carryBesideFeeds`, said as `carried`), and every result says in words the event types added, changed
and removed since (`CatalogueDiff`).

**Starting from an existing pipeline** (decided by the user, 2026-11-24; `ExistingTranslations`): when a pipeline
already translates the build's feed (its processor filter reads the raw data) or the build's source type group (the
registry's rows of its scope name it, by UUID, else by name; on an aggregated feed only that, never another group's pipeline reading the same streams), whoever wrote it, and the build holds no XSLT or
pipeline yet and hasn't been asked (`BuildPlan.existingToOffer`), the first call (no proposals, no confirm) asks the
user in a form, after the survey: "An existing pipeline already translates these source types: 'X' translates A and
B (built by Stroom AI; 7 event types, e.g. ...) / (not built by Stroom AI: its XSLT was written by hand, last changed
<date> by <user>; ...). How should this build start?", **Start from the existing pipeline (recommended)** or **Draft a
new translation**. The event types are read from its XSLT (`XsltReader.read`), the rest from its XSLT doc and tags.
The answer is kept in the build record (`start_from`: `choice`, `pipeline`, `at`, `BuildRecords.rememberStartFrom`);
starting from it, the result is `status: start_from_existing` with the plan's `next` (`copy_pipeline`), else the
evidence is listed as usual. Without a form: `status: needs_choice`, `existing` and `routes` naming both, for the
agent to ask in the chat, the answer passed back as `start_from=existing|draft` (taken only where no form can ask,
`Consent.takesChatAnswer`; anything else refused). Seen in chat 192: an existing promoted pipeline translated the
build's source types, the plan routed to drafting from scratch, and the agent spent about ten calls copying the old
catalogue by hand. Once started from it, every set's summary (and the listing's `origin`) begins "N event types from
'X', the existing pipeline this build started from (read back from its XSLT)."

* **Arguments**: `build` (required); `source` (the notes' source, else the build's name); `stream_ids`; `splitter`;
  `start_from` (`existing` or `draft`: the user's answer in the chat, only where no form could ask it);
  `reading` (a mapping's reading keys only: `input`, `root`, `record`, `xml_namespace`, `json_layout`,
  `json_fields`, `xml_fields`, `pairs`, `extract`, `nil_values`, `for_each` (each item a record); any other key, or a
  reading that doesn't parse, refused: how a source with no mapping yet is read);
  `proposals` (`[{op: add | change | remove, type_id, ...}]`); `confirm`; `catch_all` (`{allow_unknown}` with
  confirm; `{translation: {type_id, event_detail, action, description}}`, what the uncatalogued records become in the
  user's words); `reviewed` (with confirm, for a client without forms).
* **Returns**: `status` (`listed`, `checked`, `needs_showing`, `needs_confirmation`, `needs_review`,
  `needs_choice` and `start_from_existing` (with `existing`, the pipeline described, and `routes` or `next`),
  `needs_placements` (2026-11-21: a set confirmed, the next one leaving an element the schema requires empty, with
  `unplaced`), `confirmed`);
  `catalogue`; listing: `evidence` (`fields`, `shapes`, `examples`, `seed`), and with a catalogue `check` and
  `listing`; proposals: `recorded`, `source_notes`, `refused`, `check`, `not_in_sample`, `listing` (no `table`:
  only confirm=true's result carries the set the user is shown, decided by the user 2026-11-23 after chats 187 to
  189, where qwen showed the check's table restyled and the confirmation's was shown after it); confirmation: `table` (and `review_id`), `confirmed_now`, `amendments` and `amended`, `check`; `read_with`,
  `scope`, `survey`, `since_promoted`, `carried`; `hint`; `next` (`step: catalogue`, the call, then).
* **Large, outside Stroom** (`CatalogueOutline`, 2026-11-20; case 68: 61k–93k): `not_in_sample` and `catalogue`
  counted (by status, TypeIds listed as they fit), the check's counts summed and `order` left out (problems, overlaps,
  uncatalogued, several kept whole); the set's `table` (its summary, `CatalogueSummary`) cut last, by lines, its first
  line always kept, saying how many lines and how to read the rest for the user (bounded in rows, at most 10, but not
  in lines or characters: a group line an action element, the TypeIds removed, long conditions and doubts). Confirmation goes by `confirm=true` and `review_id`, never the outlined parts.
* **Refuses**: no build; no survey for the build (naming the `survey_feed` call); no sample streams; a confirm while the
  check refuses the catalogue, or before the user's amendments are proposed; a confirm in Stroom AI's chat with the user
  away; the form declined.
* **Notes**: a Data Splitter part is a line format, not an event type: event types within a format are told by
  conditions, never by a splitter part each (chat 166; the hint and the guide say so). The description stays short;
  the conditions, evidence and checks are in the mapping guide (stroom://guide/mapping, Event type catalogue).

Since 2026-11-24 (chat 192): a proposal's `description` is described as written into every event of the type
(EventDetail/Description): what happened, never counts or notes about the build. The check (`CatalogueCheck`) has a
problem for a description holding a number that is the event type's record count in the sample or the scope's record
count (counts of 10 or more; `1,444` read as 1444): proposals bringing it are refused, as other problems are.

### `record_source_notes`

Content · write (Documentation docs in the build) · checks View and Edit; docs created and changed as the user; a
document given by uuid is read as the user · changes only its build · chat group build · Classes:
`RecordSourceNotesTool`, `SourceNotes` (`stroom.ai.impl.tools.draft`), `WriteGuard`, `DocumentWrites`,
`EventVersions`

Keeps vendor or event reference documentation the user gave in Stroom, and the notes condensed from it, as
Documentation docs in the build: each document verbatim as `<source> reference - <title>` (or, given a `uuid`, the
document already in Stroom is named, not copied), and `<source> source notes` holding a markdown field dictionary,
the "Event types" table and the sources, then the notes as a JSON block (an HTML comment) the tools read back. It
keeps **fields and documents only**: the event types, documented ones among them, are proposed through
`catalogue_event_types` (`basis: documentation`), which checks them against the records and keeps the catalogue in the
same notes doc (its "Event types" table and the JSON block's `event_types`, `catch_all` and `scope`, written together).
A call giving `events` is refused, nothing recorded, pointing at `catalogue_event_types` with the proposal's shape.
The notes feed `draft_translation_mapping build=` (fields where the dictionary puts them) and `write_documentation`
(source fields with their meanings). Promotion puts them beside the feed. Called again for the same source (a long
manual a part at a time), the docs are updated, not made twice: earlier fields stay unless given again, and the
catalogue is kept as it is.

For documentation the user gave, and for the agent's own knowledge of a source (each such note `basis:
agent_knowledge` with a `confidence`, high, medium or low, required): the notes doc shows each note's basis, the
result's `to_review` lists the low-confidence ones for the user first, and field documentation says a meaning is the
agent's knowledge. Never notes guessed from the sample's values: the draft would follow them over the sample's own.
A field's `event_logging_path` the build's schema doesn't have is left out of its note (`path_problems` says what is
allowed where each stops).

* **Arguments**: `build`, `source`, `summary` (required); `fields` (each `field` and `meaning` required, then `type`,
  `example`, `event_logging_path`, `values`, `basis`, `confidence`); `references`; `documents` (each `title` and its
  `text`, or a `uuid`; `source` optional). Lists may be one object, or JSON text.
* **Returns**: `type`, `uuid`, `name` of the notes doc, `fields` count, `documents_kept`, `to_review`,
  `path_problems`, a `hint` (event types go through `catalogue_event_types`), and `next`.
* **Refuses**: `events` given (`Nothing recorded: record_source_notes keeps fields and documents only. Propose the
  event types ... through catalogue_event_types ...`); a missing required argument; a note missing a required key
  (`fields[0].meaning is required`), not an object, or a list given as text that isn't JSON; a `basis` other than
  `documentation` or `agent_knowledge`, or the agent's note without a confidence; a document without `title`, or
  without text or uuid; a uuid not in this Stroom.
* **Notes**: an existing notes or reference doc is changed only when it is the tools' own and in the build
  (`WriteGuard.checkInBuild`). Names are cut to 200 characters.

## Processing a build's pipelines

### `create_processor_filter`

Processing · write · requires Manage Processors; checks View and Edit; only for a pipeline in a build; emptying a
build's own Lucene index needs Manage Index Shards; unlocked by View Data (reading why Elasticsearch refused earlier
runs; without it the index is taken to hold what they sent) · confirms itself · chat group processing · Classes:
`CreateProcessorFilterTool`, `ProcessorFilterWrites`, `ProcessingSources`, `ProcessingApprovals`, `IndexingRuns`,
`IndexTemplateAgreements`, `OutputFeeds`, `PipelineOutputs`, `UserActionEntries`, `BuildTestIndexes`

A build pipeline whose indexing filter names a Lucene index outside the build (a working copy or new version of a
production indexing pipeline) is pointed at a test index in the build first, `<INDEX>-MCP-TEST`, mirroring the
production index's settings and fields plus the pipeline's kept plan's, so a build never writes into a production
index (seen in an eval: a working copy's test run put 192 documents into the live index). `verify_index` on the
production index searches the test index. A build pipeline writing to a production Elasticsearch index is refused
(`ProductionIndexes`, through `IndexingRuns.refuseUnsafeIndex`), before any lookup, `index_exists`, `drop_first`,
template or approval form: production is decided by the index name, not by where an Elastic Index doc lives, a name
that a non-template pipeline outside the build writes to (its own `indexName`, or the one it inherits from a
template), that an Elastic Index doc outside the build names (one naming a pattern names none), or, for a working
copy or new version, that its original writes (inherited from a template too). Names are compared with the variables
filled in that Stroom's ElasticIndexingFilter would fill (`IndexNameValues`: it fills `{name}` from the document's
root field `name`, as written): a field every XSLT of the pipeline writes as a fixed text, or as
`stroom:feed-name()` (perhaps lower- or upper-cased), once for each feed it processes (its processor filters'
feeds; for a build pipeline, its build's feeds too). Its filters' feeds are taken only where every filter's can be
listed in full (`PipelineFeeds.enumeratedFeeds`: Feed `EQUALS`, `IN`, `IS_DOC_REF` or `IN_DICTIONARY`, a sample
stream's `Id`, or an upstream pipeline whose own are listed in full, in an AND or every branch of an OR); a filter of
a stream type alone, a feed under NOT or `NOT_EQUALS`, a feed wildcard, an unreadable Dictionary, filter list or
upstream pipeline makes the feed variable a wildcard (found in review, 2026-11-18: a filter of Type only was passed
over, and the claim narrowed to the other filter's feed). A `NOT_EQUALS` Feed is never a feed read. Any other variable
is a wildcard. So children of a
`{feed}-events` template claim only their own feeds' indices (`writes to '{feed}-events' ('acme-a-events')`). The refusal says nothing was processed and nothing is to
be dropped (`Pipeline '...' writes to Elasticsearch index '...', which is a production index (...) ... Nothing was
processed, and nothing is to be dropped or deleted ...`): step a working copy clean instead, or make a new version
with its own index (`copy_pipeline`). Seen in a user's test: the build's own Elastic Index doc for the production name
made the index count as the build's, and the user was asked to drop the production index, and did. `freshIndex` and
the existing-index lookup refuse it again, so `drop_first` and `delete_first` never name one. A new version's first
run (`version_of`, no filter yet) looks its own index up again: anything it matches, or a failed lookup, refuses it.
`copy_pipeline` of such a pipeline points its copy at the test index at once and says so (`test_index`,
`test_index_note`: make no test index of your own), so the agent never makes one of its own (seen in an eval, case
61: the copy said nothing of a test index, made only once it processed, and the agent made `-TEST` indexes that
promotion took to production). Processing then gives that test index any field the plan gained since
(`test_index`, `made: false`).
At promotion the test index isn't promoted (the tidy-up deletes its shards, then it, unless the user unticks it),
the pipeline is written back pointed at production, and the production index gains the plan's missing fields (said in the
review's `index_changes`), for any build.

Creates a processor filter on a pipeline in a build (`ProcessorFilterWrites.managedPipeline`), through Stroom's
`ProcessorFilterService` as the user, as the data browser's Process action makes one. Feeds and pipelines are named
by reference (`IS_DOC_REF`), so Stroom keeps the dependency and a renamed feed is still the one selected. A build on
an existing feed whose survey isn't covered yet is refused (`BuildPlan.uncovered`: the plan's `covered` step to do),
saying to survey on, the user asked whether to read deeper or choosing to stop (asked for by the user: seen in chat
163, a feed processed and called complete while its survey said NOT COVERED). Two scopes:

* **Sample** (`stream_ids`): exactly those streams, up to `stroom.ai.builds.maxProcessingTasks` tasks at once (4 by
  default, `ProcessorFilterWrites.buildMaxTasks`). Streams the pipeline already processed (it
  has output for them, or a filter already selects them) are refused: use `reprocess_streams`.
* **Feed** (`feed`, `stream_type`, `created_after`): the feed's streams of the type created after the bound, at most
  2 tasks at a time (`ProcessorFilterWrites.MAX_FEED_FILTER_TASKS`, which the description states). `stream_type`
  defaults to Events for an indexing pipeline, else the feed's own type. A translation pipeline's feed
  filter that selects none of the feed's streams still to process is refused (seen: an agent guessed midnight after
  its sample arrived and the filter selected nothing), as is the same
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
text), so it isn't dropped. The sample's first run into an Elasticsearch index name that already holds documents (a
data stream, index or alias made by something else; `ExistingIndexes` over `ElasticIndexPresence`) gets an
`index_exists` reply before anything is asked or processed, listing every data stream and index the cluster admin deletes by hand first (`delete_first`; variables in the name taken as any value), and `rename_index` as the other way;
an existing empty index, or one that can't be looked up, is indexed into with a line in the approval.
`index_dropped=true` is checked again: an index still holding documents gets `index_exists`.

* **Arguments**: `pipeline_uuid` (required), `stream_ids` or `feed` (exactly one), `stream_type`, `created_after`
  (ISO; without an offset taken as UTC), `priority` (1 to 100, default 10), `source_pipeline_uuid`, `index_dropped`,
  `template_committed_by_user`.
* **Returns**: `filter_id`, `pipeline`, `scope`, `events_from_pipeline`, `output_feed` with `output_note`,
  `enabled`, a hint (`wait_for_processing with filter_id=`), and `next`; a disabled filter adds
  `pipeline_link`, what enabling does, `before_enabling` and `user_actions`; a `drop_first` reply in place of the
  approval when the index must start fresh; an `index_exists` reply (`problem`, `exists_as`, `names`, `documents`,
  `user_actions`, hint) when an Elasticsearch index already holds documents.
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

Processes up to 10 streams again through a pipeline in a build after a change, up to
`stroom.ai.builds.maxProcessingTasks` tasks at once, with a new filter
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
stream reprocessed): a `drop_first` reply comes before the approval, then `index_dropped=true` (an Elasticsearch
index still holding documents then gets `index_exists`); a build's own Lucene index is emptied once approved. A
production Elasticsearch index is refused first, as in `create_processor_filter`, so `drop_first` never names one.

* **Arguments**: `pipeline_uuid`, `stream_ids` (required; 1 to 10), `source_pipeline_uuid`, `index_dropped`,
  `template_committed_by_user`.
* **Returns**: `filter_id`, `pipeline`, `streams`, `max_tasks`, `enabled`, a hint, `next`; for a disabled
  filter what the user does first, with buttons; or a `drop_first` or `index_exists` reply. When the build has
  another pipeline whose index was verified, the hint says that index holds the Events this replaces, and to offer
  the user, once processing finishes, to index the new Events (the plan's next step).
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
  `next`.
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
  until the timeout) and stops at once when its thread is interrupted or the chat's run stopped.

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
  each event-logging path (and named Data entry) is populated, and the Data names. A stream holding more is read at
  events spread over it, not its start (seen in testing: "no Unknown events" taken from the first 2,000 of 6,000,
  mostly of one kind).

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
* **A source type group's draft** is stepped as draft code for its own pipeline's XSLT element, as any
  translation's: a group is its pipeline's one XSLT, so nothing is inlined or routed. Its imports (the feed's common
  module, which imports the helpers) resolve to the saved documents: the versions the group's XSLT imports.
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
  accepted for the pipeline (`AcceptedErrors`) counted as benign. The JSON parser's cut of a string longer than its
  `stringTruncateLength` is for review, with the property to raise (it was a benign Warning from an inherited
  element, so stepping was clean and the value lost; found testing awkward values, 2026-11-18), unless the user
  chose to cut long values for the pipeline's build (`draft_translation_mapping long_values=cut`, 2026-11-22): then
  it is benign, accepted, its reason naming the choice and when it was recorded. With no choice recorded, the
  review's reason says to ask the user and record it with `draft_translation_mapping build= long_values=`. Where the
  user chose to keep them whole and the parser still cut one (a value longer than the limit set, or the limit never
  set on the pipeline), it stays for review, but with the build's reason in place of the rule's
  (`BuildProgress.explainedErrors`, through `AcceptedErrors.explainedFor`; `LongValues.stillCut`, 2026-11-22): the
  build keeps long values whole, the limit recorded for it (if any) and the longest value seen, to raise the
  pipeline's limit past this value with `update_pipeline set_properties`, and that `draft_translation_mapping`
  raises the recorded limit when its sample holds a longer value. Output with no XML elements is a marker of its own
  (`SteppingVerdicts.emptyOutput`): when no template matches (usually a wrong `xpath-default-namespace`), XSLT's
  built-in rules copy text through, nothing errors, and processing silently writes nothing. CEF lines and Kafka
  records are not empty output (`SteppingVerdicts.sendsOn`), nor is text that isn't only the input's own text (CSV
  or JSON lines from an XSLT with `xsl:output method="text"`; seen: such an export given a blocking verdict). Blank
  output, or the input's text copied through, escaped or not, still is.
* **Unknown nobody agreed to.** On a build's own new pipeline (tagged managed, not a copy) whose XSLT was saved
  without a mapping, records written as `EventDetail/Unknown` are a blocking group
  (`PipelineStepping.unagreedUnknown`), with what the Unknown events hold (their TypeIds and Data values). A
  hand-written XSLT once wrote every TRAFFIC record of a firewall sample as an empty Unknown, past both
  `build_translation_xslt`'s check and the user. Not applied to draft code. A failure of the check is logged and
  never fails the step.
* **Output no schema filter checks.** Where no SchemaFilter comes after the pipeline's output element, each
  record's output in records:2, reference-data:2 or kafka-records:1 is validated against the schema Stroom holds for
  its namespace (`OutputSchemas`, by `UnvalidatedOutput`): the one its `xsi:schemaLocation` names if held for that
  namespace, else the newest. A record that fails is an ERROR of the output element, blocking, with the schema's
  first error. The IndexingFilter, ReferenceDataFilter and StandardKafkaProducer read only what fits and drop the
  rest without an error, and Stroom's own Indexing template has no schema filter, so such output stepped clean and
  was lost in processing. Event-logging Events are `check_events`'; a pipeline with a schema filter after its
  output is left to it (its errors already block). Output that isn't well-formed, or whose schema Stroom doesn't
  hold, isn't checked here.
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
`SteppingVerdicts`, `PassedOverRecords`, `SteppedEventTypes`, `BuildRecords`

The correctness check before processing. Steps every record of the sample streams, stream by stream, each stream
walked in one session, and returns one verdict for the whole sample: error groups triaged with the records they
affect, and per-record counts. A blocking group means fix and step again. A clean run of saved code is recorded for
promotion and returns the plan's `next`.

* **Arguments**: `pipeline_uuid`, `stream_ids` (required, at least one); `draft_code` (unsaved code for an element,
  a source type group's draft among them, above); `max_records` (at least 1; at most 500, the default); `records_per_stream`
  (at least 1). With neither `max_records` nor `records_per_stream`, the first 50 records of each stream are stepped
  (`StepSampleTool.PER_STREAM`): sample files of 985 records each were stepped a record per request on a remote
  Stroom, and the head of each stream already says whether the translation is right; processing reads the rest.
  With the build's event type catalogue, those defaults step on until every catalogued event type is stepped
  (below).
* **Returns**: `pipeline`, `records_stepped`, `records_with_errors`, `draft_code_used`, the triage (`verdict`,
  `groups_by_class`, `groups`, each group with up to 20 `records`, keys such as `7:3` or `7:1:3` past the first
  part), `first_record_output` (each default output element's output for the first record, up to 5,000
  characters), and as they apply `hint`, `stopped_early`, `record` and `next`. With a catalogue, for an events
  pipeline: `stepped_by_event_type` (records stepped of each event type stepped, by TypeId, in the catalogue's
  order) and `not_stepped` (`type_id`, `title`, `why`: `not in the sample: ...` when every stream was stepped to its
  end, else `not found within the limit: ...` with the limit met).
* **Refuses**: no `pipeline_uuid` or empty `stream_ids`; `max_records` or `records_per_stream` below 1 or not a
  whole number; a pipeline that looks up reference data from a feed with no stream of
  that type yet (`PipelineStepping.refuseUnloadedReferences`): a step then finds no reference data, and Stroom keeps
  that empty answer for 10 minutes, so steps made after the reference data is processed still fail.
* **Notes**:
  * Blocking groups beyond the triage: Unknown nobody agreed to (above); every record wrote Events but none an Event
    (`SteppingVerdicts.noEvents`: XML fragments read with no namespace came out as empty Events and stepping said
    clean); every record wrote referenceData but no reference (`noReference`: a header line read as data named the
    columns `col1`..., and processing then wrote no Reference stream with nothing saying why). No record stepped at
    all is never clean (`nothingStepped`), and an element that fails before the first record (an XSLT that doesn't
    compile) is reported.
  * A source type group's pipeline passes over the feed's other source types' records, logging nothing of them (a
    line a document made Stroom write an Error stream for every raw stream each group processed).
    `PassedOverRecords` tells them as the group's XSLT does, from the registry kept with the group's mapping: the
    source type field's value of the XSLT's input (a part's meta, read by the record's stream and part, for a
    `meta:` field), the rows tried in order, the first match winning (value, pattern and list-member rows); a
    record the pipeline wrote an Event for is never one. Such a record is not one `with_errors` (only its ERROR and
    FATAL markers kept) and never in the triage or the verdict; the result gives `records_passed_over`,
    `passed_over_by_source_type` and `passed_over_note`. The INFO line older group XSLTs log
    (`XsltGenerator.PASSED_OVER`) is taken out of a record's markers, and marks one passed over where no group
    mapping is kept (`(not told from the step)`). The no-Event check reads only the records not
    passed over; every record passed over is blocking (`SteppingVerdicts.allPassedOver`: nothing the group
    translates was checked). Seen in chat 177: 304 of 500 records "with errors", 238 only passed over, explained
    away by the agent. `step_records` marks such a record `passed_over: true` (no "no Event" warning) with its
    `passed_over_source_type`, leaves its shape out of `shapes_not_clean` and names it in `shapes_passed_over`;
    `evaluate_pipeline` carries the counts. A record is told even where its values hold control characters or NUL
    (read as XML 1.1, as Stroom writes it, NUL left out; seen in chat 189: Teams and Exchange bodies holding NUL,
    BEL and ESC weren't read, so passed-over records were said to produce no Event). Its WARNING and INFO markers
    are listed apart (2026-11-23), never in the verdict: a benign group each by source type, element and message
    (`passed_over_source_type`, reason "In records of X, which this group passes over: not this build's to
    decide", the pipeline's parser settings its own), so a parser setting that matters only for another group's
    records (the JSON parser cutting Exchange mail bodies, chat 189) is never asked of the user.
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
  * Every catalogued event type stepped (`SteppedEventTypes`). When the pipeline's build has an event type
    catalogue (its source notes), a record is of an event type when its output has an event whose
    `EventDetail/TypeId` is the type's (its TypeId, else the default the translation writes); a dropped event type
    writes no event and isn't looked for, nor is the catch-all. With the default limits, each stream's head is its
    first 50 records of the group's own (records passed over not counted), at most an even share of the 500;
    then, while an event type is not stepped, the streams not yet ended are stepped on from where they were left,
    in turn, each an even share of what is left of the 500, stopping as soon as every event type has been. The
    bound is the existing one: 500 records a call (`max_records` lower), and the call's time for stepping, which
    past the heads only stops the search (no `stopped_early`; the verdict of the records stepped is recorded as
    usual). `records_per_stream` or `max_records` given are kept as given, the event types still told. The hint
    says every event type was stepped, or which weren't and that the verdict doesn't cover them. Record numbers
    from the catalogue's check aren't used to step straight to a type's records: they number the sample's records
    as the survey reads them, not stepping's parts and records, and stepping only steps on from a record. Seen in
    chat 182: the first 50 records of each of two streams, 57 of the 100 passed over, and nothing said whether
    the build's 7 event types were among the 43 left.

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
  JSON text, is read too. Or `locations_from`: the path of a survey_feed result saved to the workspace
  (`whole_result.saved`), every location it lists stepped (2026-11-20); for a source type group's pipeline, only
  those of the group's source types (by each location's `source_types`; 2026-11-23, chat 189: 292 locations of other
  source types stepped, four said to produce no Event, ten calls spent finding they were passed over), the rest
  counted in `locations_left_out` (by source type) with `locations_left_out_note`; refused where none is the group's.
  A location given of another source type is stepped and said passed over as its source type's. `draft_code`. Only
  the first 500 locations are stepped.
* **Returns**: `pipeline`, `records_stepped`, `records_with_errors`, `records_not_found`, `shapes_not_clean`,
  `left_untranslated_as_intended`, `draft_code_used`, the triage, `records` (the first 100, each `record`, `shape`,
  `events`, `errors`, and `found: false` with `why` for one not found), and as they apply `hint`, `stopped_early`
  and `record`.
* **Refuses**: no `pipeline_uuid`; empty `locations`; a location that isn't one, or without whole-number `stream`
  and `record` (`locations[0] needs stream and record (and part, if any) as whole numbers: ...`); an `expect` other
  than `event` or `none`.
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
   (`DocFinder`), as Stroom imports by name, else the one whose UUID the href holds (`uuid='…', name='…'`, as an
   aggregated feed's modules import each other; `XsltImports`).
9a. An aggregated feed's modules (`ModuleChecks`): a source type group (marked `<mcp:module kind="group">`) is its
   own pipeline's whole XSLT, so it holds its stylesheet-level declarations; every name it declares (templates,
   modes, functions, global variables) is prefixed with its mode, so none meets a name of the modules it imports.
   Any XSLT's imports, followed through the documents they name, form a chain: a module reached twice (a diamond: a
   group importing the helpers or a site library its common module imports too) is refused, naming both paths, as
   are cycles and imports nesting more than 8 deep.
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
`Generator`, `Device` and `EventDetail/TypeId` present; exactly one action under `EventDetail`; no empty elements;
no identifier (`Id`, `HostName`, `IPAddress`, `MACAddress`, `Domain`, `EmailAddress`, `Port`) with a quote, full
stop, comma or semicolon at an end (`stray_punctuation`: seen in the chat 163 review, every New-session user read as
`frank.`); no value, an element's or a Data `Value`, that runs on into the next field (`over_captured`: a closing
quote then ` - Key`, or ` - Key` then a quoted value; seen, a logout method read as `"IdleTimeout" - Group(s)
"Staff"`)).
Given `stream_ids`, it reads the Events streams itself (`EventStreams`) rather than have the agent read them back:
an agent once read 50 events back 23 at a time, 47 s a read, to check them. A stream holding more events than are
checked (500 in all) is read at events spread over it, not its start (`spread` in `read`): seen in testing, the first
500 of 6,000 events were almost all of one kind.
Events are read as the XML version they declare: Stroom writes Events streams as XML 1.1, where a control character
in a source's value (a form feed, bell or escape) is written as a character reference (`&#xc;`) XML 1.0 forbids.
The merged events of `stream_ids` keep the 1.1 declaration when any record has it, and an `events_xml` with a 1.1
declaration is read as 1.1. Seen in chat 181: a Teams message's form feed, passed by Stroom's schema filter, was
refused as "not well-formed (line 1)" of the merged one-line document, and the agent spent about 30 calls looking. The
declared schema is read from `xsi:schemaLocation` up to its closing quote (found testing awkward values, 2026-11-18:
Events with no white space whose first Description held compact JSON were read as declaring a system id running into
the JSON, and refused: "Stroom has no XML schema").

* **Arguments**: `events_xml` (an `<Events>` document, e.g. step output) or `stream_ids` (Events streams processing
  wrote, as `wait_for_processing` lists them); exactly one of the two. `schema_version` (default the version the
  document declares in `xsi:schemaLocation` or its `Version`, else the newest held).
* **Returns**: `ok`, `schema` (`valid`, `schema` system id, `error_count`, `errors`: up to 50, each `line`, `path`
  such as `/Events/Event[2]/EventTime/TimeCreated`, `message`), `quality` (`events_checked`, `ok`, `rules`: each
  failed rule with `events_failing` and up to three `examples`; `notes` when events are EventDetail/Unknown), `note`
  when the XML came HTML-escaped. With `stream_ids` also `read` (per stream, records checked and held),
  `recorded_for` (the pipelines recorded) and the plan's `next`.
* **Refuses**: both or neither of `events_xml` and `stream_ids`; XML that isn't well-formed in the version it
  declares (an XML 1.0 document holding `&#xc;` is), saying where (`WellFormedFaults`): line and column, the Event
  it is in (for `stream_ids`, the stream and event number), the text near it with control characters visible, and
  for a control character reference that XML 1.1 allows it, the value is the source's, and transform
  `strip_control` is needed only if the output must be XML 1.0; a schema version this
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
* **References** (`references`) are `{feed, loader_pipeline, element, stream_type}`
  (`PipelineWrites.ReferenceRequest`): `feed` required, the loader by name or UUID (default the one other pipelines
  load that feed with, else the environment's only loader: `ReferenceData.resolveLoader`), the element default the
  pipeline's translation XSLT step. A feed's name alone is read as `{feed: <name>}`. A reference without a feed is
  refused (`references[0].feed is required: ...`); a pipeline with no XSLT step to attach it to too. With
  `stream_type: 'Context'` the reference reads each events stream's own Context child (Stroom core,
  `ReferenceData.getValueFromNestedContextStream`: each part its own, nothing processed first): `loader_pipeline` is
  required (the context loader, a child of `context_loader_template`), `feed` the feed whose streams carry the context: left out, the one feed the build's pipelines read (`BuildPlan.inputFeeds`; a group's, its feed), and refused when there is none or several. Stroom names it in its lookup logs and expects one there, so it is always set (core Stroom left unchanged).
  Stepping doesn't wait for a Context reference (`PipelineStepping.refuseUnloadedReferences`), nor does the plan
  (`StroomBuildFacts.referencedFeeds`), and `keep_references` carries it (`CurrentTranslations.ownReferences`).
  A context loader is made from `context_loader_template` with `combinedParser.type` `XML` (or `JSON`) in
  `set_properties`: a parser told so needs no text converter (`PipelineWrites.readsWithoutConverter`), where one
  left to the build's documents is refused without one. A loader-stage pipeline goes in the build's `Reference`
  subfolder (`PipelineShapes.STAGE_ROLES`). A feed is found by `FeedNames.exact`; a feed name that is
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
`PipelineTemplates`, `WriteGuard`, `Consent`, `BuildPlan`, `BuildRecords`, `SourceTypeGroups`

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
   validation properties refused; values typed. A parser element the pipeline lacks, when it has one, is refused with
   `PipelineWrites.ONE_PARSER` (a pipeline has one parser, reading the raw stream; XML or JSON in a text field is read
   in the translation through xml_fields or json_fields), as is a `replace_parser` other than DSParser for a text
   sample.
3. **The parser must read the sample** (`PipelineWrites.parserReadsSample`, checked first, as a template whose parser
   can't read the sample is the wrong one whatever else it lacks: an XML source was once refused the Data Splitter
   template for want of a text converter, not for its parser). The build's sample format (`BuildPlan.sampleFormat`,
   which for a build on an existing feed reads the survey's format) must be one the parser reads
   (`BuildPlan.PARSER_FOR_FORMAT`). XML fragments under an `XMLParser` are refused saying outright to call again
   with `replace_parser='XMLFragmentParser'`, as the general advice was passed over in testing. Overridden by
   `accept_parser_mismatch=true` when the user insists.
4. **Open slots filled from the build** (`PipelineWrites.openSlots`, `fillOpenSlots`): the parser's text converter
   (DSParser, XMLFragmentParser, CombinedParser) and the first XSLT step's `xslt`, where the template leaves them
   unset. Each is filled with the build's only document of that type (XSLTs with a kept mapping); several candidates
   are refused, naming each (`pass the right one in set_properties`); for an aggregated feed, `group=` names the
   group whose XSLT fills it, never guessed. A converter slot with none in the build
   is refused (`build_data_splitter ... or save_text_converter`); an XSLT slot with none is left open, reported in
   `still_to_set` with a hint to save the XSLT and `update_pipeline`.
5. **The build's own documents** (`PipelineWrites.ownDocuments`): an XSLT or text converter set on the child must be
   one this build made (tagged managed, in this build). Another source's or a library's is refused: a new
   pipeline's translation is written for its own source, and shared libraries are imported or inherited. Overridden
   by `reuse_existing_docs=true` when the user says so.
6. References attached; the confirmation form; then `WriteGuard.createFilled` makes and fills the pipeline in one
   go, deleting it again if filling fails, so no empty pipeline is left in the build. The build's XSLT and text
   converter the pipeline runs are moved into its role subfolder (Events or Records) with it.

**A source type group's pipeline** (`group=`; `CreatePipelineTool.groupGiven`, `SourceTypeGroups.pipelineMade`): every
group of an aggregated feed has a pipeline of its own, `<feed> <group>` by default, its one XSLT the group's (added as
`translationFilter.xslt` where `set_properties` sets none; another XSLT set is refused). One pipeline a group: a group
whose XSLT another pipeline of the build runs already is refused (`a group has one pipeline`). An XSLT property set to
the feed's common or helpers module (by its `mcp:module` marker) is refused: the group imports them. Where the group's
Events go to an output feed of its own, every StreamAppender's `feed` is set to it (said in `filled_from_build`); one
not made yet is refused (`create_feed ... stream_type=Events group=<the group> first`). Without `references`, the
reference data the group looks up (kept in the registry by `build_translation_xslt references=`) is loaded. Once made,
the registry's rows of the group name the pipeline (`pipeline`: `name`, `uuid`); a working copy of a promoted pipeline
leaves them naming the original. A group's new pipeline (the registry names none made yet, and no `version_of`) is
proposed with the group's pipeline name, the one `build_translation_xslt` named (the registry's, else
`SourceTypeGroups.pipelineName`), whatever `name` the agent passes and whether the group is given or found by its XSLT
(`GroupRun.pipelineToMake`; 2026-11-22, chat 184: the form announced `Azure-Audit-V1.0-EVENTS Messaging V1.0`, the
agent passed `Azure-Audit-V1.0-Events`, and that was made). The form says so (`pipeline name`: the group's name, in
place of the agent's) and the user can still change it; kept, `name_note` in the result says the pipeline was named for
the group, not as passed (a standalone Events pipeline for a group alike, in its `note`). `create_indexing_pipeline`
names no group pipeline (its `name` is required and an indexing XSLT is no group's), so it is unchanged.

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
inherits from no template is refused (copy it instead). The promoted source notes (beside the pipeline, in its folder
or one above) are carried into the build as a working
copy, written back at promotion, unless the build has notes of its own (`CatalogueNotes.carryInto`), so the next
version's catalogue of event types starts from the promoted one (`source_notes` says so; catalogue_event_types'
`since_promoted` then says what changed).

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
  `translationFilter.xslt` (and the parser's `textConverter` where it has one). Where the user chose to keep long
  values whole, its JSON parser's `stringTruncateLength` is set to the build's limit, as one from a template is
  (`LongValues.parserLimit`; unless `set_properties` sets it; 2026-11-22).
* `standalone` with `template` or `version_of` is refused.

* **Arguments**: `name` (required, but for a group's pipeline: the group's pipeline name, proposed whatever is given); `group` (an aggregated
  feed's source type group whose pipeline it is); `template_uuid` or `template` (UUID or exact name); `build`
  (default the session's current build); `set_properties`; `description` (kept on the pipeline doc); `replace_parser`;
  `references`; `reuse_existing_docs`; `accept_parser_mismatch`; `standalone` (`kafka` | `events`); `version_of`;
  `keep_references` (only with `version_of`).
* **Returns**: `type`, `uuid`, `name`, `template`, `group` and `source_types` (a source type group's pipeline: the
  group, and the registry rows now naming it; `name_note` where it was named for the group, not as passed),
  `version_of`, `set_in_<previous>_not_here`, `sets`,
  `filled_from_build`, `still_to_set` with `hint`, `reference_data`, and `next`; a standalone pipeline adds
  `note` (what to tell the user).
* **Refuses**: no `name`; no template; the refusals in the steps above; `keep_references` without `version_of`;
  the user declining the form, or a session that can't show one (`Consent`).
* **Notes**: Stroom can't load a template: `Stroom can't load the pipeline: ...` (`PipelineUnreadableException`).

Since 2026-11-24 (chat 192): with `version_of`, each build document the new version runs in place of the previous
one's (its XSLT, say) whose version token is the previous pipeline's (the build's source feeds' and the group's names
held) is renamed for the new version (`VersionNames.retokened`, `WriteGuard.rename`): `Azure-Audit-V1.0-EVENTS
Messaging V1.0` becomes `... V1.1`, the feed's V1.0 left; `renamed` says so and the registry's rows name the XSLT so.
The result's `production_imports_broken` names the previous version's XSLT imports that no longer exist.

### `copy_pipeline`

Content · write · checks View on the pipeline and the XSLTs and text converters it owns, Create in the build
folder, Edit on the copies · confirms itself · Classes: `CopyPipelineTool`, `PipelineWrites`, `PipelineTemplates`,
`WriteGuard`, `BuildRecords`, `Consent`, `SourceTypeGroups`

Copies an existing source's pipeline into the build with exactly the original's structure and settings: the same
parent template, the same element changes (removed or re-linked steps, extra XSLT steps), reference loaders and
property values, and its description. The XSLTs and text converters its own layer names
(`PipelineWrites.OWNED_TYPES`) are copied too, with `rename` applied to their names, and the copy is rewired to
them; other documents (shared libraries, indexes, clusters) stay shared. The user confirms the names in a form
first. It is the route for a new version that keeps the XSLT and changes parts. A copy of a promoted pipeline carries
its source notes into the build the same way (`CatalogueNotes.carryInto`, said in `note`), so the new version starts
from the promoted catalogue.

A copy is always a new version (decided by the user, 2026-11-17: a promoted pipeline and the code it runs are never
changed in place): nothing is asked but its names, recorded `version_of` its source, and `working_copy=true` is
refused (`... only a new version of it is copied, never a working copy ... Nothing was copied.`). Without forms the
reply is `needs_names`: the proposed names (`new_version_names`) and a hint to put them to the user. Seen in a user's
test: a promoted feed's pipelines copied under the same names as working copies, where the user expected a new
version.

A source type group's pipeline (`SourceTypeGroups.chainCopies`, `newVersion`): its one XSLT is the group's. An owned
document the build has made a new version of already (`version_of`: `build_translation_xslt` saves a promoted group's
change in a new version of its XSLT, without asking) is run by the copy, not copied again; one made under the
original's name (before such versions were named for them) is refused (`refuseCopiesUnderOriginalNames`: `... have
the original's ... Rename each in Stroom ... Nothing was copied.`), as a new version under the original's names is.
The common and helpers modules the group imports aren't the pipeline's own, so stay shared (the group's new XSLT
imports the build's new versions of them where `build_translation_xslt` made them); every module in the build
importing a promoted module the build has a working copy of (an older build's) is pointed at that copy, so the chain
runs the copies. The group's registry
rows move to the new version (`pipeline`: its `name` and `uuid`; `source_types` in the result); the current pipeline
runs on unchanged until the user retires it.

A new version's names are proposed (`VersionNames`) and confirmed or edited in the copy's form, each an editable
field: the agent's where they differ from the original's, else the next version's. The pipeline's last version token
goes up by its last part (`V1.0` to `V1.1`, `V1` to `V2`, `-v1.2` to `-v1.3`), the major bump (`V2.0`) said in the
form as the other choice; a name with none is taken as `V1.0` (as promotion files it) and gets `-V1.1` (`-V2.0` the
major choice), before its first `{variable}`. A token inside the name of a feed the build works from is the feed's,
never the pipeline's version (found in review, 2026-11-18: `CISCO-ASA-V9-EVENTS` of feed `CISCO-ASA-V9` was V9, its
next V10): with no other the name is unversioned, `CISCO-ASA-V9-EVENTS-V1.1`; promotion's layout, a group pipeline's
default name (`<feed> <group> V1.0` whatever the feed's name holds) and a group XSLT's new version read it the same
way. A token whose numbers run past 9 digits (a timestamp, `Acme-v20261117093000123456`) is no version. Its documents follow the
pipeline's change where they hold the same token, else bump their own. For an Elasticsearch indexing pipeline the
index name's last token goes up (`acme-events-v1` to `acme-events-v2`; none: `-v2`, before the first variable,
`acme-events{_suffix}` to `acme-events-v2{_suffix}`), the copy's ElasticIndexingFilter gets it, and each Elastic Index
doc outside the build naming exactly the old index gets a new doc in the build for the new one (its settings and
fields, `version_of` the old), beside it. A new version under any of the original's names (pipeline, document, index,
Elastic Index doc) is refused.

Once the names are confirmed and before anything is copied, a new index name is looked up
(`ProductionIndexes.taken`, asked for by the user): on the cluster (`ExistingIndexes`, variables as wildcards) and
against every pipeline and Elastic Index doc outside the build, there with its variables filled in as for production
claims (`IndexNameValues`: the copied XSLTs and the build's feeds, so feed B's `{feed}-events-v2` isn't taken by feed
A's). Any data stream, index or alias it matches, even an
empty one, a lookup that fails (never taken as free), or another Stroom document naming it, refuses the copy, saying
what matched (name, kind, document count, the Stroom document), the pattern looked up and the next free version name;
nothing it matches is offered for deletion.

* **Arguments**: `build`, `source_uuid`, `new_name` (required); `rename` (text replacements for the copied
  documents' names, e.g. `{"V1.2": "V1.3"}`; JSON text or a single-quoted literal is read too:
  `PipelineArguments.textMap`); `set_properties` (changes on the copy, e.g. a new `indexName`); `working_copy`
  (refused when true: a copy is always a new version).
* **Returns**: `type`, `uuid`, `name`, `copy_of`, `copied_documents`, `source_types` (a new version of a group's
  pipeline: the registry rows moved to it), `index_name` and `index_docs` (a new version's own index and its Elastic
  Index docs), `test_index` and `test_index_note` (a copy whose indexing filter names a production Lucene index:
  pointed at the build's test index `<INDEX>-MCP-TEST` at once, made now, `BuildTestIndexes.redirect`; seen in an
  eval, case 61, the copy said nothing of one and the agent made test indexes of its own), `note`, and `next`; or
  `needs_names` (see above).
* **Refuses**: a missing required argument; a template (`PipelineTemplates.templateReason`: `... is a template
  (...): templates are inherited, not copied ...`, pointing at `create_pipeline`); an owned document the user can't
  read; a new version under the original's names (`A new version of X needs names of its own, and ... are the
  original's ... Nothing was copied.`); a new index name that is taken (`Index name '...' (looked up as '...') is not
  free for the new version: ... Nothing was copied. ... '<next>' is free ...`); `working_copy=true`; the user
  declining the form.
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
  (`... is not in a build folder: it is changed only in a build. Make a new version in one (copy_pipeline) ...`,
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
  Never a version this Stroom doesn't hold. A namespace given (`event-logging:3` or a fork's) where a version in
  that variant is already chosen for the build is given that version without asking; only another namespace asks
  again, and `schema_version_settled` tells the agent to give the version (or the fork's document UUID) from then on.
  The record keeps the variant as `namespace`; a choice made before the build had a folder is written to its record
  once it has one.
* **`TranslationBuilds`**: the mapping `changes=` apply to, the kept mapping, `generated` (the generator's result
  compiled as Stroom compiles it), stream meta no sample carries, a named time zone the event never records, the
  Unknown, domain and stand-in choices kept with the XSLT, and the build's source notes.
* **`SentMappings`** and **`UnsavedChoices`**: the mapping last sent for an XSLT (by uuid, or `build/name`) and the
  session's draft, under `/outputs/mappings/` in the session's workspace (where the agent can read them too), and
  the user's form answers for an XSLT not yet saved with them, under `/outputs/choices/`. A session without a
  workspace keeps neither. The mappings of a build (all but the draft made with no build) are also kept in the
  user's own workspace (`ToolCallContext.userWorkspace`, over MCP), read first, with the session and time sent.
* **`MappingSampleCheck`**, **`MappingProblems`**, **`DomainSplits`**, **`PlaceholderChoices`**,
  **`CurrentTranslations`**, **`LookupAgreements`**, **`XsltHistory`**, **`TranslationArgs`** (the shared argument
  descriptions and parsers): see the tools below.

The version history `XsltHistory` keeps in an XSLT's description records each save's author (the user, as Stroom
records a document's updater) and `by`: `Stroom <version>, <caller>`, where the caller is the external MCP client
by its own name, or the chat by the model it runs on.

### `draft_translation_mapping`

content · read-only · unlocked by View Data (sample streams are read as the user); checks View on the source notes
and streams it reads · Classes: `DraftTranslationMappingTool`, `DraftMapping`, `SourceNotes`, `CatalogueTranslation`,
`SiblingSharedCalls`, `CurrentTranslations`, `EventVersions`, `SentMappings`, `MappingCompaction`

A starting translation mapping drafted from the sample, so the agent edits a valid mapping rather than writing one
from nothing (a model asked to write one tends to send back the field inventory). `DraftMapping.draft` gives the
input kind and layout from the profile, the obvious event-logging homes for fields by name (time with its pattern,
host, client and server addresses and ports, user, event type, message: `FieldHomes`), its own rules (before the
source is catalogued, one per value of the naming field: the catalogue's seed), a text field's key/value pairs read
as one extract entry a key, every other field carried as Data, and notes on what is left to decide (the action element
per rule, System Name and Environment, a time zone). A quoted value's escapes that Stroom's Data Splitter keeps are
undone by the field's transform: a CSV's doubled quote by `unescape_quotes`, a key=value quote or backslash escaped
with a backslash by `unescape_backslashes` (`EscapedQuotes`; found testing awkward values, 2026-11-18, the events
holding
`say \"hi\"`), wherever the splitter spec holds the quoted delimited or key_value spec: its own, a syslog (or other)
header's `body`, an alternatives part or a part's body (seen in the agent eval, case 25: a syslog body's escapes
kept; the delimited spec's doubled quotes likewise since 2026-11-19), never the header's fields or the body's text it
was read from. `ActionRules` reads each rule's own records for its action: connections allowed or denied become
Network/Permit or Deny, logons and logoffs Authenticate, configuration changes Update, a service started or
stopped Process, health and state records Alert. With `build`, the build's source notes are followed: the field
dictionary's fields go where it puts them (record_source_notes), and the catalogue of event types
(catalogue_event_types) gives the rules (below). Records read by an
`alternatives` splitter (`VariantDrafts`): each variant's records are drafted on their own, as a stream of that one
format would be (its own time field and format, homes and rules), then put together: what every variant writes alike
is common, the rest of a variant's common entries go into its rules, and each rule is `when` its variant equals it
(the records no part read, `when` it is absent), named `<variant>_<rule>`; notes are each variant's, prefixed. Records with a `continuation` (a stack trace):
the exception it starts with and its message are extract fields (`exception`, `exception_message`), not a kind of
event, and the trace is carried as Data, whole unless the user chooses less: asked in a form ("All of it", "Its
first 20 lines", "Only its first line"), or `continuation_lines` (`DraftMapping.withContinuationLines`); without
forms, kept whole with a note to ask. Keyed records (`KeyedRecords`): a spec's `record_key` ties consecutive
lines holding one key into a record, `line_name` nests each line's fields under its name, `skip_lines` passes over
separator lines; the profile suggests them for key=value lines and counts keys that come back (interleaved, warned
of, not joined); the draft takes the time from an epoch in the key and leaves the key out. `StroomInput` reads a
sample as Stroom's parser does (characters XML forbids taken out) and names what it took. With `join_unkeyed`,
a line holding no key joins the record before it, and named markers start sections read whole (`keyed sections`:
ModSecurity's serial audit log). Text around key=value pairs: `words` keeps each run of text that isn't pairs as the field `words`
(`withWords` sets it where records fail without it and read with it); the same few words before every record's pairs
are read as fields of their own by a regex with the pairs as its body (`withPrefix`). `pairDelimiter` picks
", ", "; " or "|" when most pairs after a record's first follow it; such a spaced delimiter reads values to the next
delimiter, keys holding spaces only straight after it. `pairSeparator` picks `:` where most records hold three or
more quoted `key:"value"` pairs; a syslog record whose message is empty and whose bracketed structured data holds
pairs is read by them (`structuredBody`). The dry run keeps the first of same-named fields, as Stroom does. LEEF (`leef`) is
inferred as a regex reading any syslog header (as CEF's prefix is) and LEEF's header, the attributes a key=value body
separated by the declared delimiter (2.0) or a tab (1.0); a tab as a delimiter is a tab alone, not any white space. Formats shortlist
(formats-shortlist-generator.py, 2026-11-04), as first inferred (fixed since, but for text lines tied by a key,
which the agent gives by hand; the guide has a tested PostgreSQL example):
Check Point Log Exporter reads as RFC 5424 with its pairs left whole in `structured` (no `:`/`;` pair reading);
iptables, LEEF 1.0/2.0, OpenLDAP slapd and Cisco ISE infer syslog with a key=value body that Stroom refuses (text
before the pairs, bare flags, LEEF's header, verbs between pairs, `, ` between pairs, keys holding spaces); Postfix
reads, its messages split into key=value and text parts, queue ids not tied (interleaved); PostgreSQL reads as
records over several lines, sessions not tied (the key isn't key=value); ModSecurity reads as keyed sections. Time zones (`TimeSources`, `TimeZones`): a time read with no zone or
offset gets no `timezone` from the draft; the user is asked (UTC, a zone a field names, or the offset the gap between
the latest time and the stream's `ReceivedTime` suggests, rounded to a quarter hour, within 15 hours), or `timezone=`
gives it; `build_translation_xslt` warns of any still unset. With a build, the zone chosen (in the form, or given) is
kept in the build's record (`time_zone`: `zone`, `how`, `recorded`; `BuildRecords.rememberTimeZone`), as the schema
version and the long values choice are, so a redraft of the build reads its times in it without asking, its note
saying it is the zone the user chose earlier for this build and that `timezone=` changes it (seen in chats 185 and
186: a redraft with the System/Name asked the zone again, answered UTC both times); `timezone=` given wins and is kept
in its place; another build asks its own. The other answers a draft asks, `time_source`, `date_source` and
`continuation_lines`, are kept the same way (`draft_choices` in the build's record, by argument;
`BuildRecords.rememberDraftChoice`): given or answered (not "Leave it"), a redraft of the build uses them without
asking, its note saying the user chose it earlier for this build and that the argument changes it. A zone read record by record
(`ZoneSource`: `timezone` as `{field|meta, map, default}`, offered where the draft finds a field of zone names, or
`timezone=field:NAME|ZONE`) is written as `stroom:format-date(value, pattern, mcp:zone(...))`: mapped, plain offsets
normalised (`+5:30`), the default where blank; `LocalCheck` names the sample's zones Stroom's `getZoneId` won't read. Times (`TimeSources`): rules that write no `EventTime/TimeCreated` are asked
about ("When Stroom received the stream", "When the stream was created in Stroom", both approximations, or "Leave
it"), or `time_source=received|created|none`; times with no date and no header giving it (the draft's
`dateless_times`) are asked about the same way, `date_source=received|created|file_name|none`, the file-name date
offered only when a sample stream's `RemoteFile` holds one, the date joined with each time and `rollover` set. A
header line giving the date (`VariantDrafts`) needs no question: it becomes a `drop_when` rule, the splitter carries
its date onto the lines after it (`header_date`), and each dateless time is joined with it, with `rollover`. Without forms or the argument, a note says to ask. Which time
is the event's (`EventTimes`, the draft's `time()`): timestamp fields are ranked by name (an event's time; one that
may be, such as `created_at`, `start`, `end`, `date`; one whose name says nothing; an object's, such as `expires`,
`valid_*`, `last*`, `*modified*`, CEF's `file*Time`, never, kept as Data), the record's own before its container's
(a syslog header before CEF, JSON or pairs, by the spec's structure), a container time without a year (RFC 3164)
after any of the record's own. The container's time is the fallback record by record: `any_of [own..., container]`
with one `time_format` reading both (`[A][B]`, `TimePatterns.combined`, checked on the values); where none can (an
epoch number beside a pattern) the container's for every record, with a note. A rule whose records lack the common
time gets its own. Where two times are alike (same rank and side, each in 90% of the records, different
values) the draft keeps the first and adds `time_candidates` (`rule` when a rule's, `fields`: each with `share`,
`time_format`, `rank`, `from_others`, the median seconds from each other over records holding both) for the agent
to choose from, the time entry marked `choose_from` (the fields), which a mapping may not carry
(`FieldMapping.read` refuses it, so build_translation_xslt does, until the agent chooses; the draft's own
`schema_check` reads it without); a note lists every timestamp field (`Timestamp fields: ...`) with its share, rank and format.
Where the draft carries fields as Data, `data_placement` (`DataPlacements`) lists each rule's Data entries beside
the leaf elements below EventSource and the rule's action element (from the schema, Data aside), and a note says to
place each in the element that means it before building (asked for by the user: Netskope's city, coordinates, URL,
method and status code stayed Data until the user asked); the mapping guide says the same, for EventSource as well
as the action element. No field names are matched in code: the agent decides.
A name/value list (2026-11-23, told by shape, `NameValueLists`: see DESIGN.md, Name/value lists) is drafted as a
Data per pair, `{"path": "EventDetail/<action>/Data", "pair": {"list": "event.Parameters", "name_key": "Name",
"value_key": "Value"}}` (each Data named by its pair's name, holding its value), not a Data group of Name and Value
Data, with a note naming each list and its names (at most 10) and the pair form, `{"path": ..., "pair": {"list":
..., "name": "<its name>", "name_key": ..., "value_key": ...}}`, for the pairs whose names mean an element; a pair
read so is left out of the list's Data per pair. A mapping entry's `pair` is JSON input only, outside groups, and
names both keys (`value_key: "*"` for items holding their values under keys of their own).
An old/new property-change list (2026-11-23, told by shape, `NameValueLists.changes`: see DESIGN.md, Old/new
property-change lists) under an Update is drafted as its Before and After snapshots, `{"path":
"EventDetail/Update/Before/<Object>/Data", "change": {"list": ..., "name_key": ..., "old_key": ..., "new_key":
...}}` and the same in After (a Data per property of that side's value, JSON text of one value unwrapped, an added
property in After only, a removed one in Before only), the object the one another entry of the rule writes in a
snapshot (the catalogue's affected object), else `Object`; an array of targets each holding one is a group over the
targets in each snapshot, an object per target with a Data per key and a Data per property. Elsewhere it is an array
of objects as before. Which key holds the old value (2026-11-23) is the user's to say: the result's `change_lists`
names each list found (its keys, `by: user` or `order`, an item holding both values); in a build with forms the
tool asks of each list it proposed ("Which key of an old/new list holds a property's value before the
change?", the proposal first: the value key whose name says before, by its words (old, previous, prev, before,
original, from against new, current, after, updated, to; whole words or camel/snake parts, any case), else the
first in the items' order; 2026-11-23, chat 189: `{Name, NewValue, OldValue}` was proposed by order, wrongly),
keeps the answer with the build (`draft_choices.change_sides`, so a redraft isn't asked) and drafts again with it,
`catalogue_event_types` checking the build's placements by it too; without forms or a build, the same proposal is
taken, and the note says to check it and swap `old_key` and `new_key`; keys given in a placement or entry win. The note
also gives the change form for a property whose name means an element, typed in both snapshots; an item key
of few words in a list of targets (`type: User, Group`) is noted as what may name the object. A mapping entry's
`change` is JSON input only, its side its path's (below Before the old value, below After the new), allowed in a
group's fields (the list a key of the item). `rebuild_mapping` doesn't read the Data per property loop back from the
XSLT (as with the Data per pair loop): a rebuild relies on the kept mapping. Whatever placed them (2026-11-23, eval
case 76: the catalogue's targets as a group in After alone, no property written), the draft writes an Update's
old/new lists as a Data per property of each snapshot after the catalogue's translation (`SnapshotChanges`): a group
below a snapshot over items holding one gains its side's Data per property (a field reading the list whole
replaced), with a copy in the other snapshot where the rule has none; an object reading a record's list by name
gains the rest; the note starting "Old/new lists written as a Data per property in each Update snapshot" says what
was done. The "Not drafted" note leaves out an old/new list inside an array's items (its own note names it).
Numbers in a field named as a time are read as one where they can be: epoch seconds to nanoseconds by range
(1990-2100; before Sep 2001 only when named), and `windows_filetime` (FILETIME: 100 ns ticks since 1601). Before
cataloguing, a naming field (matched by its own name or with its parent's, `event_code`) may have up to 64 values,
each with a rule. Several user fields in one record (`users()`) are noted with their roles by generic prefixes, the
actor's drafted as `EventSource/User/Id`; a correlation id shared by records (`correlated()`) is noted, each record
mapped alone.

**From the catalogue** (`SourceNotes.applyToDraft`, `CatalogueTranslation`; DESIGN.md, Event type catalogue): once
catalogue_event_types has confirmed every event type and the catch-all, the draft's own rules give way to one rule per
(event type, way of recognising it), in the catalogue's order (more specific first), its `when` the way's conditions,
then the catch-all's rule for uncatalogued records (`uncatalogued`, TypeId `Unknown`, their fields kept as Data,
flagged `catch_all: true` with the user's agreement as its `allow_unknown`), left out after a sole event type, which
takes every record; an event type the user dropped is a `drop_when` rule per way. The catalogue comes first and the
XSLT follows from it. Each rule starts from the draft's rule that took the way's records in the sample (severity,
names, addresses kept), then takes what the catalogue says:

* **TypeId** fixed: the event type's (Verb-Object), else the default (`TypeIds.defaultTypeId`).
* **The vendor's code as Data**: every `equals`/`one_of` field of the way but the source type field, named after the
  field's last segment (`System/EventID` is `EventID`; a bracketed key's last key as written), under the action
  element (`EventDetail/<Action>/Data`, Network's own child), so searching by the code still works. A field another
  event type tests for its code is still Data of this one's records.
* **Description**: the record's own message, alone, where the records carry one; else the event type's description
  (the vendor documentation's text, or the agent's) as fixed text.
* The action element's Action and outcome, who acted (a field naming a person, not an address), the type's
  placements as typed entries (`affected`'s included; one reading a field the records haven't is left out and named
  in `unread_fields`; one in EventSource at its path from the root, left out and named there when the common entries
  write it), the user authenticating for Authenticate; then the fields left over that the way's own records
  hold, as Data of the action element. Each field once: the draft rule's entries below the action element reading a
  placed field are taken out, and a placed field is Data too only with `also_data` (the vendor's code is always Data).
* A field empty in every sampled record is left out of every rule (typed and Data alike, unless a placement names
  it), listed in `unread_fields` as "empty in every sampled record" and in a note (2026-11-21, chat 182).

With variants (mixed lines, alternatives, a saved converter), a way goes into the variants whose sampled records it
recognises (evaluated, not guessed from its last condition), prefixed with the variant's condition; each variant's
rule for its uncatalogued records is flagged `catch_all` with its variant condition alone, and a sole event type of a
line format (its conditions all on the variant field) needs none. Repeated groups' item conditions are allowed, and
where the catalogue reads a repeated group's items as its records (`for_each`, as catalogue_event_types reads them),
the draft does too: each item a record beside its enclosing record's own fields, the mapping's `for_each` set, those
fields read with `scope: record`, the counts of items (JSON arrays and lines; 2026-11-20). A JSON field holding a list
of plain values is written one Data a value (`repeat: true`). The result says each rule, `not_in_sample` (TypeIds no sampled record has), `uncatalogued` (sampled records no event type
recognises), `dropped` and `unread_fields`. A catalogue with event types or the catch-all still to confirm is refused.
Before any catalogue, the draft keeps its own rules, the guesses the catalogue is seeded from, its last rule (the one
for the records the others leave) flagged `catch_all`, and it says the source is to be catalogued before building.

A build with a scope over an aggregated feed's source types (`TranslationBuilds.scope`, from its survey) reads the
sample streams through it (`SampleStreamTexts.inScope`): each text split into its records, those of other source
types left out as they are read (by the source type field and the scope's rows, the first match winning, never by a
pattern over the text), the rest written back in the text's format; a text of none of the scope's records is left
out, one that can't be read record by record (XML, text) kept whole and said so, and a sample of none refused. The
result's `scope` says what was kept and left out (`build`, `source_types`, `field`, `records_in_scope`,
`records_left_out` by source type, `note`). So the rules drafted are the scope's; the plan's call gives
`build` for such a build.

The user chooses the event-logging version first (`EventVersions.target`), before anything is read. The draft is
compacted (`MappingCompaction.compactRules`, `compactPairs`) so it fits a reply: a rule's leftover fields as its
`data` list, pairs as `pairs` entries.

Then the environment's common elements: `SiblingSharedCalls.intoMapping` adds the shared templates the event
pipelines call (by two or more pipelines, writing one element, with no parameters) as `shared` entries, removing
what the draft mapped below that element; a call with parameters, or writing several elements, is left as a note
for the agent. This is done before drafting, not at the pipeline step, because the XSLT had otherwise been
generated without them.

* **Arguments**: `samples` (pasted text, by name or a list) or `stream_ids` (uploaded sample streams, read by the
  tool); `source_name`, `system_name`, `environment` (with `build`, kept in the build's record as the user's
  answers: left out later, the kept ones are used; given otherwise, refused unless `user_changed=true`);
  `system_name` is one name, a JSON object of each variant's, or, for a build whose scope is a group of several
  source types, a JSON object of each source type's (`SourceTypeSystemNames`: keys that are the scope's source types;
  one left out or another named is refused, as is a group whose source types aren't each told by exact values of a
  record's own field). Then System/Name is written from the source type field (`field`, `transform: clean`:
  control characters out, then trimmed, as the group strips the value to tell its source type, and `map`, each
  row's value, cleaned alike, to its source type's name; read with `trim` alone, a value with a bell or an ANSI
  colour code's escapes matched its row but missed its name, and an Event with no System/Name isn't valid). A name
  the user gives (here, one `system_name`, each variant's, or a source type's on record_source_types) goes into the
  Event without control characters (`HeldText.clean`; user, 2026-11-22);
  build_translation_xslt puts them on the group's registry rows (`system_name`) when it saves the group, so the
  draft stays read-only. Unnamed, the System/Name note itself offers one name or each source type's own, listing the
  source types (`SourceTypeSystemNames.offerInNote`, `DraftMapping.systemNote`; 2026-11-22, chat 184: a note of its
  own after it, cut from the client's list of notes, and the agent asked for one name), a note of its own only where
  the draft has no System/Name note;
  `splitter` (a build_data_splitter spec, else inferred);
  `build` (for source notes, the chosen version, the current translation); `schema_version` (a version, or where
  this Stroom holds the version in more than one XML schema document, the UUID of the one the user chose);
  `long_values` (`whole` or `cut`: the user's choice for JSON string values longer than the JSON parser keeps; with
  `build` only; given otherwise than recorded, refused unless `user_changed=true`).
* **Returns** (`schema_check.unfilled` says when rules' action elements need parts the draft couldn't fill):
  `mapping`, `source_notes`, `splitter`, `format`, `records`, `kinds`, `notes`, `unmapped_fields`,
  `fields_seen`; `long_strings` (JSON values longer than the JSON parser keeps, with those of events too long to
  sample) and `long_values` (the user's choice: `choice`, `json_parser_stringTruncateLength` when kept whole, and
  where it is kept); `time_candidates` (when two times could each be the event's); `schema_check` (`ok`, each problem's first sentence cut near 220 characters, at most 6 warnings;
  `ok: null` and `note` when the schema can't be read); `schema_version` and `schema_version_settled`;
  `current_translation` when the build's feed is already translated by a pipeline whose XSLT looks values up; for
  XML fragments, `xml_namespace` set to the namespace of the environment's wrapper; `hint`. Or the version question
  (`needs_guidance`).
* **A large draft, outside Stroom** (`DraftOutline`, 2026-11-20): for a client outside Stroom (not Stroom AI's chat)
  a result over 40,000 characters is saved to `/outputs/drafts/<build>.json` and gives `mapping_outline` in place of
  `mapping`: each rule's TypeId, recognised by (in words), Description, vendor-code Data and what else it writes, and
  the common entries; `writes` dropped, then rules cut with a count, if it is still too large; `data_placement`
  compact. The hint says how to read one rule's entries from the file (a workspace read cuts lines at 2,000
  characters) and to build with `changes=` only, `changes={}` building the draft as it stands.
  Still too large once outlined (eval case 68, 300 event types: 58,000 characters): the source notes' lists counted,
  each note cut to its start, the schema check's warnings in brief, `data_placement`'s rules grouped by Data and
  action, then `writes` dropped and rules cut, the rest saved to `/outputs/draft_translation_mapping/<build>.json`
  ([LargeResults](#build-tools)).
* **Refuses**: no samples and no stream_ids; a pattern of the agent's that took too long on the sample
  (`PatternTimeoutException`, returned as the refusal); with `build`, a catalogue whose event types or catch-all are
  still to confirm (`The catalogue's N event type(s) and the catch-all for uncatalogued records are still to confirm:
  call catalogue_event_types build=... confirm=true ...`).
* **Notes**:
  * **Long values, the user's choice** (`LongValues`, 2026-11-22): where the sample's JSON holds string values longer
    than the JSON parser's `stringTruncateLength` (10,000), a draft with `build` asks the user in a short form
    ("Some values are longer than Stroom's JSON parser keeps (10,000 characters): <fields>, the longest N
    characters. Keep them whole, or cut them?", answered "Keep them whole (raise the JSON parser's limit to L
    characters)" or "Cut them at 10,000 characters"; L is twice the longest, rounded up to a multiple of 10,000),
    or takes `long_values`, and keeps the answer in the build's record (`BuildRecords.rememberLongValues`:
    `choice`, `limit`, `longest`, `fields`, `how`, `recorded`); a later draft follows it without asking, a longer
    value raising a kept-whole limit. Whole: `create_pipeline` sets the build's JSON parser's
    `stringTruncateLength` to L (`filled_from_build`), and `update_pipeline` does on a pipeline of the build made
    before (`long_values` in its result), unless the call sets it. Cut: triage classes the parser's Warning benign
    for the build's pipelines (`BuildProgress.acceptedErrors`, through `AcceptedErrors`), so a step can be clean
    and processing go ahead, and `write_documentation` records it with the errors accepted (so it holds after
    promotion) and says, in its `note`, to say so in Open items. Once chosen, the note says the choice, not to ask
    (as for the time zone). Without forms, the note tells the agent to ask and draft again with `long_values`;
    without a build, nothing is asked or kept. Seen in user test chat 182: the user said "Cut them" in the chat,
    nothing kept it, and `create_processor_filter` was refused for want of a clean step; hidden until `step_sample`
    stepped past each stream's head (eval cases 56, 60, 61: a 25,486-character value at record 85).
  * **Held XML, each node once** (`HeldXmlPaths`, 2026-11-21): a held XML field's element is read as
    `<field>/<path>` and as `<field>//<name>`, an attribute under both and as `<field>//@<name>` too (all still
    readable, and the note offers `//`); the draft maps each node by one of them: its full path when its name is found
    at one depth, `//<name>` only when found at several, never both, and an attribute only under its element's chosen
    path (the others are taken, so not drafted as Data). Seen in chat 182: a Teams message body's `//p` and `/div/p`,
    `//@alt`, `//emoji/@alt` and `/div/p/emoji/@alt`, each value written two or three times. A time held in XML is a
    time field once, by the same path (2026-11-22): `time_candidates`, the time chosen and the timestamp fields note
    list it as the draft maps it, not also by its other paths.
  * **Held documents kept whole** (`HeldMarkup`, decided by the user 2026-11-22, chat 183): a field the draft would
    read through `xml_fields` is first told structured data or a document. (1) A sibling field (the same parent in
    the record) whose values name a format, in 4 of 5 of the records holding the XML: an XML or JSON type
    (`xml`, `json`, `text/xml`, `application/xml`, any `+xml` or `+json`; `HeldMarkup.STRUCTURED_FORMATS`) is
    structured data, mapped as before; any other format (`html`, `text/html`, `application/xhtml+xml`,
    `text/plain`, `rtf`, `markdown`, `message/rfc822`, `multipart/*`, `application/pdf`, an unknown type) a
    document. Values are compared, never field names. (2) Else HTML: the root in the XHTML namespace, or the root
    and at least half of the element names HTML's (`HeldMarkup.HTML_ELEMENTS`, lower or upper case; SOAP's `Body`
    is XML's) in most values: a document. (3) Else the element structure, each record's element and attribute paths
    compared with the other records' of its event type (the source notes' catalogue's first way recognising it;
    without one, the sample's): alike (sharing 80% of their paths with another record) in 80% of the records is
    structured data; alike in at most 30% of at least 3 a document whose elements differ from record to record; else
    unclear (two shapes half alike, one record). A document or an unclear value is not added to `xml_fields`: it is
    one Data value (with the control-character and length handling of any value), its parts unread, so processing
    logs no "did not parse as XML" for it, and the catalogue and stepping read it as the mapping does. The note says
    why and that its parts are mapped only if the user asks (then `xml_fields` in `changes=` with entries reading
    `<field>/<path>`); without a sibling naming the format it shows one sample (HTML) or up to three (each cut to 200
    characters, white space collapsed), and for an unclear value asks the agent to judge from what it knows of the
    source (a SOAP envelope, an XML event: structured), or to ask the user; for a varied one, to confirm with the
    user. A uniform payload (case 49's SOAP envelope) and a Windows event whose EventData varies by event id stay
    field-mapped (`TestDraftHeldMarkup`). `profile_sample` gives such a field `HELD_DOCUMENT_NOTE` (with why) in
    place of the held-XML note.
  * The draft is kept for the session (`SentMappings`, as `draft:<build>` and, with a build, `draft:`), so the
    first save sends `changes=` only: a 50 KB draft written out whole in one call never reached the tool in
    testing. With a build it is also kept with the build in the user's own workspace, so a new MCP session of the
    same user picks it up (the newest wins); build_translation_xslt says so in `changes_applied_to_one` (when it was
    drafted, in an earlier session). Another user never gets it. One older than the XSLT's last save, whose kept
    mapping differs, gives way to the XSLT's (`changes_applied_to_one` says so).
  * `current_translation` lists what the current XSLT looks up (its `stroom:lookup()` maps, the imported functions
    and named templates it calls, the reference data the pipeline loads) with a note asking whether the next version
    keeps them: in testing a V1.3 drafted from the sample lost V1.2's IP lookups.
  * It stays read-only although it records the chosen version, System Name and Environment in the build's record
    (`BuildRecords.rememberSystem`): that record is the tools' own bookkeeping. The System Name and Environment are
    kept there because, in the chat 163 review, the user answered Test and a later draft was sent Prod.
  * Bodies of `Key value` parts separated by ` - ` (`DraftMapping.pairsIn`, Pairs style `dashed`, keys such as
    `Group(s)` with their brackets escaped) are read as pairs where a text holds at least two spaced dashes and more
    such keys than `Key: value` keys (Secret Server's colon pairs hold a dash in a value: `Secret: Firewall - Edge
    Admin`); a key whose value is quoted is read to its closing quote (`dashed_quoted`), so a dash inside it is kept
    (`Group(s) "Staff - North"`), the others to the next spaced dash. Covered by a golden draft case (`netscaler
    sslvpn dashed pairs`) and through Stroom (`TestE2eDashedPairs`). A quote is a quoted value only where its closing
  quote is followed by a spaced dash or the end (an unclosed quote is read as `dashed`), and `pairExtracts` falls back
  to `dashed` where the quoted regex reads a value empty or past another key. A pairs value that is a list
  (`rcpt=a@x,b@x;c@x`, items without `=`) is the `listed` style, chosen over `equals` where any value is a list. A text
  whose pairs are all its record's fields already (the splitter's key=value body) isn't read again, and a key a line
  gives more than once gets `repeat: true` on its Data entry. A key after the header's ` : ` rather than a
    dash (`Context`) isn't read. A `key = value` with spaces round the `=` (`spaced_equals`: `(cpu = 99, threshold =
    90)`, NetScaler's SNMP traps) is read only in a text that holds pairs by its other styles, so SQL's `SET a = 1
    WHERE id = 2` in a message isn't taken for pairs.

### `build_translation_xslt`

content · write; read-only when neither `build` nor `uuid` is given (the code comes back) · unlocked by View Data
(sample streams); checks View and Edit; the field-mapping preview steps as the user (Stepping); the XSLT is created
in the build folder (Create) or changed (Edit) · changes only its build · takes a run slot · Classes:
`BuildTranslationXsltTool`, `TranslationBuilds`, `TranslationDocs`, `MappingSampleCheck`, `MappingProblems`,
`DomainSplits`, `PlaceholderChoices`, `UnsavedChoices`, `SentMappings`, `RequiredImports`, `CurrentTranslations`,
`Consent`, `XsltGenerator`, `MappingChanges`, `ConfirmedEventTypes`, `CatalogueKept`

A split Id written with `repeat` (a User the schema lets repeat, its Id not) becomes a `for_each: {array: <field>}`
group at the User, its fields `Id` (`item: true`, `transform: strip_domain`, the entry's other settings kept) and
`Domain` (`item: true`, `transform: domain`), in place of the repeat (seen in chat 183: a Domain put beside a
repeated recipient's Id, which the build's own check refused, and the agent's removal of it refused too); Keep turns
such a group back into the repeat, and a group like it answers Split for the field. The sample check reads a repeat
entry's JSON array values one by one for the domain question (a two-recipient array was never found, a one-recipient
one gave `["sam@acme.example"]`).

`domain_split` (Split / Keep as is) is the user's latest answer to splitting the domain off users' Ids, and wins over
one recorded earlier; Keep also removes a split the mapping already has, as does a change removing the Domain a split
wrote. For a source type group, the feed's promoted groups' answer is applied unasked when they agree (a
`domain_split` note); the user is asked, with the disagreement, when they don't or none has answered.

Writes the event-logging translation XSLT from a field mapping, and with `build` and `name` (or `uuid`) saves it
with the mapping kept in its description, returning the document rather than the code, so the XSLT need not pass
through the model. A call runs in this order:

1. **The mapping**: given whole (`mapping`), or `changes` merged (`MappingChanges.applyChanges`) into the mapping
   last sent for this XSLT (`SentMappings`), else the one kept with it (by uuid, or the build's XSLT of that name),
   else, for a build and name not saved yet, the build's latest draft, from this session or an earlier one of the
   user's (`changes_applied_to` says so, and `changes_applied_to_one` when it was drafted earlier); or neither, with
   `uuid`, to regenerate from the kept mapping as the generator writes it now (an agent asked to restyle an XSLT
   once sent its 1,400-line kept mapping back whole). Text that is JSON is parsed, its error located with the text
   around it. A list of fields given as the mapping with a sample comes back as a draft (`needs_mapping`) rather
   than an error, as small models send an inventory. A mapping refused as not a translation mapping (an entry with
   no source, say) is not remembered as last sent, and the refusal says so: later `changes` still apply to the mapping
   before it (seen in testing: refused changes became the base, and the agent's next changes, written against the
   mapping before them, were refused as removing what "the mapping does not have"). One that parses but fails its
   checks is remembered, for `changes` to fix.
2. **The version**: a saved XSLT's own (kept with it), else `EventVersions.target`.
3. **Generation** (`TranslationBuilds.generated`): `XsltGenerator.generate` with the imports standing instructions
   require added, then compiled as Stroom compiles it (`XsltCompileCheck`): one that doesn't compile is a problem
   and is not returned or saved (seen: an XSLT saved `ok` whose every record failed with "Variable kv has not been
   declared"). Problems come back at once, before the sample is read, with the rules' `events` left out so the
   whole list fits one reply (seen: 20-70 KB results each round, fixed a part at a time).
4. **The sample check** (`MappingSampleCheck.check`, below), over `sample` or the `stream_ids` streams' text. The
   forms that don't depend on it are asked first (decided by the user, 2026-11-20; seen in eval case 36: the check
   of a 1,000-record sample ran 61 s before the first form, and Claude Code gives up on a call after 60 s with
   nothing sent on it): where a new source type group's Events go, to a client that shows forms (kept in
   `UnsavedChoices` for the next call should this one save nothing). While it runs the client is told it is still
   going every 5 s (`CallProgress.whileWorking`). The forms on what it found follow it (step 5, and the reviews
   of confirmed event types translated otherwise and rules kept as Unknown). Where
   a stream's part isn't read whole (read only to the most a sample holds, or events left out:
   `SampleStreamTexts.Cut`), the records the build's
   survey found further into that part (its kinds' examples, by location) are checked too, after the part's header
   line where its records follow one (`SurveyExamples`), and a warning says how many; only for records a line each,
   not XML or a JSON array, whose records can't stand apart from their text. Seen in testing: a sample read to its
   4,004th record of 25,000 refused the fields of the rare kinds near the end (a file upload's name and size), and
   the agent read them through raw XPath to get past the check, which then checked none of them. `sample: []` is
   no sample. A save (`build`, or `uuid`) given neither `sample` nor `stream_ids` is checked against the build's own
   sample streams (`BuildPlan.rawSampleStreams`, the newest five), else the newest five streams its survey read
   (`SurveyDoc.streams`), a warning naming them; a build with neither is refused, asking for `stream_ids` or
   `sample`. A save whose check read no record is a problem (`no sample record was checked`, with the reader's
   note), so nothing is saved: a check over no records says nothing of the mapping. Seen in the agent eval (case 36
   on Haiku): saved with `"sample": []` and no `stream_ids`, 0 records were checked, the save returned ok, and every
   form the check drives (domain split, placeholders, confirmed event types, rules kept as Unknown) was skipped. An
   XSLT in no build is never saved (WriteGuard refuses it), so it takes no fallback.
5. **The user's choices** (only when saving): a user's Id whose values carry a domain (`DomainSplits`: split into
   Id and Domain, or keep whole; one question for all fields not answered before), and values that stand for none
   (`PlaceholderChoices`: leave each out, as `nil_values`, or keep as written; one form, at most 20 questions, 19
   one by one and one for the rest when more). If either changed the mapping it is generated and checked again.
   Each answer is kept at once (`UnsavedChoices`), then with the saved XSLT (`TranslationBuilds.rememberAgreed`),
   so it is asked once: before, a later form failing lost the earlier answer and the user was asked on every call.
   Warnings the user has answered are dropped, including those the second check brings back.
6. **Checks against the build**: rules contradicting the catalogue's event types not yet confirmed
   (`source_notes_check`, warnings `catalogue: ...`); a mapping that keeps none of the current translation's lookups
   (warning); **confirmed event types kept** (catalogue_event_types, `status: confirmed`): every sampled record a
   confirmed event type recognises must be translated by that type's rules, with its action element, fixed Action and
   TypeId (`ConfirmedEventTypes`, rules compared with event types by the records they take, `CatalogueKept` with
   `LocalCheck.ruleOf`, never by condition text); records taken by no rule, dropped, or by a rule writing another
   action element (Unknown among them), Action or TypeId are lost; not another Action the schema allows where the
   confirmed one is not, since that can't be written. Event types the user dropped must have their records dropped. A
   mapping of repeated groups (`for_each`), whose item rules this can't follow, is said to be not checked. Within a
   source type scope, only the scope's records are compared. A warning each, and on saving put to the user (below).
7. **The field-mapping preview** (`field_mapping=true` with `pipeline_uuid` and `stream_ids`): the pipeline is
   stepped with a marked copy of the XSLT (up to `max_records`) and the section rendered by `EventFieldDocs`.
   Without it `field_mapping` is null and `field_mapping_needs` says why: a table from the mapping alone showed how
   values are computed, not what they are, and was copied into documentation as it was.
8. **The save**, through `TranslationDocs.createXslt` / `updateXslt` (see save_xslt for the checks). Rules kept as
   Unknown are agreed with the user first (below).

A build with a scope over an aggregated feed's source types (`TranslationBuilds.scope`, from its survey) reads the
sample streams through it (`SampleStreamTexts.inScope`): each text split into its records, those of other source
types left out as they are read (by the source type field and the scope's rows, the first match winning, never by a
pattern over the text), the rest written back in the text's format; a text of none of the scope's records is left
out, one that can't be read record by record (XML, text) kept whole and said so, and a sample of none refused. The
result's `scope` says what was kept and left out (`build`, `source_types`, `field`, `records_in_scope`,
`records_left_out` by source type, `note`). So the sample check is of the scope's records, the survey's
records further in too.

**Source type groups** (`group`, `source_types`; `SourceTypeGroups`, `SourceTypeModule`, `HelpersXslt`,
`ModuleChecks`). With `group` (a name) in a build whose feed has a source type registry, the XSLT is that group's: the
whole XSLT of a pipeline of its own. It writes `<Events>` and holds the stylesheet-level declarations; each record of
its source types is handed to its templates in a mode of its own (the name in the mapping's naming, `pega_security`),
every name it declares prefixed with it; it writes its whole Event, calling the feed's common module's named
templates for EventTime, System, Generator and Device. Imports form a chain, each module imported once: the group
imports only the common module, which imports the feed's helpers (`mcp:json-to-xml`, `mcp:parse-xml`, `mcp:zone`) and
the site's libraries its shared calls name, all by `uuid='…', name='…'`. A rule mapping one of the common parts'
elements itself writes its own (its time, say). The first group's mapping makes the common module (`<feed> common`),
kept with its own mapping: its common entries for those elements that read only the record's own fields or
constants, its shared calls, the registry's Environment and default System Name; the System part takes each group's
System Name (its source types' rows', else its mapping's) as a parameter. A System/Name read per record (each source
type's own, a `field` and `map`) is never the common module's: the group writing it keeps its whole System part (with
the registry's Environment), so a later group gets its own name or the common module's. A later group's entries the
same as the common module's (its System Name and Environment aside) are left to it; others are its own, said in
`group.notes`, written in the group's own XSLT in place of the module's part, for that group only: a group never
changes the shared module (decided by the user 2026-11-23, chat 189). The registry promoted into the feed's own
folder (`<feed folder>/<feed>`) is found there, a build's registry naming no module takes the promoted registry's,
and a later group whose feed's common module can't be found (another group translated already) is refused, never
made anew from its mapping. The shared Generator is the feed's, naming no source type (chat 188): a first group's
Generator naming the registry's source types (a row's name, matched value or System Name, any case or spacing, or a
name's last word not the feed's; `FeedGenerators`) stays its own, and the module takes the feed's own, asked once in
a form (proposed with the source types' wording taken out) or given as `feed_generator`, kept in the registry
(`generator`) for later builds; with neither, the module says `TODO generator` (the draft's default), the notes say
to ask, and a later call's `feed_generator` replaces it (the one change a group makes to the module);
a shared call of a later group's that the common module lacks is added to the common module, saved again (in place,
or as a new version of a promoted one), never imported by the group. **One source type group a build**
(decided 2026-11-13): `group` defaults to the one the build's scope names (`record_source_types`; a new translation in
such a build with no `group` is that group's), and another group name is refused (`one source type group a build`;
for a scope kept before scopes named their group, another group whose XSLT the build made is refused). Its source types
are `source_types`, else the build's scope's with those the group translates already (an existing group named in the
scope keeps its own); the sample check reads theirs alone. A build with a whole-feed scope (no registry) is
unaffected. Saving: every gate decided before anything is written (decided by the user, 2026-11-24): the feed's
modules' hand edits (below) when the group is planned, then the group's own XSLT's hand edit (`HandEditGate`, on the
XSLT the save goes to, or the promoted one its new version copies), then the helpers (made, or written again when this
generator writes them otherwise) and common module, then the group importing the common module by UUID, and the
registry's rows for the group's source types set `translated` with `group` and `group_xslt`, and its `modules` the
versions saved. **A module edited by hand** (`ModuleEdits`, decided by the user 2026-11-24): before a save writes the
feed's common module or helpers (in the build, or as a new version of a promoted one), an edit made in Stroom since the
tools saved it is told, as the version history tells one (`XsltVersionHistory`: code the tools last saved is an older
generator's, out of date, never an edit). A common module's constants are read back from its code (each where the
generator writes it), proven by generating the module from them, which must give its code exactly: values read so are
an edit whatever the history says, and what the module runs, so the group is split and documented from them (a
hand-replaced `TODO generator` is no placeholder, and isn't asked for), said in `group.notes`. Helpers of the
generator's own version other than its code are an edit. When the save would write the module, nothing is written yet:
the user is asked in a form ("The common module was edited by hand: '<feed> common' was changed in Stroom since the
tools saved it (by <user>, <date>): <path> is now "…" (was "…"). This build needs to …"): **Keep the edit
(recommended)** (the common module generated from the mapping read back, what the build adds beside it, its kept
mapping then matching its code; helpers left as edited, nothing added), **Discard the edit** (the module goes back to
the generated code), or **Stop** (refused, nothing changed). The helpers are asked first (kept, the common module's
import stays as it is). An edit that can't be kept (a common module changed in more than its values; helpers of an
older generator's version, whose new functions can't be added beside the edit) offers discard or stop, saying why.
Without a form, refused saying the choices: the agent asks, and calls again with `hand_edit_choices={'<module name>':
'keep' or 'overwrite'}`. Answers are kept for the module's code as edited until the group is saved. A group's new XSLT is named for the group, as its pipeline is (`SourceTypeGroups.xsltName`: the
registry's pipeline name for the group, else `SourceTypeGroups.pipelineName`, `<feed> <group> V1.0`), whatever `name`
the agent passes (seen in chat 185: saved as the agent's `Azure-Audit-V1.0-Events` beside a pipeline create_pipeline
named for the group); where it replaces a name given, `name_note` says so and to use the uuid from then on. No form
shows the XSLT's name, so nothing is asked; an XSLT the group has already (the registry's) keeps its name. A later
call naming the group finds its XSLT by the registry where the build has none of the name given
(`savedName`: changes=, and what the user agreed to) (the feed's newest, for a new group to import). The modules a group has are those its XSLT imports
(`SourceTypeGroups.modules`: its common module by the module's mark, and the helpers that module imports; the
build's copy of the group's XSLT where it has one), so groups may import different versions; a new group takes the
registry's.

The group carries a snapshot of every registry row when saved (its kept mapping's `module.rows`), tries them in order
(`=` a value, `matches()` an anchored pattern, checked in Java's and XPath's dialects alike, `∈` an item of the value
split by `tokenize()` on the row's separator, each item stripped, compared exactly, as `SourceTypeRows.items` reads
them), and translates a record whose first matching row is one of its own source types. A group whose field is a
stream meta attribute (`meta:<Attribute>`, `SourceTypeModule.byPart`) reads it once a document (a document is of one
part, and `stroom:meta()` reads the part's, in stepping and processing alike): a part of the group's has every record
handed to the group's mode; any other part's records are passed over whole, never judged one by one (no pass-over
template). A group on a record's field judges each record as below. It reads a record's source type value as the tools do
(`SourceTypeFields.valueOf`): stripped of the white space around it, and no value for `-`, `null`, `none` or `n/a`
(any case), so processing and the survey count the same records as each source type. A group whose field is a
reading into held values (`SourceTypeReadingXslt`) reads it with functions of its own (mode-prefixed): each held
value's format told by the tools' rules in their order with their regular expressions (`HeldText`: `json-to-xml`
of an object or of a JSON string holding one, `parse-xml` of a text holding no `<!DOCTYPE`, the syslog headers,
`tokenize()` counting pairs; held JSON is JSON itself, `ToolJson.loadsStrict` in the tools: no `NaN` or `Infinity`,
each number read as written, `2.10` and `1e10`, as `json-to-xml` keeps it), then
stepped into as the tools step (JSON keys; XML elements and attributes by local name, the first in document order;
a key's first `key=` value; a syslog field), each reading's value normalised (control characters but tab, new line
and CR taken out, and U+FFFD, which `parse-json()` reads them as, then white space around it: a row's value can't
hold one, as no XML 1.0 XSLT can; found testing awkward values, 2026-11-18), the first with a value taken; the
`(no value)` row matches an empty value (`$value = ''`). A reading that isn't one is refused (`groupProblems`). Parity
with the tools is tested over held JSON (once and twice encoded), XML, key=value, syslog, two and three held values
deep, plain text and missing or malformed held fields, and awkward values (controls, line ends, astral and
right-to-left text, XML's specials) given to the XSLT as Stroom's jsonParser gives them, XML 1.1
(`TestSourceTypeReadingParity`, `TestSourceTypeTupleParity`). A held field longer than the jsonParser keeps
(`stringTruncateLength`, 10,000 by default) reads whole to the tools and cut in processing, so differently: the cut
is a Warning triaged for review, with the property to raise. Every other record (another
group's, parked, dropped, or a value no row matches) is passed over, its record's number read so the group's log
lines name the right records, never logged nor warned of: an INFO line a document of the records passed over
(`XsltGenerator.PASSED_OVER`, written by group XSLTs generated before 2026-11-15) made Stroom write an Error stream
for every raw stream each group processed in production. The tools tell the records passed over from the registry
instead (`PassedOverRecords` in stepping, `SourceTypeCoverage` in review_coverage); the older line stays triaged
benign. A group's held message that doesn't parse (a
truncated SOAP body) is logged (WARN) a record each, `<mode>: <field> did not parse as XML ...`; its event is still
written, triaged benign, never holding a clean step. Its comments are written through
`XsltComments.comment`, so a name holding `--` (or more) still makes a well-formed comment. A group's names are its
own when they are its mode, or its mode followed by a separator (`_`, `-`, or a capital or digit starting a camelCase
word). A group XSLT's `uuid` alone regenerates it as the group it is (its kept mapping holds `module`).

**Where its Events go** (`placement`). Saving a new group (none of its rows placed or translated yet) asks the user
in a form before anything is saved: the group gets a pipeline of its own (`<feed> <group>`, recorded on its rows
then, so the group counts as placed), and "Where do the Events of group 'x' go?" ("Into the same feed (<feed>)", the
default; or "To an output feed of its own (made next)"; the group's index is its own either way, decided
2026-11-14). Without forms, or where its form couldn't be shown (the refusal says which), the call is refused,
nothing saved, until `own_output_feed=true|false` says the user's answer, which is then read before any form is
asked (seen over `/mcp`: a client that couldn't show the form refused every call, the answer read only after it); given for a group already placed, it is
ignored, said in `group.notes`. Source types added to a group already placed join its pipeline and output feed, said
in `group.notes`, with no form. A source type another group translates is refused (a source type belongs to one
group), as is a group whose mode is the common module's. The rows record it (`pipeline`: `name`, and `uuid` once
`create_pipeline` makes it, `new_version`; `output_feed`, empty until `create_feed group=` makes it).
`group.group_next` says what follows: `create_feed group=` first where it has an output feed, then `create_pipeline
group=`. `references` (as `create_pipeline` takes them) are kept on the group's rows: its pipeline loads them
(`create_pipeline`, and promotion).

**A promoted group** (`promotedPipeline`, `SourceTypeGroups.groupTarget`): a change to a group whose pipeline is
promoted is always a new version (decided by the user, 2026-11-17), nothing asked: a new XSLT in the build's Events
folder recorded `version_of` the original, which `copy_pipeline` of the group's pipeline then runs; the group's rows
move to the new pipeline. The new XSLT is named for its pipeline's new version (`SourceTypeGroups.versionName`:
`VersionNames.following` the pipeline's next version's token, as `copy_pipeline` names what it copies;
`PEGA security` for pipeline `PEGA PEGA security` becomes `PEGA security-V1.1`). A promoted shared module the change
needs (a shared call added to the common module, or the helpers out of date) is never changed in place either
(decided by the user, 2026-11-17): a new version of it is made in the build (`newModuleVersion`, recorded
`version_of`), named `<base> V<next>` after the highest version the feed knows (the module changed, the registry's
newest; one with no version is V1.0, so `PEGA common V1.1`; `VersionNames.nextModule`), and only the group's new
XSLT imports it; the feed's other groups keep the versions they import, and nothing promoted is rewritten. A
helpers' new version makes a new version of the common module too, its import of the helpers rewritten by text (code
and kept mapping), as the chain is by UUID; a common change makes no helpers version. A later save in the build
changes the build's versions where they are. At promotion each goes in a version folder beside its predecessor
(`promote_build`).

**A feed translated whole** (no `group`, `SourceTypeGroups.seenSourceTypes`): in a build whose survey confirmed a
source type field, the mapping keeps `seen_source_types` (the field, and the values seen: the registry's rows where
the build has one for the feed, else the values the survey read; none where the survey met more values than it keeps
one by one), said in `seen_source_types` in the result. The XSLT logs, once a stream a value (a `stroom:put` key),
each value of the field outside them: `New source type 'X' in <field>, not seen when this feed was surveyed: survey
the feed again to cover it` (INFO), the value read as a group reads it. Its records are translated by the rules as
any other; the line is triaged benign, its reason telling the agent to suggest a new survey; `review_coverage`
reports the values (`unseen_source_types`). Outside a build the mapping's own are kept. A feed without a source type
field gets nothing new.

`group` in the result: `name`, `mode`, `source_types`, `system_name`, `common_parts`, `notes`, and once saved
`xslt`, `helpers`, `common` (each `uuid`, `name`, `changed`, `promoted` for one beside the feed), `pipeline` (the
rows' pipeline), `output_feed`, `registry` (`uuid`, `name`, `translated`) and `group_next`.

* **Arguments**: `mapping` or `changes` (objects, or JSON text); `schema_version` (a version, or the UUID of the
  XML schema document the user chose where several hold it); `feeds` (for standing
  instructions); `sample` (text or list) or `stream_ids`, every part of a stream read as its own text; `splitter`
  (left out, a build with one Data Splitter has the sample read with it through Stroom's own parser,
  `SplitterSpec.ofConverter`, as processing reads it; else a spec inferred from the sample); `build` and `name`, or
  `uuid`; `change` (the
  version history line); `include_xslt` (also return the code when saving); `reviewed` (a `review_id`);
  `field_mapping`, `pipeline_uuid`, `max_records` (1 to 1000, default 50); `discard_hand_edit`,
  `hand_edit_choices`; `group` and `source_types` (a source type group, above); with `group`, `own_output_feed`,
  `feed_generator` (the feed's own Generator as the user said it in the chat, where no form can be shown) and
  `references`.
* **Returns**: the generator's result (`ok`, `xslt` unless saved, `problems`, `warnings` grouped, `events`),
  `schema_version` (and `schema_version_settled`), `required_imports`, `sample_check` (`records`, `problems`,
  `warnings`, `fields_seen`, `not_checked`, `kept_unknown`, `note`; an XML 1.1 sample, as Stroom writes XML, is read as
  XML 1.1: read as 1.0, one holding `&#xc;` was "not XML a parser could read"), `changes_applied`, `changes_applied_to`,
  `changes_applied_to_one`, `source_notes_check`, `set_properties` and `set_properties_note` (JSON input: the JSON parser's `addRootObject`,
  true for JSON lines, for create_pipeline), `field_mapping` / `field_mapping_sample`, `saved` (`type`, `uuid`,
  `name`, `version`) with `next`, `follow_on` (on a replace: review_coverage reviews the pipelines reading its
  Events), `standing_instructions`, `hint`. Or `needs_review`, `needs_guidance` (version, hand edit) or
  `needs_mapping`.
* **Refuses**: `mapping` and `changes` both given; neither, with no `uuid`; `changes` with nothing to apply to;
  a mapping that doesn't validate (each error with where it is and the value given, a whole example mapping when
  its shape is wrong: `MappingProblems`); `max_records` out of range; a `splitter` that isn't a spec; saving with
  `build` but no `name`; keeping a rule Unknown with no sample records to show (see below); a catalogued build's
  save while its catch-all is unconfirmed (declined, say: ask the user in the chat, then catalogue_event_types; a
  catalogue read back from a hand-written translation isn't held to it, as its notes' `made_by` says; one the tool
  made stays so when a hand edit is read back into it); and
  every save refusal of save_xslt. `changes={}` is the mapping as it stands (the session's draft, or the saved
  mapping); a source type group's first save with no `name` changes the build's draft. With `group`: no source type registry in the build; `source_types` not the registry's, or
  none and no scope; a name whose mode is the common module's; a later group whose feed's common module can't be
  found; a new group saved with no form and no `own_output_feed`; a promoted group's change with no form and no `pipeline`; a module that doesn't generate.
* **Notes**:
  * **The same input written twice** (`WrittenTwice`, 2026-11-21): a warning, never a problem, when one rule writes
    the same input (the same entry but for its path, data_name and data_parent, read in the same groups' for_each)
    to two places as Data and Data, or as Data and an element, naming the rule, the input, both places
    (`.../Data[@Name='x']/Data[@Name='y']`) and the `changes` removing one (the Data beside an element, else the less
    deep Data; a group's, when the copy is in one). Two typed elements (UserId as EventSource/User/Id and
    Send/Source/User/Id) and the same Data in two rules are not warned of; a top-level field written as it is to an
    element and as Data keeps its own warning ("the Data entry says it again"). Seen in chat 182 (rule
    update_inboxrule): `event.Parameters`' items as Data of `Update/After/Object` and of `Update`, the draft's copy left
    in as the agent's removal named another path. The mapping's common entries are checked against each rule's too
    (2026-11-22; two common entries alike are not, as they would be said for every rule): a common entry and a
    rule's writing the same input as Data and Data, or Data and a typed element (but a top-level field as it is,
    which keeps its own warning), the rule's suggested for removal unless the common one is the Data beside a typed
    element, then `changes={"common": [...]}`, saying every rule's events hold it. A `repeat` entry and a group over
    the same field (`for_each: {array: <field>}`, its field the item) are the same input: each writes the field's
    values one by one.
  * **The vendor's code kept** (`VendorCodeKept`, 2026-11-20): a confirmed event type whose rule no longer writes its
    vendor-code Data is warned of in `sample_check` (`sample: …`), naming the event type, rule, field and Data; the
    XSLT is still built. Not for the catch-all, drop rules or event types not confirmed.
  * **A large result, outside Stroom** (`BuiltRulesTrim`): over 40,000 characters for a client outside Stroom,
    `events` counts each rule's Data rather than listing it, naming the first 12 (`data_names`, then `… N more`;
    `events_note`), and `sample_check` leaves out its
    `warnings`, which the result's `warnings` repeat (each starting `sample: `; `warnings_note`). Seen in eval cases 35
    and 36 (2026-11-20): a third of results of 69,000 and 100,000 characters said twice. Still over (eval case 68,
    300 rules: 110,000): each warning without its example (long rule lists counted past five), then each rule in a
    line with its first Data names (`send_mail: … (16 fields, 23 Data: subject, sender, … 11 more)`; 12, else 6 or
    3, else counted only, as many as fit the room the warnings left; seen in chats 184-186: 13 reads of the saved
    result for which fields each rule kept as Data), `changes_applied` counted; problems, `saved` and `xslt` kept whole (`LargeResults`).
  * **Confirmed event types translated otherwise** (`agreeConfirmedChanged`, before the Unknown review; decided with
    the user after the chat 163 review, where confirmed logoffs went to Unknown and a confirmed Action Other became
    Logon, unsaid): `needs_review` with a table (`| Event type | Recognised by | You confirmed | Records | Now | Rule
    |`), the same table and `review_id` from call to call while the mapping is unchanged, then
    a form: "Keep the mapping's change" (kept with the XSLT, `TranslationBuilds.CONFIRMED_CHANGED`, so asked once,
    its warnings dropped) or "Follow what I confirmed" (nothing saved: `ok: false`, `confirmed`, `hint`), or the
    user's own words (`user_says`). Without forms the warnings are left for the agent. The Unknown review lists,
    under a rule, the confirmed event types among its records.
  * **After a failed build**, `next_changes_apply_to` says what the next `changes=` start from: this call's mapping,
    kept for its uuid or build and name, not the draft or the saved XSLT; or nothing, without either.
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
    select nothing, an xpath without `repeat` giving several values (not a Data per pair or per property, the loop
    writing an element per item, nor a pair or property read by name, one value; 2026-11-23, chat 188: the agent
    tried `repeat: true` on a Data per pair three times, refused each time), Data a rule's records never hold, required
    elements the sample leaves empty, domain-qualified Ids, stand-ins for none, and each extraction regex run with
    XPath's regex rules on the text it reads (`XPathChecks`). It stops after 60 seconds (`CHECK_TIME`), naming the
    extractions left unchecked: a Fortigate sample (10 streams of 1 MB, 60 extract entries) held the call for over
    a minute.
    The lists `pair` and `change` entries read count as read (no "fields in the sample that nothing reads" for them),
    and a change's xpath is checked as the record holds its values, without the generated XSLT's `mcp:` unwrapping
    (2026-11-23, chat 188).
  * **Old/new lists written per property** (2026-11-23, eval case 76: each target's list written as a `for_each`
    group of name and value Data, its values JSON text, in After alone): once the sample is read, a JSON input's
    Update rules are given what the draft gives them (`SnapshotChanges`, the records telling the lists by shape): a
    group below a snapshot over items holding an old/new list gains its side's Data per property, a field reading
    the list whole (or a group over it) replaced by it, and a copy in the other snapshot where the rule has none; a
    plain Data of a record's list below a snapshot becomes its side's Data per property (below an Update but no
    snapshot, both snapshots' of the object the rule writes); an object reading a record's list by name gains the
    rest. The old key: a change of the list in the mapping, else what a group in Before reads, else the items'
    order. The mapping changed is generated and checked again and kept as changed, and a warning starting "Old/new
    lists written as a Data per property in each Update snapshot" names each change and its rules.
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

Since 2026-11-24 (chat 192):

* A group the registry places nowhere (its folder deleted, say), whose promoted pipeline named for it (`<feed> <group>`,
  with or without a version token: `SourceTypeGroups.isGroupPipelineName`) reads the feed (found as a next version
  finds its predecessor: `CurrentTranslations.groupPipeline`, pipelines whose processor filters read the raw feed, not
  in a build), is placed from that pipeline: its rows name the pipeline, its XSLT and its output feed (a stream
  appender `feed` naming another feed), never asked as a new group; the change goes to a new version as for any
  promoted group, and `group.notes` says it was recovered and recorded with the save
  (`SourceTypeGroups.recoveredPlacement`).
* A constant (common or a rule's: Device or System Name, Generator) naming one of the feed's source types outside the
  group (compared without case, spaces or punctuation) is put to the user in a form (`A value names another source
  type of the feed: ...`): "Change it" is a problem, nothing saved; "Keep it" is remembered for the chat. Without a
  form, a warning to check with the user (`SourceTypeGroups.namingOtherSourceTypes`).
* The group's promoted pipeline whose XSLTs import a module that no longer exists is said in `warnings`
  ("Production pipeline '...': its XSLT '...' imports '...' (uuid), which no longer exists ...").

### `rebuild_mapping`

content · write · unlocked by Stepping; checks View and Edit (the XSLT, its pipeline and the XSLTs it imports are
read as the user; the save is the user's Edit) · confirms itself · takes a run slot · Classes:
`RebuildMappingTool`, `XsltReader`, `MappingRebuild`, `RebuiltMapping`, `SampleStreams`, `PipelineStepping`,
`TranslationDocs`, `Consent`, `CatalogueFromMapping`, `CatalogueNotes`

Rebuilds the mapping kept with a translation XSLT from the XSLT itself: when it was lost (the Documentation tab
cleared; build_status says so) or no longer matches the XSLT (edited by hand in Stroom). The XSLT is read back as
the generator writes it (`XsltReader.rebuild`). With the kept mapping, every element that still reads as it
generates keeps its entry exactly and what differs becomes new or removed entries; without one, the mapping is read
from the XSLT whole: fields, key=value extractions, maps, time formats, defaults, `max_length` caps, reference-data
lookups, dictionaries, and the shared templates and functions of the XSLTs it imports (fetched by name as Stroom
resolves the import, only when exactly one such XSLT is visible to the user); anything else is kept as an xpath
entry.

**A loop of one element per value** (`HandLoops`, 2026-11-22): an `xsl:for-each` with no variable holding its item
(written by hand, or a `repeat` loop of the generator's) that writes one element with elements of its own, e.g. one
`Email` per address, each with its `From/EmailAddress`, is read back as a `for_each` group on that element: its
fields relative to it, the loop's current value (`.`) read as the item (`item: true`), its items a field's values
(`array`) or `tokenize(field, 'separator')` (`split`, a literal separator; a split's pieces are trimmed and empty
ones dropped, which the rebuild notes). Since `repeat: true` writes only the leaf or the element holding it a value
each, where that element repeats in its own parent, a loop writing a higher element (Email repeats in Payload,
From doesn't in Email) was read back as a `repeat` the generator now refuses; as a group it regenerates the same
Events and validates. A loop reading below its value (a relative path, `@value`), writing anything but that one
element (a Data or a bare leaf is read as below), or holding another loop or a template call, is still said not to
be read. With the
kept mapping, a `repeat` loop that still reads as it generates keeps its entry. A repeated Data entry's value
transformed or cut (`substring(upper-case(.[1]), 1, 3)`) is read back with its `transform` and `max_length`.

**A repeated Data outside a named Data** (2026-11-22): a loop writing one Data a value straight into its element
(no `data_parent`; a constant Name, the Value the current value or an expression of it, as the generator writes a
`repeat` Data entry, or a loop written by hand alike) is read back as that entry, by its Name, lost or kept; it was
read with no Data name, so no entry wrote it (the remaining problem in reader golden 108). A JSON array's members
(`*[@key='x']/*`) are read as the field `x`, as a group's items are. A Data named from its value (`Name="{.}"`) is
still not read. **A bare leaf a value** (2026-11-22): a loop writing one leaf element a value (one `xsl:value-of` of
the current value; the generator's `repeat` on a leaf that may occur more than once in its own parent, or a loop
written by hand) is read back as a `repeat: true` entry where the schema lets that leaf repeat in its own parent
(`EventSchema.repeatsInPlace`, e.g. `EventSource/System/Tags/Tag`; rebuild_mapping passes the kept schema), else it
stays a problem, saying the schema allows one there.

**XSLTs written by hand** (`HandLayouts`, 2026-11-24): before reading, a translation not laid out as the generator
writes one (its start template applies templates with no mode) is laid out as it does, changing what is read, never
what the XSLT does: a template per event (`match="map[string[@key='action'] = 'login']"`), each a rule, its pattern's
predicates its condition, in the order XSLT picks them (priority, then the last first), an empty one with a pattern a
drop rule; one template per record; an `xsl:choose` inside the Event (on an event id, inside EventDetail), a rule per
branch with the branch in the choose's place, a choose inside a branch taken in turn with the tests joined; a chain of
`xsl:if` each writing an Event, a rule each in turn; JSON keys read by type (`string[@key='x']`) read as any key. Each
rule is named by its constant TypeId, else `rule-N`, `other` for the last with no test. Named templates of the XSLT's
own are followed with their parameters bound (an `xsl:param` is no longer said not to be read), imported helpers as
before. In such an XSLT a test no field reads is an xpath condition (`{xpath: "@severity", equals: "high"}`, else
`{xpath: "string(boolean(...))", equals: "true"}`: a `present` test of the record read its text, and a records:2
record or an XML element of attributes has none), and an element written only under an `xsl:if` testing more than
its value being there (`$user != '-'`; `x != ''`, `normalize-space(x)`, `exists(x)` are presence) is listed as not
read and in `unread` (no entry says it). The summary's `written_by_hand` says how the layout was read, apart from
`not_read`. The reader's hand-written fixtures (`TestHandWrittenTranslations`, `generation/hand-written/`: a choose
on an event id, a template per event, named templates, an imported library, a TypeId from `concat()`, variables and
`xsl:if` chains, priorities and an empty template) pin what each reads and every output path that differs.

The XSLT regenerated from the rebuilt mapping is then stepped beside the XSLT over the sample (up to 200 records
each way, `PROVE_RECORDS`) and the outputs compared path by path (`EventXml.fieldValues`). Only when every record's
output is the same (or the user accepts the differences) and the user confirms in a form are the mapping and the
regenerated XSLT saved.

**The catalogue read back** (an XSLT in a build): the rebuilt mapping's rules are read back into the build's catalogue
of event types (`CatalogueFromMapping`), so the catalogue stays the source of truth: each rule a way of recognising an
event type (its `when`), the rules of one fixed TypeId one event type, its meaning from what the rule writes (action
element, Action, outcome, who acted, Description); the rule flagged `catch_all`, or a hand-written last rule taking
every remaining record, is the catch-all, with its own translation when it writes a real event, not Unknown; drop
rules are the dropped event types they name. The changes are shown in the form (`event types`) and, saved, written
into the source notes (`CatalogueNotes.write`, the JSON block and the "Event types" table together) when they change
it. What the catalogue can't express (a rule writing its TypeId from a field, a drop rule of no event type) is named
(`not_read`), the catalogue's entry left as it was: the XSLT keeps it, and a draft from the catalogue would lose it.

**A build started from an existing pipeline** (its record's `start_from` is `existing`, see `catalogue_event_types`):
the result's `existing_run` says how the XSLT as it is ran over the records stepped (`records`, `events`, `no_event`
and `no_event_records`: dropped by a rule or failing, `unknown` and `unknown_records`, up to 10 each), and the event
types read back that the catalogue didn't hold confirmed are saved as proposed with basis `existing_pipeline`
(`EventType.readBack`: their TypeIds kept as the pipeline writes them, Verb-Object or not, `TypeIds.keptProblem`), so
`catalogue_event_types` checks them against the sample (uncatalogued records, event types the sample lacks, overlaps)
and puts them to the user.

* **Arguments**: `uuid` (required); `stream_ids` (else `SampleStreams`: the stream ids of the sample processor
  filters the tools made, those still there, else the feed's newest raw streams; the reply says which);
  `pipeline_uuid` (else the one pipeline whose own layer runs the XSLT); `change`; `accept_differences`;
  `accept_unread`.
* **Returns**: `xslt`; `status: in_step` when the XSLT is already what its kept mapping generates; else `mode`
  (`written by an earlier generator`, `out of step with the kept mapping`, `mapping lost`), `rebuilt` (the summary:
  kept, new, removed, kept as xpath, calling imported functions), `proven_on` (`records`, `streams`, `which`),
  `differences` (at most 20 paths, most records first, each with an example record), `existing_run` (a build started
  from an existing pipeline), `unread`, `problems`, `saved`,
  `catalogue` (`written`, `event_types`, `changes`, `not_read` and `note`) and `next`.
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
user); `alternatives` (`parts`, each with a `variant` and its kind's own `match` or one given, tried in order, the
first whose match fits a record's start reading it, the variant written in `variant_field`; inferred for `mixed
lines`, a part for every format the records hold, timestamped text a part per shape of its time; a syslog body of
key=value and text messages likewise, as `message_format`; a key=value part's own match is pairs from start to end, so
a line it can't read whole goes to the next part or `catch_all`); and `continuation` (with
`record_start`: a record's first line read by the spec, the lines after it kept whole, trimmed as Stroom trims, as
`continuation`; inferred where lines continue entries). A record a part's match fits but the part can't read is an
error, as Stroom reports it, not given to the next part or kept by `catch_all`. The converter (`SplitterXml.converter`) gives each part an `id` Stroom's errors name and passes over blank
lines, which Stroom otherwise writes as records of empty fields.

The converter is also run by Stroom's own Data Splitter parser on each sample text (`StroomSplitterRun`), as
`stroom_check`, because the dry run once parsed lines Stroom lost (over its read buffer, a key=value value holding
a space) and kept white space Stroom trims.

* **Arguments**: `sample` (text) or `stream_ids`; `spec` (object); `save_as` and `build` (defaults to the session's
  build); `buffer_size`.
* **Returns**: `spec`, `inferred`, `converter`, `converter_type` (`DATA_SPLITTER`), `format`, `records`, `fields`
  (inventory), `first_records` (5), `unmatched_lines` (10), `unmatched_count`, `wider_than_header` (records with
  more cells than the header names, kept as `(no name)` as Stroom writes them), `spans_lines` (records of the
  sample streams whose quoted value holds a line break, with a quote but not `quoted_newlines`), `buffer_size` when
  set,
  `stroom_check` (`matches`, `records`, `errors` with `error_hints`, `record_counts`, up to 5 `differences` of
  `dry_run` against `stroom` values, `loses_records`, `summary`), `line_endings`, `hint`, and `saved` or
  `not_saved`. For XML fragments: the wrapper converter (`XML_FRAGMENT`, the environment's own where it has one),
  `xslt_input`, and the namespace the mapping reads the fragments in.
* **Refuses**: JSON or single-document XML ("needs no Data Splitter", naming the parser and build_translation_xslt's
  input); a format it can't infer (asks for a regex spec, with an example written for the sample's lines); a `spec`
  that isn't one (with an example for the format); `buffer_size` outside 20,000 to 1,000,000,000; no sample and no
  spec.
* **Notes**:
  * `save_as` doesn't save while a record of the sample streams spans lines in a quoted value without
    `quoted_newlines` (each would be split in two: seen with Netskope, 40 records of 70,000 found only once the
    streams were processed; they are measured even when `buffer_size` is given),
    while any record has more cells than the header (its values shift into the wrong
    fields; the hint says to add the quote when cells start with one: seen in both runs of a 185-column CSV, a spec
    without a quote reported "Every line parsed"), while any line matches nothing, while the sample's lines end in
    a carriage return alone
    (Stroom's Data Splitter takes carriage returns out, so the stream is one line and writes no records, with no
    error; fix at the source or with a FindReplaceFilter), or while `stroom_check` loses records (errors, or a
    record count different from the dry run's). Value differences alone are reported, not blocking.
  * **The read buffer**: Stroom reads a Data Splitter's input through a buffer of `bufferSize` characters (20,000 by
    default) refilled only once half is read, so only a record up to half of it is sure to fit; a longer one matches
    nothing and is lost. When the sample's longest record (its longest line, or a multi-line record whole) is more
    than half the default, `bufferSize` is written at four times it, rounded, at most 1,000,000,000
    (`SplitterXml.bufferSizeFor`); `buffer_size` sets it outright. Seen in testing: CSV lines of about 20,000
    characters lost in processing, and a 751,000-character line with a buffer of 1,000,000. Given `stream_ids`, the
    sample streams are also read through whole (`StreamReader.recordLengths`, as `measure_records` does), so a
    record further in than the text profiled counts: `longest_record`, with `longest_record_note` when a stream
    wasn't read through in the call's time (seen: records of over 105,000 characters lost further into a 159 MB
    stream, with a buffer sized from its start).
  * The `stroom_check` summary says how many characters of sample it covered: "parses exactly as Stroom does" was
    taken for the whole feed in testing, where longer records further in were lost. Only a dry run of fewer than
    2,000 records is compared record by record.
  * `error_hints` come from the bundled error triage rules, for errors classed as blocking (the buffer exhausted,
    unmatched content).
  * `stroom_check` checks the converter against the `data-splitter:3` schema first, as the DSParser element does
    (`DataSplitterSchema`): its schema errors come first among the errors, and with no schema held nothing is run.
  * `not_saved` says what to try next: for records wider than the header or lines that match nothing, a regex spec
    with a name per group, its last group taking the rest of the line (fields delimited, then free text); and that
    a converter written by hand (`save_text_converter`) gets the same checks.
  * The save goes through `TranslationDocs.createTextConverter`, with save_text_converter's checks.

### `measure_records`

processing · read-only · unlocked by View Data (the streams are read as the user) · Classes: `MeasureRecordsTool`,
`StreamReader`, `RecordLengths`, `SplitterXml`

Measures the longest record of whole raw streams, every part read through once on the server, holding only a chunk
at a time, decoded with the feed's encoding as a pipeline decodes it (`StreamReader.recordLengths`,
`RecordLengths`), and gives the Data Splitter `bufferSize` that holds it (`SplitterXml.bufferSizeFor`: four times
the longest, rounded). Asked for by the user: an agent whose converter lost records of over 105,000 characters in a
159 MB stream had no way to find the longest, and guessed from a sample.

A record is a line, or, with `quote` (delimited text whose quoted values hold new lines: the spec's
`quoted_newlines`), the lines a quoted value spans joined, counted as `SplitterDryRun.longestRecord` counts a
sample. A newline in a record whose converter splits records at every newline (`<split delimiter="\n">` with no
container) ends the record anyway, so the longest line is the measure there. A record over several lines of
another kind (a marked start line, a blank-line block) needs the converter's own pattern and isn't measured: its
longest line understates it.

* **Arguments**: `stream_ids` (required; 1 to 10), `quote` (one character).
* **Returns**: `streams` (each `stream_id`, `parts`, `lines`, `chars`, `longest_line` and, with `quote`,
  `longest_record` (each `chars`, `line`, and `part` when there are several), `records_over_lines`, `complete`),
  `longest`, `complete`, `buffer_size` (none when Stroom's default holds every record), `hint`, and `not_measured`
  when the call's time ran out first (reading stops at the first stream not read through).
* **Refuses**: 0 or more than 10 streams; a `quote` of more than one character; a stream Stroom can't read.
* **Notes**: a record over half of Stroom's largest buffer (1,000,000,000) can't be held by any Data Splitter: the
  hint says to tell the user.

### `save_text_converter`

content · write · checks View and Edit; the converter is created in the build folder (Create) or changed (Edit) ·
changes only its build · Classes: `SaveTextConverterTool`, `TranslationDocs`, `DataSplitterSchema`,
`StroomSplitterRun`

Creates a text converter in the build (in its Events folder, until create_pipeline moves it), or with `uuid`
replaces the code of one the tools created. For templates whose parser needs one (`DSParser.textConverter`,
`xmlFragmentParser.textConverter`). Its description says to prefer `build_data_splitter` with a spec and
`save_as` for a Data Splitter, and that one written by hand gets the same checks. Seen in testing: for a
space-delimited Citrix feed the agent wrote one by hand naming its fields with `<entry>` elements under a `<regex
group="0">`; Stroom's parser ignores both, so the converter saved and its records had no fields.

A Data Splitter is checked as Stroom's DSParser checks it before running it (`DataSplitterSchema`, as
`DS3ParserFactory.configure` does through its schema filter): the `data-splitter:3` namespace and an
`xsi:schemaLocation` for it, and valid against the schema Stroom holds for that namespace, found as the pipeline
finds it (by namespace, as the processing user, not among the schemas the user may view). Then it is run through
Stroom's parser (`StroomSplitterRun`) on the build's newest raw sample streams (at most 3; Raw Events, else Raw
Reference).

* **Arguments**: `code` (the only one required by the schema); `build` and `name` for a new converter, or `uuid` to
  replace one; `converter_type` (`DATA_SPLITTER` or `XML_FRAGMENT`; default `DATA_SPLITTER`); `version`.
* **Returns**: `type`, `uuid`, `name`, `version`, with the build's next step; for a Data Splitter, `sample_run`:
  the records Stroom's parser wrote from the sample streams, or why it wasn't run (no raw sample stream of the
  build's own, or one that can't be read).
* **Refuses** (`TranslationDocs.checkConverter` and `checkDataSplitter`): blank code; JSON ("code is JSON, not a
  converter", pointing to build_data_splitter with the spec and `save_as`; it had read "Content is not allowed in
  prolog"); an XML_FRAGMENT that isn't a
  wrapper (no `<!ENTITY fragment SYSTEM "fragment">` and `&fragment;`); a Data Splitter holding a JSON element ("a
  text converter cannot parse JSON": the template's JSONParser does, with no converter); one that isn't
  well-formed, or whose root isn't `<dataSplitter>`; one that adds `ignoreErrors="true"`, which hides the errors for
  text no expression matches so that text is lost without a word (one the replaced converter already had is kept);
  one with no namespace or no `xsi:schemaLocation` (saying the root element to start it with); one with schema
  errors, each with its line and element; any Data Splitter when Stroom holds no `data-splitter:3` schema (no Data
  Splitter can run there: the core XML schemas content pack must be installed); one with no `<data>` element (its
  records would have no fields, with no error); one Stroom's parser can't read (e.g. a regex that doesn't compile);
  one Stroom's parser reports errors for, or writes no records from, on the build's sample streams; a new converter
  without `build` and `name`; an update of a converter that isn't the tools' own, or that changed since `version`.

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

* **Context data**: `input: 'xml'` reads XML records (fields are XML paths relative to a record, root `/` and record
  `*/*` by default, `xml_namespace` the records' default namespace), as a context loader reads a stream's Context
  child; JSON context data is `input: 'json'` as for a JSON reference feed (`json_layout`, dotted fields, `record`
  for entries wrapped in an object). A map's `key_fields` (1 to 3) makes its key as a lookup's `key_fields` does
  (`LookupKeys`: each value normalised, joined by U+241F), and a value part's `copy: true` copies the nodes it
  selects (a table of items for `mask_names`) into its element. From JSON input they are copied in no namespace
  (`ReferenceGenerator.PLAIN_COPY`, each element keeping its `key`): Stroom's reference store refused the JSON
  namespace copied with them ("Duplicate namespace attribute entry" reading the value back, seen in e2e).
* **Arguments**: `mapping` (required; object or JSON text: input, root, record, maps with name, key (or key_xpath or
  key_fields) and value parts, imports, drop rules, xml_namespace); `schema_version` (default: the highest version of
  the reference-data schemas this
  Stroom holds, compared as versions (`stroom.util.shared.Version`) whatever order they are listed in, else the
  generator's default; found reviewing the tools: the last listed was taken); `build` (the imports standing
  instructions require of reference XSLTs there are added).
* **Returns**: the generator's result (`ok`, `xslt`, `problems`, `warnings`), `schema_version`, `required_imports`,
  and a `hint` with the steps after (save_xslt, create_pipeline from the reference template, a processor filter on
  the Raw Reference stream, wait_for_processing, then the events pipeline's `references`).
* **Refuses**: no mapping; one that isn't a reference mapping.

### `propose_context_lookup`

content · read-only until confirmed · requires View Data (the sample streams and their Context children are read as
the user) · confirms itself · chat group build · Classes: `ProposeContextLookupTool`, `ContextLookups`,
`ContextProposal`, `StreamReader.childText`

Only on the user's request (the survey never looks at context data): how a feed's records are enriched from the
context data each stream part carries. For each part of the `stream_ids` (up to 20 a stream) it reads the Context
child and the part's records (`RecordSplitter`; an XML record's fields its XML paths relative to it, a JSON record's
its dotted paths, an array's items its values). XML context: each entry an element at `entries` below the root, by
default the root's most common child; an entry's fields are the paths that come up once in it, and a list element's
items (children of one name, each with fields of its own) are its tables (`entry_tables`, for `mask_names`). JSON
context (`ContextJson`, told by its first character): an array of objects, JSON lines, one object wrapping its
entries (the array at `entries`, a dotted key path, by default its array holding the most objects, three levels deep
at most), or one object holding no such array (or `entries` `.`) as the one entry; every part's must be laid out
alike, as one loader reads them. An entry's fields are its scalars by dotted path (numbers as written, nulls and
arrays of scalars left out); its tables are its arrays of objects and its objects keyed by mask (every key a non-zero
mask, every value a scalar), whose items are `{"@key": mask, ".": name}`, so a mask_names output of one needs no
`mask_field` or `name_field`. The key (`ContextLookups.keys`) is found by value sets: an entry field
every entry has is paired with the record field most of whose values are among its values (then the one covering
most of its values, then the same name); combinations of up to 3 paired fields that tell every part's entries apart
are counted against that part's records, smallest first, stopping at a size where one matches every record. Each
part's records are matched against that part's own context, as a Context lookup reads them. `key_fields` with
`event_key_fields` counts a key given instead.

With `outputs` (each `{path, data_name?, from, render: text | fill_template | mask_names, pattern?, values?, mask?,
mask_field?, name_field?, repeat?, separator?}`) it previews each for two sample records (from as many parts as there
are) with the Java stand-ins of the generated functions (`FillTemplate.fill`, `MaskNames.named`), as a table for the
chat (`needs_showing`); a template's pattern left out is the commonest of `%(\d+)`, `\{(\d+)\}` and `\$(\d+)` in
its entries. Called again with `confirm=true`, the user agrees in a short plain-text form (the key, its match rate,
the outputs' count), and the reply gives `reference_mapping` (for build_reference_xslt: input xml, or json with its
`json_layout` (`array`, else `lines`) and, for wrapped entries, `record` where Stroom's JSON reader puts them with its
root map or without; the map's `key_fields`, a value element per output, a mask table copied whole: an XML list's
items, a JSON table's items `*[@key='t']/*`), `mapping_entries` (for build_translation_xslt: lookups with the
matching `key_fields`, `fill_template`, `mask_names` with a lookup of the copied table, a JSON table's at `t/*` with
its item paths `/`-separated) and `reference` (`{stream_type: 'Context', loader_pipeline}`), with the steps after:
the hint names the loader's `combinedParser.type`, `XML` or `JSON` as the context is (a CombinedParser's JSON reader
always adds its root map; no other property is needed).

* **Arguments**: `stream_ids` (required), `entries`, `map` (default `CONTEXT_ENTRY`), `key_fields` and
  `event_key_fields` (together), `outputs`, `confirm`.
* **Returns**: `parts`, `entries`, `entry_fields` (each with a sample value), `entry_tables` (each with a sample
  item), `keys` (up to 3, best first: `key_fields`, `event_key_fields`, `matched`, `records`, `match_rate`); with
  outputs `previews` (`stream`, `part`, `record`, `key`, and per output `before`, `after`, `reported`), `status`
  `needs_showing` and `table`; confirmed, `reference_mapping`, `mapping_entries`, `reference`.
* **Refuses**: no stream ids; no part with a Context child; context data that is neither one XML document nor JSON,
  or has no entries at the path (JSON: no objects, `entries` for an array or JSON lines); parts whose JSON context is
  laid out differently; `key_fields` without `event_key_fields` (or of different lengths, or over 3); an output without
  path, from or a known render, a fill_template without values, a mask_names without mask, mask_field and name_field
  (but for a JSON table keyed by mask);
  the user declining the form.

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
  there, so it always agrees with the XSLT. A translation's section is stepped over the build survey's examples
  beyond those records too (`SteppedSamples`: at most 60, by their survey locations), so each kind the survey
  found has values of its own (asked for by the user: a 10,000-record stream's rarer kinds documented "(not in the
  sample)" from its first 200). What is stepped is kept for the session (`SteppedSamples`, four hours, by the
  versions of the pipeline and every document it runs, the code stepped in an element's place, the streams and the
  survey's records; never a run the call's time cut short), so the rounds of field descriptions don't step the same
  records again. An empty Sample values cell says why: "(no sample record reaches this rule)" (or "these rules"),
  or "(no value in the N sampled events)" (`EventFieldDocs.orWhyNone`). A catch-all rule (`catch_all`, or one
  without conditions kept as Unknown) that no sample record reaches, writing only Data below `EventDetail/Unknown`,
  is summarised in a line (it writes Unknown, holding each of its N fields as Data) with no table, and fields only
  it writes are not asked to be described (2026-11-22, chat 182: a 74-row table of "(no sample record reaches this
  rule)", and three of five rounds of descriptions spent on them); one the sample reaches keeps its table, as does
  every rule without a sample. A kept-Unknown reason ends its sentence before "No sample record reaches it". For a translation the pipeline is stepped with the mapping's XSLT
  generated with rule markers (processing instructions, `XsltGenerator.RULE_INSTRUCTION`, which survive stepping
  where comments don't), so each Event is matched to the rule that wrote it. The section opens with "Counts are of
  N sample records (n of stream ...)" so a reader knows which records were counted (seen: counts from a different
  sample taken for a change in routing); a source type group's adds ", M of them translated by this group (the rest
  are of source types it passes over)" when some aren't (2026-11-22, chat 182: 206 records, 84 events and the agent's
  1,207 in one doc). The documentation guide asks the agent not to count the sample in Purpose and data. An index XSLT written by hand is documented from the documents it writes
  (`writtenFieldsSection`), a CEF one from the lines it writes (`cefWrittenSection`), to Kafka or as text to a file
  alike (`CefLines.linesIn` reads Kafka records' values, a text XSLT's lines, or the text of XML a TextWriter
  writes). A hand-written discovery
  pipeline (stage `discovery`, no plan) is documented the same way from the documents it writes from its raw
  streams, with none of a planned discovery index's framing (its settings aren't known without the plan). An
  aggregated feed's group pipeline, whose one XSLT is its source type group's (keeping its mapping), is documented
  as a translation (`FieldMappingSections.groupLine`, `withCommonParts`): the section opens saying the XSLT is group
  'x''s, translating its source types and passing over the feed's others, and names the feed's registry for what
  becomes of every source type; the common module's parts it calls are shown as common fields. An indexing
  pipeline's section (planned, or discovery) ends with what reprocessing does, from its own indexing filter
  (`IndexReprocessing`): Elasticsearch writes each document as a bulk `create` with no id (Elasticsearch assigns
  it), and a reprocess processor filter first deletes the stream's documents by StreamId when `purgeOnReprocess` is
  true (the default; said otherwise when the pipeline sets it false), a stream processed again outside one being
  indexed twice; Lucene only adds documents, so a stream indexed again is there twice until its shards are deleted.
  Seen in chat 177: the agent wrote "The document _id is <StreamId>-<EventId>, so re-indexing the same Events
  stream replaces its documents".
* **Reference maps** is generated for a reference data pipeline (`PipelineShapes` stage `reference`; its XSLT, made
  by build_reference_xslt or by hand, keeps no mapping) in place of the agent's own section of that name
  (`FieldMappingSections.referenceMapsSection`): the pipeline is stepped over `stream_ids` (its Raw Reference
  streams), and the section opens with "Counts are of N sample records (n of stream ...)", then a row for each map:
  its key and value as its XSLT writes them, how many entries the sample gave, and an example key and value; then
  the feed(s) its processor filters read and the pipelines that look the maps up (a pipeline reference naming one
  of those feeds, or the pipeline as loader). The facts are evaluate_pipeline's `reference` section's
  (`ReferenceEvaluation`), shared, not duplicated. Nothing is kept with it, so no digest closes it.
* **Errors** is generated from the pipeline's Error streams: every kind of error with counts and an example (the
  same class, element, severity and message pattern merged across the streams, counts added, the first example
  kept: `BuildDocumentation.acrossStreams`, 2026-11-22, chat 182 listed each kind once per stream), and
  the errors the user accepted as benign with their reasons (`AcceptedErrors`), kept with the doc so triage doesn't
  raise them again. The agent's own sections on errors stay as written.

Stroom renders a Documentation doc's Markdown with showdown, unsanitised (a sandboxed frame: no scripts, but HTML,
images and links render), so no markup from the data or the agent reaches it (`MarkdownHtml`). In the generated
sections each value from the data (sample values, examples, kind and rule names, source type groups, conditions'
values) is inline code (`FieldDocs.sampleValue`, its backticks shown as quotes) or escaped (`&lt;` `&gt;` `&amp;`).
And the whole doc as written (the agent's markdown with the generated sections and the change line) has every tag
outside code spans and fenced code blocks escaped (`MarkdownHtml.escapeTags`): `<` starting a tag, comment,
processing instruction or CDATA section becomes `&lt;`. The tools' own `<br>` line breaks, their hidden
`<!-- stroom-mcp … -->` blocks (to the first `-->`, as a browser ends a comment) and autolinks are kept. Seen in
the Azure audit sample: mail bodies' HTML, an XSLT fragment and a CDATA section in a subject.

* **Arguments**: `build` (required); `pipeline_uuid` or `index_uuid` (both left out: the session's one
  documentation waiting for descriptions, as if named, with a `continued` note saying so; 2026-11-23, chat 187: a
  second round without `pipeline_uuid` was refused); `markdown`; `change`; `stream_ids`;
  `accept_errors` (a list of `{element, example, reason, matches}`, one object, or JSON text); `field_descriptions`
  (`{field: description}`, or JSON text); `field_mapping_layout` (`by_event_type` or `compact`; else an AGENTS
  doc's Documentation section, else by event type); `index_itself`; `document_missing_fields`; `evaluation`.
* **Returns**: `type`, `uuid`, `name`, `written` (`created` or `updated`: seen, `updated: false` on a new doc sent
  the agent to check it was written), `link` and an open-document action, `field_mapping` (the generated section;
  `reference_maps` instead for a reference data pipeline),
  `note`, the build's `next`; for a promoted pipeline `working_copy_of` and a `next` about promote_build. Or
  `needs_descriptions` (nothing written yet): the fields not yet described, 25 a round, fewer when their entries are
  long: no more than come to 8,000 characters as Stroom AI's chat lists them (`BuildDocumentation.
  DESCRIPTIONS_ROUND_CHARS`, the room its stub gives a round's fields in full; always at least one; 2026-11-22, seen
  in chats 184 to 186: 25 fields of 7,000 to 8,000 characters cut at 4,000, and the rest read back 30 times), a
  field's `from` past 1,000 characters cut to its first lines, saying how many more (a value each rule sets lists
  every rule: eval case 68's TypeId and Description, 300 lines each) (the rest follow once those are given; its hint asks for no code meaning the values or source notes don't show, and whose an Id is only where
  its name or the notes say, 2026-11-22), each with where it comes from, sample values, `schemas_say` (the vendor notes and the schemas), `under`
  for a Data name, `was` for one whose source changed.
* **Large, outside Stroom** (`DocumentationOutline`, 2026-11-20; cases 35, 36, 56: 73k–88k): `field_mapping` (or
  `reference_maps`) given by size and headings, written to the doc as generated, saved whole a line an entry to
  `/outputs/write_documentation/<build>.json`.
* **Refuses**: no `build`; empty markdown; neither `pipeline_uuid` nor `index_uuid` with none or several
  documentations waiting for descriptions (several: each named, label and uuid); an unknown layout; an update
  without `change` ("give change, one line for its version control"); accepting `EventDetail/Unknown` events as a
  benign error (Unknown is agreed per rule in build_translation_xslt); `accept_errors` with `index_uuid`; an
  accepted error without `example` or `reason` (`accept_errors[0].example is required`); a kept mapping with no
  streams to step (`streamsNeeded` says which: sample raw streams, the Events streams indexed or sent); an events
  pipeline whose XSLT keeps no mapping without `stream_ids` (none kept from its last clean stepping); an index or CEF XSLT written by hand without `stream_ids` (for a discovery pipeline, asking for the
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
    what the mapping generates, duplicates and all; before that, TWITTER's links left out. Where most of its events
    carry no rule's marker, it is documented by event type instead (`WrittenEventDocs`), as one written by hand is.
    A section the agent wrote is replaced.
  * **A translation XSLT written by hand** (no mapping kept) is documented from the Events it writes over the
    sample (`FieldMappingSections.writtenEventsSection`, `WrittenEventDocs`): a Common fields table (the EventTime
    and EventSource fields every event type has), then a section per event type (its TypeId, else its action
    element; types grouped on their EventDetail as rules are), each field with its sample values and a description
    asked for as usual; no From column. A lost mapping is said in the notes. Seen in testing: the agent's own section
    for one had the common fields and no event type's own.
  * **Which streams**: `stream_ids` left out are the streams the pipeline's current code last stepped clean on, kept
    in the build's record (prompts and agents left them out, and every call was refused once first). An events
    pipeline's Events streams given in place of its raw sample stand for the raw streams they were made from
    (`BuildDocumentation.rawParents`).
  * **How the Field mapping is laid out**: event types grouped on what they write besides their Data, an object's
    side ignored, Data every type writes alike as common fields, extractions left to the XSLT; an index's common
    fields only those every event has, each section naming its TypeIds; an event type's section says how many
    elements below the action element its rule types and keeps as Data (`Below the action element: 5 typed, 3 kept
    as Data.`, 2026-11-21). A constant TypeId's From says it is the event type and what recognises it, from its
    rule's conditions (`event type Send-Item, recognised by `Operation` = Send`; a type's several rules joined with
    "or"; the catch-all `for the records no event type recognises`; a rule with no conditions `event type X`); a row
    of several event types names each line's rules (2026-11-22, chat 184: the From was the bare `"Send-Item"`;
    `EventFieldDocs.typeFrom`). See `EventFieldDocs` and `IndexFieldDocs`
    under "Field-mapping documentation and the CEF mapping".
  * **Field descriptions**: each field is described by what it means for this source (`Descriptions`): a
    `needs_descriptions` round asks for those not yet described. A Data name read from the same source under
    several elements is asked once (seen: 224 asked for one feed, its 18 Data names once per action element); a
    full path still overrides it. Descriptions are kept with the doc with where each field came from, and one is
    asked for again when its source changes (seen: a description outlived the values it described).
  * The build's source notes add a "Source fields" table (`sourceFieldsMarkdown`).
  * `accept_errors` are recorded only after the user confirms in a form. One the doc records already (the same
    element and kind of message) is not put to the user again, and is said in `accept_errors_already_recorded` (seen
    in eval case 61: the same error given again in a later call, and the user asked to accept it twice).
  * **Version control**: the title is the doc's name (`DocumentationText.titled`), sections written at `# ` are put
    at `## ` (seen with Haiku: a survey section above the agent's read as empty); an update replaces the body and
    keeps the version-control rows and accepted errors; the change waits, with the build's others, for one row at
    promotion (`DocumentationVersions`). A row's `by` is `<user> (agent: Stroom <version>, <caller>)`.
  * **A promoted pipeline's** documentation is changed through a working copy in the build, which promote_build
    writes back after a backup, not a second doc of its name.
  * `evaluation=true` keeps the markdown as its own doc, `<pipeline> - Evaluation`, beside the pipeline once
    promoted (an earlier one updated through a working copy), nothing generated, the pipeline's documentation left
    as it is: saved as the documentation it would have replaced it at promotion.
  * **An existing index** (`IndexDocumentation`): the user confirms the index doc in a form, once a session for
    the same build, index doc, folder and doc beside it (`DocumentAgreements`; seen in chat 177, asked four times
    for one index doc, each call asking afresh), another target asking again; the section comes
    from a survey of the index through Stroom; an index this build made is documented through its indexing pipeline
    unless `index_itself`; fields its feeding pipelines write that the index lacks refuse the call unless
    `document_missing_fields`. `stream_ids` given are ignored (and said to be).
  * Stepping is within the session's time limit for a long call, and audited; cut short, the section is of the
    records stepped by then.

Since 2026-11-24 (chat 192): an events pipeline in a build that stepped clean before but whose current code (its
latest saved XSLT) hasn't is refused: `Not written: the pipeline's XSLT was saved after its last clean step ...
step_sample ... first` (`BuildDocumentation.notSteppedClean`). One never stepped clean is not: documenting it is how
the user accepts errors its steps leave (`accept_errors`), and a copy compared rather than stepped is documented
before its first step; promotion still waits for a clean step.

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

content · write in a build only (an aggregated feed's registry; read-only outside a build) · requires View Data;
unlocked by Stepping (records are stepped); checks View (only streams of feeds the user may view) and Edit (the
build's registry) · takes a run slot · Classes: `ReviewCoverageTool`, `CoverageReads`, `ProcessorFilters`,
`IndexTemplateAgreements`, `CefDraft`, `PipelineStepping`, `EventStreams`, `SourceTypeCoverage`, `SourceTypeScope`,
`CaughtShapes`

Whole-feed coverage of an events pipeline once its feed is processed: the kinds of event the sample missed. The
generated translation logs, into the Error stream of the raw stream a record came from, each record no rule
matched (`No event mapping matched record N (...)`, WARN, `XsltGenerator.UNMATCHED`) and, once a stream for each
kind (the rule and the values the rules test; `mcp_log_kind`), the first record a rule keeps as Unknown (`Kept as
Unknown by rule 'r': record N (...)`, INFO, benign in triage), with the values its rules test (up to 4,
`LOGGED_FIELDS`); and so too, once a stream for each kind, the first record the catch-all takes (the rule flagged
`catch_all`, never inferred: `Caught by rule 'r': record N (...)`, the same template, `mcp_log_kind`). A kept-Unknown or
caught kind's streams are counted (`streams`), not its records, and told apart in `tell_user`. So the kinds are
grouped from the Error streams alone, however large the feed, with a few raw records read only for examples
(`CoverageReads.example`).

**Uncatalogued records become proposals** (DESIGN.md, Event type catalogue, After the first build): the records the
catch-all caught are grouped by shape (`CaughtShapes`: `Shapes`' signatures over the values each line logged, the
fields the rules test) into `uncatalogued.shapes` (each `signature`, `logged_by`, `values`, `raw_streams`,
`examples`), each likely an event type the sample didn't have. `next` names `catalogue_event_types` (`step:
catalogue`) with the build and these raw streams as `stream_ids`: the agent proposes an event type per shape
(`op: add`), checked against the sample and the records caught, the user confirms them, and draft_translation_mapping
and build_translation_xslt apply them in a new version (a production pipeline is first copied into a build with
copy_pipeline).

**Aggregated feeds** (`SourceTypeCoverage`): where the feed has a source type field (the build's survey, its
registry, the pipeline's kept mapping, or the lines it logged), each kind's first example record (up to 60 kinds) is
read and its source type told by the field and the registry's rows, the first match winning (`source_type` on the
kind). What it is judged against:

* **A source type group's pipeline** (its XSLT's kept mapping a group's): the group's source types
  (`source_types.group`, `group_source_types`), whatever the build's scope. A kind of a source type another group
  translates (the registry's `translated` rows of another group) is left to that group's pipeline, never missed:
  `other_groups` (each kind with `translated_by`, `group 'y', pipeline '...'`), and `source_types.other_groups` by
  source type. A kind of any other source type outside the group is passed over (`passed_over`). The records the
  group's pipeline passed over in processing are told from the raw streams it processed in the call's range of Ids
  (the parents of its Events streams with an Id after `continue_from`, up to the last Error stream read, so each
  falls in one call; `CoverageReads.processedRaws`, at most `SourceTypeCoverage.MAX_PROCESSED_RAWS`, 50, a call):
  each part of each raw stream read whole (to `SourceTypeCoverage.MAX_COUNTED_CHARS`, 64,000,000 characters, far
  past a sample's: a sample's cut counted 717 of 818 in e2e), split into its records, and those of a source type
  outside the group (by the
  registry's rows, the first match winning; a part's meta for a `meta:` field) counted:
  `source_types.processing.passed_over` (`records`, `raw_streams` holding any, `by_source_type`,
  `raw_streams_read`, and a `note` where parts were cut short, unread or not readable record by record), said in
  `tell_user`. The group's XSLT logs nothing of them; an older group XSLT's INFO line of them
  (`CoverageReads.SourceTypeLines`) is taken out and not counted.
* **A whole translation**: the build's scope, where it has one (kinds outside it `passed_over`; `tell_user` starts
  with the scope and its share of the records the survey read); kinds of a source type a group translates are left to
  that group's pipeline as above. Its INFO lines of values its survey didn't see (`New source type 'X' in <field>,
  not seen when this feed was surveyed: ...`, logged once a stream a value by an XSLT whose mapping keeps
  `seen_source_types`) are reported, up to 20 raw streams a value: `source_types.unseen_source_types` (by value:
  `raw_streams`), and `tell_user` asks the agent to suggest surveying the feed again. Nothing is written for them.

Only the kinds missed within what the pipeline translates make `missed`, the affected streams and `next`. A value no
row matches, met in a kind, is a `new` source type where the feed has a registry: added to the build's registry as a
`new` row (decided by `review_coverage`; through a working copy when the registry is beside the feed, written back at
promotion) for the user to place with record_source_types; `source_types.new_source_types` and `registry` say so. A
group's pipeline never adds one from processing: values outside the group are counted with the rest passed over.
Outside a build nothing is written (only a build changes the registry): they are said. With no survey state and no
kind missed, the feed is told from the raw streams the lines were logged for.

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
  `stopped_early`); `uncatalogued` (the catch-all's records by shape, above); `missed` (groups, most records first:
  `found_as`, `kind` (the logged values), `records`,
  `raw_streams`, `examples` with `raw_stream`, `record` and `holds`); `affected_raw_streams` (`count`, `first_id`,
  `last_id`, `from`, `to`) and `affected_raw_stream_ids` (at most 200); `tell_user`; `next` (show the kinds, add
  rules with build_translation_xslt `changes=`, write_documentation with affected streams, how to reprocess; when
  every kind found was kept as Unknown, only to show them: kinds the user agreed to keep as Unknown need no rule);
  `counts_capped` (`raw_streams`, `count`, `note`) when an Error stream held as many log lines of a severity as
  Stroom writes (`CoverageReads.MAX_WRITTEN_MARKERS`, 1,000, from `RecordErrorReceiver`), so its counts are short;
  `follow_on` and `follow_on_events`; on an aggregated feed `source_types` (`field`; for a group's pipeline `group`
  and `group_source_types`; `scope` with its share; `passed_over`, the kinds' records by source type;
  `other_groups`, source type to the group translating it; `processing.passed_over`, a group's `records` and
  `raw_streams` passed over; `unseen_source_types`, a whole translation's values its survey didn't see;
  `new_source_types` and `registry`), `passed_over` (the kinds outside what the pipeline translates) and
  `other_groups` (another group's kinds); for a group's pipeline whose feed's groups import different versions of
  its common module or helpers, `module_versions`, one informational line naming the groups and versions
  (`SourceTypeGroups.mixedModuleVersionsOf`, 2026-11-17). Examples (`holds`) are read with the pipeline's own Data
  Splitter (`KeptMappings.dataSplitter`), as processing read them, else a spec inferred from the stream.
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

Since 2026-11-24 (chat 192): each follow-on pipeline whose own XSLTs import a module that no longer exists has
`imports_broken`, to tell the user.

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
`draft_index_mapping` with that `convention` or `like_index` neither asks nor confirms again; the backend and naming
chosen in these forms are also recorded with the session's build (`indexing_choices` in the build record), so a call
with neither, after a compacted context or a restart, returns them as settled (`status: chosen`) instead of asking
again. Following an existing index with several to choose from asks which in a second form.

On an aggregated feed (a build holding its source type registry) the backend and naming are the feed's **shared
decisions** (`FeedIndexDecisions`): what the first scoped build's forms choose is also written to the registry's
machine block (`indexing`: `backend`, `naming`, `decided`); a later build with nothing recorded of its own inherits
them (copied into its build record, `shared_from` naming the feed) and gets `status: chosen` with `settled` saying
where they are kept, nothing asked. A `backend` other than the shared one is refused, naming the registry, unless
`change_shared=true` (the user asked): then they confirm it in a form, it replaces the registry's, and the naming is
asked again. Once one of the feed's indexes is recorded, a build whose group has none yet also gets `index_name`: each
group has an index of its own, named like the feed's first with the group in it (`FeedIndexes.groupIndexName`: the
other group's name in it replaced, else the group's added before the version, `stroom-pega-security-v1` to
`stroom-pega-mail-v1`).

* **Arguments**: `name` (profile: `ecs` or `stroom-flat` as bundled); `backend` (`lucene` or `elasticsearch`);
  `change_shared` (only when the user asks to change an aggregated feed's shared backend);
  `ecs_fields` (with `name=ecs`: a field set or prefix, e.g. `process`, `user.target`, listing its fields with type
  and description, up to 200).
* **Returns**: `status: not_yet` with `next` and `hint`; or `needs_guidance` with `options` (Elasticsearch) or
  `profiles` (descriptions), `indexing_templates` where known, and `hint`; or `status: chosen` with `choice`,
  `backend`, `how` or `convention`, `like_index` (when one index was picked), `settled` (what the user decided in
  forms and must not be asked again, and what is still open, e.g. the cluster), `index_name` (an aggregated feed's
  later group: the name suggested for its own index) and `hint`. With `name`: `name`,
  `profile` (its YAML as JSON: description, structure, field_map, reference_index_docs), `reference_fields` (each
  reference index doc's fields and Stroom types, up to 500 each; one Stroom can't list says why), and for `ecs` the
  schema's `version` and `field_sets`, plus `ecs_fields` when asked.
* **Refuses**: a `backend` other than the two (`backend is 'lucene' or 'elasticsearch', not '...'`); on an
  aggregated feed, a `backend` other than the shared one without `change_shared=true` (`Feed '<feed>' indexes into
  <backend>: a decision its scoped builds share ...`); an unknown profile (`No convention profile 'x'. Profiles:
  ...`).
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
comes from whichever action the event records). An entry with a `fallback` path reads it where the event has none
at its own (`FieldPlans.orElse`: `A | B[not(../../A)]`, one value an event): ECS's `event.action` is an action's
`Action`, else the event's `TypeId` (asked for by the user: View, Send, Create and the like have no Action, and seen
in chat 178 event.action was written by no event, never mapped, and a search on it refused). The event's action element (Alert, Authenticate, Process, Update;
not Network, not Data) adds up to 20 fields of its own by default, as users asked for them every time; under ECS
these go under the first `custom_namespaces` entry (default: one named after the index), not a top-level group per
action (seen: `authenticate.*`, `copy.*` drafted and passed). Under ECS a Data element whose name is an ECS field's
written another way (`source_ip`, `SourceIp` as `source.ip`) is planned under that name (`ecs_data_elements`);
abbreviations are not guessed. With an example, fields take the example's names for the same data and the rest are
named in its style (`ExampleNames.namesFromExample`), its components composed as Elasticsearch composes them;
`subobjects: false` in the example is kept in the plan. Without an example, `extra_fields` go in before the tool's
own fields: one reading a path alone takes it (no field of the tool's for it; a union of paths takes none), and one
named as a field already planned replaces it (`draft_notes` says so); with an example they are added after it, also
replacing by name. For Elasticsearch `@timestamp` from `EventTime/TimeCreated` if not planned. Where a name is a value
and also the object holding another, the tool's own field is renamed (`name.value`, or joined at the clash:
`user.id` under `user` becomes `user_id`) and the draft kept, said in `draft_notes`; a clash between fields given or
the convention's is refused, naming where each came from. A tool field whose path ends as one a planned field reads
(`.../User/Id`) is noted for the user to decide: a second source, its own field, or both. A union source is several
values, each written once. The plan is marked with the fields whose source can hold
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

A constant field holds the same value in every document, only on the user's request (never by default):
`{"name": ..., "type": "constant", "value": ...}` with no source, in `extra_fields` or `changes.fields`
(`PlannedField.constant`). Elasticsearch: the template maps it `constant_keyword` with its `value` (`PlanTemplates`,
never left to ecs@mappings), and the indexing XSLT doesn't write it (`ElasticDocument`). Lucene: a keyword field
(TEXT, KEYWORD analyzer) the indexing XSLT writes with the value in every record (`IndexingXslt`, the value escaped
and its braces doubled). verify_index counts every indexed event as holding it (`PlannedValues`), so an exact check
on it expects every document; the documentation's field tables show `constant `value` (every document)`. A
template built from the user's example keeps the example's `constant_keyword` fields that have a value, though the
pipeline doesn't write them (Elasticsearch fills them), and says so in its notes; a planned constant keeps its own
value over the example's mapping of the name (`FromExample`).

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
  whose kept plan to amend); `change_shared` (only when the user asks: an aggregated feed's group in another index
  than its own (a new version) or backend than the shared one, below); `share_index_with` (only when the user asks
  for one index with another group of the aggregated feed: that group's name or its index's). `plan`, `discovery`, `extra_fields` and `shared` may be given as JSON
  text.
* **Returns**: `status: needs_showing` and `table` first (`IndexPlans.withShownPlan`): the plan's fields as a
  Markdown table (`#`, field, type, source path; an object field's parts a row each), which Stroom AI's chat shows
  the user itself after the call (the model's copy says it was shown, `ToolOutputs.forModel`) and another client's
  agent is told to show as it is, before asking about it or creating the index (seen in testing: the user was asked
  to approve an index of fields never shown to them). Then `plan`, `problems` (`FieldPlan.required()`), `rendered`
  (Lucene field list, or Elasticsearch index template), `convention_paths_not_in_sample`, `populated_paths_not_mapped`, `fields_not_in_sample` (no event read
  has the source, and the translation that wrote the Events doesn't write it: the user chooses to leave it out, have
  the translation write it, or keep it), `fields_rare` (no event read has the source, but the translation writes it:
  kept, as a sample can't show a path is never populated; seen: a byte count early in its stream said to be one the
  index never gets), `field_mapping` (the Field mapping section's Markdown), `core_fields_mostly_empty` (core
  fields of the convention, ECS `level: core`, and the time field, that fewer than half the events read populate:
  `name <- path: in n% of events read`, or `in no event read`, for the user; `IndexPlans.mostlyEmptyCore`; seen in
  chat 177: event.action empty in 2,233 of 2,457 documents; in chat 178, in every one), `ecs_check` where it departs from ECS, `from_example`
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
  plan isn't valid: ...`); argument objects that don't validate (`plan isn't a valid field plan: ...`); a plan,
  drafted or amended, in which a name is a value and also the object holding others (`'a.b' is a value and also the
  object holding ['a.b.c'] ... Nothing was kept`), for nested Elasticsearch names: the plan kept before stays (seen:
  kept, it failed to render on every later change, an empty one too).
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

* **Aggregated feeds** (`FeedIndexDecisions.forPlan`, `FeedIndexes`): for Events of an output feed of the build's
  registry (the feed's own, its test feed standing in for it, or a group's own output feed), the plan is for the
  index of the source type group whose pipeline wrote them (the stream's meta `Pipeline`, matched to the registry
  rows' `pipeline.uuid`; else the build's scope group). Each group has an index of its own by default (decided
  2026-11-14), even where several groups' Events go into one feed: its indexing pipeline's processor filter selects
  only its group's pipeline's Events (`create_processor_filter source_pipeline_uuid=`), so nobody is asked whether
  groups share one. An `index_name` another group's index has (recorded, or chosen in the build) is refused, naming
  a name of the group's own (`FeedIndexes.groupIndexName`) and `share_index_with`; once the group's index is
  recorded, another name is refused unless `change_shared=true` (the user asked: a new version, confirmed in a form).
  Only with `share_index_with=<group or index>` (the user asked for one index with another group: it must be
  recorded, else refused listing the feed's indexes) is the plan that index's: the paths each of its groups' mappings
  write (kept with their XSLTs) are planned whether or not the sample has them (`group_paths`), and the fields the
  index has (recorded at earlier promotions) are added where the plan lacks them, the union of the groups' fields.
  Without it, only the group's own mapping's paths, and its own index's recorded fields (a later build of the group).
  A field the index has with another type, or of the same type read from another path or described
  otherwise (a field's meaning: its description where both have one, else what it reads), is a clash: all of them
  put to the user in one form of short plain text, each with its two answers (another type: rename this build's
  field to `<name>_<group>`, or a new index version `<index>-v2` with the index's fields; another meaning: one
  field read from both paths, or renamed); past what a form holds (20 fields), the first 19 one by one and one more
  question for the others, all the same way (rename each; same meaning where only the meaning differs, the others
  renamed; or a new index version), the message naming the first five clashes and how many more (all in
  `index_clashes`). The reply's `feed_index` says the `output_feed`, the `group`, the index's
  `groups`, `shared_with` and `moves_from` (shared at the user's word), the `index`, `inherited_fields`,
  `clashes_resolved`, `new_version_of` (with `new_version_hint` when the user chose a new version in the form) and
  where it is `kept`; without forms (or where the form couldn't be shown) the clashes come back in it as
  `index_clashes`, with `clash_hint` on how to apply them by `changes=` (a rename: remove the field and add it under
  the new name; one field read from both: the field with both sources), and a new version by drafting again with the
  next version's `index_name` and `change_shared=true`, which the user confirms in a form. Amending (`changes=`) is checked the same way. The
  `backend` is the feed's shared one (refused otherwise, as `get_field_conventions` refuses it).

### `create_index_doc`

Indexing · write · checks View and Edit; the user needs Create in the build folder; an Elastic Index doc's cluster
must be one the user may view · confirms itself · Classes: `CreateIndexDocTool`, `IndexFields.addLuceneFields`,
`BuildIndexes`, `ElasticClusterTests`, `IndexPlans`

Makes the build's index doc in its folder (role Indexing): a Lucene Index in a volume group (partitioned by month,
one shard per partition) with the plan's fields, or an Elastic Index doc on an existing Elastic Cluster doc naming
the index or data stream. The cluster's connection is tested before anything is made: one whose test fails is
refused with the test's message and the clusters whose test passes, for the agent to ask the user (seen in chat 177:
made on ES_PROD, its test then failed with "missing authentication credentials"). Named again with another
`cluster_uuid`, the build's own Elastic Index doc moves to that cluster once the user confirms ("Change the cluster
of Elastic Index doc ..."), with the build's pipelines writing to it that name a cluster (`BuildIndexes.setCluster`);
the reply has `changed` and `pipelines_changed` (seen in chat 177: refused as a name the build has, and the user
edited the doc by hand). The user confirms the backend, names and target in a form, and
may correct the doc name and (Elasticsearch) the index name there; the reply carries the names as made (seen in Ask
Stroom AI: the reply kept the name from before the user's edit, and the agent offered it back to them). With a plan,
the form's `fields` line refers to the table `draft_index_mapping` showed ("12 field(s), as the index field plan
shown in the chat lists them"; `CreateIndexDocTool.planShown`), as a form is short plain text.

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
* **Refuses**: an aggregated feed's index an earlier build made (recorded in the registry with its document beside
  the feed, a group's own or shared): promotion adds a later build's fields to it, so no second doc is made;
  a bad `backend`; Elasticsearch with no index name (from `index_name` or the plan) or no cluster (none
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
such fields (its `<data name="...">`) is refused instead. Fields are added only to the build's own index: one
outside the build (a production index) lacking the plan's fields is refused before the pipeline is made
(`WriteGuard.checkInBuild`: make a new version of its pipeline with `copy_pipeline`). Seen in an eval (case 61): the
pipeline was made, then the fields refused, and the agent's `copy_pipeline` of the production indexing pipeline under
that name was refused as already in the build, so the build promoted a pipeline that was no new version of it.

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
`dev_tools` is shown in the chat (in Stroom AI by the chat itself, as a JSON code block with `from_example` or
`notes`, `AiToolMessages.shownTable`, the model's copy holding a note that it was shown; another client's agent shows
it; seen in testing: told to print it and call again without waiting, the agent called again, and the form asked
the user to confirm a template never shown), then, called again with `reviewed=<review_id>` in the same turn, a short
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
* **Returns**: `template_name`, `index`, `cluster`, `existing_index` (a warning when the index name already holds
  documents on the cluster, so another can be chosen before indexing), `template`, `dev_tools`, `self_check`,
  `from_example` (or `notes` from the plan alone), `data_stream` (discovery), `pipeline_link`, `stopped_early` and `stepped` when the
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
    `ecs-windows{_suffix}v1*` matched no index). The pattern is the exact index name otherwise
    (`["acme-events-v1"]`, not `acme-events-v1*`, which also covered `acme-events-v10`; asked for by the user), each
    variable a `*` (`index-name-v1{_suffix}` to `["index-name-v1*"]`; `IndexNamePatterns`); a data stream's template
    matches the data stream's name. A template drafted from an Elastic Index doc's fields does the same.
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
`TemplatePipelines`, `TemplateReviews`, `TemplateCheck`, `TemplateMappings`, `ExampleTemplates.ecsDuplicates`,
`TemplateOverlaps`

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
  excluded, components not given and so unchecked, ECS fields mapped twice), `warnings` (not blocking: a pattern
  ending in a digit then `*`, which also covers longer numbers, unless it is the index name with its variables as
  `*`; another agreed template, kept in another pipeline's description, whose `index_patterns` also cover the index
  on the same cluster at the same priority, named with its pipeline and patterns, to narrow or out-prioritise:
  `TemplateOverlaps`), `component_templates`,
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
doc (`<index doc name>-VERIFY`, always: `dashboard_name` is no longer taken, a different one is answered with
`dashboard_name_ignored`; role folder Indexing, `WriteGuard.createFilled`) once the user confirms its
columns: a query on the time field from the sample's earliest event (rounded back to a 30-day boundary) through the
end of today, run on open; a table of the user's fields, newest first, with `StreamId` and `EventId` as hidden
columns; and a text pane on the selected row's record, with stepping and no extraction pipeline. It is laid out as
Stroom's UI lays one out (sized panes, the config's own size, constraints, model version and time range): without
them the search API ran the dashboard but the UI showed it empty. The build's verification dashboard of the index
is reused whatever it was named (`verificationDashboard`: the one of that name, else a `-VERIFY` dashboard whose query
is on the index; seen in chat 177: `<base> -VERIFY`, named by the agent, then `<base> V1-VERIFY`). The form lists every
column, one per line, never cut (`Consent.Listed`; seen in chat 177: the list cut with "…"). An existing dashboard is laid out again
if needed, and its columns changed once the user confirms. The dashboard is the build's when the index is, or when
`pipeline_uuid` is the build's own pipeline writing to it (seen in production: a new version's verification left no
dashboard to promote); any other index is searched through an unsaved dashboard.

Then the test searches (`IndexChecks.runTestSearches`), each of the sample's streams only and unsorted, as the
index may hold millions of other documents (a check of the whole index answered for them, and sorting made Stroom
read every match): every document of the streams (the count must match `expected_documents`, retried every 5 s
while the index catches up, stopping early when two counts agree or Stroom couldn't finish counting); an exact
match per `exact` item; the time range; and any further `searches`. Each exact match and search (EQUALS with
wildcards, NOT_EQUALS, IN, and number ranges) is counted from the Events themselves (`EventsExpectations`,
`PlannedValues`): each field read by the source path the plan kept with the indexing XSLT gives it (the given
pipeline's, else the build's own pipeline writing to the index), as the XSLT writes it (drop_when, stand-ins, the
outcome transform, one value joined or several each), over each stream's first 1,000 events (2,000 in all), and the
index searched over the same events (a whole stream by StreamId, a longer one by EventId as well). It passes only
when the index finds as many (`expected`, `expected_from`); an `expected` given that differs is reported as the
assertion's error (`assertion`), not the index's; an exact value no event holds fails, saying so. A check the Events
can't count (a field the XSLT computes or Stroom writes, analysed text, a date, objects made distinct by a key,
CONTAINS, MATCHES_REGEX) is `independent: false` and doesn't count towards `passed`. Seen in chat 177: the agent's
asserted counts were wrong four times, then it took every expected count from the index itself and reran until
23/23 passed. `expected_counts` says where the counts came from (given, without a kept plan). `findings` names the
core fields of the convention (ECS `level: core`, and the time field) empty in more than half the events read, with
their Events paths, for the user (seen in chat 177: event.action empty in 2,233 of 2,457 documents), and those of
an Elasticsearch index Stroom doesn't list however many events hold them (`not_in_index`: no document gave it a
value, so it was never mapped; seen in chat 178); a mostly empty time field fails. A stream of more than 10,000 events is checked
by its first and last 1,000 and none past its last (asked for by the user: a 100,000-record stream was counted
seven times over); a count Stroom hadn't finished in time is unproven, not a failure. Given `pipeline_uuid`, each
check's lowest-EventId hit is traced back to its record by stepping the pipeline at the hit's StreamId and EventId
(record index EventId - 1), every hit of a stream in one stepping session (each in its own captured the stream
again: verifying a long stream took over five minutes); the trace passes when the document there holds the value
searched and is the hit's, matching on the hit's other columns when stepping numbers the record otherwise (stepped
alone, every record was numbered 1). The stepped document is read as the XML version it declares (found testing awkward values,
2026-11-18: an Elasticsearch indexing XSLT's XML 1.1 output holding `&#xc;` was read as XML 1.0, giving "no
document", so the check failed). With `pipeline_uuid`, a field the pipeline's plan writes for these Events that
the index lacks fails the run.

Passing records the build's pipelines writing to the index as verified (`BuildRecords.VERIFIED`), with the streams
searched (`indexed_streams`), and returns the build's `next`. The plan's `indexed` step stays done only while those
streams are none of them deleted since (`BuildFacts.goneStreams`) or left out of the build's current Events
(`BuildPlan.staleIndex`); one merely older than the feed's newest streams the plan lists still counts: after an Events reprocess the next step is
a new processor filter on the indexing pipeline over the new Events (the index dropped first, as for any index the
pipeline wrote to), an earlier filter counting only if it selects one of them by id (`BuildPlan.selectsAny`). A
record made before the streams were kept still counts as done. Seen in testing: after a reprocess the plan said
indexed, over an index of the Events it replaced. `checks=false`, when the user asks to skip verification, makes the dashboard and returns its link
with no searches; nothing is marked verified.

* **Arguments**: `build`, `index_uuid`, `backend`, `stream_ids`, `expected_documents`, `fields` (the table's
  columns, as the index names them) (required); `exact` (`{field, value}` each, `expected` compared with the
  Events' count); `time_range` (`{field, from, to, expected}`); `retries` (0 to 20, default 6); `searches` (`{field, condition, value, expected}`,
  conditions as a dashboard's, default EQUALS); `pipeline_uuid`; `checks` (default true).
* **Returns**: `dashboard` (`uuid`, `name`, `saved`, `link` when saved, and the design), `passed`, `checks` (each
  with `check`, `pass`, `returned` or `parts`, `errors`, `sample`, `trace`, or `checked: earlier in this session`),
  `failed` where `passed` is false (2026-11-23, chat 188: the checks that didn't pass, each without its `sample` and
  `trace`, its `errors` the first 3 and how many more; among the parts a large result's stub gives in full, so the
  failures reach Stroom AI's chat's model past its 4,000-character inline limit, which before showed the dashboard
  and the first check, passing, and the agent read 130 lines of the saved result), `unfinished` and `next` when time
  ran out, `verified` (pipelines recorded); or `checks_skipped`.
* **Refuses**: missing arguments; `backend` other than the two; `expected_documents` or `retries` not a whole number
  in range; malformed `exact` or `time_range`; a search with no field or a condition dashboards don't take; an
  index doc that can't be read; searches that would mislead (`MisleadingSearches`: on Elasticsearch, Stroom finds
  nothing for STARTS_WITH and CONTAINS, nor a wildcard on an ip field, nor a field it doesn't list, and IS_NULL /
  IS_NOT_NULL match every document, so those are searched as MATCHES_REGEX `.+` on a string field (EQUALS `*` on
  another) / NOT_EQUALS `*`, by the index's field types, `searched_as` saying so; on Lucene, STARTS_WITH,
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
* Before changing a document: `checkManaged` (it must be tagged `mcp-managed`, else "Make a new version of its pipeline
  in a build (copy_pipeline ...)"), `checkInBuild` (and in a build folder, which it returns: index docs and pipelines change only
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
`stepped`, `verified`, `validated` (lists of code digests), `stepped_streams`, `indexed_streams` (the streams the
last passed verification searched: `indexedStreams`), `sample_files` and `user_samples`. `verifiedIndexingBesides`
names a build's other pipelines that passed a verification, for `reprocess_streams`' hint.
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
was declined by the client as too long. A detail given as `Consent.Listed` is shown whole, one value per line, within
1,500 characters, for what the user must see in full to agree to (a verification dashboard's columns: seen in chat
177, cut with "…"). A choice form too large to show at all is not shown: `choose` returns empty
and the agent asks in the chat (seen: a stand-ins form with a question for each of about 150
values, and a hand-edit question naming every field of a wide mapping); so does a form the client answers with an
error. Either is kept for the session, by tool and question (two hours), so the next call, whatever its other
arguments, takes the user's answer as the chat gave it: `takesChatAnswer(context, action, question)` is true where
no form can be shown now or that question's form couldn't be, and `whyInChat` begins the tool's hint ("The form was
too large to show (...)", "The client couldn't show the form") with the argument that takes the answer (decided by
the user, 2026-11-18: tools build forms that fit; one that still can't be shown has its chat answer honoured on the
next call, never a loop or a dead end). Forms are built to fit: `pick` asks many options as one multi-select (the
scope's source types), `firstLines` gives a list in a message as its first five lines and "And N more (where)" with
the whole list in the chat, and questions past `MAX_FIELDS` are asked the first 19 one by one and one for the
others (stand-ins, index clashes). `require` checks its form first: a yes/no field's description is cut to 300
characters (the message carries the summary); values to edit too many for one form are shown in the chat first (a
refusal listing them, with a `shown_id`), and the call made again with `shown_id` asks one short confirmation bound
to what was shown (a value to change is changed by the call's arguments). A confirmation that still can't be shown
(or that the client couldn't show) is refused, saying why its form wasn't shown: a confirmation is given only in a
form, never said in the chat, so the agent can't approve for the user (decided by the user, 2026-11-18). `shown_id` is
taken by every tool
though none declares them (`UserQuestions.RESERVED_ARGS`, kept by the call guard). `Consent.reviewId(shown)` and `wasReviewed`
bind a `needs_review` reply to what it showed (promotion layouts, index templates, records kept as Unknown). Every
ask and answer is audited.

A form put aside by a message the user sent in the chat instead of answering it (`McpElicitations.sentInstead`: a
cancel carrying the message) tells the agent, as the call's result, "The user sent a message instead of answering
'...': "...". Nothing was done. Reply to their message first. Don't call <tool> again until they've said what they
want." (one wording, `McpElicitations.sentInsteadText`), never "closed or ran out" (decided by the user, 2026-11-19).
For a question or choice (`answer`, `choose`, `chooseEach`, `answerEach`, `pick`, `review`), the question is then
answered in the chat: it is kept for the session as a form not shown is, so `takesChatAnswer` is true for it, the
tool takes its argument as the user's answer, and a call without it is told to pass it (`whyInChat`: "The form isn't
shown again: the user put it aside for the chat ..."), the form never shown again. A confirmation or approval
(`require`) is never taken from the chat: put aside, it is asked in its form again on a later call. Words written in a
form whose answer must be one of its options or a field's name are taken only when they are one whole, ignoring case
and surrounding spaces (`Consent.exactly`); anything else is put to the agent (`Consent.wroteInstead`: "The user wrote
"..." in the form instead of choosing ...: if it's a question, answer it first; then ...") and the question answered
in the chat from then on, as for a form put aside.

**`FormsRequired`**: what a call that changes something is told when the user can't be asked in a form: an MCP client
that didn't say it can show forms (over `/mcp` it can't make changes at all; its read-only calls still work), or a chat
the user is away from (nothing needing agreement is done unattended).

**`BuildPlan`** and `BuildMemory`: the onboarding plan with its state read from the build.

* The checklist's stages: `1 events` (feed, samples, converter, surveyed, source_types, catalogue, translation,
  pipeline, stepped, covered, processed, unmapped, validated, documented; `catalogue` done once every event type and
  the catch-all are confirmed, catalogue_event_types' `next` naming it), `1 reference` (reference_pipeline,
  reference_stepped, reference_processed, reference_documented, between the converter and the translation, as a lookup
  made before its Reference stream exists
  is remembered for 10 minutes), `2 indexing` (index, indexing_pipeline, index_template, indexed, index_documented),
  `3 output` (cef_mapping, cef_pipeline, cef_stepped, cef_documented) and `finish` (promoted). `status` marks each step
  `done`, `to do` or `not needed` from what the build holds (`BuildFacts`, read by `StroomBuildFacts`): its kind,
  whether it works from existing data (source feeds or working copies: then no feed to create or samples to upload),
  a discovery, reference-only or CEF build, its survey, its sample's format (from the newest raw sample's head, else
  the survey; a converter isn't needed once the events pipeline reads the data with a JSON or XML parser).
  `plannedSteps(build)` leaves out what the build's kind doesn't need; `next.progress` doesn't count those.
* **Starting from an existing pipeline** (2026-11-24): on existing data, before the build holds a translation, the
  `catalogue` step's call (`catalogue:offer`) says catalogue_event_types first asks the user how the build starts
  (`BuildFacts.existingTranslation`, `ExistingTranslations`). Once they choose the existing pipeline (the record's
  `start_from`), `catalogue` stays to do though the build holds its copy's translation, and its call follows the
  work: `copy_pipeline source_uuid=<it>` (`catalogue:existing_copy`), then `rebuild_mapping uuid=<the copy's XSLT,
  keeping no mapping> pipeline_uuid=<the copy>` (`catalogue:existing_rebuild`), then catalogue_event_types to check
  and confirm the catalogue read back (`catalogue:existing_confirm`); then stepping, as any new version. Drafting
  instead leaves the plan as before.
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
  `key=value` or `unknown text`), `records`, `fields` (each `field`, `fill_rate` (real values only: an empty
  value or a placeholder such as `-` isn't filled), `placeholder_rate` (when any), `type`, `examples`; types include
  `ip`, `integer`, `decimal`, `embedded json`, `embedded json after prefix`, `embedded xml` and `embedded xml after
  prefix`, the XML types with a `note` to keep the field whole and read it in the translation through xml_fields; one that reads as a document, `HeldMarkup`, told to keep it as one Data value, 2026-11-22), `suggested_parser`, and what the format
  needs (`text_converter`, `parser_properties`, `xslt_input`, `delimiter`, `has_header`, `columns`, `malformed_lines`,
  `note`). Up to 200 records a sample are profiled. Lines that are not records are named from up to 5,000 lines (a JSON
  line cut short, a CSV row short of columns or with an unclosed quote, a key=value line with no pairs); a file mostly
  of JSON lines is still JSON lines, read through a Data Splitter with `json_fields`, as Stroom's JSONParser stops at
  the first bad line. Kubernetes CRI logs get `record_end`. Several files are profiled together with `files` and
  `differences`. Text that starts as JSON and doesn't parse is refused with where and why.
* **`JsonStringValues`**: for JSON the JSONParser reads (not JSON lines read by a Data Splitter), the string values
  it wouldn't pass on as they are: the longest over its default `stringTruncateLength`, 10,000 (`long_strings`, up
  to 5 fields: `field` as a dotted path with `[]` for array items, `length`, `record`), which it cuts with a
  Warning, and the fields holding a NUL (`nul_strings`), which Stroom's XML writer takes for its escape switch
  (`preventEscapeSwitching` false by default), writing the text after it unescaped. Each comes with a note
  (`long_strings_note`: the choice, whole or cut, is the user's, which `draft_translation_mapping` asks and records
  (`long_values`); raise `jsonParser.stringTruncateLength` or cap the field in the mapping, leave `maxStringLength`
  alone; `nul_strings_note`: `transform: strip_control`), which `draft_translation_mapping` adds to its `notes`
  (replaced by the choice once made: `askNote`, `wholeNote`, `cutNote`). Seen: audit mail bodies of 20,000 to 46,000 characters cut with a Warning, the agent lowering
  `maxStringLength` instead (every body an Error), and NULs in them leaving `&nbsp;` and a form feed unescaped
  in the Events streams. The pipeline's own `stringTruncateLength` is not read: the draft has no pipeline.
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
  addresses, numbers, times or booleans (a misfit). A misfit is asked only where the destination would refuse it:
  callers name the fields whose destination takes any text (`LocalCheck.placeholders` from the schema: a Data Value,
  or a string leaf with no pattern or enumeration; `draft_index_mapping` from the plan: `FieldPlans.takesText`, keyword
  and text), and among numbers a misfit holding a digit is data (`146.0.0.0`, `D02`). Seen: W3C logs' `-` for a missing address went into an Elasticsearch ip field,
  which refused every document holding it. Used by the translation draft's local check and by `draft_index_mapping`
  (its stand-in questions, through `PlaceholderChoices`), not by the tools above.

Raw stream text is read through the stream store (`StreamReader.readHead`, `readWindows`, `cachedRawText`,
`samplePart`), decoded as a pipeline decodes it: undecodable bytes are replaced and `StreamReader.charsetReport` says
what was replaced, which the tools pass on (`undecodable_bytes`, with advice). `samplePart` decodes a chunk at a time
(`StreamCharsets.ReportingReader`), so a part of any size is read through.

**Sample streams' sampling** (`SampleStreamTexts.read`, `StreamReader.samplePart`, `PartSampler`; settings
`ai.sampling`, `AiSamplingConfig`). The tools given `stream_ids` (profile_sample, catalogue_event_types, the drafts and
checks) read each stream's parts as their own texts, at most 20 parts, chosen spread through a stream of more from
its first to its last (`PartSampler.spread`: deterministic, evenly spaced), each up to its share of the stream's
`maxCharsPerStream` (2,000,000) characters (`PartSampler.share`: what is left over the parts left, never less than
`maxEventChars`, so every chosen part is read however small the stream's characters: they bound only the depth beyond
that). Characters decide how much of a part is kept, whatever its format: whole events up to its share, never a count.
A part is read as a stream of events, never cut, told by its first character: `[` is one JSON array
(`JsonArrayStream`: nesting, strings and escapes tracked a character at a time, nothing parsed), its events rejoined
as `[e1,e2,...]` (an array followed by more text, JSON lines of arrays, is read a line at a time); `<` (not a syslog
priority) is XML (`XmlRecordStream`: markup and nesting tracked, nothing parsed), its records the root's children, or
the top-level elements of fragments (a second top-level element after the first), as `SampleProfiler` and `XmlTree`
take them, kept records written back in the document's own prolog and root start tag (namespace declarations and
attributes as they are) and closed by the root's name, so the profile, draft and checks read the same document
shape; XML that can't be read so (no root, a root holding no record, markup not well-formed before a record) is read
from its start to its share and cut back to a line end, and said; anything else is read a line at a time to its
share, each line kept as it is (`LineStream`). An event (or
line) over `maxEventChars` (100,000) is read through without being held and left out, and said in the notes with its
length and its longest string value (e.g. "stream 9 part 3: 4 events over 100,000 characters left out of the sample
(largest 412,118 characters; longest string value 398,002, in 'message'); 20 events sampled"; an XML record's
longest text is given, its field not); that value still
reaches profile_sample's and the draft's `long_strings` warning (`JsonStringValues.addSkipped`), so the JSONParser's
cut of it isn't lost. A part ending inside its last event drops it and says so; a sample cut short by the call's
time isn't kept in `StreamReader`'s cache, so a later call reads the part again; a part whose every line is too long
is read from its start instead (a one-line object wrapping its records). Seen in user testing: an OpenWebUI audit
stream of 787 newline-free JSON arrays (67 MB), its parts read to 100,000 characters, cut inside a `\u` escape, and
every sampling tool refused the sample as "isn't valid JSON". Text still cut at a read limit (a client's, or a head)
is said to be cut, never invalid: `SampleProfiler` tells a cut from invalid JSON (`JsonCut`: it ends open, no wrong
escape before the end, the parse failing in its last record), "cut at the read limit (N characters) inside its first
record" when no record is whole, and `RecordSplitter.Chunk.note` says where a JSON array's split stopped.

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
  leaf types, patterns, enumerations, descriptions), read once from the XSD's text. An element repeats when its own
  maxOccurs or that of anything around it (a sequence, a choice, a group reference) allows more than one, and a
  choice's members are alternatives only when neither it nor anything around it repeats: in 4.x, View's, Move's
  Source's and Authorise's objects are a choice of elements each maxOccurs 1 inside `MultiObjectGroup` with maxOccurs
  unbounded, so several Folders, or an Object and a Folder, go in one View (3.x has the choice itself unbounded).
  That is repetition in the element's own parent: an Email of Send/Payload repeats, its From doesn't. A group
  (`for_each`) and a typed placement's group go only on an element that repeats in its parent, and `repeat: true`
  only on a leaf that does or whose element does (Destination/User/EmailAddress, not Payload/Email/From/EmailAddress:
  a for_each on the Email instead); an index field is marked multiple when anything on its path repeats
  (`canRepeat`). Used by the checks and by the
  translation generator (schema order, paths that don't exist with suggestions, constants against enumerations).
* **`EventChecks`** and **`SchemaErrors`**: `check_events`' two checks. `SchemaErrors` validates against a compiled
  schema, giving each error's line, a path such as `/Events/Event[2]/EventTime/TimeCreated` (a position where the
  parent holds more than one of that name) and the message, the validator's two messages for one bad value given as
  one error. **`EventStreams`** reads Events streams as one `<Events>` document for it, XML 1.1 (with its
  declaration) when any record declares 1.1, as Stroom writes Events.
* **`WellFormedFaults`**: where XML that isn't well-formed is at fault, for the refusals of `XsltChecks.parse` and
  `check_events`: the line and column, the Event it is in (the Nth `<Event>`), up to 50 characters before and 30
  after with control characters shown as `\u000c`, and, for a C0 character reference XML 1.0 forbids, that XML 1.1
  allows it and the value is the source's.
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
  rule's keys first appeared at the 557th of 3,933 records, and the check said they matched nothing). An
  extraction reading an xpath (XML input) is first checked against the first 200 record documents
  (`XPathChecks.MAX_SOURCE_DOCUMENTS`); one whose regex matches none of them is checked against all of them
  before it is called a problem; if the check's time runs out before the rest, it is a warning (seen in the agent
  eval, case 36: a SecretServer export's folder and permission keys, first held after the 200th of 1,000
  records, refused, and the agent then saved with an empty sample to get past them). It also
  reports:
  * a TypeId the same for different event types (`sharedTypeIds`), as a problem, not a warning (seen as a
    warning: two runs left TypeId the category alone, 'User' for logons, logoffs and lockouts, the warning one among
    many);
  * required elements a rule writes only when their inputs have a value, whose inputs are empty in its records
    (`requiredLeftEmpty`; seen: the schema's error named the element after the one left out, read as Data in the
    wrong place, two rounds of stepping before the Command entry got a default). The elements every record of their
    rule fills (`requiredFilled`) are dropped from the generator's warning that they may be left out, which then names
    only what the sample says nothing of (seen in chat 185: kept, the agent asked the user and put 'Unknown' in
    each). Rules' warnings alike are merged, an element only some rules name followed by `(element: rule1, rule2
    only)`;
  * Data entries reading fields none of the rule's records hold, as a warning (`dataNotHeld`; seen: a SecretServer
    launch rule's rpc_account, target and duration_ms, in none of its records);
  * value maps none of whose keys is a value the records give the field, so every record gets the default or nothing,
    as a warning naming the keys and the values held (`mapKeysNotHeld`; seen in testing: iptables' `PROTO=TCP`
    mapped by protocol number, 6, 17 and 1, so every dropped packet's TransportProtocol was Other);
  * fields mapped as they are into a user's Id whose sample values are domain-qualified, pointing to `strip_domain`
    (`domainQualifiedIds`; seen in eval cases 35, 36 and 43: the domain kept in every Id).

  Schema values (`checkSchemaValues`) are checked on what the mapping's extractions give as well as the record's own
  fields (seen: SecretServer's fields, read from its message's key/value pairs, were never checked). Where a field's
  values fit the schema's once lower- or upper-cased, it names the entry transform to give (`transform: lower` or
  `upper`) rather than only saying to map the values that fit.
* **`ActionRules`**: the action elements a sample's own values show, as rules (`actionRules`, `described`, `read`,
  `severityFields`, `networkFields`). An agent left with Unknown placeholders kept marking firewall traffic and
  administrator logons Unknown; for events plain from their values, the draft writes the rule, and a rule that keeps
  them Unknown is given the rules to use instead.
* **`SplitterXml`**: a `SplitterSpec` written as Data Splitter XML (`converter`, `generateSplitter`, `recordRegex`,
  `recordEndRegex`, `bufferSizeFor`, `withBufferSize`, `longestLine`, `EXAMPLES`). It writes the subset of the
  Data Splitter language the dry run (`SplitterDryRun`) follows. `SplitterSpec.fromMap` reads a spec's JSON.
* **`SourceNotes`**: the source notes block (the field dictionary record_source_notes keeps, and the catalogue of
  event types catalogue_event_types keeps: `event_types`, `catch_all`, `scope`, read by `catalogue`): drafted from
  (`applyToDraft`: the dictionary's fields, then the catalogue's rules through `CatalogueTranslation`), checked
  against (`checkMapping`: the event types not yet confirmed, compared by the records rules take, `CatalogueKept`;
  `merged`, `detailProblem`), and listed in the documentation.
* **`CatalogueTranslation`**: the translation's rules from the catalogue (see draft_translation_mapping, From the
  catalogue). **`LocalCheck.holds`** and **`ruleOf`** are the one evaluator of conditions the catalogue's check, the
  build-time checks and the generated XSLT agree on (`TestConditionParity`, `TestE2eConditionParityProcessed`).

## The translation generator, the reader and mapping changes (`stroom.ai.impl.tools.generation`)

The translation XSLT generator and what works with it, in `stroom-ai-impl`, used by build_translation_xslt,
draft_translation_mapping, save_xslt, rebuild_mapping, write_documentation and the build checks.

**The mapping** (`TranslationMapping`, `EventRule`, `FieldMapping`, `Condition`, `Lookup`, `Extraction`,
`KeyValuePairs`, `DropRule`, `XsltStyle`, `SharedTemplate`, `SharedFunctions`, `FillTemplate`, `MaskNames`): plain records whose JSON is read and
written by hand (`ModelReader`), so the stored form is exactly the one kept in XSLT descriptions. `fromJson` /
`parse` validate with lax types, defaults and each model's own checks; every error is a `MappingError` (`loc`,
`type`, `msg`, `input`) in a `MappingValidationException`, which `MappingProblems` turns into the problems the agent
reads. Reading also normalises: `pairs` become extract entries, a rule's `data` list Data entries, a `meta` entry the
xpath reading it, Data Names the style's `data_names`. `toJson(DumpMode)` writes `ALL`, `WITHOUT_NONE` or `COMPACT`
(no nulls, no defaults: the form kept in a document's description), keys in the models' field order. Style numbers
(`variable_min_reads` and the like) are longs, and a number past that is refused.

**Conditions and the catch-all** (`Condition`, `EventRule`; DESIGN.md, Event type catalogue, Contracts). A rule's
`when` is an AND of conditions, each a `field` (or `xpath`) and exactly one test: `equals`, `one_of`, `matches`,
`present`, `in_dictionary`, and since 2026-11-19 `starts_with` and `contains` (exact, case-sensitive, over the decoded
value, written as XPath's `starts-with()` and `contains()`, cheap on long values); `not: true` for the test not to
hold (a missing field makes it hold, as XPath's `not(f = 'x')`; refused with `present`, which says it with
`present: false`). Over a JSON array field `equals`, `one_of`, `starts_with`, `contains` and `matches` hold when any
item does, `not` when none does (Stroom's JSON as XML runs an array's items together as its text, so the generator
compares them one by one). A Data Splitter field's condition compares the value as its mapping decodes it (CSV `""`,
key=value `\"`). A key holding `.` or `/` (or starting `[`) is spelt in brackets: `['log.level']`,
`labels['app.kubernetes.io/name']`. Each test is generated, read back by the reader and said in words
(`is`, `starts with`, `isn't`). `catch_all: true` marks the rule for uncatalogued records: no conditions (a line
format's: only conditions on the variant field), never a drop; the generator logs `Caught by` from the flag, never by
inference, and the reader reads it back.

A `MappingError`'s type and message come from a fixed vocabulary (`missing` / `Field required`, `string_type`,
`literal_error`, `value_error` / `Value error, ...`), located with dots in a message (`events.0.name`) and with
brackets in the problems the agent reads (`events[2].fields[0].path`); an exception's message starts with the count
and the model (`1 validation error for TranslationMapping`). The agent acts on this wording, and the model fixtures
(`model_cases.json`) pin it, so a change to it is a decision, not a fix. Reading is lax: a boolean may be `0`, `1` or
a word such as `yes` or `off`, an integer a whole number, or a string or float holding one, and unknown keys are
ignored. A field added to a model after mappings were first kept (`data_parent`, `join`) is written only when set,
whatever the dump mode, so a mapping that doesn't use it is written exactly as before and every kept mapping and
fixture still reads and compares unchanged; the reader likewise gives `data_parent` in a leaf's reading only for a
Data inside Data. A group (`for_each`, `as`, `fields`; and `item` in its fields, see DESIGN.md, Repeated groups) is written the same way: only when set. `mapping-descriptions.json` (a resource of stroom-mcp-impl, beside `MappingProblems`) holds a
description of every key of the mapping's models (`TranslationMapping`, `EventRule`, `FieldMapping` and the rest),
from which `MappingProblems` says what a key left out is. Nothing generates it: a key added to a model, or changed,
needs its description added or changed there by hand.

**Composite keys and rendered values**: a lookup's `key_fields` (1 to 3 input fields) and a reference map's
`key_fields` make one key alike (`LookupKeys.keyText`, written by both generators: each field's first value
normalised, joined by U+241F SYMBOL FOR UNIT SEPARATOR, written `codepoints-to-string(9247)`; one field is a plain key,
as `field` and `key` write it). `LookupKeys.partCount` reads a loader's key back, so build_translation_xslt warns of a
lookup whose key has a different number of fields than a loader of its map writes (`LookupKeyCounts`).
`fill_template` (`FillTemplate`: a placeholder regex whose first group is the number, and a field or xpath giving the
values in order) fills the entry's input, a template, with `mcp:fill-template` (`xsl:analyze-string`; a placeholder
with no value, or a match whose group isn't a number, is left as written). `mask_names` (`MaskNames`: a static
`names` table or a looked-up one, its items' `mask` and `name` paths, each named step an element of that name or, for
an element with a `key` attribute (JSON as Stroom reads it, copied by a JSON loader), the one of that key; `@key` and
`.` read a JSON object keyed by mask) writes the names of the masks an unsigned
64-bit value (decimal or `0x` hex) holds, with `mcp:mask-names`: XPath has no bitwise operators, so the value is an
`xs:integer` split into set bits by halving (`mcp:set-bits`), exact for bit 63; a mask names the value when all its
bits are set; names in the table's order, each once, one element each with `repeat` else joined by `separator`. Both
log a warning naming the record (`RenderXslt`): placeholders left unfilled, set bits no name covers, a value that is no
mask; a key a map lacks is Stroom's own lookup warning. `RenderXslt` writes the functions only when the mapping uses
them, so other XSLTs are unchanged to the byte.

**A length cap** (`max_length`, 2026-11-21): an entry's value cut to that many characters after any transform,
`substring(…, 1, N)` round the transformed first value (`XPathText.capText`, from `XsltGenerator.scalar`), for every
input kind and inside groups and `repeat` loops alike. A positive whole number; refused with `value`, `map`,
`time_format`, `fill_template` and `mask_names`, and on a group entry as its other conversions are. The documentation
says it beside the transform (`` `body` (strip_control, first 4000 characters) ``). Asked for in chat 182: the
`long_strings_note` told the agent to cap a long JSON body with an xpath, which it wrote as
`substring(event.Body.Content, 1, 10000)`, selecting nothing for JSON input; the note now names `max_length`, and says
the JSONParser still cuts (and warns) at its `stringTruncateLength` first. `rebuild_mapping` reads the cap back
(`ReadIdioms.capped`, by the `max length` shape): the entry it cuts, with its `max_length`, for a plain, transformed,
defaulted, repeated or group-item entry, so mapping → XSLT → rebuild gives the same mapping (2026-11-22; it was read
as an xpath before).

**`clean`** (a transform, 2026-11-22): control characters out (all but tab, new line and CR, and U+FFFD, as
`HeldText.CONTROL`), then `trim` (`normalize-space`, which makes tab, new line and CR spaces): a value as the tools
read it, which a source type group's `source-type` function tells its source type by. Unlike `strip_control`, an
ANSI colour code keeps its parameters (`ESC[1m` gives `[1m`), as the tools' reading does. Written for the per-source
type System/Name (draft_translation_mapping), read back by rebuild_mapping.

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
suggestions, disallowed constants, alternatives of a one-of choice used together, missing required elements, unquoted
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
outline, plan fields) and `CodeDiff` (with `LineDiff`). The kept blocks are in `stroom.ai.impl.tools.documents.KeptMapping`
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
nothing to read"). One written by hand is laid out as the generator's first (`HandLayouts`, see rebuild_mapping). The reader also knows the time conversions earlier generators wrote (a seconds' fraction
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
the whole mapping (or index plan): a rule changed by name (each key given replacing its own, and its `fields`
changed entry by entry by path and Data name, as common entries are; `"replace": true` replaces it whole), a common
entry by its path (and Data name), an extract entry by the fields it extracts (`names`), any other key whole,
`{"remove": true}` to remove a rule or an entry. Seen in testing: `changes.extract` replaced the whole list, so the
few new entries an agent sent wiped the rest (26 cut to 2), and every record fell to the rule for the rest. Seen: with
rules replaced whole, changing one entry of a rule of about 140 meant resending them all, so the agent edited the
mapping file by hand (a dozen calls) and lost the TypeId of a rule it resent. The result is an `Applied` (the merged mapping and what each
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
    all events (up to 15; seen: nothing said which kinds of event a section's fields came from). For a plan, the
    shares are of the Events the indexing XSLT got for the records stepped (`StreamSummaries.summariseEventRecords`
    over each step's input at the element), so they and the values are of the same records (seen: a field in 2.5%
    of events, counted from records read spread through the first stream, with "(none in the sample)" for values
    stepped from others); the streams are read apart only when the steps give no input. Any field the stepped
    documents have no value of, whatever its share, is looked up in the index (`IndexValues.withoutValues`) and its
    values shown as from the index (asked for by the user); a failed lookup gives none and is logged at INFO. A
    field some events have that still has none says why: `(none in the N documents stepped; not found in the
    index)`, or `index not searched` when no index could be read; one no event has keeps `(none in the sample)`.
    When stepping stopped at the call's time limit, the section says so in a note. Percentages are whole numbers,
    a half rounded to the even number. `TestIndexFieldDocs` pins the text
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
`stroom.ai.impl.tools.instructions.AgentKnowledge`). Without that module there is none, and the tools that use it say so.

* **Working guides** (`guides/<name>.md`): `agent-instructions`, `cef`, `data-splitter`, `documentation`,
  `aggregated-feeds` (source types, the registry, scopes, source type groups and their pipelines, indexes and
  promotion), `event-logging`, `indexing`, `joins` (facts only: StroomQL's join kinds with examples, keys and
  limits, sequence_query's engines and guards, the table tool's join, and what fetched rows are; every example run by
  `TestE2eJoinsGuide`), `json-input`, `mapping` (a compact reference of a translation mapping's keys, time
  formats and how `changes=` merges, which the draft's hint names, and the event type catalogue: evidence, event
  types, conditions with `not`, `starts_with` and `contains`, proposals, the check and confirmation, which
  catalogue_event_types' description points to), `reference-data`, `visualisation` and `xslt`.
  Short, hand-written
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
