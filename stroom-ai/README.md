# Stroom AI: maintainers' guide

Stroom AI is Stroom's built-in agent. It has two front doors that share one set of tools:

* **Ask Stroom AI**, a chat panel in the Stroom UI: a user asks questions and an AI model (any OpenAI-compatible
  API) answers, using Stroom's own tools (to query Stroom, read streams, step pipelines, build feeds,
  translations, indexes, dashboards and charts, and work with files in a per-chat workspace) and the tools of any
  MCP servers the model is given.
* **Stroom's MCP endpoint, `/mcp`**, which serves the same tools to outside MCP clients (VS Code, Claude Code and
  others), as the signed-in user.

Which tools a user is given, in the chat and over MCP alike, is set by MCP tool policies.

This file is the entry point for people (and agents) who maintain and extend Stroom AI: what the module is, where
things live, how to build, test and run it, and how it is configured. It is not the user or administrator guide:
that is in the stroom-docs repository (see [Documentation](#documentation)). GitHub issue
[#5848](https://github.com/gchq/stroom/issues/5848) is the original design issue.


## Documentation

In this directory:

| File | What it holds |
|---|---|
| [DESIGN.md](DESIGN.md) | The design: how the chat, the agent loop, the tools, the build workflow and `/mcp` fit together, and the decisions behind them. |
| [TOOLS.md](TOOLS.md) | The tool reference: every tool, its arguments and what it does. |
| [FINDINGS.md](FINDINGS.md) | How Stroom actually behaves, as learned building and testing the tools: the facts the tools are designed around, each with where it is handled in code. Read it before changing how a tool reads, steps, processes or indexes. |
| `stroom-mcp-impl/src/main/resources/stroom/mcp/impl/knowledge/` | What the model is given: `server-instructions.md` (the rules for every build), `guides/*.md` (the agent guides) and `conventions/` (field naming profiles and the ECS reference files). |

User and administrator documentation is in the stroom-docs repository, under
`content/en/docs/user-guide/ai/`:

| Page | Covers |
|---|---|
| `ask-stroom-ai/setting-up.md` | Setting up a model, default chat settings, table analysis, tool policies, charting libraries, MCP tool servers, the main configuration properties, troubleshooting |
| `ask-stroom-ai/chat-interface.md`, `using-the-chat.md` | The chat: modes, models, auto-approve, steering, attachments, suggestions, task list, approvals, questions and forms, steps for the user, links, workspace files, commands, context and memory |
| `ask-stroom-ai/dashboards.md`, `charts.md`, `analytics.md`, `search-extraction.md` | Dashboards and queries, charts (visualisations), table and chart analysis, search extraction |
| `ask-stroom-ai/prompt-guide.md` | What to ask |
| `ask-stroom-ai/cluster-questions.md` | The administration tools: processing tasks, server tasks, system properties, volumes, data retention, Elasticsearch index templates |
| `how-it-works/runs-and-context.md`, `memory.md`, `trust.md`, `workspaces.md` | Runs, compaction, token counting, memory, untrusted content, workspaces and their limits |
| `agent-tools/_index.md`, `connecting-clients.md`, `deployment.md`, `tool-policies.md`, `tool-reference.md` | `/mcp`: clients (VS Code, Claude Code), sign-in, load balancers, tool policies, the tool list |
| `pipeline-admin/onboarding.md`, `builds-and-promotion.md`, `editing-pipelines.md`, `configuration.md` | Onboarding feeds (builds, templates, discovery, reference data), what the agent changes, build folders, hand edits and promotion, changing, processing and verifying pipelines, and the build settings (workspace folder, pipeline stages, error triage, field conventions, sample uploads, AGENTS documents and their typed sections, guides) |

When behaviour a user or administrator sees changes, update the stroom-docs page as well as this repository.
[Behaviour internals](#behaviour-internals) below says how that behaviour is implemented.


## Where things live

| Module | What it holds |
|---|---|
| `stroom-ai/stroom-ai-api` | The interfaces other modules use (e.g. the `ai()` XSLT and StroomQL functions' service). |
| `stroom-ai/stroom-ai-impl` | The chat, the agent loop and every tool (built-in and ported). |
| `stroom-ai/stroom-ai-impl-db` | Persistence: jOOQ DAOs and the Flyway migrations (`src/main/resources/stroom/ai/impl/db/migration/`). The jOOQ code under `db/jooq/` is generated. |
| `stroom-ai/stroom-ai-mock` | A mock module for tests of other modules. |
| `stroom-ai/stroom-mcp-impl` | The `/mcp` endpoint (servlet, sessions, forms, approvals, uploads) and the bundled knowledge (guides, conventions, server instructions). |
| `stroom-core-shared/src/main/java/stroom/ai/shared/` | Shared (GWT and server) types: chat messages, REST resources, settings. |
| `stroom-core-client/src/main/java/stroom/ai/client/` | The GWT UI: the chat panel (`AskStroomAiPresenter`), settings, memory, tool approvals, tool policies, forms and cards. |
| `stroom-app/src/main/resources/ui/vis.js` | The visualisation frame's `stroom.report` hook, which generated charts call with their render report. |
| `stroom-app/src/test/java/stroom/ai/impl/`, `stroom/mcp/impl/` | Integration and end-to-end tests on a real Stroom (see [Testing](#testing)). |

In `stroom-ai-impl` (`stroom.ai.impl`):

* **The chat and runs**: `AiChatRunManager` starts and tracks runs (one per question, worked on in the
  background as the user); `McpAgentChat` is the agent loop; `ContextAssembler` builds what the model is sent;
  `ChatCompactor`, `ContextBudget`, `ContextUsageTracker`, `TurnSummaries` and `ReferenceResults` keep a chat
  and a run within the context window; `ModelStreamer` streams the model's replies; `AskStroomAIService` and
  `AskStroomAiResourceImpl` are the REST side.
* **Models and MCP servers**: `OpenAIModelStoreImpl` (the OpenAI Model document), `McpToolset`,
  `McpServerConnection`, `McpClientCache`, `McpToolAdapter`, `McpToolFilter` (an MCP server's tool policies),
  `McpElicitations` (forms from MCP servers' tools), `McpJson` (the MCP SDK's mapper, with a mix-in Jackson 3.2
  needs to write forms).
* **Approvals and trust**: `ToolConsentGate` is the one place a call is approved; `ChatConsentGate` and
  `ToolConsent` decide for a chat; `ToolApprovalService` records answers; `ToolGrants` and `CurrentToolGrants`
  apply MCP tool policies (`AiToolPolicyService`, `AiToolPolicyResourceImpl`).
* **Tool groups**: `ToolGroups` and `BuiltInToolGroups` (the built-in tools only some questions need, offered
  behind `activate_<group>` tools).
* **Answers**: `AnswerLinks` (documents and streams named in an answer made into links), `AnswerNotes`,
  `FollowUpSuggester`, `ChatTitler`.
* **Dashboards and charts**: `DashboardBuilder`, `DashboardChanges`, `DashboardComponents`,
  `DashboardSettings`; `vis/` (`ChartLibraries` finds ECharts and D3 Script docs and puts Stroom's adapters
  beside them, `AdapterScripts` serves this release's adapter for each, `EChartsTemplate`, `D3Visualisation`,
  `ChartCheck`); the adapters are resources in `src/main/resources/stroom/ai/impl/vis/`
  (`echarts-generic.js`, `d3-generic.js`).
* **The workspace**: `workspace/` (files, tables, safe jq, XPath and regex, limits).
* **Tools**: `tools/` holds every tool. The chat's own tools (`*Tool.java`, e.g. `RunStroomQlTool`,
  `ProposeDashboardChangeTool`, `ProposeVisualisationTool`, `ProcessorTasksTool`, `ExplainProcessingTool`) are
  registered in `BuiltInTools`; the build workflow's shared code is in `tools/draft` (drafting mappings),
  `tools/generation` (mapping to XSLT and back: `XsltGenerator`, `XsltReader`, `HandEdits`), `tools/sample`
  (profiling, surveying, the Data Splitter dry run), `tools/indexing` (field plans, index templates, ECS),
  `tools/fielddoc` (documentation of fields) and `tools/cef`. `error_rules.yaml` holds Stroom's error triage rules.
* **Memory**: `memory/`. **Search extraction**: `extraction/`.

In `stroom-mcp-impl` (`stroom.mcp.impl`):

* **The endpoint**: `McpServlet` (`/mcp`), `StroomMcpServer` (Streamable HTTP with per-user tool lists),
  `McpSession`, `TransportSessions`, `RunningCalls`, `McpForms` and `McpApprovals` (forms and approvals for
  outside clients), `McpUploadServlet` (`/mcp/upload`), `McpProtectedResourceServlet`, `McpWorkspaces`.
* **The build tools** (onboarding, translation, indexing, processing, documentation, promotion): `tools/`,
  registered in `BuildToolsModule`. `WriteGuard` keeps writes to the build folder; `BuildPlan` and
  `BuildStatusTool` give the plan and its `next` call; `Consent` asks the user in forms.
* **Bundled knowledge**: `BundledKnowledge` reads `knowledge/`. Its guides and conventions are listed by name in
  the class (a folder can't be listed reliably inside a jar), so a new guide must be added to `GUIDES` too.


## Building and running

Stroom AI builds with the rest of Stroom (see the repository's `AGENTS.md`). The modules on their own:

```bash
./gradlew :stroom-ai:stroom-ai-impl:compileJava :stroom-ai:stroom-mcp-impl:compileJava
./gradlew gwtCompile    # The chat UI
```

A Flyway migration added to `stroom-ai-impl-db` needs the jOOQ code regenerating:
`./gradlew :stroom-ai:stroom-ai-impl-db:generateJooq`.

To run it locally, start a Stroom dev node as usual and:

* create an **OpenAI Model** document and set it as the default (see stroom-docs `ask-stroom-ai/setting-up.md`);
* for work without a model, set its model ID to `__stub__` (`AiServiceImpl.STUB_MODEL_ID`): the stub answers at
  once, and a question can include `[[think]]` (to get some reasoning) or `[[tool:NAME {json arguments}]]` (to
  call a tool first);
* give users a tool policy (Security > MCP Tool Policies): the upgrade adds only _Default read-only tool
  policy_, so the write tools need a policy granting them;
* for `/mcp`, the user needs the _Use MCP Tools_ permission and an API key; point a client at
  `<stroom>/mcp` (see stroom-docs `agent-tools/connecting-clients.md`).

Don't rebuild while a dev node or a long test run is using the jars.


## Testing

* **Unit tests** in each module: `./gradlew :stroom-ai:stroom-ai-impl:test :stroom-ai:stroom-mcp-impl:test`.
* **Reference files.** Several tests compare what they produce with files under
  `src/test/resources/stroom/ai/impl/golden/` (`GoldenFiles`): the tools and prompts the model is shown, the exact
  output of the larger tools, scripted chat runs, how stored chats are read back, and the permission, audit,
  consent and tool-name tables. They show that a change (e.g. a refactor) leaves what the agent does as it was. A
  mismatch writes the actual text under `build/golden-actual/` and fails. When a change is meant to alter them,
  accept it with `-DtestUpdateGoldenFiles=true` (e.g.
  `./gradlew :stroom-ai:stroom-ai-impl:test -DtestUpdateGoldenFiles=true`) and review the reference files'
  `git diff` with the change. The stored-chat fixture (`fixtures/stored-chats/`) is never regenerated: it is how
  chats are stored today, so a change that can't read it breaks existing chats.
* **Integration and end-to-end tests** in `stroom-app` run the real chat and `/mcp` tools on the integration test
  database, stream store and the standard content packs (`AbstractAiIntegrationTest`):
  * `stroom/mcp/impl/e2e/Test*` (`AbstractE2eTest`): each build workflow end to end through an administrator's MCP
    client (`McpTestClient`) that agrees to every form unless told otherwise, with the processing and shard
    flushes Stroom's jobs would do run by the test. `TestE2eEvalReference` builds every evaluation case from its
    reference solution (`STROOM_EVAL_CASES=12,35` runs only those).
  * `stroom/ai/impl/TestAskStroomAi*` and `stroom/mcp/impl/Test*`: the chat with real tools against a fake model
    server, MCP sessions, policies and sample uploads across nodes.
  * Elasticsearch tests need a test cluster: `STROOM_TEST_ELASTIC_URL` (and `STROOM_TEST_ELASTIC_TLS_URL`,
    `STROOM_TEST_ELASTIC_TLS_PASSWORD` for the TLS cases); `STROOM_TEST_ELASTIC_REQUIRED=true` fails rather than
    skips without one (`ElasticTestNode`).

  Run them as any `stroom-app` test, e.g.
  `./gradlew :stroom-app:test --tests 'stroom.mcp.impl.e2e.TestE2eTranslation'`.
* **Model evaluation.** `TestModelEvaluation` (in `stroom-ai-impl`'s tests) runs a small set of real prompts
  against a real model through the real agent loop, to compare the tools the agent chooses and what it answers
  before and after a change. It is not part of the build: it needs a model, costs tokens, and a model's answers
  vary from run to run, so compare reports rather than expecting every check to pass every time. It runs over a
  small fixed Stroom held in memory (`ModelEvaluationWorld`: feeds, a pipeline and its processor filter, an index,
  a dashboard, processor and server tasks, system properties, canned query results, and a fake MCP server whose
  index template tool asks for review), so no database or Stroom instance is needed.

  ```bash
  export STROOM_AI_EVAL_API_KEY=...   # Not needed for a local server without one.
  ./gradlew :stroom-ai:stroom-ai-impl:test --tests '*TestModelEvaluation*' \
    -DrunModelEvaluation=true \
    -DtestAiEvalBaseUrl=https://api.openai.com/v1/ \
    -DtestAiEvalModel=gpt-4.1
  ```

  * `-DtestAiEvalScenarios=pipeline-feeds,stroomql-query` runs only the scenarios named.
  * `-DtestAiEvalReportDir=...` writes the report elsewhere (the default is
    `stroom-ai/stroom-ai-impl/build/ai-eval/`).

  The scenarios are in `src/test/resources/stroom/ai/impl/eval/scenarios.json`: each has a prompt, the tab
  attached (`pipeline` or `dashboard`, or none), the user's application permissions, the tools expected to be
  called (`a|b` for either), and checks on what the user sees (the answer, and any `ask_user` questions) and on the
  tools' arguments. Each run writes `report-<time>.md` and `.json`, with the tools called per scenario and whether
  each check passed, and the test fails if any check failed.


## Configuration

All under `appConfig.ai` (`stroom.ai` in the Properties screen) unless stated; the classes are
`AiConfig`, `AiWorkspaceConfig`, `AiCompactionConfig`, `McpEndpointConfig`, `AiBuildsConfig`,
`AiSampleUploadsConfig`, `ErrorTriageConfig`, `AiPipelineStagesConfig` and `AiInstructionSuggestionsConfig` in
`stroom-ai-impl`. The generated
`stroom-config/stroom-config-app/src/test/resources/stroom/config/app/expected.yaml` has them all with their
defaults: regenerate it (`GenerateExpectedYaml`) when one changes.

| Property | Default | What it does |
|---|---|---|
| `allowApproveEverything` | `false` | Lets users turn on _Auto-approve_ (approve everything) for a chat |
| `askReadOnlyToolsAfterUntrustedContent` | `false` | Trusted MCP servers' read-only tools ask once a run has read untrusted data |
| `chatResponseCache.*` | `maximumSize: 1000`, `expireAfterWrite: PT10M` | Caches the `ai()` XSLT and StroomQL functions' answers (`maximumSize: 0` asks the model every time) |
| `chatRetention` | `P90D` | How long an unused chat is kept |
| `compaction.thresholdPercent` | `80` | How full the window (less the reply reserve) gets before a chat is compacted |
| `compaction.turnsKeptInFull` | `2` | The latest turns never compacted |
| `compaction.digestMaxResultRows` | `20` | Tool results of up to this many lines are copied exactly into a compacted turn's digest |
| `compaction.replyReserveTokens` | `4096` | Tokens of the window kept free for the model's reply |
| `elicitationTimeout` | `PT10M` | How long a form from an MCP server's tool waits |
| `maxChatTitleWords` | `12` | The most words in a chat title the model is asked for |
| `maxElicitationsPerNode` | `5` | Forms from MCP servers' tools that may wait on a node at once |
| `maxRunDuration` | `PT2H` | The longest a run may take |
| `maxRunsPerNode` | `20` | Runs working at once on a node |
| `maxRunsPerUser` | `3` | Runs (and heavy `/mcp` calls) working at once for a user |
| `maxTabSnapshotBytes` | `2097152` | The most bytes of an attached tab's content kept for one question |
| `maxToolIterations` | `100` | The most calls to the model for one question; on the last it is offered no tools, so it sums up |
| `shareActiveTab` | `true` | Lets users attach a document tab to a chat |
| `suggestFollowUps` | `true` | The model suggests follow-ups after answers, and questions about what is attached |
| `toolGroupThreshold` | `24` | More MCP tools than this are offered as groups (0 never groups); the built-in tools are grouped by `BuiltInToolGroups` |
| `unattendedAfter` | `PT10M` | When a run with no one watching becomes unattended |
| `workspaceFileRetention` | `P7D` | How long workspace files are kept |
| `workspace.maxFileBytes` | `20971520` | The largest workspace file |
| `workspace.maxChatBytes` | `104857600` | The most a chat's workspace may hold |
| `workspace.maxUserBytes` | `524288000` | The most all of a user's workspaces may hold |
| `workspace.maxLinesPerRead` | `500` | The most lines of a file a tool returns in one call |
| `workspace.maxCharsPerRead` | `20000` | The most characters a tool returns in one call |
| `workspace.toolTimeLimit` | `PT30S` | The longest a workspace tool call may run |
| `workspace.queryTimeLimit` | `PT2M` | The longest a `run_stroomql` query may take |
| `workspace.maxQueryLength` | `10000` | The longest `run_stroomql` query, in characters |
| `workspace.maxQueryRows` | `10000` | The most rows `run_stroomql` returns (a larger `limit` is lowered to it) |
| `workspace.maxEventsPerFetch` | `500` | The most events `fetch_events` fetches in one call |
| `workspace.maxEventBytes` | `1048576` | The largest event `fetch_events` fetches |
| `workspace.maxStreamStoreReads` | `500` | The most streams `stream_meta` reads attributes for from the stream store in one call |
| `workspace.maxToolArgumentBytes` | `1048576` | The largest MCP tool call's arguments once `{"$workspace": ...}` references are filled in |
| `mcpEndpoint.enabled` | `true` | Whether `/mcp` serves MCP clients |
| `mcpEndpoint.formTimeout` | `PT10M` | How long a form asked of an MCP client's user waits |
| `mcpEndpoint.maxFormsPerNode` | `50` | Forms that may wait for MCP clients' users on a node at once (a user at most half) |
| `mcpEndpoint.keepAliveInterval` | `PT20S` | How often an MCP session's open stream is pinged |
| `mcpEndpoint.sessionRetention` | `P1D` | How long an idle MCP session, and its workspace, is kept |
| `mcpEndpoint.maxSessionsPerUser` | `20` | MCP sessions one user may have open on a node at once |
| `builds.workspaceFolder` | `MCP Workspace` | The folder below System in which the build tools make each build's folder |
| `sampleUploads.maxUploadMb` | `100` | The largest sample file that may be posted to `/mcp/upload` |
| `sampleUploads.commandsLast` | `PT15M` | How long `upload_sample`'s upload commands may be used |
| `sampleUploads.maxPendingPerUser` | `20` | Sets of upload commands one user may have waiting at once |
| `instructionSuggestions.evidenceWindow` | `P90D` | How far back `instruction_evidence` reads the user's own chats |
| `instructionSuggestions.minChats` | `2` | Chats that must make a choice the same way (and none another way) for `instruction_evidence` to call it repeated |
| `errorTriage.rules` | `[]` | The environment's own rules for triaging pipeline errors, before Stroom's (`error_rules.yaml`) |
| `pipelineStages.<stage>.*` | (see `expected.yaml`) | Per stage (translation, indexing, discovery, reference, records, forwarding): `templateFolders`, `templateNames`, and the `schemaGroups` and `streamTypes` that mark a pipeline as that stage |
| `db.*` | | The AI tables' database connection |

Under `appConfig.askStroomAi` (`AskStroomAiConfig`), the defaults for each user's own chat settings and the
table analysis settings: `dockType`, `dockLocation`, `dockSize`, `chatSystemPrompt`, `historySummaryPrompt`,
`maxHistorySafetyCapMessages` (200), `attachmentDownloadTimeoutMs` (60000), `enableDebugDetail`,
`showToolCalls`, `showReasoning`, `browserNotifications` (all `true`), and `tableAnalysis.*` (`maxTotalRows`
50000, `maxRowsPerBatch` 1000, `maxParallelBatches` 4, `maxRowsWithoutTools` 10000, and the batch and merge
prompts). Users change their own in the chat's settings; administrators change the defaults and table analysis
in the chat's **Settings** (see stroom-docs `ask-stroom-ai/setting-up.md`).


## Behaviour internals

How the user-visible behaviour described in stroom-docs is implemented, for maintainers. The user and admin side of
each is in stroom-docs: approvals, forms and steps for the user in `ask-stroom-ai/using-the-chat.md`; the
administration tools in `ask-stroom-ai/cluster-questions.md`; builds, hand edits and promotion in
`pipeline-admin/builds-and-promotion.md`; processing, reprocessing and verification in
`pipeline-admin/editing-pipelines.md`; the typed AGENTS sections in `pipeline-admin/configuration.md`.

### Approvals

* `ToolConsentGate` is the one place a call is approved; `ChatConsentGate` and `ToolConsent` decide for a chat,
  `McpApprovals` for `/mcp`. Calls that only read never ask. A call that changes something runs without asking until
  the chat has read untrusted content: an attached tab or table, the result (in this run or stored from an earlier
  one) of any tool whose `StroomTool.readsUntrustedContent()` is true (nearly all of Stroom's tools, and every MCP
  tool), and workspace files put into an MCP tool's arguments. A stored result whose tool can't be told (an old row
  without the tool's name, or a tool since removed) counts, as does a chat with no run on this node. The same signal
  makes a change to the user's memory ask.
* A tool that confirms each change itself (`StroomTool.confirmsItself`), or a trusted MCP server's tool marked
  `_meta.confirms`, isn't approved as a whole as well. An _Ask every time_ policy still asks.
* Answers are recorded by `ToolApprovalService` with a compare-and-set (two tabs answering at once: one counts);
  requests expire after 24 hours. Every tool call and decision is event-logged by `AiAuditLog`.

### Forms and steps for the user

* `ElicitationSchema` holds the form limits (2,000-character message, 20 fields, 16,000-character schema). A form
  from an MCP server over them is answered with an error (invalid params), not a decline. Stroom's own tools check
  their forms first (`Consent`) and ask in the chat when one is too large.
* Steps for the user (`structuredContent.user_actions`) are checked and run by `UserActions` and
  `UserActionService`: the four kinds an MCP server may offer, the allowed Elasticsearch requests, re-checks on
  click, 24-hour expiry, and auditing as `AskStroomAi.userAction`.

### The chat

* The list of chats is searched with `POST /ai/v1/searchChats` (at least 2 characters, at most 50 chats, sent once
  typing pauses for 300 ms).
* Questions about what is attached come first from what each kind of thing offers; with a conversation or several
  things attached, the model is asked (`POST /ai/v1/suggestPrompts`), given only the latest two questions, the start
  of the latest answer and a two-line hint of each thing attached (never its content).
* A dashboard proposal is applied unsaved, all or none; _Undo apply_ puts back exactly what it changed. A code or
  query proposal made against an older tab is merged line by line onto changes applied with the chat's buttons, and
  refused if it touches lines they changed.

### Administration tools

* `ProcessorTasksTool`: counts come from the database's task summaries; Error streams are looked for per pipeline
  (the scoped ones, or the 20 with most failures), at most 200 each, and parsed into markers as the data viewer does
  (`MarkerListCreator`). `ServerTasksTool` reads every enabled node through `TaskResource`, waiting 15 seconds.
  `ConfigPropertiesTool` explains values as `ConfigMapper` resolves them and masks with `ConfigValueMasker`.
  `ElasticIndexTemplatesTool`'s output is treated as untrusted. `VolumesTool` (`VolumeReport`) and
  `ExplainRetentionTool` state Stroom's behaviour from fixed text checked against its code.
* Searches are logged as Search events and reads as View events.

### `/mcp`

* The form shown for a call that changes something leads with what the call acts on; arguments past 1,500
  characters are cut, saying so. A client that doesn't declare the `elicitation` capability is refused such calls
  but still offered the tools. The model is never given an id to approve a call with, since a model holding one
  could approve its own call.
* A call is worked on by the thread serving its request, within the longer of `workspace.toolTimeLimit` and
  `workspace.queryTimeLimit` (time waiting for the user doesn't count), and cancelled by `notifications/cancelled`,
  the end of its HTTP request or of its session. Heavy calls take one of the user's run slots, given back while a
  call waits for the user.
* Any request for a session that isn't the user's is answered `404`, as for a session the node doesn't have.
* `upload_sample`'s commands single-quote the file's path (a path with a line break or control character is
  refused) and post the body as `application/octet-stream`. Waiting uploads are held in the database, so a post may
  reach any node; a claim left by a node that stopped part way may be taken again after 10 minutes.
* The server instructions are sent when a client connects, and the guides and convention profiles are offered as
  resources (`stroom://guide/<name>`, `stroom://conventions/<name>`). The chat is given the same instructions once
  it is offered the build tools.

### Builds

* `WriteGuard` creates only in the build folder and changes only documents tagged `mcp-managed`. The build record
  (`BuildRecords`, a Documentation doc named `Build record`) holds working copies, the code digests that stepped
  clean, were verified or validated, and sample-file feeds; earlier versions' `mcp-build-*`, `mcp-copy-of-*` and
  `mcp-stepped-*` tags are folded into it on first read and removed.
* Test feeds for builds on existing feeds: `OutputFeeds` (the StreamAppender's `feed` property, taken off at
  promotion). Version history: `XsltVersionHistory`. Typed AGENTS sections: `InstructionRules`.


## Roadmap

Planned, not yet built. Decisions and status are in [DESIGN.md](DESIGN.md).

* **The remaining dashboard widgets.** The agent can create a dashboard (a query, a table and a data preview),
  change its tables, a classic query's expression, formatting, selection filters and component settings, add
  tables as tabs, Text widgets and charts. Still to come: the inputs (text, list, key-value), the Embedded
  Query's settings, removing and moving components, and laying out widgets beyond a tab beside a table or a pane
  below it.
* **Charts.** Built: `list_visualisations` and `propose_visualisation` chart a table or a StroomQL query (bar,
  line, pie, scatter, network graph, heatmap, calendar, histogram, Sankey, chord, gauge and sunburst) with ECharts
  or D3 installed as Script docs, drawn by Stroom's own adapter scripts so no code from the model runs in the
  visualisation frame; the content pack's D3 Visualisations are wired as they are. Still to come: other libraries
  (e.g. Chart.js, Highcharts), and sandboxing the visualisation frame.
* **Models per task.** A default model, with an administrator able to set others for kinds of work (tool calling,
  code such as XSLT, writing). The likely shape: a small utility model for side calls (titles, follow-ups,
  summaries, digests); choosing the model for a question when it starts, shown and overridable; and tools that
  hand a piece of work to a specialist model. Open: one server or separate endpoints, and whether these override
  a user's own choice of model.
* **Document settings in configuration answers.** `config_properties` covers system properties only, so an answer
  about what affects behaviour (e.g. Elasticsearch query timeouts) can miss the settings of the documents
  involved, such as an Elastic Cluster's timeouts. Planned: its search also looks through the settings fields of
  every document type (found from each document class's serialised fields), giving the values of documents the
  user can view, secrets masked; `@JsonPropertyDescription` on the settings that matter; and guidance to check both.
* **Suggested standing instructions.** Built (`instruction_evidence`, `propose_instructions`, the nudge at
  promotion; see DESIGN.md). Still to do: try the review and apply on an AGENTS doc, and creating one from the
  button, in a browser.
