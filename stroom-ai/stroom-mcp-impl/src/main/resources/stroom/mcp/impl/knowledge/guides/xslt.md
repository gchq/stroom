# Stroom XSLT conventions

Stroom runs XSLT 2.0/3.0 on Saxon-HE 9.9. Declare `xmlns:stroom="stroom"` to use Stroom functions.

Saxon-HE has no higher-order functions: inline functions, function references (`upper-case#1`), the functions that
take a function (`fold-left`, `for-each`, `filter`...) and string constructors don't compile (Saxon says they need Saxon-PE), and
a mapping xpath using one is refused when the XSLT is built. Use `for`, `let`, `if`, `!` and `=>`, or in XSLT
`xsl:iterate` and `xsl:for-each-group`, which are supported.

## Input depends on the parser before the XSLT

| Parser (template) | XSLT input | `xpath-default-namespace` |
| --- | --- | --- |
| `DSParser` (a text template's) | `<records><record><data name="field" value="..."/>` | `records:2` |
| `JSONParser` (a JSON template's), no text converter | JSON as `map`/`array`/`string` elements, keys in `@key`; root `/map` for JSON lines, `/array` for an array (see the JSON guide) | `http://www.w3.org/2013/XSL/json` |
| `XMLParser` (an XML template's) | the source XML | the source namespace |
| `XMLFragmentParser` with an XML_FRAGMENT wrapper converter | the fragments (one `<Event>` per line) inside the wrapper's root (`records`, or `Events`) | the fragments' own namespace, else the wrapper root's: `records:2` for a `<records>` wrapper, `event-logging:3` for an `<Events>` one |
| Indexing pipelines | `<Events>` from the Events stream, with `@StreamId` and `@EventId` on each `Event` | `event-logging:3` |

Match the record element and build one `Event` per record; the split filter hands the XSLT one
record at a time. Without the right `xpath-default-namespace` (or a prefix bound to it) a bare `match="record"`
or `select="map"` selects nothing, the output is empty text, and processing writes no Events and no error;
`check_xslt` names such expressions, and stepping reports "Output contains no XML elements".

### Namespaces, from raw input to Events

Three namespaces are in play, and each step has its own: the parser's output (what the XSLT reads), the XSLT's
output (`event-logging:3`, always), and the schema the `SchemaFilter` validates against (event-logging, picked by
`xsi:schemaLocation`). A mapping sets the first with `input` (and `xml_namespace` for XML) and the generator writes
the other two; by hand, `xpath-default-namespace` is the first and the default `xmlns` the second.

| What you see | Why | Fix |
| --- | --- | --- |
| Stepping clean, but every record's output is an empty `<Events/>` (or "Output contains no XML elements"); processing writes no Events and no Error stream | The XSLT reads the input in the wrong namespace: its record template matches nothing | Read the parser's output in `step_sample` and take the root's `xmlns`: set the mapping's `xml_namespace` (or `xpath-default-namespace`) to it |
| XML fragments come out empty | A fragment with no `xmlns` of its own takes the wrapper root's: `records:2` in a `<records>` wrapper, `event-logging:3` in an `<Events>` one | `xml_namespace` = the wrapper root's namespace (`profile_sample`'s `xslt_input.namespace`) |
| JSON comes out empty | The JSONParser writes `http://www.w3.org/2013/XSL/json`; `json-to-xml()` writes `http://www.w3.org/2005/xpath-functions` | `input: json` sets it; by hand, read the parser's output in the first, a `json-to-xml()` result in the second |
| Source XML comes out empty | Its own `xmlns` (Windows events: `http://schemas.microsoft.com/win/2004/08/events/event`) | `xml_namespace` = that namespace; with none, leave it empty |
| Schema errors on elements that look right (`Event`, `EventTime` "not expected") | The output elements are not in `event-logging:3` (an XSLT with no default `xmlns`, or one copying input elements with their own namespace) | Write output with `xmlns="event-logging:3"` on the stylesheet; never `xsl:copy-of` an input element into an Event |
| `schemaLocation` names a version this Stroom lacks | The `Events` root's `xsi:schemaLocation` picks the XSD | Use a version this Stroom holds: the one the user chose (`build_translation_xslt schema_version=` writes it) |
| The `Version` attribute "is not facet-valid with respect to enumeration '[3.0.0, ..., 3.2.3]'" for a version this Stroom holds | `xsi:schemaLocation` names a system id Stroom doesn't hold exactly (`file:///`, three slashes, or no `file://`): Stroom then validates against another schema of the namespace, not the one named | Write `file://event-logging-vX.Y.Z.xsd`, two slashes; `check_xslt` refuses any other spelling |

## Output

Every element written below `Event` must be one the event-logging schema has: `EventTime`, `EventSource`,
`EventDetail` (holding `TypeId`, `Description` and exactly one action element such as `Authenticate`, `Process`,
`View`, `Alert` or `Unknown`), in schema order. An element the schema lacks (`EventDetail/ServerEvent`,
`Outcome/Result`) fails validation on every record; `check_xslt` refuses it and names what is allowed there,
and `build_translation_xslt` only accepts schema paths. Source values with no home go in `Data` elements
(`EventDetail/<Action>/Data` with `Name` and `Value`).

## Functions used most

Only the functions Stroom registers compile; `check_xslt` refuses any other `stroom:` name with the nearest
real one. There is no `stroom:json-parse()`: JSON in a string is parsed with `json-to-xml()`, and raw JSON by
the pipeline's `JSONParser`.

| Function | Use |
| --- | --- |
| `stroom:format-date(value, 'pattern')` | Parse a timestamp into `yyyy-MM-ddTHH:mm:ss.SSSZ`. A third argument gives the input time zone (e.g. `'+10:00'`); the five-argument form also sets the output pattern and zone. A value that does not match logs a WARNING and returns empty, which then fails the schema. |
| `stroom:lookup('map', key)` | Reference data lookup through a reference loader attached to the XSLT element. |
| `stroom:meta('Name')` | A receipt header of the source stream, e.g. `Feed`, `ReceivedTime`, `RemoteAddress`, or a custom one. `stream_meta streamId=` lists a stream's. |
| `stroom:feed-name()`, `stroom:stream-id()`, `stroom:record-no()` | Where the record came from. |
| `stroom:dictionary('Name')` | Text of a Dictionary doc, e.g. a field mapping table. |
| `json-to-xml(string)` | Parse JSON held in a string field into `map`/`array` elements. |
| `stroom:log('ERROR', 'message')` | Raise your own marker, e.g. for an unexpected event type. |

## Generating instead of writing

Start with `draft_translation_mapping` on the sample files: it returns a valid mapping with the input kind, the
obvious homes for fields by name (time and its pattern, host, client and server addresses and ports, user, event
type, message), a rule per kind of event and every other field as `Data`, with notes on what is still to decide
(the action element per kind, System Name and Environment, a time zone). Edit that; never send the field
inventory as the mapping. `build_translation_xslt` then writes the translation from the mapping, so the XSLT
itself need not be written by hand:

```json
{"input": "data_splitter",
 "common": [{"path": "EventTime/TimeCreated", "field": "time", "time_format": "yyyy-MM-dd'T'HH:mm:ss"},
            {"path": "EventSource/System/Name", "value": "Acme VPN"},
            {"path": "EventSource/System/Environment", "value": "Prod"},
            {"path": "EventSource/Generator", "value": "vpnd"},
            {"path": "EventSource/Device/HostName", "field": "host"},
            {"path": "EventSource/User/Id", "field": "user"}],
 "events": [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
             "fields": [{"path": "EventDetail/TypeId", "value": "VPN-Login"},
                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                        {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                        {"path": "EventDetail/Authenticate/Outcome/Success", "field": "result",
                         "map": {"ok": "true", "fail": "false"}},
                        {"path": "EventDetail/Authenticate/Data", "data_name": "session", "field": "sid"}]}]}
```

Fields are Data Splitter names, JSON keys (`user.name` for nested keys), XML paths relative to the record, or
names from `extract`. The tool puts elements in schema order, converts times with `stroom:format-date`, leaves
elements out when their input is empty (or writes `default`), and logs records no rule matches. Unknown paths,
constants the schema does not allow, alternatives used together and missing required elements come back as
problems to fix in the mapping. Give it the `sample` too (and the `splitter` spec for text): it then reports
fields no sample record has, with the nearest names, and time formats the sample's values do not fit, before
anything is stepped. What the sample check could not tell (a rule whose conditions test an xpath, or a field
extracted from one) is listed in its `not_checked`: stepping shows those. The XSLT is compiled before it is saved,
as Stroom compiles it: one that doesn't compile is a problem, and nothing is saved.

An `xpath` reads from the record: `.//*:EventData/*:Data`, or a path from the record's children. One starting at
the document root (`//*:EventData/*:Data`, `/records/...`) is a problem: stepping hands the XSLT one record at a
time, so it works there, but processing reads a stream's records as one document, where `//` finds every record's
value: a function taking one value stops the whole stream ("A sequence of more than one item is not allowed"), and
otherwise every event gets the first record's value. (For a source's own XML document, a value outside the records
read from the root is only a warning.) An extract's names are fields, not variables: read one with `field`, never
`$name` in an xpath (the XSLT declares no such variable).

Each value has one source and optional modifiers:

| Source | Meaning |
| --- | --- |
| `field` | An input field, or a name from `extract` |
| `any_of: [a, b]` | The first of these fields with a value, for sources whose variants name a thing differently |
| `join: [a, b]` | These fields' values joined with `separator` (default `-`), those with no value left out: `User-Login` from a category and an action, extracted fields included |
| `value` | A constant |
| `lookup: {map, field, path?}` | What reference data holds for the key (see the reference-data guide) |
| `xpath` | A computed value (embedded JSON: `json-to-xml(...)/*/*[@key='x']`) |
| `meta` | A stream meta attribute, the headers a stream was sent with: `{"path": "EventSource/Device/HostName", "meta": "MyHostName"}` reads `stroom:meta('MyHostName')`. `stream_meta streamId=` lists a stream's; given the sample streams, `build_translation_xslt` warns of one none carries |

| Modifier | Meaning |
| --- | --- |
| `transform` | `lower`, `upper`, `trim`, `strip_domain` (`DOMAIN\user`, `user@domain` → `user`), `domain`, `digits`, `unescape_quotes` (a quoted CSV value's doubled quotes as one), `strip_control` (ANSI colour codes and other control characters out, from a log written for a terminal) |
| `dictionary: name` | The value a Dictionary of key=value lines gives the field |
| `map`, `default`, `time_format`, `timezone` | As before: a value map, a fallback, time parsing |

Times: `time_format` is the profile's pattern, which reads an offset (`XXX`, `Z`) or a zone (`VV` for
`Australia/Sydney`, `z` for `AEST`) in the value, so Stroom converts the time to UTC, daylight saving included;
fractions of a second are kept (`.SSS`; epoch seconds with a fraction as `epoch_s`), and finer ones (`.SSSSSS`,
epoch microseconds `epoch_us`, nanoseconds `epoch_ns`) are cut to the schema's three digits, never rounded, so no
event's time is moved on. Only a time with no zone of its
own takes `timezone`, the zone the source writes in (UTC unless the user says otherwise). A zone the source names is
kept with the event as `EventSource/Device/Location/TimeZoneName` (an IANA name: the draft maps it from the value or
a field of zone names); a time read with an IANA `timezone` that isn't kept is warned about. A bare offset (`+10:00`)
isn't an IANA name, so there is nowhere in the schema to keep it.

Conditions test a field with `equals`, `one_of`, `matches`, `present` or `in_dictionary` (a Dictionary of one
entry per line). Write XSLT by hand only for what none of this expresses.

### Several events in one record, repeated values, and records to leave out

- **`for_each`**: when a record is a batch (`{"host": ..., "events": [...]}`, or `<Batch><Entry/>...`), name the
  field or XPath selecting the items and each item becomes an Event. Fields, conditions and extractions then read
  the item; mark the ones that read the record round it with `scope: record` (the batch's host). The generated
  XSLT hands each item to the rules with the record as a tunnel parameter.
- **`repeat: true`** on a field whose input has several values (a JSON array, an element the record has several
  of) writes one element per value: the nearest element on the path the schema lets repeat, so
  `EventSource/User/Groups/Group/Name` with `repeat` writes one `Group` per value, and
  `EventDetail/<Action>/Data` with a `data_name` writes one `Data` per value. Nothing else may be mapped below
  the repeated element; `transform` applies to each value. A key=value key given several times (`rcpt=` once per
  recipient) is such a field: without `repeat` only its first value is read.
- **`drop_when`**: records (or items) to leave untranslated on purpose, each with a reason: heartbeats, test
  traffic, service accounts. They are tried before the event rules, write no Event and raise no "no mapping
  matched" warning; the reasons appear in the XSLT and the documentation. `survey_feed`'s `set_shape_handling`
  records the user's decision for an existing feed; `drop_when` is how the mapping carries it out.

The same filtering exists where other schemas are written: `build_reference_xslt` takes `drop_when` (records to
keep out of the reference data), and `draft_index_mapping` takes `drop_when` as XPath tests on an Event
(`"EventDetail/TypeId = 'Heartbeat'"`) for events the index must not hold.

### Text fields holding several values

A message string such as `2026-10-01 10:00:00 alice LOGIN Successful login from 10.0.0.1` is parsed with
`extract`: a regular expression whose capture groups become fields, usable in `common`, `events` and `when` like
any input field. An extraction can read a field an earlier one produced. Not `substring-before()` /
`substring-after()` chains, which break on the first value with a space or a missing part.

```json
{"input": "json", "json_layout": "lines",
 "extract": [{"field": "message", "regex": "^(\\S+ \\S+) (\\S+) (\\S+) (.*)$", "names": ["ts", "user", "action", "desc"]},
             {"field": "desc", "regex": "^(Successful|Failed)", "names": ["result"]}],
 "common": [{"path": "EventTime/TimeCreated", "field": "ts", "time_format": "yyyy-MM-dd HH:mm:ss", "timezone": "UTC"},
            {"path": "EventSource/User/Id", "field": "user"},
            {"path": "EventDetail/Description", "field": "desc"}],
 "events": [{"name": "logon", "when": [{"field": "action", "equals": "LOGIN"}],
             "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                        {"path": "EventDetail/Authenticate/Outcome/Success", "field": "result",
                         "map": {"Successful": "true", "Failed": "false"}}]}]}
```

Text of key/value pairs (`Event: [User] Action: [Login] By User: alice (Item Id: 219); Source IP: 10.0.0.1`, or
`user=alice src="10.0.0.1"`) is drafted by `draft_translation_mapping` as `pairs` entries, one a text field and the
way it writes its pairs: `{"field": "body", "style": "quoted", "keys": ["user", "src"]}` (styles `equals` key=value,
`quoted` key="value", `colon` Key: value, `bracketed` Key: [value]; `names` for a key named otherwise, `{"Source IP":
"src_ip"}`). Each key is a field named for it in snake case (`source_ip` from `Source IP`), read like any field:
homed, ruled on (a key naming the kind, `Event`, gives the rules) and carried as Data. Each key is read as an extract
entry would be, and the XSLT writes them as one function called with the key (`mcp:keyed_value($text, 'Source IP')`).
The draft keeps the keys its sample shows more than once; add any it lacks to the entry's keys.

The generated XSLT holds `analyze-string(message, regex)` in a variable per template, named for the first field it
extracts (`ts_parts`), and reads each group from it; text two or more extractions read is the field's own variable
(`$message`), read once. XPath regular expressions have no lookaround and no named groups; use `(?:...)` for groups that are not
fields, and anchor the pattern. A record the pattern does not match gets no values from it, so its elements are
left out; a rule can test that with `{"field": "ts", "present": false}` (and `drop` it, or map it to `Unknown`
with `allow_unknown` set to the reason, in the user's words: a rule that writes `Unknown` for sample records is
refused without it, as the kind it singles out nearly always has an action element, and the user confirms the
reason when the XSLT is saved, seeing what those records hold). `allow_unknown` is refused for records that are
connections or logons, or whose values give a rule for each of them, with the rules to use; a schema error in an
action element is fixed, not avoided with `Unknown`. Only if the user, shown those rules, still wants Unknown (they
can say so in the chat), set `keep_unknown: true` with `allow_unknown`: they confirm it in the form, which shows
what the values suggest. Never set it to get past the refusal yourself. The user agrees once per rule: the XSLT keeps
what they agreed to, and saving it again asks only about a rule not agreed yet, or whose conditions or reason changed.

`EventDetail/TypeId` is required. Read from a field, it would be left out of events whose records lack that field (the
rule for the rest, often): there the generator falls back to the rule's name and warns. Give TypeId a `default` to
choose another value.

TypeId names the kind of event, so each kind gets its own: a TypeId the sample shows to be the same for events of
different rules (a category alone, `User` for logons and logoffs alike) is refused. Write it in the format the standing
instructions (AGENTS docs) set for TypeIds, where they set one (`<Category>-<Action>`, say, or the vendor's event
code); otherwise join the fields that tell the kinds apart, `{"path": "EventDetail/TypeId", "join": ["event",
"action"]}` (`User-Login`, `User-Logout`), or give each rule a value of its own.

A field no element of the schema means (a rule id, a byte count, a vendor's key) is carried as `Data` (a path ending
`/Data`, with `data_name`) on the element it describes: `destination_key` under
`EventDetail/Network/Connect/Destination/Data`, a source zone under `.../Source/Data`, anything else under the action
element itself. Never invent an element for it, and never leave the event `Unknown` because of it. A rule's
`data` list is short for those: `"data": ["rule_id", "bytes_sent"]` carries each as Data of the rule's action
element, named after the field. Keep it when you change the element; the drafts use it. A build's
translation XSLT written by hand (`save_xslt`) whose records come out as `Unknown` doesn't step clean: only
`build_translation_xslt` can agree Unknown with the user.

Data holds Data: to group values, give the entries a `data_parent`, the Name of the Data they're written in.
`{"path": "EventDetail/Create/Document/Data", "data_parent": "mentions", "data_name": "mention", "repeat": true,
"field": "mentions"}` writes `<Data Name="mentions"><Data Name="mention" Value="..."/>...</Data>`, one `mention`
per value; entries with the same `data_parent` share its Data, written only when one of them has a value.
`"data_parent": "client/geo"` writes the entry in a `geo` Data inside `client`. Every Data has a Name, so each
value is a named Data of its own: `<Data Value="tim"/>` without a Name isn't valid.

## Style

The mapping's `style` decides how the XSLT reads. Take it from an XSLT style section in the standing instructions
(AGENTS docs) when there is one, or from what the user asks for; otherwise leave the defaults. The indexing XSLT
(the index plan's `style`) and the CEF XSLT (`draft_cef_mapping style=`) take the same style: naming, variables and
layout apply to them as here, a template a top-level object (or Event section, or CEF part):

| Setting | Default | Effect |
| --- | --- | --- |
| `naming` | `snake_case` | Variables, templates, modes and the XSLT's own functions: `client_ip`, `event_source`, `action_to_success`. Also `camelCase`, `PascalCase`, `kebab-case`. |
| `variable_min_reads` | 3 | A field read this often in a template (its guard and value are two reads) goes in a variable. Fewer reads stay inline; text two extractions read is always a variable. 1: always variables. |
| `variables` | `just_in_time` | Where variables are declared: `just_in_time`, immediately before the first element that reads each, inside the innermost element holding every read; `top`, at the start of their template (or of the one rule that uses them). |
| `inline_map_max_keys` | 3 | A value map used by one element with at most this many keys is written inline as an `if`; longer or shared ones become one `xsl:map`. 0: always `xsl:map`. |
| `function_min_uses` | 2 | A conversion this many elements use (a time format, or a `strip_domain`, `domain` or `digits` transform) is declared once as an `xsl:function` (`mcp:parse_time`, `mcp:strip_domain`) and called; fewer stay inline. 0: always inline. |
| `data_values` | `interpolated` | How a computed `Data` value is written: `interpolated`, `<Data Name="x" Value="{...}"/>`; `attribute`, `<Data Name="x"><xsl:attribute name="Value" select="..."/></Data>`. An expression holding a brace keeps `xsl:attribute` (or its variable). |
| `data_entries` | `function` | How a `Data` entry read from the input is written: `function`, one line, `<xsl:sequence select="mcp:data('name', value)"/>`, the XSLT's own `mcp:data` writing it only when the value is present (not blank, nor a `nil_values` value); `guarded`, an `xsl:if` around each `Data` element. An entry with a map, a default or a conversion is always guarded. |
| `data_run_min` | 4 | `Data` entries several rules write the same way, this many or more in a row, are written once as a template (`data_logid_to_dstintfrole`, after its first and last) and applied in each rule's place; each rule keeps its order. A large number keeps every entry in its rule. |
| `data_names` | `as_given` | `Data` Names in a style, applied to every `data_name` of the mapping: `server_node` is `ServerNode` in `PascalCase`; a name already in the style is kept (`IPAddress`). Also `snake_case`, `camelCase`, `kebab-case`. The names are the mapping's own from then on, so the documentation and the index fields follow them. |
| `layout` | `modes` | `modes`: each event kind is a template rule with its own mode (`match="node()" mode="eventTypeLogon"` with `camelCase` naming), applied to the record with `select="."` from the record template's `xsl:choose`; parts several kinds share are mode templates too. `named`: the same with named templates and `xsl:call-template`. `inline`: every kind written in the `xsl:choose`. |

Elements that come out the same in several rules (EventTime, EventSource, ...) are written once, in a template of
the layout's kind, whatever the style.

Each rule's template is headed by a comment saying what it does: the records it handles (its conditions, or
"records no other rule matches"), the event it writes (action element and TypeId), the fields it reads (which are
extracted, and from what), and, for a rule kept `Unknown`, why. A shared template's comment names the rules that use
it. Nothing to write by hand: they come from the mapping.

## Regular expressions in a mapping

An `extract` regex is written once, as XPath reads it: a literal `[` is `\[`. Only the JSON of the call doubles
each backslash (`"\\["`); the server puts the regex in the XSLT as it is, so nothing escapes it again. Copy the
separators from the sample text exactly: an en dash (–) is not a hyphen. `build_translation_xslt` runs each regex
on the sample's text: one that matches none is a problem that says where it stops matching and what the text has
there (naming characters such as an en dash or a no-break space); one that matches only some is a warning with a
text it misses. Fix what it names, rather than the escaping.

## Reuse

Existing pipelines `xsl:import` shared XSLTs by document name (e.g. `IP Lookup`) and keep field
mappings in Dictionary docs. Find them with `find_in_content` and `describe_template`
before writing new code, and check imports resolve with `check_xslt`. A mapping uses a shared XSLT two ways:
its named templates through `shared` (called in the element's place), and its `xsl:function`s through
`functions` (`href`, `prefix`, `namespace`, as `describe_template`'s `shared_xslt` gives them), called in any
xpath: `"xpath": "gs:parseTimestamp(data[@name='eventtime']/@value)"`. Conditions compare strings, so test a
boolean function as `string(gs:isLocalIpAddress(...))` with `equals: 'true'`. An xpath calling a prefix that
no `functions` entry binds is a problem. Stroom steps the XSLT with its imports; the local checks skip these
xpaths.

The environment's common elements are written as its pipelines write them: `draft_translation_mapping` pre-fills, as
`shared` entries, the shared templates two or more of the environment's event pipelines call (each writing one
element, with no parameters), in place of what it would have mapped there, and notes others. `build_translation_xslt`
and `save_xslt` warn when an XSLT doesn't call a shared template its stage's pipelines call, and when it imports a
required import but calls none of its named templates.

## Checking a draft

1. `check_xslt` for well-formedness, namespaces, function names and imports.
2. `step_pipeline` with `draft_code` for one record, then `step_sample` with `draft_code` for all of
   them. Nothing is saved; errors come back per record and element.

## Less repetition, by the generator

Each `Data` entry read from the input is one line, `<xsl:sequence select="mcp:data('logid', mcp:quoted_value($body,
'logid'))"/>`: the value read once, and written only when present. A run of `Data` entries several rules write the
same way is a template of its own, applied in each rule's place (`data_run_min`). Seen: 135 guarded `Data` elements
were 31 KB of a 50 KB FortiGate translation; with both, the translation is 25 KB, writing the same Events.

Extractions that differ only by the key they find in one text field (`key="..."` for thirty keys) become one function
a shape, called with the key: `mcp:quoted_value($body, 'dstintfrole')`, in place of an `analyze-string` variable a key
declared again in every template that reads it. A part several rules write the same way is written once as a template
of its own and applied from each (EventSource, and below an action element, a Source, a Destination, a Rule or an
Outcome: the action element itself, Deny or Permit, Authenticate or View, may differ). These come from the mapping,
which is what is changed; the XSLT is regenerated from it.

## Regenerating a saved XSLT

The mapping an XSLT was generated from is kept with it. `build_translation_xslt uuid=<xslt>`, with no mapping and no
changes, regenerates it from that mapping as the generator writes it now: after a server upgrade changes the style
defaults, this is how an XSLT is brought up to them. Never send the kept mapping back whole; `read_document`
summarises it (`mapping=true` shows it, to read). `build_status` says when an XSLT was written by an earlier generator
(and is unchanged since), as against edited by hand: a hand edit is carried into the mapping (`changes=`) first; an
edit no mapping can hold (an `xsl:iterate`, say, inline or building a variable) can't be, so it is the user's to keep
(the XSLT stays as it is, with no generated change) or drop. A save that would undo a hand edit is refused, saying what it changed (`discard_hand_edit=true` only when the user says to drop
it). Where a change is to a field the edit changed too (a constant they changed, say), the user is asked first whether
to overwrite the XSLT with the change, else per field whether to keep their edit or use the proposed one
(`hand_edit_choices`, from their answers, never chosen for them). For a hand edit it shows what differs (`-` what the mapping generates, `+` what
the XSLT has). `rebuild_mapping uuid=<xslt>` carries a hand edit into the mapping, or reads a lost mapping back from
the XSLT alone (its Documentation tab cleared; lookups, dictionaries and imported XSLTs' templates and functions too; a call of
an imported function stays an xpath entry, reported under `calls_imported_functions`):
it keeps every entry that still reads as it generates, reads the rest from the XSLT, regenerates the XSLT from the
result and steps both over the pipeline's original sample streams (`stream_ids` for others), and saves only when every record's output is the same (the user confirms). An edit a mapping can't express (an element
written even when its value is empty) shows as a difference: nothing is saved unless the user accepts it
(`accept_differences=true`). It refuses while anything inside an event couldn't be read back (listed as `unread`:
an `xsl:iterate` added by hand, say, which stepping misses when no sample record reaches it), unless the user accepts
losing it (`accept_unread=true`). Small changes can still be made by hand in the mapping with `changes=`.
