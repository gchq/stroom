# Skills: saved procedures

A skill is how to do one kind of work (a hunt, a timeline, an investigation), saved so the agent, or an analyst,
does it again with new parameters. AGENTS docs hold rules, guides facts, skills procedures.

## Where skills are

Markdown in Documentation docs named `SKILLS` (one a folder), holding several skills, a `#` heading each. Only the
name makes a skills doc: a doc of another name is never read for skills, whatever its front matter says. Every skill the reader can view is listed to the agent (name, folder,
description), those in the folders of the question's context first; `find_skills` searches them and reads one whole.
Two of one name are told apart by folder.

## Front matter

YAML between `---` lines at the top: `stroom: skills` and `history`, its change history. Each
entry has `version`, `date`, `author`, `how` (`agent`: by `propose_skill`; `user`: by hand), `skill`, `change` and
`body` (a short hash of the text below the front matter as the change left it, so an edit by hand since shows).
`propose_skill` writes it; it is never written by hand. It shows as YAML text in Stroom.

## A skill, by convention

~~~markdown
# User activity timeline

Builds a timeline of one user's activity over a period, in time order, on each host.

## When to use
- "build a timeline of jsmith's activity over the past 7 days"
- "what was this user doing last week?"

## Parameters
- user (required): the user's account name.
- days (default 7): how many days back from now.

## Steps
1. Find the user's events in time order:
   ```json
   {"tool": "run_stroomql", "arguments": {"query": "from \"Endpoint\" where UserId = '{user}' and EventTime > now() - {days}d select EventTime, Host, Action sort by EventTime"}}
   ```

## Output
A table in the chat, in time order.
~~~

- The `#` heading is the name; the first paragraph under it the description (what the agent is shown to choose it).
- `## When to use`: how people ask for it, in their words; the agent matches questions against it.
- `## Parameters`: a list, each a name, `(required)` or `(default <value>)`, and what it is.
- `## Steps`: numbered, plain language; `{name}` where a parameter goes; where a tool is used, an example call in
  fenced JSON, `{"tool": "<name>", "arguments": {...}}`, its arguments as the tool takes them (a parameter used as
  a value is quoted: `"{days}"`).
- `## Output`: a table, a summary, or a chart. The chat draws only timelines (`gantt`); any other chart is a
  dashboard's.
- The steps are guidance the agent follows, not replayed exactly; the user's request comes first.
- `[[...]]` in a template is a placeholder for what a site supplies (an index, its role conditions), to fill.
- A timeline skill's `## Event chains`: what its sources' events mean beyond the schema, a line each:
  `- <index>: pair <start type> -> <end type> by <key path>` or
  `- <index>: <type> is active|idle|focus|open|close|action|ignore` (focus: a window or application coming to the
  front, which starts a focus session).
  activity_timeline (with `skill`) applies them first and offers to add what it learns; check_skill checks them
  against the index's recent events.

## The tools

- `find_skills`: search by words, or read one by name (checked first); with `arguments`, its parameters bound and
  any required one missing asked in a form. The chat's `/skill:<name> name=value` runs one by name this way.
- `check_skill`: problems (no description or steps, a parameter used but not listed or listed and unused, a tool
  that doesn't exist or the policies deny, an example call off the tool's parameters, a missing index or field, a
  hand edit) and suggestions (a vague step, a value that should be a parameter, a chart the chat doesn't draw, a thin
  "When to use", a near-duplicate, often offered and never chosen, asked for by name and not offered).
- `propose_skill`: `draft` (from the chat's calls or a description; what only the author knows asked in a form),
  `template` (the built-in starting points: a user activity timeline, X then Y by the same entity, who of a group
  did X), `trial` (the draft run once on given values: follow the steps it gives back), `propose` (a diff through
  "Review and apply": into the doc's editor unsaved, or a new `SKILLS` doc created), `remove`, `undo` (the reverse of
  a change applied in the chat), `record`.
