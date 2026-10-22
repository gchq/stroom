# Data Splitter recipes

A Data Splitter text converter (type `DATA_SPLITTER`, used by `DSParser`) turns text into
`records:2` XML: one `<record>` per match, with `<data name value>` for each field.

## Generate it from a spec

`build_data_splitter` takes the sample's text, infers the spec from its format (delimited with or without a header,
key=value, syslog with a key=value body) and runs it locally: the records it produces, the lines that match
nothing, and the field names the mapping may use. Give it a `spec` only when the inferred one is wrong or the
text is free-form (a regex with a name per group). Give the same spec to `build_translation_xslt` as `splitter`
so the mapping is checked against those records. A sample is the file's text, never a path: the server cannot
read the client's files, and refuses a path with that instruction.

| Spec | Example |
| --- | --- |
| Delimited with a header line | `{"kind": "delimited", "delimiter": ",", "header": true, "quote": "\""}` |
| Delimited, named columns | `{"kind": "delimited", "delimiter": "\|", "header": ["time", "user", "action"]}` |
| Regex, a name per group | `{"kind": "regex", "pattern": "^(\\S+) (\\S+) (.*)$", "names": ["time", "user", "message"]}` |
| key=value pairs | `{"kind": "key_value", "delimiter": " ", "pair_separator": "=", "quote": "\""}` |
| Syslog, body parsed further | `{"kind": "syslog", "rfc": "rfc3164", "body": {"kind": "key_value"}}` |
| CEF, alone or after syslog | `{"kind": "cef"}` (a syslog header before it is inferred as `body`) |
| Records over several lines (Java stack traces) | `{"kind": "regex", "record_start": "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", "pattern": "(?s)^(\\S+ \\S+) +(\\w+) +\\[([^\\]]*)\\] (\\S+) - (.*)$", "names": ["time", "level", "thread", "logger", "message"]}` |
| Records ending at a marked line (Kubernetes CRI) | `{"kind": "regex", "record_end": "\S+ (?:stdout\|stderr) F ", "pattern": "(?s)^(\S+) (stdout\|stderr) [PF] (.*)$", "names": ["time", "stream", "message"]}` |
| Quoted values holding new lines | `{"kind": "delimited", "delimiter": ",", "header": true, "quote": "\"", "quoted_newlines": true}` |
| A separator of several characters | `{"kind": "delimited", "delimiter": " ~ ", "header": ["time", "user", "action"]}` |
| Syslog, then delimited columns (Palo Alto) | `{"kind": "syslog", "rfc": "rfc3164", "body": {"kind": "delimited", "delimiter": ",", "header": ["future_use1", "receive_time", ...]}}` |
| Columns named by a directive line (W3C, IIS, Blue Coat, Zeek) | `{"kind": "delimited", "delimiter": " ", "header": true, "quote": "\"", "comment_prefix": "#", "header_directive": "#Fields:", "directives": ["#Software:", "#Remark:"]}` |

