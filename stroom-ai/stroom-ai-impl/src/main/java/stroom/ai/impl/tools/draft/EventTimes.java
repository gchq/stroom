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

import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.SyslogHeaders;
import stroom.ai.impl.tools.sample.TimePatterns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Which of a record's times is its event's: each time field ranked by what its name says it is, and the evidence
/// that tells two apart (how many records have each, how far apart they are). Generic: names any source uses for an
/// event's time, an object's times (an expiry, a file's creation, an account's last logon) and a container's (a
/// syslog header before the record), never one product's fields.
final class EventTimes {

    // Names said of an event's own time.
    private static final Pattern EVENT = Pattern.compile("(?i)^(@?timestamp|time|ts|date_?time|datetime|event_?time|"
                                                         + "eventtime|logged_?at|time_?created|occurred(_?at)?|"
                                                         + "when|_time|rt|system_?time|time_?generated|"
                                                         + "event_?timestamp|log_?time)$");
    // Names that may be the event's time or something's creation, or a period's ends: chosen only when nothing is
    // named as the event's time, and said to be checked.
    private static final Pattern AMBIGUOUS = Pattern.compile("(?i)^(created|created_?at|creation_?time|date|start|"
                                                             + "end|start_?time|end_?time|begin|begin_?time|"
                                                             + "received(_?at|_?time)?)$");
    // Names of an object's times, never the event's: kept as data.
    private static final Pattern OBJECT = Pattern.compile("(?i)^(.*expir.*|valid_?(from|to|until|before|after)|"
                                                          + "not_?(valid_?)?(before|after)|last_?.+|.*modified.*|"
                                                          + "updated(_?at)?|(old_?)?file_?\\w*time|pwd_?last_?set|"
                                                          + "password_?last_?set|.*birth.*|due(_?date)?)$");
    // A name ending as an event's time does (receive_time, generated_time, eventTimestamp, x-timestamp-unix), once
    // an object's and a may-be's are ruled out.
    private static final Pattern EVENT_LIKE = Pattern.compile("(?i)(time|timestamp)$|timestamp");
    // A name that says it holds a time of some kind: a number in it may be an epoch time or a FILETIME.
    private static final Pattern TIME_WORD = Pattern.compile("(?i)(time|date|stamp|_at$|logon|logoff|expir|"
                                                             + "created|modified|^ts$|^rt$|last_?set)");

    private EventTimes() {
        // Static only.
    }

    /// @param key A field's own name (the last part of its path).
    /// @return What its name says of it as a time.
    static Rank rank(final String key) {
        if (EVENT.matcher(key).matches()) {
            return Rank.EVENT;
        }
        if (OBJECT.matcher(key).matches()) {
            return Rank.OBJECT;
        }
        if (AMBIGUOUS.matcher(key).matches()) {
            return Rank.AMBIGUOUS;
        }
        if (EVENT_LIKE.matcher(key).find()) {
            return Rank.EVENT;
        }
        return Rank.PLAIN;
    }

    /// @param key A field's own name.
    /// @return Whether the name says it holds a time, so a number in it is read as one if it can be.
    static boolean namedAsTime(final String key) {
        return TIME_WORD.matcher(key).find() || rank(key) != Rank.PLAIN;
    }

    /// The fields a container writes around a record (a syslog header before CEF, JSON or pairs), by the spec that
    /// reads them; a time there is the relay's or the collector's, read after the record's own. A relay's header
    /// around the source's ([#relayFields]) is a container too.
    ///
    /// @param spec The splitter's spec, or null.
    /// @return The container's fields, none if the spec has no container.
    static Set<String> containerFields(final SplitterSpec spec) {
        final Set<String> out = new LinkedHashSet<>(relayFields(spec));
        final SplitterSpec inner = innermostHeader(spec);
        if (inner == null || inner.body() == null) {
            return out;
        }
        final Set<String> header = new LinkedHashSet<>(headerNames(inner));
        if (!"cef".equals(inner.kind())) {
            header.remove(inner.bodyField());
            header.remove("message");
        }
        out.addAll(header);
        return out;
    }

    /// The fields of the headers around the source's own: a relay's syslog header whose message is the source's
    /// line (syslog within syslog), a collector's words before a header. Their time is when a relay or collector
    /// passed the record on, read after any of the source's own.
    ///
    /// @param spec The splitter's spec, or null.
    /// @return The fields, outermost first; none if nothing wraps the source's header.
    static Set<String> relayFields(final SplitterSpec spec) {
        final Set<String> out = new LinkedHashSet<>();
        if (spec != null && !wrapsHeader(spec) && "regex".equals(spec.kind())) {
            // One regex for lines relayed and sent directly: its relay's fields are named so.
            spec.names().stream().filter(name -> name.startsWith(SyslogHeaders.RELAY_PREFIX)).forEach(out::add);
        }
        SplitterSpec at = spec;
        while (wrapsHeader(at)) {
            final List<String> names = new ArrayList<>(headerNames(at));
            names.remove(at.bodyField());
            out.addAll(names);
            at = at.body();
        }
        return out;
    }

