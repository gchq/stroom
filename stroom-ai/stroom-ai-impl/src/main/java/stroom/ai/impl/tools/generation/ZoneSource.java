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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.ToolText;
import stroom.util.date.DateFormatterCache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Where a time's zone comes from, record by record (asked for by the user): an input field or a stream meta
/// attribute, its values read as Stroom's `stroom:format-date` reads a zone (`ZoneId.of`: IANA names, `UTC`, offsets
/// such as `+10:00` or `UTC+10`). Offsets it can't read but whose meaning is plain (`+5:30`, `+10.5`) are written as it
/// reads them; other values (abbreviations such as `AEST`, which can mean more than one zone) need `map`, as the user
/// says what each is. `default` is the zone where the source gives none. A value still unreadable in processing is
/// Stroom's: it warns, naming the record, and reads the time as UTC.
///
/// @param field        The input field holding the zone, or null.
/// @param meta         The stream meta attribute holding it, or null.
/// @param map          Values and the zones they are (the user's answers), or null.
/// @param defaultZone  `default`: the zone where the source gives none.
public record ZoneSource(String field,
                         String meta,
                         Map<String, String> map,
                         String defaultZone) {

    // An offset Stroom can't read but whose meaning is plain: one-digit hours (+5, +5:30), decimal hours (+10.5).
    private static final Pattern SHORT_HOURS = Pattern.compile("([+-])(\\d)(?::(\\d\\d))?");
    private static final Pattern DECIMAL_HOURS = Pattern.compile("([+-])(\\d{1,2})\\.(5|25|75)");
    // A UTC or GMT prefix before an offset.
    private static final Pattern PREFIXED = Pattern.compile("(UTC|GMT|UT)([+-].*)");

    /// Reads one from JSON-like values.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static ZoneSource read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "ZoneSource");
        if (fields == null) {
            return null;
        }
        final ZoneSource source = new ZoneSource(fields.optionalString("field"), fields.optionalString("meta"),
                fields.optionalStringMap("map"), fields.optionalString("default"));
        if (!fields.ok()) {
            return null;
        }
        final String problem = source.problem();
        if (problem != null) {
            fields.valueError(problem);
            return null;
        }
        return source;
    }

    // The model's own checks: one source, a default, and the zones it names readable by Stroom.
    private String problem() {
        if ((field == null) == (meta == null) || ToolText.isBlank(field == null
                ? meta
                : field)) {
            return "timezone as a source names one field or one meta attribute holding the zone, e.g. {\"field\": "
                   + "\"tz\", \"default\": \"UTC\"}";
        }
        if (meta != null && meta.contains("'")) {
            return "timezone meta names one stream meta attribute, e.g. 'TimeZone'";
        }
        if (defaultZone == null || unreadable(defaultZone)) {
            return "timezone as a source needs default: the zone where a record gives none, as the user agreed, and "
                   + "one Stroom reads (an IANA name such as Australia/Sydney, UTC, or an offset such as +10:00)";
        }
        if (map != null) {
            for (final Map.Entry<String, String> entry : map.entrySet()) {
                if (unreadable(entry.getValue())) {
                    return "timezone map: '" + entry.getKey() + "' is given as '" + entry.getValue() + "', which "
                           + "Stroom doesn't read as a zone: give an IANA name or an offset";
                }
            }
        }
        return null;
    }

    /// @param value A zone as a source gives it.
    /// @return It as Stroom is given it: mapped, else an offset's plain spelling written as Stroom reads it, else as
    /// it is; empty when blank. As the generated XSLT's `mcp:zone` writes it.
    public String asGiven(final String value) {
        final String zone = ToolText.strip(value == null
                ? ""
                : value);
        if (map != null && map.containsKey(zone)) {
            return map.get(zone);
        }
        return normalised(zone);
    }

    /// @param zone A zone, stripped.
    /// @return An offset Stroom can't read but whose meaning is plain, as it reads it; anything else as it is.
    static String normalised(final String zone) {
        final Matcher prefixed = PREFIXED.matcher(zone);
        if (prefixed.matches()) {
            return prefixed.group(1) + normalised(prefixed.group(2));
        }
        final Matcher shortHours = SHORT_HOURS.matcher(zone);
        if (shortHours.matches()) {
            return shortHours.group(1) + "0" + shortHours.group(2) + ":" + (shortHours.group(3) == null
                    ? "00"
                    : shortHours.group(3));
        }
        final Matcher decimal = DECIMAL_HOURS.matcher(zone);
        if (decimal.matches()) {
            final String hours = decimal.group(2).length() == 1
                    ? "0" + decimal.group(2)
                    : decimal.group(2);
            final String minutes = switch (decimal.group(3)) {
                case "25" -> "15";
                case "75" -> "45";
                default -> "30";
            };
            return decimal.group(1) + hours + ":" + minutes;
        }
        return zone;
    }

    /// @param values Values a source gives.
    /// @return Those Stroom wouldn't read as a zone once mapped and normalised (blank ones take the default), in
    /// order, each once.
    public List<String> unreadable(final List<String> values) {
        return values.stream()
                .filter(value -> !ToolText.isBlank(value) && unreadable(asGiven(value)))
                .distinct()
                .toList();
    }

    /// @param zone A zone.
    /// @return Whether Stroom's `stroom:format-date` would not read it (it warns and reads UTC).
    public static boolean unreadable(final String zone) {
        try {
            DateFormatterCache.getZoneId(zone);
            return false;
        } catch (final RuntimeException e) {
            return true;
        }
    }

    /// @param mode How to write it.
    /// @return It as JSON, keys in the model's order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("field", field, null)
                .put("meta", meta, null)
                .put("map", map == null
                        ? null
                        : new LinkedHashMap<>(map), null)
                .put("default", defaultZone, null)
                .build();
    }
}
