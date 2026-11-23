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

package stroom.ai.impl.tools;

import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.KeyField;
import stroom.ai.api.ElasticKeyStreams.KeyRange;
import stroom.ai.api.ElasticKeyStreams.Refusal;
import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.api.ElasticKeyStreams.Side;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.tools.KeyGroupSource.KeyGroup;
import stroom.ai.impl.tools.TimelineSessions.Event;
import stroom.ai.impl.tools.TimelineSessions.Role;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;
import stroom.task.api.TaskTerminatedException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    /// @param sources   The sources, each with its fields and roles.
    /// @param entity    The user (or entity) as the call gives it.
    /// @param timeRange The period.
    /// @param budget    The events cap, the time limit and the stop.
    /// @return The events, with how many each source gave and whether reading ended early.
    /// @throws WorkspaceException saying why a source couldn't be read.
    static Read read(final ElasticKeyStreams streams,
                     final List<Source> sources,
                     final String entity,
                     final TimeRange timeRange,
                     final JoinBudget budget) {
        final List<Event> events = new ArrayList<>();
        final List<Long> counts = new ArrayList<>();
        long cut = 0;
        for (int s = 0; s < sources.size(); s++) {
            final Source source = sources.get(s);
            final Object term = source.term(entity);
            long count = 0;
            for (final RoleRead role : source.roles()) {
                final Side side = new Side(source.doc(), List.of(new KeyField(source.userField(),
                        source.transforms())), role.filter(), timeRange, KeyRange.ALL, List.of(List.of(term)));
                try (final KeyGroupSource scan = KeyGroupSource.scan(streams, List.of(side), source.fetched(),
                        PAGE_SIZE, JoinKeys.comparator(List.of(source.kind())), budget)) {
                    final KeyGroup group = refused(scan::next);
                    if (group != null) {
                        for (final Row row : group.rows()) {
                            events.add(event(s, source, role, row));
                        }
                        count += group.rows().size();
                    }
                    cut += scan.cutKeys();
                }
                if (budget.check()) {
                    break;
                }
            }
            counts.add(count);
            if (budget.stoppedByUser()) {
                throw new TaskTerminatedException();
            }
            if (budget.reached() != null) {
                break;
            }
        }
        return new Read(events, counts, budget.reached(), cut > 0);
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
                  List<RoleRead> roles) {

        /// Copies the lists.
        Source {
            transforms = List.copyOf(transforms);
            roles = List.copyOf(roles);
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
            return List.copyOf(fields);
        }

        /// @param entity The user as the call gives it.
        /// @return It as the terms condition takes it: transformed text, or a number for a number field.
        Object term(final String entity) {
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
    record Read(List<Event> events, List<Long> counts, JoinBudget.Limit reached, boolean cut) {

    }
}
