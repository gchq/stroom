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
* **Trusted** (Tools tab): only trusted servers' tool annotations (read-only, destructive, open-world) are
  believed, and only their instructions are given to the model. Mark a server trusted only if you control
  it.
* **Server instructions**: what a trusted server says, when connected, about how to use it (for FastMCP,
  the order of work and its rules) is added to the system prompt while the model is offered any of its
  tools, up to 20,000 characters per server. The context meter shows what it takes.
* **Tool policies** (Tools tab): a list of a regular expression and a policy, added with **+**. The pattern
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
* MCP tools on untrusted servers always ask. On trusted servers, read-only tools run without asking and
  others ask, unless a tool policy says otherwise.
* A trusted server's tool marked `_meta.confirms` (it asks the user itself, through a form, as FastMCP's
  changing tools do) runs without Stroom asking as well, so the user isn't asked twice. An _Ask every
  time_ policy still asks.
* The user can allow a tool for the rest of the chat, or all of a trusted server's read-only tools.
  A change to a tool's name or description, or to the server's settings, asks again.
* _Approve everything_ (per chat: the shield beside the model, or **Tool Approvals**) is off unless
  `allowApproveEverything` is true. The shield is highlighted while it is on.
  Even then it only covers trusted servers' tools that are not destructive or open-world.
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


## Forms from MCP tools (elicitation)

An MCP tool can ask the user to fill in a form while it runs. The chat shows a card, and _Respond…_ opens the
form. The answer goes to the MCP server, never to the model. The tool call keeps its thread while it waits, so:

* forms expire after `elicitationTimeout` (default 10 minutes);
* at most `maxElicitationsPerNode` (default 5) wait on a node at once, and more are declined;
* only flat forms are shown (text, numbers, true/false, a choice of one or several); others are declined.


## Steps for the user (user actions)

Some steps are the user's to take, such as enabling a processor filter. A trusted MCP server's tool result
can list them (`structuredContent.user_actions`, as FastMCP does), and the chat shows them as buttons in a
_Steps for you_ card. The model can neither make nor click one.

* Only four actions are shown: _Enable_ or _Disable_ a processor filter, _Open_ a document, and an
  Elasticsearch request for the cluster admin. Anything else is dropped.
* Stroom writes each button's text itself, from the filter as it is (pipeline, selection, task limit), and
  warns if the pipeline isn't tagged `mcp-generated`. The server's own wording is only shown for "Do first"
  conditions, as plain text.
* When clicked, the step is checked again (the filter exists, is on that pipeline and still needs the
  change), then done as the user, so their permissions apply. Each button works once, for 24 hours, only for
  the chat's owner, and each click is audited (`AskStroomAi.userAction`). A note goes into the chat, so the
  model knows on its next turn.
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
* The prompts MCP servers offer are slash commands too, e.g. `/onboard_data_source feed=ACME`.
  The filled-in prompt is put in the input box for the user to check and send.
* A link in the chat to a Stroom document (`?action=open-doc&docType=…&docUuid=…`, as FastMCP returns) opens
  the document in the current Stroom session, whatever host the link names, so set FastMCP's
  `STROOM_MCP_STROOM_UI_URL` to Stroom's public address for links copied elsewhere. Other links open in a
  new browser tab.
* Nothing is uploaded to an MCP server through the chat. Sample files for a new feed go through Stroom's
  own data upload, on the feed's Data tab.


## Context, memory and compaction

* The context meter beside the model shows how much of the model's context window the chat uses.
* When a chat reaches `compaction.thresholdPercent` of the window, earlier turns are replaced (for the
  model only) by short digests, keeping the last `compaction.turnsKeptInFull` turns. Users can compact
  with `/compact [focus]`, and undo the latest compaction.
* The agent has memory: the user's own (`/memories/`, loaded into every chat) and the chat's
  (`/memories/session/`). Changes to the user's memory from a chat that has read tool output or data need
  the user's approval. Users view, edit and undo changes in **Memory**.


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
| `toolGroupThreshold` | `24` | More MCP tools than this are offered as groups (0 never groups) |
| `unattendedAfter` | `PT10M` | When a run with no one watching becomes unattended |
| `workspace.maxQueryLength` | `10000` | The longest StroomQL query `run_stroomql` may run, in characters |
| `workspace.queryTimeLimit` | `PT2M` | The longest a `run_stroomql` query may take |
| `workspaceFileRetention` | `P7D` | How long workspace files are kept |
| `appConfig.askStroomAi.browserNotifications` | `true` | Browser notifications when a chat finishes or needs the user |


## Troubleshooting

* **"The tools of MCP tool server ... are unavailable"**: the server could not be reached, or its
  authentication isn't set up, e.g. OAuth2 passthrough with the internal identity provider needs a token
  audience, a trusted server and https. The chat still works with the built-in tools.
* **A chat shows "Interrupted because the Stroom node running it stopped"**: the node running it went
  away. _Resume_ carries on from the last question.
* **Nothing streams**: some OpenAI-compatible servers don't stream; the answer then arrives whole.
