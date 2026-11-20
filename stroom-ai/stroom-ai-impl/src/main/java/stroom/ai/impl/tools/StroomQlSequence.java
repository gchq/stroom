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

import stroom.ai.impl.tools.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.EqlQueryWriter.Step;
import stroom.ai.impl.tools.EqlSequenceResults.Entity;
import stroom.ai.impl.tools.EqlSequenceResults.Hit;
import stroom.ai.impl.tools.EqlSequenceResults.Row;
import stroom.ai.impl.tools.SequenceQueryTool.GivenStep;
import stroom.ai.impl.tools.SequenceStroomQl.Side;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.task.api.TaskTerminatedException;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/// Runs a `sequence_query` call whose steps aren't all on Elastic Indexes in Stroom itself, as one StroomQL windowed
/// join ([SequenceStroomQl]) through Stroom's query service, as `run_stroomql` runs a query: as the user, with Use
/// on each data source checked by the service, and each search removed once read ([AgentSearches]).
///
/// A windowed join takes two steps, joined by their `by` fields, so anything more (a third step, `until`, a missing
/// step, `runs`) is refused, saying it needs Elastic Indexes. Its cost grows with the first step's events, so the
/// first step is counted first (events and distinct join values, one grouped search) and a join too slow to finish
/// is refused before it runs ([#tooBroad]), as is a second step with no condition of its own.
class StroomQlSequence {

    /// The most first-step events a windowed join is run for. Measured on 1 million synthetic events on one node
    /// (2026-11-20, `TestBenchSequenceJoins`): a Lucene windowed join costs about 11 µs per joined row read plus
    /// 0.37 µs per joined row read for each first-step event of the same entity; 10,000 first-step events ran in
    /// under 5 seconds with a filtered second step, 100,000 by 100 users took 37 seconds.
    static final long MAX_ANCHORS = 100_000;
    /// The first-step events per distinct join value above which the lower cap, [#DENSE_MAX_ANCHORS], applies: the
    /// second part of the cost is per first-step event of the same entity, so many events per entity are slow
    /// (the same benchmark as [#MAX_ANCHORS]).
    static final long DENSE_ANCHORS_PER_KEY = 100;
    /// The most first-step events a windowed join is run for when they have more than [#DENSE_ANCHORS_PER_KEY] each
    /// per distinct join value (the same benchmark as [#MAX_ANCHORS]).
    static final long DENSE_MAX_ANCHORS = 10_000;
    /// The most pairs the join returns when the call doesn't say.
    static final int DEFAULT_LIMIT = 1_000;
    /// What every result with pairs says of them.
    static final String PAIRS_NOTE = "Each pair is a step 1 event and a step 2 event of the same entity within the "
                                     + "window after it, so a step 1 event followed by several step 2 events gives "
                                     + "a pair for each.";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final String FIELDS_HINT = "describe_data_source gives its fields";
    private static final String NEEDS_ELASTIC = "3 or more steps, until, missing and runs need every step's data "
                                                + "source to be an Elastic Index (run as EQL): split the question "
                                                + "into two-step sequences, or ask the user.";

    private final Provider<QueryService> queryServiceProvider;
    private final AgentSearches agentSearches;
    private final DataSourceAccess dataSourceAccess;

    /// @param queryServiceProvider Runs the searches, as `run_stroomql` does.
    /// @param agentSearches        Removes each search once read.
    /// @param dataSourceAccess     Finds each data source's time field.
    StroomQlSequence(final Provider<QueryService> queryServiceProvider,
                     final AgentSearches agentSearches,
                     final DataSourceAccess dataSourceAccess) {
        this.queryServiceProvider = queryServiceProvider;
        this.agentSearches = agentSearches;
        this.dataSourceAccess = dataSourceAccess;
    }

