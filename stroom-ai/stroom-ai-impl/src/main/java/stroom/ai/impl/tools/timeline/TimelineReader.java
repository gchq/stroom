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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// Reads one user's (or entity's) events of a timeline's sources, with the streaming join's machinery (part 1): each
/// source is read once, a [Side] of its Elastic Index restricted to the one entity by a terms condition on its user
/// field (transformed as the call says, so `CORP\jsmith` and `jsmith` are one), read in time order by a point-in-time
/// scan ([ElasticKeyStreams#openScan], through [KeyGroupSource]: one key, its events in time order), as the user,
/// within a [JoinBudget] (the events cap, the time limit and the user's stop); each event read takes every role whose
/// conditions (a Stroom expression, [StreamFilters]) it holds, in memory ([RoleConditions]), so a source of several
/// roles is one scan, not one per role (decided with the user 2026-11-25). Nothing is read in Stroom's own search: the
/// events come straight from Elasticsearch.
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
        int scans = 0;
        for (int s = 0; s < sources.size(); s++) {
            final Source source = sources.get(s);
            final List<Object> terms = source.terms();
            final Map<String, Long> byAccount = new LinkedHashMap<>();
            long count = 0;
            // One scan per source (and per user field, where the pipelines feeding it put the user in different
            // fields), filtered by the user and the period only; each event read takes every role whose conditions
            // it holds, in memory.
            final Set<String> seen = new HashSet<>();
            for (final String userField : source.userFields()) {
                final Side side = new Side(source.doc(), List.of(new KeyField(userField, source.transforms())), null,
                        timeRange, KeyRange.ALL, List.of(terms));
                try (final KeyGroupSource scan = KeyGroupSource.scan(streams, List.of(side), source.read(),
                        PAGE_SIZE, JoinKeys.comparator(List.of(source.kind())), budget)) {
                    // A group a value filtered on (several where several logon ids were given), in key order.
                    KeyGroup group = refused(scan::next);
                    while (group != null) {
                        long taken = 0;
                        for (final Row row : group.rows()) {
                            // A document holding the user in two of the fields is read once.
                            if (!seen.add(row.index() + "/" + row.id())) {
                                continue;
                            }
                            for (final RoleRead role : source.roles()) {
                                if (!RoleConditions.holds(role.filter(), row.fields())) {
                                    continue;
                                }
                                final Event event = event(s, source, role, row);
                                events.add(event);
                                taken++;
                                if (!source.extraFields().isEmpty()) {
                                    final Map<String, String> values = new LinkedHashMap<>();
                                    for (final String field : source.extraFields()) {
                                        NullSafe.consume(first(row.fields(), field),
                                                value -> values.put(field, value));
                                    }
                                    extras.put(event.id(), values);
                                }
                            }
                        }
                        count += taken;
                        byAccount.merge(account(group), taken, Long::sum);
                        if (budget.check()) {
                            break;
                        }
                        group = refused(scan::next);
                    }
                    cut += scan.cutKeys();
                    scans++;
                }
                if (budget.reached() != null) {
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
        return new Read(events, counts, budget.reached(), cut > 0, extras, accounts, scans);
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
        final Side side = new Side(source.doc(), List.of(new KeyField(source.userField(), source.transforms())),
                null, timeRange, KeyRange.ALL, List.of(source.terms()));
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
    /// @param alsoUserFields Other fields holding the user, where the pipelines feeding the index differ: each read
    ///                     too (a scan each), a document read once.
    /// @param varied       The things whose field the pipelines feeding the index differ on (by `## Fields` key,
    ///                     e.g. `host`), each with its field per pipeline: every one is fetched (in `extraFields`) and
    ///                     each event takes its own pipeline's ([StreamFields]).
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
                  PersonScope.By by,
                  List<String> alsoUserFields,
                  Map<String, Varied> varied) {

        /// Copies the collections.
        Source {
            transforms = List.copyOf(transforms);
            roles = List.copyOf(roles);
            extraFields = List.copyOf(extraFields);
            values = List.copyOf(values);
            alsoUserFields = List.copyOf(NullSafe.list(alsoUserFields));
            varied = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(varied)));
        }

        /// A source whose fields every feeding pipeline agrees on.
        ///
        /// @param name         Its name in the result.
        /// @param doc          Its Elastic Index.
        /// @param userField    The field holding the user.
        /// @param kind         How the user field's values compare.
        /// @param transforms   What is done to the user's value before it is compared.
        /// @param hostField    The host's field, or null.
        /// @param processField The application's field, or null.
        /// @param titleField   The window title's field, or null.
        /// @param windowField  The window id's field, or null.
        /// @param objectField  The object's field, or null.
        /// @param textField    The free text's field, or null.
        /// @param hasStreamIds True if the index has StreamId and EventId fields.
        /// @param roles        Its roles.
        /// @param automatic    True if its events are classified.
        /// @param extraFields  More fields fetched.
        /// @param values       The values its user field is filtered on.
        /// @param by           Whether `userField` holds the person id or the logon id.
        Source(final String name,
               final DocRef doc,
               final String userField,
               final JoinKeys.Kind kind,
               final List<Transform> transforms,
               final String hostField,
               final String processField,
               final String titleField,
               final String windowField,
               final String objectField,
               final String textField,
               final boolean hasStreamIds,
               final List<RoleRead> roles,
               final boolean automatic,
               final List<String> extraFields,
               final List<String> values,
               final PersonScope.By by) {
            this(name, doc, userField, kind, transforms, hostField, processField, titleField, windowField, objectField,
                    textField, hasStreamIds, roles, automatic, extraFields, values, by, List.of(), Map.of());
        }

        /// @return Every field holding the user, the first `userField`.
        List<String> userFields() {
            final List<String> out = new ArrayList<>();
            out.add(userField);
            alsoUserFields.stream().filter(field -> !out.contains(field)).forEach(out::add);
            return out;
        }

        /// @param newTransforms What is done to the user's value before it is compared.
        /// @return It with them.
        Source withTransforms(final List<Transform> newTransforms) {
            return new Source(name, doc, userField, kind, newTransforms, hostField, processField, titleField,
                    windowField, objectField, textField, hasStreamIds, roles, automatic, extraFields, values, by,
                    alsoUserFields, varied);
        }

        /// @return The fields a scan fetches for its events (without those its roles' conditions read).
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

        /// @return The fields its one scan fetches: the events' own ([#fetched]) and those its roles' conditions
        /// read, so each event's roles are told in memory.
        List<String> read() {
            final Set<String> fields = new LinkedHashSet<>(fetched());
            roles.forEach(role -> fields.addAll(RoleConditions.fields(role.filter())));
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


    /// A thing the pipelines feeding an index put in different fields.
    ///
    /// @param primary    The field read where an event's pipeline isn't known: the first pipeline's.
    /// @param byPipeline Each pipeline's field, by the pipeline's name, in the plans' order.
    record Varied(String primary, Map<String, String> byPipeline) {

        /// Copies the map, in order.
        Varied {
            byPipeline = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(byPipeline)));
        }

        /// @return Every field it may be in, the primary first.
        List<String> fields() {
            final List<String> out = new ArrayList<>();
            NullSafe.consume(primary, out::add);
            byPipeline.values().stream().filter(field -> !out.contains(field)).forEach(out::add);
            return out;
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
    /// @param cut     True if a source had more events than one scan keeps.
    /// @param extras   The more fields' values of the automatic sources' events, by event id.
    /// @param accounts How many events each source gave of each value filtered on (e.g. each logon id).
    /// @param scans    The scans opened: one per source read.
    record Read(List<Event> events,
                List<Long> counts,
                JoinBudget.Limit reached,
                boolean cut,
                Map<String, Map<String, String>> extras,
                List<Map<String, Long>> accounts,
                int scans) {

        /// @param newEvents The events, e.g. each with its own pipeline's fields ([StreamFields]).
        /// @param newExtras Their more fields, by event id.
        /// @return It with those events.
        Read with(final List<Event> newEvents, final Map<String, Map<String, String>> newExtras) {
            return new Read(newEvents, counts, reached, cut, newExtras, accounts, scans);
        }

    }
}
