# Data Splitter recipes

A Data Splitter text converter (type `DATA_SPLITTER`, used by `DSParser`) turns text into
`records:2` XML: one `<record>` per match, with `<data name value>` for each field.

## Generate it from a spec

Write the Data Splitter with `build_data_splitter`, never by hand first.

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
| LEEF 1.0/2.0, alone or after syslog (`LEEF:2.0\|Vendor\|Product\|Version\|EventID\|^\|src=...^usrName=...`) | inferred: a regex for any syslog header and LEEF's header (`leef_version`, `leef_vendor`, `leef_product`, `leef_product_version`, `leef_event_id`, 2.0's `leef_delimiter`), the attributes read as key=value pairs separated by the delimiter the header declares (a character or its hex, `x5E`) or a tab (1.0) |
| CEF, alone or after syslog | `{"kind": "cef"}` (a syslog header before it is inferred as `body`) |
| Records over several lines (Java stack traces) | `{"kind": "regex", "record_start": "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", "pattern": "(?s)^(\\S+ \\S+) +(\\w+) +\\[([^\\]]*)\\] (\\S+) - (.*)$", "names": ["time", "level", "thread", "logger", "message"]}` |
| Records ending at a marked line (Kubernetes CRI) | `{"kind": "regex", "record_end": "\S+ (?:stdout\|stderr) F ", "pattern": "(?s)^(\S+) (stdout\|stderr) [PF] (.*)$", "names": ["time", "stream", "message"]}` |
| Quoted values holding new lines | `{"kind": "delimited", "delimiter": ",", "header": true, "quote": "\"", "quoted_newlines": true}` |
| A separator of several characters | `{"kind": "delimited", "delimiter": " ~ ", "header": ["time", "user", "action"]}` |
| Escaped delimiters and quotes (`a\,b`, `"say \"hi\""`) | `{"kind": "delimited", "delimiter": ",", "header": true, "quote": "\"", "escape": "\\"}` |
| key=value, values holding spaces unquoted | `{"kind": "key_value", "delimiter": " ", "spaced_values": true}` (inferred when the sample needs it) |
| Labelled fields anywhere, in any order | `{"kind": "fields", "fields": {"user": "user=(\\S+)", "src": "src=(\\S+)"}}` |
| Records separated by blank lines | `{"kind": "key_value", "delimiter": "\n", "blank_line_records": true}` |
| Keep a record the spec doesn't match | add `"catch_all": true` (not delimited): it is kept whole as the field `unparsed` |
| Lines in several formats (syslog, key=value, timestamped text) | `{"kind": "alternatives", "parts": [{"kind": "syslog", "variant": "syslog"}, {"kind": "key_value", "variant": "key_value"}, {"kind": "regex", "variant": "text", "pattern": "^(\\S+ \\S+) (.*)$", "names": ["time", "message"]}], "catch_all": true}` |
| Lines tied into records by a key (auditd's `msg=audit(...)`) | `{"kind": "key_value", "delimiter": " ", "quote": "\"", "single_quotes": true, "record_key": "(?<!\\S)msg=(\"[^\"]*\"\|[^\\s\"]+)", "key_field": "msg", "line_name": "(?:[^\\s=]+=\\S*\\s+)*?type=\"?([A-Za-z_][\\w.-]*)\"?(?=\\s\|$)", "lead_field": "type"}` (add `"skip_lines": "\\s*[-=*#~_]{3,}[ \\t]*(?=\\n\|$)\|\\s*time->"` for ausearch's output) |
| Sections between marker lines carrying the key (ModSecurity's `--5f005215-B--`) | `{"kind": "regex", "pattern": "(?s)^[^\\n]*\\n?(.*)$", "names": ["text"], "record_key": "(?<![^\\n])\\-\\-(\\w+)\\-\\w+\\-\\-[ \\t]*(?=\\n\|$)", "line_name": "\\-\\-\\w+\\-(\\w+)\\-\\-[ \\t]*(?=\\n\|$)", "lead_field": "first_section", "join_unkeyed": true}` |
| Text around the pairs (a prefix, bare flags, verbs: iptables' `[12.3] DROP: IN=eth0 ... DF PROTO=TCP SYN`) | add `"words": true` (key_value): each run of text that isn't pairs is kept as the field `words` (given again for each run; a field reads the first, `repeat: true` each) |
| Pairs separated by `, ` or `; `, keys holding spaces (Cisco ISE's `..., Device IP Address=10.1.2.3, ...`) | `{"kind": "key_value", "delimiter": ", "}` (inferred from what precedes the pairs): a delimiter with white space round it reads values to the next delimiter, keys may hold spaces straight after it, and a trailing delimiter ends a record |
| Quoted `key:"value"` pairs (Check Point's `action:"Accept"; ifdir:"inbound"`) | `{"kind": "key_value", "pair_separator": ":", "quote": "\"", "delimiter": "; "}` (inferred; in a syslog record's bracketed structured data with an empty message, read there as the body) |
| The same few words before every record's pairs (`2026-09-28T15:00:00Z fw01 action=allow ...`) | inferred as a regex reading the words as fields (a time as `time`, others `prefix1`, `prefix2`...) with the pairs as its `body` |
| Values in single quotes too, some of them pairs | add `"single_quotes": true` (key_value): `a='two words'` read without its quotes; `msg='op=login res=success'` read as its pairs |
| Entries whose stack traces are kept apart | add `"record_start": <profile's record_start>, "continuation": true`: each record's first line is read by the spec, the lines after it kept whole as `continuation` |
| Syslog within syslog: a relay's header before the source's own line (`<13>Nov  9 10:00:01 relay01 <38>Nov  9 09:59:58 host sshd[2211]: ...`) | `{"kind": "syslog", "rfc": "rfc3164", "name_prefix": "relay_", "body": {"kind": "syslog", "rfc": "rfc3164", "body": {"kind": "key_value"}}}` (inferred): the relay's fields are `relay_time`, `relay_host`... and `relay_message` (the source's whole line), the source's keep their own names, so its time and host are the event's; with lines sent directly too, inferred as one regex whose relay header is optional, the `relay_*` fields empty on a direct line |
| A collector's words before each syslog line (`2026-11-09T10:00:01Z collector01 <38>Nov  9 ...`) | inferred as a regex reading the words (a time as `relay_time`, the others `relay_word2`...) with the line, `relay_line`, read by a syslog `body` |
| Syslog, then delimited columns (Palo Alto) | `{"kind": "syslog", "rfc": "rfc3164", "body": {"kind": "delimited", "delimiter": ",", "header": ["future_use1", "receive_time", ...]}}` |
| Columns named by a directive line (W3C, IIS, Blue Coat, Zeek) | `{"kind": "delimited", "delimiter": " ", "header": true, "quote": "\"", "comment_prefix": "#", "header_directive": "#Fields:", "directives": ["#Software:", "#Remark:"]}` |

A body's field named as one before it (Palo Alto's `app` column after the syslog header's `app`) is a second data
element of that name: a field reads the first, so read the body's with an xpath (`data[@name='app'][2]/@value`) or
rename the column.

`body` parses one field further (syslog's `message`, or a regex group) and adds its fields; the recipes below
are what it writes.

## Stroom's own parser checks it

`build_data_splitter` also runs the converter through Stroom's own Data Splitter parser on the sample and returns
`stroom_check`: the errors Stroom reports (with `error_hints`), and any record it writes differently from the dry
run. Processing does what Stroom's parser does, so fix the spec until `stroom_check` says `matches`; a converter
Stroom loses records with is not saved. The check covers only the sample text read: records further in (longer, or
laid out differently) are checked by processing the whole feed. Stroom's errors name the converter's part by its id: `header`, `record`,
`fields` (a record's expressions), `blankLine`, `body`, `unparsed`.

What Stroom does that is easy to miss (each seen in Stroom):

* A cell from a split loses the white space round it, inside quotes too; a cell past the header's last column is
  written with no name.
* Blank lines are passed over by the converter `build_data_splitter` writes (otherwise each is a record of empty
  fields, with no error).
* Lines ended by a carriage return alone: Stroom takes carriage returns out, so the stream is one line and gives no
  records, with no error. `build_data_splitter` refuses to save it: the source ends its lines with a new line, or a
  `FindReplaceFilter` before the parser turns them into new lines.
* A key=value value holding the delimiter unquoted (`zones=Zone 1`) is cut at it and the rest reported:
  `spaced_values` reads the pairs from the end (Data Splitter's `reverse`), as a key never holds the delimiter.
* `ignoreErrors` is never added (save_text_converter refuses it): it hides the errors for text no expression
  matches, which is then lost without a word. `catch_all` keeps such records instead, as `unparsed`, for the
  mapping to keep as Unknown and review_coverage to find; agree it with the user.
* A converter written by hand (`save_text_converter`) gets the checks Stroom's DSParser makes: the
  `data-splitter:3` namespace and `xsi:schemaLocation`, valid against Stroom's Data Splitter schema (each field a
  `<data name="..." value="$1"/>` inside the `<regex>` or `<split>` that matches it), and run through Stroom's
  parser on the build's sample streams. When `build_data_splitter` won't save, fix the spec (its `not_saved` says
  what to try): writing the XML by hand is no way round the checks.

## Long records: the read buffer

A Data Splitter reads its input through a buffer of `bufferSize` characters, 20,000 by default, refilled once half
of it is read: only a record up to half the buffer is sure to fit (`bufferSize` at least twice the longest record). A
longer one is lost, and processing reports that a top-level expression "matched too much content and exhausted the
buffer". The buffer is an attribute of the converter's root element, `<dataSplitter ... version="3.0" bufferSize="100000">`
(20,000 to 1,000,000,000), not a property of the DSParser element. `build_data_splitter` sets it from the sample's
longest record (a record over several lines counts whole), at four times that, when the default leaves too little
room; given `stream_ids`, it reads those streams through whole for it, not just the start it profiles. When
records are lost for their length in other streams, `measure_records` with those streams gives their longest
record and the `buffer_size` that holds it (a line, or with `quote`, the lines a quoted value spans joined; a
record of a marked line or a blank-line block is measured only by its lines). Build the converter again with
`build_data_splitter`, the same spec and that `buffer_size` (without `save_as`), and replace the saved one with
the `converter` it returns (`save_text_converter` with its `uuid`): don't edit the XML by hand. Then step and
process those streams again.
Tell the user the size chosen and why.

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
(`"Said ""bye"" and left"`) stays doubled: Data Splitter has no way to make it one (its `escape` attribute is for
an escape character before the quote, `\"`, and breaks quoting by doubling), so the mapping reads such a field with
`transform: unescape_quotes`; the draft sets it where the sample has them. An escape character (`a\,b`) is the
spec's `escape`: the converter's split then takes it out of each cell (`$2`).

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

## Lines in several formats

One stream can hold records in several formats: syslog lines, key=value lines and plain timestamped log lines
together. `profile_sample` says `mixed lines`, with each format's share of the records (`line_formats`; two or more
formats of at least a tenth each) and the records no format fits (`unrecognised`); a stream mostly of one format is
that format. `build_data_splitter` infers an `alternatives` spec: a part per format the records hold (a format under a tenth of
them too; timestamped text a part per shape of its time, so each has one time format), tried in order, the first
whose `match` fits a record's start reading it (a part's kind has its own match: a syslog header, CEF's, key=value
pairs from start to end, a regex's own pattern; a `fields` part needs one). A key=value line the part can't read
whole (an unclosed quote) is not the part's: it goes on to the next part, or `catch_all`. Each record is written with its part's `variant`
(`variant_field`, `variant` by default), and the mapping's rules tell the formats apart by it (`when` the variant
`equals` ...). A record a part's match fits but the part can't read is an error, not passed to the next part; with
`catch_all`, a record no part's match fits is kept as `unparsed`. Syslog messages some key=value and some not are
read the same way, by a body of two parts (`message_format`). Fix a part rather than writing the XSLT by hand.

A part is a line format, not a kind of event. Where a word of each record names its event (`SSLVPN LOGIN`, an event
id), read it into a field of the format's part. Never make a part for each wording of a message (`sshd_invalid_user`,
`sshd_failed_password`...): one part for the program's lines, its message whole. The events in it are told apart by
patterns in `classify_kinds`: a proposal per event with `field` (the message), `matches` (a regex in XPath's dialect,
e.g. `^Failed password for (?:invalid user )?(\S+) from (\S+)`), `names` for its capture groups (the user, the
address: fields the rule reads) and `within` (the listed kind it splits, `variant=syslog`; `*` when no kinds are told
apart). The user confirms them; what they leave of the kind stays to classify.

Lines holding only a time of day (`22:14:03.123 INFO ...`) are timestamped text too, read by their own part. A
header line holding a date for the lines after it (`=== Log opened 2026-10-11 ===`) is a `date header`, read by a
last part (variant `header`, its date as `date`) whatever its share, with `"carry": "header_date"`: the splitter
writes the date on every record after it (a var, read as `$carryN$1[0]`), so stepping a line alone sees it too
(mapping guide, Times). Without a header, the profile says the times have no date, and the draft asks where it
comes from.

Stack traces are grouped with their entry first, so a format is judged on records, not on the trace's lines: a line
that is indented, or a frame (`at ...`), cause (`Caused by:`), elision (`... 12 more`), exception line or Python
traceback, continues the record before it. The profile gives that `record_start` with `continuation: true`; the
splitter reads each record's first line with its spec and keeps the lines after it whole as `continuation`. Lines
before the first record start (a stream cut in a trace) are a record of their own.

## Lines tied by a key

Some sources write one event as several lines that share a key: auditd's lines
`type=SYSCALL msg=audit(1791709234.390:4506): ...` and `type=PATH msg=audit(1791709234.390:4506): ...` are one event. `profile_sample` finds such a key (`record_key`, the
field `key_field`): a key=value field on nearly every line whose value holds over runs of consecutive lines of
different kinds, and isn't a time. It also finds the field naming each line (`line_name`, `lead_field`), and says
how many records the lines make. The line name may come after other fields (an audispd forwarder's `node=web01`
first, kept). Separator lines between records (ausearch's `----` rows and `time->...` lines) are passed over
(`skip_lines`). Nothing here is auditd's: any key=value lines tied by a field work (a transaction log's `txn=`, its
lines named by `step=`), and a spec of another kind (a regex reading text lines) takes `record_key` and `line_name`
given by hand; only the profile's suggestion is for key=value lines. `build_data_splitter` infers the spec: consecutive lines holding one key are one
record (a backreference in the record's regex), the key written as `key_field`, the first line's name as
`lead_field` (the kind of event), and each line's fields nested under its name, read as `SYSCALL/exe` (a line name
given twice, such as several PATH lines, gives a field per line; a field reads the first, `repeat: true` each). A line
holding no key is a record of its own. The draft takes the time from an epoch in the key (`epoch_s`) and leaves the
key and each line's copy of it out of the fields.

The profile suggests a key only for key=value lines and marker lines. When one event's lines share a key held some
other way (a session or request id in text lines: PostgreSQL's `[4512] 6713a2f1.1190 LOG: ...` then `... DETAIL:
...`, a bracketed `[req-42]`, Cisco ISE's message id before its segment number), give the spec yourself:
`record_key` is a regex whose one group is the key, found anywhere in the line (anchor it as the line's layout
needs), and `line_name` a regex at the line's start whose one group names the line (`LOG`, `DETAIL`), with the
spec reading each line, e.g. `{"kind": "regex", "pattern": "(?s)^(\\S+ \\S+ \\S+) \\[(\\d+)\\] (\\S+) (\\w+):\\s+(.*)$",
"names": ["time", "pid", "session", "level", "message"], "record_key": "\\] (\\w+\\.\\w+) ", "key_field": "session",
"line_name": "\\S+ \\S+ \\S+ \\[\\d+\\] \\S+ (\\w+):", "lead_field": "level", "join_unkeyed": true}`
(each line's fields under its level: `LOG/message`, `DETAIL/message`). Ask the user which value ties an event's
lines when it isn't plain; `build_data_splitter` checks it against the sample and Stroom's parser. Lines holding no
key (PostgreSQL's SQL continued on tab-indented lines) join the line before them with `join_unkeyed`.

Lines of two events written interleaved (auditd under load) are not put back together: Stroom's translation reads
records in chunks, so no tool can join them safely. The profile warns (`interleaved`), and each such event is read
as several records; tell the user. `ausearch -i` output (interpreted) is not key=value: ask for raw lines.

Some sources put the key only on marker lines, the lines between them part of the record: ModSecurity's serial audit
log writes each transaction as sections `--5f005215-A--`, `--5f005215-B--` ... `--5f005215-Z--`. `profile_sample`
says `keyed sections` when lines with no white space, word tokens between punctuation, hold one token over runs
(the key) and another that differs within a run (the section's name); any marker style of that shape works
(`[[job-8f2c:extract]]`). The spec adds `join_unkeyed: true`: a line holding no key (a blank one too) joins the
record before it, and with `line_name` each marker and the lines after it are a section, read by the spec as one text
under its name (`B/text`, kept whole; the draft reads `Key: value` lines in it as fields). The time is usually inside
a section: map it with an extract on that section's text.

Characters XML doesn't allow (control characters other than tab and line ends) are taken out of the text by Stroom's
parser before the Data Splitter reads it, and by the dry run: `profile_sample` and `build_data_splitter` name them
(`removed_characters`). Where one separates fields (auditd's ENRICHED format puts `\x1d` before its translated
fields), a value runs into the next key: put a FindReplaceFilter before the parser that makes them spaces, and build
the splitter from the sample as it then reads.

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

Written by hand, the syslog header is the `cef` part's own `body`: a regex reading the text before `CEF:`, its groups
named. In an `alternatives` spec, with CEF lines beside others (`2026-11-16T00:00:12+00:00 ns-gw01 ns: CEF:0|Citrix|...`):

```json
{"kind": "alternatives", "parts": [
  {"kind": "cef", "variant": "cef",
   "body": {"kind": "regex",
            "pattern": "^(?:<(\\d+)>)?(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})) (\\S+)(?: ([^:\\[\\s]+)(?:\\[(\\d+)\\])?:)?\\s*$",
            "names": ["pri", "time", "host", "app", "pid"]}},
  {"kind": "regex", "variant": "linux", "pattern": "^(\\S+) (\\S+) ([\\w.:-]+)(?:\\[(\\d+)\\])?: (.*)$",
   "names": ["time", "host", "program", "pid", "message"]}]}
```

Seen going wrong in testing (some twelve tries, then the inferred spec copied):
- `{"kind": "cef"}` written by hand, with no `body`: the CEF fields are read, but the header's `time` and `host` are
  lost. Only an inferred spec adds the header's `body` itself.
- CEF as the `body` of a `regex` or `syslog` part (`{"kind": "regex", ..., "body": {"kind": "cef"}}`): the CEF
  fields come out empty. The `cef` part is the outer one; the header is its body.
- A `names` list that doesn't fit the groups: each capturing group needs a name, `pri` too when the pattern has the
  optional `(?:<(\d+)>)?` priority group; Stroom's parser then reports "Group number N not found" (`stroom_check`).
- A `match` of your own on a `cef` part: its kind's match (CEF's header) already picks its lines; one of your own can
  stop the part reading them. Leave `match` out unless two parts would take the same lines.

When unsure, call `build_data_splitter` with `stream_ids` and no spec: the inferred spec shows the shape, and you
change only the parts it gets wrong.

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
