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
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.tools.sequence.EqlSequenceResults;
import stroom.ai.impl.tools.sequence.JoinBudget;
import stroom.ai.impl.tools.sequence.JoinKeys;
import stroom.ai.impl.tools.sequence.KeyGroupSource;
import stroom.ai.impl.tools.sequence.KeyGroupSource.KeyGroup;
import stroom.ai.impl.tools.sequence.KeyStreamSequence;
import stroom.ai.impl.tools.sequence.StreamFilters;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// Reads one user's (or entity's) events of a timeline's sources, with the streaming join's machinery (part 1): each
/// role of each source is a [Side] of its Elastic Index, its conditions as a Stroom expression ([StreamFilters]),
/// restricted to the one entity by a terms condition on its user field (transformed as the call says, so
/// `CORP\jsmith` and `jsmith` are one), read in time order by a point-in-time scan ([ElasticKeyStreams#openScan],
/// through [KeyGroupSource]: one key, its events in time order), as the user, within a [JoinBudget] (the events
/// cap, the time limit and the user's stop). Nothing is read in Stroom's own search: the events come straight from
/// Elasticsearch.
final class TimelineReader {

    /// The events a page reads.
    static final int PAGE_SIZE = ElasticKeyStreams.MAX_PAGE;

    private TimelineReader() {
        // Static only.
    }

    /// Reads the events.
    ///
    /// @param streams   The key streams.
    /// @param sources   The sources, each with its fields, roles and the values its user (or person) field is
    ///                  filtered on.
    /// @param timeRange The period.
    /// @param budget    The events cap, the time limit and the stop.
    /// @return The events, with how many each source gave (and of each account) and whether reading ended early.
    /// @throws WorkspaceException saying why a source couldn't be read.
    static Read read(final ElasticKeyStreams streams,
                     final List<Source> sources,
                     final TimeRange timeRange,
                     final JoinBudget budget) {
        final List<Event> events = new ArrayList<>();
        final List<Long> counts = new ArrayList<>();
        final List<Map<String, Long>> accounts = new ArrayList<>();
        final Map<String, Map<String, String>> extras = new LinkedHashMap<>();
        long cut = 0;
        for (int s = 0; s < sources.size(); s++) {
            final Source source = sources.get(s);
            final List<Object> terms = source.terms();
            final Map<String, Long> byAccount = new LinkedHashMap<>();
            long count = 0;
            for (final RoleRead role : source.roles()) {
                final Side side = new Side(source.doc(), List.of(new KeyField(source.userField(),
                        source.transforms())), role.filter(), timeRange, KeyRange.ALL, List.of(terms));
                try (final KeyGroupSource scan = KeyGroupSource.scan(streams, List.of(side), source.fetched(),
                        PAGE_SIZE, JoinKeys.comparator(List.of(source.kind())), budget)) {
                    // A group a value filtered on (several where several logon ids were given), in key order.
                    KeyGroup group = refused(scan::next);
                    while (group != null) {
                        for (final Row row : group.rows()) {
                            final Event event = event(s, source, role, row);
                            events.add(event);
                            if (!source.extraFields().isEmpty()) {
                                final Map<String, String> values = new LinkedHashMap<>();
                                for (final String field : source.extraFields()) {
                                    NullSafe.consume(first(row.fields(), field), value -> values.put(field, value));
                                }
                                extras.put(event.id(), values);
                            }
                        }
                        count += group.rows().size();
                        byAccount.merge(account(group), (long) group.rows().size(), Long::sum);
                        if (budget.check()) {
                            break;
                        }
                        group = refused(scan::next);
                    }
                    cut += scan.cutKeys();
                }
                if (budget.check()) {
                    break;
                }
            }
            counts.add(count);
            accounts.add(byAccount);
            if (budget.stoppedByUser()) {
                throw new TaskTerminatedException();
            }
            if (budget.reached() != null) {
                break;
            }
        }
        return new Read(events, counts, budget.reached(), cut > 0, extras, accounts);
    }

