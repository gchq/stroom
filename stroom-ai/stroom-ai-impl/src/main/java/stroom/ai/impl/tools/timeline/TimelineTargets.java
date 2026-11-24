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

import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.KeyField;
import stroom.ai.api.ElasticKeyStreams.KeyRange;
import stroom.ai.api.ElasticKeyStreams.Refusal;
import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.api.ElasticKeyStreams.Side;
import stroom.ai.impl.tools.sequence.EqlSequenceResults;
import stroom.ai.impl.tools.sequence.JoinBudget;
import stroom.ai.impl.tools.sequence.JoinKeys;
import stroom.ai.impl.tools.sequence.KeyGroupSource;
import stroom.ai.impl.tools.sequence.KeyGroupSource.KeyGroup;
import stroom.ai.impl.tools.sequence.KeyStreamSequence;
import stroom.ai.impl.tools.timeline.PersonScope.Target;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/// What was done to the person in a timeline's sources (`activity_timeline` with `includeTargets`): one more read
/// of each source, its events whose target field (the person or account an action was done to, [PersonScope#target])
/// holds them, the person's own acts on themselves left out, each labelled by its type (the Action, else the
/// element, else the source's event code) and who did it (the actor's logon id, else their person id, else "no
/// account", as a failed logon against the account has none). These events form a lane of their own per source and
/// are never put in the person's activity: not in the sessions, spans, activity periods, pairs or characterisation.
final class TimelineTargets {

    /// The lane (and source) prefix of what was done to the person, in the tables.
    static final String LANE = "done to them";
    /// The role column's value for the person's own events.
    static final String ACTOR = "actor";
    /// The role column's value for events done to the person.
    static final String TARGET = "target";
    /// Who did an event no account did (e.g. a failed logon against the account).
    static final String NO_ACCOUNT = "no account";
    /// What a type no field gives is called.
    static final String UNKNOWN_TYPE = "an event";

    /// What a result says of what was done to the person.
    static final String KEPT_APART = "Done to them: events whose target is the person (access or a group granted to "
                                     + "them, a password reset, MFA changed, a failed logon against their account), "
                                     + "their own acts on themselves left out. Kept apart: never in their activity, "
                                     + "sessions, spans, pairs or activities. Indicators of something done to the "
                                     + "person or their account, not of anything they did.";
    /// The most events done to the person listed in a result; the rest are in the tables.
    static final int LISTED = 20;

    private TimelineTargets() {
        // Static only.
    }

