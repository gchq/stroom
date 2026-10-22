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

import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.IndexSearches.Counted;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.MisleadingSearches;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.Fetched;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.fielddoc.IndexFieldDocs;
import stroom.ai.impl.tools.generation.PythonRepr;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.PythonText;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.LayoutConstraints;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.Size;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TimeRange;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// verify_index's checks of an index (the Stroom MCP server's `tools/indexing.py` `run_test_searches`,
/// `_completeness`, `_count`, `_counted`, `_stream_events`, `_unsorted`, `_cut_off`, `_traced`, `_trace_checks`,
/// `_hit_ids`, `_same_value`, `_own_xslt_element`, `dashboard_config`, `window_start`, `SearchCheck`, `_as_searched`
/// and `_searchable`): searches of the sample's streams through Stroom, as a dashboard makes them (nothing saved),
/// each hit traced back to the record it came from by stepping the indexing pipeline.
class IndexChecks {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexChecks.class);

    /// A stream's documents counted one by one; past this, its first and last [#ENDS] events are, and none past its
    /// last: every document of a stream would be read through Stroom.
    static final long FULL_COUNT = 10_000;
    /// The events counted at each end of a large stream.
    static final long ENDS = 1_000;
    /// The columns kept hidden on a verification dashboard, for its text pane and for tracing hits.
    static final List<String> IDS = List.of("StreamId", "EventId");
    /// The conditions a search takes, as a dashboard's.
    static final List<String> CONDITIONS = List.of("EQUALS", "NOT_EQUALS", "CONTAINS", "STARTS_WITH", "ENDS_WITH",
            "MATCHES_REGEX", "GREATER_THAN", "GREATER_THAN_OR_EQUAL_TO", "LESS_THAN", "LESS_THAN_OR_EQUAL_TO",
            "BETWEEN", "IN", "IS_NULL", "IS_NOT_NULL");
    /// The time between counts while the index catches up.
    static final Duration CATCH_UP = Duration.ofSeconds(5);

    private static final String QUERY_ID = "query-VERIFY";
    private static final String TABLE_ID = "table-VERIFY";
    private static final String TEXT_ID = "text-VERIFY";
    private static final String MODEL_VERSION = "7.2.0";
    private static final String TEXT_MODEL_VERSION = "7.8.0";
    private static final int PAGE = 100;
    private static final DateTimeFormatter WINDOW = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'.000Z'")
            .withZone(ZoneOffset.UTC);
    // What a stepped record, a record not found and one whose step failed are kept as, by stream and record.
    private static final Object NOT_FOUND = new Object();

    private final IndexSearches indexSearches;
    private final PipelineStepping stepping;
    private final StreamReader streamReader;
    private final IndexingMemory memory;
    private final IndexTemplateAgreements agreements;
    private final Sleeper sleeper;

    /// @param indexSearches Searches the index as a dashboard does.
    /// @param stepping      Steps the indexing pipeline to trace hits.
    /// @param streamReader  Counts the events of a stream.
    /// @param memory        Keeps the checks that passed in the session.
    /// @param agreements    Digests the indexing pipeline's code.
    @Inject
    IndexChecks(final IndexSearches indexSearches,
                final PipelineStepping stepping,
                final StreamReader streamReader,
                final IndexingMemory memory,
                final IndexTemplateAgreements agreements) {
        this(indexSearches, stepping, streamReader, memory, agreements, IndexChecks::sleep);
    }

    /// As the injected constructor, with the wait while the index catches up given, for tests.
    IndexChecks(final IndexSearches indexSearches,
                final PipelineStepping stepping,
                final StreamReader streamReader,
                final IndexingMemory memory,
                final IndexTemplateAgreements agreements,
                final Sleeper sleeper) {
        this.indexSearches = indexSearches;
        this.stepping = stepping;
        this.streamReader = streamReader;
        this.memory = memory;
        this.agreements = agreements;
        this.sleeper = sleeper;
    }

    // --------------------------------------------------------------------------------
    // Searches that would mislead.
    // --------------------------------------------------------------------------------

    /// IS_NOT_NULL and IS_NULL on Elasticsearch searched as EQUALS '*' and NOT_EQUALS '*', which is what they mean
    /// there: Stroom matches every document with either on Elasticsearch. Seen: refused with that advice, the agent
    /// sent IS_NOT_NULL again and again.
    ///
    /// @param backend  `elasticsearch` or `lucene`.
    /// @param searches The searches.
    /// @return Them as they are searched.
    static List<SearchCheck> asSearched(final String backend, final List<SearchCheck> searches) {
        final List<SearchCheck> out = new ArrayList<>();
        for (final SearchCheck search : searches) {
            final Condition asked = Condition.valueOf(search.condition());
            final Condition condition = MisleadingSearches.ELASTICSEARCH.equals(backend)
                    ? MisleadingSearches.elasticsearchNullAs(asked).orElse(null)
                    : null;
            if (condition == null) {
                out.add(search);
                continue;
            }
            final String said = condition == Condition.EQUALS
                    ? "any value"
                    : "no value";
            out.add(new SearchCheck(search.field(), condition.name(), "*", search.expected(),
                    search.field() + " " + condition.name() + " '*' (" + said + "), for " + asked.name()
                    + ": Stroom matches every document with " + asked.name() + " on Elasticsearch"));
        }
        return out;
    }

    /// Searches whose answer would mislead: on Elasticsearch, Stroom (7.13) finds nothing for STARTS_WITH and
    /// CONTAINS, nor for a wildcard on an ip field, nor on a field it doesn't list, and matches every document for
    /// IS_NULL and IS_NOT_NULL, without an error; on Lucene it finds nothing for STARTS_WITH and ENDS_WITH, for
    /// CONTAINS on a keyword field, nor for a range on a text field; IN takes values separated by commas.
    ///
    /// @param backend    `elasticsearch` or `lucene`.
    /// @param searches   The searches.
    /// @param ipFields   The index's ip fields (Elasticsearch).
    /// @param exact      The exact-match checks.
    /// @param luceneText The Lucene index's text fields and their analyzers.
    /// @param listed     The fields Stroom lists for an Elasticsearch index, or null (not checked).
    /// @param unindexed  The fields no search finds, with why.
    /// @param ecs        The ECS schema, or null.
    /// @throws ToolRefusal if any search would mislead, saying what to use instead.
    static void searchable(final String backend,
                           final List<SearchCheck> searches,
                           final Set<String> ipFields,
                           final List<Map<String, String>> exact,
                           final Map<String, String> luceneText,
                           final Set<String> listed,
                           final Map<String, String> unindexed,
                           final EcsSchema ecs) {
        final List<String> problems = new ArrayList<>(MisleadingSearches.problems(backend, List.of(), Set.of(),
                List.of(), Map.of(), unindexed));
        if (MisleadingSearches.ELASTICSEARCH.equals(backend) && !NullSafe.isEmptyCollection(listed)) {
            // Seen in production: message, mapped as match_only_text by ecs@mappings, isn't listed by Stroom; every
            // search on it found nothing, and after three tries the agent dropped the check.
            final Set<String> names = new LinkedHashSet<>();
            searches.forEach(search -> names.add(search.field()));
            NullSafe.list(exact).forEach(check -> {
                if (NullSafe.isNonEmptyString(check.get("field"))) {
                    names.add(check.get("field"));
                }
            });
            names.stream().filter(name -> !listed.contains(name))
                    .forEach(name -> problems.add(IndexFields.unlisted(name, ecs)));
        }
        problems.addAll(MisleadingSearches.problems(backend,
                searches.stream()
                        .map(search -> new MisleadingSearches.Search(search.field(),
                                Condition.valueOf(search.condition()), search.value()))
                        .toList(),
                ipFields,
                NullSafe.list(exact).stream()
                        .map(check -> new MisleadingSearches.Exact(check.get("field"), check.get("value")))
                        .toList(),
                luceneText,
                Map.of()));
        if (!problems.isEmpty()) {
            throw new ToolRefusal(MisleadingSearches.REFUSAL + String.join("; ", problems));
        }
    }

    // --------------------------------------------------------------------------------
    // The verification dashboard.
    // --------------------------------------------------------------------------------

    /// A verification dashboard: a query on the index doc (the time field, from windowStart through the end of
    /// today, run on open); a table of the user's fields, newest first, with StreamId and EventId as hidden
    /// columns; and a text pane on the selected row's record, with stepping, and no extraction pipeline. Laid out
    /// as a dashboard made in Stroom's UI is (live): every layout node sized, and the config's own size,
    /// constraints, model version and time range set. Without them the search API still ran the dashboard, but the
    /// UI showed it empty: no query, no widgets.
    ///
    /// @param source      The index doc.
    /// @param fields      The table's columns.
    /// @param timeField   The index's time field, or null.
    /// @param windowStart Where the query's time range starts, or null for no initial query.
    /// @return The dashboard's settings.
    static DashboardConfig dashboardConfig(final DocRef source,
                                           final List<String> fields,
                                           final String timeField,
                                           final String windowStart) {
        final List<String> shown = new ArrayList<>(new LinkedHashSet<>(fields)).stream()
                .filter(field -> !IDS.contains(field))
                .toList();
        final List<Column> columns = new ArrayList<>();
        for (final String field : shown) {
            columns.add(field.equals(timeField)
                    ? column(field).copy().sort(newestFirst()).build()
                    : column(field));
        }
        if (timeField != null && !shown.contains(timeField)) {
            // Sorted on, though not shown.
            columns.add(column(timeField).copy().visible(false).sort(newestFirst()).build());
        }
        final Map<String, Column> ids = new LinkedHashMap<>();
        IDS.forEach(name -> ids.put(name, column(name).copy().visible(false).build()));
        columns.addAll(ids.values());
        final ExpressionOperator.Builder expression = ExpressionOperator.builder();
        final boolean initial = timeField != null && windowStart != null;
        if (initial) {
            expression.addTerm(term(timeField, Condition.BETWEEN, windowStart + ",day()+1d"));
        }
        final List<ComponentConfig> components = List.of(
                ComponentConfig.builder().type("query").id(QUERY_ID).name("Query")
                        .settings(QueryComponentSettings.builder()
                                .dataSource(source)
                                .expression(expression.build())
                                .automate(Automate.builder().open(initial).refresh(false).build())
                                .build())
                        .build(),
                ComponentConfig.builder().type("table").id(TABLE_ID).name("Table")
                        .settings(TableComponentSettings.builder()
                                .queryId(QUERY_ID)
                                .dataSourceRef(source)
                                .columns(columns)
                                .extractValues(false)
                                .maxResults(List.of(1000L))
                                .pageSize(PAGE)
                                .showDetail(false)
                                .modelVersion(MODEL_VERSION)
                                .build())
                        .build(),
                ComponentConfig.builder().type("text").id(TEXT_ID).name("Text")
                        .settings(TextComponentSettings.builder()
                                .tableId(TABLE_ID)
                                .showAsHtml(false)
                                .showStepping(true)
                                .streamIdField(new ColumnRef(ids.get("StreamId").getId(), "StreamId"))
                                .recordNoField(new ColumnRef(ids.get("EventId").getId(), "EventId"))
                                .modelVersion(TEXT_MODEL_VERSION)
                                .build())
                        .build());
        return DashboardConfig.builder()
                .components(components)
                .layout(SplitLayoutConfig.builder()
                        .preferredSize(new Size(200, 200))
                        .dimension(Dimension.Y)
                        .children(List.of(
                                pane(QUERY_ID, 200, 150),
                                SplitLayoutConfig.builder()
                                        .preferredSize(new Size(200, 700))
                                        .dimension(Dimension.X)
                                        .children(List.of(pane(TABLE_ID, 860, 700), pane(TEXT_ID, 480, 700)))
                                        .build()))
                        .build())
                .layoutConstraints(new LayoutConstraints(true, true))
                .preferredSize(new Size(0, 0))
                .designMode(false)
                .timeRange(new TimeRange("All time", Condition.BETWEEN, null, null))
                .modelVersion(MODEL_VERSION)
                .build();
    }

    /// @param name An index field.
    /// @return Its column, as a dashboard shows it.
    static Column column(final String name) {
        return IndexSearches.column(name);
    }

    /// @param config A dashboard's settings.
    /// @return Its table's columns.
    static List<Column> tableColumns(final DashboardConfig config) {
        return NullSafe.list(config.getComponents()).stream()
                .filter(component -> component.getSettings() instanceof TableComponentSettings)
                .map(component -> NullSafe.list(((TableComponentSettings) component.getSettings()).getColumns()))
                .findFirst()
                .orElse(List.of());
    }

    /// The earliest time, rounded back to a 30-day boundary (days since the epoch), as the dashboard's start.
    ///
    /// @param times Times as a dashboard shows them; those that aren't times are passed over.
    /// @return The start, e.g. `2026-09-01T00:00:00.000Z`, or null if none is a time.
    static String windowStart(final Collection<String> times) {
        Instant earliest = null;
        for (final String time : times) {
            final Instant at = instant(time, true);
            if (at != null && (earliest == null || at.isBefore(earliest))) {
                earliest = at;
            }
        }
        if (earliest == null) {
            return null;
        }
        final long days = Math.floorDiv(earliest.getEpochSecond(), 86_400L);
        return WINDOW.format(Instant.ofEpochSecond((days - Math.floorMod(days, 30L)) * 86_400L));
    }

    // --------------------------------------------------------------------------------
    // The test searches.
    // --------------------------------------------------------------------------------

    /// Runs the test searches through the verification dashboard, the way people will search: all documents for
    /// the indexed stream ids (count must match), an exact match per key field, a time range, and any other
    /// searches. Every check is of the sample's streams, and unsorted. The index may hold millions of other
    /// documents: a check of the whole index answered for them (a time range counted everyone's documents in it,
    /// an exact match passed on any stream's), and the dashboard's table, sorted newest first, made Stroom read
    /// every match to sort it. The user's dashboard stays sorted. With the indexing pipeline, each check's first
    /// hit is traced back to its record.
    ///
    /// @param context      The call, told what each search is as it runs (asked for by the user).
    /// @param dashboard    The verification dashboard (saved or not).
    /// @param streamIds    The Events streams indexed.
    /// @param expected     The Events records in them.
    /// @param exact        Exact-match checks, each `{field, value}`.
    /// @param timeRange    `{field, from, to, expected}`, or null.
    /// @param retries      Retries while the index catches up.
    /// @param searches     Any other searches.
    /// @param pipelineUuid The indexing pipeline, or null.
    /// @return `passed`, `checks`, and `unfinished` and `next` when the call's time ran out.
    /// @throws ToolTimeoutException if the call is cancelled.
    Map<String, Object> runTestSearches(final ToolCallContext context,
                                        final Dashboard dashboard,
                                        final List<Long> streamIds,
                                        final long expected,
                                        final List<Map<String, String>> exact,
                                        final Map<String, Object> timeRange,
                                        final int retries,
                                        final List<SearchCheck> searches,
                                        final String pipelineUuid) {
        final CallProgress progress = CallProgress.of(context);
        final String label = NullSafe.nonBlankStringElse(dashboard.source().getName(), dashboard.name());
        final Dashboard unsorted = dashboard.unsorted();
        final ExpressionOperator byStream = byStream(streamIds);
        // Checks that passed earlier in the session, of these streams in this index as the same code wrote them,
        // aren't run again: seen on a long stream, a call outlasted the user's token, and the next one started
        // from the start.
        final Map<String, Map<String, Object>> passed = passedChecks(dashboard.source().getUuid(), streamIds,
                expected, pipelineUuid);
        final List<String> unfinished = new ArrayList<>();
        final List<Map.Entry<String, Supplier<Map<String, Object>>>> planned = new ArrayList<>();
        planned.add(Map.entry("documents", () -> documents(context, unsorted, streamIds, expected, retries, label,
                progress)));
        for (final Map<String, String> item : exact) {
            planned.add(Map.entry("exact " + item.get("field") + "=" + item.get("value"),
                    () -> exactMatch(context, unsorted, byStream, item, label)));
        }
        if (timeRange != null) {
            planned.add(Map.entry("time " + PyJson.dumpsSortedKeys(timeRange),
                    () -> inTimeRange(context, unsorted, byStream, timeRange, label)));
        }
        for (final SearchCheck search : searches) {
            planned.add(Map.entry("search " + search.field() + " " + search.condition() + " " + search.value() + " "
                                  + (search.expected() == null
                    ? "None"
                    : search.expected()), () -> other(context, unsorted, byStream, search, label)));
        }
        final List<Map<String, Object>> checks = new ArrayList<>();
        for (final Map.Entry<String, Supplier<Map<String, Object>>> check : planned) {
            final String key = check.getKey();
            if (passed.containsKey(key)) {
                final Map<String, Object> earlier = new LinkedHashMap<>(passed.get(key));
                earlier.put("checked", "earlier in this session");
                checks.add(earlier);
            } else if (PipelineStepping.timeIsUp()) {
                unfinished.add(key);
            } else {
                final Map<String, Object> made = new LinkedHashMap<>(check.getValue().get());
                made.put("_key", key);
                checks.add(made);
            }
        }
        if (NullSafe.isNonBlankString(pipelineUuid)) {
            unfinished.addAll(traceChecks(context, pipelineUuid,
                    checks.stream().filter(check -> check.containsKey("_key")).toList(),
                    PipelineStepping::timeIsUp));
        }
        for (final Map<String, Object> check : checks) {
            check.remove("_rows");
            check.remove("_spec");
            final Object key = check.remove("_key");
            if (key != null && Boolean.TRUE.equals(check.get("pass"))) {
                passed.put(String.valueOf(key), new LinkedHashMap<>(check));
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", checks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("pass")))
                             && unfinished.isEmpty());
        result.put("checks", checks);
        if (!unfinished.isEmpty()) {
            result.put("unfinished", unfinished);
            result.put("next", "Stopped before this session's time limit for a call ("
                               + PipelineStepping.timeLimit(context).map(limit -> limit.toSeconds() + " s")
                                       .orElse("none") + ") ran out, with " + unfinished.size() + " check(s) "
                               + "still to run: call verify_index again with the same arguments. The checks that "
                               + "passed are kept for this session, so only the rest run; each call has its own "
                               + "time, so there is nothing for the user to do.");
        }
        return result;
    }

    // The checks that passed earlier in the session, a store this run adds its own passes to; one kept nowhere
    // when there is no code to go by.
    private Map<String, Map<String, Object>> passedChecks(final String indexUuid,
                                                          final List<Long> streamIds,
                                                          final long expected,
                                                          final String pipelineUuid) {
        try {
            final String code = NullSafe.isNonBlankString(pipelineUuid)
                    ? agreements.indexingXsltDigest(pipelineUuid)
                    : "";
            return memory.passedChecks(indexUuid + "\n" + new TreeSet<>(streamIds) + "\n" + expected + "\n" + code);
        } catch (final RuntimeException e) {
            // Kept for convenience: without it, every check runs.
            LOGGER.debug(() -> "Passed checks not kept: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, Object> documents(final ToolCallContext context,
                                          final Dashboard dashboard,
                                          final List<Long> streamIds,
                                          final long expected,
                                          final int retries,
                                          final String label,
                                          final CallProgress progress) {
        final Map<Long, Long> sizes = streamEvents(streamIds);
        Map<String, Object> complete = Map.of();
        Object last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            complete = completeness(context, dashboard, streamIds, sizes, expected, label);
            // Waited for only while the index catches up: not when Stroom couldn't finish counting (waiting doesn't
            // make it count faster), nor once two counts in a row found the same.
            final boolean unproven = complete.containsKey("unproven")
                                     || NullSafe.list((List<?>) complete.get("parts")).stream()
                                             .anyMatch(part -> ((Map<?, ?>) part).containsKey("unproven"));
            if (Boolean.TRUE.equals(complete.get("pass")) || attempt == retries || unproven
                || Objects.equals(complete.get("progress"), last) || PipelineStepping.timeIsUp()) {
                break;
            }
            last = complete.get("progress");
            progress.say("Waiting for " + label + " to catch up: " + complete.get("progress") + " (try "
                         + (attempt + 1) + " of " + (retries + 1) + ")");
            sleeper.sleep(CATCH_UP);
        }
        final Found found = search(context, dashboard, byStream(streamIds), PAGE,
                "Reading the documents of streams " + PythonText.numbers(streamIds) + " in " + label);
        final Map<String, Object> out = new LinkedHashMap<>(complete);
        out.remove("progress");
        final List<Object> errors = new ArrayList<>(NullSafe.list((List<?>) complete.get("errors")));
        errors.addAll(found.errors());
        out.put("errors", errors);
        out.put("sample", found.rows().subList(0, Math.min(3, found.rows().size())));
        out.put("_rows", found.rows());
        out.put("_spec", Map.of());
        return out;
    }

    private Map<String, Object> exactMatch(final ToolCallContext context,
                                           final Dashboard dashboard,
                                           final ExpressionOperator byStream,
                                           final Map<String, String> item,
                                           final String label) {
        final String field = item.get("field");
        final String value = NullSafe.string(item.get("value"));
        final Found res = search(context, dashboard, scoped(byStream, term(field, Condition.EQUALS, value)), PAGE,
                "Searching " + label + ": " + field + " = " + value);
        // A value as a dashboard shows a field of several values ("retry, timeout"): the index holds each value on
        // its own, so EQUALS finds nothing (asked about by the user).
        final boolean several = res.rows().isEmpty() && java.util.Arrays.stream(value.split(",", -1))
                .filter(part -> !part.strip().isEmpty())
                .count() > 1;
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("check", field + " = " + value);
        check.put("returned", res.rows().size());
        check.put("pass", !res.rows().isEmpty());
        check.put("errors", res.errors());
        check.put("sample", res.rows().subList(0, Math.min(2, res.rows().size())));
        if (several) {
            check.put("why", "if " + field + " holds several values (a dashboard shows them separated by commas), "
                             + "the index holds each on its own: search one of them, e.g. '"
                             + stripChars(value.split(",", -1)[0], " []") + "'");
        }
        check.put("_rows", res.rows());
        check.put("_spec", spec(field, "EQUALS", value));
        return check;
    }

    private Map<String, Object> inTimeRange(final ToolCallContext context,
                                            final Dashboard dashboard,
                                            final ExpressionOperator byStream,
                                            final Map<String, Object> timeRange,
                                            final String label) {
        final String field = String.valueOf(timeRange.get("field"));
        final String from = String.valueOf(timeRange.get("from"));
        final String to = String.valueOf(timeRange.get("to"));
        final ExpressionOperator between = scoped(byStream, term(field, Condition.BETWEEN, from + "," + to));
        final String doing = "Searching " + label + ": " + field + " between " + from + " and " + to;
        final Counted counted = count(context, dashboard, between, doing);
        final long n = counted.count() != null
                ? counted.count()
                : search(context, dashboard, between, PAGE, doing).rows().size();
        final Object wanted = timeRange.get("expected");
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("check", field + " between " + from + " and " + to);
        check.put("expected", wanted);
        check.put("returned", n);
        check.put("pass", counted.complete() && (wanted == null || (wanted instanceof final Number number
                                                                    && number.longValue() == n)));
        check.put("errors", counted.errors());
        if (!counted.complete()) {
            check.put("unproven", cutOff(n));
        }
        return check;
    }

    private Map<String, Object> other(final ToolCallContext context,
                                      final Dashboard dashboard,
                                      final ExpressionOperator byStream,
                                      final SearchCheck search,
                                      final String label) {
        final ExpressionOperator expression = scoped(byStream, term(search.field(),
                Condition.valueOf(search.condition()), search.value()));
        final String doing = ("Searching " + label + ": " + search.field() + " " + search.condition() + " "
                              + search.value()).stripTrailing();
        final Found res = search(context, dashboard, expression, PAGE, doing);
        long n = res.rows().size();
        boolean countedAll = true;
        if (search.expected() != null && n >= PAGE) {
            // A page is full: the number is counted, not the rows of the first page.
            final Counted counted = count(context, dashboard, expression, doing);
            countedAll = counted.complete();
            n = counted.count() == null
                    ? n
                    : counted.count();
        }
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("check", (search.field() + " " + search.condition() + " " + search.value()).stripTrailing());
        check.put("expected", search.expected());
        check.put("returned", n);
        check.put("pass", (search.expected() == null
                ? n >= 1
                : n == search.expected()) && countedAll && res.errors().isEmpty());
        check.put("errors", res.errors());
        check.put("sample", res.rows().subList(0, Math.min(2, res.rows().size())));
        if (!countedAll) {
            check.put("unproven", cutOff(n));
        }
        if (search.searchedAs() != null) {
            check.put("searched_as", search.searchedAs());
        }
        check.put("_rows", res.rows());
        check.put("_spec", spec(search.field(), search.condition(), search.value()));
        return check;
    }

    /// Whether every document of the streams is in the index. A stream of more than [#FULL_COUNT] events is
    /// checked by its first and last [#ENDS] (EventId is the event's number in its stream, from 1) and by none past
    /// its last: a few thousand documents read, however large the stream (asked for by the user: a stream of
    /// 100,000 records was counted through Stroom, seven times over while the index caught up). The others are
    /// counted whole.
    ///
    /// @param context   The call.
    /// @param dashboard The verification dashboard.
    /// @param streamIds The streams.
    /// @param sizes     Each stream's events, or null for one whose count isn't known.
    /// @param expected  The documents expected in all.
    /// @param label     The index, as the user knows it.
    /// @return The check: `check`, `expected`, `returned` or `parts`, `pass`, `errors`, `progress`, `unproven`.
    Map<String, Object> completeness(final ToolCallContext context,
                                     final Dashboard dashboard,
                                     final List<Long> streamIds,
                                     final Map<Long, Long> sizes,
                                     final long expected,
                                     final String label) {
        final Map<Long, Long> large = new LinkedHashMap<>();
        sizes.forEach((stream, n) -> {
            if (n != null && n > FULL_COUNT) {
                large.put(stream, n);
            }
        });
        final List<Long> rest = streamIds.stream().filter(stream -> !large.containsKey(stream)).toList();
        final List<Map<String, Object>> parts = new ArrayList<>();
        final List<Object> errors = new ArrayList<>();
        if (!rest.isEmpty()) {
            final long want = expected - large.values().stream().mapToLong(Long::longValue).sum();
            final Counted counted = count(context, dashboard, of(rest),
                    "Counting the documents of streams " + PythonText.numbers(rest) + " in " + label);
            errors.addAll(counted.errors());
            final Map<String, Object> part = new LinkedHashMap<>();
            part.put("streams", rest);
            part.put("expected", want);
            part.put("returned", counted.count());
            part.put("pass", counted.complete() && Objects.equals(counted.count(), want));
            if (!counted.complete()) {
                part.put("unproven", cutOff(counted.count()));
            }
            parts.add(part);
        }
        large.forEach((stream, n) -> {
            final List<Object[]> ranges = List.of(
                    new Object[]{"events 1 to " + ENDS, List.of(
                            term("EventId", Condition.GREATER_THAN_OR_EQUAL_TO, "1"),
                            term("EventId", Condition.LESS_THAN_OR_EQUAL_TO, String.valueOf(ENDS))), ENDS},
                    new Object[]{"events " + (n - ENDS + 1) + " to " + n, List.of(
                            term("EventId", Condition.GREATER_THAN_OR_EQUAL_TO, String.valueOf(n - ENDS + 1)),
                            term("EventId", Condition.LESS_THAN_OR_EQUAL_TO, String.valueOf(n))), ENDS},
                    new Object[]{"none past event " + n, List.of(
                            term("EventId", Condition.GREATER_THAN, String.valueOf(n))), 0L});
            for (final Object[] range : ranges) {
                @SuppressWarnings("unchecked") final List<ExpressionTerm> terms = (List<ExpressionTerm>) range[1];
                final Counted counted = count(context, dashboard, of(List.of(stream), terms),
                        "Counting stream " + stream + "'s " + range[0] + " in " + label);
                errors.addAll(counted.errors());
                final Map<String, Object> part = new LinkedHashMap<>();
                part.put("streams", List.of(stream));
                part.put("of", range[0]);
                part.put("expected", range[2]);
                part.put("returned", counted.count());
                part.put("pass", counted.complete() && Objects.equals(counted.count(), range[2]));
                if (!counted.complete()) {
                    part.put("unproven", cutOff(counted.count()));
                }
                parts.add(part);
            }
        });
        final boolean passed = parts.stream().allMatch(part -> Boolean.TRUE.equals(part.get("pass")));
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("check", "documents for streams " + PythonText.numbers(streamIds));
        check.put("expected", expected);
        if (large.isEmpty()) {
            final Map<String, Object> only = parts.getFirst();
            check.put("returned", only.get("returned"));
            check.put("pass", passed);
            check.put("errors", errors);
            if (only.containsKey("unproven")) {
                check.put("unproven", only.get("unproven"));
            }
            check.put("progress", (only.get("returned") == null
                    ? 0
                    : only.get("returned")) + " of " + expected + " documents indexed so far");
            return check;
        }
        final long held = parts.stream()
                .mapToLong(part -> part.get("returned") instanceof final Number number
                        ? number.longValue()
                        : 0L)
                .sum();
        check.put("pass", passed);
        check.put("errors", errors);
        check.put("how", "streams of more than " + FULL_COUNT + " events by their first and last " + ENDS + " and "
                         + "none past their last; the others counted whole");
        check.put("parts", parts);
        check.put("progress", parts.stream().filter(part -> Boolean.TRUE.equals(part.get("pass"))).count() + " of "
                              + parts.size() + " counts as expected, " + held + " documents found");
        return check;
    }

    /// Events in each stream, for a segmented one (an Events stream: a record each), as Stroom's data viewer says;
    /// null for a raw stream, whose count is of parts, or one it can't read.
    ///
    /// @param streamIds The streams.
    /// @return Each stream's events, or null.
    Map<Long, Long> streamEvents(final List<Long> streamIds) {
        final Map<Long, Long> sizes = new LinkedHashMap<>();
        for (final long streamId : streamIds) {
            try {
                final Fetched first = streamReader.fetch(streamId, 0, null);
                sizes.put(streamId, first.segmented()
                        ? first.totalItems()
                        : null);
            } catch (final RuntimeException e) {
                sizes.put(streamId, null);
            }
        }
        return sizes;
    }

    /// @param n The documents counted so far, or null.
    /// @return What a count Stroom hadn't finished in time says.
    static String cutOff(final Long n) {
        return "Stroom had not finished counting after " + IndexSearches.SEARCH_WAIT.toSeconds() + " s: at least "
               + (n == null
                ? 0
                : n) + " so far, not missing documents. Call again to check once more.";
    }

    // --------------------------------------------------------------------------------
    // Tracing hits to their records.
    // --------------------------------------------------------------------------------

    /// Each check's hit traced back to its record ([#traced]), every hit of a stream stepped in one stepping
    /// session. Stroom's stepping captures the whole stream in a session, and each hit stepped in a session of its
    /// own captured it again (seen: verifying a long stream took over five minutes, 'Tracing the first hit' said
    /// check after check, and outlasted the user's token). The hit with the lowest EventId is the one traced: the
    /// record nearest the start of its stream.
    ///
    /// @param context      The call, told each record traced.
    /// @param pipelineUuid The indexing pipeline.
    /// @param checks       The checks, each with its `_rows` and `_spec`: given `trace`, and `pass` with it.
    /// @param outOfTime    True once the call's time is up.
    /// @return The checks left untraced as time ran out, by key.
    /// @throws ToolTimeoutException if the call is cancelled.
    List<String> traceChecks(final ToolCallContext context,
                             final String pipelineUuid,
                             final List<Map<String, Object>> checks,
                             final BooleanSupplier outOfTime) {
        final List<Map.Entry<Map<String, Object>, Map<String, String>>> chosen = new ArrayList<>();
        for (final Map<String, Object> check : checks) {
            Map<String, String> first = null;
            for (final Map<String, String> row : rows(check)) {
                final long[] ids = hitIds(row);
                if (ids != null && (first == null || ids[1] < hitIds(first)[1])) {
                    first = row;
                }
            }
            if (first != null) {
                chosen.add(Map.entry(check, first));
            }
        }
        if (chosen.isEmpty()) {
            return List.of();
        }
        final Map<String, Object> stepped = new HashMap<>();
        String element = null;
        try {
            stepping.checkCanStep();
            final SteppedPipeline pipeline = stepping.load(pipelineUuid);
            pipeline.refuseMistyped();
            element = ownXsltElement(pipeline, pipelineUuid);
            stepAll(context, pipeline, chosen, element, stepped, outOfTime);
        } catch (final ToolRefusal e) {
            // Each hit's trace says why, as each step failing did.
            chosen.forEach(entry -> {
                final long[] ids = hitIds(entry.getValue());
                stepped.put(ids[0] + ":" + (ids[1] - 1), e);
            });
        }
        final String own = element;
        final List<String> untraced = new ArrayList<>();
        for (final Map.Entry<Map<String, Object>, Map<String, String>> entry : chosen) {
            final Map<String, Object> check = entry.getKey();
            final long[] ids = hitIds(entry.getValue());
            final String key = ids[0] + ":" + (ids[1] - 1);
            if (!stepped.containsKey(key)) {
                final Map<String, Object> trace = new LinkedHashMap<>();
                trace.put("traced", null);
                trace.put("stream", ids[0]);
                trace.put("event", ids[1]);
                trace.put("why", "not traced: no time left");
                check.put("trace", trace);
                check.put("pass", false);
                untraced.add(String.valueOf(Objects.requireNonNullElse(check.get("_key"), check.get("check"))));
            } else if (stepped.get(key) instanceof final RuntimeException failed) {
                final Map<String, Object> trace = new LinkedHashMap<>();
                trace.put("traced", false);
                trace.put("stream", ids[0]);
                trace.put("event", ids[1]);
                trace.put("why", "stepping record index " + (ids[1] - 1) + " of stream " + ids[0] + " failed: "
                                 + NullSafe.nonBlankStringElse(failed.getMessage(), failed.getClass().getSimpleName()));
                check.put("trace", trace);
                check.put("pass", false);
            } else {
                final Object held = stepped.get(key);
                final Map<String, Object> trace = traced(entry.getValue(), spec(check),
                        (stream, record) -> held == NOT_FOUND
                                ? null
                                : (Stepped) held, own);
                check.put("trace", trace);
                check.put("pass", Boolean.TRUE.equals(check.get("pass")) && Boolean.TRUE.equals(trace.get("traced")));
            }
        }
        return untraced;
    }

    // Every chosen hit's record stepped, a stepping session per stream.
    private void stepAll(final ToolCallContext context,
                         final SteppedPipeline pipeline,
                         final List<Map.Entry<Map<String, Object>, Map<String, String>>> chosen,
                         final String element,
                         final Map<String, Object> stepped,
                         final BooleanSupplier outOfTime) {
        final Map<Long, Set<Long>> wanted = new LinkedHashMap<>();
        for (final Map.Entry<Map<String, Object>, Map<String, String>> entry : chosen) {
            final long[] ids = hitIds(entry.getValue());
            wanted.computeIfAbsent(ids[0], stream -> new TreeSet<>()).add(ids[1] - 1);
        }
        final long total = wanted.values().stream().mapToLong(Set::size).sum();
        final CallProgress progress = CallProgress.of(context);
        int n = 0;
        for (final Map.Entry<Long, Set<Long>> stream : wanted.entrySet()) {
            try (final Walk walk = stepping.open(pipeline, stream.getKey(), null)) {
                for (final long record : stream.getValue()) {
                    if (outOfTime.getAsBoolean()) {
                        break;
                    }
                    n++;
                    progress.say("Tracing hits back to their records: record " + record + " of stream "
                                 + stream.getKey() + " (" + n + " of " + total + ")");
                    final String key = stream.getKey() + ":" + record;
                    try {
                        final SteppingResult result = walk.refresh(new StepLocation(stream.getKey(), 0L, record));
                        stepped.put(key, PipelineStepping.isFound(result)
                                ? new Stepped(NullSafe.get(result.getFoundLocation(), StepLocation::getRecordIndex),
                                SteppingWalks.output(result, element))
                                : NOT_FOUND);
                    } catch (final ToolTimeoutException e) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw e;
                        }
                        // The step outlasted the call's time: the rest are left untraced.
                        return;
                    } catch (final RuntimeException e) {
                        stepped.put(key, e);
                    }
                }
            }
        }
    }

    /// A hit back to its record: the indexing pipeline stepped at the hit's StreamId and EventId (the record's
    /// number in its stream, from 1: record index EventId - 1), and the document it writes there read. The hit is
    /// traced when that document holds the value searched for, and is the hit's: its EventId the hit's, or, when
    /// stepping numbers it otherwise, the only document the record gives, holding what the hit's other columns
    /// show. Stepping one record may not number it as processing did: Stroom's IdEnrichmentFilter counts the
    /// Events it reads, and seen on the live Stroom (verify_index), every record stepped alone was numbered 1
    /// (EventId 1 traced, every other hit failed, and the agent told the user the tracer was off by one).
    ///
    /// @param row     The hit, by column.
    /// @param check   What was searched: `field`, `condition`, `value` (none for the documents check).
    /// @param step    Steps the record at a stream's record index, giving null when Stroom finds none.
    /// @param element The element running the pipeline's own XSLT, where its documents are written.
    /// @return `{traced, stream, event, stepped_event_id?, matched_on?, why?}`.
    static Map<String, Object> traced(final Map<String, String> row,
                                      final Map<String, String> check,
                                      final Stepper step,
                                      final String element) {
        final long[] ids = hitIds(row);
        final Map<String, Object> out = new LinkedHashMap<>();
        if (ids == null) {
            out.put("traced", false);
            out.put("why", "the hit has no StreamId and EventId to go back with");
            return out;
        }
        final long stream = ids[0];
        final long event = ids[1];
        final Stepped stepped = step.step(stream, event - 1);
        final String at = "record index " + (event - 1) + " of stream " + stream + " (the hit's EventId " + event
                          + ", counted from 1)";
        out.put("traced", false);
        out.put("stream", stream);
        out.put("event", event);
        if (stepped == null) {
            out.put("why", "Stroom found no record at " + at);
            return out;
        }
        if (stepped.record() != null && stepped.record() != event - 1) {
            out.put("why", "asked to step " + at + ", Stroom stepped record index " + stepped.record());
            return out;
        }
        final String output = NullSafe.string(stepped.output());
        final List<Map<String, List<String>>> documents = output.isEmpty()
                ? List.of()
                : IndexFieldDocs.indexDocuments(List.of(output));
        Map<String, List<String>> document = documents.stream()
                .filter(doc -> eventIds(doc).contains(String.valueOf(event)))
                .findFirst()
                .orElse(null);
        if (document == null && documents.size() == 1) {
            document = documents.getFirst();
            final String numbered = eventIds(document).isEmpty()
                    ? null
                    : eventIds(document).getFirst();
            out.put("stepped_event_id", numbered);
            // Matched on what the hit shows instead: each column the document also has, compared as the values
            // are.
            final List<String> agreed = new ArrayList<>();
            for (final Map.Entry<String, String> column : row.entrySet()) {
                final List<String> values = document.get(column.getKey());
                if (IDS.contains(column.getKey()) || NullSafe.isEmptyString(column.getValue())
                    || NullSafe.isEmptyCollection(values)) {
                    continue;
                }
                final List<Boolean> same = values.stream().map(value -> sameValue(column.getValue(), value))
                        .toList();
                if (same.stream().allMatch(Boolean.FALSE::equals)) {
                    out.put("why", "stepping " + at + " gives one document, numbered EventId " + pyStr(numbered)
                                   + " by stepping, and it isn't the hit's: its " + column.getKey() + " is "
                                   + PythonRepr.list(values) + ", where the hit has " + column.getValue());
                    return out;
                }
                if (same.contains(Boolean.TRUE)) {
                    agreed.add(column.getKey());
                }
            }
            if (agreed.isEmpty()) {
                final Set<String> others = new TreeSet<>(row.keySet());
                others.removeAll(IDS);
                out.put("why", "stepping " + at + " gives one document, numbered EventId " + pyStr(numbered)
                               + " by stepping, and none of the hit's columns (" + PythonRepr.list(others) + ") has "
                               + "a value to match it on");
                return out;
            }
            out.put("matched_on", agreed);
        }
        if (document == null) {
            out.put("why", "stepping " + at + " with element " + pyStr(element) + " gives " + (documents.isEmpty()
                    ? "no document"
                    : documents.size() + " documents, EventIds " + PythonRepr.list(documents.stream()
                            .flatMap(doc -> eventIds(doc).stream()).toList()) + ", none " + event));
            return out;
        }
        final String field = check.get("field");
        final String value = NullSafe.string(check.get("value"));
        // A pattern (wildcard, or a CIDR range on an ip field) only needs the hit's record; a plain value must be
        // there.
        final List<String> held = document.get(field);
        final boolean holds = !"EQUALS".equals(NullSafe.nonBlankStringElse(check.get("condition"), "EQUALS"))
                              || value.contains("*") || value.contains("?") || value.contains("/")
                              || field == null || held == null
                              || held.stream().anyMatch(v -> v.toLowerCase(Locale.ROOT)
                .contains(value.toLowerCase(Locale.ROOT)));
        out.put("traced", holds);
        if (!holds) {
            out.put("why", "the document stepping " + at + " gives has " + field + " = " + PythonRepr.list(held)
                           + ", not " + value);
        }
        return out;
    }

    /// Whether a value a dashboard shows is one a document was written with: as times, as numbers, else as text
    /// (either holding the other: a text field shows its words). Null when they can't be compared (one a time, the
    /// other not): a dashboard formats some values its own way.
    ///
    /// @param shown   The value a dashboard shows.
    /// @param written A value the document was written with.
    /// @return True, false, or null when they can't be compared.
    static Boolean sameValue(final String shown, final String written) {
        final Boolean same = whole(shown, written);
        // A field of several values, which a dashboard shows separated by commas ("80, 443"): the written value is
        // the hit's when it is one of them (asked about by the user; as a whole, numbers and times couldn't be
        // compared).
        final List<String> parts = java.util.Arrays.stream(shown.split(",", -1))
                .map(part -> stripChars(part, " []"))
                .filter(part -> !part.isEmpty())
                .toList();
        if (!Boolean.TRUE.equals(same) && parts.size() > 1) {
            final List<Boolean> each = parts.stream().map(part -> whole(part, written)).toList();
            if (each.contains(Boolean.TRUE)) {
                return true;
            }
            if (same == null && each.stream().allMatch(Boolean.FALSE::equals)) {
                return false;
            }
        }
        return same;
    }

    private static Boolean whole(final String shown, final String written) {
        final Instant shownTime = instant(shown, false);
        final Instant writtenTime = instant(written, false);
        if (shownTime != null && writtenTime != null) {
            return shownTime.equals(writtenTime);
        }
        final Double shownNumber = number(shown);
        final Double writtenNumber = number(written);
        if (shownNumber != null && writtenNumber != null) {
            return shownNumber.equals(writtenNumber);
        }
        if (shownTime != null || writtenTime != null || shownNumber != null || writtenNumber != null) {
            return null;
        }
        final String a = shown.toLowerCase(Locale.ROOT);
        final String b = written.toLowerCase(Locale.ROOT);
        return b.contains(a) || a.contains(b);
    }

    /// @param row A hit, by column.
    /// @return Its StreamId and EventId, or null if it hasn't both as numbers.
    static long[] hitIds(final Map<String, String> row) {
        if (row == null) {
            return null;
        }
        try {
            return new long[]{(long) Double.parseDouble(row.get("StreamId")),
                    (long) Double.parseDouble(row.get("EventId"))};
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// @param pipeline     The indexing pipeline.
    /// @param pipelineUuid Its UUID.
    /// @return The element running the pipeline's own XSLT (where its documents are written), or null.
    static String ownXsltElement(final SteppedPipeline pipeline, final String pipelineUuid) {
        return pipeline.merged().translationDocs(pipelineUuid).stream()
                .filter(doc -> XsltDoc.TYPE.equals(doc.doc().getType()) && !doc.inheritedFromTemplate())
                .map(TranslationDoc::element)
                .findFirst()
                .orElse(null);
    }

    // --------------------------------------------------------------------------------
    // Searching.
    // --------------------------------------------------------------------------------

    private Found search(final ToolCallContext context,
                         final Dashboard dashboard,
                         final ExpressionOperator expression,
                         final int length,
                         final String doing) {
        return indexSearches.search(context, dashboard.probe(expression), length, doing);
    }

    private Counted count(final ToolCallContext context,
                          final Dashboard dashboard,
                          final ExpressionOperator expression,
                          final String doing) {
        return indexSearches.count(context, dashboard.probe(expression), doing);
    }

    /// @param streamIds Streams.
    /// @return An expression for their documents: StreamId EQUALS each, ORed.
    static ExpressionOperator byStream(final List<Long> streamIds) {
        final ExpressionOperator.Builder streams = ExpressionOperator.builder().op(Op.OR);
        streamIds.forEach(id -> streams.addTerm(term("StreamId", Condition.EQUALS, String.valueOf(id))));
        return streams.build();
    }

    private static ExpressionOperator of(final List<Long> streamIds) {
        return of(streamIds, List.of());
    }

    private static ExpressionOperator of(final List<Long> streamIds, final List<ExpressionTerm> terms) {
        final ExpressionOperator.Builder and = ExpressionOperator.builder().addOperator(byStream(streamIds));
        terms.forEach(and::addTerm);
        return and.build();
    }

    private static ExpressionOperator scoped(final ExpressionOperator byStream, final ExpressionItem term) {
        return ExpressionOperator.builder().addOperator(byStream).addItem(term).build();
    }

    /// @param field     A field.
    /// @param condition A condition.
    /// @param value     A value.
    /// @return The term.
    static ExpressionTerm term(final String field, final Condition condition, final String value) {
        return ExpressionTerm.builder().field(field).condition(condition).value(value).build();
    }

    private static Map<String, String> spec(final String field, final String condition, final String value) {
        final Map<String, String> spec = new LinkedHashMap<>();
        spec.put("field", field);
        spec.put("condition", condition);
        spec.put("value", value);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> spec(final Map<String, Object> check) {
        return check.get("_spec") instanceof final Map<?, ?> spec
                ? (Map<String, String>) spec
                : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> rows(final Map<String, Object> check) {
        return check.get("_rows") instanceof final List<?> rows
                ? (List<Map<String, String>>) rows
                : List.of();
    }

    private static List<String> eventIds(final Map<String, List<String>> document) {
        return NullSafe.list(document.get("EventId")).stream().map(id -> id.split("\\.", -1)[0]).toList();
    }

    private static Sort newestFirst() {
        return new Sort(0, SortDirection.DESCENDING);
    }

    private static TabLayoutConfig pane(final String componentId, final int width, final int height) {
        return TabLayoutConfig.builder()
                .preferredSize(new Size(width, height))
                .tabs(List.of(new TabConfig(componentId, true)))
                .selected(0)
                .build();
    }

    // A time as Python's datetime.fromisoformat reads it, with 'Z' as UTC; one without an offset only when
    // naiveAsUtc (window_start), as Python's timestamp() reads a naive time in the server's own zone.
    private static Instant instant(final String text, final boolean naiveAsUtc) {
        if (text == null) {
            return null;
        }
        final String value = text.strip();
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (final DateTimeParseException e) {
            if (!naiveAsUtc) {
                return null;
            }
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
        } catch (final DateTimeParseException e) {
            try {
                return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (final DateTimeParseException notADate) {
                return null;
            }
        }
    }

    // A number as Python's float() reads it, or null.
    private static Double number(final String text) {
        final String value = text.strip();
        if (value.isEmpty() || value.matches(".*[dDfFxX].*") && !value.toLowerCase(Locale.ROOT).contains("inf")) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace("inf", "Infinity").replace("nan", "NaN"));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static String stripChars(final String text, final String chars) {
        int start = 0;
        int end = text.length();
        while (start < end && chars.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end);
    }

    private static String pyStr(final Object value) {
        return value == null
                ? "None"
                : String.valueOf(value);
    }

    private static void sleep(final Duration wait) {
        PipelineStepping.timeIsUp();
        try {
            Thread.sleep(wait.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolTimeoutException();
        }
    }


    // --------------------------------------------------------------------------------


    /// Waits while the index catches up.
    @FunctionalInterface
    interface Sleeper {

        /// @param wait How long.
        void sleep(Duration wait);
    }

    /// Steps one record for a trace.
    @FunctionalInterface
    interface Stepper {

        /// @param stream The stream.
        /// @param record The record's index in it, from 0.
        /// @return The step, or null if Stroom found no record there.
        Stepped step(long stream, long record);
    }

    /// One record stepped.
    ///
    /// @param record The record index Stroom stepped, or null if it doesn't say.
    /// @param output The output of the element running the pipeline's own XSLT.
    record Stepped(Long record, String output) {

    }

    /// One search, as a person would make it on a dashboard.
    ///
    /// @param field      The index's field.
    /// @param condition  One of [#CONDITIONS].
    /// @param value      BETWEEN: 'from,to'; IN: values separated by commas; EQUALS takes * wildcards.
    /// @param expected   Rows it must return; null: at least one; 0: none (e.g. another case).
    /// @param searchedAs How it was searched in place of what was asked, or null.
    record SearchCheck(String field, String condition, String value, Long expected, String searchedAs) {

        /// @param given A search as JSON: `{field, condition?, value?, expected?}`.
        /// @return It.
        /// @throws ToolRefusal if it has no field, or a condition a dashboard doesn't take.
        static SearchCheck of(final Object given) {
            if (!(given instanceof final Map<?, ?> map) || !(map.get("field") instanceof final String field)
                || field.isEmpty()) {
                throw new ToolRefusal("Each search needs its field: {\"field\": ..., \"condition\": ..., "
                                      + "\"value\": ..., \"expected\": ...}");
            }
            final String condition = map.get("condition") == null
                    ? "EQUALS"
                    : String.valueOf(map.get("condition")).strip().toUpperCase(Locale.ROOT);
            if (!CONDITIONS.contains(condition)) {
                throw new ToolRefusal("search " + field + ": condition '" + map.get("condition") + "' is not one a "
                                      + "dashboard takes: " + String.join(", ", CONDITIONS));
            }
            final Object expected = map.get("expected");
            final Long count;
            if (expected == null) {
                count = null;
            } else if (expected instanceof final Number number) {
                count = number.longValue();
            } else {
                try {
                    count = Long.parseLong(String.valueOf(expected).strip());
                } catch (final NumberFormatException e) {
                    throw new ToolRefusal("search " + field + ": expected is a whole number of rows, not '"
                                          + expected + "'");
                }
            }
            return new SearchCheck(field, condition, map.get("value") == null
                    ? ""
                    : String.valueOf(map.get("value")), count, null);
        }
    }

    /// A verification dashboard, as its searches read it: the index its query is on, and its table.
    ///
    /// @param uuid       The dashboard's UUID (a new one for an unsaved dashboard).
    /// @param name       Its name.
    /// @param source     The index doc its query is on.
    /// @param columns    Its table's columns.
    /// @param maxResults The most rows its table keeps.
    record Dashboard(String uuid, String name, DocRef source, List<Column> columns, int maxResults) {

        /// @param uuid   The dashboard's UUID.
        /// @param name   Its name.
        /// @param config Its settings.
        /// @return It, as its searches read it.
        static Dashboard of(final String uuid, final String name, final DashboardConfig config) {
            DocRef source = null;
            List<Column> columns = List.of();
            int maxResults = 1000;
            for (final ComponentConfig component : NullSafe.list(config.getComponents())) {
                if (component.getSettings() instanceof final QueryComponentSettings query && source == null) {
                    source = query.getDataSource();
                } else if (component.getSettings() instanceof final TableComponentSettings table) {
                    columns = NullSafe.list(table.getColumns());
                    if (!NullSafe.isEmptyCollection(table.getMaxResults())) {
                        maxResults = table.getMaxResults().getFirst().intValue();
                    }
                }
            }
            return new Dashboard(uuid, name, source, columns, maxResults);
        }

        /// @param name   Its name.
        /// @param config Its settings.
        /// @return An unsaved dashboard, under a new UUID.
        static Dashboard unsaved(final String name, final DashboardConfig config) {
            return of(UUID.randomUUID().toString(), name, config);
        }

        /// @return It with its table unsorted, for checks: Stroom sorts a table itself, reading every match to do
        /// it, while an unsorted search stops once its page is full.
        Dashboard unsorted() {
            return new Dashboard(uuid, name, source, columns.stream()
                    .map(column -> column.getSort() == null
                            ? column
                            : column.copy().sort(null).build())
                    .toList(), maxResults);
        }

        /// @param expression The query's expression.
        /// @return The search it makes.
        Probe probe(final ExpressionOperator expression) {
            return new Probe(source, name, expression, columns, maxResults);
        }
    }

    /// What the user is shown of a verification dashboard before it is made or changed.
    ///
    /// @param fields    The table's columns.
    /// @param timeField The index's time field, or null.
    /// @param window    Where the query's time range starts, or null.
    /// @return The design, as the confirmation's details.
    static Map<String, Object> design(final List<String> fields,
                                      final String timeField,
                                      final String window) {
        final Map<String, Object> design = new LinkedHashMap<>();
        design.put("columns (newest first)", fields);
        final List<String> hidden = new ArrayList<>(IDS);
        if (timeField != null && !fields.contains(timeField)) {
            hidden.add(timeField);
        }
        design.put("hidden", hidden);
        design.put("initial query", window != null
                ? timeField + " from " + window + " through today"
                : "none");
        design.put("text pane", "the selected row's record, with stepping; no extraction pipeline");
        return design;
    }
}