`body` parses one field further (syslog's `message`, or a regex group) and adds its fields; the recipes below
are what it writes.

## Delimited with a header row

```xml
<?xml version="1.1" encoding="UTF-8"?>
<dataSplitter xmlns="data-splitter:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="data-splitter:3 file://data-splitter-v3.0.xsd" version="3.0">
  <split delimiter="\n" maxMatch="1">          <!-- first line: remember the column names -->
    <group>
      <split delimiter=",">
        <var id="heading" />
      </split>
    </group>
  </split>
  <split delimiter="\n">                        <!-- every other line: one record -->
    <group>
      <split delimiter=",">
        <data name="$heading$1" value="$1" />
      </split>
    </group>
  </split>
</dataSplitter>
```

Change `delimiter` for tab (`\t`), pipe or semicolon data. Quoted fields need
`<split delimiter="," containerStart="&quot;" containerEnd="&quot;">`. A doubled quote inside a quoted value
(`"Said ""bye"" and left"`) stays doubled: Data Splitter has no way to make it one (its `escape` attribute breaks
the quoting), so the mapping reads such a field with `transform: unescape_quotes`; the draft sets it where the
sample has them.

## Records over several lines

A Java (or Python, .NET) log entry and its stack trace, a wrapped message: the record is not a line. `record_start` is a
regex a record's first line matches at its start (its time, usually); the lines after it until the next such line are
part of it. `profile_sample` recognises such text (`multi-line text`) and `build_data_splitter` infers the start, with a
pattern that takes the time and the rest as `message`: give the record's own pattern (level, thread, logger), starting
`(?s)` so its last `.*` takes the trace's lines. In the mapping, a regex on such a message must cross lines too:
`[\s\S]*`, not `.*`, which stops at a line break in XPath.

A record whose last line is marked is the reverse: `record_end` is a regex its last line matches at its start, and
the lines before it since the last such line are part of it. Kubernetes writes a container's log on the node as the
CRI does, `<time> <stream> <P|F> <content>`: a long message is split into partial (`P`) lines at any character and
ended by a full (`F`) line. `profile_sample` recognises it (`cri`) and `build_data_splitter` infers the spec above;
the continuation lines keep their prefixes in `message`, so the mapping joins the parts with an `extract` (or field)
xpath `replace(data[@name='message']/@value, '\n\S+ (stdout|stderr) [PF] ', '')`.

Quoted values that hold new lines (a CSV comment over two lines) are another case: `quoted_newlines: true` ends a
record at a new line outside quotes only (`profile_sample` sets it when the sample has one).

## Lines that are not records

Stroom's Data Splitter skips a line its pattern doesn't match and reports an error for it; a delimited line short of
columns still becomes a record, its missing columns empty. `profile_sample` names such lines (`malformed_lines`:
line number, reason, text) from up to 5,000 lines of the sample, so they can be told to the user and dropped by name
(`drop_when`) rather than lost unseen. A byte order mark before the first line is taken first by every generated
splitter: Stroom keeps it, and it became part of the first field's name.

## Separators and bodies

A separator of several characters (` ~ `, `||`, ` :: `) or an unusual one (`^`) is recognised when every line has it
the same number of times; a split takes it as it is. A syslog header followed by delimited columns (Palo Alto's CSV)
is read as `syslog` with a `delimited` body whose columns are named by position (`col1` ...): name them from the
vendor's documentation, as the source has no header.

## Columns named by a directive line

Some logs name their columns in a comment-like directive rather than a first line: W3C extended logs (IIS, Blue
Coat: `#Fields: date time c-ip ...`), Zeek (`#fields	ts	uid ...`, tab-separated) and the like, with other directives
around it (`#Software:`, `#Remark:`, `#path`). `profile_sample` and `build_data_splitter` recognise them: the
directive whose value has as many names as the data lines have columns names them. The spec says so with:

- `comment_prefix`: lines starting with it are not records (`#`).
- `header_directive`: the directive naming the columns. Its columns hold for the lines after it, until it appears
  again: a log that restarts mid-file (an upgrade, a new day) names them afresh, often reordered or with more.
- `directives`: other directives whose value each record after them carries, as a field named as in the file
  (`#Software`, `#Remark`): the device, its software and the like, which the lines themselves often lack. Pull a part
  out of one with an `extract` regex on that field (`#Remark: 4111140007 "proxy01" ...` gives the proxy's name).

Before saving, read the dry run: every data line should be a record (`unmatched_lines` empty), the field names should
be the directive's, and a record after a restart should have the new columns.

## Without a header

Name the columns in the spec: `{"kind": "delimited", "header": ["time", "user", "src_ip", "action"]}`. Give
the same spec to `draft_translation_mapping` and `build_translation_xslt` as `splitter`, so they use those names
rather than inferring `col1`, `col2`, ...

## Syslog and other free text

```xml
<regex pattern="^&lt;(\d+)&gt;(\w{3} +\d+ \d{2}:\d{2}:\d{2}) (\S+) ([^:\[]+)(?:\[(\d+)\])?: (.*)$">
  <data name="pri" value="$1"/><data name="time" value="$2"/><data name="host" value="$3"/>
  <data name="tag" value="$4"/><data name="pid" value="$5"/><data name="message" value="$6"/>
</regex>
```

Split the message further with a nested `<group value="$6">`. Key=value pairs are one `<regex>` matched
along the text, its value in one group whether quoted or not:

```xml
<regex pattern="\s*([^=\s]+)=&quot;?((?&lt;=&quot;)[^&quot;]*(?=&quot;)|(?&lt;!&quot;)[^\s&quot;]*)&quot;?">
  <data name="$1" value="$2" />
</regex>
```

Not `<split delimiter="=">` with `$2`: a split has only `$1`, and Stroom fails with "Group number 2 not found". Nor a
value made of two groups (`$2$3`) or two alternative regexes: a group that took no part in the match can't be
named, and a group's regexes aren't tried in turn at each place. `build_data_splitter` writes this for you.

Many syslog and key=value sources write `-` for none (RFC 5424's NILVALUE): the mapping's `nil_values: ["-"]` leaves
those elements out instead of writing `-` (the draft sets it when the sample has them).

## CEF

`CEF:Version|Device Vendor|Device Product|Device Version|Signature ID|Name|Severity|Extension`, alone or after a
syslog header. The header's fields are `cef_version`, `cef_vendor`, `cef_product`, `cef_device_version`,
`cef_signature_id`, `cef_name` and `cef_severity`; the extension's pairs are fields by their own keys (`suser`, `src`,
`act`, `msg`, `rt`...), a value running to the next ` key=`, spaces and all. A syslog header before `CEF:` gives
`pri`, `time` and `host`. `profile_sample` names the format and `build_data_splitter` infers the spec
(`{"kind": "cef"}`); the draft reads `act` for the kind of event, `suser` for the user, `src`/`dst` for addresses.

Step the pipeline after each change: the `dsParser` element's output shows the records produced.

## XML fragments (XML_FRAGMENT converter)

Several root elements, e.g. one `<Event>` per line with no document root, are not XML the `XMLParser` can read.
The `XMLFragmentParser` reads them through a text converter of type `XML_FRAGMENT`: the wrapper document the
fragments are parsed inside, where the entity `fragment` is the stream. Environments usually have wrappers of
their own: `profile_sample` returns the one to use as `text_converter.code` (the environment's own when there is one,
named in `text_converter.environment`), and `build_data_splitter` with `save_as` saves it in the build. With none,
the standard one is a `records` wrapper:

```xml
<?xml version="1.1" encoding="UTF-8"?>
<!DOCTYPE records [
<!ENTITY fragment SYSTEM "fragment">
]>
<records xmlns="records:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
&fragment;
</records>
```

An `<Events xmlns="event-logging:3">` wrapper is as common (fragments that are event-logging `<Event>`s already
always take one; `X.Y.Z` is the event-logging version chosen for the build):

```xml
<?xml version="1.1" encoding="UTF-8"?>
<!DOCTYPE Events [
<!ENTITY fragment SYSTEM "fragment">
]>
<Events xmlns="event-logging:3" xmlns:stroom="stroom" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="event-logging:3 file://event-logging-vX.Y.Z.xsd" Version="X.Y.Z">
&fragment;
</Events>
```

Two things follow from the wrapper:

- **Namespace.** A fragment that declares no `xmlns` of its own takes the wrapper root's default namespace, so the
  XSLT reads it in `records:2` inside a `<records>` wrapper and in `event-logging:3` inside an `<Events>` one: set
  the mapping's `xml_namespace` to it (`records:2` is the default for `xml_fragments`). A fragment with its own namespace
  (Windows event XML, say) keeps it, and the mapping gives it as `xml_namespace`. Read in the wrong namespace, every
  record comes out as an empty `Events`: `step_sample` blocks on that.
- **Records.** The fragments sit under the wrapper's root: mapping `input: xml_fragments, record: Event` matches
  `/` and selects `*/Event`. The split filter still hands the XSLT one fragment at a time.

The pipeline needs an `XMLFragmentParser` element. Use a template whose chain has one (`find_pipeline_templates`
shows `parser`, and `child_must_supply` names its `textConverter`); when the environment has none, it says so, and
`create_pipeline` from a translation template whose parser is the `XMLParser` (whatever it is called here) with
`replace_parser: XMLFragmentParser` puts one in place of the `XMLParser`, with the converter on
`xmlFragmentParser.textConverter`.
