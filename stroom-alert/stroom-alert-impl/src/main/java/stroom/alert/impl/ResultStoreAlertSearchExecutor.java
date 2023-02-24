package stroom.alert.impl;

import stroom.alert.api.AlertManager;
import stroom.alert.impl.RecordConsumer.Data;
import stroom.alert.impl.RecordConsumer.Record;
import stroom.alert.rule.impl.AlertRuleStore;
import stroom.alert.rule.shared.AbstractAlertRule;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.alert.rule.shared.AlertRuleType;
import stroom.alert.rule.shared.ThresholdAlertRule;
import stroom.docref.DocRef;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultBuilder;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreManager;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ResultStoreAlertSearchExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStoreAlertSearchExecutor.class);

    private final ResultStoreManager resultStoreManager;
    private final AlertRuleSearchRequestHelper alertRuleSearchRequestHelper;
    private final ExecutorProvider executorProvider;
    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final Provider<AlertRuleStore> alertRuleStoreProvider;
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    // TODO : Make persistent with DB.
    private final Map<AlertRuleDoc, Instant> lastExecutionTimes = new ConcurrentHashMap<>();

    @Inject
    public ResultStoreAlertSearchExecutor(final ResultStoreManager resultStoreManager,
                                          final AlertRuleSearchRequestHelper alertRuleSearchRequestHelper,
                                          final ExecutorProvider executorProvider,
                                          final Provider<AlertRuleStore> alertRuleStoreProvider,
                                          final SecurityContext securityContext,
                                          final Provider<DetectionsWriter> detectionsWriterProvider,
                                          final PipelineScopeRunnable pipelineScopeRunnable) {
        this.resultStoreManager = resultStoreManager;
        this.alertRuleSearchRequestHelper = alertRuleSearchRequestHelper;
        this.executorProvider = executorProvider;
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.alertRuleStoreProvider = alertRuleStoreProvider;
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            // Group aggregate rules by destination feed.
            final Map<DocRef, List<AlertRuleDoc>> rulesByDestinationFeed = new HashMap<>();
            final AlertRuleStore alertRuleStore = alertRuleStoreProvider.get();
            final List<DocRef> docRefList = alertRuleStore.list();
            for (final DocRef docRef : docRefList) {
                final AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(docRef);
                if (AlertRuleType.THRESHOLD.equals(alertRuleDoc.getAlertRuleType())) {
                    final AbstractAlertRule alertRule = alertRuleDoc.getAlertRule();
                    if (alertRule instanceof ThresholdAlertRule) {
                        final DocRef destinationFeed = alertRule.getDestinationFeed();
                        if (destinationFeed != null) {
                            rulesByDestinationFeed
                                    .computeIfAbsent(destinationFeed, k -> new ArrayList<>())
                                    .add(alertRuleDoc);
                        }
                    }
                }
            }

            // Now run them.
            for (final Entry<DocRef, List<AlertRuleDoc>> entry : rulesByDestinationFeed.entrySet()) {
                final DocRef feedDocRef = entry.getKey();
                pipelineScopeRunnable.scopeRunnable(() -> {
                    final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                    detectionsWriter.setFeed(feedDocRef);
                    detectionsWriter.start();
                    try {
                        for (final AlertRuleDoc alertRuleDoc : entry.getValue()) {
                            try {
                                execThresholdAlertRule(alertRuleDoc,
                                        (ThresholdAlertRule) alertRuleDoc.getAlertRule(),
                                        detectionsWriter);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    } finally {
                        detectionsWriter.end();
                    }
                });
            }
        });
    }

    private void execThresholdAlertRule(final AlertRuleDoc alertRuleDoc,
                                        final ThresholdAlertRule thresholdAlertRule,
                                        final RecordConsumer recordConsumer) {
        final Duration executionFrequency = Duration.parse(thresholdAlertRule.getExecutionFrequency());
        final Duration executionDelay = Duration.parse(thresholdAlertRule.getExecutionDelay());

        final Instant lastTime = lastExecutionTimes.get(alertRuleDoc);
        if (lastTime == null) {
            // Execute from the beginning of time as this hasn't executed before.
            Instant from = Instant.ofEpochMilli(0);

            // Execute up to now minus the execution delay.
            Instant to = Instant.now();
            to = to.minus(executionDelay);
            to = roundDown(to, executionFrequency);

            execThresholdAlertRule(alertRuleDoc, thresholdAlertRule, from, to, recordConsumer);

        } else {
            // Get the last time we executed and round down to the frequency.
            Instant from = lastTime;
            from = roundDown(from, executionFrequency);

            // Add the frequency to the `from` time.
            Instant to = from.plus(executionFrequency);
            to = roundDown(to, executionFrequency);

            // See if it is time to execute again.
            if (to.isBefore(Instant.now().minus(executionDelay))) {
                execThresholdAlertRule(alertRuleDoc, thresholdAlertRule, from, to, recordConsumer);
            }
        }
    }

    private void execThresholdAlertRule(final AlertRuleDoc alertRuleDoc,
                                        final ThresholdAlertRule thresholdAlertRule,
                                        final Instant from,
                                        final Instant to,
                                        final RecordConsumer recordConsumer) {
        final QueryKey queryKey = alertRuleDoc.getQueryKey();
        final Optional<ResultStore> optionalResultStore = resultStoreManager.getIfPresent(queryKey);
        if (optionalResultStore.isPresent()) {
            // Create a search request.
            SearchRequest searchRequest = alertRuleSearchRequestHelper.create(alertRuleDoc);
            // Create a time filter. Note the filter times are exclusive.
            final TimeFilter timeFilter = new TimeFilter(thresholdAlertRule.getTimeField(),
                    from,
                    to.plusMillis(1));

            searchRequest = searchRequest.copy().key(queryKey).incremental(true).build();
            // Perform the search.
            final Map<String, ResultBuilder<?>> resultBuilderMap = new HashMap<>();
            for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
                final TableResultConsumer tableResultConsumer =
                        new TableResultConsumer(alertRuleDoc, recordConsumer, timeFilter);
                resultBuilderMap.put(resultRequest.getComponentId(), tableResultConsumer);
            }

            resultStoreManager.search(searchRequest, resultBuilderMap);

            // Remember last successful execution.
            lastExecutionTimes.put(alertRuleDoc, to);
        } else {
            LOGGER.info(() -> LogUtil.message("No result store found to try alert query: {}", alertRuleDoc.getUuid()));
        }
    }

    private Instant roundDown(final Instant instant, final Duration duration) {
        if (duration.toMillis() < 1000) {
            return instant.truncatedTo(ChronoUnit.MILLIS);
        } else if (duration.toSeconds() < 60) {
            return instant.truncatedTo(ChronoUnit.SECONDS);
        } else if (duration.toMinutes() < 60) {
            return instant.truncatedTo(ChronoUnit.MINUTES);
        } else if (duration.toHours() < 24) {
            return instant.truncatedTo(ChronoUnit.HOURS);
        } else {
            return instant.truncatedTo(ChronoUnit.DAYS);
        }
    }

    private static class TableResultConsumer implements TableResultBuilder {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultConsumer.class);

        private final AlertRuleDoc alertRuleDoc;
        private final RecordConsumer recordConsumer;
        private final TimeFilter timeFilter;

        private List<Field> fields;
        private int timeFieldIndex = -1;

        public TableResultConsumer(final AlertRuleDoc alertRuleDoc,
                                   final RecordConsumer recordConsumer,
                                   final TimeFilter timeFilter) {
            this.alertRuleDoc = alertRuleDoc;
            this.recordConsumer = recordConsumer;
            this.timeFilter = timeFilter;
        }

        @Override
        public TableResultConsumer componentId(final String componentId) {
            return this;
        }

        @Override
        public TableResultConsumer errors(final List<String> errors) {
            for (final String error : errors) {
                LOGGER.error(error);
            }
            return this;
        }

        @Override
        public TableResultConsumer fields(final List<Field> fields) {
            this.fields = fields;

            try {
                for (int i = 0; i < fields.size(); i++) {
                    if (timeFilter.timeField()
                            .equals(fields.get(i).getName())) {
                        timeFieldIndex = i;
                    }
                }
                if (timeFieldIndex == -1) {
                    throw new RuntimeException("Unable to find time field: " +
                            timeFilter.timeField());
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }

            return this;
        }

        @Override
        public TableResultConsumer addRow(final Row row) {
            try {
                final List<String> values = row.getValues();
                // See if we match the time filter.
                final String timeString = values.get(timeFieldIndex);
                Instant time;
                try {
                    time = Instant.ofEpochMilli(Long.parseLong(timeString));
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    time = DateUtil.parseNormalDateTimeStringToInstant(timeString);
                }

                if (timeFilter.from().isBefore(time) && timeFilter.to().isAfter(time)) {
                    // Match - dump record.
                    final List<Data> rows = new ArrayList<>();
                    rows.add(new Data(AlertManager.DETECT_TIME_DATA_ELEMENT_NAME_ATTR,
                            DateUtil.createNormalDateTimeString()));
                    rows.add(new Data("alertRuleUuid", alertRuleDoc.getUuid()));
                    rows.add(new Data("alertRuleName", alertRuleDoc.getName()));
                    for (int i = 0; i < fields.size(); i++) {
                        rows.add(new Data(fields.get(i).getName(), values.get(i)));
                    }
                    recordConsumer.accept(new Record(rows));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }

            return this;
        }

        @Override
        public TableResultConsumer resultRange(final OffsetRange resultRange) {
            return this;
        }

        @Override
        public TableResultConsumer totalResults(final Integer totalResults) {
            return this;
        }

        @Override
        public TableResult build() {
            return null;
        }
    }
}