    /// Checks the steps and works out the join's two sides, before the window is asked for, so nothing the join
    /// can't do is asked of the user first.
    ///
    /// @param given   The steps as given.
    /// @param until   The until step as given, or null.
    /// @param byGiven Each step's data source, by its index as given.
    /// @param reason  Why the sequence runs in Stroom, naming the data source that isn't an Elastic Index.
    /// @return The plan.
    /// @throws WorkspaceException for anything the join can't do, saying what it can.
    Plan plan(final List<GivenStep> given,
              final GivenStep until,
              final Map<String, DocRef> byGiven,
              final String reason) {
        final DocRef other = byGiven.values().stream()
                .filter(doc -> !SequenceQueryTool.isElastic(doc))
                .findFirst()
                .orElseThrow();
        final String intro = "On " + DataSourceAccess.describe(other) + ", which isn't an Elastic Index, a sequence "
                             + "runs in Stroom as a StroomQL windowed join, which takes 2 steps joined by their by "
                             + "fields (each with where, any_of and fields to show): ";
        if (given.size() != 2) {
            throw new WorkspaceException(intro + "this has " + given.size() + " steps. " + NEEDS_ELASTIC);
        }
        if (until != null) {
            throw new WorkspaceException(intro + "this has an until step. " + NEEDS_ELASTIC);
        }
        for (int i = 0; i < given.size(); i++) {
            if (given.get(i).missing()) {
                throw new WorkspaceException(intro + "step " + (i + 1) + " is missing=true. " + NEEDS_ELASTIC);
            }
            if (given.get(i).runs() > 1) {
                throw new WorkspaceException(intro + "step " + (i + 1) + " has runs=" + given.get(i).runs() + ". "
                                             + NEEDS_ELASTIC);
            }
        }
        final DocRef firstDoc = byGiven.get(given.getFirst().index());
        final DocRef secondDoc = byGiven.get(given.getLast().index());
        final Step first = step(given.getFirst(), "Step 1", firstDoc, 1);
        final Step second = step(given.getLast(), "Step 2", secondDoc, firstDoc.equals(secondDoc)
                ? 1
                : 2);
        if (first.joinFields().isEmpty()) {
            throw new WorkspaceException("Step 1 has no join fields: give by, the fields that say the events are the "
                                         + "same entity's (e.g. [\"UserId\"]), in both steps.");
        }
        if (second.joinFields().size() != first.joinFields().size()) {
            throw new WorkspaceException("Step 2 joins on " + second.joinFields() + " but step 1 on "
                                         + first.joinFields() + ": give both steps the same number of join fields, "
                                         + "in the same order (each its own names for the same things).");
        }
        final String firstUnwritable = SequenceStroomQl.unwritable("Step 1", first, firstDoc);
        if (firstUnwritable != null) {
            throw new WorkspaceException(firstUnwritable);
        }
        final String secondUnwritable = SequenceStroomQl.unwritable("Step 2", second, secondDoc);
        if (secondUnwritable != null) {
            throw new WorkspaceException(secondUnwritable);
        }
        if (second.filters().isEmpty() && second.anyOf().isEmpty()) {
            throw new WorkspaceException("Step 2 has no conditions: in Stroom the join reads every event of "
                                         + DataSourceAccess.describe(secondDoc) + " in the windows after step 1's "
                                         + "events, which is slow (measured over 1 million events: 14 to 400 "
                                         + "seconds, against under 5 with a condition). Give step 2 a where or "
                                         + "any_of condition.");
        }
        final Side firstSide = new Side(firstDoc, timeField(firstDoc), first, hasIds(firstDoc));
        final Side secondSide = new Side(secondDoc, timeField(secondDoc), second, hasIds(secondDoc));
        return new Plan(firstSide, secondSide, reason);
    }

