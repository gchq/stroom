# Leaver review

Reviews a person's activity in the days before they leave against their own past and their peers', for the
indicators of data being taken: breadth widening (systems, repositories or sites outside their usual set), bulk
transfers, new destinations such as personal cloud storage or webmail, activity moving out of hours, and acts in a
sequence (sensitive files, then an archive, then an upload). Findings are indicators with evidence for an analyst to
weigh, never a conclusion about the person.

## When to use

- "has this person's behaviour changed in the 90 days before they left?"
- "review a leaver's activity before their leaving date"
- "anything unusual about a leaver's last three months?"

## Parameters

- person (required): the person id, as the indexes' person field holds it.
- leaving_date (required): the leaving date, yyyy-MM-dd, from the question or the HR record the analyst has.
- window_start (default 90 days before leaving_date): the first day reviewed, yyyy-MM-dd.
- baseline_start (default a year before window_start): the first day of the person's own baseline, yyyy-MM-dd.
- logon_ids (default none): the person's accounts, for sources with no person field, separated by commas.
- leave (default none): the person's leave in the period, each yyyy-MM-dd..yyyy-MM-dd, from the question or the
  AGENTS doc; leave isn't scored, and long quiet runs no one named are reported, not scored.

## Steps

1. Compare the review window with the person's own year and their peers, by week:
   ```json
   {"tool": "compare_activity", "arguments": {"person": "{person}", "logonIds": "{logon_ids}", "windowFrom": "{window_start}", "windowTo": "{leaving_date}", "baselineFrom": "{baseline_start}", "unit": "week", "sources": "[[the sources to compare: proxy, cloud audit and endpoint indexes, by name]]", "sumFields": "[[numeric fields to sum, e.g. bytes sent, or none]]", "breadthFields": "[[fields for breadth and new values: destinations first, then hosts, applications, repositories]]", "categories": true, "categoryField": "[[the proxy's own category field, or leave out for the model to classify the destinations]]", "accessField": "[[a field of the sites, shares or repositories people reach, or leave out]]", "leave": "{leave}"}}
   ```
   Read each change point against the leaving date (e.g. "breadth up threefold from 12 April, 41 days before the
   leaving date"). The site's own list of categories, where the AGENTS doc has one ([[the AGENTS doc's list of
   personal cloud, webmail and job sites as value=category, or none]]), goes in categoryList and overrides the rest.
2. Look for the sequences that take data out, by the person's field, from window_start to leaving_date:
   ```json
   {"tool": "sequence_query", "arguments": {"steps": [{"index": "[[the index of file access]]", "where": [{"field": "[[a field marking sensitive files]]", "value": "[[its value]]"}], "by": ["[[person field]]"]}, {"index": "[[the index of archive or upload events]]", "where": [{"field": "[[a field marking an archive or an upload to a personal destination]]", "value": "[[its value]]"}], "by": ["[[person field]]"]}], "maxspan": "1h", "timeFrom": "{window_start}", "timeTo": "{leaving_date}"}}
   ```
3. For the flagged days the comparison names (at most five), build each day's timeline with activity_timeline
   (entity the person's logon id, timeFrom the day, timeTo the next day) to see what was done around each finding.
4. Write the evidence summary: a table of the findings (date, days before leaving, source, what was seen, the
   baseline and peers' values, and the alternative explanation), the sequences found, and what each timeline
   showed. Word every line as an indicator ("uploads to new destinations rose from 0 to 12 a day"), never as intent
   or guilt; say which sources had gaps or weren't compared.

## Output

The evidence summary in the chat: the findings as indicators with their dates against the leaving date, each with
its alternative explanation; the sequences; the timelines of the flagged days; the sources compared and those left
out; and the workspace files of the daily profile and monthly rollup.
