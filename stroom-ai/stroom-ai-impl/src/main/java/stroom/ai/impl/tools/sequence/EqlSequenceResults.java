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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticEql.Event;
import stroom.ai.api.ElasticEql.Sequence;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Step;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/// What an `sequence_query` call's sequences become: a row each (join keys, each step's time, the gaps between steps,
/// the fields chosen, each step's StreamId/EventId), a summary per entity (the join keys' values), and a StroomQL
/// review query per document that finds the events again through a Stroom search.
///
/// The review query names the hit events exactly, grouped by stream, while that fits the longest query allowed;
/// past that, or where the index has no StreamId and EventId, it finds the entities' events in the window the
/// sequences span, which holds the hits and the entities' other events.
public final class EqlSequenceResults {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EqlSequenceResults.class);

    /// The stream id field Stroom indexes every event with.
    public static final String STREAM_ID = "StreamId";
    /// The event id field Stroom indexes every event with.
    public static final String EVENT_ID = "EventId";
    /// The characters a review query keeps clear of the longest query allowed, for the user's own edits.
    static final int REVIEW_MARGIN = 100;
    /// Why a review query isn't exact: naming the hits would be longer than a query may be.
    static final String TOO_LONG = "too long";
    /// Why a review query isn't exact: the doc's index has no StreamId and EventId.
    static final String NO_IDS = "no ids";
    /// The most entities a review query names; past it, only the window.
    static final int MAX_REVIEW_ENTITIES = 100;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z_@][A-Za-z0-9_@.]*");

    private EqlSequenceResults() {
        // Static only.
    }

    /// Reads each sequence's events by step: a step with `runs` has that many, a missing step none (its place
    /// held by Elasticsearch's missing event).
    ///
    /// @param sequences The sequences.
    /// @param steps     The steps, in order.
    /// @param timeField The documents' time field.
    /// @return A row per sequence.
    static List<Row> rows(final List<Sequence> sequences, final List<Step> steps, final String timeField) {
        final List<Row> rows = new ArrayList<>();
        for (final Sequence sequence : sequences) {
            final List<List<Hit>> byStep = new ArrayList<>();
            int at = 0;
            for (final Step step : steps) {
                final List<Hit> hits = new ArrayList<>();
                for (int run = 0; run < Math.max(1, step.runs()); run++) {
                    final Event event = at < sequence.events().size()
                            ? sequence.events().get(at)
                            : null;
                    at++;
                    if (event != null && !event.missing() && !step.missing()) {
                        hits.add(hit(event, timeField));
                    }
                }
                byStep.add(hits);
            }
            rows.add(new Row(sequence.joinKeys(), byStep));
        }
        return rows;
    }

    private static Hit hit(final Event event, final String timeField) {
        final Long time = epochMillis(first(event.fields().get(timeField)));
        return new Hit(time, text(first(event.fields().get(STREAM_ID))), text(first(event.fields().get(EVENT_ID))),
                event.fields());
    }

    /// The table's columns: the join keys (named as the first step names them), then for each step its time (and
    /// last time and count, with runs), its fields, and its StreamId and EventId; the gap after the step before,
    /// in seconds; and the span from the first event to the last, in seconds.
    ///
    /// @param steps The steps.
    /// @return The columns.
    static List<String> columns(final List<Step> steps) {
        final List<String> columns = new ArrayList<>(steps.getFirst().joinFields());
        for (int i = 0; i < steps.size(); i++) {
            final Step step = steps.get(i);
            final String prefix = "s" + (i + 1) + "_";
            if (step.missing()) {
                continue;
            }
            if (i > 0 && previousPresent(steps, i) >= 0) {
                columns.add("gap_" + (previousPresent(steps, i) + 1) + "_" + (i + 1) + "_s");
            }
            columns.add(prefix + "time");
            if (step.runs() > 1) {
                columns.add(prefix + "last_time");
                columns.add(prefix + "count");
            }
            step.fields().forEach(field -> columns.add(prefix + field));
            columns.add(prefix + STREAM_ID);
            columns.add(prefix + EVENT_ID);
        }
        columns.add("span_s");
        return columns;
    }

    /// @param row   A sequence.
    /// @param steps The steps.
    /// @return Its cells, a cell per [#columns(List)].
    static List<String> cells(final Row row, final List<Step> steps) {
        final List<String> cells = new ArrayList<>();
        for (int k = 0; k < steps.getFirst().joinFields().size(); k++) {
            cells.add(k < row.joinKeys().size()
                    ? text(row.joinKeys().get(k))
                    : "");
        }
        for (int i = 0; i < steps.size(); i++) {
            final Step step = steps.get(i);
            if (step.missing()) {
                continue;
            }
            final List<Hit> hits = row.byStep().get(i);
            final int previous = previousPresent(steps, i);
            if (i > 0 && previous >= 0) {
                cells.add(seconds(gap(row, previous, i)));
            }
            cells.add(hits.isEmpty()
                    ? ""
                    : iso(hits.getFirst().time()));
            if (step.runs() > 1) {
                cells.add(hits.isEmpty()
                        ? ""
                        : iso(hits.getLast().time()));
                cells.add(String.valueOf(hits.size()));
            }
            for (final String field : step.fields()) {
                final Set<String> values = new LinkedHashSet<>();
                hits.forEach(hit -> NullSafe.list(hit.fields().get(field)).forEach(value -> values.add(text(value))));
                cells.add(String.join(", ", values));
            }
            cells.add(String.join(", ", hits.stream().map(Hit::streamId).toList()));
            cells.add(String.join(", ", hits.stream().map(Hit::eventId).toList()));
        }
        cells.add(seconds(span(row)));
        return cells;
    }

    // The step before this one that has events, or -1.
    private static int previousPresent(final List<Step> steps, final int i) {
        for (int j = i - 1; j >= 0; j--) {
            if (!steps.get(j).missing()) {
                return j;
            }
        }
        return -1;
    }

    // From the last event of one step to the first of a later one, in milliseconds, or null if not known.
    private static Long gap(final Row row, final int from, final int to) {
        final List<Hit> before = row.byStep().get(from);
        final List<Hit> after = row.byStep().get(to);
        if (before.isEmpty() || after.isEmpty() || before.getLast().time() == null
            || after.getFirst().time() == null) {
            return null;
        }
        return after.getFirst().time() - before.getLast().time();
    }

    /// @param row A sequence.
    /// @return From its first event to its last, in milliseconds, or null if not known.
    static Long span(final Row row) {
        final Long first = row.firstTime();
        final Long last = row.lastTime();
        return first == null || last == null
                ? null
                : last - first;
    }

    /// @param rows The sequences.
    /// @return How many have no time on any of their events, so show empty times.
    static int untimed(final List<Row> rows) {
        return (int) rows.stream()
                .filter(row -> row.firstTime() == null)
                .count();
    }

    /// A summary per entity (the join keys' values): how many sequences, the first and last time, and the shortest
    /// span from a sequence's first event to its last. The most sequences first.
    ///
    /// @param rows The sequences.
    /// @return The entities.
    static List<Entity> entities(final List<Row> rows) {
        final Map<List<String>, Entity> byKey = new LinkedHashMap<>();
        for (final Row row : rows) {
            final List<String> key = row.joinKeys().stream().map(EqlSequenceResults::text).toList();
            final Entity entity = byKey.getOrDefault(key, new Entity(key, 0, null, null, null));
            byKey.put(key, entity.with(row));
        }
        return byKey.values().stream()
                .sorted(Comparator.comparingLong(Entity::sequences).reversed())
                .toList();
    }

    /// The StroomQL review query of one document. Exact where it can be: its hit events named by StreamId and
    /// EventId, grouped by stream (`(StreamId = 1 and EventId in (2, 5)) or (StreamId = 3 and EventId = 4)`), while
    /// that fits [#REVIEW_MARGIN] inside the longest query allowed. Else, or where the doc's index has no StreamId
    /// and EventId (filled from outside Stroom), the entities' events (by the doc's join fields) in the window the
    /// sequences span; past [#MAX_REVIEW_ENTITIES] entities, or too long, the window alone.
    ///
    /// @param doc       The document.
    /// @param timeField The time field.
    /// @param hasIds    True if the doc's index has StreamId and EventId fields.
    /// @param rows      The sequences.
    /// @param steps     The steps.
    /// @param docIndex  The document's position (from 1).
    /// @param maxLength The longest the query may be.
    /// @return The query, or null if the doc has no hits, or no form fits within the length.
    static Review review(final DocRef doc,
                         final String timeField,
                         final boolean hasIds,
                         final List<Row> rows,
                         final List<Step> steps,
                         final int docIndex,
                         final int maxLength) {
        final List<Integer> docSteps = new ArrayList<>();
        final Set<String> shown = new LinkedHashSet<>();
        if (hasIds) {
            shown.add(STREAM_ID);
            shown.add(EVENT_ID);
        }
        shown.add(timeField);
        for (int i = 0; i < steps.size(); i++) {
            final Step step = steps.get(i);
            if (step.doc() == docIndex && !step.missing()) {
                docSteps.add(i);
                shown.addAll(step.joinFields());
                shown.addAll(step.fields());
            }
        }
        if (docSteps.isEmpty()) {
            return null;
        }
        final String head = "from \"" + doc.getName().replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n";
        // StroomQL takes sort before select.
        final String tail = "\nsort by " + name(timeField) + "\nselect "
                            + String.join(", ", shown.stream().map(EqlSequenceResults::name).toList());
        final int limit = maxLength - REVIEW_MARGIN;
        boolean allIds = hasIds;
        if (hasIds) {
            final Map<String, Set<String>> byStream = new LinkedHashMap<>();
            int events = 0;
            for (final Row row : rows) {
                for (final int i : docSteps) {
                    for (final Hit hit : row.byStep().get(i)) {
                        if (hit.streamId().isEmpty() || hit.eventId().isEmpty()) {
                            allIds = false;
                        } else if (byStream.computeIfAbsent(hit.streamId(), key -> new LinkedHashSet<>())
                                .add(hit.eventId())) {
                            events++;
                        }
                    }
                }
            }
            if (allIds && !byStream.isEmpty()) {
                final List<String> terms = new ArrayList<>();
                byStream.forEach((stream, ids) -> terms.add("(" + STREAM_ID + " = " + stream + " and " + EVENT_ID
                                                            + (ids.size() == 1
                        ? " = " + ids.iterator().next()
                        : " in (" + String.join(", ", ids) + ")") + ")"));
                final String query = head + "where " + String.join("\nor ", terms) + tail;
                if (query.length() <= limit) {
                    return new Review(doc, query, true, true, events, null);
                }
            }
        }
        final String why = allIds
                ? TOO_LONG
                : NO_IDS;
        // The window the sequences span, and the entities.
        Long from = null;
        Long to = null;
        final Set<String> entities = new LinkedHashSet<>();
        for (final Row row : rows) {
            for (final int i : docSteps) {
                for (final Hit hit : row.byStep().get(i)) {
                    if (hit.time() != null) {
                        from = from == null
                                ? hit.time()
                                : Math.min(from, hit.time());
                        to = to == null
                                ? hit.time()
                                : Math.max(to, hit.time());
                    }
                }
                final List<String> fields = steps.get(i).joinFields();
                final List<String> terms = new ArrayList<>();
                for (int k = 0; k < fields.size() && k < row.joinKeys().size(); k++) {
                    terms.add(name(fields.get(k)) + " = " + stroomQlString(text(row.joinKeys().get(k))));
                }
                if (!terms.isEmpty()) {
                    entities.add("(" + String.join(" and ", terms) + ")");
                }
            }
        }
        if (from == null) {
            return null;
        }
        final String window = name(timeField) + " >= " + iso(from) + " and " + name(timeField) + " <= " + iso(to);
        if (entities.size() <= MAX_REVIEW_ENTITIES) {
            final String query = head + "where " + window + "\nand (" + String.join("\nor ", entities) + ")" + tail;
            if (query.length() <= limit) {
                return new Review(doc, query, false, true, entities.size(), why);
            }
        }
        final String query = head + "where " + window + tail;
        return query.length() <= limit
                ? new Review(doc, query, false, false, entities.size(), why)
                : null;
    }

    /// @param rows      The sequences.
    /// @param steps     The steps.
    /// @param docIndex  A document's position, or 0 for every document.
    /// @return How many distinct hit events the sequences hold (in that document's steps), and whether each has a
    /// StreamId and EventId.
    static EventCount eventCount(final List<Row> rows, final List<Step> steps, final int docIndex) {
        final Set<String> ids = new LinkedHashSet<>();
        boolean allIds = true;
        final Predicate<Step> inDoc = step -> docIndex == 0 || step.doc() == docIndex;
        for (final Row row : rows) {
            for (int i = 0; i < steps.size(); i++) {
                if (!inDoc.test(steps.get(i))) {
                    continue;
                }
                for (final Hit hit : row.byStep().get(i)) {
                    if (hit.streamId().isEmpty() || hit.eventId().isEmpty()) {
                        allIds = false;
                    }
                    ids.add(steps.get(i).doc() + ":" + hit.streamId() + ":" + hit.eventId());
                }
            }
        }
        return new EventCount(ids.size(), allIds);
    }

    /// @param ms Epoch milliseconds, or null.
    /// @return The time in ISO 8601, UTC, or an empty string.
    static String iso(final Long ms) {
        return ms == null
                ? ""
                : ISO.format(Instant.ofEpochMilli(ms));
    }

    /// @param ms Milliseconds, or null.
    /// @return Them in seconds, to the millisecond, or an empty string.
    static String seconds(final Long ms) {
        if (ms == null) {
            return "";
        }
        return ms % 1_000 == 0
                ? String.valueOf(ms / 1_000)
                : String.format(Locale.ROOT, "%.3f", ms / 1_000.0);
    }

    // A field as StroomQL names it: as it is, or in double quotes.
    private static String name(final String field) {
        return PLAIN_NAME.matcher(field).matches()
                ? field
                : stroomQlString(field);
    }

    private static String stroomQlString(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Object first(final List<Object> values) {
        return NullSafe.isEmptyCollection(values)
                ? null
                : values.getFirst();
    }

    /// An event's time as Elasticsearch gives it (format `epoch_millis`): a whole number of milliseconds, as a
    /// number or as text; a `date_nanos` field gives a decimal (`1768471560123.456`), and a number may come as a
    /// double or in scientific notation. The fraction of a millisecond is dropped.
    ///
    /// @param value The time field's first value, or null.
    /// @return The time in epoch milliseconds, or null if there is none or it can't be read.
    static Long epochMillis(final Object value) {
        if (value == null) {
            return null;
        }
        try {
            final BigDecimal millis = switch (value) {
                case final Long number -> BigDecimal.valueOf(number);
                case final Integer number -> BigDecimal.valueOf(number);
                case final BigDecimal number -> number;
                case final BigInteger number -> new BigDecimal(number);
                case final Number number -> new BigDecimal(number.toString());
                case final String text -> new BigDecimal(text.strip());
                default -> null;
            };
            if (millis != null) {
                return millis.setScale(0, RoundingMode.FLOOR).longValueExact();
            }
        } catch (final NumberFormatException | ArithmeticException e) {
            // Not a number of milliseconds, or too big for one.
        }
        LOGGER.debug(() -> LogUtil.message("Unable to read the event time '{}' ({}) as epoch milliseconds", value,
                value.getClass().getSimpleName()));
        return null;
    }

    /// @param value A value.
    /// @return It as a cell: empty for null, a whole number without a fraction.
    static String text(final Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof final Double number && number == Math.rint(number) && !number.isInfinite()
                   && Math.abs(number) < 1e15) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// One event of a sequence.
    ///
    /// @param time     Its time, in epoch milliseconds, or null if not known.
    /// @param streamId Its StreamId, or an empty string.
    /// @param eventId  Its EventId, or an empty string.
    /// @param fields   The fields fetched.
    record Hit(Long time, String streamId, String eventId, Map<String, List<Object>> fields) {

    }


    // --------------------------------------------------------------------------------


    /// One sequence, by step.
    ///
    /// @param joinKeys The join keys' values.
    /// @param byStep   Each step's events (none for a missing step).
    record Row(List<Object> joinKeys, List<List<Hit>> byStep) {

        /// @return The time of its first event, or null.
        Long firstTime() {
            return byStep.stream()
                    .flatMap(List::stream)
                    .map(Hit::time)
                    .filter(time -> time != null)
                    .min(Long::compare)
                    .orElse(null);
        }

        /// @return The time of its last event, or null.
        Long lastTime() {
            return byStep.stream()
                    .flatMap(List::stream)
                    .map(Hit::time)
                    .filter(time -> time != null)
                    .max(Long::compare)
                    .orElse(null);
        }
    }


    // --------------------------------------------------------------------------------


    /// One entity's sequences.
    ///
    /// @param keys         The join keys' values.
    /// @param sequences    How many sequences.
    /// @param first        The time of the first event of its first sequence, or null.
    /// @param last         The time of the last event of its last sequence, or null.
    /// @param shortestSpan The shortest span of one of its sequences, first event to last, in ms, or null.
    record Entity(List<String> keys, long sequences, Long first, Long last, Long shortestSpan) {

        /// @param row A sequence of the entity.
        /// @return The entity with the sequence counted.
        Entity with(final Row row) {
            final Long rowFirst = row.firstTime();
            final Long rowLast = row.lastTime();
            final Long rowSpan = span(row);
            return new Entity(keys, sequences + 1, min(first, rowFirst), max(last, rowLast),
                    min(shortestSpan, rowSpan));
        }

        // Plain if/else on Long: a conditional mixing Long and long unboxes, so two nulls threw a
        // NullPointerException (seen when an entity's first sequence had no time).
        private static Long min(final Long a, final Long b) {
            if (a == null) {
                return b;
            } else if (b == null) {
                return a;
            } else {
                return a <= b
                        ? a
                        : b;
            }
        }

        private static Long max(final Long a, final Long b) {
            if (a == null) {
                return b;
            } else if (b == null) {
                return a;
            } else {
                return a >= b
                        ? a
                        : b;
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// A review query.
    ///
    /// @param doc      The document it reads.
    /// @param query    The StroomQL.
    /// @param exact    True if it names the hit events; false if it finds the entities' events in the window.
    /// @param entities True if it filters by the entities (always when exact); false for the window alone.
    /// @param terms    How many events (exact) or entities it names.
    /// @param why      Why it isn't exact ([#TOO_LONG] or [#NO_IDS]), or null if it is.
    record Review(DocRef doc, String query, boolean exact, boolean entities, int terms, String why) {

    }


    // --------------------------------------------------------------------------------


    /// How many hit events there are.
    ///
    /// @param events How many distinct events.
    /// @param allIds True if every one has a StreamId and an EventId.
    record EventCount(int events, boolean allIds) {

    }
}
