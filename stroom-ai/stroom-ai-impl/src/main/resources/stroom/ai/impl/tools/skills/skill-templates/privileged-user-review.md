# Privileged user review

Reviews an administrator's privileged activity against their own past and their peers': sessions outside their
usual hours, administration tools never used before, hosts reached for the first time, and acts in a sequence such
as a privileged logon, mass changes, then logs cleared; and, apart, what was done to their accounts (privileged
groups granted, resets, MFA changed). Findings are indicators with evidence for an analyst to weigh, never a
conclusion about the person.

## When to use

- "review this administrator's privileged activity over the last quarter"
- "has an admin started using new tools or reaching new servers?"
- "any out-of-hours admin sessions by this person?"

## Parameters

- person (required): the person id, as the indexes' person field holds it.
- days (default 90): how many days back from now to review.
- baseline_days (default 270): how many days before those make the baseline.
- logon_ids (default none): the person's accounts, admin accounts included, separated by commas; other accounts
  aren't included unless named.

## Steps

1. Compare the review period with the person's own baseline and their peers, by week:
   ```json
   {"tool": "compare_activity", "arguments": {"person": "{person}", "logonIds": "{logon_ids}", "windowFrom": "now()-{days}d", "windowTo": "now()", "baselineFrom": "now()-{baseline_days}d", "unit": "week", "sources": "[[the privileged session, authentication and endpoint indexes, by name]]", "breadthFields": "[[fields for breadth and new values: hosts reached, processes or administration tools run]]", "includeTargets": true}}
   ```
2. Look for a privileged logon followed by a control weakened, by the person's field:
   ```json
   {"tool": "sequence_query", "arguments": {"steps": [{"index": "[[the index of privileged logons]]", "where": [{"field": "[[a field marking a privileged logon]]", "value": "[[its value]]"}], "by": ["[[person field]]"]}, {"index": "[[the index of audit and logging changes]]", "where": [{"field": "[[a field marking logs cleared or auditing disabled]]", "value": "[[its value]]"}], "by": ["[[person field]]"]}], "maxspan": "1h", "timeFrom": "now()-{days}d"}}
   ```
3. Check the sessions found against approved work: [[where change tickets or approved exceptions are recorded: the
   AGENTS doc, a ticket index, or none]]; a session with a ticket is listed as explained, not dropped.
4. For the flagged days the comparison names (at most five), build each day's timeline with activity_timeline
   (entity the admin account, timeFrom the day, timeTo the next day, includeTargets true).
5. List what was done to their accounts (the "done to them" findings: a privileged group or access granted, a
   reset, MFA changed, by which account and when) apart from their own activity, worded as things done to the
   person or their account, never as their own acts or intent; check a grant against approved work as in step 3.

## Output

A summary in the chat: the findings as indicators (new tools, new hosts, sessions out of hours, the sequences),
each with its evidence, the baseline and peers' values and an alternative explanation (an on-call week, a planned
change, a new system); which were matched to approved work; what was done to their accounts, apart; the timelines of
the flagged days; and the sources left out.
