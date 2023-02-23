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
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TimeRange;
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
import java.util.List;
import java.util.Map;
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

    private String feedName;

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

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            pipelineScopeRunnable.scopeRunnable(() -> {
                final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                detectionsWriter.setFeedName(feedName);
                detectionsWriter.start();
                try {
                    final AlertRuleStore alertRuleStore = alertRuleStoreProvider.get();
                    final List<DocRef> docRefList = alertRuleStore.list();
                    for (final DocRef docRef : docRefList) {
                        final AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(docRef);
                        if (AlertRuleType.THRESHOLD.equals(alertRuleDoc.getAlertRuleType())) {
                            final AbstractAlertRule alertRule = alertRuleDoc.getAlertRule();
                            if (alertRule instanceof ThresholdAlertRule) {
                                execThresholdAlertRule(alertRuleDoc, (ThresholdAlertRule) alertRule, detectionsWriter);
                            }
                        }
                    }
                } finally {
                    detectionsWriter.end();
                }
            });
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
        final QueryKey queryKey = new QueryKey(alertRuleDoc.getUuid());
        final Optional<ResultStore> optionalResultStore = resultStoreManager.getIfPresent(queryKey);
        if (optionalResultStore.isPresent()) {
            // Create a search request.
            SearchRequest searchRequest = alertRuleSearchRequestHelper.create(alertRuleDoc);
            // Narrow the search time range.
            final TimeRange timeRange = new TimeRange(
                    "Custom",
                    DateUtil.createNormalDateTimeString(from),
                    DateUtil.createNormalDateTimeString(to));
            final Query query = searchRequest.getQuery().copy().timeRange(timeRange).build();
            searchRequest = searchRequest.copy().key(queryKey).query(query).incremental(true).build();
            // Perform the search.
            final SearchResponse searchResponse = resultStoreManager.search(searchRequest);
            if (searchResponse.getErrors() != null) {
                for (final String error : searchResponse.getErrors()) {
                    LOGGER.error(error);
                }
            }

            final List<Result> results = searchResponse.getResults();
            if (results != null) {
                for (final Result result : results) {
                    if (result instanceof TableResult) {
                        final TableResult tableResult = (TableResult) result;

                        int thresholdFieldIndex = -1;
                        for (int i = 0; i < tableResult.getFields().size(); i++) {
                            if (thresholdAlertRule.getThresholdField() != null &&
                                    thresholdAlertRule.getThresholdField()
                                            .equals(tableResult.getFields().get(i).getName())) {
                                thresholdFieldIndex = i;
                                break;
                            }
                        }

                        if (thresholdFieldIndex == -1) {
                            throw new RuntimeException("Unable to find threshold field: " +
                                    thresholdAlertRule.getThresholdField());
                        }

                        for (final Row row : tableResult.getRows()) {
                            final List<String> values = row.getValues();
                            // See if the threshold field value has been exceeded.
                            final String stringValue = values.get(thresholdFieldIndex);
                            try {
                                final long value = Long.parseLong(stringValue);
                                if (value > thresholdAlertRule.getThreshold()) {
                                    // Match - dump record.
                                    final List<Data> rows = new ArrayList<>();
                                    rows.add(new Data(AlertManager.DETECT_TIME_DATA_ELEMENT_NAME_ATTR,
                                            DateUtil.createNormalDateTimeString()));
                                    rows.add(new Data("alertRuleUuid", alertRuleDoc.getUuid()));
                                    rows.add(new Data("alertRuleName", alertRuleDoc.getName()));
                                    for (int i = 0; i < tableResult.getFields().size(); i++) {
                                        rows.add(new Data(tableResult.getFields().get(i).getName(), values.get(i)));
                                    }
                                    recordConsumer.accept(new Record(rows));
                                }
                            } catch (final NumberFormatException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    }
                }
            }

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
}
