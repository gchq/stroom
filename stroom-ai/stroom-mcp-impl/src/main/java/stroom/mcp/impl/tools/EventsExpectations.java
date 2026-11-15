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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.indexing.PlannedValues;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// What verify_index's searches must find, counted from the Events streams verified rather than taken from the agent
/// or the index (seen in chat 177: the agent's asserted counts were wrong four times, then it took "every expected
/// count from the index itself" and reran until 23/23 passed). Each field is read from the Events by the source path
/// the plan kept with the indexing XSLT gives it ([PlannedValues]), so a check passes only when the index holds what
/// the Events do.
///
/// A stream is read from its start, at most [#PER_STREAM] events and [#MOST] in all, and the searches are of the same
/// events: a whole stream by its StreamId, a longer one by its first events' EventIds.
class EventsExpectations {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventsExpectations.class);

    /// The most events read of a stream.
    static final int PER_STREAM = 1_000;
    /// The most events read in all.
    static final int MOST = 2_000;
    /// A share of events (%) above which a core field's being empty is reported.
    static final double MOSTLY_EMPTY = 50.0;
    // Characters of the records read of one stream.
    private static final int BUDGET = 20_000_000;
    // The conditions counted on any field, and those counted only on a number.
    private static final Set<String> ANY_FIELD = Set.of("EQUALS", "NOT_EQUALS", "IN");
    private static final Set<String> RANGES = Set.of("GREATER_THAN", "GREATER_THAN_OR_EQUAL_TO", "LESS_THAN",
            "LESS_THAN_OR_EQUAL_TO", "BETWEEN");
    private static final Set<String> NUMBERS = Set.of("long", "double", "id");

    private final StreamReader streamReader;

    /// @param streamReader Reads the Events streams' records.
    @Inject
    EventsExpectations(final StreamReader streamReader) {
        this.streamReader = streamReader;
    }

    /// Reads the streams' first events, as the plan's indexing XSLT reads them.
    ///
    /// @param plan      The plan kept with the indexing XSLT.
    /// @param streamIds The Events streams verified.
    /// @return What they hold.
    Sample read(final FieldPlan plan, final List<Long> streamIds) {
        final List<Part> parts = new ArrayList<>();
        final List<String> unread = new ArrayList<>();
        int remaining = MOST;
        for (final long streamId : streamIds) {
            final int want = Math.min(PER_STREAM, remaining);
            if (want <= 0) {
                unread.add(streamId + " (past the " + ToolText.thousands(MOST) + " events read)");
                continue;
            }
            final StreamReader.Records records;
            try {
                records = streamReader.readRecords(streamId, 0, want, null, BUDGET);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Events of stream {} not read: {}", streamId, e.getMessage()));
                unread.add(streamId + " (" + StreamSummaries.reason(e) + ")");
                continue;
            }
            if (records == null) {
                unread.add(streamId + " (no records)");
                continue;
            }
            final Long total = NullSafe.get(records.first(), StreamReader.Fetched::totalItems);
            final int read = records.records().size();
            final Part part = new Part(streamId, read, total != null && read >= total,
                    PlannedValues.of(plan, records.records()));
            if (!part.whole() && part.values().eventsPerRecord().stream().anyMatch(n -> n != 1)) {
                // EventId numbers events, not records: a part of a stream holding several events a record can't be
                // searched by its EventIds.
                unread.add(streamId + " (records of several events, and longer than " + read + " read)");
                continue;
            }
            parts.add(part);
            remaining -= read;
        }
        return new Sample(plan, parts, unread);
    }

    /// @param condition     A search's condition, as a dashboard names it.
    /// @param value         Its value (BETWEEN: `from,to`; IN: values separated by commas; EQUALS takes `*` and
    ///                      `?` wildcards).
    /// @param values        A document's values of the field.
    /// @param type          The plan's logical type of the field.
    /// @param caseSensitive Whether the index compares text by case (Elasticsearch's keyword does; the Lucene
    ///                      fields the tools make don't).
    /// @return Whether a search finds the document, or null for a search not counted from the Events.
    static Boolean matches(final String condition,
                           final String value,
                           final List<String> values,
                           final String type,
                           final boolean caseSensitive) {
        final boolean number = NUMBERS.contains(type);
        final String given = NullSafe.string(value);
        if ("EQUALS".equals(condition) || "NOT_EQUALS".equals(condition)) {
            final boolean wild = given.contains("*") || given.contains("?");
            if (wild && (number || "ip".equals(type))) {
                return null;
            }
            final boolean found = values.stream().anyMatch(held -> wild
                    ? glob(given, caseSensitive).matcher(held).matches()
                    : same(held, given, number, caseSensitive));
            return "EQUALS".equals(condition) == found;
        }
        if ("IN".equals(condition)) {
            final List<String> any = List.of(given.split(",", -1)).stream().map(String::strip)
                    .filter(item -> !item.isEmpty()).toList();
            return values.stream().anyMatch(held -> any.stream().anyMatch(item -> same(held, item, number,
                    caseSensitive)));
        }
        if (RANGES.contains(condition) && number) {
            return values.stream().anyMatch(held -> inRange(condition, given, held));
        }
        return null;
    }

    private static boolean same(final String held, final String given, final boolean number,
                                final boolean caseSensitive) {
        if (number) {
            final BigDecimal a = decimal(held);
            final BigDecimal b = decimal(given);
            return a != null && b != null && a.compareTo(b) == 0;
        }
        return caseSensitive
                ? held.equals(given)
                : held.equalsIgnoreCase(given);
    }

    private static boolean inRange(final String condition, final String given, final String held) {
        final BigDecimal at = decimal(held);
        if (at == null) {
            return false;
        }
        if ("BETWEEN".equals(condition)) {
            final String[] ends = given.split(",", -1);
            final BigDecimal from = ends.length == 2
                    ? decimal(ends[0])
                    : null;
            final BigDecimal to = ends.length == 2
                    ? decimal(ends[1])
                    : null;
            return from != null && to != null && at.compareTo(from) >= 0 && at.compareTo(to) <= 0;
        }
        final BigDecimal bound = decimal(given);
        if (bound == null) {
            return false;
        }
        final int compared = at.compareTo(bound);
        return switch (condition) {
            case "GREATER_THAN" -> compared > 0;
            case "GREATER_THAN_OR_EQUAL_TO" -> compared >= 0;
            case "LESS_THAN" -> compared < 0;
            default -> compared <= 0;
        };
    }

    private static BigDecimal decimal(final String text) {
        try {
            return new BigDecimal(NullSafe.string(text).strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // A wildcard value as a pattern: * any characters, ? one.
    private static Pattern glob(final String value, final boolean caseSensitive) {
        final StringBuilder regex = new StringBuilder();
        for (final char c : value.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL | (caseSensitive
                ? 0
                : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }


    // --------------------------------------------------------------------------------


    /// The events read of one stream.
    ///
    /// @param streamId The stream.
    /// @param read     How many of its records were read, from its first.
    /// @param whole    True if they are all of it.
    /// @param values   What the plan's XSLT indexes of them.
    record Part(long streamId, int read, boolean whole, PlannedValues values) {

    }

    /// A count of what a search must find, from the Events.
    ///
    /// @param count  The documents it must find, or null when it can't be counted from the Events.
    /// @param source The Events path the field is read from, or null.
    /// @param why    Why it can't be counted, or null.
    record Expectation(Long count, String source, String why) {

    }

    /// The Events read for verification, and what the index's searches of the same events must find.
    ///
    /// @param plan   The plan kept with the indexing XSLT.
    /// @param parts  The streams read.
    /// @param unread The streams not read, each with why.
    record Sample(FieldPlan plan, List<Part> parts, List<String> unread) {

        /// @return True if any events were read.
        boolean any() {
            return parts.stream().anyMatch(part -> part.values().indexed() > 0);
        }

        /// @return The events read that the index holds (drop_when's left out).
        int indexed() {
            return parts.stream().mapToInt(part -> part.values().indexed()).sum();
        }

        /// @return The searches' scope: each whole stream by its StreamId, a longer one by its first EventIds.
        ExpressionOperator scope() {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            for (final Part part : parts) {
                if (part.whole()) {
                    or.addTerm(IndexChecks.term("StreamId", Condition.EQUALS, String.valueOf(part.streamId())));
                } else {
                    or.addOperator(ExpressionOperator.builder()
                            .addTerm(IndexChecks.term("StreamId", Condition.EQUALS, String.valueOf(part.streamId())))
                            .addTerm(IndexChecks.term("EventId", Condition.LESS_THAN_OR_EQUAL_TO,
                                    String.valueOf(part.read())))
                            .build());
                }
            }
            return or.build();
        }

        /// @return The events counted, as the user reads it.
        String said() {
            final List<String> each = parts.stream()
                    .map(part -> part.whole()
                            ? "stream " + part.streamId()
                            : "stream " + part.streamId() + "'s events 1 to " + ToolText.thousands(part.read()))
                    .toList();
            final String streams = each.size() <= 1
                    ? String.join("", each)
                    : String.join(", ", each.subList(0, each.size() - 1)) + " and " + each.getLast();
            return streams + " (" + ToolText.thousands(indexed()) + " events indexed)";
        }

        /// @param field     The index's field.
        /// @param condition The search's condition.
        /// @param value     Its value.
        /// @return The documents the search must find in [#scope], from the Events.
        Expectation expect(final String field, final String condition, final String value) {
            if (!ANY_FIELD.contains(condition) && !RANGES.contains(condition)) {
                return new Expectation(null, null, condition + " isn't counted from the Events (only EQUALS, "
                                                   + "NOT_EQUALS, IN and number ranges)");
            }
            final PlannedField planned = plan.leafFields().stream()
                    .filter(leaf -> leaf.name().equals(field))
                    .findFirst()
                    .orElse(null);
            final String type = planned == null
                    ? null
                    : planned.type();
            long count = 0;
            String source = null;
            for (final Part part : parts) {
                final PlannedValues.Written written = part.values().written(field);
                if (!written.known()) {
                    return new Expectation(null, null, written.unknown());
                }
                source = written.source();
                for (final List<String> values : written.perEvent()) {
                    final Boolean found = matches(condition, value, values, type, plan.isElasticsearch());
                    if (found == null) {
                        return new Expectation(null, source, condition + " '" + value + "' on a " + type
                                                             + " field isn't counted from the Events");
                    }
                    if (found) {
                        count++;
                    }
                }
            }
            return new Expectation(count, source, null);
        }

        /// The core fields of the plan's convention (ECS's `level: core`, and the time field) that most events read
        /// hold no value for: a finding for the user (seen in chat 177: event.action empty in 2,233 of 2,457
        /// documents, and nothing said so).
        ///
        /// @param ecs The ECS schema, or null.
        /// @return Each such field: `field`, `source`, `empty_in`, and `required` for the time field.
        List<Map<String, Object>> mostlyEmpty(final EcsSchema ecs) {
            return mostlyEmpty(ecs, null);
        }

        /// As [#mostlyEmpty(EcsSchema)], with the fields the index lists: a core field of the plan it doesn't list
        /// (no document gave it a value, so Elasticsearch never mapped it) is a finding too, however many events
        /// hold it. Seen in chat 178: event.action written by no event, unmapped, and a search on it refused before
        /// anything said so.
        ///
        /// @param ecs    The ECS schema, or null.
        /// @param listed The fields the index lists, or null when they aren't known.
        /// @return Each such field: `field`, `source`, `empty_in`, `not_in_index` for one the index doesn't list,
        /// and `required` for the time field.
        List<Map<String, Object>> mostlyEmpty(final EcsSchema ecs, final Set<String> listed) {
            final int indexed = indexed();
            final List<Map<String, Object>> out = new ArrayList<>();
            if (indexed == 0) {
                return out;
            }
            for (final PlannedField field : plan.leafFields()) {
                final boolean required = field.name().equals(plan.timeField());
                if (!required && !core(field.name(), plan, ecs)) {
                    continue;
                }
                final boolean unlisted = listed != null && !listed.isEmpty() && !listed.contains(field.name());
                long empty = 0;
                String source = null;
                boolean known = true;
                for (final Part part : parts) {
                    final PlannedValues.Written present = part.values().present(field.name());
                    if (!present.known()) {
                        known = false;
                        break;
                    }
                    source = present.source();
                    empty += present.perEvent().stream()
                            .filter(values -> values.stream().allMatch(String::isBlank))
                            .count();
                }
                if (!unlisted && (!known || 100.0 * empty / indexed <= MOSTLY_EMPTY)) {
                    continue;
                }
                final Map<String, Object> finding = new LinkedHashMap<>();
                finding.put("field", field.name());
                finding.put("source", NullSafe.nonBlankStringElse(source, field.source()));
                if (known) {
                    finding.put("empty_in", ToolText.thousands(empty) + " of " + ToolText.thousands(indexed)
                                            + " events read");
                }
                if (unlisted) {
                    finding.put("not_in_index", "the index doesn't list it: no document has a value for it");
                }
                if (required) {
                    finding.put("required", true);
                }
                out.add(finding);
            }
            return out;
        }

        private static boolean core(final String name, final FieldPlan plan, final EcsSchema ecs) {
            if (ecs == null || !"ecs".equals(NullSafe.string(plan.convention()).toLowerCase(Locale.ROOT))) {
                return false;
            }
            final Map<String, Object> spec = ecs.field(name);
            return spec != null && "core".equals(spec.get("level"));
        }
    }
}
