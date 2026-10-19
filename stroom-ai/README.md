# Ask Stroom AI: operator guide

Ask Stroom AI is a chat panel in the Stroom UI. Users ask questions about their data, and an AI model answers,
using built-in tools (to query Stroom, and to work with files in a per-chat workspace) and the tools of any
MCP servers the model is given, such as the Stroom FastMCP Server.

This guide covers setting it up and running it safely. See GitHub issue
[#5848](https://github.com/gchq/stroom/issues/5848) for the design.


## Setting up a model

1. Create an **OpenAI Model** document (any OpenAI-compatible API, including self-hosted ones such as vLLM).
1. On its **Settings** tab, set the base URL, the API key (as a credential), the model ID and the model's
   context window (_Maximum context window tokens_).
   * _Tools enabled_: turn off for models that can't call tools.
   * _Show reasoning_: whether users see the model's reasoning, when it returns any. On by default.
   * _Maximum tools per request_: a limit on the tool definitions sent in one request, or 0 for none.
   * _Set Http Client Config_ → **Headers**: headers sent with every request to the model, e.g.
     `anthropic-version` for Anthropic's OpenAI-compatible endpoint. Don't put secrets here; the API key
     belongs in the credential. MCP tool servers have the same setting, and their authentication headers
     take precedence over it.
1. Users choose the model in the chat panel's settings, or an administrator sets the default with
   _Set As Default_.

Users who have not changed a chat setting use the configured defaults, so a change to the defaults reaches
them. A user whose settings have no model, or a model that no longer exists, gets the default model. To put
everyone back on the defaults (model, prompts, dock position and so on), an administrator (Manage
Properties) can use _Reset Everyone to Defaults…_ in the chat's **Settings**.

Answers and reasoning stream into the chat as the model writes them.

Each chat has a mode, chosen beside the model:

* _Agent_: does what is asked, with all the tools it is given.
* _Ask_: answers questions, with only the tools that cannot change anything.
* _Plan_: works out a step-by-step plan, with only the tools that cannot change anything, without
  carrying it out. The user can then switch to _Agent_ to do it.

A run carried on later (after an approval, a form or a resume) keeps the chat's mode.

For testing without a model, set the model ID to `__stub__`. The stub answers instantly, and a question can
include `[[think]]` (to get some reasoning) or `[[tool:NAME {json arguments}]]` (to call a tool first).


## MCP tool servers

On the model's **MCP Tool Servers** tab, add each server's Streamable HTTP endpoint, for example
`https://fastmcp.example.com/mcp`. Each chat keeps its own connection to each server, closed when idle.

* **Authentication**
  * _OAuth2 passthrough_ signs each user in to the server, so the server acts as them.
    * With an **external identity provider**, the user's own sign-in token is passed through. The token's
      audience must include both Stroom and the MCP server.
    * With Stroom's **internal identity provider**, Stroom issues each user a short-lived token (5 minutes,
      reused until it has a minute left) for the server's **Token audience**, e.g. `stroom-mcp`. It is only
      sent to a trusted server, over https or on the same machine. The token belongs to the grant of the
      user's sign-in, so it is revoked when they sign out or their session ends, and it shows in the
      identity provider's token inventory (scope `openid stroom-delegated`). Configure the server (e.g.
      FastMCP's JWT verifier) with Stroom's issuer and JWKS URL, both given by
      `<stroom>/api/oauth2/v1/.well-known/openid-configuration` (`issuer`, `jwks_uri`), and the audience.
      For the server to call Stroom's API back with the token, add the audience to
      `appConfig.security.authentication.openId.allowedAudiences`.
  * _None_, _Bearer token_, _Custom API key header_ and _Basic auth_ use one shared credential, so every
    user's calls run as one account. Use them for development, or for servers that do their own checks.
* **Test Connection** (General tab) connects with the settings as they are in the dialog and lists the
  tools the server would offer. It needs EDIT permission on the model.
