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
Properties) can use _Reset Everyone to Defaults…_ in the chat's **Settings…**.

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
  * _OAuth2 passthrough_ passes the user's own Stroom sign-in through, so the server acts as them. This
    needs Stroom to use an **external identity provider**
    (`appConfig.security.authentication.openId.identityProviderType: EXTERNAL_IDP`), and the token's
    audience must include both Stroom and the MCP server. With the internal identity provider, the
    server's tools are unavailable, the chat says so, and the dialog warns when this mode is chosen.
  * _None_, _Bearer token_, _Custom API key header_ and _Basic auth_ use one shared credential, so every
    user's calls run as one account. Use them for development, or for servers that do their own checks.
* **Test Connection** (General tab) connects with the settings as they are in the dialog and lists the
  tools the server would offer. It needs EDIT permission on the model.
* **Trusted** (Tools tab): only trusted servers' tool annotations (read-only, destructive, open-world) are
  believed. Mark a server trusted only if you control it.
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
* The user can allow a tool for the rest of the chat, or all of a trusted server's read-only tools.
  A change to a tool's name or description, or to the server's settings, asks again.
* _Approve everything_ (per chat: the shield beside the model, or **Tool Approvals…**) is off unless
  `allowApproveEverything` is true. The shield is highlighted while it is on.
  Even then it only covers trusted servers' tools that are not destructive or open-world.
* If no browser has polled the chat for `unattendedAfter`, the run is unattended: only read-only tools
  run, and anything else waits for the user.
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


## Prompts and uploads

* The prompts MCP servers offer are slash commands in the input box, e.g. `/onboard_data_source feed=ACME`.
  The filled-in prompt is put in the input box for the user to check and send.
* Nothing is uploaded to an MCP server through the chat. Sample files for a new feed go through Stroom's
  own data upload, on the feed's Data tab.


## Context, memory and compaction

* The context meter beside the model shows how much of the model's context window the chat uses.
* When a chat reaches `compaction.thresholdPercent` of the window, earlier turns are replaced (for the
  model only) by short digests, keeping the last `compaction.turnsKeptInFull` turns. Users can compact
  with `/compact [focus]`, and undo the latest compaction.
* The agent has memory: the user's own (`/memories/`, loaded into every chat) and the chat's
  (`/memories/session/`). Changes to the user's memory from a chat that has read tool output or data need
  the user's approval. Users view, edit and undo changes in **Memory…**.


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
| `workspaceFileRetention` | `P7D` | How long workspace files are kept |
| `appConfig.askStroomAi.browserNotifications` | `true` | Browser notifications when a chat finishes or needs the user |


## Troubleshooting

* **"The tools of MCP tool server ... are unavailable"**: the server could not be reached, or its
  authentication needs an external identity provider. The chat still works with the built-in tools.
* **A chat shows "Interrupted because the Stroom node running it stopped"**: the node running it went
  away. _Resume_ carries on from the last question.
* **Nothing streams**: some OpenAI-compatible servers don't stream; the answer then arrives whole.
