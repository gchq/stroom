# Translation mapping reference

What a translation mapping (the input to `build_translation_xslt`) may hold, in one place. Start from
`draft_translation_mapping`'s draft and change only what it gets wrong; the xslt guide explains each part at length
(stroom://guide/xslt).

## Rules

- Before drafting, once the samples are surveyed (`survey_feed`, the plan's `surveyed` step: only the survey recognises
  an aggregated feed), catalogue the source's event types with `catalogue_event_types` (the plan's `catalogue` step;
  see Event type catalogue below). The draft then follows the catalogue: a rule a way of recognising each event type,
  then the catch-all.
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
- A document held as XML (a message's HTML body, a mail, text whose elements differ from record to record) is not
  structured data: it is one Data value, its parts unread. A sibling field naming the format decides (XML and JSON
  types are structured data; `html`, `text/plain`, `rtf` and any other format a document), else HTML's elements,
  else whether its structure is alike from record to record of an event type. Its parts are mapped only if the user
  asks (`xml_fields` in `changes=`). Where the draft can't tell, it shows samples: judge from what you know of the
  source (a SOAP envelope or an XML event is structured data), or ask the user.
- Only `stroom:` functions that exist may be used (`format-date`, `lookup`, `meta`, `dictionary`, `log`,
  `json-to-xml`, ...; there is no `stroom:json-parse`): `check_xslt` refuses unknown ones, elements the schema has no
  place for, and expressions that would select nothing for want of a namespace (stroom://guide/xslt).


## Event type catalogue

The output of cataloguing is a catalogue of **event types**: what types of event the source writes, what each means,
and how a **record** of each is **recognised**. You propose them from the evidence and what you know of the product;
the tool checks every proposal against every sampled record of the build's source types; the user confirms them.
The records are read as the build's latest mapping reads them (its extractions, pairs, held JSON or XML, `for_each`
items), so the fields those give are fields conditions may test; before there is a mapping, give `reading` (its
reading keys: `input`, `json_fields`, `xml_fields`, `extract`, `pairs`, `for_each`...), kept for the calls after.

- **Evidence** (call without proposals): `fields` that tell records apart (their values and counts; hosts, nodes,
  severities and times left out), record `shapes` (fields present together, with counts), `examples` (rare records
  always among them), and the `seed`: the draft's own rules as event types (`basis: draft`). The seed is for you to
  correct, not to take: add only what you keep. A logger, a file path or a program says where a record came from,
  not what happened: one logger often writes several event types, told apart by another field or its message.
- **An event type**: `type_id` Verb-Object (`Update-Password`; the vendor's code is written as Data, so keep it out
  of the TypeId), `title`, `description` (fixed text, no record values: the record's own message is used where it
  has one), `event_detail` (`Authenticate`, `Update`, `Network/Permit`...), `action`, `success`, `actor` (a
  field), `placements`, `basis` (`agent_knowledge`, `documentation`, or `draft` for the seed's), `confidence`
  (high, medium, low), optionally `doubt` (why you are unsure of it).
- **Placements**: where the event type's fields go in the schema, from what you know of the source: each a mapping
  fields entry (An entry, below) whose `path` is below `event_detail` (`Email/Subject`, `After/Object/Name`,
  `Source/User/EmailAddress`), or in EventSource from `EventSource/` (`EventSource/Client/Name` for a field naming
  the client application; not an element the common entries already write), with `field`, a constant `value`, an `xpath` or a `lookup` (not `data_name`), and the
  entry's conversions (`transform`,
  `time_format`, `timezone`, `map`, `default`, `repeat`); a list goes to an element that repeats with `for_each`
  (Groups, below), e.g. `{path: "Email/To", for_each: {array: "Recipients"}, fields: [{path: "EmailAddress", item:
  true}]}`; a field whose items have fields of their own (an array of `{Name, Value}`) goes to a `for_each` group
  with `fields`, each reading a key of the item (`{path: "Name", field: "Name"}`; `item: true` writes it whole), or
  one item is picked by an `xpath`, never to a leaf. The objects under an action element are Email, File, Folder, Group, User, Configuration, Object, Chat,
  GroupChat, Document...: `read_document type=XMLSchema element=EventDetail/<element>` lists them. The draft writes
  each as a typed entry of the event type's rule; every field no placement takes is kept as Data, each field once
  (`also_data: true` keeps a placed one as Data too; the vendor's code is always Data too). `affected` with
  `affected_as` is one placement (the affected field at that object's Id, Name, Path or Title), checked as the others
  are.
- **What placements are checked for**: the path is the schema's below the action element, to a leaf, not Data; the
  field is one the type's sampled records hold (a misspelt one is named with the record fields it may have meant), its
  fill rate in the type's records said in `typing`; a field holding several values in a record goes to an element that
  repeats (`repeat: true`) or a `for_each`; the values, converted as the translation converts them, are valid for the
  element's type, values or pattern (refused naming the values and the fix: transform, time_format, map, for_each, or
  leaving the field as Data); items with fields of their own aren't placed at a leaf; an xpath's or a lookup's values aren't checked (said in `not_checked`), its path is.
  A proposal bringing one of these is refused. An element the schema requires of the
  action element (Update's After, Send's Source and Destination, the object View, Create or Delete is of,
  Authenticate's Action and user) that nothing is placed at is `unplaced`: the set isn't put to the user until it is.
  Most of a type's fields kept as Data is only worth a look (`typing`); its `could_type` lists those fields and the
  schema's elements below the action element and in EventSource that nothing writes yet: place those that have a home there, from what you know of the source.
- **Recognised by**: `recognised_by: [{when: [conditions], names: []}]`, each way an AND of conditions; a second way
  when the type is written two ways (two line formats, two loggers): one event type, not two. A condition is
  `{field, <one test>}`, the test one of `equals`, `one_of`, `starts_with`, `contains`, `matches` (XPath's regex: no
  lookaround, at most 500 characters; prefer `starts_with` or `contains` over long values), `present`. A value that
varies record by record (an id in a message: "Object 3f2a… has been modified") is never an `equals` or `one_of`:
recognise what the records share, `starts_with` their fixed start, or `matches` with a named group for the part that
varies; `not: true` for
  the test not to hold (a missing field makes it hold). `names` names a `matches` regex's capture groups, fields the
  translation may read. Fields as the draft names them (`event.component` inside a held JSON string; a key holding a
  dot in brackets, `['log.level']`). Never the build's source type field: told by the records' other fields or
  shape. Only the source's one event type (or a line format's) may have no conditions: every record.
- **Proposals**: `[{op: add | change | remove, type_id, ...}]`, by TypeId, never the whole catalogue; `change` gives
  only the keys that change. Each call checks the whole catalogue: counts per event type and per way, `overlaps` (a
  record two event types recognise: refused until one is tightened, by the `telling_fields` named),
  `uncatalogued` records by shape (the catch-all translates them as Unknown: propose types for those that are events
  of their own), `several` (a type whose records split by a field: split it, or say why it is one with
  `one_type_because`; blocking for the seed's), `problems` (a proposal bringing one is refused, nothing recorded),
  `not_checked` (conditions the check can't evaluate: said, not refused). An event type from the documentation with
  no sampled record is kept and said to be not in the sample. The full listing is saved to the workspace (`listing`).
- **Confirmation** (`confirm: true`, again after each answer): at most three sets, the high-confidence event types,
  then the medium, then the low, then the catch-all for the uncatalogued records (give
  `catch_all: {allow_unknown: why}`). Each set is a summary by what happened with the event types worth a look row by
  row (in a medium or low set every one; give `doubt: why` in a proposal you are unsure of, and it is said there);
  every row is in the source notes, which the summary links. Each is shown first (in Stroom AI's chat, by the chat
  itself; else show it in your reply as it is), then a short form: "Accept all N event types, or type your
  amendments, or ask about any of them". The confirmation is only ever the form's. A message the user types instead
  of answering, or words in the form's text, are yours to read: a question about an item (examples, what it means,
  how it is recognised, why it is grouped so) you answer in the chat from `explain: [TypeIds or action elements]`
  (changes nothing), then put the set again with `confirm: true` (`answered: true` too after words in the form); a
  change they ask for you propose (`op: change`, or add and remove), and the set is put to them again, those changed
  since they saw it listed (`changed since shown`); if you can't tell which, ask them; never accept for them.
  `drop: true` only where the user said to drop one. A client without forms gets `needs_review` and a `review_id`:
  show the summary, and once the user agrees, call with `reviewed=<review_id>`. The step is done when every event type and the catch-all are confirmed. The user
  declining the catch-all wants something else for those records: ask them in the chat, then propose event types for
  them, or give what they become (`catch_all: {translation: {type_id, event_detail, action, description}}`), and
  confirm again; the XSLT can't be saved until the catch-all is confirmed.

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
| `lookup: {map, field}` | A reference data lookup; `key_fields: [a, b]` in place of `field` for a key of several fields (reference-data guide) |

| Modifier | |
| --- | --- |
| `map` | Values replaced: `{"ok": "true", "fail": "false"}` |
| `default` | Written when the input is empty |
| `transform` | `lower`, `upper`, `trim`, `strip_domain`, `domain`, `digits`, `unescape_quotes`, `unescape_backslashes`, `strip_control`, `clean` |
| `max_length` | The value cut to this many characters, after any `transform` (e.g. `4000` for a long message body); not with `value`, `map` or `time_format` |
| `dictionary` | The value a Dictionary of key=value lines gives |
| `repeat: true` | One element per value of a field holding several (a list of strings); for a delimited list, or several elements per item, use a group (below) |
| `time_format`, `timezone` | Time parsing: see below |
| `rollover: true` | A time of day on a date that doesn't move on by itself: see below |
| `time_fallback` | A stream meta time written when the time won't parse: see below |
| `meta_fallback` | Stream meta attributes tried in order (e.g. `["RemoteHost", "RemoteAddress"]`) for a record whose input gives no value; only as the user chose (a stand-in left out of a required element's only source) |
| `data_name`, `data_parent` | A `Data` entry: see below |
| `fill_template` | The input is a template with numbered placeholders, filled from the record's values in order: `{"pattern": "%(\\d+)", "xpath": "Args/Arg"}` (reference-data guide, Context data) |
| `mask_names` | The input is a bit mask, written as the names of the masks it holds: `{"names": {"0x1": "Read"}}` or a looked-up table; with `repeat` one element a name (reference-data guide) |

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
| `when` | Conditions, all of which must hold: a `field` with `equals`, `one_of`, `starts_with`, `contains`, `matches`, `in_dictionary` or `present: true`/`false`; `not: true` for the test not to hold (a missing field makes it hold) |
| `fields` | Its entries, written as well as `common` |
| `data` | Fields carried as Data of its action element |
| `drop: true` | Records it matches are left untranslated |
| `allow_unknown`, `keep_unknown` | Only with the user's agreement: see the xslt guide |
| `catch_all: true` | The rule for uncatalogued records, the records no other rule takes: last, with no `when` (a line format's: only its variant condition); processing logs what it catches |

`EventDetail/TypeId` is required in every Event, and differs between event types.

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