    /// The fields whose text is a whole line the spec reads further as a syslog header (a relay's message, a
    /// collector's line after its words): every part of it is a field of its own, so the text itself isn't mapped.
    ///
    /// @param spec The splitter's spec, or null.
    /// @return The fields.
    static Set<String> wrappedTexts(final SplitterSpec spec) {
        final Set<String> out = new LinkedHashSet<>();
        SplitterSpec at = spec;
        while (wrapsHeader(at)) {
            out.add(at.bodyField());
            at = at.body();
        }
        return out;
    }

    // A spec whose body is a syslog header: the relay's header, or the regex reading a collector's words.
    private static boolean wrapsHeader(final SplitterSpec spec) {
        return spec != null && spec.body() != null && "syslog".equals(spec.body().kind())
               && ("syslog".equals(spec.kind()) || "regex".equals(spec.kind()));
    }

    // The source's own header: the spec itself, or the header inside every relay's.
    private static SplitterSpec innermostHeader(final SplitterSpec spec) {
        SplitterSpec at = spec;
        while (wrapsHeader(at)) {
            at = at.body();
        }
        return at;
    }

    private static List<String> headerNames(final SplitterSpec spec) {
        return switch (spec.kind()) {
            // CEF's body is the header before it.
            case "cef" -> spec.body() == null
                    ? List.of()
                    : Objects.requireNonNullElse(spec.body().names(), List.of());
            case "syslog" -> spec.syslogNames();
            default -> Objects.requireNonNullElse(spec.names(), List.of());
        };
    }

    /// What tells a kind's time fields apart, for the agent to choose from: each one's share of the records, its
    /// format, its rank, and how far its time is from each other's (the median, in seconds, over records holding
    /// both; never from the clock, as a source may send old events).
    ///
    /// @param fields  The fields, best first.
    /// @param formats Each field's time format.
    /// @param records The records they are in.
    /// @param keys    Each field's own name.
    /// @return One entry a field.
    static List<Map<String, Object>> evidence(final List<String> fields,
                                              final Map<String, String> formats,
                                              final List<Map<String, Object>> records,
                                              final Map<String, String> keys) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String field : fields) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", field);
            entry.put("share", share(field, records));
            entry.put("time_format", formats.get(field));
            entry.put("rank", rank(keys.get(field)).label());
            final Map<String, Object> distances = new LinkedHashMap<>();
            for (final String other : fields) {
                if (!other.equals(field)) {
                    final Double seconds = medianSeconds(field, other, formats, records);
                    if (seconds != null) {
                        distances.put(other, seconds);
                    }
                }
            }
            entry.put("from_others", distances);
            out.add(entry);
        }
        return out;
    }

    /// @param field   A field.
    /// @param records Records.
    /// @return The percentage of the records holding a value for it, rounded.
    static int share(final String field, final List<Map<String, Object>> records) {
        if (records.isEmpty()) {
            return 0;
        }
        final long held = records.stream().filter(record -> held(record, field)).count();
        return (int) Math.round(100.0 * held / records.size());
    }

    /// @param record A record.
    /// @param field  A field.
    /// @return Whether the record has a value for it.
    static boolean held(final Map<String, Object> record, final String field) {
        final Object value = record.get(field);
        return value != null && !String.valueOf(value).isBlank();
    }

    // The median distance between two fields' times, in seconds to a tenth, over the records holding both.
    private static Double medianSeconds(final String field,
                                        final String other,
                                        final Map<String, String> formats,
                                        final List<Map<String, Object>> records) {
        final List<Long> gaps = new ArrayList<>();
        for (final Map<String, Object> record : records) {
            if (held(record, field) && held(record, other)) {
                final Long one = TimePatterns.epochMillis(formats.get(field), String.valueOf(record.get(field)));
                final Long two = TimePatterns.epochMillis(formats.get(other), String.valueOf(record.get(other)));
                if (one != null && two != null) {
                    gaps.add(Math.abs(one - two));
                }
            }
        }
        if (gaps.isEmpty()) {
            return null;
        }
        Collections.sort(gaps);
        return Math.round(gaps.get(gaps.size() / 2) / 100.0) / 10.0;
    }

    /// What a field's name says of it as a time, best first.
    enum Rank {
        /// Named as the event's time.
        EVENT("event time"),
        /// Named as a creation, a period's start or end, or a date: maybe the event's.
        AMBIGUOUS("may be the event's time or something else's (a creation, a period's start or end): check"),
        /// A time whose name says nothing of it.
        PLAIN("a time its name says nothing of"),
        /// Named as an object's time: never the event's.
        OBJECT("an object's time (not the event's): left as data");

        private final String said;

        Rank(final String said) {
            this.said = said;
        }

        /// @return How the draft's notes describe it.
        String said() {
            return said;
        }

        /// @return Its name in time_candidates.
        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
