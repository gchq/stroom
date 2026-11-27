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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.sample.SplitterSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Quoted key=value values holding a quote escaped with a backslash (`msg="say \"hi\""`): Stroom's Data Splitter reads
/// the value whole (the converter's regex reads past the escape) but keeps the backslash, so the draft reads such a
/// field with `transform: unescape_backslashes`. Found testing awkward values (2026-11-18): the events said
/// `say \"hi\"`. Format-level knowledge only: whether the sample's values hold the escape. The key_value spec may be
/// the splitter's own or nested in it: a syslog (or other) header's body, an alternatives part, or a part's body. Seen
/// in the agent eval (case 25): a syslog header with a key_value body read with no transform, its events saying
/// `say \"hi\"`. A quoted delimited spec's doubled quote (a CSV's `""`), which Data Splitter keeps doubled, is read
/// with `transform: unescape_quotes` the same way, wherever the delimited spec is nested ([#undouble]).
final class EscapedQuotes {

    private EscapedQuotes() {
        // Static only.
    }

    /// Gives each entry reading a field whose values hold an escaped quote the transform, unless it has one (or a
    /// time format), and says so in a note.
    ///
    /// @param spec    The Data Splitter spec the sample is read with, or null.
    /// @param records The sample's records.
    /// @param mapping The draft's mapping: its common entries and its rules' fields are given the transform.
    /// @param notes   The draft's notes, added to.
    static void unescape(final SplitterSpec spec,
                         final List<Map<String, Object>> records,
                         final Map<String, Object> mapping,
                         final List<String> notes) {
        if (spec == null) {
            return;
        }
        final Set<String> escaped = holding(spec, "key_value", "\\", records);
        if (escaped.isEmpty()) {
            return;
        }
        transform(mapping, escaped, "unescape_backslashes");
        notes.add(ToolText.repr(escaped.stream().sorted(ToolText::compare).toList()) + " hold quotes escaped with a "
                  + "backslash inside quoted values, which Data Splitter keeps escaped: the draft reads them with "
                  + "transform unescape_backslashes (a quote or backslash after a backslash as itself).");
    }

    /// Gives each entry reading a field whose values hold a doubled quote of a quoted delimited spec (a CSV's `""`)
    /// the transform `unescape_quotes`, unless it has one (or a time format), and says so in a note. The delimited
    /// spec may be the splitter's own or nested in it, as for [#unescape]; a header's fields and the text a body is
    /// read from are left as written.
    ///
    /// @param spec    The Data Splitter spec the sample is read with, or null.
    /// @param records The sample's records.
    /// @param mapping The draft's mapping: its common entries and its rules' fields are given the transform.
    /// @param notes   The draft's notes, added to.
    static void undouble(final SplitterSpec spec,
                         final List<Map<String, Object>> records,
                         final Map<String, Object> mapping,
                         final List<String> notes) {
        if (spec == null) {
            return;
        }
        final Set<String> doubled = holding(spec, "delimited", "", records);
        if (doubled.isEmpty()) {
            return;
        }
        transform(mapping, doubled, "unescape_quotes");
        notes.add(ToolText.repr(doubled.stream().sorted(ToolText::compare).toList()) + " hold doubled quotes inside "
                  + "quoted values, which Data Splitter keeps doubled: the draft reads them with transform "
                  + "unescape_quotes.");
    }

    // The fields whose values hold a quote of a quoted spec of the kind nested in the spec, written after the prefix
    // (a backslash) or doubled (no prefix): never a header's field nor the text a body is read from.
    private static Set<String> holding(final SplitterSpec spec,
                                       final String kind,
                                       final String prefix,
                                       final List<Map<String, Object>> records) {
        final Set<String> quotes = new LinkedHashSet<>();
        final Set<String> notPairs = new HashSet<>();
        collect(spec, kind, quotes, notPairs);
        final Set<String> found = new HashSet<>();
        if (quotes.isEmpty()) {
            return found;
        }
        records.forEach(record -> record.forEach((key, value) -> {
            final String text = ToolText.str(value);
            if (!notPairs.contains(key) && quotes.stream().anyMatch(quote -> text.contains(prefix.isEmpty()
                    ? quote + quote
                    : prefix + quote))) {
                found.add(key);
            }
        }));
        return found;
    }

    // Each entry reading one of the fields given the transform, unless it has one or a time format.
    @SuppressWarnings("unchecked")
    private static void transform(final Map<String, Object> mapping,
                                  final Set<String> fields,
                                  final String transform) {
        final List<Map<String, Object>> entries = new ArrayList<>(JsonValues.maps(mapping.get("common")));
        JsonValues.maps(mapping.get("events")).forEach(rule -> entries.addAll(
                (List<Map<String, Object>>) rule.get("fields")));
        for (final Map<String, Object> entry : entries) {
            if (fields.contains(entry.get("field")) && !JsonValues.truthy(entry.get("transform"))
                && !JsonValues.truthy(entry.get("time_format"))) {
                entry.put("transform", transform);
            }
        }
    }

    // The quotes of the quoted specs of the kind in the spec, wherever nested (its body, its alternatives' parts and
    // theirs), and the fields that aren't the nested spec's values: the header's fields of a spec with a body, and
    // the text its body is read from (kept whole, escapes and all, as the source wrote it).
    private static void collect(final SplitterSpec spec,
                                final String kind,
                                final Set<String> quotes,
                                final Set<String> notPairs) {
        if (kind.equals(spec.kind()) && LocalCheck.hasText(spec.quote())) {
            quotes.add(spec.quote());
        }
        if (spec.body() != null) {
            notPairs.add(spec.bodyField());
            if ("syslog".equals(spec.kind())) {
                notPairs.addAll(spec.syslogNames());
            }
            collect(spec.body(), kind, quotes, notPairs);
        }
        spec.parts().forEach(part -> collect(part, kind, quotes, notPairs));
    }
}
