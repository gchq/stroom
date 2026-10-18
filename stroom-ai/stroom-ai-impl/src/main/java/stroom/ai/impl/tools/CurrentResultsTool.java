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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TableDownloader;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.MarkdownTables;
import stroom.ai.impl.workspace.TableProfiler;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.TableResult;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `current_results` - a snapshot of the rows of a table of results shown in the user's active tab (a query's
/// results, or a table on a dashboard), taken when the model needs them, so the user never has to attach them. It
/// is taken through Stroom's own download of search results, as the user (so with their permissions), as an
/// attached table is, and kept as a hidden workspace file under `/tabs/results/`, with a profile.
///
/// Each snapshot records what it was taken from and how much it holds: a hash of the search request (the query,
/// or the dashboard's expression, and the parameters and time range), the time range, when it was taken, the rows
/// it holds, the rows the search found where known, whether the search had finished, and whether the global row
/// limit cut it. Each time it is used, it is compared with the tab as the user sees it now, and said to be stale if
/// the tab's search has changed, or incomplete if it may not hold everything asked for.
///
/// It never runs a search again unless refresh is given, which the agent is told to do only once the user has said
/// yes, as a search can be slow or costly. A snapshot of rows the user already has in their tab is taken again
/// (without searching) when the user has run the search again in the tab since.
class CurrentResultsTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CurrentResultsTool.class);

    static final String NAME = "current_results";
    static final String TABLE_ARG = "table";
    static final String ROWS_ARG = "rows";
    static final String REFRESH_ARG = "refresh";
    static final String WITH_EVENT_IDS_ARG = "withEventIds";
    /// What a snapshot of the events behind a table has at the end of its path, after the table's own.
    static final String EVENTS_SUFFIX = "-events";
    /// The names of the fields a data source must have for the events behind its rows to be fetched.
    static final List<String> EVENT_ID_FIELDS = List.of("StreamId", "EventId");
    /// The most fields read to tell whether a data source has the event id fields.
    static final int MAX_FIELDS = 10_000;
    static final String RESULTS_FOLDER = "/tabs/results/";
    static final String NO_TAB = "No tab with tables of results was shared with the user's latest question: ask "
                                 + "the user to open the query or dashboard and ask again.";
    // A snapshot's table, or the first rows of one: its path without the extension (and the scope) is its record's.
    private static final Pattern SNAPSHOT_FILE = Pattern.compile(
            "(" + Pattern.quote(RESULTS_FOLDER) + ".+?)(?:-first-\\d+)?\\.md");
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    /// The part of the time limit spent waiting for a search run again to finish, leaving time to download it.
    private static final double SEARCH_TIME_SHARE = 0.75;

    private final TabSnapshots tabSnapshots;
    private final TableDownloader tableDownloader;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<DashboardService> dashboardServiceProvider;
    private final Provider<TableAnalysisConfig> tableAnalysisConfigProvider;
    private final AiAuditLog auditLog;

    private final AgentSearches agentSearches;

    @Inject
    CurrentResultsTool(final ChatWorkspace workspace,
                       final TabSnapshots tabSnapshots,
                       final TableDownloader tableDownloader,
                       final DataSourceAccess dataSourceAccess,
                       final Provider<QueryService> queryServiceProvider,
                       final Provider<DashboardService> dashboardServiceProvider,
                       final Provider<TableAnalysisConfig> tableAnalysisConfigProvider,
                       final AiAuditLog auditLog,
                       final AgentSearches agentSearches) {
        super(workspace);
        this.agentSearches = agentSearches;
        this.tabSnapshots = tabSnapshots;
        this.tableDownloader = tableDownloader;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.dashboardServiceProvider = dashboardServiceProvider;
        this.tableAnalysisConfigProvider = tableAnalysisConfigProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Takes a snapshot of the rows of a table of results shown in the user's active tab "
                             + "(a query's results, or a table on a dashboard) as a workspace table, and gives its "
                             + "path, a profile, and its state: ok, stale (the tab's query, expression, parameters "
                             + "or time range have changed since) or incomplete (the search hadn't finished, the "
                             + "row limit cut it, or it holds fewer rows than asked for while more exist). Asking "
                             + "again reuses the snapshot. Never pass refresh unless the user has said yes: it runs "
                             + "the search again, which can be slow. Give withEventIds for the events behind the "
                             + "rows (e.g. for fetch_events and search_events): each row then has its event's "
                             + "StreamId and EventId, and a grouped dashboard table is taken ungrouped, one row per "
                             + "event, by running its search again separately (so with refresh, only with the "
                             + "user's yes). It can also be the first step of a pipeline.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(TABLE_ARG, "Optional. The table's name as the user sees it, for a "
                                                      + "dashboard with several tables.")
                        .addIntegerProperty(ROWS_ARG, "Optional. Only the first n rows, when the user asks about "
                                                      + "a scope like that.")
                        .addBooleanProperty(REFRESH_ARG, "Optional. Run the search again, as the tab now stands, "
                                                         + "and take a new snapshot. Only with the user's yes.")
                        .addBooleanProperty(WITH_EVENT_IDS_ARG, "Optional. Take each row's StreamId and EventId, "
                                                                + "one row per event, for the events behind the "
                                                                + "table.")
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit() {
        return limits().getQueryTimeLimit().getDuration();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        return outcome(request, chatId).text();
    }

    /// Takes a snapshot of a table's rows, or reuses the one already taken, as the tool does.
    ///
    /// @param request The model's call, or a pipeline step's.
    /// @param chatId  The chat.
    /// @return What the model is told, and the snapshot's workspace file, which is null if no snapshot could be
    /// used (the text then says why).
    /// @throws WorkspaceException for arguments the model can correct.
    Outcome outcome(final ToolExecutionRequest request, final int chatId) {
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        if (tab == null || NullSafe.isEmptyCollection(tab.searches())) {
            return Outcome.problem(tab == null
                    ? NO_TAB
                    : "The user's active tab, " + ReadDocumentTool.describe(tab.docRef())
                      + ", shows no tables of results.");
        }
        final AiTabSearch search = chooseTable(tab.searches(), stringArg(request, TABLE_ARG));
        if (search == null) {
            final StringBuilder sb = new StringBuilder("Say which table: ");
            for (final AiTabSearch each : tab.searches()) {
                sb.append("'").append(name(each)).append("' ");
            }
            return Outcome.problem(sb.toString().strip() + ".");
        }
        final int limit = Math.max(1, NullSafe.getOrElse(tableAnalysisConfigProvider.get(),
                TableAnalysisConfig::getMaxTotalRows, TableAnalysisConfig.DEFAULT_MAX_TOTAL_ROWS));
        final Integer rows = intArg(request, ROWS_ARG);
        if (rows != null && rows < 1) {
            throw new WorkspaceException("Give rows as a whole number of at least 1.");
        }
        if (rows != null && rows > limit) {
            return Outcome.problem("Refused: a snapshot can hold at most " + limit + " rows (the global row "
                                   + "limit), so the first " + rows + " can't be taken. Offer the user the first "
                                   + limit + " instead.");
        }
        final boolean refresh = booleanArg(request, REFRESH_ARG, false);
        final boolean withEventIds = booleanArg(request, WITH_EVENT_IDS_ARG, false);
        if (withEventIds && needsEventSearch(search)) {
            return eventsOutcome(chatId, tab, search, rows, limit, refresh);
        }

        final String base = basePath(tab.docRef(), search);
        final ResultsMeta existing = readMeta(chatId, base).orElse(null);
        final String tabQueryKey = NullSafe.get(ResultsRequests.queryKey(search.getResults()), QueryKey::getUuid);
        final boolean take = refresh
                             || existing == null
                             || !Objects.equals(existing.tabQueryKey(), tabQueryKey);
        final ResultsMeta meta;
        if (take) {
            final Taken taken = take(chatId, tab, search, base, rows, limit, refresh, tabQueryKey, false);
            if (taken.problem() != null) {
                return Outcome.problem(taken.problem());
            }
            meta = taken.meta();
        } else {
            meta = existing;
        }
        final String liveHash = ResultsRequests.hash(search.getLive());
        final Assessment assessment = assess(meta, rows, liveHash, limit);
        final String path = rows != null && rows < meta.rowsHeld()
                ? scopedCopy(chatId, tab.docRef(), base, meta, rows)
                : meta.path();
        final String text = describe(meta, assessment, path, rows, take, ResultsRequests.changes(meta.request(),
                search.getLive()), readProfile(chatId, path));
        return new Outcome(withEventIds
                ? text + "\n" + eventIdsNote(chatId, path)
                : text, path);
    }

    /// @return True if the events behind a table's rows can only be had by running its search again: a dashboard
    /// table that is grouped, or doesn't keep each row's stream and event ids.
    private static boolean needsEventSearch(final AiTabSearch search) {
        final AskStroomAiContext request = liveOrResults(search);
        return request instanceof DashboardTableContext
               && (ResultsRequests.isGrouped(request) || !ResultsRequests.hasEventIdColumns(request));
    }

    // The table's search request as the tab now stands, else the one behind the rows shown, or null.
    private static AskStroomAiContext liveOrResults(final AiTabSearch search) {
        return search.getLive() != null
                ? search.getLive()
                : search.getResults();
    }

    // Says whether a snapshot's rows have the ids of their events, for fetch_events.
    private String eventIdsNote(final int chatId, final String path) {
        final List<String> header = header(chatId, path);
        final boolean hasIds = EVENT_ID_FIELDS.stream().allMatch(id -> header.stream()
                .anyMatch(column -> column.equalsIgnoreCase(id)));
        return hasIds
                ? "Each row has its event's StreamId and EventId, so fetch_events can fetch the events behind them."
                : "These rows have no StreamId and EventId, so the events behind them can't be fetched. For a "
                  + "query, suggest the user adds them to its select (without grouping), as StroomQL to copy.";
    }

    // A snapshot of the events behind a dashboard table: its search run again, ungrouped, with each event's ids.
    private Outcome eventsOutcome(final int chatId,
                                  final StoredTabSnapshot tab,
                                  final AiTabSearch search,
                                  final Integer rows,
                                  final int limit,
                                  final boolean refresh) {
        final AskStroomAiContext request = liveOrResults(search);
        final DocRef dataSource = dataSource(request);
        if (dataSource == null || !dataSourceAccess.canUse(dataSource)) {
            return Outcome.problem("You can't use the data source these results come from, so they can't be "
                                   + "read.");
        }
        final Boolean stored = storesEventIds(dataSource);
        if (stored == null) {
            return Outcome.problem("It couldn't be told whether " + ReadDocumentTool.describe(dataSource)
                                   + " stores each event's StreamId and EventId, so the events behind '"
                                   + name(search) + "' can't be fetched.");
        }
        if (!stored) {
            return Outcome.problem(ReadDocumentTool.describe(dataSource) + " doesn't store each event's StreamId "
                                   + "and EventId, so the events behind '" + name(search) + "' can't be fetched. "
                                   + "Answer from the table's own columns, or suggest a column from the "
                                   + "dashboard's extraction pipeline or a query on an indexed field, as text to "
                                   + "copy.");
        }
        final String base = basePath(tab.docRef(), search) + EVENTS_SUFFIX;
        final ResultsMeta existing = readMeta(chatId, base).orElse(null);
        final boolean grouped = ResultsRequests.isGrouped(request);
        if (existing == null && !refresh) {
            return Outcome.problem("Getting the events behind '" + name(search) + "' means running its search "
                                   + "again, separately (the user's dashboard isn't changed): "
                                   + (grouped
                    ? "ungrouped, one row per event, "
                    : "") + "with each event's StreamId and EventId. As a search can be slow, ask the user "
                                   + "(ask_user) and, with their yes, call again with withEventIds and refresh.");
        }
        final ResultsMeta meta;
        if (refresh) {
            final Taken taken = take(chatId, tab, search, base, rows, limit, true, null, true);
            if (taken.problem() != null) {
                return Outcome.problem(taken.problem());
            }
            meta = taken.meta();
        } else {
            meta = existing;
        }
        final Assessment assessment = assess(meta, rows, ResultsRequests.hash(search.getLive()), limit);
        final String path = rows != null && rows < meta.rowsHeld()
                ? scopedCopy(chatId, tab.docRef(), base, meta, rows)
                : meta.path();
        final StringBuilder sb = new StringBuilder(describe(meta, assessment, path, rows, refresh,
                ResultsRequests.changes(meta.request(), search.getLive()), readProfile(chatId, path)));
        final long events = Objects.requireNonNullElse(meta.totalRows(), meta.rowsHeld());
        sb.append("\nThis is the table's search run again separately, ungrouped: one row per event, with each "
                  + "event's StreamId and EventId, for fetch_events");
        if (meta.groups() != null) {
            sb.append(" (the ").append(events).append(" event(s) behind the table's ").append(meta.groups())
                    .append(" group(s))");
        }
        sb.append(". Say how many events the answer covers.");
        return new Outcome(sb.toString(), path);
    }

    // True if a data source has the event id fields, false if it doesn't, or null if it can't be told.
    private Boolean storesEventIds(final DocRef dataSource) {
        try {
            final ResultPage<QueryField> fields = queryServiceProvider.get().findFields(new FindFieldCriteria(
                    new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource, null, null));
            final List<String> names = NullSafe.list(NullSafe.get(fields, ResultPage::getValues)).stream()
                    .map(QueryField::getFldName)
                    .filter(Objects::nonNull)
                    .toList();
            return EVENT_ID_FIELDS.stream().allMatch(id -> names.stream().anyMatch(id::equalsIgnoreCase));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the fields of " + dataSource, e);
            return null;
        }
    }

    // The column names of a snapshot's Markdown table.
    private List<String> header(final int chatId, final String path) {
        try (final BufferedReader reader = Files.newBufferedReader(workspace.localFile(chatId,
                WorkspacePath.parse(path)), StandardCharsets.UTF_8)) {
            final String line = reader.readLine();
            return line == null
                    ? List.of()
                    : MarkdownTables.parseRow(line);
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the header of " + path, e);
            return List.of();
        }
    }

    // Takes a snapshot of a table's rows: those the tab shows, or, with refresh, those of the search run again.
    private Taken take(final int chatId,
                       final StoredTabSnapshot tab,
                       final AiTabSearch search,
                       final String base,
                       final Integer rows,
                       final int limit,
                       final boolean refresh,
                       final String tabQueryKey,
                       final boolean events) {
        AskStroomAiContext source = refresh
                ? search.getLive()
                : search.getResults();
        if (events && source instanceof final DashboardTableContext dashboard) {
            // One row per event, with its ids, as a search of its own.
            source = ResultsRequests.ungroupedWithEventIds(dashboard);
        }
        if (source == null) {
            return Taken.problem(refresh
                    ? "The search can't be run again, as the tab's search request isn't known."
                    : "The search in '" + name(search) + "' hasn't been run, so there are no results to take. "
                      + "Offer the user to run it (refresh), with ask_user.");
        }
        final DocRef dataSource = dataSource(source);
        if (dataSource == null || !dataSourceAccess.canUse(dataSource)) {
            return Taken.problem("You can't use the data source these results come from, so they can't be read.");
        }
        final SearchStatus status;
        if (refresh) {
            final Ran ran = runAgain(source);
            if (ran.error() != null) {
                return Taken.problem("The search failed when run again: " + ran.error());
            }
            source = ran.context();
            status = ran.status();
        } else {
            status = status(source);
        }
        final long wanted = rows != null
                ? rows
                : limit;
        final Path temp = workspace.tempFile();
        final TableDownloader.Downloaded downloaded;
        try {
            try {
                downloaded = tableDownloader.download(source, wanted, temp);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to take a snapshot of " + name(search), e);
                return Taken.problem("The results couldn't be read (" + NullSafe.nonBlankStringElse(e.getMessage(),
                        e.getClass().getSimpleName()) + "). Stroom may no longer hold them, e.g. if the tab was "
                                     + "closed or the search run again; offer the user to run the search again "
                                     + "(refresh).");
            } finally {
                if (refresh) {
                    // The search this tool ran again is stopped once its rows are taken (or can't be), keeping
                    // what it found, so it never runs on after the call.
                    agentSearches.terminate(status.queryKey());
                }
            }
            final String sourceUuid = dataSource.getUuid();
            workspace.writeTabFile(chatId, WorkspacePath.parse(base + ".md"), temp, sourceUuid);
        } finally {
            ChatWorkspace.deleteQuietly(temp);
        }
        final ResultsMeta meta = new ResultsMeta(tab.docRef(), name(search), search.getComponentId(),
                base + ".md", ResultsRequests.hash(source), ResultsRequests.timeRange(source),
                System.currentTimeMillis(), downloaded.rows(), wanted, downloaded.truncated(),
                status.totalRows(), status.complete(), wanted == limit && downloaded.truncated(), refresh,
                tabQueryKey, source, events && ResultsRequests.isGrouped(search.getLive())
                ? search.getVisibleRows()
                : null);
        writeProfile(chatId, meta.path(), describe(tab, search), dataSource.getUuid());
        workspace.writeTabFile(chatId, WorkspacePath.parse(base + ".json"), JsonUtil.writeValueAsString(meta),
                dataSource.getUuid());
        auditLog.searched(chatId, dataSource, ResultsRequests.queryText(source), downloaded.rows(),
                (events
                        ? "Search run again ungrouped, and a snapshot taken of the events behind its rows"
                        : (refresh
                                ? "Search run again, and a snapshot of its results taken"
                                : "Snapshot taken of the results shown")) + " for " + describe(tab, search) + " by the "
                + NAME + " tool");
        return new Taken(meta, null);
    }

    /// Says how much of a table's search a snapshot of its rows covers, for the tools that work on its rows (e.g.
    /// fetch_events), so they can say an answer is "of the first N of M".
    ///
    /// @param workspace The chat's workspace.
    /// @param chatId    The chat.
    /// @param table     A table file, which may be a snapshot this tool took (or the first rows of one).
    /// @param rowsUsed  How many of its rows were used.
    /// @return What it covers, as a sentence for the model, if the file is a snapshot whose record can be read.
    static Optional<String> coverage(final ChatWorkspace workspace,
                                     final int chatId,
                                     final WorkspacePath table,
                                     final long rowsUsed) {
        final Matcher matcher = SNAPSHOT_FILE.matcher(table.path());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final WorkspacePath metaPath = WorkspacePath.parse(matcher.group(1) + ".json");
        try {
            if (workspace.find(chatId, metaPath).isEmpty()) {
                return Optional.empty();
            }
            final ResultsMeta meta = JsonUtil.readValue(Files.readString(workspace.localFile(chatId, metaPath),
                    StandardCharsets.UTF_8), ResultsMeta.class);
            return Optional.of(coverage(meta, rowsUsed));
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> "Unable to read " + metaPath + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /// @param meta     A snapshot's record.
    /// @param rowsUsed How many of its rows were used.
    /// @return What they cover, as a sentence for the model, with what to suggest if it isn't everything.
    static String coverage(final ResultsMeta meta, final long rowsUsed) {
        final boolean events = meta.path() != null && meta.path().contains(EVENTS_SUFFIX + ".md");
        final String unit = events
                ? "event(s)"
                : "row(s)";
        final StringBuilder sb = new StringBuilder("Coverage: ");
        final long found = Objects.requireNonNullElse(meta.totalRows(), meta.rowsHeld());
        final boolean all = rowsUsed >= found && !meta.truncated() && meta.finished();
        if (all) {
            sb.append("all ").append(found).append(' ').append(unit).append(" the table's search found");
        } else {
            sb.append("the first ").append(Math.min(rowsUsed, meta.rowsHeld())).append(" of ")
                    .append(meta.totalRows() != null
                            ? String.valueOf(meta.totalRows())
                            : "more than " + meta.rowsHeld())
                    .append(' ').append(unit).append(" the table's search found");
        }
        if (meta.groups() != null) {
            sb.append(" (behind the table's ").append(meta.groups()).append(" group(s))");
        }
        sb.append('.');
        if (!all) {
            sb.append(" Say so (\"of the first N of M events\"). Offer to take more (current_results with rows, "
                      + "within the global row limit) if that would help. For an answer over all the data, "
                      + "suggest the scalable route as text to copy: a column from the dashboard's extraction "
                      + "pipeline, or a query on an indexed field.");
        }
        return sb.toString();
    }

    /// Works out a snapshot's state, as compared with the tab as the user sees it now.
    ///
    /// @param meta      The snapshot.
    /// @param rowsAsked How many rows were asked for, or null for all it holds.
    /// @param liveHash  The hash of the tab's search request as it now stands, or null if not known.
    /// @param limit     The global row limit.
    /// @return Its state, why, and anything else to say about it.
    static Assessment assess(final ResultsMeta meta, final Integer rowsAsked, final String liveHash, final int limit) {
        final boolean stale = liveHash != null && !liveHash.equals(meta.requestHash());
        final List<String> incomplete = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final boolean moreExist = meta.truncated()
                                  || (meta.totalRows() != null && meta.totalRows() > meta.rowsHeld())
                                  || !meta.finished();
        if (!meta.finished()) {
            incomplete.add("the search hadn't finished when it was taken");
        }
        if (meta.cutByLimit()) {
            incomplete.add("the global row limit of " + limit + " cut it");
        }
        if (rowsAsked != null && rowsAsked > meta.rowsHeld()) {
            if (moreExist && !meta.cutByLimit()) {
                incomplete.add("it holds " + meta.rowsHeld() + " row(s), fewer than the " + rowsAsked
                               + " asked for, and more exist");
                notes.add(rowsAsked <= limit
                        ? "With the user's yes (ask_user), call again with refresh and rows " + rowsAsked + "."
                        : "The global row limit is " + limit + "; offer that many instead.");
            } else if (!moreExist) {
                notes.add("The table has only " + meta.rowsHeld() + " row(s), fewer than the " + rowsAsked
                          + " asked for, so all of them are used.");
            }
        } else if (rowsAsked == null && meta.truncated() && meta.rowsWanted() < limit) {
            incomplete.add("it holds only the first " + meta.rowsHeld() + " row(s), as fewer were asked for when "
                           + "it was taken, and more exist");
            notes.add("With the user's yes (ask_user), call again with refresh to take up to " + limit + " rows.");
        }
        final State state = stale
                ? State.STALE
                : (incomplete.isEmpty()
                        ? State.OK
                        : State.INCOMPLETE);
        return new Assessment(state, stale, incomplete, notes);
    }

    // What the model is given about a snapshot.
    private static String describe(final ResultsMeta meta,
                                   final Assessment assessment,
                                   final String path,
                                   final Integer rowsAsked,
                                   final boolean taken,
                                   final List<String> changes,
                                   final String profile) {
        final StringBuilder sb = new StringBuilder();
        sb.append(taken
                ? (meta.refreshed()
                        ? "Ran the search again and took a snapshot of "
                        : "Took a snapshot of ")
                : "Using the snapshot already taken of ");
        sb.append(meta.tableName()).append(" in ").append(ReadDocumentTool.describe(meta.tab())).append(": ")
                .append(meta.rowsHeld()).append(" row(s)");
        if (meta.totalRows() != null) {
            sb.append(" of ").append(meta.totalRows()).append(" found");
        } else if (meta.truncated()) {
            sb.append(" (more exist)");
        }
        sb.append(", taken ").append(Instant.ofEpochMilli(meta.takenAtMs()));
        if (meta.timeRange() != null) {
            sb.append(", time range ").append(meta.timeRange());
        }
        sb.append(".\nState: ").append(assessment.state().name().toLowerCase(Locale.ROOT));
        if (assessment.stale()) {
            sb.append(" - the tab's search has changed since the snapshot was taken")
                    .append(changes.isEmpty()
                            ? ""
                            : " (" + String.join(", ", changes) + ")")
                    .append(", so these rows are not what the tab's search would give now. Offer the user to run "
                            + "it again (refresh), with ask_user. If they say no, you may use these rows, but say "
                            + "the results are from the earlier query.");
        }
        if (!assessment.incomplete().isEmpty()) {
            sb.append(assessment.stale()
                    ? "\nIt is also incomplete: "
                    : " - ").append(String.join("; ", assessment.incomplete()))
                    .append(". Say so before giving figures.");
        }
        assessment.notes().forEach(note -> sb.append('\n').append(note));
        if (rowsAsked != null && rowsAsked < meta.rowsHeld()) {
            sb.append("\nThe first ").append(rowsAsked).append(" row(s), as asked, are in ").append(path)
                    .append("; work on those only, and say so (e.g. \"Of the first ").append(rowsAsked)
                    .append(meta.totalRows() != null
                            ? " of " + meta.totalRows()
                            : "").append(" rows…\").");
        }
        sb.append("\nThe rows are in ").append(path).append(". Work out figures with the table tool (or "
                                                            + "summarise_column, fetch_events).");
        if (profile != null) {
            sb.append("\n\n").append(profile);
        }
        return sb.toString();
    }

    /// @param searches The tables of results a tab shows.
    /// @param table    The table asked for, or null.
    /// @return The table asked for (by name, ignoring case), the only one if none was asked for, or null if it
    /// can't be told which.
    static AiTabSearch chooseTable(final List<AiTabSearch> searches, final String table) {
        if (table == null) {
            return searches.size() == 1
                    ? searches.getFirst()
                    : null;
        }
        return searches.stream()
                .filter(search -> table.equalsIgnoreCase(name(search))
                                  || table.equalsIgnoreCase(search.getComponentId()))
                .findFirst()
                .orElse(null);
    }

    /// @return The workspace path, without its extension, of the snapshot of a table's results.
    static String basePath(final DocRef tab, final AiTabSearch search) {
        return RESULTS_FOLDER + safe(tab.getUuid()) + "/" + safe(NullSafe.nonBlankStringElse(
                search.getComponentId(), "results"));
    }

    private static String safe(final String text) {
        final String safe = NullSafe.string(text).replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isEmpty()
                ? "results"
                : safe.substring(0, Math.min(safe.length(), 100));
    }

    private static String name(final AiTabSearch search) {
        return NullSafe.nonBlankStringElse(search.getName(), NullSafe.nonBlankStringElse(search.getComponentId(),
                "Results"));
    }

    private static String describe(final StoredTabSnapshot tab, final AiTabSearch search) {
        return "'" + name(search) + "' in " + ReadDocumentTool.describe(tab.docRef());
    }

    // The data source a table's search reads: a dashboard's, or the one a query names.
    private DocRef dataSource(final AskStroomAiContext context) {
        final DocRef dashboard = ResultsRequests.dashboardDataSource(context);
        if (dashboard != null) {
            return dashboard;
        }
        final String query = context instanceof final QueryTableContext queryTable
                ? NullSafe.get(queryTable.getSearchRequest(), QuerySearchRequest::getQuery)
                : null;
        if (query == null) {
            return null;
        }
        try {
            return queryServiceProvider.get().getReferencedDataSource(query).orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the data source of a query", e);
            return null;
        }
    }

    // Whether the search behind the rows shown has finished, and how many rows it found, if Stroom still holds it.
    private SearchStatus status(final AskStroomAiContext context) {
        try {
            return status(search(context, null), componentId(context));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the state of a search", e);
            return new SearchStatus(true, null, null);
        }
    }

    // Runs a search again, as the tab now stands, waiting for it to finish for most of the time limit.
    private Ran runAgain(final AskStroomAiContext live) {
        final long waitUntilNanos = System.nanoTime()
                                    + (long) (timeLimit().toNanos() * SEARCH_TIME_SHARE);
        DashboardSearchResponse response = search(live, null);
        try {
            while (response != null
                   && NullSafe.isEmptyCollection(response.getErrors())
                   && !response.isComplete()
                   && System.nanoTime() < waitUntilNanos) {
                pause();
                response = search(live, response.getQueryKey());
            }
        } catch (final RuntimeException e) {
            // E.g. the time limit: the search this tool started never runs on after the call.
            agentSearches.terminate(NullSafe.get(response, DashboardSearchResponse::getQueryKey));
            throw e;
        }
        if (response == null) {
            return new Ran(null, null, "it did not run");
        }
        if (!NullSafe.isEmptyCollection(response.getErrors())) {
            return new Ran(null, null, String.join("; ", response.getErrors()));
        }
        return new Ran(withQueryKey(live, response.getQueryKey(), response.getNode()),
                status(response, componentId(live)), null);
    }

    // Asks for a search's state (and its first row, to learn the total): a new search if the query key is null and
    // the request has none, otherwise the one the key names.
    private DashboardSearchResponse search(final AskStroomAiContext context, final QueryKey queryKey) {
        final OffsetRange firstRow = new OffsetRange(0L, 1L);
        if (context instanceof final QueryTableContext query) {
            final QuerySearchRequest request = query.getSearchRequest();
            return queryServiceProvider.get().search(request.copy()
                    .queryKey(queryKey != null
                            ? queryKey
                            : request.getQueryKey())
                    .storeHistory(false)
                    .requestedRange(firstRow)
                    .build());
        } else if (context instanceof final DashboardTableContext dashboard) {
            final DashboardSearchRequest request = dashboard.getSearchRequest();
            final List<ComponentResultRequest> tables = request.getComponentResultRequests().stream()
                    .filter(TableResultRequest.class::isInstance)
                    .map(TableResultRequest.class::cast)
                    .map(table -> (ComponentResultRequest) table.copy().requestedRange(firstRow).build())
                    .toList();
            return dashboardServiceProvider.get().search(request.copy()
                    .queryKey(queryKey != null
                            ? queryKey
                            : request.getQueryKey())
                    .storeHistory(false)
                    .componentResultRequests(tables)
                    .build());
        }
        throw new IllegalStateException("Not a table of results");
    }

    private static SearchStatus status(final DashboardSearchResponse response, final String componentId) {
        if (response == null) {
            return new SearchStatus(true, null, null);
        }
        final Long total = NullSafe.list(response.getResults()).stream()
                .filter(result -> componentId == null || componentId.equals(result.getComponentId()))
                .filter(TableResult.class::isInstance)
                .map(TableResult.class::cast)
                .map(TableResult::getTotalResults)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new SearchStatus(response.isComplete(), total, response.getQueryKey());
    }

    private static String componentId(final AskStroomAiContext context) {
        if (context instanceof final DashboardTableContext dashboard) {
            return NullSafe.list(dashboard.getSearchRequest().getComponentResultRequests()).stream()
                    .filter(TableResultRequest.class::isInstance)
                    .map(ComponentResultRequest::getComponentId)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static AskStroomAiContext withQueryKey(final AskStroomAiContext context,
                                                   final QueryKey queryKey,
                                                   final String node) {
        if (context instanceof final QueryTableContext query) {
            return new QueryTableContext(query.getDescription(), node,
                    query.getSearchRequest().copy().queryKey(queryKey).build());
        }
        final DashboardTableContext dashboard = (DashboardTableContext) context;
        return new DashboardTableContext(dashboard.getDescription(), node,
                dashboard.getSearchRequest().copy().queryKey(queryKey).build());
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

    private Optional<ResultsMeta> readMeta(final int chatId, final String base) {
        final WorkspacePath path = WorkspacePath.parse(base + ".json");
        if (workspace.find(chatId, path).isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonUtil.readValue(Files.readString(workspace.localFile(chatId, path),
                    StandardCharsets.UTF_8), ResultsMeta.class));
        } catch (final IOException | RuntimeException e) {
            // Gone (e.g. its retention ended), or the user can no longer use what it came from.
            LOGGER.debug(() -> "Unable to read " + path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void writeProfile(final int chatId, final String tablePath, final String description,
                              final String sourceUuid) {
        try (final Reader reader = Files.newBufferedReader(workspace.localFile(chatId,
                WorkspacePath.parse(tablePath)), StandardCharsets.UTF_8)) {
            workspace.writeTabFile(chatId, WorkspacePath.parse(profilePath(tablePath)),
                    TableProfiler.profile(reader, description), sourceUuid);
        } catch (final IOException | RuntimeException e) {
            // The table is still usable without its profile.
            LOGGER.debug(() -> "Unable to profile " + tablePath, e);
        }
    }

    private String readProfile(final int chatId, final String tablePath) {
        final WorkspacePath path = WorkspacePath.parse(profilePath(tablePath));
        try {
            return workspace.find(chatId, path).isPresent()
                    ? Files.readString(workspace.localFile(chatId, path), StandardCharsets.UTF_8).strip()
                    : null;
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> "Unable to read " + path, e);
            return null;
        }
    }

    // A copy of the first rows of a snapshot, for a scope the user asked for, with its own profile.
    private String scopedCopy(final int chatId,
                              final DocRef tab,
                              final String base,
                              final ResultsMeta meta,
                              final int rows) {
        final String path = base + "-first-" + rows + ".md";
        final Path temp = workspace.tempFile();
        try {
            try (final BufferedReader reader = Files.newBufferedReader(workspace.localFile(chatId,
                    WorkspacePath.parse(meta.path())), StandardCharsets.UTF_8);
                    final BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                String line;
                long lines = 0;
                while (lines < rows + 2L && (line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    lines++;
                }
            }
            final String sourceUuid = workspace.sourceDocUuid(chatId, WorkspacePath.parse(meta.path()));
            workspace.writeTabFile(chatId, WorkspacePath.parse(path), temp, sourceUuid);
            writeProfile(chatId, path, "The first " + rows + " rows of " + meta.tableName() + " in "
                                       + ReadDocumentTool.describe(tab), sourceUuid);
            return path;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            ChatWorkspace.deleteQuietly(temp);
        }
    }

    private static String profilePath(final String tablePath) {
        return tablePath.substring(0, tablePath.length() - ".md".length()) + ".profile.md";
    }

    @Override
    public boolean readsUntrustedContent() {
        // Stroom data can hold text written by anyone.
        return true;
    }


    // --------------------------------------------------------------------------------


    /// A snapshot's state, as compared with the tab now.
    enum State {
        /// It holds what was asked for, from the tab's search as it stands.
        OK,
        /// The tab's search has changed since it was taken.
        STALE,
        /// It may not hold everything asked for.
        INCOMPLETE
    }

    /// What is known about a snapshot of a table's results, kept beside it.
    ///
    /// @param tab          The document whose tab showed the table.
    /// @param tableName    The table's name, as the user sees it.
    /// @param componentId  The table's component id on a dashboard, or null.
    /// @param path         The snapshot's workspace file.
    /// @param requestHash  The hash of the search request it was taken from.
    /// @param timeRange    The search's time range, for people, or null.
    /// @param takenAtMs    When it was taken.
    /// @param rowsHeld     How many rows it holds.
    /// @param rowsWanted   How many rows were asked for when it was taken.
    /// @param truncated    True if there were more rows than it holds.
    /// @param totalRows    How many rows the search found, where known.
    /// @param finished     True if the search had finished when it was taken.
    /// @param cutByLimit   True if the global row limit cut it.
    /// @param refreshed    True if the search was run again to take it.
    /// @param tabQueryKey  The query key of the results the tab showed when it was taken, or null.
    /// @param request      The search request it was taken from.
    /// @param groups       For a snapshot of the events behind a grouped table, how many rows (groups) the table
    ///                     had, where known, else null.
    record ResultsMeta(DocRef tab,
                       String tableName,
                       String componentId,
                       String path,
                       String requestHash,
                       String timeRange,
                       long takenAtMs,
                       long rowsHeld,
                       long rowsWanted,
                       boolean truncated,
                       Long totalRows,
                       boolean finished,
                       boolean cutByLimit,
                       boolean refreshed,
                       String tabQueryKey,
                       AskStroomAiContext request,
                       Long groups) {

    }

    /// A snapshot's state, and why.
    ///
    /// @param state      Its state.
    /// @param stale      True if the tab's search has changed since it was taken.
    /// @param incomplete Why it may not hold everything asked for, if it may not.
    /// @param notes      Anything else to say, e.g. what to offer the user.
    record Assessment(State state, boolean stale, List<String> incomplete, List<String> notes) {

    }

    // Whether a search had finished, how many rows it found, and its query key.
    private record SearchStatus(boolean complete, Long totalRows, QueryKey queryKey) {

    }

    // A search run again: its results, as a request with its query key, or why it failed.
    private record Ran(AskStroomAiContext context, SearchStatus status, String error) {

    }

    /// What the tool tells the model, and the snapshot it used.
    ///
    /// @param text What the model is told.
    /// @param path The snapshot's workspace file, or null if none could be used.
    record Outcome(String text, String path) {

        static Outcome problem(final String text) {
            return new Outcome(text, null);
        }
    }

    // A snapshot taken, or why it couldn't be.
    private record Taken(ResultsMeta meta, String problem) {

        static Taken problem(final String problem) {
            return new Taken(null, problem);
        }
    }
}
