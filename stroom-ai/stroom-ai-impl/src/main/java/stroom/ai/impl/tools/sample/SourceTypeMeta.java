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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/// A source type field read from a stream part's meta (its receipt headers, what `stroom:meta()` reads) rather than
/// from its records: `meta:<Attribute>`, used only where the user names it (survey_feed's `source_type_field`), never
/// found by the survey on its own. Every record of a part has the part's value, so a source type group whose field is
/// a meta attribute passes over a whole part at once, never judging its records one by one.
///
/// The tools read a part's value as they read a record's ([SourceTypeFields#valueOf]): the part's attributes are
/// laid over each of its records' fields under the field's name ([#withPart]), so the source type profiles, scopes
/// and registry rows work on meta values unchanged.
public final class SourceTypeMeta {

    /// What a source type field read from a part's meta starts with.
    public static final String PREFIX = "meta:";
    // The most attributes said where a part lacks the one named.
    private static final int ATTRIBUTES_SAID = 8;

    private SourceTypeMeta() {
        // Static only.
    }

    /// @param field A source type field, or null.
    /// @return True if it is read from a part's meta (`meta:<Attribute>`).
    public static boolean isMeta(final String field) {
        return field != null && field.regionMatches(true, 0, PREFIX, 0, PREFIX.length())
               && !field.substring(PREFIX.length()).isBlank();
    }

    /// @param field A source type field read from a part's meta.
    /// @return The attribute it names, e.g. `ServedRoles` for `meta:ServedRoles`; null for a record field.
    public static String attribute(final String field) {
        return isMeta(field)
                ? field.substring(PREFIX.length()).strip()
                : null;
    }

    /// @param attribute A part's meta attribute.
    /// @return The source type field reading it: `meta:<attribute>`.
    public static String field(final String attribute) {
        return PREFIX + attribute;
    }

    /// The fields the tools lay over each record of a part: the field's value, as the part's meta holds it.
    ///
    /// @param field      The source type field, `meta:<Attribute>`.
    /// @param attributes The part's meta attributes (any case of name).
    /// @return The field and its value; none when the part doesn't carry the attribute.
    public static Map<String, Object> partFields(final String field, final Map<String, String> attributes) {
        final String attribute = attribute(field);
        if (attribute == null || attributes == null) {
            return Map.of();
        }
        return carried(attributes.keySet(), attribute)
                .map(name -> Map.<String, Object>of(field, attributes.get(name)))
                .orElse(Map.of());
    }

    /// @param parsed     A record's fields, or null for one with none.
    /// @param partFields The part's fields laid over it ([#partFields]).
    /// @return The record's fields with the part's over them; the record's own when the part has none.
    public static Map<String, Object> withPart(final Map<String, Object> parsed, final Map<String, Object> partFields) {
        if (partFields == null || partFields.isEmpty()) {
            return parsed;
        }
        final Map<String, Object> out = parsed == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(parsed);
        out.putAll(partFields);
        return out;
    }

    /// @param names     The attributes parts carry.
    /// @param attribute An attribute named by the user.
    /// @return It as the parts spell it (matched ignoring case, as `stroom:meta()` reads it); empty when none does.
    public static Optional<String> carried(final Iterable<String> names, final String attribute) {
        String found = null;
        for (final String name : names) {
            if (name.equals(attribute)) {
                return Optional.of(name);
            }
            if (found == null && name.equalsIgnoreCase(attribute)) {
                found = name;
            }
        }
        return Optional.ofNullable(found);
    }

    /// @param attributes Each part read's meta attributes.
    /// @return The attributes they carry, commonest first, at most eight, each with how many parts carry it, e.g.
    /// `RemoteHost (12 parts)`.
    public static List<String> commonest(final List<Map<String, String>> attributes) {
        final Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        attributes.forEach(part -> part.keySet().forEach(name -> counts.merge(name, 1, Integer::sum)));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.comparing(name -> name.toLowerCase(
                                Locale.ROOT)))))
                .limit(ATTRIBUTES_SAID)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + " part" + (entry.getValue() == 1
                        ? ""
                        : "s") + ")")
                .toList();
    }
}