    /// The first events of one source for the user, with the fields asked for: to tell which of an index's fields
    /// holds what (e.g. the EventDetail element), by their values.
    ///
    /// @param streams   The key streams.
    /// @param source    The source, with the values its user (or person) field is filtered on.
    /// @param timeRange The period.
    /// @param fields    The fields to fetch.
    /// @param count     The most events.
    /// @param deadline  When to stop.
    /// @param stopped   True once the user has stopped the call.
    /// @return Each event's values by field.
    static List<Map<String, String>> sample(final ElasticKeyStreams streams,
                                            final Source source,
                                            final TimeRange timeRange,
                                            final List<String> fields,
                                            final int count,
                                            final Instant deadline,
                                            final BooleanSupplier stopped) {
        return sample(streams, source, timeRange, fields, count, deadline, stopped, null);
    }

    /// The first events of one source for the user that also meet a condition, with the fields asked for.
    ///
    /// @param streams   The key streams.
    /// @param source    The source, with the values its user (or person) field is filtered on.
    /// @param timeRange The period.
    /// @param fields    The fields to fetch.
    /// @param count     The most events.
    /// @param deadline  When to stop.
    /// @param stopped   True once the user has stopped the call.
    /// @param filter    The condition, e.g. the user field not holding the user as given; null for none.
    /// @return Each event's values by field.
    static List<Map<String, String>> sample(final ElasticKeyStreams streams,
                                            final Source source,
                                            final TimeRange timeRange,
                                            final List<String> fields,
                                            final int count,
                                            final Instant deadline,
                                            final BooleanSupplier stopped,
                                            final ExpressionOperator filter) {
        final Side side = new Side(source.doc(), List.of(new KeyField(source.userField(), source.transforms())),
                filter, timeRange, KeyRange.ALL, List.of(source.terms()));
        final JoinBudget budget = new JoinBudget(count, Long.MAX_VALUE, deadline, stopped);
        final List<Map<String, String>> out = new ArrayList<>();
        try (final KeyGroupSource scan = KeyGroupSource.scan(streams, List.of(side), fields, PAGE_SIZE,
                JoinKeys.comparator(List.of(source.kind())), budget)) {
            KeyGroup group = refused(scan::next);
            while (group != null && out.size() < count) {
                for (final Row row : group.rows()) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    for (final String field : fields) {
                        NullSafe.consume(first(row.fields(), field), value -> values.put(field, value));
                    }
                    out.add(values);
                    if (out.size() >= count) {
                        break;
                    }
                }
                group = out.size() < count
                        ? refused(scan::next)
                        : null;
            }
        }
        return out;
    }

    // The account a group is of: the value its key holds (transformed, where the source transforms it).
    private static String account(final KeyGroup group) {
        return group.key().isEmpty() || group.key().getFirst() == null
                ? ""
                : String.valueOf(group.key().getFirst());
    }

    private static Event event(final int s, final Source source, final RoleRead role, final Row row) {
        final Map<String, List<Object>> fields = row.fields();
        return new Event(Objects.requireNonNullElse(row.time(), 0L), s, row.index() + "/" + row.id() + "/"
                                                                         + role.tag(), role.role(), role.kind(),
                first(fields, source.hostField()), first(fields, source.processField()),
                first(fields, source.titleField()), first(fields, source.windowField()),
                first(fields, source.objectField()), first(fields, source.textField()),
                first(fields, EqlSequenceResults.STREAM_ID), first(fields, EqlSequenceResults.EVENT_ID));
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


    /// One source of a timeline, as it is read.
    ///
    /// @param name         Its name in the result.
    /// @param doc          Its Elastic Index.
    /// @param userField    The field holding the user (or entity), as a key.
    /// @param kind         How the user field's values compare.
    /// @param transforms   What is done to the user's value before it is compared.
    /// @param hostField    The field holding the host, or null.
    /// @param processField The field holding the application, or null.
    /// @param titleField   The field holding the window title, or null.
    /// @param windowField  The field holding the window's id, or null.
    /// @param objectField  The field holding what an event touched (a file, a URL, a subject), or null.
    /// @param textField    The field holding free text (a message, an AI chat prompt), or null.
    /// @param hasStreamIds True if the index has StreamId and EventId fields.
    /// @param roles        Its roles, each with its conditions.
    /// @param automatic    True if its events are classified (from their Events XML and so on), not by roles.
    /// @param extraFields  More fields fetched, for classifying its events (e.g. the element, the event code).
    /// @param values       The values its user field is filtered on: a person id, or one or more logon ids.
    /// @param by           Whether `userField` holds the person id or the logon id.
    record Source(String name,
                  DocRef doc,
                  String userField,
                  JoinKeys.Kind kind,
                  List<Transform> transforms,
                  String hostField,
                  String processField,
                  String titleField,
                  String windowField,
                  String objectField,
                  String textField,
                  boolean hasStreamIds,
                  List<RoleRead> roles,
                  boolean automatic,
                  List<String> extraFields,
                  List<String> values,
                  PersonScope.By by) {

        /// Copies the lists.
        Source {
            transforms = List.copyOf(transforms);
            roles = List.copyOf(roles);
            extraFields = List.copyOf(extraFields);
            values = List.copyOf(values);
        }

        /// @param newTransforms What is done to the user's value before it is compared.
        /// @return It with them.
        Source withTransforms(final List<Transform> newTransforms) {
            return new Source(name, doc, userField, kind, newTransforms, hostField, processField, titleField,
                    windowField, objectField, textField, hasStreamIds, roles, automatic, extraFields, values, by);
        }

        /// @return The fields a scan fetches.
        List<String> fetched() {
            final Set<String> fields = new LinkedHashSet<>();
            for (final String field : new String[]{hostField, processField, titleField, windowField, objectField,
                    textField}) {
                if (field != null) {
                    fields.add(field);
                }
            }
            if (hasStreamIds) {
                fields.add(EqlSequenceResults.STREAM_ID);
                fields.add(EqlSequenceResults.EVENT_ID);
            }
            fields.addAll(extraFields);
            return List.copyOf(fields);
        }

        /// @return The values filtered on as the terms condition takes them: transformed text, or numbers for a
        /// number field.
        List<Object> terms() {
            final List<Object> terms = new ArrayList<>();
            for (final String value : values) {
                final Object term = term(value);
                if (!terms.contains(term)) {
                    terms.add(term);
                }
            }
            return terms;
        }

        private Object term(final String entity) {
            if (kind == JoinKeys.Kind.NUMBER) {
                try {
                    return Long.parseLong(entity.strip());
                } catch (final NumberFormatException e) {
                    throw new WorkspaceException(name + ": its user field " + userField + " holds numbers, and '"
                                                 + entity + "' isn't one.");
                }
            }
            return Transform.applyAll(transforms, entity.strip());
        }

        /// @return True if it has a role that opens windows.
        boolean opens() {
            return roles.stream().anyMatch(role -> role.role() == Role.OPEN);
        }
    }


    // --------------------------------------------------------------------------------


    /// One role of a source.
    ///
    /// @param role   The role.
    /// @param kind   An action's kind, else null.
    /// @param filter Its conditions, or null for every event of the source.
    record RoleRead(Role role, String kind, ExpressionOperator filter) {

        /// @return What tells its events apart from the same document's events of another role.
        String tag() {
            return kind == null
                    ? role.argName()
                    : role.argName() + ":" + kind;
        }
    }


    // --------------------------------------------------------------------------------


    /// What was read.
    ///
    /// @param events  The events of every source.
    /// @param counts  How many events each source gave.
    /// @param reached Why reading ended early, or null if it didn't.
    /// @param cut     True if a role of a source had more events than one scan keeps.
    /// @param extras   The more fields' values of the automatic sources' events, by event id.
    /// @param accounts How many events each source gave of each value filtered on (e.g. each logon id).
    record Read(List<Event> events,
                List<Long> counts,
                JoinBudget.Limit reached,
                boolean cut,
                Map<String, Map<String, String>> extras,
                List<Map<String, Long>> accounts) {

    }
}
