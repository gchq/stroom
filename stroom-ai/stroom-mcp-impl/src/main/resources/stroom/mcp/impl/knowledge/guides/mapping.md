# Translation mapping reference

What a translation mapping (the input to `build_translation_xslt`) may hold, in one place. Start from
`draft_translation_mapping`'s draft and change only what it gets wrong; the xslt guide explains each part at length
(stroom://guide/xslt).

## Rules

- Before drafting, `classify_kinds` (the plan's `classify` step): it lists the kinds of event the user's
  documentation doesn't cover; propose each from its records and what you know of the source, with a short `title`
  and the `product` it comes from, saying how sure you are; show the table it gives back in your reply, as it is (in Stroom AI's chat, the
  chat shows it itself: don't repeat it), then call it
  with `confirm: true`: the user accepts the set shown (up to ten of one confidence, high first) or amends it,
  then you show the next set it gives back and confirm again; a source of many kinds goes in rounds of 18. When no field
  is named like a kind of event, it may first ask the user which field tells the kinds apart (without forms
  `needs_kind_field`: ask them in the chat, show the candidates, and pass what they say as `kind_field`); never
  choose it yourself. For each, who acted (`actor`) and what it was done to (`affected`, and
  `affected_as`: User, File, Document, Group...). The draft then follows them, a rule a kind. Send the user's
  amendments and corrections back as proposals with `from_user: true` (only ever what the user said, never your own
  proposals unconfirmed); ask about any they reject. Show only the table each call gives back: the others come after
  the form.
- Never write a translation XSLT by hand, and never send the field inventory as the mapping: draft it, edit what the
  draft gets wrong, and give `build_translation_xslt` the edited mapping with the draft's `schema_version`, the sample
  (and the Data Splitter's spec as `splitter`), so fields no record has and times their format doesn't fit are caught
  before stepping.
- Before building, place every field the draft carries as Data in the schema element that means it, in EventSource
  (Device, Client, Server, User and their Location: city, country, latitude, longitude) as well as the action element
  (a URL, an HTTP method, a response code, a rule): the draft's `data_placement` lists each rule's Data entries beside
  the elements available, and `read_document type=XMLSchema element=` says what each takes. Don't wait for the user
  to ask.
- A field that no element of the schema means (a rule id, a byte count, a vendor code) is carried as Data of the action
  element (see Data below): never invent an element for it, and never leave the event Unknown because of it.
  `read_document type=XMLSchema element=` says what an element takes (`EventDetail` for the action elements,
  `EventDetail/<Action>` for its children and values).
- Values the record doesn't carry come from reference data (`find_reference_data`; `build_reference_xslt` and a
  reference-data pipeline for a new table, the events pipeline naming its feed in references:
  stroom://guide/reference-data) or from a Dictionary doc (`save_dictionary`).
- A text field holding several values (a message with a time, user, action and description) is parsed with `extract`,
  never substring-before/after chains; JSON held in a string is read with an `xpath` using `json-to-xml()`
  (stroom://guide/json-input), JSON encoded twice (a JSON string holding it) too. A syslog line held in a field (a
  shipper's JSON around it), or what follows the host in an RFC 5424 header, is drafted as an `extract` of its
  header's parts (`<field>.time`, `<field>.app`, `<field>.structured`, `<field>.message`...).
- XML held in a text field (a SOAP envelope, a syslog message holding an `<Event>`): name the field in
  `xml_fields` and read inside it as `<field>/<path>` from the document's root element down
  (`payload/Envelope/Body/Login/User`), `<field>//<name>` for an element anywhere in it, and `<field>//<name>/@<attr>`
  for an attribute; steps match by local name, and a value that isn't XML gives nothing rather than an error. XML
  after words of the source's own is read out first by an `extract` entry (`<field>.xml`), then named in
  `xml_fields`. A field an extract reads (`<field>.message`) can be named too. `draft_translation_mapping` does all
  this itself. Never a second parser or regexes per element in the splitter: a pipeline has one parser, reading the
  raw stream.
- Only `stroom:` functions that exist may be used (`format-date`, `lookup`, `meta`, `dictionary`, `log`,
  `json-to-xml`, ...; there is no `stroom:json-parse`): `check_xslt` refuses unknown ones, elements the schema has no
  place for, and expressions that would select nothing for want of a namespace (stroom://guide/xslt).

## The mapping

| Key | What it holds |
| --- | --- |
| `input` | Required: `data_splitter`, `json`, `xml` or `xml_fragments` |
| `json_layout` | `array` or `lines` (JSON input) |
| `root`, `record`, `xml_namespace` | XML input: the record element and its namespace |
| `extract` | Regexes over a field: `{"field": "message", "regex": "^(\\S+) (.*)$", "names": ["user", "rest"]}`; the names are fields |
| `pairs` | Key/value text, as drafted: `{"field": "body", "style": "equals", "keys": [...]}` |
| `for_each` | The field or XPath of a batch's items, each an Event |
| `nil_values` | Values that mean none, e.g. `["-"]`: an element whose input is one is left out |
| `drop_when` | Records left untranslated on purpose, each with a `reason` |
| `common` | Entries written in every Event |
| `events` | Rules, tried in order: the first whose `when` matches writes the Event |
| `unmatched` | `warn` (default) or `skip` for records no rule matches |
| `ignored_fields` | input fields left out of the events on purpose (Data Splitter and JSON input). Any other field no rule reads is logged in processing (WARN, once a stream, by name, classed benign; summarise_streams kind=errors lists them), so a field the sample lacked is found: map it, or list it here with the user's agreement |
| `log_unmapped_fields` | `true` (default) logs those fields; `false` stops it. Written into the XSLT as its variable `log_unmapped_fields`, so it can be turned off in Stroom's editor too, and a save from the tools keeps it off |

## An entry

`{"path": "EventSource/User/Id", "field": "user"}`: a `path` below Event and one source, with optional modifiers.

| Source | |
| --- | --- |
| `field` | An input field (a column, a JSON key, `user.name` nested, an extracted name) |
| `any_of: [a, b]` | The first of these fields with a value |
| `join: [a, b]` | These fields' values joined with `separator` (default `-`) |
| `value` | A constant |
| `xpath` | A computed value, relative to the record (never starting `//` or `/`) |
| `meta` | A stream meta attribute |
| `lookup: {map, field}` | A reference data lookup |

| Modifier | |
| --- | --- |
| `map` | Values replaced: `{"ok": "true", "fail": "false"}` |
| `default` | Written when the input is empty |
| `transform` | `lower`, `upper`, `trim`, `strip_domain`, `domain`, `digits`, `unescape_quotes`, `strip_control` |
| `dictionary` | The value a Dictionary of key=value lines gives |
| `repeat: true` | One element per value of a field holding several (a list of strings); for a delimited list, or several elements per item, use a group (below) |
| `time_format`, `timezone` | Time parsing: see below |
| `rollover: true` | A time of day on a date that doesn't move on by itself: see below |
| `time_fallback` | A stream meta time written when the time won't parse: see below |
| `meta_fallback` | Stream meta attributes tried in order (e.g. `["RemoteHost", "RemoteAddress"]`) for a record whose input gives no value; only as the user chose (a stand-in left out of a required element's only source) |
| `data_name`, `data_parent` | A `Data` entry: see below |

## Times

`time_format` is a Java time pattern with letters that aren't pattern letters quoted:
`yyyy-MM-dd'T'HH:mm:ss.SSSX`. An epoch time is named, never described: `epoch_s` (seconds, a fraction allowed),
`epoch_ms`, `epoch_us`, `epoch_ns`; a Windows FILETIME (100 ns ticks since 1601, as Active Directory's `lastLogon`)
is `windows_filetime`. A time field that is an object's (an expiry, a file's creation, an account's last logon) is not
the event's: keep it as Data. `timezone` (an IANA name, e.g. `UTC`, `Australia/Sydney`, or an offset, `+10:00`) only for a time that
carries no zone or offset of its own, and never assumed: the zone is the user's to say. The draft asks, offering UTC,
a zone the records name, and an offset guessed from the gap between the sample's latest time and when Stroom received
it (`timezone=` gives the answer); left unset, Stroom reads the time as UTC and the build warns. Where each record
says its own zone, `timezone` is an object naming where: `{"field": "tz", "default": "UTC"}` or `{"meta":
"TimeZone", "default": "Australia/Sydney"}` (`timezone=field:tz` or `meta:TimeZone`, `|ZONE` the default, in the
draft). Stroom reads IANA names, `UTC`, `Z` and offsets (`+10:00`, `+1000`, `UTC+10`); `+5:30` and `+10.5` are
written as it reads them; other values (abbreviations such as `AEST`; `IST` and `CST` name more than one zone) go in
its `map`, as the user says what each is: `{"field": "tz", "map": {"AEST": "Australia/Sydney"}, "default": "UTC"}`.
`default` is the zone where a record gives none. `build_translation_xslt` names the sample's zones Stroom wouldn't
read; in processing one it can't read is read as UTC, with Stroom's warning naming the record. `profile_sample` and
the draft give the pattern the sample's values fit.

Where a time is incomplete, the user says where the rest comes from; the draft asks (`time_source`,
`date_source`) and writes the answer. Never `stroom:current-time()` (processing time), and never Stroom's own
completion of a dateless time (it moves a time later than the stream's creation back a whole month).

- **A date in a header line** (`=== Log opened 2026-10-11 ===`, then `22:14:03 ...` lines): the splitter writes the
  header's date on each line after it as `header_date` (its header part's `carry`), the header is a `drop_when` rule,
  and each time is `"xpath": "concat(data[@name='header_date']/@value, ' ', data[@name='time']/@value)"` with the
  joined `time_format` and `"rollover": true`. A later header (`Day changed to ...`) replaces the date.
- **No date anywhere in the records:** the date Stroom received or created the stream
  (`substring(stroom:meta('ReceivedTime'), 1, 10)`), or one in the file's name
  (`replace(stroom:meta('RemoteFile'), '^.*?(\d{4}-\d{2}-\d{2}).*$', '$1')`), joined the same way, with `rollover`.
- **No time at all:** `"meta": "ReceivedTime"` (or `CreatedTime`): an approximation, only as the user agreed.
- **`rollover`** adds a day each time a record's time goes back past the one before it on the same date (a log
  crossing midnight); a new date starts again. It holds the previous time with `stroom:put`, which processing keeps
  for the stream but stepping keeps for one record: a time past midnight steps on the day before, and is right in
  processing.
- **Times in several layouts:** one `time_format` reads them where Java's optional sections can say it: a
  fraction some values have, `yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]XXX`, or whole layouts as alternatives,
  `[yyyy-MM-dd HH:mm:ss][dd/MMM/yyyy:HH:mm:ss Z]` (Stroom reads both; the check suggests one where it fits).
  Otherwise read each layout (a splitter part each, or a rule each with its own `time_format`). A record whose time
  still won't parse gets an empty time the schema refuses, unless the user agrees to
  `"time_fallback": "ReceivedTime"`, which writes that instead and logs a warning naming the record.
- **Times that repeat the event's:** where every record has the time EventTime/TimeCreated reads, the draft lists
  the other time fields that only say it again (the same instant, or a date and a time of day that make it) in
  `ignored_fields`, so processing doesn't log them as unread. A time named as a start or a creation stays to map.
- **Which time is the event's:** the draft reads the record's own time before its container's (a syslog header's),
  falling back to the container's record by record, and keeps an object's times (an expiry, a file's creation, an
  account's last logon) as Data. Where two times could each be the event's, it drafts with the first and lists them
  in `time_candidates` (per rule, or for every record), each with its share of the records, format, rank and median
  distance from the others; that entry carries `choose_from`, which `build_translation_xslt` refuses until you
  choose. Choose from that evidence and what you know of the source (the time the event happened, not when a period
  began or something was created), send the entry with `changes=` with its field and without `choose_from`, say
  which you chose and why, and ask the user when unsure.

## Data

A field no element means is carried as `Data`: a path ending `/Data` with `data_name`, under the element it describes
(`EventDetail/Network/Permit/Source/Data`) or the action element. `data_parent` groups entries in a named Data. A
rule's `"data": ["rule_id", "bytes"]` is short for Data entries of its action element, each named after its field.

## Groups: arrays of objects

An element written once per item, each holding its own fields, is a group: `path` (an element the schema lets repeat),
`for_each` (where the items come from), `as` (the item's name) and `fields` (paths relative to the group's element,
each read from the item). A mail gateway's `recipients="john.smith@mail.com,jack.sparrow@gmail.com"`, each recipient
a User with its own Id and Domain:

```json
{"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","}, "as": "recipient",
 "fields": [{"path": "Id", "item": true, "transform": "strip_domain"},
            {"path": "Domain", "item": true, "transform": "domain"}]}
```

- `for_each`: `{"split": "<field>", "on": ","}` (a literal separator, not a regex: no `tokenize()` needed; pieces
  trimmed, empty ones dropped); `{"array": "<field>"}` (a JSON array, or an element repeated in XML); `{"zip":
  {"recipient": "recipients", "status": "recipient_status"}, "on": ","}` (lists paired by position; a record whose
  lists differ in length is logged as an error and its event isn't written). A source is read from the enclosing
  item (the record at the top); `"scope": "record"` or `"item": "<as>"` in `for_each` reads elsewhere.
- A field reads a key of its item (`"field": "status"`, `address.city` inside it; a zip's names), the item itself
  (`"item": true`), an outer group's item (`"item": "recipient"`, with or without `field`), or the record (`"scope":
  "record"`). Anything else an entry has works (transform, map, lookup, default...), but not `repeat`.
- A group's fields may be groups (16 deep at most). Nothing else in the rule maps below a group's element.
- A Data group: `path` ending `Data` with `data_name`, its fields Data entries (`"path": "Data"`), gives `<Data
  Name="recipient"><Data Name="id" Value="..."/>...</Data>` per item.

## A rule

| Key | |
| --- | --- |
| `name` | Required, unique |
| `when` | Conditions, all of which must hold: a `field` with `equals`, `one_of`, `matches`, `in_dictionary` or `present: true`/`false` |
| `fields` | Its entries, written as well as `common` |
| `data` | Fields carried as Data of its action element |
| `drop: true` | Records it matches are left untranslated |
| `allow_unknown`, `keep_unknown` | Only with the user's agreement: see the xslt guide |

`EventDetail/TypeId` is required in every Event, and differs between kinds of event.

## Changing a mapping

`build_translation_xslt changes=` sends only what changes, merged into the mapping last sent or saved (last sent
includes a call that was refused or had problems: its changes stand, so don't send them again; a rule or extract
an earlier call removed is said in `changes_applied`, not refused, while a name the mapping never had is refused):

- `events`: a rule by `name`. A new one is added after the others. For one already there, each key given replaces
  its own, and its `fields` change entry by entry: an entry replaces the rule's entry with the same `path` (and
  `data_name`), or is added; `{"path": ..., "remove": true}` removes one. `"replace": true` replaces the rule whole;
  `{"name": ..., "remove": true}` removes it.
- `common`: entries by `path` (and `data_name`), replaced, added, or removed with `"remove": true`.
- `extract`: entries by the fields they extract (`names`): an entry replaces the one extracting any of its names, or
  is added; `{"names": [...], "remove": true}` removes one. Send only the entries that change.
- Any other key replaces that key whole.

`changes_applied` in the result says what each change did.
