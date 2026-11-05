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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// How one record of text divides into named fields: a Data Splitter spec, which [SplitterDryRun] applies to a
/// sample as the generated Data Splitter would.
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
/// @param escape          delimited, with a header line or column names: the character that escapes the delimiter
///                        or the quote character after it (`\` in `a\,b`), taken out of the value; or null.
/// @param spacedValues    key_value, without quote: values may hold the delimiter unquoted (`zones=Zone 1, Zone 2
///                        user=An end user`), so the pairs are read from the end, as a key never holds it.
/// @param catchAll        Not delimited: a record the spec doesn't match is kept whole as the field
///                        [#UNPARSED], and for `fields` the text no field took, rather than reported as an error
///                        and lost.
/// @param blankLineRecords Not delimited, without record_start or record_end: a record is a block of lines ended by
///                        a blank line, the spec applied to the whole block.
/// @param fields          fields: each field's name and the regex that finds it anywhere in the record (its first
///                        group, or the whole match), in any order, and each only when present.
/// @param continuation    With record_start: a record's first line is parsed by the spec, and the lines after it (a
///                        stack trace, a wrapped message) are kept whole as the field [#CONTINUATION].
/// @param parts           alternatives: the specs a record may be read with, tried in order, the first whose
///                        match fits reading it (a stream whose lines come in several formats).
/// @param variant         An alternatives part: the value of the variant field its records are written with.
/// @param match           An alternatives part: the regex a record matches at its start to be read by it, or null
///                        for the kind's own (see [#guard()]).
/// @param variantField    alternatives: the field each record's variant is written in.
/// @param carry           An alternatives part: the field its first named field is written as on every record after
///                        it (a header's date for the lines after it, kept by the splitter so stepping, which reads a
///                        record alone, sees it too); or null.
/// @param recordKey       A regex found in each line whose one group is the line's key (auditd's `msg=audit(...)`
///                        envelope): consecutive lines with the same key are one record; a line with none is a record
///                        of its own. Or null.
/// @param keyField        With record_key: the field each record's key is written in.
/// @param lineName        With record_key: a regex matched at each line's start whose one group names the line
///                        (auditd's `type=SYSCALL`); the rest of the line is read by the spec, its fields nested under
///                        that name (`SYSCALL/exe`). Or null, the lines' fields all the record's.
/// @param leadField       With line_name: the field the record's first line's name is written in (the kind of event).
/// @param joinUnkeyed     With record_key: a line holding no key joins the record before it (the key only on marker
///                        lines, as ModSecurity's `--5f005215-B--`); with line_name, each named line and the
///                        unnamed lines after it are a section, read by the spec as one text under its name.
/// @param skipLines       With record_key: a regex a line matches at its start to be passed over, ending the record
///                        before it (ausearch's `----` separators and `time->...` lines); or null.
/// @param words           key_value: text that isn't pairs (a prefix, bare flags, verbs between pairs: iptables'
///                        `[12.3] DROP: IN=eth0 ... DF PROTO=TCP`) is kept, each run of such words as the field
///                        [#WORDS] (given again for each run), rather than an error and the record lost.
/// @param singleQuotes    key_value: values may be in single quotes too (`a='two words'`), read without them; a
///                        single-quoted value made of pairs (auditd's `msg='op=login res=success'`) is read as those
///                        pairs, the record's own.
/// @param converter       [#CONVERTER] only: the Data Splitter's XML, run through Stroom's own parser; never given by
///                        the model (see [#ofConverter(String)]).
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
                           List<String> directives,
                           String escape,
                           boolean spacedValues,
                           boolean catchAll,
                           boolean blankLineRecords,
                           Map<String, String> fields,
                           boolean continuation,
                           List<SplitterSpec> parts,
                           String variant,
                           String match,
                           String variantField,
                           String carry,
                           String recordKey,
                           String keyField,
                           String lineName,
                           String leadField,
                           boolean joinUnkeyed,
                           String skipLines,
                           boolean words,
                           boolean singleQuotes,
                           String converter) {

    /// The kinds of spec.
    public static final List<String> KINDS = List.of("delimited", "regex", "key_value", "syslog", "cef", "fields",
            "alternatives");
    /// The field catch_all keeps what the spec didn't parse in.
    public static final String UNPARSED = "unparsed";
    /// The field continuation keeps the lines after a record's first in.
    public static final String CONTINUATION = "continuation";
    /// The field an alternatives spec writes each record's variant in, unless it names another.
    public static final String VARIANT = "variant";
    /// The kind of a spec that is a saved Data Splitter, read by Stroom's own parser: made by the tools only, never
    /// given (not one of [#KINDS]).
    public static final String CONVERTER = "converter";
    /// The kind of a spec reading records in several formats.
    public static final String ALTERNATIVES = "alternatives";
    /// The field words keeps each run of text that isn't pairs in.
    public static final String WORDS = "words";
    /// The field a record's key is written in, unless key_field names another.
    public static final String RECORD_KEY = "record_key";
    /// The field a record's first line's name is written in, unless lead_field names another.
    public static final String LEAD = "lead";
    private static final Pattern VARIANT_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    // A key=value part's own match, with spaced values (read from the end): a record that starts with a pair. Without
    // them, a record that is pairs from start to end, read by the part's own pair regex: one the part can't read whole
    // (an unclosed quote, text before the pairs) is the next part's or catch_all's, not an error and lost.
    private static final String PAIRS_MATCH = "\\s*[A-Za-z_][\\w.-]*=";

    /// Checks the spec is consistent.
    ///
    /// @throws IllegalArgumentException with what is inconsistent if it isn't.
    public SplitterSpec {
        Objects.requireNonNull(kind, "kind");
        if (CONVERTER.equals(kind) && (converter == null || converter.isBlank())) {
            throw new IllegalArgumentException("a converter spec needs the Data Splitter's XML");
        }
        if (!KINDS.contains(kind) && !CONVERTER.equals(kind)) {
            throw new IllegalArgumentException("kind must be one of " + KINDS);
        }
        delimiter = Objects.requireNonNullElse(delimiter, ",");
        columns = List.copyOf(Objects.requireNonNullElse(columns, List.of()));
        names = List.copyOf(Objects.requireNonNullElse(names, List.of()));
        pairSeparator = Objects.requireNonNullElse(pairSeparator, "=");
        rfc = Objects.requireNonNullElse(rfc, "rfc3164");
        bodyField = Objects.requireNonNullElse(bodyField, "message");
        directives = List.copyOf(Objects.requireNonNullElse(directives, List.of()));
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNullElse(fields, Map.of())));
        parts = List.copyOf(Objects.requireNonNullElse(parts, List.of()));
        variantField = Objects.requireNonNullElse(variantField, VARIANT);
        keyField = Objects.requireNonNullElse(keyField, RECORD_KEY);
        leadField = Objects.requireNonNullElse(leadField, LEAD);
        if (recordKey != null && recordKey.isEmpty()) {
            recordKey = null;
        }
        if (lineName != null && lineName.isEmpty()) {
            lineName = null;
        }
        if (skipLines != null && skipLines.isEmpty()) {
            skipLines = null;
        }
        if (joinUnkeyed && recordKey == null) {
            throw new IllegalArgumentException("join_unkeyed joins lines to a keyed record: give record_key with it");
        }
        if (skipLines != null && recordKey == null) {
            throw new IllegalArgumentException("skip_lines passes over the lines between keyed records: give "
                                               + "record_key with it");
        }
        if (skipLines != null) {
            try {
                Pattern.compile(skipLines);
            } catch (final PatternSyntaxException e) {
                throw new IllegalArgumentException("skip_lines is not a regex: " + e.getDescription());
            }
        }
        if (match != null && match.isEmpty()) {
            match = null;
        }
        if (escape != null && escape.isEmpty()) {
            escape = null;
        }
        checkAdditions(kind, escape, spacedValues, catchAll, blankLineRecords, fields, quote, commentPrefix,
                recordStart, recordEnd, delimiter);
        checkAlternatives(kind, parts, variantField, delimiter);
        if (words && (!"key_value".equals(kind) || spacedValues)) {
            throw new IllegalArgumentException("words is for key_value (without spaced_values)");
        }
        if (singleQuotes && (!"key_value".equals(kind) || spacedValues)) {
            throw new IllegalArgumentException("single_quotes is for key_value (without spaced_values)");
        }
        checkKeyed(kind, recordKey, keyField, lineName, leadField, recordStart, recordEnd, blankLineRecords,
                continuation);
        if (continuation && (recordStart == null || recordStart.isEmpty() || "delimited".equals(kind))) {
            throw new IllegalArgumentException("continuation is for records of several lines: give record_start "
                                               + "with it (not delimited)");
        }
        if (match != null) {
            try {
                Pattern.compile(match);
            } catch (final PatternSyntaxException e) {
                throw new IllegalArgumentException("match is not a regex: " + e.getDescription());
            }
        }
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

    // An alternatives spec's parts: two or more, each named by a distinct variant, of a kind that reads one record
    // (not delimited or alternatives), with nothing that divides the text into records (that is the whole spec's),
    // and a match where its kind has none of its own.
    private static void checkAlternatives(final String kind,
                                          final List<SplitterSpec> parts,
                                          final String variantField,
                                          final String delimiter) {
        if (!ALTERNATIVES.equals(kind)) {
            if (!parts.isEmpty()) {
                throw new IllegalArgumentException("parts are for the alternatives kind");
            }
            return;
        }
        // One part is enough: a stream of one format whose records are told apart from lines in none (catch_all),
        // or whose times need the variant to be read with a date (a log of time-only lines).
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("alternatives needs parts: a spec per format of record, tried in "
                                               + "order");
        }
        if (variantField.isBlank()) {
            throw new IllegalArgumentException("variant_field names the field each record's variant is written in");
        }
        final Set<String> variants = new HashSet<>();
        for (final SplitterSpec part : parts) {
            final String which = "part '" + part.variant() + "'";
            if (part.variant() == null || part.variant().isBlank()) {
                throw new IllegalArgumentException("each of parts needs a variant: the value its records' "
                                                   + variantField + " is written with");
            }
            if (!VARIANT_NAME.matcher(part.variant()).matches()) {
                throw new IllegalArgumentException(which + ": a variant is letters, digits, '_', '.' and '-'");
            }
            if (!variants.add(part.variant())) {
                throw new IllegalArgumentException("parts' variants must differ: '" + part.variant() + "' is given "
                                                   + "twice");
            }
            if ("delimited".equals(part.kind()) || ALTERNATIVES.equals(part.kind())) {
                throw new IllegalArgumentException(which + ": a part is regex, key_value, syslog, cef or fields, "
                                                   + "not " + part.kind());
            }
            if (part.recordStart() != null || part.recordEnd() != null || part.blankLineRecords()
                || part.continuation() || part.catchAll()) {
                throw new IllegalArgumentException(which + ": record_start, record_end, blank_line_records, "
                                                   + "continuation and catch_all are the whole spec's, not a "
                                                   + "part's");
            }
            if (part.carry() != null && (!"regex".equals(part.kind())
                                         || part.names().stream().allMatch(String::isEmpty)
                                         || !VARIANT_NAME.matcher(part.carry()).matches())) {
                throw new IllegalArgumentException(which + ": carry goes on a regex part with a named field, and is "
                                                   + "a field name (letters, digits, '_', '.' and '-')");
            }
            if (part.guard() == null) {
                throw new IllegalArgumentException(which + ": a fields part needs match, the regex its records "
                                                   + "match at their start");
            }
        }
        if (!",".equals(delimiter)) {
            throw new IllegalArgumentException("alternatives takes no delimiter: give each part its own");
        }
    }

    /// The regex a record matches at its start to be read by this spec as an alternatives part: its `match`, else
    /// its kind's own (a syslog header, CEF's, pairs from start to end, a regex's own pattern).
    ///
    /// @return The regex, or null for a fields part without a match.
    public String guard() {
        if (match != null) {
            return match;
        }
        return switch (kind) {
            case "syslog" -> "rfc5424".equals(rfc)
                    ? SplitterDryRun.SYSLOG_5424
                    : SplitterDryRun.SYSLOG_3164;
            case "cef" -> SplitterDryRun.CEF_PATTERN;
            case "key_value" -> spacedValues
                    ? PAIRS_MATCH
                    : words
                    // Text around the pairs too, with at least one pair.
                    ? "(?=[^\\n]*=)(?:" + SplitterDryRun.kvPatterns(this).getFirst() + "|"
                      + SplitterDryRun.wordsPattern(this) + ")+(?:" + SplitterDryRun.trailingDelimiter(this) + "|\\s*$)"
                    : "(?:" + SplitterDryRun.kvPatterns(this).getFirst() + ")+(?:"
                      + (SplitterDryRun.spacedDelimiter(this)
                    ? SplitterDryRun.trailingDelimiter(this) + "|"
                    : "") + "\\s*$)";
            case "regex" -> pattern;
            default -> null;
        };
    }

    // record_key and line_name: each a regex of one group; the key the whole spec's way of dividing records, so
    // not with another; not delimited (a line a record) or alternatives (a part per line's format).
    private static void checkKeyed(final String kind,
                                   final String recordKey,
                                   final String keyField,
                                   final String lineName,
                                   final String leadField,
                                   final String recordStart,
                                   final String recordEnd,
                                   final boolean blankLineRecords,
                                   final boolean continuation) {
        if (lineName != null && recordKey == null) {
            throw new IllegalArgumentException("line_name names the lines of a record of several: give record_key "
                                               + "with it");
        }
        if (recordKey == null) {
            return;
        }
        if ("delimited".equals(kind) || ALTERNATIVES.equals(kind)) {
            throw new IllegalArgumentException("record_key is for the kinds that read one line (not " + kind + ")");
        }
        if (recordStart != null || recordEnd != null || blankLineRecords || continuation) {
            throw new IllegalArgumentException("record_key divides the records: not with record_start, record_end, "
                                               + "blank_line_records or continuation");
        }
        oneGroup("record_key", recordKey);
        if (lineName != null) {
            oneGroup("line_name", lineName);
        }
        for (final String name : List.of(keyField, leadField)) {
            if (!VARIANT_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("key_field and lead_field are field names (letters, digits, '_', "
                                                   + "'.' and '-'), not '" + name + "'");
            }
        }
    }

    private static void oneGroup(final String option, final String regex) {
        final int groups;
        try {
            groups = Pattern.compile(regex).matcher("").groupCount();
        } catch (final PatternSyntaxException e) {
            throw new IllegalArgumentException(option + " is not a regex: " + e.getDescription());
        }
        if (groups != 1) {
            throw new IllegalArgumentException(option + " needs one capturing group, the " + (option.equals(
                    "record_key")
                    ? "key"
                    : "line's name") + " (use (?:...) for any other), not " + groups);
        }
    }

    // The checks of escape, spaced_values, catch_all, blank_line_records and the fields kind.
    private static void checkAdditions(final String kind,
                                       final String escape,
                                       final boolean spacedValues,
                                       final boolean catchAll,
                                       final boolean blankLineRecords,
                                       final Map<String, String> fields,
                                       final String quote,
                                       final String commentPrefix,
                                       final String recordStart,
                                       final String recordEnd,
                                       final String delimiter) {
        final boolean delimited = "delimited".equals(kind);
        if (escape != null && (!delimited || commentPrefix != null)) {
            throw new IllegalArgumentException("escape is for delimited text (without comment_prefix)");
        }
        if (escape != null && (escape.equals(delimiter) || escape.equals(quote))) {
            throw new IllegalArgumentException("escape must differ from the delimiter and the quote");
        }
        if (spacedValues && (!"key_value".equals(kind) || quote != null)) {
            throw new IllegalArgumentException("spaced_values is for key_value without quote: quoted values need "
                                               + "no reading from the end");
        }
        if (catchAll && delimited) {
            throw new IllegalArgumentException("catch_all is for the kinds that can fail to match a record: a "
                                               + "delimited record always splits");
        }
        if (blankLineRecords && (delimited || recordStart != null || recordEnd != null)) {
            throw new IllegalArgumentException("blank_line_records is for the kinds other than delimited, without "
                                               + "record_start or record_end");
        }
        if ("fields".equals(kind) != !fields.isEmpty()) {
            throw new IllegalArgumentException("fields (a name and a regex for each field) is for the fields kind, "
                                               + "which needs them");
        }
        fields.forEach((name, regex) -> {
            if (name == null || name.isBlank() || regex == null || regex.isEmpty()) {
                throw new IllegalArgumentException("each of fields needs a name and a regex");
            }
        });
    }

    /// A spec that is a saved Data Splitter, read by Stroom's own parser, as processing reads it: the sample check's
    /// records where no spec was given. Seen in testing: without one, the check guessed a spec from the sample's
    /// first format, so a stream of two read only one, its records had none of the mapping's fields, and the agent
    /// took the Unknown gate's refusal to mean a spec reads one pattern only.
    ///
    /// @param converter The Data Splitter's XML.
    /// @return The spec.
    public static SplitterSpec ofConverter(final String converter) {
        final Builder builder = builder(CONVERTER);
        builder.converter = converter;
        return builder.build();
    }

    /// @param kind The kind of spec: one of [#KINDS].
    /// @return A builder of a spec of that kind, with the defaults.
    public static Builder builder(final String kind) {
        return new Builder(kind);
    }

    /// Reads a spec from its JSON (the form [#toMap()] writes).
    ///
    /// @param json The spec as JSON: `kind`, and any of `delimiter`, `header` (true or false, or the column
    ///             names), `quote`, `pattern`, `names`, `pair_separator`, `rfc`, `body` (a spec), `body_field`,
    ///             `quoted_newlines`, `record_end`, `record_start`, `comment_prefix`, `header_directive`,
    ///             `directives`, `escape`, `spaced_values`, `catch_all`, `blank_line_records`, `fields`,
    ///             `continuation`, `parts` (specs), `variant`, `match`, `variant_field` and `carry`.
    /// @return The spec.
    /// @throws IllegalArgumentException if it isn't a consistent spec.
    @SuppressWarnings("unchecked")
    public static SplitterSpec fromMap(final Map<String, ?> json) {
        if (!(json.get("kind") instanceof final String kind) || !KINDS.contains(kind)) {
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
        builder.escape = (String) json.get("escape");
        builder.spacedValues = Boolean.TRUE.equals(json.get("spaced_values"));
        builder.catchAll = Boolean.TRUE.equals(json.get("catch_all"));
        builder.blankLineRecords = Boolean.TRUE.equals(json.get("blank_line_records"));
        if (json.get("fields") instanceof final Map<?, ?> fields) {
            final Map<String, String> named = new LinkedHashMap<>();
            fields.forEach((name, regex) -> named.put(String.valueOf(name), regex == null
                    ? null
                    : String.valueOf(regex)));
            builder.fields = named;
        }
        builder.continuation = Boolean.TRUE.equals(json.get("continuation"));
        if (json.get("parts") instanceof final List<?> parts) {
            final List<SplitterSpec> read = new ArrayList<>();
            for (final Object part : parts) {
                if (!(part instanceof final Map<?, ?> map)) {
                    throw new IllegalArgumentException("each of parts is a spec");
                }
                read.add(fromMap((Map<String, ?>) map));
            }
            builder.parts = read;
        }
        builder.variant = (String) json.get("variant");
        builder.match = (String) json.get("match");
        builder.variantField = (String) json.get("variant_field");
        builder.carry = (String) json.get("carry");
        builder.recordKey = (String) json.get("record_key");
        builder.keyField = (String) json.get("key_field");
        builder.lineName = (String) json.get("line_name");
        builder.leadField = (String) json.get("lead_field");
        builder.joinUnkeyed = Boolean.TRUE.equals(json.get("join_unkeyed"));
        builder.skipLines = (String) json.get("skip_lines");
        builder.words = Boolean.TRUE.equals(json.get("words"));
        builder.singleQuotes = Boolean.TRUE.equals(json.get("single_quotes"));
        return builder.build();
    }

    /// @return The spec each line of a keyed record is read with: this one without record_key and line_name.
    public SplitterSpec lineSpec() {
        return toBuilder().recordKey(null).lineName(null).keyField(null).leadField(null).joinUnkeyed(false)
                .skipLines(null).build();
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
        builder.escape = escape;
        builder.spacedValues = spacedValues;
        builder.catchAll = catchAll;
        builder.blankLineRecords = blankLineRecords;
        builder.fields = fields;
        builder.continuation = continuation;
        builder.parts = parts;
        builder.variant = variant;
        builder.match = match;
        builder.variantField = variantField;
        builder.carry = carry;
        builder.recordKey = recordKey;
        builder.keyField = keyField;
        builder.lineName = lineName;
        builder.leadField = leadField;
        builder.joinUnkeyed = joinUnkeyed;
        builder.skipLines = skipLines;
        builder.words = words;
        builder.singleQuotes = singleQuotes;
        return builder;
    }

    /// @return The spec's `header` as its JSON holds it: true or false for a header line, or the column names.
    public Object header() {
        return columns.isEmpty()
                ? (Object) headerLine
                : columns;
    }

    /// @return The spec as JSON: its kind, and the values that aren't the defaults.
    public Map<String, Object> toMap() {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", kind);
        if (CONVERTER.equals(kind)) {
            // Its XML is the build's converter's, not the model's to send back.
            return map;
        }
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
        putIf(map, "escape", escape, escape != null);
        putIf(map, "spaced_values", spacedValues, spacedValues);
        putIf(map, "catch_all", catchAll, catchAll);
        putIf(map, "blank_line_records", blankLineRecords, blankLineRecords);
        putIf(map, "fields", fields, !fields.isEmpty());
        putIf(map, "continuation", continuation, continuation);
        putIf(map, "parts", parts.stream().map(SplitterSpec::toMap).toList(), !parts.isEmpty());
        putIf(map, "variant", variant, variant != null);
        putIf(map, "match", match, match != null);
        putIf(map, "variant_field", variantField, !VARIANT.equals(variantField));
        putIf(map, "carry", carry, carry != null);
        putIf(map, "record_key", recordKey, recordKey != null);
        putIf(map, "key_field", keyField, !RECORD_KEY.equals(keyField));
        putIf(map, "line_name", lineName, lineName != null);
        putIf(map, "lead_field", leadField, !LEAD.equals(leadField));
        putIf(map, "join_unkeyed", joinUnkeyed, joinUnkeyed);
        putIf(map, "skip_lines", skipLines, skipLines != null);
        putIf(map, "words", words, words);
        putIf(map, "single_quotes", singleQuotes, singleQuotes);
        return map;
    }

    private static void putIf(final Map<String, Object> map, final String key, final Object value,
                              final boolean put) {
        if (put) {
            map.put(key, value);
        }
    }


    // --------------------------------------------------------------------------------


    /// Builds a [SplitterSpec], with the defaults.
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
        private String escape;
        private boolean spacedValues;
        private boolean catchAll;
        private boolean blankLineRecords;
        private Map<String, String> fields = Map.of();
        private boolean continuation;
        private List<SplitterSpec> parts = List.of();
        private String variant;
        private String match;
        private String variantField = VARIANT;
        private String carry;
        private String recordKey;
        private String keyField = RECORD_KEY;
        private String lineName;
        private String leadField = LEAD;
        private boolean joinUnkeyed;
        private String skipLines;
        private boolean words;
        private boolean singleQuotes;
        private String converter;

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

        /// @param escape The character that escapes the delimiter or quote after it, or null.
        /// @return This builder.
        public Builder escape(final String escape) {
            this.escape = escape;
            return this;
        }

        /// @param spacedValues True if key=value values may hold the delimiter unquoted.
        /// @return This builder.
        public Builder spacedValues(final boolean spacedValues) {
            this.spacedValues = spacedValues;
            return this;
        }

        /// @param catchAll True to keep what the spec doesn't parse as [#UNPARSED].
        /// @return This builder.
        public Builder catchAll(final boolean catchAll) {
            this.catchAll = catchAll;
            return this;
        }

        /// @param blankLineRecords True if a record is a block of lines ended by a blank line.
        /// @return This builder.
        public Builder blankLineRecords(final boolean blankLineRecords) {
            this.blankLineRecords = blankLineRecords;
            return this;
        }

        /// @param fields Each field's name and the regex that finds it, for the fields kind.
        /// @return This builder.
        public Builder fields(final Map<String, String> fields) {
            this.fields = fields;
            return this;
        }

        /// @param continuation True to keep the lines after a record's first as [#CONTINUATION].
        /// @return This builder.
        public Builder continuation(final boolean continuation) {
            this.continuation = continuation;
            return this;
        }

        /// @param parts The alternatives' parts, tried in order.
        /// @return This builder.
        public Builder parts(final List<SplitterSpec> parts) {
            this.parts = parts;
            return this;
        }

        /// @param variant A part's variant.
        /// @return This builder.
        public Builder variant(final String variant) {
            this.variant = variant;
            return this;
        }

        /// @param match A part's match, or null for its kind's own.
        /// @return This builder.
        public Builder match(final String match) {
            this.match = match;
            return this;
        }

        /// @param variantField The field the alternatives write each record's variant in.
        /// @return This builder.
        public Builder variantField(final String variantField) {
            this.variantField = variantField;
            return this;
        }

        /// @param carry The field a part's first named field is written as on every record after it, or null.
        /// @return This builder.
        public Builder carry(final String carry) {
            this.carry = carry;
            return this;
        }

        /// @return The spec.
        /// @throws IllegalArgumentException if it isn't consistent.
        public Builder recordKey(final String recordKey) {
            this.recordKey = recordKey;
            return this;
        }

        public Builder keyField(final String keyField) {
            this.keyField = keyField;
            return this;
        }

        public Builder lineName(final String lineName) {
            this.lineName = lineName;
            return this;
        }

        public Builder leadField(final String leadField) {
            this.leadField = leadField;
            return this;
        }

        public Builder joinUnkeyed(final boolean joinUnkeyed) {
            this.joinUnkeyed = joinUnkeyed;
            return this;
        }

        public Builder skipLines(final String skipLines) {
            this.skipLines = skipLines;
            return this;
        }

        public Builder words(final boolean words) {
            this.words = words;
            return this;
        }

        public Builder singleQuotes(final boolean singleQuotes) {
            this.singleQuotes = singleQuotes;
            return this;
        }

        public SplitterSpec build() {
            return new SplitterSpec(kind, delimiter, headerLine, columns, quote, pattern, names, pairSeparator, rfc,
                    body, bodyField, quotedNewlines, recordEnd, recordStart, commentPrefix, headerDirective,
                    directives, escape, spacedValues, catchAll, blankLineRecords, fields, continuation, parts,
                    variant, match, variantField, carry, recordKey, keyField, lineName, leadField,
                    joinUnkeyed, skipLines, words, singleQuotes, converter);
        }
    }
}
