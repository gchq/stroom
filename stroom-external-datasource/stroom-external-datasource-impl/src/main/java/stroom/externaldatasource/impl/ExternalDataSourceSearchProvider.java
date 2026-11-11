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

package stroom.externaldatasource.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;

/// Runs StroomQL and dashboard queries against an [ExternalDataSourceDoc].
///
/// This class does what every type of External Data Source shares: permissions, the result store,
/// running the search and reporting on it. What depends on the document's type, such as where the rows
/// come from and how the time range is used, is up to its [Connector].
///
/// The rows go into the result store, so everything after them (`eval`, `select`, grouping, sorting
/// and so on) works as for any other data source. The `where` expression is applied to the rows in
/// Stroom.
///
/// This provider reports no time field, so `ResultStoreManager` does not add the time range to the
/// `where` expression. Each connector uses the time range in its own way.
///
/// ## Permissions
///
/// Running a query, or listing its fields, needs Use on the document. The document's credential is
/// resolved as the current user, who needs Use on it.
public class ExternalDataSourceSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExternalDataSourceSearchProvider.class);

    static final String FIELDS_HINT = "The fields of this External Data Source are the columns returned by the "
                                      + "query's SQL, so write the SQL first.";

    private final Executor executor;
    private final ExternalDataSourceStore store;
    private final DocFinder docFinder;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;
    private final Connectors connectors;

    /// Creates the provider. Guice supplies every argument.
    @Inject
    ExternalDataSourceSearchProvider(final Executor executor,
                                     final ExternalDataSourceStore store,
                                     final DocFinder docFinder,
                                     final SecurityContext securityContext,
                                     final CoprocessorsFactory coprocessorsFactory,
                                     final ResultStoreFactory resultStoreFactory,
                                     final TaskManager taskManager,
                                     final TaskContextFactory taskContextFactory,
                                     final ExpressionPredicateFactory expressionPredicateFactory,
                                     final FieldInfoResultPageFactory fieldInfoResultPageFactory,
                                     final Connectors connectors) {
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
        this.connectors = connectors;
    }

    @Override
    public String getDataSourceType() {
        return ExternalDataSourceDoc.TYPE;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return store.list();
    }

    @Override
    public List<DocRef> findDataSourceByName(final String name) {
        return docFinder.findByName(getDataSourceType(), name);
    }

    /// Whether the document's type takes its SQL in each query, as JDBC and Athena do.
    ///
    /// A document that is missing, or that the user may not use, answers true, so a query of it fails
    /// for that reason rather than over its `sql` clause.
    @Override
    public boolean takesNativeQuery(final DocRef docRef) {
        try {
            return connectors.get(getDoc(docRef)).takesNativeQuery();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read {}: {}", docRef, e.getMessage()), e);
            return true;
        }
    }

    /// The fields are those [#getFields(DocRef, String)] gives for the native query in the criteria.
    ///
    /// This serves field completion while a query is typed, so SQL that cannot be described yet, or a
    /// document or credential the user may not use, gives no fields rather than an error. Running the
    /// query reports the problem.
    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return fieldInfoResultPageFactory.create(criteria, fieldsOrNone(criteria));
    }

    private List<QueryField> fieldsOrNone(final FindFieldCriteria criteria) {
        try {
            return getFields(criteria.getDataSourceRef(), criteria.getNativeQuery());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to list the fields of {}: {}",
                    criteria.getDataSourceRef(), e.getMessage()), e);
            return List.of();
        }
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return 0;
    }

    /// The document's description, followed for types that take SQL by a hint that the fields come
    /// from the SQL.
    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        final ExternalDataSourceDoc doc = getDoc(docRef);
        final String description = doc.getDescription();
        if (!connectors.get(doc).takesNativeQuery()) {
            return NullSafe.isBlankString(description)
                    ? Optional.empty()
                    : Optional.of(description);
        }
        return Optional.of(NullSafe.isBlankString(description)
                ? FIELDS_HINT
                : description + "\n\n" + FIELDS_HINT);
    }

    /// The document's minimum refresh interval, as its type's connector reads it.
    ///
    /// @param docRef The External Data Source.
    /// @return The minimum, or empty for none.
    /// @throws PermissionException If the user may not use the document.
    @Override
    public Optional<SimpleDuration> fetchMinRefreshInterval(final DocRef docRef) {
        final ExternalDataSourceDoc doc = getDoc(docRef);
        final int minutes = connectors.get(doc).getMinRefreshMinutes(doc);
        return minutes > 0
                ? Optional.of(new SimpleDuration((long) minutes, TimeUnit.MINUTES))
                : Optional.empty();
    }

    /// Always empty, so the time range is not added to the `where` expression. Each connector uses
    /// the time range in its own way.
    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.empty();
    }

    /// Lists the fields a query returns.
    ///
    /// @param docRef      The External Data Source.
    /// @param nativeQuery The query's SQL, or `null`. A type that takes SQL has no fields without it.
    /// @return The fields, in column order. For Amazon Athena, none until the SQL has run.
    /// @throws PermissionException If the user may not use the document or its credential.
    /// @throws RuntimeException    If the SQL cannot be read or the database refuses it.
    public List<QueryField> getFields(final DocRef docRef, final String nativeQuery) {
        final ExternalDataSourceDoc doc = getDoc(docRef);
        final Connector connector = connectors.get(doc);
        if (connector.takesNativeQuery() && NullSafe.isBlankString(nativeQuery)) {
            return List.of();
        }
        return connector.getFields(doc, nativeQuery);
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);
        final Query query = modifiedSearchRequest.getQuery();
        final ExternalDataSourceDoc doc = getDoc(query.getDataSource());
        final DateTimeSettings dateTimeSettings = modifiedSearchRequest.getDateTimeSettings();

        // Check everything that can be checked before the search starts, so a mistake in the
        // query is reported straight away rather than as a search error.
        final PreparedQuery preparedQuery = connectors.get(doc).prepare(doc, query, dateTimeSettings);

        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest,
                DataStoreSettings.createBasicSearchResultStoreSettings());
        final ResultStore resultStore = resultStoreFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                coprocessors);
        preparedQuery.warnings().forEach(warning ->
                coprocessors.getErrorConsumer().add(Severity.WARNING, () -> warning));

        final String searchKey = modifiedSearchRequest.getKey().toString();
        final String infoPrefix = LogUtil.message("Querying External Data Source {} {} - ", doc.getName(), searchKey);
        final QueryCancellation cancellation = new QueryCancellation();

        final Runnable runnable = taskContextFactory.context("External Data Source Search", taskContext -> {
            resultStore.setSearchProcess(createSearchProcess(taskContext, cancellation));
            try {
                if (!cancellation.isCancelled()) {
                    taskContext.info(() -> infoPrefix + "running query");
                    final Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory = columns ->
                            SqlRowConsumer.create(
                                    columns,
                                    coprocessors.getFieldIndex(),
                                    preparedQuery.expression(),
                                    dateTimeSettings,
                                    expressionPredicateFactory,
                                    coprocessors);
                    final QueryRunSummary summary = preparedQuery.run().run(
                            rowConsumerFactory,
                            cancellation,
                            message -> taskContext.info(() -> infoPrefix + message));
                    addWarnings(summary, coprocessors);
                    LOGGER.debug(() -> LogUtil.message("{} read {} rows, {} passed the where expression",
                            infoPrefix, summary.rowsRead(), summary.rowsPassed()));
                }
            } catch (final Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
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

    private static void addWarnings(final QueryRunSummary summary, final CoprocessorsImpl coprocessors) {
        if (summary.info() != null) {
            coprocessors.getErrorConsumer().add(Severity.INFO, summary::info);
        }
        summary.warnings().forEach(warning ->
                coprocessors.getErrorConsumer().add(Severity.WARNING, () -> warning));
        if (summary.limitReached()) {
            coprocessors.getErrorConsumer().add(Severity.WARNING, () -> LogUtil.message(
                    "There were more than {} rows, so only the first {} were read. Filter in the query, "
                    + "or raise the External Data Source's row limit.",
                    summary.maxRows(),
                    summary.maxRows()));
        }
        if (!summary.unmatchedFields().isEmpty()) {
            coprocessors.getErrorConsumer().add(Severity.WARNING, () ->
                    "These fields are not columns returned by the query, so they are empty: "
                    + String.join(", ", summary.unmatchedFields()) + ".");
        }
    }

    // Use is enough to query a data source, so the document is read as the processing user once
    // that has been checked, as reading it as the user would need View.
    private ExternalDataSourceDoc getDoc(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            throw new IllegalArgumentException("No External Data Source has been given.");
        }
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to use {}", docRef.toInfoString()));
        }
        final ExternalDataSourceDoc doc = securityContext.asProcessingUserResult(() -> store.readDocument(docRef));
        if (doc == null) {
            throw new IllegalArgumentException("External Data Source " + docRef.toInfoString() + " was not found.");
        }
        return doc;
    }
}
