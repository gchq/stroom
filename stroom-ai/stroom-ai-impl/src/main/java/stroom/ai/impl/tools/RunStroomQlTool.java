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

import stroom.ai.api.ElasticEsql;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.EventsFile;
import stroom.ai.impl.workspace.MarkdownTables;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableProfiler;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@code run_stroomql} - runs a StroomQL query as the chat's user (so only data they can see), and writes the
 * results to the workspace as a table, giving the model a profile of it rather than the rows. It only reads, and
 * is limited in time and in the size of what it writes.
 */
class RunStroomQlTool extends AbstractWorkspaceTool {

    static final String NAME = "run_stroomql";
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RunStroomQlTool.class);

    static final String QUERY_ARG = "query";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private final Provider<QueryService> queryServiceProvider;
    private final Provider<StroomQlSources> stroomQlSourcesProvider;
    private final Provider<ResourceStore> resourceStoreProvider;
    private final AgentSearches agentSearches;
    private LuceneTextFields luceneTextFields;
    private Provider<ElasticEsql> elasticEsqlProvider;

    @Inject
    RunStroomQlTool(final ChatWorkspace workspace,
                    final Provider<QueryService> queryServiceProvider,
                    final Provider<StroomQlSources> stroomQlSourcesProvider,
                    final Provider<ResourceStore> resourceStoreProvider,
                    final AgentSearches agentSearches) {
        super(workspace);
        this.queryServiceProvider = queryServiceProvider;
        this.stroomQlSourcesProvider = stroomQlSourcesProvider;
        this.agentSearches = agentSearches;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    /// @param luceneTextFields A Lucene index's text fields' analyzers, for the misleading-search check (found
    ///                         reviewing the tools: without them, a CONTAINS on a keyword field ran, and found
    ///                         nothing).
    @Inject
    void setLuceneTextFields(final LuceneTextFields luceneTextFields) {
        this.luceneTextFields = luceneTextFields;
    }

    /// @param elasticEsqlProvider Runs ES|QL on an Elastic Index, for the hint that an aggregation there is
    ///                            better done by esql_query (with how many documents Stroom read for it).
    @Inject
    void setElasticEsql(final Provider<ElasticEsql> elasticEsqlProvider) {
        this.elasticEsqlProvider = elasticEsqlProvider;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .note("Use on the query's data source is checked by Stroom's query service when the search runs, "
                      + "as for any search.")
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It runs a search.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Runs a StroomQL query as the user (e.g. 'from \"My View\" where EventTime > "
                                 + "day() - 1d group by Host select Host, count() as N') and writes the results to a "
                                 + "table")
                        .chatOnly(" in the workspace")
                        .general(". You get the table's path and a profile of it (row count, columns, common "
                                 + "values)")
                        .chatOnly("; query it further with the table tool")
                        .general(". Use eval (e.g. 'eval Rate = Bytes / Duration') to work values out in the query, "
                                 + "in Stroom's expression language")
                        .chatOnly(", as the table tool's expressions are")
                        .general(". It is right for row-level results, Lucene indexes, views and other data sources, "
                                 + "and Stroom's own expression functions; on an Elasticsearch index, totals, top N "
                                 + "and grouped questions are answered faster by Elasticsearch with ES|QL")
                        .chatOnly(" (esql_query), as a StroomQL query reads every matching document through "
                                  + "Stroom to group them")
                        .general(". It can join Stroom data sources (join lines, or where X in (from ...)); join "
                                 + "kinds, keys and limits, and sequences: the joins guide (stroom://guide/joins")
                        .chatOnly(", or get_instructions guide=joins")
                        .general("). Add a limit to the query for large results. If more than one data source could "
                                 + "hold what the user asked for, ask them which to use")
                        .chatOnly(" with ask_user")
                        .general(" first; never choose one for them.")
                        .chatOnly(" If the user may instead want a Query document to run themselves "
                                  + "(propose_query), ask them which.")
                        .general(" A query on a Lucene or Elastic index whose answer through Stroom would mislead "
                                 + "(e.g. is null on Elasticsearch, which matches every document) is refused, "
                                 + "saying what to query instead. Stroom's warnings, a join's too, follow 'Stroom "
                                 + "warned:' in the result.")
                        .chatOnly(" To find an event the user describes in an index, with each hit's StreamId and "
                                  + "EventId and the streams holding every match, use search_events with index.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(QUERY_ARG, "The StroomQL query.")
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A .md file in /outputs/ for the results (default "
                                          + "/outputs/queries/query-<time>.md).")
                                .build())
                        .required(QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String given = rawArg(request, QUERY_ARG);
        if (given == null || given.isBlank()) {
            throw new WorkspaceException("Give a StroomQL query.");
        }
        final int maxQueryLength = limits(context).getMaxQueryLength();
        if (given.length() > maxQueryLength) {
            throw new WorkspaceException("The query may be at most " + maxQueryLength + " characters.");
        }
        // Never an unbounded search: the rows are capped, whatever the query asks for.
        final int maxRows = limits(context).getMaxQueryRows();
        final StroomQlLimit.Limited limited = StroomQlLimit.apply(given, maxRows);
        // The zone day(), a time without an offset and the results' times are in.
        final ToolTimeZone zone = timeZone(request, context);
        final long referenceTime = System.currentTimeMillis();
        final String query = StroomQlDatePoints.inZone(limited.query(), zone, referenceTime);
        // The times its conditions' date points stood for, said in the result: the Stroom UI works them out in UTC.
        final List<String> datePoints = StroomQlDatePoints.used(limited.query(), zone, referenceTime);
        final boolean changed = limited.change() != StroomQlLimit.Change.NONE || !query.equals(limited.query());
        final WorkspacePath givenOut = outArg(request);
        final WorkspacePath out = givenOut != null
                ? givenOut
                : workspace.newFile("/outputs/queries/query", ".md");
        final QueryService queryService = queryServiceProvider.get();
        final DocRef dataSource = queryService.getReferencedDataSource(query).orElse(null);
        final String sourceDocUuid = NullSafe.get(dataSource, DocRef::getUuid);
        final Optional<String> misleading = misleading(queryService, sources(query), source ->
                luceneTextFields == null
                        ? Map.of()
                        : luceneTextFields.of(source));
        if (misleading.isPresent()) {
            throw new WorkspaceException(misleading.get());
        }

        final QuerySearchRequest searchRequest = QuerySearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.API).build())
                .query(query)
                .queryContext(QueryContext.builder().dateTimeSettings(zone.dateTimeSettings(referenceTime)).build())
                .incremental(false)
                .timeout(timeLimit(context).toMillis())
                .storeHistory(false)
                .requestedRange(new OffsetRange(0L, 100L))
                .build();
        QueryKey queryKey = null;
        try {
            DashboardSearchResponse response = queryService.search(searchRequest);
            queryKey = NullSafe.get(response, DashboardSearchResponse::getQueryKey);
            // Whether the search started, so a failure says if it failed part way rather than before it began.
            boolean started = false;
            while (response != null && SearchProblems.errors(response).isEmpty() && !response.isComplete()) {
                started = true;
                pause();
                response = queryService.search(searchRequest.copy().queryKey(response.getQueryKey()).build());
            }
            return ToolResult.text(results(workspace, searchRequest, response, started, out, sourceDocUuid, given,
                    new Written(limited, changed, zone, datePoints), maxRows)
                                   + esqlHint(queryService, dataSource, given, context));
        } finally {
            // The search is the agent's own, and is never left running, e.g. when the time limit is reached.
            agentSearches.destroy(queryKey);
        }
    }

    // Every data source the query searches (the first, then each joined one), with what is pushed down to it; none
    // if it can't be read, as Stroom then refuses it with its own error when it runs.
    private List<StroomQlSources.Source> sources(final String query) {
        try {
            return stroomQlSourcesProvider.get().of(query);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read the query's data sources: {}", e.getMessage()), e);
            return List.of();
        }
    }

    /// The refusal for a query whose search of a Lucene or Elastic index would give a misleading answer through
    /// Stroom (e.g. IS_NULL on Elasticsearch matches every document, and a wildcard on an ip field finds nothing):
    /// it says what to query instead. Each data source the query searches is checked, with the conditions pushed
    /// down to it: the first source's, and each joined source's (a join line's, or a `where` subquery's).
    ///
    /// @param queryService Lists each index's fields.
    /// @param sources      The data sources the query searches ([StroomQlSources]).
    /// @param luceneText   A Lucene index's text fields and their analyzers ([LuceneTextFields]), or none, by data
    ///                     source.
    /// @return The refusal for the first source whose search would mislead, or empty if each can be trusted, isn't
    /// on an index, or can't be checked.
    static Optional<String> misleading(final QueryService queryService,
                                       final List<StroomQlSources.Source> sources,
                                       final Function<DocRef, Map<String, String>> luceneText) {
        for (final StroomQlSources.Source source : NullSafe.list(sources)) {
            final Optional<String> refusal = misleading(queryService, source.expression(), source.dataSource(),
                    luceneText.apply(source.dataSource()));
            if (refusal.isPresent()) {
                return refusal.map(text -> text + (source.joined()
                        ? ". They are in the search of the joined data source "
                          + DataSourceAccess.describe(source.dataSource())
                        : "") + ". In StroomQL, EQUALS is = and NOT_EQUALS is !=.");
            }
        }
        return Optional.empty();
    }

    /// The refusal for one data source's search, as [#misleading(QueryService, List, Function)] gives it, without
    /// its closing hint.
    ///
    /// @param queryService Lists the index's fields.
    /// @param expression   The conditions pushed down to the data source's search, or null for none.
    /// @param dataSource   The data source, or null.
    /// @param luceneText   A Lucene index's text fields and their analyzers ([LuceneTextFields]), or none.
    /// @return The refusal, or empty if the search can be trusted, isn't on an index, or can't be checked.
    static Optional<String> misleading(final QueryService queryService,
                                       final ExpressionOperator expression,
                                       final DocRef dataSource,
                                       final Map<String, String> luceneText) {
        final Optional<String> backend = MisleadingSearches.backendOf(dataSource);
        if (backend.isEmpty()) {
            return Optional.empty();
        }
        final List<MisleadingSearches.Search> searches;
        final List<QueryField> fields;
        try {
            searches = MisleadingSearches.searches(expression);
            if (searches.isEmpty()) {
                return Optional.empty();
            }
            fields = NullSafe.list(NullSafe.get(queryService.findFields(new FindFieldCriteria(
                    new PageRequest(0, IndexEvents.MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource,
                    null, null)), ResultPage::getValues));
        } catch (final RuntimeException e) {
            // A check on the query, not a reason for it not to run: its own errors are Stroom's to give.
            LOGGER.debug(() -> LogUtil.message("Unable to check the query's searches: {}", e.getMessage()), e);
            return Optional.empty();
        }
        return MisleadingSearches.refusal(backend.get(), searches, MisleadingSearches.ipFields(fields), List.of(),
                luceneText, MisleadingSearches.unindexed(dataSource, fields,
                        searches.stream().map(MisleadingSearches.Search::field).toList()));
    }

    // For an aggregation on an Elastic Index that translates exactly to ES|QL: the esql_query pipeline that gives the
    // same rows with the grouping done in Elasticsearch, and how many documents Stroom read to group them here.
    // Nothing for anything else, or if ES|QL can't run on the index's cluster.
    private String esqlHint(final QueryService queryService,
                            final DocRef dataSource,
                            final String query,
                            final ToolCallContext context) {
        if (elasticEsqlProvider == null || dataSource == null || !ElasticIndexDoc.TYPE.equals(dataSource.getType())) {
            return "";
        }
        final Map<String, FieldType> types = new HashMap<>();
        try {
            NullSafe.list(NullSafe.get(queryService.findFields(new FindFieldCriteria(new PageRequest(0,
                            IndexEvents.MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource, null, null)),
                    ResultPage::getValues)).forEach(field -> types.put(field.getFldName(), field.getFldType()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to list the index's fields: {}", e.getMessage()), e);
            return "";
        }
        final Optional<StroomQlToEsql.Translation> translation = StroomQlToEsql.translate(query, types);
        if (translation.isEmpty()) {
            return "";
        }
        String read = "";
        try {
            final String where = translation.get().where();
            final ElasticEsql.Result counted = elasticEsqlProvider.get().query(new ElasticEsql.Request(dataSource,
                    (where.isEmpty()
                            ? ""
                            : where + " ") + "| STATS documents = COUNT(*)", null, null, 1, timeLimit(context),
                    false));
            if (!counted.rows().isEmpty() && counted.rows().getFirst().getFirst() instanceof final Number number) {
                read = " Stroom read " + number.longValue() + " documents from Elasticsearch to group them here.";
            }
        } catch (final ElasticEsql.Refusal e) {
            if (e.reason() == ElasticEsql.Reason.CLUSTER_TOO_OLD) {
                return "";
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to count the documents read: {}", e.getMessage()), e);
        }
        return "\n\nThis is an aggregation on an Elasticsearch index." + read + " esql_query gives the same rows with "
               + "the grouping done in Elasticsearch: index '" + dataSource.getUuid() + "', pipeline '"
               + translation.get().pipeline() + "'.";
    }

    // The parser's error for the query as the agent wrote it, when what this tool changed (the limit, or a date
    // point put in the call's time zone) is in the one that failed: a query with group by after select fails at the
    // limit added before select, a token the agent never wrote (seen with Haiku in eval case 71). Empty if the query
    // ran as written, or the query as written parses.
    private static Optional<String> errorAsWritten(final QueryService queryService,
                                                   final String given,
                                                   final boolean changed) {
        if (!changed) {
            return Optional.empty();
        }
        final ValidateExpressionResult result = queryService.validateQuery(given);
        return result == null || result.isOk()
                ? Optional.empty()
                : Optional.ofNullable(NullSafe.nonBlankStringElse(result.getString(), null));
    }

    // Writes the results of a search that has finished to the workspace, and says what they hold.
    private String results(final SessionWorkspace workspace,
                           final QuerySearchRequest searchRequest,
                           final DashboardSearchResponse response,
                           final boolean started,
                           final WorkspacePath out,
                           final String sourceDocUuid,
                           final String given,
                           final Written written,
                           final int maxRows) {
        final StroomQlLimit.Limited limited = written.limited();
        final QueryService queryService = queryServiceProvider.get();
        if (response == null) {
            throw new WorkspaceException(started
                    ? "The query started, but Stroom's query service then gave no response."
                    : "The query did not run: Stroom's query service gave no response.");
        }
        final List<String> errors = SearchProblems.errors(response);
        if (!errors.isEmpty()) {
            throw new WorkspaceException(started
                    ? "The query started, but failed while it ran: " + String.join("; ", errors)
                    : "The query failed: " + StroomQlErrorHints.appendTo(errorAsWritten(queryService, given,
                            written.changed()).orElse(String.join("; ", errors)), given));
        }
        // Warnings don't stop the results being used, but say what to make of them (e.g. a join's row matching
        // very many rows).
        final List<String> warnings = SearchProblems.warnings(response);

        // The download takes the rows the request asks for, so it asks for all those the limit allows, not the few
        // the search was polled for.
        final ResourceGeneration generation = queryService.downloadSearchResults(new DownloadQueryResultsRequest(
                searchRequest.copy()
                        .queryKey(response.getQueryKey())
                        .requestedRange(new OffsetRange(0L, (long) maxRows))
                        .build(),
                // With each row's stream and event ids, so the events behind the rows can be fetched.
                DownloadSearchResultFileType.MARKDOWN, false, 100, true));
        final ResourceKey resourceKey = generation.getResourceKey();
        if (resourceKey == null) {
            throw new WorkspaceException("The query's results could not be written: " + generation.getMessageList());
        }
        final ResourceStore resourceStore = resourceStoreProvider.get();
        try {
            // A data source without streams gives empty ids, which only mislead.
            MarkdownTables.dropEmptyColumns(resourceStore.getTempFile(resourceKey), EventsFile.SOURCE_ID_COLUMNS);
            // Stroom gives a date field's values in UTC whatever the query's zone: they are shown in it.
            final ToolTimeZone zone = written.zone();
            if (!zone.isUtc()) {
                MarkdownTables.mapCells(resourceStore.getTempFile(resourceKey), zone::inZone);
            }
            workspace.write(out, resourceStore.getTempFile(resourceKey), sourceDocUuid);
        } finally {
            resourceStore.deleteTempFile(resourceKey);
        }
        try (final Reader reader = Files.newBufferedReader(workspace.localFile(out),
                StandardCharsets.UTF_8)) {
            final String profile = TableProfiler.profile(reader, out.path());
            final long rows = MarkdownTables.countRows(workspace.localFile(out));
            return "Ran the query. The results are in " + out + ". " + written.zone().stated()
                   + datePointsNote(written.datePoints()) + written.zone().formatDateNote(given)
                   + limitNote(limited, maxRows, rows)
                   + (warnings.isEmpty()
                    ? ""
                    : " Stroom warned: " + String.join("; ", warnings))
                   + "\n\n" + profile;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param datePoints The times the query's conditions' date points stood for ([StroomQlDatePoints#used]).
    /// @return What the result says of them, e.g. " In its conditions day() = 2026-11-21T00:00:00+11:00.", or
    /// nothing if there were none.
    static String datePointsNote(final List<String> datePoints) {
        return datePoints.isEmpty()
                ? ""
                : " In its conditions " + String.join(", ", datePoints) + ".";
    }

    /// @return What the model is told about the row limit: nothing if the query's own limit was used and the rows
    /// didn't reach the most allowed.
    static String limitNote(final StroomQlLimit.Limited limited, final int maxRows, final long rows) {
        final StringBuilder sb = new StringBuilder();
        if (limited.change() == StroomQlLimit.Change.ADDED) {
            sb.append(" The query had no limit, so 'limit ").append(maxRows).append("' (the most rows a query may "
                                                                                   + "return here) was added.");
        } else if (limited.change() == StroomQlLimit.Change.LOWERED) {
            sb.append(" The query's limit was over ").append(maxRows).append(", the most rows a query may return "
                                                                               + "here, so it was lowered to that.");
        }
        if (rows >= maxRows) {
            sb.append(" It returned ").append(maxRows).append(" rows, the most allowed, so there may be more: don't "
                                                              + "take the row count for a total. To count rows, "
                                                              + "use count() with group by (without group by, "
                                                              + "each row is its own group).");
        }
        return sb.toString();
    }

    private static void pause() {
        ToolDeadline.check();
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolTimeoutException();
        }
    }

    @Override
    public boolean readsUntrustedContent() {
        // Stroom data can hold text written by anyone.
        return true;
    }


    // --------------------------------------------------------------------------------


    // The query as run: its limit, whether it differs from the agent's, the time zone it ran in, and the times its
    // conditions' date points stood for.
    private record Written(StroomQlLimit.Limited limited, boolean changed, ToolTimeZone zone,
                           List<String> datePoints) {

    }
}
