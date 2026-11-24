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

package stroom.ai.impl.tools.timeline;

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.KeyField;
import stroom.ai.api.ElasticKeyStreams.KeyRange;
import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.api.ElasticKeyStreams.Side;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.sequence.EqlSequenceResults;
import stroom.ai.impl.tools.sequence.JoinBudget;
import stroom.ai.impl.tools.sequence.JoinKeys;
import stroom.ai.impl.tools.sequence.KeyGroupSource;
import stroom.ai.impl.tools.sequence.KeyGroupSource.KeyGroup;
import stroom.ai.impl.tools.timeline.EventChains.Entry;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.PairInference.Candidate;
import stroom.ai.impl.tools.timeline.PairInference.Typed;
import stroom.docref.DocRef;
import stroom.query.api.TimeRange;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/// Checks a timeline skill's Event chains against recent data, as `check_skill` checks a skill: each entry's
/// index can be used and has a field holding the user; its event types are seen in the index's recent events (read
/// as a timeline reads them: their Events XML by StreamId and EventId), a pair's key path is held by its events, and
/// a pair still alternates for nearly every key (per user and host). Read as the reader, within a small budget.
public class TimelineChainCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TimelineChainCheck.class);

    /// How far back the recent data goes.
    static final Duration RECENT = Duration.ofDays(7);
    /// The most recent events read of an index.
    static final int MAX_EVENTS = 5_000;
    /// The longest the check of an index may take.
    static final Duration TIME_LIMIT = Duration.ofSeconds(30);

    private final Provider<ElasticKeyStreams> keyStreamsProvider;
    private final DataSourceAccess dataSourceAccess;
    private final TimelineEventXml eventXml;
    private final TimelineFields timelineFields;

    /// @param keyStreamsProvider Reads the indexes' recent events.
    /// @param dataSourceAccess   Finds the indexes the reader can use.
    /// @param eventXml           Reads the events' Events XML.
    /// @param timelineFields     Finds each index's user field.
    @Inject
    public TimelineChainCheck(final Provider<ElasticKeyStreams> keyStreamsProvider,
                              final DataSourceAccess dataSourceAccess,
                              final TimelineEventXml eventXml,
                              final TimelineFields timelineFields) {
        this.keyStreamsProvider = keyStreamsProvider;
        this.dataSourceAccess = dataSourceAccess;
        this.eventXml = eventXml;
        this.timelineFields = timelineFields;
    }

    /// @param session The reader's session.
    /// @param entries A skill's Event chains entries.
    /// @return What is wrong with them in recent data, a line each; empty if nothing is (or it can't be checked).
    public List<String> check(final ToolSession session, final List<Entry> entries) {
        final Map<String, List<Entry>> bySource = new LinkedHashMap<>();
        entries.forEach(entry -> bySource.computeIfAbsent(entry.source(), s -> new ArrayList<>()).add(entry));
        final List<String> out = new ArrayList<>();
        bySource.forEach((source, list) -> {
            if (EventChains.ANY.equalsIgnoreCase(source)) {
                return;
            }
            try {
                out.addAll(checkSource(session, source, list));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to check the Event chains of " + source, e);
                out.add("'" + source + "' couldn't be read to check its Event chains: " + e.getMessage());
            }
        });
        return out;
    }

    private List<String> checkSource(final ToolSession session, final String source, final List<Entry> entries) {
        final DocRef index = dataSourceAccess.find(source).stream()
                .filter(doc -> ElasticIndexDoc.TYPE.equals(doc.getType()))
                .findFirst()
                .orElse(null);
        if (index == null) {
            return List.of("Event chains name '" + source + "', which isn't an Elastic Index you can use.");
        }
        final ElasticKeyStreams streams = keyStreamsProvider.get();
        final ElasticKeyStreams.Target target = streams.target(List.of(index));
        if (target == null || target.docs().isEmpty() || target.docs().getFirst().timeField() == null) {
            return List.of("'" + source + "' has no time field, so its Event chains can't be checked.");
        }
        final Map<String, ElasticEql.Field> mapping = target.docs().getFirst().fields();
        final String user = timelineFields.find(session, index, InstructionRules.USER_FIELD, List.of(),
                        field -> mapping.containsKey(field) && JoinKeys.kind(mapping.get(field).nativeType()) != null)
                .map(TimelineFields.Found::field)
                .orElse(null);
        if (user == null) {
            return List.of("'" + source + "' has no field holding the user that can be found, so its Event chains "
                           + "can't be checked: give one in the AGENTS doc's ## Fields.");
        }
        if (!mapping.containsKey(EqlSequenceResults.STREAM_ID) || !mapping.containsKey(EqlSequenceResults.EVENT_ID)) {
            return List.of();
        }
        final String host = timelineFields.find(session, index, InstructionRules.HOST_FIELD, List.of(),
                mapping::containsKey).map(TimelineFields.Found::field).orElse(null);
        final List<Typed> typed = recent(session, streams, index, user, host, JoinKeys.kind(mapping.get(user)
                .nativeType()));
        final List<String> out = new ArrayList<>();
        for (final Entry entry : entries) {
            if (entry.isPair()) {
                final Candidate figures = PairInference.evaluate(typed, entry.start(), entry.end(), entry.by());
                if (figures == null) {
                    out.add("'" + entry.line().substring(2) + "': neither type is seen in the last "
                            + RECENT.toDays() + " days of " + source + ".");
                } else if (entry.by() != null && typed.stream().filter(event -> event.isType(entry.start()))
                        .noneMatch(event -> event.keys().containsKey(entry.by()))) {
                    out.add("'" + entry.line().substring(2) + "': its events hold no key '" + entry.by() + "'.");
                } else if (figures.keys() > 0 && figures.share() < PairInference.CLEAR_SHARE) {
                    out.add("'" + entry.line().substring(2) + "' no longer alternates: " + figures.percent()
                            + "% of " + figures.keys() + " key(s) in the last " + RECENT.toDays() + " days.");
                }
            } else if (typed.stream().noneMatch(event -> event.isType(entry.type()))) {
                out.add("'" + entry.line().substring(2) + "': the type isn't seen in the last " + RECENT.toDays()
                        + " days of " + source + ".");
            }
        }
        return out;
    }

    // The index's recent events of every user, classified from their XML, each user and host a lane of its own.
    private List<Typed> recent(final ToolSession session,
                               final ElasticKeyStreams streams,
                               final DocRef index,
                               final String user,
                               final String host,
                               final JoinKeys.Kind kind) {
        final Instant now = Instant.now();
        final TimeRange range = new TimeRange(null, now.minus(RECENT).toString(), now.toString());
        final Side side = new Side(index, List.of(new KeyField(user, List.of())), null, range, KeyRange.ALL,
                List.of());
        final List<String> fields = host == null
                ? List.of(EqlSequenceResults.STREAM_ID, EqlSequenceResults.EVENT_ID)
                : List.of(EqlSequenceResults.STREAM_ID, EqlSequenceResults.EVENT_ID, host);
        final JoinBudget budget = new JoinBudget(MAX_EVENTS, Long.MAX_VALUE, now.plus(TIME_LIMIT), null);
        final List<String[]> rows = new ArrayList<>();
        final Map<Long, List<Long>> ids = new TreeMap<>();
        try (final KeyGroupSource scan = KeyGroupSource.scan(streams, List.of(side), fields, TimelineReader.PAGE_SIZE,
                JoinKeys.comparator(List.of(kind)), budget)) {
            KeyGroup group;
            while (rows.size() < MAX_EVENTS && (group = scan.next()) != null) {
                for (final Row row : group.rows()) {
                    final Long streamId = number(first(row, EqlSequenceResults.STREAM_ID));
                    final Long eventId = number(first(row, EqlSequenceResults.EVENT_ID));
                    if (streamId != null && eventId != null) {
                        rows.add(new String[]{String.valueOf(group.key()), first(row, host), streamId + ":" + eventId,
                                String.valueOf(Objects.requireNonNullElse(row.time(), 0L))});
                        ids.computeIfAbsent(streamId, k -> new ArrayList<>()).add(eventId);
                    }
                }
            }
        }
        final Map<String, String> xml = eventXml.read(session, ids, 1_000_000, () -> false, count -> {
        }).xml();
        final List<Typed> typed = new ArrayList<>();
        for (final String[] row : rows) {
            final Classified c = EventXmlClassifier.classify(xml.get(row[2])).orElse(null);
            if (c != null && c.type() != null) {
                typed.add(new Typed(row[2], 0, Long.parseLong(row[3]), row[0] + "/" + row[1], c.type(),
                        c.schemaType(), c.keys()));
            }
        }
        return typed;
    }

    private static String first(final Row row, final String field) {
        if (field == null) {
            return null;
        }
        final List<Object> values = row.fields().get(field);
        if (values == null || values.isEmpty() || values.getFirst() == null) {
            return null;
        }
        final Object value = values.getFirst();
        return value instanceof final Double number && number == Math.rint(number)
                ? String.valueOf(number.longValue())
                : String.valueOf(value);
    }

    private static Long number(final String value) {
        try {
            return value == null
                    ? null
                    : Long.parseLong(value.strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
