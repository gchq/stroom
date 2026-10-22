/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl.tools.sample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// How one record of text divides into named fields: the Stroom MCP server's Data Splitter spec (`SplitterSpec` in
/// `utils/dsgen.py`), which [SplitterDryRun] applies to a sample as the generated Data Splitter would.
///
/// @param kind            `delimited`: columns separated by delimiter; `regex`: pattern with one capture group per
///                        name; `key_value`: pairs such as `a=1 b="two words"`; `syslog`: an RFC 5424 or 3164 header
///                        (pri, time, host, app, pid, message), with the message parsed further by body; `cef`:
///                        `CEF:0|vendor|product|version|id|name|severity|` then key=value pairs whose values may hold
///                        spaces, with any syslog header before it parsed by body.
/// @param delimiter       delimited: the column separator; key_value: what separates pairs (' ' by default).
/// @param headerLine      delimited: true when the first line names the columns.
/// @param columns         delimited: the column names in order, when no line names them; else empty.
/// @param quote           delimited and key_value: the character that quotes a value holding the delimiter, or
///                        null.
/// @param pattern         regex: the pattern (a Java regex, as Data Splitter runs it), or null.
/// @param names           regex: a field name per capture group, in order; "" skips a group.
/// @param pairSeparator   key_value: what separates a key from its value.
/// @param rfc             syslog: the header format, `rfc5424` or `rfc3164`.
/// @param body            The spec the field named by bodyField is parsed further with, or null.
/// @param bodyField       The field body parses.
/// @param quotedNewlines  delimited, with quote: values in quotes may hold new lines, so a record ends at a new line
///                        outside quotes only.
/// @param recordEnd       A regex the last line of a record matches at its start (Kubernetes CRI), or null.
/// @param recordStart     A regex the first line of a record matches at its start (a log entry and its stack
///                        trace), or null.
/// @param commentPrefix   delimited: lines starting with this are not records, e.g. `#`; or null.
/// @param headerDirective delimited, with a header line: the directive line that names the columns, e.g.
///                        `#Fields:` (W3C) or `#fields` (Zeek); or null.
/// @param directives      delimited: other directive lines whose value every record after them carries, as a
///                        field named as in the file.
public record SplitterSpec(String kind,
                           String delimiter,
                           boolean headerLine,
                           List<String> columns,
                           String quote,
                           String pattern,
                           List<String> names,
                           String pairSeparator,
                           String rfc,
                           SplitterSpec body,
                           String bodyField,
                           boolean quotedNewlines,
                           String recordEnd,
                           String recordStart,
                           String commentPrefix,
                           String headerDirective,
                           List<String> directives) {

    /// The kinds of spec.
    public static final List<String> KINDS = List.of("delimited", "regex", "key_value", "syslog", "cef");

    /// Checks the spec is consistent, as the Python model's validator does.
    ///
    /// @throws IllegalArgumentException with the Python validator's message if it isn't.
    public SplitterSpec {
        Objects.requireNonNull(kind, "kind");
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("kind must be one of " + KINDS);
        }
        delimiter = Objects.requireNonNullElse(delimiter, ",");
        columns = List.copyOf(Objects.requireNonNullElse(columns, List.of()));
        names = List.copyOf(Objects.requireNonNullElse(names, List.of()));
        pairSeparator = Objects.requireNonNullElse(pairSeparator, "=");
        rfc = Objects.requireNonNullElse(rfc, "rfc3164");
        bodyField = Objects.requireNonNullElse(bodyField, "message");
        directives = List.copyOf(Objects.requireNonNullElse(directives, List.of()));
        if ("regex".equals(kind) && (pattern == null || pattern.isEmpty() || names.isEmpty())) {
            throw new IllegalArgumentException("regex needs pattern and names");
        }
        if ("delimited".equals(kind) && !headerLine && columns.isEmpty()) {
            throw new IllegalArgumentException("delimited needs header: True (a header line) or the column names");
        }
        if ("key_value".equals(kind) && ",".equals(delimiter)) {
            delimiter = " ";
        }
        if ((headerDirective != null && !headerDirective.isEmpty()) || !directives.isEmpty()) {
            if (commentPrefix == null || commentPrefix.isEmpty()) {
                throw new IllegalArgumentException("header_directive and directives need comment_prefix: the prefix "
                                                   + "of every directive line, e.g. '#'");
            }
            final List<String> all = new ArrayList<>(directives);
            if (headerDirective != null) {
                all.addFirst(headerDirective);
            }
            for (final String directive : all) {
                if (!directive.isEmpty() && !directive.startsWith(commentPrefix)) {
                    throw new IllegalArgumentException("directives start with the comment_prefix '" + commentPrefix
                                                       + "'");
                }
            }
        }
        if (headerDirective != null && !headerDirective.isEmpty() && !headerLine) {
            throw new IllegalArgumentException("header_directive names the columns: give header: true with it");
        }
    }

    /// @param kind The kind of spec: one of [#KINDS].
    /// @return A builder of a spec of that kind, with the Python model's defaults.
    public static Builder builder(final String kind) {
        return new Builder(kind);
    }

    /// Reads a spec from its JSON, as the Python model's `model_validate` does (the form [#toMap()] writes).
    ///
    /// @param json The spec as JSON: `kind`, and any of `delimiter`, `header` (true or false, or the column
    ///             names), `quote`, `pattern`, `names`, `pair_separator`, `rfc`, `body` (a spec), `body_field`,
    ///             `quoted_newlines`, `record_end`, `record_start`, `comment_prefix`, `header_directive` and
    ///             `directives`.
    /// @return The spec.
    /// @throws IllegalArgumentException if it isn't a consistent spec.
    @SuppressWarnings("unchecked")
    public static SplitterSpec fromMap(final Map<String, ?> json) {
        if (!(json.get("kind") instanceof final String kind)) {
            throw new IllegalArgumentException("kind must be one of " + KINDS);
        }
        final Builder builder = builder(kind);
        builder.delimiter = (String) json.get("delimiter");
        final Object header = json.get("header");
        if (header instanceof final List<?> columns) {
            builder.columns = (List<String>) columns;
        } else {
            builder.headerLine = Boolean.TRUE.equals(header);
        }
        builder.quote = (String) json.get("quote");
        builder.pattern = (String) json.get("pattern");
        builder.names = (List<String>) json.get("names");
        builder.pairSeparator = (String) json.get("pair_separator");
        builder.rfc = (String) json.get("rfc");
        if (json.get("body") instanceof final Map<?, ?> body) {
            builder.body = fromMap((Map<String, ?>) body);
        }
        builder.bodyField = (String) json.get("body_field");
        builder.quotedNewlines = Boolean.TRUE.equals(json.get("quoted_newlines"));
        builder.recordEnd = (String) json.get("record_end");
        builder.recordStart = (String) json.get("record_start");
        builder.commentPrefix = (String) json.get("comment_prefix");
        builder.headerDirective = (String) json.get("header_directive");
        builder.directives = (List<String>) json.get("directives");
        return builder.build();
    }

    /// @return A builder holding this spec's values.
    public Builder toBuilder() {
        final Builder builder = new Builder(kind);
        builder.delimiter = delimiter;
        builder.headerLine = headerLine;
        builder.columns = columns;
        builder.quote = quote;
        builder.pattern = pattern;
        builder.names = names;
        builder.pairSeparator = pairSeparator;
        builder.rfc = rfc;
        builder.body = body;
        builder.bodyField = bodyField;
        builder.quotedNewlines = quotedNewlines;
        builder.recordEnd = recordEnd;
        builder.recordStart = recordStart;
        builder.commentPrefix = commentPrefix;
        builder.headerDirective = headerDirective;
        builder.directives = directives;
        return builder;
    }

    /// @return The spec's `header` as the Python model holds it: true or false for a header line, or the column
    /// names.
    public Object header() {
        return columns.isEmpty()
                ? (Object) headerLine
                : columns;
    }

    /// @return The spec as JSON (the Python model's `model_dump(exclude_defaults=True)`): its kind, and the values
    /// that aren't the defaults.
    public Map<String, Object> toMap() {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", kind);
        putIf(map, "delimiter", delimiter, !",".equals(delimiter));
        putIf(map, "header", header(), headerLine || !columns.isEmpty());
        putIf(map, "quote", quote, quote != null);
        putIf(map, "pattern", pattern, pattern != null);
        putIf(map, "names", names, !names.isEmpty());
        putIf(map, "pair_separator", pairSeparator, !"=".equals(pairSeparator));
        putIf(map, "rfc", rfc, !"rfc3164".equals(rfc));
        putIf(map, "body", body == null
                ? null
                : body.toMap(), body != null);
        putIf(map, "body_field", bodyField, !"message".equals(bodyField));
        putIf(map, "quoted_newlines", quotedNewlines, quotedNewlines);
        putIf(map, "record_end", recordEnd, recordEnd != null);
        putIf(map, "record_start", recordStart, recordStart != null);
        putIf(map, "comment_prefix", commentPrefix, commentPrefix != null);
        putIf(map, "header_directive", headerDirective, headerDirective != null);
        putIf(map, "directives", directives, !directives.isEmpty());
        return map;
    }

    private static void putIf(final Map<String, Object> map, final String key, final Object value,
                              final boolean put) {
        if (put) {
            map.put(key, value);
        }
    }


    // --------------------------------------------------------------------------------


    /// Builds a [SplitterSpec], with the Python model's defaults.
    public static final class Builder {

        private final String kind;
        private String delimiter = ",";
        private boolean headerLine;
        private List<String> columns = List.of();
        private String quote;
        private String pattern;
        private List<String> names = List.of();
        private String pairSeparator = "=";
        private String rfc = "rfc3164";
        private SplitterSpec body;
        private String bodyField = "message";
        private boolean quotedNewlines;
        private String recordEnd;
        private String recordStart;
        private String commentPrefix;
        private String headerDirective;
        private List<String> directives = List.of();

        private Builder(final String kind) {
            this.kind = kind;
        }

        /// @param delimiter The column or pair separator.
        /// @return This builder.
        public Builder delimiter(final String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        /// @param headerLine True when the first line names the columns.
        /// @return This builder.
        public Builder header(final boolean headerLine) {
            this.headerLine = headerLine;
            this.columns = List.of();
            return this;
        }

        /// @param columns The column names in order, when no line names them.
        /// @return This builder.
        public Builder header(final List<String> columns) {
            this.headerLine = false;
            this.columns = columns;
            return this;
        }

        /// @param quote The character that quotes a value, or null.
        /// @return This builder.
        public Builder quote(final String quote) {
            this.quote = quote;
            return this;
        }

        /// @param pattern The regex.
        /// @return This builder.
        public Builder pattern(final String pattern) {
            this.pattern = pattern;
            return this;
        }

        /// @param names A field name per capture group.
        /// @return This builder.
        public Builder names(final List<String> names) {
            this.names = names;
            return this;
        }

        /// @param pairSeparator What separates a key from its value.
        /// @return This builder.
        public Builder pairSeparator(final String pairSeparator) {
            this.pairSeparator = pairSeparator;
            return this;
        }

        /// @param rfc The syslog header format.
        /// @return This builder.
        public Builder rfc(final String rfc) {
            this.rfc = rfc;
            return this;
        }

        /// @param body The spec the body field is parsed further with.
        /// @return This builder.
        public Builder body(final SplitterSpec body) {
            this.body = body;
            return this;
        }

        /// @param bodyField The field body parses.
        /// @return This builder.
        public Builder bodyField(final String bodyField) {
            this.bodyField = bodyField;
            return this;
        }

        /// @param quotedNewlines True if quoted values may hold new lines.
        /// @return This builder.
        public Builder quotedNewlines(final boolean quotedNewlines) {
            this.quotedNewlines = quotedNewlines;
            return this;
        }

        /// @param recordEnd A regex the last line of a record matches at its start.
        /// @return This builder.
        public Builder recordEnd(final String recordEnd) {
            this.recordEnd = recordEnd;
            return this;
        }

        /// @param recordStart A regex the first line of a record matches at its start.
        /// @return This builder.
        public Builder recordStart(final String recordStart) {
            this.recordStart = recordStart;
            return this;
        }

        /// @param commentPrefix The prefix of lines that are not records.
        /// @return This builder.
        public Builder commentPrefix(final String commentPrefix) {
            this.commentPrefix = commentPrefix;
            return this;
        }

        /// @param headerDirective The directive line that names the columns.
        /// @return This builder.
        public Builder headerDirective(final String headerDirective) {
            this.headerDirective = headerDirective;
            return this;
        }

        /// @param directives Other directive lines whose values the records carry.
        /// @return This builder.
        public Builder directives(final List<String> directives) {
            this.directives = directives;
            return this;
        }

        /// @return The spec.
        /// @throws IllegalArgumentException if it isn't consistent.
        public SplitterSpec build() {
            return new SplitterSpec(kind, delimiter, headerLine, columns, quote, pattern, names, pairSeparator, rfc,
                    body, bodyField, quotedNewlines, recordEnd, recordStart, commentPrefix, headerDirective,
                    directives);
        }
    }
}
