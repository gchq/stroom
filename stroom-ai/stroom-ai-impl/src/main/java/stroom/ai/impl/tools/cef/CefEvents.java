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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.util.shared.NullSafe;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The sample Events a CEF plan is drafted from and checked against: each Event's kind (its action element) and
/// its values by path, and what the plan's types make of them (an IPv4 address, a number, a time). Ported from the
/// Stroom MCP server's `utils/cef.py`.
public final class CefEvents {

    private static final Pattern NETWORK_ACTION = Pattern.compile("^EventDetail/Network/[^/]+/");
    private static final Pattern INTEGER = PyText.re("-?\\d+");
    private static final Pattern DECIMAL = PyText.re("-?\\d+(\\.\\d+)?");
    private static final Pattern MAC = Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}");
    private static final Pattern LONG = PyText.re("-?\\d{1,18}");
    private static final Pattern FLOAT = PyText.re("-?\\d+\\.\\d+");
    // datetime.fromisoformat (Python 3.12): a date (extended, basic or a week date), any one character, a time
    // (hours, then minutes and seconds with or without colons, a fraction), and an offset.
    private static final Pattern ISO = Pattern.compile(
            "(?<date>\\d{4}-\\d{2}-\\d{2}|\\d{8}|\\d{4}-?W\\d{2}(?:-?\\d)?)"
            + "(?:.(?<hour>\\d{2})(?::?(?<minute>\\d{2})(?::?(?<second>\\d{2})(?:[.,](?<fraction>\\d+))?)?)?"
            + "(?<offset>[+-]\\d{2}(?::?\\d{2}(?::?\\d{2}(?:[.,]\\d+)?)?)?)?)?",
            Pattern.DOTALL);
    private static final Pattern OFFSET = Pattern.compile("([+-])(\\d{2})(?::?(\\d{2})(?::?(\\d{2}))?)?.*");

    private CefEvents() {
        // Static only.
    }

    /// `events_of`: the Events in an XML text.
    ///
    /// @param xml The text, e.g. an Events stream.
    /// @return Its `event-logging:3` Event elements, in order.
    /// @throws XmlParser.XmlParseException if it doesn't parse.
    public static List<XmlElement> eventsOf(final String xml) {
        return XmlParser.parse(xml).iterElements(CefKeys.EL, "Event");
    }

    /// `event_kind`: an Event's kind.
    ///
    /// @param event An Event element.
    /// @return Its action element's name (the first child of EventDetail not in [CefKeys#NOT_ACTION]), or null.
    public static String eventKind(final XmlElement event) {
        final XmlElement detail = event.childElements().stream()
                .filter(c -> "EventDetail".equals(c.localName()))
                .findFirst()
                .orElse(null);
        if (detail == null) {
            return null;
        }
        return detail.childElements().stream()
                .map(XmlElement::localName)
                .filter(name -> !CefKeys.NOT_ACTION.contains(name))
                .findFirst()
                .orElse(null);
    }

    /// `event_values`: each value an Event holds, by its path from the Event (the first, where an element
    /// repeats); a Data as `Data[@Name='x']/@Value`; Network's action as `*`.
    ///
    /// @param event An Event element.
    /// @return Path to value, in document order.
    public static Map<String, String> eventValues(final XmlElement event) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final XmlElement child : event.childElements()) {
            walk(child, "", out);
        }
        final Map<String, String> anyAction = new LinkedHashMap<>();
        out.forEach((path, value) -> anyAction.put(NETWORK_ACTION.matcher(path).replaceFirst(
                "EventDetail/Network/*/"), value));
        return anyAction;
    }

    /// `_fits`: whether values suit a key's type.
    ///
    /// @param key    A key in the dictionary.
    /// @param values Values.
    /// @return True if every one is of the key's type (IPv4, a whole number, a decimal, a MAC address, a time);
    /// text fits anything.
    public static boolean fits(final String key, final List<String> values) {
        final String kind = CefKeys.KEYS.get(key).type();
        return switch (kind) {
            case "ip" -> values.stream().allMatch(CefEvents::isIpv4);
            case "int", "long" -> values.stream().allMatch(v -> INTEGER.matcher(v).matches());
            case "float" -> values.stream().allMatch(v -> DECIMAL.matcher(v).matches());
            case "mac" -> values.stream().allMatch(v -> MAC.matcher(v).matches());
            case "time" -> values.stream().allMatch(CefEvents::isTime);
            default -> true;
        };
    }

    /// `_is_time`: whether a value is a date and time, as Python's `datetime.fromisoformat` reads one (a `Z` for
    /// UTC), with a `T` in it.
    ///
    /// @param value A value.
    /// @return True if it is a time.
    public static boolean isTime(final String value) {
        return parseIso(value).isPresent() && value.contains("T");
    }

    /// `_slot_kind`: which custom slots values suit.
    ///
    /// @param values Values.
    /// @return `long`, `float`, `time` or `string`.
    public static String slotKind(final List<String> values) {
        if (!values.isEmpty() && values.stream().allMatch(v -> LONG.matcher(v).matches())) {
            return "long";
        }
        if (!values.isEmpty() && values.stream().allMatch(v -> FLOAT.matcher(v).matches())) {
            return "float";
        }
        if (!values.isEmpty() && values.stream().allMatch(CefEvents::isTime)) {
            return "time";
        }
        return "string";
    }

    /// A time as CEF sends it: `int(datetime.fromisoformat(value).timestamp() * 1000)`; a time with no offset is
    /// read in the server's time zone, as Python reads it.
    ///
    /// @param value A value.
    /// @return Its milliseconds since 1970, or empty if it is not a time.
    public static Optional<String> epochMillis(final String value) {
        return parseIso(value).map(time -> {
            final long seconds = time.toEpochSecond();
            final double timestamp = (seconds * 1_000_000.0 + time.getNano() / 1000) / 1_000_000.0;
            return String.valueOf((long) (timestamp * 1000));
        });
    }

    /// @param value A value.
    /// @return True if Python's `IPv4Address` takes it: four decimal parts of 0 to 255, no leading zeros.
    static boolean isIpv4(final String value) {
        final List<String> parts = PyText.split(value, ".");
        if (parts.size() != 4) {
            return false;
        }
        for (final String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(c -> c >= '0' && c <= '9')
                || (part.length() > 1 && part.charAt(0) == '0') || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    // The value as datetime.fromisoformat reads it, at its offset (or the server's zone).
    private static Optional<java.time.ZonedDateTime> parseIso(final String value) {
        final Matcher matcher = ISO.matcher(value.replace("Z", "+00:00"));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            final LocalDate date = date(matcher.group("date"));
            LocalTime time = LocalTime.MIDNIGHT;
            if (matcher.group("hour") != null) {
                final String fraction = matcher.group("fraction");
                final int nanos = fraction == null
                        ? 0
                        : Integer.parseInt((fraction + "000000").substring(0, 6)) * 1000;
                time = LocalTime.of(Integer.parseInt(matcher.group("hour")), number(matcher.group("minute")),
                        number(matcher.group("second")), nanos);
            }
            final String offset = matcher.group("offset");
            final ZoneId zone = offset == null
                    ? ZoneId.systemDefault()
                    : offset(offset);
            return Optional.of(LocalDateTime.of(date, time).atZone(zone));
        } catch (final DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static LocalDate date(final String text) {
        if (text.contains("W")) {
            final String digits = text.replace("-", "");
            final int year = Integer.parseInt(digits.substring(0, 4));
            final int week = Integer.parseInt(digits.substring(5, 7));
            final int day = digits.length() > 7
                    ? Integer.parseInt(digits.substring(7))
                    : 1;
            if (week < 1 || week > 53 || day < 1 || day > 7) {
                throw new DateTimeException("Invalid week date " + text);
            }
            final LocalDate first = LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 1)
                    .with(DayOfWeek.MONDAY);
            return first.plusWeeks(week - 1).plusDays(day - 1);
        }
        final String digits = text.replace("-", "");
        return LocalDate.of(Integer.parseInt(digits.substring(0, 4)), Integer.parseInt(digits.substring(4, 6)),
                Integer.parseInt(digits.substring(6, 8)));
    }

    private static ZoneOffset offset(final String text) {
        final Matcher matcher = OFFSET.matcher(text);
        if (!matcher.matches()) {
            throw new DateTimeException("Invalid offset " + text);
        }
        final int sign = "-".equals(matcher.group(1))
                ? -1
                : 1;
        return ZoneOffset.ofHoursMinutesSeconds(sign * Integer.parseInt(matcher.group(2)),
                sign * number(matcher.group(3)), sign * number(matcher.group(4)));
    }

    private static int number(final String digits) {
        return digits == null
                ? 0
                : Integer.parseInt(digits);
    }

    private static void walk(final XmlElement el, final String path, final Map<String, String> out) {
        final List<XmlElement> children = el.childElements();
        final String name = el.localName();
        if ("Data".equals(name) && JsonValues.truthy(el.get("Name"))) {
            final String here = path + "/Data[@Name='" + el.get("Name") + "']";
            if (el.get("Value") != null) {
                out.putIfAbsent(here + "/@Value", el.get("Value"));
            }
            for (final XmlElement child : children) {
                walk(child, here, out);
            }
            return;
        }
        final String here = path.isEmpty()
                ? name
                : path + "/" + name;
        if (children.isEmpty() && !PyText.strip(NullSafe.string(el.text())).isEmpty()) {
            out.putIfAbsent(here, PyText.strip(NullSafe.string(el.text())));
        }
        for (final XmlElement.Attribute attribute : el.attributes()) {
            if (attribute.namespace() == null) {
                out.putIfAbsent(here + "/@" + attribute.name(), attribute.value());
            }
        }
        for (final XmlElement child : children) {
            walk(child, here, out);
        }
    }
}