    /// Counts the first step, refuses a join too slow to finish, and runs it.
    ///
    /// @param plan The plan.
    /// @param call What the call asks: window, time range, limit, output.
    /// @return The result.
    ToolResult run(final Plan plan, final Call call) {
        final String query = SequenceStroomQl.join(plan.first(), plan.second(), call.maxspan(), call.timeRange(),
                call.limit());
        if (query.length() > call.maxQueryLength()) {
            throw new WorkspaceException("The sequence's StroomQL would be longer than a query may be ("
                                         + call.maxQueryLength() + " characters): give fewer conditions or values.");
        }
        final String countQuery = SequenceStroomQl.anchorCount(plan.first(), call.timeRange());
        final DashboardSearchResponse counted = search(countQuery, 1, call, "Counting step 1");
        final Map<String, String> count = firstRow(counted, List.of(SequenceStroomQl.ANCHORS,
                SequenceStroomQl.KEYS));
        final long anchors = number(count.get(SequenceStroomQl.ANCHORS));
        final long keys = number(count.get(SequenceStroomQl.KEYS));
        final String tooBroad = tooBroad(anchors, keys);
        if (tooBroad != null) {
            throw new WorkspaceException("Not run: " + tooBroad + " Narrow the first step (more conditions, or a "
                                         + "shorter time range) to at most " + MAX_ANCHORS + " events, or at most "
                                         + DENSE_MAX_ANCHORS + " when each entity has more than "
                                         + DENSE_ANCHORS_PER_KEY + ".\n\nThe join would be:\n" + query);
        }
        final List<Row> rows;
        final List<String> warnings = new ArrayList<>();
        if (anchors == 0) {
            rows = List.of();
        } else {
            final DashboardSearchResponse joined = search(query, call.limit(), call, "The sequence's join");
            warnings.addAll(SearchProblems.warnings(joined));
            rows = rows(joined, plan);
        }
        final List<Step> steps = List.of(plan.first().step(), plan.second().step());
        final List<String> columns = EqlSequenceResults.columns(steps);
        final List<List<String>> cells = rows.stream().map(row -> EqlSequenceResults.cells(row, steps)).toList();
        if (!cells.isEmpty()) {
            ToolTables.write(call.workspace(), call.out(), columns, cells, plan.first().doc().getUuid());
        }
        final Outcome outcome = new Outcome(plan, query, countQuery, anchors, keys, rows, steps, columns, cells,
                EqlSequenceResults.entities(rows), warnings, invalid(query), rows.size() >= call.limit(), call);
        final Map<String, Object> structured = structured(outcome);
        return call.chat()
                ? ToolResult.text(markdown(outcome)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    /// Why a join with this many first-step events would be too slow to run, or null if it isn't.
    ///
    /// @param anchors The first step's events.
    /// @param keys    Their distinct join values.
    /// @return The reason, or null.
    static String tooBroad(final long anchors, final long keys) {
        final String matched = "step 1 matches " + anchors + " events, with " + keys + " distinct join value"
                               + (keys == 1
                ? ""
                : "s");
        if (anchors > MAX_ANCHORS) {
            return matched + ", and a windowed join of more than " + MAX_ANCHORS + " first-step events is too slow "
                   + "to finish in the time limit.";
        }
        if (anchors > DENSE_MAX_ANCHORS && anchors > keys * DENSE_ANCHORS_PER_KEY) {
            return matched + " (" + (keys == 0
                    ? anchors
                    : anchors / keys) + " each), and with more than " + DENSE_ANCHORS_PER_KEY + " per entity a "
                   + "windowed join of more than " + DENSE_MAX_ANCHORS + " first-step events is too slow to finish "
                   + "in the time limit.";
        }
        return null;
    }

    // --------------------------------------------------------------------------------
    // Planning

    // A step, checked against its data source's fields.
    private Step step(final GivenStep given, final String what, final DocRef doc, final int position) {
        final List<QueryField> fields = EsqlQueryTool.fields(queryServiceProvider.get(), doc);
        if (fields.isEmpty()) {
            throw new WorkspaceException("The fields of " + DataSourceAccess.describe(doc) + " can't be read, so "
                                         + "the steps' fields can't be checked. " + FIELDS_HINT + ".");
        }
        final Map<String, FieldType> types = new LinkedHashMap<>();
        fields.forEach(field -> types.put(field.getFldName(), field.getFldType()));
        final Set<String> named = new LinkedHashSet<>();
        given.filters().forEach(filter -> named.add(filter.field()));
        given.anyOf().forEach(filter -> named.add(filter.field()));
        named.addAll(given.by());
        named.addAll(given.fields());
        final List<String> unknown = named.stream().filter(name -> !types.containsKey(name)).toList();
        if (!unknown.isEmpty()) {
            throw new WorkspaceException(what + ": not fields of " + DataSourceAccess.describe(doc) + ": "
                                         + EsqlQueryTool.said(unknown, types.keySet()) + ". " + FIELDS_HINT + ".");
        }
        final List<Filter> all = new ArrayList<>(given.filters());
        all.addAll(given.anyOf());
        for (final Filter filter : all) {
            if (filter.value().isEmpty() && filter.condition() != Condition.IS_NULL
                && filter.condition() != Condition.IS_NOT_NULL) {
                throw new WorkspaceException(what + ": the condition on '" + filter.field() + "' has no value.");
            }
        }
        return new Step(position, given.filters(), given.anyOf(), given.by(), false, 1, given.fields(), types);
    }

    private String timeField(final DocRef doc) {
        return dataSourceAccess.timeField(doc)
                .map(QueryField::getFldName)
                .filter(NullSafe::isNonBlankString)
                .orElseThrow(() -> new WorkspaceException(DataSourceAccess.describe(doc) + " has no time field, which "
                                                          + "a sequence orders its events by: use a data source with "
                                                          + "one."));
    }

    private boolean hasIds(final DocRef doc) {
        final Set<String> names = new LinkedHashSet<>();
        EsqlQueryTool.fields(queryServiceProvider.get(), doc).forEach(field -> names.add(field.getFldName()));
        return names.contains(EqlSequenceResults.STREAM_ID) && names.contains(EqlSequenceResults.EVENT_ID);
    }

    // --------------------------------------------------------------------------------
    // Searching

    // Runs a query as run_stroomql does, waiting until it completes, and removes the search once read.
    private DashboardSearchResponse search(final String query,
                                           final int rows,
                                           final Call call,
                                           final String what) {
        final QueryService queryService = queryServiceProvider.get();
        final QuerySearchRequest request = QuerySearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.API).build())
                .query(query)
                .queryContext(QueryContext.builder().dateTimeSettings(DateTimeSettings.builder().build()).build())
                .incremental(false)
                .timeout(call.timeLimit().toMillis())
                .storeHistory(false)
                .requestedRange(new OffsetRange(0L, (long) rows))
                .build();
        QueryKey queryKey = null;
        try {
            DashboardSearchResponse response = queryService.search(request);
            queryKey = NullSafe.get(response, DashboardSearchResponse::getQueryKey);
            while (response != null && SearchProblems.errors(response).isEmpty() && !response.isComplete()) {
                pause(call.stopped());
                response = queryService.search(request.copy().queryKey(response.getQueryKey()).build());
            }
            if (response == null) {
                throw new WorkspaceException(what + " failed: Stroom's query service gave no response.");
            }
            final List<String> errors = SearchProblems.errors(response);
            if (!errors.isEmpty()) {
                throw new WorkspaceException(what + " failed: " + String.join("; ", errors) + "\n\nThe query was:\n"
                                             + query);
            }
            return response;
        } finally {
            agentSearches.destroy(queryKey);
        }
    }

