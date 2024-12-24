package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.analytics.shared.ExecutionHistory;
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
import stroom.dashboard.impl.download.SearchResultWriter;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.expression.api.DateTimeSettings;
import stroom.meta.api.MetaProperties;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableResultBuilder;
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
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.Severity;

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
import java.util.List;
import java.util.regex.Pattern;

public class ReportGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReportGenerator.class);

    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final TempDirProvider tempDirProvider;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final ExpressionContextFactory expressionContextFactory;
    private final SearchRequestFactory searchRequestFactory;
    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final ResultStoreManager searchResponseCreatorManager;
    private final Store streamStore;
    private final NotificationStateService notificationStateService;
    private final Provider<EmailSender> emailSenderProvider;
    private final ExecutionScheduleDao executionScheduleDao;

    @Inject
    public ReportGenerator(final TempDirProvider tempDirProvider,
                           final ExpressionPredicateFactory expressionPredicateFactory,
                           final ExpressionContextFactory expressionContextFactory,
                           final SearchRequestFactory searchRequestFactory,
                           final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                           final ResultStoreManager searchResponseCreatorManager,
                           final Store streamStore,
                           final NotificationStateService notificationStateService,
                           final Provider<EmailSender> emailSenderProvider,
                           final ExecutionScheduleDao executionScheduleDao) {
        this.tempDirProvider = tempDirProvider;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.expressionContextFactory = expressionContextFactory;
        this.searchRequestFactory = searchRequestFactory;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.streamStore = streamStore;
        this.notificationStateService = notificationStateService;
        this.emailSenderProvider = emailSenderProvider;
        this.executionScheduleDao = executionScheduleDao;
    }

    public boolean process(final ReportDoc reportDoc,
                           final Trigger trigger,
                           final Instant executionTime,
                           final Instant effectiveExecutionTime,
                           final ExecutionSchedule executionSchedule,
                           final ExecutionTracker currentTracker) {
        LOGGER.debug(() -> LogUtil.message(
                "Executing analytic: {} with executionTime: {}, effectiveExecutionTime: {}, currentTracker: {}",
                reportDoc.asDocRef().toShortString(), executionTime, effectiveExecutionTime, currentTracker));

        boolean success = false;
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
        ExecutionResult executionResult = new ExecutionResult(null, null);

        try {
            final SearchRequestSource searchRequestSource = SearchRequestSource
                    .builder()
                    .sourceType(SourceType.SCHEDULED_QUERY_ANALYTIC)
                    .componentId(SearchRequestFactory.TABLE_COMPONENT_ID)
                    .build();

            final String query = reportDoc.getQuery();
            final Query sampleQuery = Query
                    .builder()
                    .params(reportDoc.getParameters())
                    .timeRange(reportDoc.getTimeRange())
                    .build();
            final SearchRequest sampleRequest = new SearchRequest(
                    searchRequestSource,
                    null,
                    sampleQuery,
                    null,
                    DateTimeSettings.builder().referenceTime(effectiveExecutionTime.toEpochMilli()).build(),
                    false);
            final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
            SearchRequest mappedRequest = searchRequestFactory.create(query, sampleRequest, expressionContext);

            // Fix table result requests.
            final List<ResultRequest> resultRequests = mappedRequest.getResultRequests();
            if (resultRequests != null && resultRequests.size() == 1) {
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

                    Path file = null;
                    try {
                        // Create the output file.
                        file = createFile(
                                reportDoc,
                                effectiveExecutionTime,
                                modifiedRequest.getDateTimeSettings(),
                                dataStore,
                                resultRequest);

                        for (final NotificationConfig notificationConfig : reportDoc.getNotifications()) {
                            try {
                                sendFile(reportDoc, notificationConfig, file, executionTime, effectiveExecutionTime);
                            } catch (final IOException e) {
                                errorConsumer.add(e);
                            }
                        }

                    } catch (final IOException e) {
                        errorConsumer.add(e);
                    } finally {
                        // Delete the file after we complete.
                        if (file != null) {
                            Files.deleteIfExists(file);
                        }
                    }

                } finally {
                    // Destroy search result store.
                    searchResponseCreatorManager.destroy(modifiedRequest.getKey(), DestroyReason.NO_LONGER_NEEDED);
                }
            }

            // Remember last successful execution time and compute next execution time.
            final Instant now = Instant.now();
            final Instant nextExecutionTime;
            if (executionSchedule.isContiguous()) {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(effectiveExecutionTime);
            } else {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(now);
            }

            // Update tracker.
            final ExecutionTracker executionTracker = new ExecutionTracker(
                    now.toEpochMilli(),
                    effectiveExecutionTime.toEpochMilli(),
                    nextExecutionTime.toEpochMilli());
            if (currentTracker != null) {
                executionScheduleDao.updateTracker(executionSchedule, executionTracker);
            } else {
                executionScheduleDao.createTracker(executionSchedule, executionTracker);
            }

            if (executionResult.status == null) {
                executionResult = new ExecutionResult("Complete", executionResult.message);
                success = true;
            }

        } catch (final Exception e) {
            executionResult = new ExecutionResult("Error", e.getMessage());

            try {
                LOGGER.debug(e::getMessage, e);
                errorReceiverProxyProvider.get()
                        .getErrorReceiver()
                        .log(Severity.ERROR, null, null, e.getMessage(), e);
            } catch (final RuntimeException e2) {
                LOGGER.error(e2::getMessage, e2);
            }

            // Disable future execution.
            LOGGER.info("Disabling: " + AnalyticUtil.getAnalyticRuleIdentity(reportDoc));
            executionScheduleDao.updateExecutionSchedule(executionSchedule.copy().enabled(false).build());

        } finally {
            // Record the execution.
            addExecutionHistory(executionSchedule,
                    executionTime,
                    effectiveExecutionTime,
                    executionResult);
        }

        return success;
    }

    private Path createFile(final ReportDoc reportDoc,
                            final Instant effectiveExecutionTime,
                            final DateTimeSettings dateTimeSettings,
                            final DataStore dataStore,
                            final ResultRequest resultRequest) throws IOException {
        long totalRowCount = 0;
        final DownloadSearchResultFileType fileType = reportDoc.getReportSettings().getFileType();

        // Import file.
        final String dateTime = DateUtil.createFileDateTimeString(effectiveExecutionTime);
        final String fileName = getFileName(reportDoc.getName() + "_" + dateTime,
                fileType.getExtension());
        final Path file = tempDirProvider.get().resolve(fileName);

        final FormatterFactory formatterFactory =
                new FormatterFactory(dateTimeSettings);

        // Start target
        try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
            SearchResultWriter.Target target = null;

            // Write delimited file.
            switch (fileType) {
                case CSV:
                    target = new DelimitedTarget(outputStream, ",");
                    break;
                case TSV:
                    target = new DelimitedTarget(outputStream, "\t");
                    break;
                case EXCEL:
                    target = new ExcelTarget(outputStream, dateTimeSettings);
                    break;
            }

            try {
                target.start();

                try {
                    target.startTable("Report " +
                                      DateUtil.createNormalDateTimeString(effectiveExecutionTime));

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
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            } finally {
                target.end();
            }
        }

        return file;
    }

    private String getFileName(final String baseName,
                               final String extension) {
        String fileName = baseName;
        fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
        fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
        fileName = fileName.replace(" ", "_");
        fileName = fileName + "." + extension;
        return fileName;
    }

    private void sendFile(final ReportDoc reportDoc,
                          final NotificationConfig notificationConfig,
                          final Path file,
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

                    try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(file))) {
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
                                               AnalyticUtil.getAnalyticRuleIdentity(reportDoc));
                }
            } else if (NotificationDestinationType.EMAIL.equals(notificationConfig.getDestinationType())) {
                if (notificationConfig.getDestination() instanceof
                        final NotificationEmailDestination emailDestination) {
                    emailSenderProvider.get().sendReport(
                            reportDoc,
                            emailDestination,
                            file,
                            executionTime,
                            effectiveExecutionTime);
                } else {
                    throw new RuntimeException("No email destination config found: " +
                                               AnalyticUtil.getAnalyticRuleIdentity(reportDoc));
                }
            }
        }
    }

    private void write(final Writer writer, final String key, final String value) throws IOException {
        writer.write(key);
        writer.write(":");
        writer.write(value);
        writer.write("\n");
    }

    private void addExecutionHistory(final ExecutionSchedule executionSchedule,
                                     final Instant executionTime,
                                     final Instant effectiveExecutionTime,
                                     final ExecutionResult executionResult) {
        try {
            final ExecutionHistory executionHistory = ExecutionHistory
                    .builder()
                    .executionSchedule(executionSchedule)
                    .executionTimeMs(executionTime.toEpochMilli())
                    .effectiveExecutionTimeMs(effectiveExecutionTime.toEpochMilli())
                    .status(executionResult.status)
                    .message(executionResult.message)
                    .build();
            executionScheduleDao.addExecutionHistory(executionHistory);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private record ExecutionResult(String status, String message) {

    }
}
