# Who of a group did X

Finds which members of a group (a team, a department) did something in a period: the group's members from the
site's identity source, their actions from an activity source, joined by account name.

## When to use

- "which users in team A accessed abc.com?"
- "who in the Finance group downloaded a file yesterday?"
- "did anyone in this team do X this week?"

## Parameters

- group (required): the group's name, as [[identity index]] holds it.
- days (default 7): how many days back from now.

## Steps

1. Find the group's members:
   ```json
   {"tool": "run_stroomql", "arguments": {"query": "from \"[[identity index]]\" where [[group field]] = '{group}' select [[account field]]", "out": "/outputs/skills/group-members.md"}}
   ```
2. Find the action in the period ([[activity index]]: [[the action's conditions]]):
   ```json
   {"tool": "run_stroomql", "arguments": {"query": "from \"[[activity index]]\" where [[action condition]] and [[time field]] > now() - {days}d select [[time field]], [[account field]], [[object field]]", "out": "/outputs/skills/group-actions.md"}}
   ```
3. Keep the actions of the group's members:
   ```json
   {"tool": "table", "arguments": {"path": "/outputs/skills/group-actions.md", "join": {"path": "/outputs/skills/group-members.md", "on": ["[[account field]]"]}}}
   ```

## Output

A table in the chat: each member who did it, when and on what; and how many of the group did.