    private static void pause(final BooleanSupplier stopped) {
        if (stopped.getAsBoolean()) {
            throw new TaskTerminatedException();
        }
        ToolDeadline.check();
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolTimeoutException();
        }
    }

    // The first row's values of the named columns; empty if there is no row.
    private static Map<String, String> firstRow(final DashboardSearchResponse response, final List<String> names) {
        final TableResult table = table(response);
        final Map<String, String> values = new LinkedHashMap<>();
        if (table == null || NullSafe.isEmptyCollection(table.getRows())) {
            return values;
        }
        final List<String> row = table.getRows().getFirst().getValues();
        final List<Integer> positions = positions(table, names);
        for (int i = 0; i < names.size(); i++) {
            values.put(names.get(i), value(row, positions.get(i)));
        }
        return values;
    }

    // The joined rows as sequences of one event per step, in time order.
    private static List<Row> rows(final DashboardSearchResponse response, final Plan plan) {
        final TableResult table = table(response);
        if (table == null || NullSafe.isEmptyCollection(table.getRows())) {
            return List.of();
        }
        final List<String> names = SequenceStroomQl.columnNames(plan.first(), plan.second());
        final List<Integer> positions = positions(table, names);
        final Map<String, Integer> byName = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            byName.put(names.get(i), positions.get(i));
        }
        final List<Row> rows = new ArrayList<>();
        for (final stroom.query.api.Row tableRow : table.getRows()) {
            final List<String> values = tableRow.getValues();
            final List<Object> keys = new ArrayList<>();
            plan.first().step().joinFields().forEach(key -> keys.add(value(values, byName.get(key))));
            rows.add(new Row(keys, List.of(List.of(hit(values, byName, 1, plan.first())),
                    List.of(hit(values, byName, 2, plan.second())))));
        }
        rows.sort(Comparator.comparing((Row row) -> time(row, 0), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> time(row, 1), Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private static Long time(final Row row, final int step) {
        return row.byStep().get(step).getFirst().time();
    }

    private static Hit hit(final List<String> values,
                           final Map<String, Integer> byName,
                           final int stepNumber,
                           final Side side) {
        final Map<String, List<Object>> fields = new LinkedHashMap<>();
        for (final String shown : side.step().fields()) {
            final String value = value(values, byName.get(SequenceStroomQl.stepColumn(stepNumber, shown)));
            fields.put(shown, value.isEmpty()
                    ? List.of()
                    : List.of(value));
        }
        final String time = value(values, byName.get(SequenceStroomQl.stepColumn(stepNumber,
                SequenceStroomQl.TIME)));
        return new Hit(epochMillis(time),
                side.hasIds()
                        ? value(values, byName.get(SequenceStroomQl.stepColumn(stepNumber,
                        EqlSequenceResults.STREAM_ID)))
                        : "",
                side.hasIds()
                        ? value(values, byName.get(SequenceStroomQl.stepColumn(stepNumber,
                        EqlSequenceResults.EVENT_ID)))
                        : "",
                fields);
    }

    // A time as a search gives it: ISO 8601, or epoch milliseconds.
    private static Long epochMillis(final String value) {
        if (NullSafe.isBlankString(value)) {
            return null;
        }
        try {
            return Instant.parse(value.strip()).toEpochMilli();
        } catch (final DateTimeParseException e) {
            return EqlSequenceResults.epochMillis(value);
        }
    }

    private static TableResult table(final DashboardSearchResponse response) {
        for (final Result result : NullSafe.list(response.getResults())) {
            if (result instanceof final TableResult table) {
                return table;
            }
        }
        return null;
    }

    // Where each named column is in the table's rows.
    private static List<Integer> positions(final TableResult table, final List<String> names) {
        final List<String> columns = NullSafe.list(table.getColumns()).stream().map(Column::getName).toList();
        final List<Integer> positions = new ArrayList<>();
        for (final String name : names) {
            final int position = columns.indexOf(name);
            if (position < 0) {
                throw new WorkspaceException("The search's results have no column '" + name + "' (its columns: "
                                             + String.join(", ", columns) + ").");
            }
            positions.add(position);
        }
        return positions;
    }

    private static String value(final List<String> values, final Integer position) {
        return position == null || values == null || position >= values.size() || values.get(position) == null
                ? ""
                : values.get(position);
    }

    private static long number(final String value) {
        if (NullSafe.isBlankString(value)) {
            return 0;
        }
        try {
            return (long) Double.parseDouble(value.strip().replace(",", ""));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    // Why the query isn't valid StroomQL, or null if it is.
    private String invalid(final String query) {
        try {
            final ValidateExpressionResult result = queryServiceProvider.get().validateQuery(query);
            return result != null && result.isOk()
                    ? null
                    : NullSafe.nonBlankStringElse(NullSafe.get(result, ValidateExpressionResult::getString),
                            "the parser gave no reason");
        } catch (final RuntimeException e) {
            return NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName());
        }
    }

    // --------------------------------------------------------------------------------
    // The result

    private static String countedText(final Outcome outcome) {
        return outcome.anchors() == 0
                ? "Step 1 matches no events, so the join wasn't run: check its conditions and the time range."
                : "Counted first: step 1 matches " + outcome.anchors() + " events, with " + outcome.keys()
                  + " distinct join value" + (outcome.keys() == 1
                        ? ""
                        : "s") + ".";
    }

    /// What the pairs are: how many, and whether the limit cut them short.
    ///
    /// @param outcome The call's outcome.
    /// @return The note.
    static String pairsNote(final Outcome outcome) {
        final int count = outcome.rows().size();
        final StringBuilder sb = new StringBuilder().append(count).append(" pair").append(count == 1
                ? ""
                : "s").append(" found, for ").append(outcome.entities().size()).append(" entit")
                .append(outcome.entities().size() == 1
                        ? "y"
                        : "ies").append('.');
        if (count > 0) {
            sb.append(' ').append(PAIRS_NOTE);
        }
        if (!outcome.warnings().isEmpty()) {
            sb.append(" Stroom warned: ").append(String.join("; ", outcome.warnings()));
        }
        if (outcome.truncated()) {
            sb.append(" They reached the limit (").append(outcome.call().limit()).append("), so there may be more: "
                                                                                       + "narrow the steps or the "
                                                                                       + "time range, or raise "
                                                                                       + "limit, and don't present "
                                                                                       + "them as all.");
        }
        return sb.toString();
    }

    private static String reviewNote(final Outcome outcome) {
        return outcome.invalid() == null
                ? "The query above is the sequence itself, as valid StroomQL: offer it with propose_query for the "
                  + "user to save as a Query, or propose_dashboard for a table with the event preview."
                : "The query above isn't valid StroomQL to save (" + outcome.invalid() + "), so don't offer it.";
    }

    private static String markdown(final Outcome outcome) {
        final Call call = outcome.call();
        final StringBuilder sb = new StringBuilder("Ran the sequence in Stroom as a StroomQL windowed join, with "
                                                   + "maxspan ")
                .append(EqlQueryWriter.maxspanText(call.maxspan())).append(" (").append(call.maxspanSource())
                .append("). ").append(outcome.plan().reason()).append("\n\n```\n").append(outcome.query())
                .append("\n```\n");
        if (call.timeRange() != null) {
            sb.append("Time: ").append(NullSafe.nonBlankStringElse(call.timeRange().getFrom(), "any"))
                    .append(" to ").append(NullSafe.nonBlankStringElse(call.timeRange().getTo(), "any"))
                    .append(".\n");
        }
        sb.append('\n').append(countedText(outcome)).append("\n\n").append(pairsNote(outcome)).append('\n');
        if (!outcome.entities().isEmpty()) {
            final List<String> columns = new ArrayList<>(outcome.steps().getFirst().joinFields());
            columns.addAll(List.of("pairs", "first", "last", "shortest gap (s)"));
            final List<List<String>> cells = outcome.entities().stream().map(StroomQlSequence::entityCells).toList();
            sb.append("\nBy entity:\n\n").append(ToolTables.preview(columns, cells, SequenceQueryTool.SHOWN_ROWS))
                    .append('\n');
            sb.append("\nPairs:\n\n").append(ToolTables.preview(outcome.columns(), outcome.cells(),
                            SequenceQueryTool.SHOWN_ROWS))
                    .append("\n\nThe pairs are in ").append(call.out()).append(" (each step's StreamId and EventId, "
                                                                           + "for fetch_events and search_events).\n");
        }
        sb.append('\n').append(reviewNote(outcome));
        return sb.toString();
    }

    private static List<String> entityCells(final Entity entity) {
        final List<String> cells = new ArrayList<>(entity.keys());
        cells.add(String.valueOf(entity.sequences()));
        cells.add(EqlSequenceResults.iso(entity.first()));
        cells.add(EqlSequenceResults.iso(entity.last()));
        cells.add(EqlSequenceResults.seconds(entity.shortestSpan()));
        return cells;
    }

    private static Map<String, Object> structured(final Outcome outcome) {
        final Call call = outcome.call();
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("engine", "StroomQL");
        map.put("engine_reason", outcome.plan().reason());
        final List<DocRef> docs = new ArrayList<>(new LinkedHashSet<>(List.of(outcome.plan().first().doc(),
                outcome.plan().second().doc())));
        map.put("indexes", docs.stream().map(doc -> {
            final Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", doc.getType());
            ref.put("uuid", doc.getUuid());
            ref.put("name", doc.getName());
            return ref;
        }).toList());
        map.put("query", outcome.query());
        map.put("count_query", outcome.countQuery());
        map.put("maxspan", EqlQueryWriter.maxspanText(call.maxspan()));
        map.put("maxspan_source", call.maxspanSource());
        if (call.timeRange() != null) {
            final Map<String, Object> time = new LinkedHashMap<>();
            time.put("from", call.timeRange().getFrom());
            time.put("to", call.timeRange().getTo());
            map.put("time_range", time);
        }
        map.put("time_field", outcome.plan().first().timeField());
        map.put("step_counts", List.of(outcome.anchors()));
        map.put("first_step_keys", outcome.keys());
        map.put("counted_note", countedText(outcome));
        map.put("sequence_count", outcome.rows().size());
        map.put("entity_count", outcome.entities().size());
        map.put("limit", call.limit());
        map.put("truncated", outcome.truncated());
        map.put("note", pairsNote(outcome));
        if (!outcome.warnings().isEmpty()) {
            map.put("warnings", outcome.warnings());
        }
        map.put("entities", outcome.entities().stream().limit(SequenceQueryTool.SHOWN_ROWS).map(entity -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("keys", entity.keys());
            row.put("sequences", entity.sequences());
            row.put("first", EqlSequenceResults.iso(entity.first()));
            row.put("last", EqlSequenceResults.iso(entity.last()));
            row.put("shortest_gap_s", EqlSequenceResults.seconds(entity.shortestSpan()));
            return row;
        }).toList());
        map.put("columns", outcome.columns());
        map.put("rows", outcome.cells().stream().limit(SequenceQueryTool.SHOWN_ROWS).toList());
        if (!outcome.cells().isEmpty()) {
            map.put("table", call.out().toString());
        }
        final Map<String, Object> review = new LinkedHashMap<>();
        review.put("index", outcome.plan().first().doc().getName());
        review.put("uuid", outcome.plan().first().doc().getUuid());
        review.put("query", outcome.query());
        review.put("exact", false);
        review.put("valid", outcome.invalid() == null);
        if (outcome.invalid() != null) {
            review.put("invalid", outcome.invalid());
        }
        review.put("note", "The sequence's own windowed join: it gives the same pairs when run again.");
        map.put("review_queries", List.of(review));
        map.put("review_hint", reviewNote(outcome));
        return map;
    }


    // --------------------------------------------------------------------------------


    /// The join's two sides, checked.
    ///
    /// @param first  The first step's side.
    /// @param second The second step's side.
    /// @param reason Why the sequence runs in Stroom.
    record Plan(Side first, Side second, String reason) {

    }


    // --------------------------------------------------------------------------------


    /// What the call asks of the join.
    ///
    /// @param maxspan        The window.
    /// @param maxspanSource  Where the window came from.
    /// @param timeRange      The time range, or null.
    /// @param limit          The most pairs.
    /// @param timeLimit      How long each search may take.
    /// @param maxQueryLength The longest a query may be.
    /// @param workspace      The workspace the pairs are written to.
    /// @param out            The pairs' file.
    /// @param chat           True for a chat (Markdown), false over MCP (JSON).
    /// @param stopped        True once the user has stopped the run.
    record Call(Duration maxspan,
                String maxspanSource,
                TimeRange timeRange,
                int limit,
                Duration timeLimit,
                int maxQueryLength,
                SessionWorkspace workspace,
                WorkspacePath out,
                boolean chat,
                BooleanSupplier stopped) {

    }


    // --------------------------------------------------------------------------------


    /// Everything the result says.
    ///
    /// @param plan       The plan.
    /// @param query      The join.
    /// @param countQuery The first step's count.
    /// @param anchors    The first step's events.
    /// @param keys       Their distinct join values.
    /// @param rows       The pairs.
    /// @param steps      The two steps.
    /// @param columns    The pairs table's columns.
    /// @param cells      The pairs table's rows.
    /// @param entities   The summary per entity.
    /// @param warnings   What Stroom warned of.
    /// @param invalid    Why the join isn't valid StroomQL, or null.
    /// @param truncated  True if the pairs reached the limit.
    /// @param call       What the call asked.
    record Outcome(Plan plan,
                   String query,
                   String countQuery,
                   long anchors,
                   long keys,
                   List<Row> rows,
                   List<Step> steps,
                   List<String> columns,
                   List<List<String>> cells,
                   List<Entity> entities,
                   List<String> warnings,
                   String invalid,
                   boolean truncated,
                   Call call) {

    }
}