* **Trusted server** (General tab): only trusted servers' tool annotations (read-only, destructive, open-world) are
  believed, and only their instructions are given to the model. Mark a server trusted only if you control
  it.
* **Server instructions**: what a trusted server says, when connected, about how to use it (for FastMCP,
  the order of work and its rules) is added to the system prompt while the model is offered any of its
  tools, up to 20,000 characters per server. The context meter shows what it takes.
* **Tool policies** (Tool Policies tab): a list of a regular expression and a policy, added with **+**. The pattern
  must match the tool's whole name, ignoring case, e.g. `find_.*` or `(get|list)_feeds?`, and the first
  policy to match applies: _Allow_ (runs without asking), _Ask every time_, or _Deny_ (never offered to the
  model). Tools no policy matches run without asking if the server is trusted and the tool is read-only,
  and otherwise ask.
* **Tool groups**: when a model would be offered more MCP tools than `toolGroupThreshold`, they are offered
  as groups, each with one `activate_<group>` tool, and a group's tools are only sent to the model once it
  turns the group on. Groups come from the server (`_meta.group`), otherwise from shared name prefixes
  (e.g. `pipeline_*`), otherwise one per server.

## Approvals

Before a tool runs, Stroom decides whether to ask the user:

* Built-in tools run without asking.
* MCP tools on untrusted servers always ask. On trusted servers, read-only tools run without asking, and so
  do tools that modify data but aren't destructive and don't reach outside Stroom, until the run has read
  an untrusted MCP server's output, which could hold planted instructions (output from trusted servers,
  built-in tools and attached tables doesn't count); destructive and open-world tools ask. A tool policy can
  say otherwise.
* Approval cards classify each tool (read-only, can modify, destructive, reaches outside Stroom, untrusted
  server, after reading data); each chip's tooltip explains what it means.
* A trusted server's tool marked `_meta.confirms` (it asks the user itself, through a form, as FastMCP's
  changing tools do) runs without Stroom asking as well, so the user isn't asked twice. An _Ask every
  time_ policy still asks.
* The user can allow a tool for the rest of the chat, or all of a trusted server's read-only tools.
  A change to a tool's name or description, or to the server's settings, asks again.
* _Approve everything_ (per chat: the shield beside the model, or **Tool Approvals**) is off unless
  `allowApproveEverything` is true. The shield is highlighted while it is on. It lets trusted servers'
  tools that modify data keep running without asking after the run has read an untrusted server's output; it
  never covers
  destructive or open-world tools.
* If no browser has polled the chat for `unattendedAfter`, the run is unattended: only read-only tools
  run, and anything else waits for the user.
* A read-only tool changes nothing, but its arguments still go to its server. Set
  `askReadOnlyToolsAfterUntrustedContent` to make trusted servers' read-only tools ask too, once a run has
  read data from tools or attached tables that could hold planted instructions. A tool the user has allowed
  for the chat still runs.
* Approval cards show each call's full arguments. Denying can give a reason, which the model is told.
* A run waiting for an approval gives up its thread, and carries on when the user answers. Requests
  expire after 24 hours.

Every tool call and every decision is event-logged.


## Questions for the user (`ask_user`)

When the agent can't carry on without the user deciding something (e.g. which feed to use, or whether to replace
an existing pipeline), it asks with its built-in `ask_user` tool rather than listing questions at the end of its
answer and stopping. The run waits for the answers and then carries on with them, so a task with several steps,
such as onboarding a feed, isn't broken up into separate questions the user has to piece back together.

The chat shows the questions in a card, one at a time ("Question 1 of 4"). Each comes with the answers the agent
thinks likely, as buttons to click, with the one it recommends marked and shown first, and, unless the agent says
only those answers make sense, a box for the user's own answer (Enter to give it). _Back_ returns to the previous question; _Skip these questions_
declines them. After the last answer, the answers are the tool's result, and the card shrinks to the questions and
answers. The questions use the same machinery as forms from MCP tools (below): the run waits, Stop cancels them, and
they expire after `elicitationTimeout`. An unattended run isn't asked; the agent is told to put the questions in its
answer instead.

