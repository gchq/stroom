# JSON input

JSON is parsed by the `JSONParser` element of a translation template that has one (`find_pipeline_templates`
shows each template's `parser`; in a standard Stroom, Event Data (JSON)). It needs no text converter: a
Data Splitter is for text, and a converter holding a `<jsonParser>` element parses nothing (`save_text_converter`
refuses one). The parser turns JSON into XML in the namespace `http://www.w3.org/2013/XSL/json` (not the
`xpath-functions` namespace that `json-to-xml()` uses):

```json
{"ts": "2026-09-28T10:00:00Z", "user": {"name": "alice"}, "tags": ["a", "b"], "ok": true}
```

```xml
<map xmlns="http://www.w3.org/2013/XSL/json">
  <string key="ts">2026-09-28T10:00:00Z</string>
  <map key="user"><string key="name">alice</string></map>
  <array key="tags"><string>a</string><string>b</string></array>
  <boolean key="ok">true</boolean>
</map>
```

## Layouts: JSON lines and arrays

The parser reads every top-level value in the stream. Its `addRootObject` property (true by default) wraps them
all in one `map`, which is what makes JSON lines (one object per line, or concatenated objects) well-formed XML:

| Source | `jsonParser.addRootObject` | XSLT root | Records | Mapping |
| --- | --- | --- | --- | --- |
| JSON lines | `true` (keep the default; false gives several root elements, which fails) | `/map` | `/map/map` | `input: json, json_layout: lines` |
| One JSON array | `false` (set it on `create_pipeline`, as sibling pipelines do) | `/array` | `/array/map` | `input: json, json_layout: array` |
| One JSON array, default left | `true` | `/map` | `/map/array/map` | the same; the generated XSLT matches both |
| One object wrapping the records (AWS CloudTrail's `{"Records": [...]}`, an API's `data` or `items`) | `true` | `/map` | `/map/map`, each item through `for_each` | `input: json, json_layout: lines, for_each: Records` |

A wrapped object is the parser's one record; `for_each` names its array and makes an Event of each item, fields read
from the item (mark one read from the wrapper with `scope: record`). `profile_sample` names the array's key (the one
holding the most objects), also from a stream's head cut off mid-array.

`profile_sample` reports which (`parser_properties`, `xslt_input`), and `build_translation_xslt` returns the
setting as `set_properties`, to pass to `create_pipeline` as it is. Check `read_document` for the value a
template's children use.

## Lines that are not JSON

Stroom's JSONParser stops the stream at the first line that isn't JSON (a line cut short, a shipper's banner): the
error is fatal, and every record after it is lost. `profile_sample` still calls a file JSON lines when most lines
are, and names the others (`malformed_lines`); its `xslt_input` then reads each line through a Data Splitter (one
field, `json`) with `json_fields: ["json"]`, in the Event Data (Text) template, so a bad line gives a record with
no values: drop it by name with `drop_when` (`json.time` `present: false`) and tell the user how many there were.
`create_pipeline` shows the format as `json lines (some not JSON)`.

## Addressing fields

Declare `xmlns:json="http://www.w3.org/2013/XSL/json"` and match `json:map` and `json:string[@key='ts']`, or set
`xpath-default-namespace="http://www.w3.org/2013/XSL/json"`, as the generated XSLT does. With the wrong namespace
(or none) no template matches, the output is bare text, and processing writes nothing; `check_xslt` reports the
bare names, and stepping reports "Output contains no XML elements". In a mapping, fields are JSON keys:
`user.name` for nested keys.

## The event inside a string field

Log shippers often wrap the real event as a string. Two cases:

**Text** (a message such as `2026-10-01 10:00:00 alice LOGIN Successful login from 10.0.0.1`): parse it with the
mapping's `extract`, a regular expression whose groups become fields, not with `substring-before()` /
`substring-after()` chains:

```json
{"input": "json", "json_layout": "lines",
 "extract": [{"field": "message", "regex": "^(\\S+ \\S+) (\\S+) (\\S+) (.*)$", "names": ["ts", "user", "action", "desc"]},
             {"field": "desc", "regex": " from (\\S+)$", "names": ["client_ip"]}],
 "common": [{"path": "EventTime/TimeCreated", "field": "ts", "time_format": "yyyy-MM-dd HH:mm:ss", "timezone": "UTC"},
            {"path": "EventSource/User/Id", "field": "user"},
            {"path": "EventSource/Client/IPAddress", "field": "client_ip"},
            {"path": "EventDetail/Description", "field": "desc"}],
 "events": [{"name": "logon", "when": [{"field": "action", "equals": "LOGIN"}], "fields": ["..."]}]}
```

The XSLT it writes holds `analyze-string(message, regex)` in a variable (named for what it extracts, `ts_parts`)
and reads each group from it; several extractions of one field read it once, as `$message`. A record the pattern
does not match gets no values, so its elements are left out and a rule can test `present: false`.

**JSON** (the field holds a JSON document): name the field in the mapping's `json_fields`, and read a key inside
it as `<field>.<key>` (nested: `<field>.<a>.<b>`) wherever a field goes: in common, events, `when` and `any_of`.
draft_translation_mapping does this itself for a field whose values are JSON. Never map the field itself into an
element: the whole JSON line ends up in it.

```json
{"json_fields": ["message"],
 "common": [{"path": "EventSource/User/Id", "field": "message.user"}],
 "events": [{"name": "login", "when": [{"field": "message.event_type", "equals": "login"}], "fields": [...]}]}
```

The generated XSLT reads it safely: a record whose field is empty, or isn't JSON, gives nothing for it rather than
stopping processing (a bare `json-to-xml()` on such a value is a fatal error). An `xpath` using `json-to-xml()`, as
in `json-to-xml(*[@key='message'])/*/*[@key='user']`, is read the same safe way; its output is in the
`http://www.w3.org/2005/xpath-functions` namespace.

**XML** (the field holds an XML document, such as a SOAP envelope or a Windows event): name it in `xml_fields`,
and read inside it as `<field>/<path>` from the document's root element down (`payload/Envelope/Body/Login/User`),
`<field>//<name>` for an element anywhere in it, and `<field>//<name>/@<attr>` for an attribute. Steps match by local
name, so the document's namespaces need no declaring. A value that isn't XML gives nothing rather than an error.
`draft_translation_mapping` does this itself for a field whose values are XML documents.

When the sample's records come in different shapes (one file flat, another with the event as JSON in a field), an
element reads every shape's field in turn with `any_of`, e.g. `{"path": "EventDetail/TypeId", "any_of":
["event_type", "message.event_type"]}`, and each shape's kinds get rules.

There is no `stroom:json-parse()` or similar: `json-to-xml()` is the function, with no prefix. By hand (guard it:
`if (normalize-space(x)) then json-to-xml(x) else ()`, inside `xsl:try` for text that isn't JSON), after a prefix:

```xml
<xsl:analyze-string select="json:string[@key='body']" regex="^.+?(\{{.+\}})$">
  <xsl:matching-substring>
    <xsl:apply-templates select="json-to-xml(regex-group(1))/*" mode="event"/>
  </xsl:matching-substring>
</xsl:analyze-string>
```

(Braces in a `regex` attribute are doubled because the attribute is an attribute value template.)
Some environments do this in a separate XSLT step before the translation (e.g. `innerJsonFilter`);
`describe_template` shows whether sibling pipelines do.
