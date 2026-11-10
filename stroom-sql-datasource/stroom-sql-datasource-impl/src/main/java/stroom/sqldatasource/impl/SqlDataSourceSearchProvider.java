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

package stroom.sqldatasource.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.sqldatasource.shared.AthenaConnectionSettings;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;

/// Runs StroomQL and dashboard queries against a [SqlDataSourceDoc].
///
/// The SQL comes with each query, in [Query#getNativeQuery()]. The columns it returns become the
/// query's fields, and its rows go into the result store, so everything after the rows (`eval`,
/// `select`, grouping, sorting and so on) works as for any other data source. The `where`
/// expression is applied to the rows in Stroom; authors should filter in the SQL for speed.
///
/// ## The time range
///
/// This provider reports no time field, so `ResultStoreManager` does not add the time range to
/// the `where` expression. It handles the time range itself, as the SQL decides:
///
/// - If the SQL has `:from` or `:to`, the time range is bound to them and the database filters.
/// - Otherwise, if the document names a time column, rows are filtered on it in Stroom.
/// - Otherwise the time range is not used, and the query warns that it was ignored.
///
/// Amazon Athena bills by the data a query scans, so filtering in Stroom after Athena has read
/// everything would cost money for nothing. An Athena query with a time range must therefore take it
/// through `:from` or `:to`, and is refused otherwise.
///
/// ## Amazon Athena fields
///
/// Athena cannot describe a query without running it, and every run is billed. So the fields of an
/// Athena query are only known once it has run; until then [#getFields(DocRef, String)] has none.
///
/// ## Permissions
///
/// Running a query, or listing its fields, needs Use on the document. The document's credential is
/// resolved as the current user, who needs Use on it.
public class SqlDataSourceSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqlDataSourceSearchProvider.class);

    static final String ATHENA_TIME_RANGE_REFUSED = "The query has a time range, but its SQL has no :from or :to "
                                                    + "placeholder. Amazon Athena bills by the data a query scans, "
                                                    + "so the time range must go into the SQL, for example "
                                                    + "'where event_time >= :from and event_time < :to'.";
    static final String FIELDS_HINT = "The fields of a SQL Data Source are the columns returned by the " +
                                      "query's SQL, so write the SQL first.";

    private final Executor executor;
    private final SqlDataSourceStore store;
    private final DocFinder docFinder;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;
    private final JdbcQueryRunner jdbcQueryRunner;
    private final JdbcConnectionResolver connectionResolver;
    private final AthenaQueryRunner athenaQueryRunner;
    private final AthenaConnectionResolver athenaConnectionResolver;
    private final SqlFieldCache fieldCache;

    /// Creates the provider. Guice supplies every argument.
    @Inject
    SqlDataSourceSearchProvider(final Executor executor,
                                final SqlDataSourceStore store,
                                final DocFinder docFinder,
                                final SecurityContext securityContext,
                                final CoprocessorsFactory coprocessorsFactory,
                                final ResultStoreFactory resultStoreFactory,
                                final TaskManager taskManager,
                                final TaskContextFactory taskContextFactory,
                                final ExpressionPredicateFactory expressionPredicateFactory,
                                final FieldInfoResultPageFactory fieldInfoResultPageFactory,
                                final JdbcQueryRunner jdbcQueryRunner,
                                final JdbcConnectionResolver connectionResolver,
                                final AthenaQueryRunner athenaQueryRunner,
                                final AthenaConnectionResolver athenaConnectionResolver,
                                final SqlFieldCache fieldCache) {
        this.executor = executor;
        this.store = store;
        this.docFinder = docFinder;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
        this.jdbcQueryRunner = jdbcQueryRunner;
        this.connectionResolver = connectionResolver;
        this.athenaQueryRunner = athenaQueryRunner;
        this.athenaConnectionResolver = athenaConnectionResolver;
        this.fieldCache = fieldCache;
    }

    @Override
    public String getDataSourceType() {
        return SqlDataSourceDoc.TYPE;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return store.list();
    }

    @Override
    public List<DocRef> findDataSourceByName(final String name) {
        return docFinder.findByName(getDataSourceType(), name);
    }

    /// The SQL of a SQL Data Source is given in each query.
    @Override
    public boolean takesNativeQuery() {
        return true;
    }

    /// The fields are the columns of the SQL in the criteria, as [#getFields(DocRef, String)] gives
    /// them. With no SQL there are none.
    ///
    /// This serves field completion while a query is typed, so SQL that cannot be described yet, or a
    /// document or credential the user may not use, gives no fields rather than an error. Running the
    /// query reports the problem.
    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return fieldInfoResultPageFactory.create(criteria, fieldsOrNone(criteria));
    }

    private List<QueryField> fieldsOrNone(final FindFieldCriteria criteria) {
        final String sql = criteria.getNativeQuery();
        if (NullSafe.isBlankString(sql)) {
            return List.of();
        }
        try {
            return getFields(criteria.getDataSourceRef(), sql);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to list the fields of SQL for {}: {}",
                    criteria.getDataSourceRef(), e.getMessage()), e);
            return List.of();
        }
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return 0;
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        final String description = getDoc(docRef).getDescription();
        return Optional.of(NullSafe.isBlankString(description)
                ? FIELDS_HINT
                : description + "\n\n" + FIELDS_HINT);
    }

    /// The document's minimum refresh interval for its connection type: Athena sources default to
    /// [AthenaConnectionSettings#DEFAULT_MIN_REFRESH_MINUTES], JDBC sources to none.
    ///
    /// @param docRef The SQL Data Source.
    /// @return The minimum, or empty for none.
    /// @throws PermissionException If the user may not use the document.
    @Override
    public Optional<SimpleDuration> fetchMinRefreshInterval(final DocRef docRef) {
        final int minutes = minRefreshMinutes(getDoc(docRef));
        return minutes > 0
                ? Optional.of(new SimpleDuration((long) minutes, TimeUnit.MINUTES))
                : Optional.empty();
    }

    /// @param doc A SQL Data Source.
    /// @return Its minimum refresh interval in minutes for its connection type, 0 for none.
    static int minRefreshMinutes(final SqlDataSourceDoc doc) {
        final Integer minutes = isAthena(doc)
                ? Objects.requireNonNullElse(
                        NullSafe.get(doc.getAthena(), AthenaConnectionSettings::getMinRefreshMinutes),
                        AthenaConnectionSettings.DEFAULT_MIN_REFRESH_MINUTES)
                : NullSafe.get(doc.getJdbc(), JdbcConnectionSettings::getMinRefreshMinutes);
        return Math.max(0, Objects.requireNonNullElse(minutes, 0));
    }

    /// Always empty, so the time range is not added to the `where` expression. See the class
    /// description for how the time range is used instead.
    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.empty();
    }

    /// Lists the fields a query's SQL returns: its columns.
    ///
    /// @param docRef The SQL Data Source.
    /// @param sql    The query's SQL.
    /// @return The fields, in column order. For Amazon Athena, none until the SQL has run.
    /// @throws PermissionException If the user may not use the document or its credential.
    /// @throws RuntimeException    If the SQL cannot be read or the database refuses it.
    public List<QueryField> getFields(final DocRef docRef, final String sql) {
        final SqlDataSourceDoc doc = getDoc(docRef);
        requireConnection(doc);
        final ParsedSql parsedSql = SqlStatementParser.parse(sql);
        // The cache may answer without connecting, so check here that the user may use the
        // credential, as connecting would.
        if (isAthena(doc)) {
            athenaConnectionResolver.resolve(doc.getAthena());
            return fieldCache.getIfPresent(doc, sql)
                    .map(SqlDataSourceSearchProvider::toQueryFields)
                    .orElse(List.of());
        }
        connectionResolver.resolve(doc.getJdbc());
        final List<SqlColumn> columns = fieldCache.get(doc, sql, ignored -> {
            try {
                return jdbcQueryRunner.describe(doc, parsedSql);
            } catch (final SQLException e) {
                throw new RuntimeException("The database could not describe the SQL: " + e.getMessage(), e);
            }
        });
        return toQueryFields(columns);
    }

    private static List<QueryField> toQueryFields(final List<SqlColumn> columns) {
        return columns.stream()
                .map(SqlColumn::toQueryField)
                .toList();
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);
        final Query query = modifiedSearchRequest.getQuery();
        final DocRef docRef = query.getDataSource();
        final SqlDataSourceDoc doc = getDoc(docRef);
        requireConnection(doc);
        final boolean athena = isAthena(doc);

        // Check everything that can be checked before the search starts, so a mistake in the
        // query is reported straight away rather than as a search error.
        final String sql = query.getNativeQuery();
        final ParsedSql parsedSql = SqlStatementParser.parse(sql);
        final DateTimeSettings dateTimeSettings = modifiedSearchRequest.getDateTimeSettings();
        final List<Object> values = SqlParameterBinder.resolveValues(
                parsedSql,
                query.getTimeRange(),
                query.getParams(),
                dateTimeSettings);
        if (athena) {
            AthenaQueryRunner.requireReadOnly(parsedSql);
            if (hasTimeRange(query.getTimeRange()) && !parsedSql.usesTimeRange()) {
                throw new IllegalArgumentException(ATHENA_TIME_RANGE_REFUSED);
            }
        }
        final ExpressionOperator expression = athena
                ? query.getExpression()
                : addTimeRange(query.getExpression(), query.getTimeRange(), parsedSql, doc.getTimeField());
        final boolean filtersOnTimeColumn = expression != query.getExpression();

        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest,
                DataStoreSettings.createBasicSearchResultStoreSettings());
        final ResultStore resultStore = resultStoreFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                coprocessors);

        if (hasTimeRange(query.getTimeRange())
            && !parsedSql.usesTimeRange()
            && NullSafe.isBlankString(doc.getTimeField())) {
            coprocessors.getErrorConsumer().add(Severity.WARNING, () ->
                    "The time range was ignored, as the SQL has no :from or :to placeholder and the SQL Data "
                    + "Source has no time column.");
        }

        final String searchKey = modifiedSearchRequest.getKey().toString();
        final String infoPrefix = LogUtil.message("Querying SQL Data Source {} {} - ", doc.getName(), searchKey);
        final QueryCancellation cancellation = new QueryCancellation();

        final Runnable runnable = taskContextFactory.context("SQL Data Source Search", taskContext -> {
            resultStore.setSearchProcess(createSearchProcess(taskContext, cancellation));
            try {
                if (!cancellation.isCancelled()) {
                    taskContext.info(() -> infoPrefix + "running query");
                    final Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory = columns -> {
                        fieldCache.put(doc, sql, columns);
                        if (filtersOnTimeColumn) {
                            requireTimeColumn(doc, columns);
                        }
                        return SqlRowConsumer.create(
                                columns,
                                coprocessors.getFieldIndex(),
                                expression,
                                dateTimeSettings,
                                expressionPredicateFactory,
                                coprocessors);
                    };
                    final QueryRunSummary summary = athena
                            ? athenaQueryRunner.run(doc, parsedSql, values, rowConsumerFactory, cancellation,
                                    message -> taskContext.info(() -> infoPrefix + message))
                            : jdbcQueryRunner.run(doc, parsedSql, values, rowConsumerFactory, cancellation);
                    addWarnings(summary, coprocessors);
                    LOGGER.debug(() -> LogUtil.message("{} read {} rows, {} passed the where expression",
                            infoPrefix, summary.rowsRead(), summary.rowsPassed()));
                }
            } catch (final SQLException | RuntimeException e) {
                if (cancellation.isCancelled()) {
                    LOGGER.debug(() -> LogUtil.message("{}stopped after cancel: {}", infoPrefix, e.getMessage()), e);
                } else {
                    LOGGER.debug(() -> LogUtil.message("{}failed: {}", infoPrefix, e.getMessage()), e);
                    resultStore.addError(e);
                }
            } finally {
                taskContext.info(() -> infoPrefix + "complete");
                resultStore.signalComplete();
            }
        });
        executor.execute(runnable);

        return resultStore;
    }

    private SearchProcess createSearchProcess(final TaskContext taskContext, final QueryCancellation cancellation) {
        return new SearchProcess() {
            @Override
            public SearchTaskProgress getSearchTaskProgress() {
                final TaskProgress taskProgress = taskManager.getTaskProgress(taskContext);
                if (taskProgress == null) {
                    return null;
                }
                return new SearchTaskProgress(
                        taskProgress.getTaskName(),
                        taskProgress.getTaskInfo(),
                        taskProgress.getUserRef(),
                        taskProgress.getThreadName(),
                        taskProgress.getNodeName(),
                        taskProgress.getSubmitTimeMs(),
                        taskProgress.getTimeNowMs());
            }

            @Override
            public void onTerminate() {
                // Stop the work in the database as well as in Stroom.
                cancellation.cancel();
                taskManager.terminate(taskContext.getTaskId());
            }
        };
    }

    /// Adds the time range to the `where` expression as terms on the document's time column, when
    /// the SQL does not take the time range itself.
    ///
    /// @param expression The query's `where` expression, or `null`.
    /// @param timeRange  The query's time range, or `null`.
    /// @param parsedSql  The query's SQL.
    /// @param timeField  The document's time column, or `null`.
    /// @return The expression to filter rows with, or `null` for none.
    static ExpressionOperator addTimeRange(final ExpressionOperator expression,
                                           final TimeRange timeRange,
                                           final ParsedSql parsedSql,
                                           final String timeField) {
        if (!hasTimeRange(timeRange) || parsedSql.usesTimeRange() || NullSafe.isBlankString(timeField)) {
            return expression;
        }
        final QueryField timeQueryField = QueryField.createDate(timeField);
        final ExpressionOperator.Builder and = ExpressionOperator.builder().op(Op.AND);
        if (!NullSafe.isBlankString(timeRange.getFrom())) {
            and.addDateTerm(timeQueryField, Condition.GREATER_THAN_OR_EQUAL_TO, timeRange.getFrom());
        }
        if (!NullSafe.isBlankString(timeRange.getTo())) {
            and.addDateTerm(timeQueryField, Condition.LESS_THAN, timeRange.getTo());
        }
        if (expression != null) {
            and.addOperator(expression);
        }
        return and.build();
    }

    private static boolean hasTimeRange(final TimeRange timeRange) {
        return timeRange != null
               && (!NullSafe.isBlankString(timeRange.getFrom()) || !NullSafe.isBlankString(timeRange.getTo()));
    }

    private static void addWarnings(final QueryRunSummary summary, final CoprocessorsImpl coprocessors) {
        if (summary.info() != null) {
            coprocessors.getErrorConsumer().add(Severity.INFO, summary::info);
        }
        if (summary.limitReached()) {
            coprocessors.getErrorConsumer().add(Severity.WARNING, () -> LogUtil.message(
                    "The SQL returned more than {} rows, so only the first {} were used. Filter in the SQL, " +
                    "or raise the SQL Data Source's row limit.",
                    summary.maxRows(),
                    summary.maxRows()));
        }
        if (!summary.unmatchedFields().isEmpty()) {
            coprocessors.getErrorConsumer().add(Severity.WARNING, () ->
                    "These fields are not columns returned by the SQL, so they are empty: "
                    + String.join(", ", summary.unmatchedFields()) + ".");
        }
    }

    // The time range is filtered on the document's time column, so the SQL must return it.
    private static void requireTimeColumn(final SqlDataSourceDoc doc, final List<SqlColumn> columns) {
        if (SqlRowConsumer.findColumn(columns, doc.getTimeField()) < 0) {
            throw new IllegalArgumentException("The time range cannot be applied, as the SQL does not return "
                                               + "the time column '" + doc.getTimeField() + "' of SQL Data Source '"
                                               + doc.getName() + "'. Add it to the SQL's columns, or use :from "
                                               + "and :to in the SQL.");
        }
    }

    private static boolean isAthena(final SqlDataSourceDoc doc) {
        return SqlConnectionType.ATHENA.equals(doc.getConnectionType());
    }

    private static void requireConnection(final SqlDataSourceDoc doc) {
        if (isAthena(doc)) {
            if (doc.getAthena() == null) {
                throw new IllegalArgumentException("SQL Data Source '" + doc.getName()
                                                   + "' has no Amazon Athena settings.");
            }
            return;
        }
        if (doc.getJdbc() == null || NullSafe.isBlankString(doc.getJdbc().getUrl())) {
            throw new IllegalArgumentException("SQL Data Source '" + doc.getName() + "' has no JDBC URL.");
        }
    }

    // Use is enough to query a data source, so the document is read as the processing user once
    // that has been checked, as reading it as the user would need View.
    private SqlDataSourceDoc getDoc(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            throw new IllegalArgumentException("No SQL Data Source has been given.");
        }
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to use {}", docRef.toInfoString()));
        }
        return securityContext.asProcessingUserResult(() -> store.readDocument(docRef));
    }
}