    /// @param asked What was done to the person, as read.
    /// @param zone  The user's zone, for the times listed.
    /// @return It as the result's JSON gives it.
    static Map<String, Object> structured(final Asked asked, final ZoneId zone) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("events", asked.read().events().size());
        map.put("note", KEPT_APART);
        final List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < asked.sources().size(); i++) {
            final TargetSource source = asked.sources().get(i);
            final List<Done> own = asked.read().events().stream()
                    .filter(event -> event.source() == source.source())
                    .toList();
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("source", source.name());
            one.put("by", source.target().describe());
            one.put("events", i < asked.read().counts().size()
                    ? asked.read().counts().get(i)
                    : 0L);
            one.put("kinds", kinds(own));
            one.put("done_by", actors(own));
            sources.add(one);
        }
        map.put("sources", sources);
        if (!asked.leftOut().isEmpty()) {
            map.put("left_out", asked.leftOut().entrySet().stream()
                    .map(entry -> Map.<String, Object>of("source", entry.getKey(), "why", entry.getValue()))
                    .toList());
        }
        if (asked.read().reached() != null || asked.read().cut()) {
            map.put("ended_early", "Not every event done to them was read (the events cap or the time limit): "
                                   + "narrow the period.");
        }
        map.put("listed", asked.read().events().stream().limit(LISTED).map(event -> {
            final Map<String, Object> listed = new LinkedHashMap<>();
            listed.put("time", local(event.time(), zone));
            listed.put("source", event.name());
            listed.put("type", event.type());
            listed.put("by", event.actor());
            NullSafe.consume(event.host(), host -> listed.put("host", host));
            NullSafe.consume(event.object(), object -> listed.put("object", object));
            NullSafe.consume(event.streamId(), id -> listed.put("StreamId", id));
            NullSafe.consume(event.eventId(), id -> listed.put("EventId", id));
            return listed;
        }).toList());
        return map;
    }

    /// @param asked What was done to the person, as read.
    /// @param zone  The user's zone, for the times listed.
    /// @return It as the chat's Markdown says it, a heading line first.
    static String markdown(final Asked asked, final ZoneId zone) {
        final StringBuilder sb = new StringBuilder("\nDone to them: ").append(asked.read().events().size())
                .append(" event(s). ").append(KEPT_APART).append('\n');
        for (final TargetSource source : asked.sources()) {
            final List<Done> own = asked.read().events().stream()
                    .filter(event -> event.source() == source.source())
                    .toList();
            final List<String> kinds = new ArrayList<>();
            kinds(own).forEach((kind, count) -> kinds.add(kind + " " + count));
            sb.append("- ").append(source.name()).append(": ").append(source.target().describe()).append(": ")
                    .append(own.size()).append(own.isEmpty()
                            ? ""
                            : " (" + String.join(", ", kinds) + "; by " + String.join(", ", actors(own)) + ")")
                    .append('\n');
        }
        asked.leftOut().forEach((source, why) -> sb.append("- ").append(source).append(": not read (").append(why)
                .append(")\n"));
        asked.read().events().stream().limit(LISTED).forEach(event -> sb.append("  - ").append(local(event.time(),
                zone)).append(" ").append(event.label()).append(" (").append(event.name()).append(")\n"));
        if (asked.read().events().size() > LISTED) {
            sb.append("  - ... the rest in spans.csv and actions.csv (role ").append(TARGET).append(")\n");
        }
        if (asked.read().reached() != null || asked.read().cut()) {
            sb.append("Not every event done to them was read (the events cap or the time limit): narrow the "
                      + "period.\n");
        }
        return sb.toString();
    }

    private static String local(final long ms, final ZoneId zone) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString().replace('T', ' ');
    }

    /// Reads what was done to the person in each source that has target fields.
    ///
    /// @param streams   The key streams.
    /// @param sources   Each source's target read.
    /// @param timeRange The period.
    /// @param budget    The events cap, the time limit and the stop.
    /// @return The events, in time order, with how many each source gave.
    /// @throws WorkspaceException saying why a source couldn't be read.
    static Read read(final ElasticKeyStreams streams,
                     final List<TargetSource> sources,
                     final TimeRange timeRange,
                     final JoinBudget budget) {
        final List<Done> events = new ArrayList<>();
        final List<Long> counts = new ArrayList<>();
        long cut = 0;
        for (final TargetSource source : sources) {
            long count = 0;
            final Target target = source.target();
            if (source.terms().isEmpty()) {
                // Nothing the field can hold (e.g. a person id that isn't a number, in a number field): none read,
                // never every event.
                counts.add(0L);
                continue;
            }
            final Side side = new Side(source.doc(), List.of(new KeyField(target.field().field(), List.of())),
                    notTheirOwn(target), timeRange, KeyRange.ALL, List.of(source.terms()));
            try (final KeyGroupSource scan = KeyGroupSource.scan(streams, List.of(side), source.fetched(),
                    TimelineReader.PAGE_SIZE, JoinKeys.comparator(List.of(source.kind())), budget)) {
                KeyGroup group = refused(scan::next);
                while (group != null) {
                    for (final Row row : group.rows()) {
                        events.add(done(source, row));
                    }
                    count += group.rows().size();
                    if (budget.check()) {
                        break;
                    }
                    group = refused(scan::next);
                }
                cut += scan.cutKeys();
            }
            counts.add(count);
            if (budget.stoppedByUser()) {
                throw new TaskTerminatedException();
            }
            if (budget.reached() != null) {
                break;
            }
        }
        events.sort(Comparator.comparingLong(Done::time).thenComparing(Done::source));
        return new Read(events, counts, budget.reached(), cut > 0);
    }

    /// The condition leaving out the person's own acts on themselves (their own logon, a password they changed): the
    /// actor's field holding none of their values. An event with no actor (a failed logon) is kept.
    ///
    /// @param target The target scope.
    /// @return The condition, or null where the index has no actor field.
    static ExpressionOperator notTheirOwn(final Target target) {
        if (target.actorField() == null || target.actorValues().isEmpty()) {
            return null;
        }
        return ExpressionOperator.builder().op(Op.NOT)
                .addTerm(ExpressionTerm.builder().field(target.actorField()).condition(Condition.IN)
                        .value(String.join(",", target.actorValues())).build())
                .build();
    }

    /// @param events What was done to the person.
    /// @return How many of each type, the most first (then by name).
    static Map<String, Long> kinds(final List<Done> events) {
        final Map<String, Long> counts = new LinkedHashMap<>();
        events.forEach(event -> counts.merge(event.type(), 1L, Long::sum));
        final Map<String, Long> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    /// @param events What was done to the person.
    /// @return Who did it, each once, in the order first seen.
    static List<String> actors(final List<Done> events) {
        final Set<String> actors = new LinkedHashSet<>();
        events.forEach(event -> actors.add(event.actor()));
        return List.copyOf(actors);
    }

    private static Done done(final TargetSource source, final Row row) {
        final Map<String, List<Object>> fields = row.fields();
        final String type = firstNonBlank(first(fields, source.actionField()), first(fields, source.elementField()),
                first(fields, source.eventTypeField()));
        final String actor = firstNonBlank(first(fields, source.actorUserField()), first(fields,
                source.actorPersonField()));
        return new Done(Objects.requireNonNullElse(row.time(), 0L), source.source(), source.name(),
                Objects.requireNonNullElse(type, UNKNOWN_TYPE), Objects.requireNonNullElse(actor, NO_ACCOUNT),
                first(fields, source.hostField()), first(fields, source.objectField()),
                first(fields, EqlSequenceResults.STREAM_ID), first(fields, EqlSequenceResults.EVENT_ID));
    }

    private static String firstNonBlank(final String... values) {
        for (final String value : values) {
            if (NullSafe.isNonBlankString(value)) {
                return value;
            }
        }
        return null;
    }

    private static String first(final Map<String, List<Object>> fields, final String field) {
        if (field == null) {
            return null;
        }
        final List<Object> values = fields.get(field);
        if (values == null || values.isEmpty() || values.getFirst() == null) {
            return null;
        }
        final Object value = values.getFirst();
        final String text = value instanceof final Double number && number == Math.rint(number)
                ? String.valueOf(number.longValue())
                : String.valueOf(value);
        return text.isBlank()
                ? null
                : text;
    }

    private static <T> T refused(final Supplier<T> read) {
        try {
            return read.get();
        } catch (final Refusal refusal) {
            if (refusal.reason() == ElasticKeyStreams.Reason.STOPPED) {
                throw new TaskTerminatedException();
            }
            throw new WorkspaceException(KeyStreamSequence.refusal(refusal));
        }
    }


    // --------------------------------------------------------------------------------


    /// One source's read of what was done to the person.
    ///
    /// @param source           The source's place among the timeline's sources.
    /// @param name             Its name in the result.
    /// @param doc              Its Elastic Index.
    /// @param target           Its target field, the values and the actor's own acts left out.
    /// @param kind             How the target field's values compare.
    /// @param actionField      The field holding the Action, or null.
    /// @param elementField     The field holding the EventDetail element, or null.
    /// @param eventTypeField   The field holding the source's own event code, or null.
    /// @param actorUserField   The field holding who acted (their logon id), or null.
    /// @param actorPersonField The field holding who acted (their person id), or null.
    /// @param hostField        The field holding the host, or null.
    /// @param objectField      The field holding what was touched, or null.
    /// @param hasStreamIds     True if the index has StreamId and EventId fields.
    record TargetSource(int source,
                        String name,
                        DocRef doc,
                        Target target,
                        JoinKeys.Kind kind,
                        String actionField,
                        String elementField,
                        String eventTypeField,
                        String actorUserField,
                        String actorPersonField,
                        String hostField,
                        String objectField,
                        boolean hasStreamIds) {

        /// @return The target values as the terms condition takes them: text, or numbers for a number field
        /// (a value that isn't one left out).
        List<Object> terms() {
            final List<Object> terms = new ArrayList<>();
            for (final String value : target.values()) {
                if (kind != JoinKeys.Kind.NUMBER) {
                    terms.add(value);
                } else {
                    try {
                        terms.add(Long.parseLong(value.strip()));
                    } catch (final NumberFormatException e) {
                        // Not a number: it can't be held by a number field.
                    }
                }
            }
            return terms;
        }

        /// @return The fields a scan fetches.
        List<String> fetched() {
            final Set<String> fields = new LinkedHashSet<>();
            for (final String field : new String[]{actionField, elementField, eventTypeField, actorUserField,
                    actorPersonField, hostField, objectField}) {
                if (field != null) {
                    fields.add(field);
                }
            }
            if (hasStreamIds) {
                fields.add(EqlSequenceResults.STREAM_ID);
                fields.add(EqlSequenceResults.EVENT_ID);
            }
            return List.copyOf(fields);
        }
    }


    // --------------------------------------------------------------------------------


    /// An event done to the person.
    ///
    /// @param time     When.
    /// @param source   The source's place.
    /// @param name     The source's name.
    /// @param type     What it was: the Action, else the element, else the source's event code.
    /// @param actor    Who did it: their logon id, else person id, else [#NO_ACCOUNT].
    /// @param host     The host, or null.
    /// @param object   What was touched, or null.
    /// @param streamId Its StreamId, or null.
    /// @param eventId  Its EventId, or null.
    record Done(long time,
                int source,
                String name,
                String type,
                String actor,
                String host,
                String object,
                String streamId,
                String eventId) {

        /// @return How its span is labelled: its type and who did it.
        String label() {
            return type + " by " + actor;
        }

        /// @return Its lane in the tables, one per source.
        String lane() {
            return LANE + ": " + name;
        }
    }


    // --------------------------------------------------------------------------------


    /// What was done to the person, asked for by the call.
    ///
    /// @param sources The sources read, each with its target field.
    /// @param read    What was read.
    /// @param leftOut The sources not read, by name, with why (e.g. no target field).
    record Asked(List<TargetSource> sources, Read read, Map<String, String> leftOut) {

        /// Copies the collections.
        Asked {
            sources = List.copyOf(sources);
            leftOut = new LinkedHashMap<>(leftOut);
        }
    }


    // --------------------------------------------------------------------------------


    /// What was read.
    ///
    /// @param events  The events done to the person, in time order.
    /// @param counts  How many each source read gave.
    /// @param reached Why reading ended early, or null if it didn't.
    /// @param cut     True if a source had more events than one scan keeps.
    record Read(List<Done> events, List<Long> counts, JoinBudget.Limit reached, boolean cut) {

        /// Copies the lists.
        Read {
            events = List.copyOf(events);
            counts = List.copyOf(counts);
        }

        /// @return Nothing read (the call didn't ask).
        static Read none() {
            return new Read(List.of(), List.of(), null, false);
        }
    }
}
