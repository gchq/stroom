# Translation mapping reference

What a translation mapping (the input to `build_translation_xslt`) may hold, in one place. Start from
`draft_translation_mapping`'s draft and change only what it gets wrong; the xslt guide explains each part at length
(stroom://guide/xslt).

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
| `repeat: true` | One element per value of a field holding several |
| `time_format`, `timezone` | Time parsing: see below |
| `data_name`, `data_parent` | A `Data` entry: see below |

## Times

`time_format` is a Java time pattern with letters that aren't pattern letters quoted:
`yyyy-MM-dd'T'HH:mm:ss.SSSX`. An epoch time is named, never described: `epoch_s` (seconds, a fraction allowed),
`epoch_ms`, `epoch_us`, `epoch_ns`. `timezone` (an IANA name, e.g. `UTC`, `Australia/Sydney`) only for a time that
carries no zone or offset of its own. `profile_sample` and the draft give the pattern the sample's values fit.

## Data

A field no element means is carried as `Data`: a path ending `/Data` with `data_name`, under the element it describes
(`EventDetail/Network/Permit/Source/Data`) or the action element. `data_parent` groups entries in a named Data. A
rule's `"data": ["rule_id", "bytes"]` is short for Data entries of its action element, each named after its field.

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

`build_translation_xslt changes=` sends only what changes, merged into the mapping last sent or saved:

- `events`: a rule by `name`. A new one is added after the others. For one already there, each key given replaces
  its own, and its `fields` change entry by entry: an entry replaces the rule's entry with the same `path` (and
  `data_name`), or is added; `{"path": ..., "remove": true}` removes one. `"replace": true` replaces the rule whole;
  `{"name": ..., "remove": true}` removes it.
- `common`: entries by `path` (and `data_name`), replaced, added, or removed with `"remove": true`.
- Any other key replaces that key whole.

`changes_applied` in the result says what each change did.
