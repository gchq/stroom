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

import stroom.ai.impl.tools.PyText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Values a source writes in a field to stand for none, such as `-` or `N/A`, found in sample values so the user
/// can say what to do with them: leave them out, write them as null or empty, or keep them as they are.
///
/// Seen in testing: W3C logs' `-` for a missing address went into an Elasticsearch ip field, which refused every
/// document holding it. Only syslog, CEF and key=value drafts knew `-` meant none.
///
/// A value counts when it recurs in a field (two records or more) and is either one of the usual stand-ins
/// ([#KNOWN]), or, in a field whose other values are nearly all of one kind (addresses, numbers, times or
/// booleans), a short value that isn't of that kind.
public final class Placeholders {

    /// The usual stand-ins for none, compared ignoring case.
    public static final Set<String> KNOWN = Set.of("-", "--", "na", "n/a", "null", "(null)", "none", "(none)", "nil",
            "?", "undefined", "unknown");
    /// How many records a value must be in to count.
    static final int MIN_RECORDS = 2;
    /// The longest value taken for a stand-in when only its not fitting the field's kind says it is one.
    static final int MAX_MISFIT_LENGTH = 16;
    /// The share of a field's other values that must be of one kind for a value not of it to stand out.
    static final double KIND_SHARE = 0.8;
    // The kinds a field's values can be strictly, by the profiler's type of a value, and how the user is told.
    private static final Map<String, String> KINDS = Map.of(
            "ip", "IP addresses",
            "integer", "numbers",
            "decimal", "numbers",
            "timestamp", "times",
            "boolean", "true or false");

    private Placeholders() {
        // Static only.
    }

    /// @param valuesByField Each field's sample values, a record each (null where a record has none).
    /// @param decided       Values already decided on (e.g. a mapping's nil_values), which aren't found again.
    /// @return The stand-ins found, each with the fields holding it, most records first.
    public static List<Found> find(final Map<String, List<String>> valuesByField, final Set<String> decided) {
        final Map<String, Found> found = new LinkedHashMap<>();
        valuesByField.forEach((field, values) -> {
            final Map<String, Long> counts = values.stream()
                    .filter(value -> value != null && !PyText.isBlank(value))
                    .map(PyText::strip)
                    .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
            final List<String> recurring = counts.entrySet().stream()
                    .filter(entry -> entry.getValue() >= MIN_RECORDS && !decided.contains(entry.getKey()))
                    .map(Map.Entry::getKey)
                    .toList();
            final String kind = kind(counts, recurring);
            for (final String value : recurring) {
                final boolean known = isKnown(value);
                final boolean misfit = kind != null && PyText.len(value) <= MAX_MISFIT_LENGTH
                                       && !kind.equals(kindOf(value));
                if (known || misfit) {
                    final Found was = found.get(value);
                    final List<String> fields = new ArrayList<>(was == null
                            ? List.of()
                            : was.fields());
                    fields.add(field);
                    found.put(value, new Found(value, List.copyOf(fields),
                            (was == null
                                    ? 0
                                    : was.records()) + counts.get(value).intValue(),
                            was != null && was.kind() != null
                                    ? was.kind()
                                    : kindWords(kind)));
                }
            }
        });
        return found.values().stream()
                .sorted(Comparator.comparingInt(Found::records).reversed())
                .toList();
    }

    /// @param value A value.
    /// @return True if it is one of the usual stand-ins for none ([#KNOWN]).
    public static boolean isKnown(final String value) {
        return value != null && KNOWN.contains(PyText.strip(value).toLowerCase(Locale.ROOT));
    }

    // The kind nearly all the field's values are, or null. The recurring values not of a kind, which may be
    // stand-ins, aren't counted against it: a field of addresses with a '-' in many records is a field of addresses,
    // and one of times with a recurring 0 a field of times. At least MIN_RECORDS values must be of the kind.
    private static String kind(final Map<String, Long> counts, final List<String> recurring) {
        final Map<String, Long> byKind = new LinkedHashMap<>();
        counts.forEach((value, count) -> {
            final String kind = kindOf(value);
            if (kind != null) {
                byKind.merge(kind, count, Long::sum);
            }
        });
        for (final Map.Entry<String, Long> entry : byKind.entrySet()) {
            final String kind = entry.getKey();
            final long counted = counts.entrySet().stream()
                    .filter(value -> !recurring.contains(value.getKey()) || kind.equals(kindOf(value.getKey())))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            if (entry.getValue() >= MIN_RECORDS && entry.getValue() >= KIND_SHARE * counted) {
                return kind;
            }
        }
        return null;
    }

    // The strict kind of a value (see KINDS), or null for text.
    private static String kindOf(final String value) {
        final String type = SampleProfiler.valueType(value);
        final String kind = type.startsWith("timestamp")
                ? "timestamp"
                : type;
        return KINDS.containsKey(kind)
                ? ("decimal".equals(kind)
                        ? "integer"
                        : kind)
                : null;
    }

    // How a kind is told to the user, or null.
    private static String kindWords(final String kind) {
        return kind == null
                ? null
                : KINDS.get(kind);
    }

    /// A value standing for none.
    ///
    /// @param value   The value, e.g. `-`.
    /// @param fields  The fields holding it, in the order found.
    /// @param records How many of the fields' values it is, across them all.
    /// @param kind    What the fields' other values are (e.g. `IP addresses`), when that is why it stood out, or
    ///                null.
    public record Found(String value, List<String> fields, int records, String kind) {

    }
}
