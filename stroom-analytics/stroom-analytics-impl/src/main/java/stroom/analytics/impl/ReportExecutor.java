/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.analytics.impl.ScheduledExecutorService.ExecutionResult;
import stroom.analytics.rule.impl.ReportStore;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionTracker;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.ReportDoc;
import stroom.dashboard.impl.SampleGenerator;
import stroom.dashboard.impl.download.DelimitedTarget;
import stroom.dashboard.impl.download.ExcelTarget;
import stroom.dashboard.impl.download.ExcelTarget.KV;
import stroom.dashboard.impl.download.SearchResultWriter;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.docref.DocRef;
import stroom.meta.api.MetaProperties;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.OffsetRange;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableResultBuilder;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.ui.config.shared.ReportUiDefaultConfig;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ReportExecutor extends AbstractScheduledQueryExecutable<ReportDoc> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReportExecutor.class);

    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final ReportStore reportStore;
    private final ResultStoreManager searchResponseCreatorManager;
    private final SearchRequestFactory searchRequestFactory;
    private final ExpressionContextFactory expressionContextFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final Provider<ReportUiDefaultConfig> reportUiDefaultConfigProvider;
    private final TempDirProvider tempDirProvider;
    private final Store streamStore;
    private final NotificationStateService notificationStateService;
    private final Provider<EmailSender> emailSenderProvider;

    @Inject
    public ReportExecutor(final Provider<AnalyticErrorWriter> analyticErrorWriterProvider,
                          final ReportStore reportStore,
                          final ResultStoreManager searchResponseCreatorManager,
                          final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                          final SearchRequestFactory searchRequestFactory,
                          final ExpressionContextFactory expressionContextFactory,
                          final ExpressionPredicateFactory expressionPredicateFactory,
                          final Provider<ReportUiDefaultConfig> reportUiDefaultConfigProvider,
                          final TempDirProvider tempDirProvider,
                          final Store streamStore,
                          final NotificationStateService notificationStateService,
                          final Provider<EmailSender> emailSenderProvider) {
        super(analyticErrorWriterProvider, errorReceiverProxyProvider);
        this.reportStore = reportStore;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.searchRequestFactory = searchRequestFactory;
        this.expressionContextFactory = expressionContextFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.reportUiDefaultConfigProvider = reportUiDefaultConfigProvider;
        this.tempDirProvider = tempDirProvider;
        this.streamStore = streamStore;
        this.notificationStateService = notificationStateService;
        this.emailSenderProvider = emailSenderProvider;
    }

    @Override
    public ExecutionResult run(final ReportDoc doc,
                               final Trigger trigger,
                               final Instant executionTime,
                               final Instant effectiveExecutionTime,
                               final ExecutionSchedule executionSchedule,
                               final ExecutionTracker currentTracker,
                               final ExecutionResult executionResult) {
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();

        final SearchRequestSource searchRequestSource = SearchRequestSource
                .builder()
                .sourceType(SourceType.SCHEDULED_QUERY_ANALYTIC)
                .componentId(SearchRequestFactory.TABLE_COMPONENT_ID)
                .build();

        final String query = doc.getQuery();
        final Query sampleQuery = Query
                .builder()
                .params(doc.getParameters())
                .timeRange(doc.getTimeRange())
                .build();
        final SearchRequest sampleRequest = new SearchRequest(
                searchRequestSource,
                null,
                sampleQuery,
                null,
                DateTimeSettings.builder().referenceTime(effectiveExecutionTime.toEpochMilli()).build(),
                false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        final SearchRequest mappedRequest = searchRequestFactory.create(query, sampleRequest, expressionContext);

        // Fix table result requests.
        final List<ResultRequest> resultRequests = mappedRequest.getResultRequests();
        if (NullSafe.size(resultRequests) == 1) {
            final ResultRequest resultRequest = resultRequests.getFirst().copy()
                    .openGroups(null)
                    .requestedRange(OffsetRange.UNBOUNDED)
                    .build();

            // Create a result store and begin search.
            final RequestAndStore requestAndStore = searchResponseCreatorManager
                    .getResultStore(mappedRequest);
            final SearchRequest modifiedRequest = requestAndStore.searchRequest();
            try {
                final DataStore dataStore = requestAndStore
                        .resultStore().getData(SearchRequestFactory.TABLE_COMPONENT_ID);
                // Wait for search to complete.
                dataStore.getCompletionState().awaitCompletion();

                ReportFile reportFile = null;
                try {
                    // Create the output file.
                    reportFile = createFile(
                            doc,
                            executionTime,
                            effectiveExecutionTime,
                            modifiedRequest.getDateTimeSettings(),
                            dataStore,
                            resultRequest);

                    for (final NotificationConfig notificationConfig : doc.getNotifications()) {
                        try {
                            sendFile(doc, notificationConfig, reportFile, executionTime, effectiveExecutionTime);
                        } catch (final IOException e) {
                            errorConsumer.add(e);
                        }
                    }

                } catch (final IOException e) {
                    errorConsumer.add(e);
                } finally {
                    // Delete the file after we complete.
                    if (reportFile != null) {
                        try {
                            Files.deleteIfExists(reportFile.file());
                        } catch (final IOException e) {
                            // Swallow as just a temp file
                            LOGGER.error("Error deleting reportFile: {} - {}",
                                    reportFile, LogUtil.exceptionMessage(e), e);
                        }
                    }
                }

            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            } finally {
                // Destroy search result store.
                searchResponseCreatorManager.destroy(modifiedRequest.getKey(), DestroyReason.NO_LONGER_NEEDED);
            }
        }

        return executionResult;
    }

    @Override
    public DocRef getDocRef(final ReportDoc doc) {
        return doc.asDocRef();
    }

    @Override
    public ReportDoc reload(final ReportDoc doc) {
        return reportStore.readDocument(doc.asDocRef());
    }

    @Override
    public String getIdentity(final ReportDoc doc) {
        return RuleUtil.getRuleIdentity(doc);
    }

    @Override
    public String getProcessType() {
        return "report";
    }

    private ReportFile createFile(final ReportDoc reportDoc,
                            final Instant executionTime,
                            final Instant effectiveExecutionTime,
                            final DateTimeSettings dateTimeSettings,
                            final DataStore dataStore,
                            final ResultRequest resultRequest) throws IOException {
        long totalRowCount = 0;
        final DownloadSearchResultFileType fileType = reportDoc.getReportSettings().getFileType();
        final String dateTime = DateUtil.createFileDateTimeString(effectiveExecutionTime);
        final String fileName = getFileName(reportDoc.getName() + "_" + dateTime, fileType.getExtension());
        final Path file = tempDirProvider.get().resolve(fileName);
        final FormatterFactory formatterFactory = new FormatterFactory(dateTimeSettings);

        // Start target
        try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
            final SearchResultWriter.Target target = switch (fileType) {
                case CSV -> new DelimitedTarget(outputStream, ",");
                case TSV -> new DelimitedTarget(outputStream, "\t");
                case EXCEL -> new ExcelTarget(outputStream, dateTimeSettings);
            };

            // Write delimited file.
            try {
                target.start();
                try {
                    target.startTable("Report");
                    final SampleGenerator sampleGenerator =
                            new SampleGenerator(false, 100);
                    final SearchResultWriter searchResultWriter = new SearchResultWriter(
                            sampleGenerator,
                            target);
                    final TableResultCreator tableResultCreator =
                            new TableResultCreator(formatterFactory, expressionPredicateFactory) {
                                @Override
                                public TableResultBuilder createTableResultBuilder() {
                                    return searchResultWriter;
                                }
                            };

                    final Result result = tableResultCreator.create(dataStore, resultRequest);
                    totalRowCount += searchResultWriter.getRowCount();
                } catch (final Exception e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } finally {
                    target.endTable();
                }

                // Write report info sheet if this is an Excel target.
                if (target instanceof final ExcelTarget excelTarget) {
                    final List<ExcelTarget.KV> info = new ArrayList<>();
                    info.add(new KV("Report Name", reportDoc.getName()));
                    info.add(new KV("Report Description",
                            reportDoc.getDescription() != null
                                    ? reportDoc.getDescription().replaceAll("\n", "")
                                    : ""));
                    info.add(new KV("Execution Time",
                            DateUtil.createNormalDateTimeString(executionTime)));
                    info.add(new KV("Effective Execution Time",
                            DateUtil.createNormalDateTimeString(effectiveExecutionTime)));
                    excelTarget.writeInfo(info);
                }
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            } finally {
                target.end();
            }
        }
        return new ReportFile(file, fileType, totalRowCount);
    }

    private String getFileName(final String baseName,
                               final String extension) {
        String fileName = baseName;
        fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
        fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
        fileName = fileName.replace(' ', '_');
        fileName = fileName + "." + extension;
        return fileName;
    }

    private void sendFile(final ReportDoc reportDoc,
                          final NotificationConfig notificationConfig,
                          final ReportFile reportFile,
                          final Instant executionTime,
                          final Instant effectiveExecutionTime) throws IOException {
        final NotificationState notificationState =
                notificationStateService.getState(reportDoc, notificationConfig);
        notificationState.enableIfPossible();
        if (notificationState.incrementAndCheckEnabled()) {
            if (NotificationDestinationType.STREAM.equals(notificationConfig.getDestinationType())) {
                if (notificationConfig.getDestination() instanceof
                        final NotificationStreamDestination streamDestination) {

                    final MetaProperties metaProperties = MetaProperties.builder()
                            .feedName(streamDestination.getDestinationFeed().getName())
                            .typeName("Report")
                            .pipelineUuid(reportDoc.getUuid())
                            .build();

                    try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(
                            reportFile.file()))) {
                        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                            try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
                                StreamUtil.streamToStream(inputStream, outputStreamProvider.get());

                                try (final Writer writer = new OutputStreamWriter(outputStreamProvider.get(
                                        StreamTypeNames.META))) {
                                    write(writer, "ReportName", reportDoc.getName());
                                    write(writer,
                                            "ReportDescription",
                                            reportDoc.getDescription() != null
                                                    ? reportDoc.getDescription().replaceAll("\n", "")
                                                    : "");
                                    write(writer, "ExecutionTime",
                                            DateUtil.createNormalDateTimeString(executionTime));
                                    write(writer, "EffectiveExecutionTime",
                                            DateUtil.createNormalDateTimeString(effectiveExecutionTime));
                                }
                            }
                        }
                    }
                } else {
                    throw new RuntimeException("No stream destination config found: " +
                                               RuleUtil.getRuleIdentity(reportDoc));
                }
            } else if (NotificationDestinationType.EMAIL.equals(notificationConfig.getDestinationType())) {
                if (notificationConfig.getDestination() instanceof
                        final NotificationEmailDestination emailDestination) {
                    emailSenderProvider.get().sendReport(
                            reportDoc,
                            emailDestination,
                            reportFile,
                            executionTime,
                            effectiveExecutionTime);
                } else {
                    throw new RuntimeException("No email destination config found: " +
                                               RuleUtil.getRuleIdentity(reportDoc));
                }
            }
        } else {
            LOGGER.debug("sendFile() - Not notifying - notificationConfig: {}, notificationState: {}",
                    notificationConfig, notificationState);
        }
    }

    private void write(final Writer writer, final String key, final String value) throws IOException {
        writer.write(key);
        writer.write(":");
        writer.write(value);
        writer.write("\n");
    }

    @Override
    List<ReportDoc> getRules() {
        // TODO this is not very efficient. It fetches all the docrefs from the DB,
        //  then loops over them to fetch+deser the associated doc for each one (one by one)
        //  so the caller can filter half of them out by type.
        //  It would be better if we had a json type col in the doc table, so that the
        //  we can pass some kind of json path query to the persistence layer that the DBPersistence
        //  can translate to a MySQL json path query.
        final List<ReportDoc> currentRules = new ArrayList<>();
        final List<DocRef> docRefs = reportStore.list();
        for (final DocRef docRef : docRefs) {
            try {
                final ReportDoc doc = reportStore.readDocument(docRef);
                if (doc != null) {
                    currentRules.add(doc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return currentRules;
    }

    @Override
    String getErrorFeedName(final ReportDoc doc) {
        String errorFeedName = null;
        if (doc.getErrorFeed() != null) {
            errorFeedName = doc.getErrorFeed().getName();
        }
        if (errorFeedName == null) {
            LOGGER.debug(() ->
                    LogUtil.message("Error feed not defined: {}", RuleUtil.getRuleIdentity(doc)));

            final DocRef defaultErrorFeed = reportUiDefaultConfigProvider.get().getDefaultErrorFeed();
            if (defaultErrorFeed == null) {
                throw new RuntimeException("Default error feed not defined");
            }
            errorFeedName = defaultErrorFeed.getName();
        }
        return errorFeedName;
    }
}