## Forms from MCP tools (elicitation)

An MCP tool can ask the user to fill in a form while it runs. The chat shows the form in a card, below what the
model wrote before calling the tool, with _Submit_ (or Enter in a field) and _Decline_. Fields start with the
values the server suggests (its schema's `default`, e.g. a proposed pipeline name). An answer that doesn't fit the
form (e.g. a required field left empty) is refused, and the form stays to be put right. Once answered, the card
shrinks to the question's first line and the answer given. Stopping the run cancels the form, and the card says so (in another tab too). The answer goes to the MCP server, never to the model. The tool call keeps its thread while it waits, so:

* _Stop_ gives up on an MCP tool call at once (its client can't cancel it, so the server may still finish what it
  started), rather than when it returns;

* forms expire after `elicitationTimeout` (default 10 minutes);
* at most `maxElicitationsPerNode` (default 5) wait on a node at once, and more are declined;
* only flat forms are shown (text, numbers, true/false, a choice of one or several); others are declined.


## Steps for the user (user actions)

Some steps are the user's to take, such as enabling a processor filter. A trusted MCP server's tool result
can list them (`structuredContent.user_actions`, as FastMCP does), and the chat shows them as buttons in a
_Steps for you_ card. The model can neither make nor click one.

* From an MCP server, only four actions are shown: _Enable_ or _Disable_ a processor filter, _Open_ a
  document, and an Elasticsearch request for the cluster admin. Anything else is dropped.
* Stroom's own tools add buttons that create documents or change what the user has open: _Create query…_,
  _Create dashboard…_, _Apply to query_, _Apply to dashboard_ and _Review and apply_ (see _Attaching a tab_).
  An MCP server can't offer these, even by naming them.
* A proposed query is shown as a code block with a copy button. A proposed classic dashboard is shown as a
  list of what its Query and Table will hold instead, as its StroomQL isn't what it will hold.
* Stroom writes each button's text itself, from the filter as it is (pipeline, selection, task limit), and
  warns if the pipeline isn't tagged `mcp-generated`. The server's own wording is only shown for "Do first"
  conditions, as plain text.
* When clicked, the step is checked again (the filter exists, is on that pipeline and still needs the
  change), then done as the user, so their permissions apply. Each button works for 24 hours, only for the
  chat's owner, and each click is audited (`AskStroomAi.userAction`). A button that changes something works
  once, and a note goes into the chat, so the model knows on its next turn. _Open_ and the create buttons
  change nothing themselves (the user creates the document in Stroom's dialog), so they can be used again,
  e.g. after the dialog was cancelled.
* An Elasticsearch request is shown read-only, with a copy button, only if it is `PUT _index_template/<name>`,
  `DELETE _data_stream/<index>` or `DELETE <index>` (no wildcards, lists or `_all`), for an index of an
  Elastic Index document used by a pipeline in the same result. Stroom never runs it.
* A run carries on without waiting for buttons; nothing happens until the user clicks.


## Commands, prompts and uploads

* Typing `/` in the input box lists the commands. The chat's own are `/compact [focus]`,
  `/mode agent|ask|plan`, `/plan <task>` (switches to Plan mode and asks), `/new [question]`, `/clear`
  (deletes the chat's messages, attached data, workspace files and memory, keeping the chat, its mode and
  its tool approvals), `/remember <fact>` (adds the fact to the user's memory, in `remembered.md`) and
  `/help`. A message starting with `//` is sent as it is, starting with `/`.
* The list of chats can be searched, as you type, by the chats' titles and the user's questions in them
  (`POST /ai/v1/searchChats`; at least 2 characters, at most 50 chats). A search is only sent once the user
  pauses typing for 300ms, so typing doesn't load the database with a query per key. Matches are highlighted,
  and a chat found by a question shows up to 3 lines of the latest matching question. Escape clears the search.
* A chat is named after its first question, then, after the answers to its 1st, 3rd, 6th and 10th questions
  (and every 10th after), the chat's model is asked for a title of at most `stroom.ai.maxChatTitleWords`
  words (12 by default; a longer reply is cut to that many). Titles are cut short with an ellipsis only where
  they would not fit on one line. Only the user's questions
  are sent. Once the user renames a chat, it is no longer named automatically; `/clear` lets it be again.
* For work with several steps (e.g. onboarding a feed: create the feed, upload sample data, build and test a
  pipeline, set up indexing), the agent writes a task list with its `task_list` tool before it starts, and keeps it
  up to date as it works, so the user can see the plan, how far it has got and what is left. The list is shown
  above the input, with how many steps are done, and each step marked as not started, in progress or completed;
  it can be folded away. Each update is kept (as a `TASK_LIST` message, never sent to the model), so
  it survives a refresh; a new question clears it.
* After an answer, up to three things the user might ask next are offered as links floating over the bottom of
  the conversation, just above the input (the conversation keeps room below its latest message, so they never
  hide it); clicking one sends it. They come from one small call to the chat's model, given only the question and the
  start of the answer, and are kept (as a `SUGGESTIONS` message, never shown in the conversation or sent to
  the model) so they survive a refresh. None are offered while the chat is working, once the user asks
  something else, or after a turn in which the agent asked the user for something (a form, an approval, or
  steps to carry out). `stroom.ai.suggestFollowUps: false` turns them off.
* When the user attaches something (a tab, a document from the Explorer, or a table), up to three questions about
  it are offered at once, from what that kind of thing offers (e.g. a feed: what it has received, which pipelines
  process it, processing errors; a query with no results: why), drawn from everything attached, the most
  recently attached first. Removing something offers them again for what is left (or the follow-ups to the latest
  answer, with nothing left). If the chat already has a conversation, or several things are attached, the chat's
  model is then asked (`POST /ai/v1/suggestPrompts`) for suggestions that also fit what the user has been asking
  and use the attached things together, given only the latest two questions, the start of the latest
  answer and a two-line hint of each thing attached (what it is and what the agent can do with it, never its
  content; documents the user can't view are left out). Follow-ups after an answer get the same hints for the
  attached tab and documents, and lean towards acting on them. `suggestFollowUps: false` turns off the model's
  suggestions of both kinds; the ones made at once, without the model, are still offered.
* A message sent while the chat works steers the run. It is shown queued (a dashed outline, "Waiting for the
  current step to finish") and the run picks it up once its current step finishes, before its next call to the
  model; it is then shown as any other message. _Send now_ (the fast-forward button on it) has the run give up
  waiting for a tool from an MCP server (within about 2 seconds; the server may still finish what it started,
  and the model is told so) and skip the rest of the step's tool calls. Built-in tools, which limit their own
  time, and a reply the model is writing, are not cut short. A message the run never picks up (e.g. it was
  stopped) stays in the conversation, as sent, for the next run.
* A message sent while the chat waits for the user to fill in a form or approve a tool call answers it instead:
  the form is put aside, or the calls are denied with the message as the reason, and the run carries on.
* A message sent just after an answer appears, while the run is still finishing (e.g. naming the chat), is
  answered as the next question in the same run, rather than being taken as steering a run that has
  already answered.
* The prompts MCP servers offer are slash commands too, e.g. `/onboard_data_source feed=ACME`.
  The filled-in prompt is put in the input box for the user to check and send.
* A link in the chat to a Stroom document (`?action=open-doc&docType=…&docUuid=…`, as FastMCP returns) opens
  the document in the current Stroom session, whatever host the link names, so set FastMCP's
  `STROOM_MCP_STROOM_UI_URL` to Stroom's public address for links copied elsewhere. Other links open in a
  new browser tab.
* Nothing is uploaded to an MCP server through the chat. Sample files for a new feed go through Stroom's
  own data upload, on the feed's Data tab.


## Context, memory and compaction

* The context meter beside the model shows how much of the model's context window the chat uses. While a question
  is being answered, it shows what the model was sent for the latest call (as the model reported it) and what has
  been added since, never more than the window; its tooltip says when the run has shortened earlier steps to fit,
  so a breakdown larger than the figure (the parts as stored) is explained. Otherwise it is an estimate ("≈") of
  what the next question would use.
* When a chat reaches `compaction.thresholdPercent` of the window, earlier turns are replaced (for the
  model only) by short digests, keeping the last `compaction.turnsKeptInFull` turns. Users can compact
  with `/compact [focus]`, and undo the latest compaction.
* A long run (e.g. onboarding a feed) fills the window with its own steps, which compaction doesn't cover.
  Before each call to the model, if the run is getting too large, it is made smaller a step at a time, least
  lossy first: earlier tool results, the arguments of earlier tool calls (e.g. whole code proposed), all but
  the latest result, then earlier steps put as a line or two each in a note. The latest step is always kept,
  and everything stays stored: the model can read it again with `read_turn`.
* Token counts are estimated (characters / 4, corrected by what the model reported for the chat's last call;
  25% more until a call has been measured, as XML and JSON take more tokens than that). Compaction and the
  run's own shortening use the same estimate, so a chat is compacted when a question starts before the run would
  have to shorten its steps. If the model says a
  request is too large anyway, the estimate is corrected from the size it gave (e.g. vLLM's "135904 in the
  messages"), the run is made smaller again and the call made again, up to 4 times, so the run carries on
  where it was. Only if that fails is the chat compacted as far as it will go and the run started again, with
  the model told to carry on from its steps. A reply cut off as the window filled up isn't taken as the
  answer: room is made and the model is told to carry on.
* A rate limit (HTTP 429) is never taken as the context being too large, even if it mentions tokens. The
  _Request detail_ entry of an attempt that overflowed (with _Show request detail_ on) shows what the model
  said.
* The agent has memory: the user's own (`/memories/`, loaded into every chat) and the chat's
  (`/memories/session/`). Changes to the user's memory from a chat that has read tool output or data need
  the user's approval. Users view, edit and undo changes in **Memory**.


## Attaching a tab

The chat only sees a tab the user has attached to it. Nothing is shared just because a tab is open or selected, so
moving between tabs (e.g. from one dashboard to another while a question is being worked on) never changes what
the agent is looking at without the user choosing it:

* Above the input, a dashed _+_ chip names the document tab the user is on (or, when the chat is open as a tab of
  its own, the one they were on before). Clicking it attaches that tab to the chat, in place of any attached
  before; one tab is attached at a time.
* The attached tab is shown as a chip, "Tab: _name_" with its icon, and "(unsaved)" while it has unsaved changes.
  It stays attached whichever tab the user is on, until they remove it (× on the chip) or close the tab.
  Attachments are kept per chat, in the browser, until the page is reloaded.
* Each question is sent with the attached tab as the user sees it then: unsaved changes, the inner tab shown
  (e.g. a pipeline's _Stepping_ view, with the selected element's input, output and code), and the tables of
  results shown. A tab that hasn't changed since the last question is sent as its hash only. The agent is
  given it as a snapshot in `/tabs/<n>/` with a note on the latest question; earlier questions keep a short
  note of where theirs is.
* Attaching a tab moves the cursor to the input box and offers questions about it (see _Commands, prompts and
  uploads_). The tab's chip is set apart from the chips of attached documents and tables by a divider: the tab
  stays attached for every question, while documents and tables go with the next one.
* Editing a question, or _Ask again_, sends the attached tab as it is now.
* Only documents the user can view are shared. `shareActiveTab: false` turns attaching tabs off.

What the agent can offer for the attached tab, all as buttons the user clicks, never saved by the agent:

* A StroomQL Query: _Apply to query_ (the revised query in the editor, unsaved, with _Undo apply_).
* A dashboard: _Apply to dashboard_, with _Undo apply_. A proposal is for one of its tables, and can also
  change the dashboard's other components. It can:
  * add, edit (name, expression, format), move, hide or show columns; group, sort and filter them. Column
    expressions are in Stroom's expression language, checked against the data source's fields, so the agent
    can derive what the user means (e.g. `if()`, `match()`, `decode()`, `formatDate()`) rather than only show
    raw fields;
  * set the expression of the classic query that feeds the table. The agent writes the conditions as a
    StroomQL `where` clause has them; Stroom turns them into the expression, checks them against the data
    source, and the card shows them in words. The agent never shows StroomQL for a classic query;
  * add tables beside it as tabs, fed by the same query, starting with its columns or none, which the
    changes after them set up, e.g. a tab per event category, each with columns that suit it and a filter
    on the category column;
  * add and remove conditional formatting rules: rows matching conditions on the table's columns (all or
    any of them) get a background or text colour from the rule editor's palette, bold or italic, or are
    hidden. The dashboard file lists each table's rules, numbered, so the agent can remove one;
  * add a Text widget in a pane below the table (or a table added before it), showing the event of the
    selected row, which later changes can set up (e.g. the pipeline it shows the data through, or its
    stepping button). A grouped row has no single event, so on a grouped table the Text widget shows nothing
    and stepping can't start until the table's detail rows are on; the agent is told to say so and offer to
    turn them on;
  * rename any component (and so its tab), and change the settings of any table, query or text component,
    as its Settings dialog has them:
    * table: rows per page, row limits per group level, detail rows, value extraction and its pipeline, the
      row of column filters, the query that feeds it, and the longest text a cell shows;
    * query: data source, run when the dashboard opens, refresh on a timer and how often;
    * text: the table it follows (or any), the pipeline it shows data through (or none), HTML, and the
      stepping button.

    Each value is checked first (a pipeline the user can view, a data source they can use, a component the
    dashboard has), and the dashboard file the agent reads lists each component's settings as they are.

  Changes are applied unsaved, on top of any unsaved changes the dashboard has (the agent proposed them
  against the dashboard as the user has it), and all or none. _Undo apply_ puts back exactly what they
  changed (the table, the query's expression, and each component's name and settings) and removes any tables
  or Text widgets added, while the dashboard is open and hasn't been changed since; otherwise it is withdrawn,
  saying why.

  These are for classic dashboards (a Query and its Tables). On a StroomQL dashboard, the Embedded Query's
  table follows its StroomQL, which the agent can propose as a new dashboard or query instead.
* A pipeline being stepped: a change to the selected XSLT or text converter, as a diff with _Review and
  apply_. The agent gives either the whole revised code or just the text to replace and its replacement, so a
  small change (e.g. adding `normalize-space()`) doesn't mean copying the whole XSLT. Before it is offered, the
  change is tried by stepping the record with it, and refused if it gives errors the current code doesn't.
* `current_results` takes a snapshot of the rows of a table the tab shows.

The agent can also offer new documents, whichever tab is attached, as buttons that open Stroom's own New
Query or New Dashboard dialog with a suggested name. Nothing is created unless the user confirms the dialog,
which checks their permission to create the document in the folder they choose and logs it as any new
document is logged.

Once created, the new document opens and is attached to the chat in place of any tab attached before, so
the user can carry on with it there, e.g. asking the agent to refine it (with _Apply to query_ or _Apply to
dashboard_).

* _Create query…_: a StroomQL Query holding the agent's query.
* _Create dashboard…_: a dashboard built from a StroomQL query the agent writes, with a query, a table and,
  if the user wants one, a data preview (a Text widget showing the event of the selected row). Before
  offering it, the agent asks, in one form:
  * whether the query should be StroomQL or Expression Language (classic);
  * which fields the table should show, unless the request says, offering sets of fields from the data
    source to choose from;
  * whether to add a data preview;
  * which data source to use, if more than one could hold what was asked for.

  A StroomQL dashboard holds the query in an Embedded Query, which shows its own table. For a classic
  dashboard, Stroom turns the query's `where` into the Query's expression, and its `select` (with grouping,
  sorting and `limit`) into the Table's columns, extracting values with the data source's default extraction
  pipeline; the button's card then lists what the Query and Table will hold (the expression in words, the
  columns, grouping, sorting and row limit) rather than the StroomQL they come from, while a StroomQL
  dashboard's card shows its query. A classic dashboard can't hold StroomQL's `window`, `filter`, `having`
  or `show` clauses, and a data preview needs ungrouped rows; the agent is told why when either doesn't fit.
  The settings are built again from the query when the button is clicked, so they follow the data source as
  it is then.


## Attached tables

Tables attached to a chat (from a dashboard, query or table) are downloaded into the chat's workspace, up
to `tableAnalysis.maxTotalRows` rows (default 50,000).

* A model with tools is given each table's profile and where it is, and works out figures with its tools.
* A model without tools is given the first `tableAnalysis.maxRowsWithoutTools` rows (default 10,000), and
  told when there are more. If they don't fit its context window, the tables are analysed in batches (no
  more than `maxRowsPerBatch` rows, or half the context window, each), with the chat so far as compacted,
  and the partial answers are merged into the answer.
* These settings, and the batch and merge prompts, apply to everyone. Administrators change them on the
  **Table Analysis** tab of the chat's **Settings**, which other users don't see.

### The events behind the rows

A dashboard or StroomQL table that isn't grouped is attached with each row's `StreamId` and `EventId` (from
the hidden columns Stroom adds), as are the agent's own `run_stroomql` results. Where they are empty in every
row (a data source without streams, such as reference data), they are left out. A table that already shows its
own StreamId or EventId column isn't given a second. The agent can then look into
the events themselves, e.g. for a value the table doesn't show:

* `fetch_events` reads the event behind each row of a table (or of a CSV made from one with the `table`
  tool, to pick rows) into one XML file in the workspace, each event in a
  `<stroom-event streamId=".." eventId="..">` element. Events are read as the user, who needs the _View Data_
  permission; streams of feeds they can't see are left out. An event that isn't well-formed XML (or has a
  DOCTYPE) is kept as text. At most `workspace.maxEventsPerFetch` events (default 500) are fetched, each
  up to `workspace.maxEventBytes` (default 1 MiB). Each stream is opened once and its events read in order;
  on local disk 500 events take about a third of a second.
* `search_events` evaluates an XPath against each event on its own and/or finds text in each, and writes a
  CSV of `StreamId`, `EventId` and the result, or adds the result as a column of the original table (`join`),
  for the `table` tool to count or group.


## Retention and limits

The _AI Chat Retention_ job (hourly) removes:

* workspace files older than `workspaceFileRetention` (default 7 days);
* chats not updated for `chatRetention` (default 90 days);
* the chats and memory of deleted users.

Files made from Stroom data are checked against the user's permissions each time they are used, and removed if
the user can no longer see the data.

`workspace.*` limits file sizes and how much a tool can read or run for. `maxRunsPerUser`, `maxRunsPerNode` and
`maxRunDuration` limit runs.


## Configuration

All under `appConfig.ai` unless stated.

| Property | Default | What it does |
|---|---|---|
| `allowApproveEverything` | `false` | Lets users turn on _approve everything_ for a chat |
| `askReadOnlyToolsAfterUntrustedContent` | `false` | Trusted servers' read-only tools ask once a run has read untrusted data |
| `chatRetention` | `P90D` | How long an unused chat is kept |
| `compaction.thresholdPercent` | `80` | When a chat is compacted automatically |
| `compaction.turnsKeptInFull` | `2` | The latest turns never compacted |
| `elicitationTimeout` | `PT10M` | How long a form from an MCP tool waits |
| `maxElicitationsPerNode` | `5` | Forms that may wait on a node at once |
| `maxRunDuration` | `PT2H` | The longest a run may take |
| `maxRunsPerNode` | `20` | Runs working at once on a node |
| `maxRunsPerUser` | `3` | Runs working at once for a user |
| `maxTabSnapshotBytes` | `2097152` | The most bytes of an attached tab's content kept for one question |
| `shareActiveTab` | `true` | Lets users attach a document tab to a chat |
| `suggestFollowUps` | `true` | The model suggests follow-ups after answers, and questions about what is attached |
| `toolGroupThreshold` | `24` | More MCP tools than this are offered as groups (0 never groups) |
| `unattendedAfter` | `PT10M` | When a run with no one watching becomes unattended |
| `workspace.maxQueryLength` | `10000` | The longest StroomQL query `run_stroomql` may run, in characters |
| `workspace.queryTimeLimit` | `PT2M` | The longest a `run_stroomql` query may take |
| `workspace.maxEventsPerFetch` | `500` | The most source events `fetch_events` fetches in one call |
| `workspace.maxEventBytes` | `1048576` | The largest source event `fetch_events` fetches, in bytes |
| `workspaceFileRetention` | `P7D` | How long workspace files are kept |
| `appConfig.askStroomAi.browserNotifications` | `true` | Browser notifications when a chat finishes or needs the user |
| `appConfig.askStroomAi.showToolCalls` | `true` | Shows tool calls in each turn's activity (only hides them when off) |
| `appConfig.askStroomAi.showReasoning` | `true` | Shows the model's reasoning in each turn's activity |
| `appConfig.askStroomAi.enableDebugDetail` | `true` | Keeps and shows each answer's request detail |

The `appConfig.askStroomAi` settings are the defaults for each user's own settings, which they change in the
chat's settings (_Show tool calls_, _Show reasoning_, _Show request detail_, _Enable browser notifications_).
Turning off tool calls or reasoning only hides them, in past turns too, and turning them on again shows them;
the agent works the same either way. A model's own _Show reasoning_ setting decides whether its reasoning is
kept at all. Turning off request detail stops it being kept for later answers, and hides what was kept.


## Troubleshooting

* **"The tools of MCP tool server ... are unavailable"**: the server could not be reached, or its
  authentication isn't set up, e.g. OAuth2 passthrough with the internal identity provider needs a token
  audience, a trusted server and https. The chat still works with the built-in tools.
* **A chat shows "Interrupted because the Stroom node running it stopped"**: the node running it went
  away. _Resume_ carries on from the last question.
* **Nothing streams**: some OpenAI-compatible servers don't stream; the answer then arrives whole.


## Roadmap

Planned, not yet built.

* **The remaining dashboard widget types.** The agent can create a dashboard (a query, a table and a data
  preview), change its tables, a classic query's expression, formatting and component settings, and add
  tables as tabs and Text widgets below them. Still to come:
  * Visualisations: adding one from a Visualisation already in Stroom, set up for a table's columns (its
    `field` settings mapped to columns, its other settings given values), and changing an existing one's
    settings. On a StroomQL dashboard, the `show` clause already does this.
  * New visualisations from scratch: a Visualisation document with its Script and settings. Stroom ships no
    chart libraries itself; the visualisations content pack provides them as Scripts (D3 3.4.8, so its
    version 3 API, with Chroma, Leaflet, Underscore and shared helper scripts). A script runs in the
    dashboard's visualisation frame, which isn't sandboxed, so it would have the user's Stroom session: it
    would have to be reviewed before it is created, and tried against a snapshot of the table's rows first.
  * The other widgets: the inputs (text, list, key-value), the table filter, the Embedded Query's settings,
    and their links to tables and queries; and laying out widgets beyond a tab beside a table or a pane
    below it.
* **Models per task.** A default model, with an administrator able to set others for kinds of work (tool
  calling, code such as XSLT, writing). Rather than swapping the model mid-answer, the likely shape is: a
  small utility model for side calls (titles, follow-ups, summaries, digests); choosing the model for a
  question when it starts, from what is attached and the prompt used, shown and overridable; and tools that
  hand a piece of work (e.g. writing code) to a specialist model. Open questions: one server or separate
  endpoints, and whether these override a user's own choice of model.
