/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.docref.DocRef;
import stroom.expression.api.ExpressionContext;
import stroom.meta.shared.Meta;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldListConsumerHolder;
import stroom.search.extraction.FieldValue;
import stroom.search.extraction.MemoryIndex;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class StreamingAnalyticDataProcessorDecorator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(StreamingAnalyticDataProcessorDecorator.class);

    private final AnalyticRuleStore analyticRuleStore;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final AnalyticHelper analyticHelper;
    private final ExpressionContextFactory expressionContextFactory;
    private final MemoryIndex memoryIndex;
    private final NotificationStateService notificationStateService;
    private final DetectionConsumerFactory detectionConsumerFactory;
    private final DetectionConsumerProxy detectionConsumerProxy;
    private final FieldListConsumerHolder fieldListConsumerHolder;

    private AnalyticFieldListConsumer fieldListConsumer;

    @Inject
    public StreamingAnalyticDataProcessorDecorator(final AnalyticRuleStore analyticRuleStore,
                                                   final AnalyticRuleSearchRequestHelper
                                                           analyticRuleSearchRequestHelper,
                                                   final AnalyticHelper analyticHelper,
                                                   final ExpressionContextFactory expressionContextFactory,
                                                   final MemoryIndex memoryIndex,
                                                   final NotificationStateService notificationStateService,
                                                   final DetectionConsumerFactory detectionConsumerFactory,
                                                   final DetectionConsumerProxy detectionConsumerProxy,
                                                   final FieldListConsumerHolder fieldListConsumerHolder) {
        this.analyticRuleStore = analyticRuleStore;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.analyticHelper = analyticHelper;
        this.expressionContextFactory = expressionContextFactory;
        this.memoryIndex = memoryIndex;
        this.notificationStateService = notificationStateService;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.detectionConsumerProxy = detectionConsumerProxy;
        this.fieldListConsumerHolder = fieldListConsumerHolder;
    }

    public String getErrorFeedName(final ProcessorFilter processorFilter, final Meta meta) {
        if (processorFilter != null &&
                processorFilter.getQueryData() != null &&
                processorFilter.getQueryData().getAnalyticRule() != null) {
            // Load rule.
            final StreamingAnalytic analytic = loadStreamingAnalytic(processorFilter.getQueryData().getAnalyticRule());
            if (analytic.streamingAnalyticProcessConfig() != null &&
                    analytic.streamingAnalyticProcessConfig().getErrorFeed() != null &&
                    analytic.streamingAnalyticProcessConfig().getErrorFeed().getName() != null) {
                return analytic.streamingAnalyticProcessConfig.getErrorFeed().getName();
            }
        }
        return meta.getFeedName();
    }

    public void start(final ProcessorFilter processorFilter) {
        if (processorFilter != null &&
                processorFilter.getQueryData() != null &&
                processorFilter.getQueryData().getAnalyticRule() != null) {
            // Load rule.
            final StreamingAnalytic analytic = loadStreamingAnalytic(processorFilter.getQueryData().getAnalyticRule());
            fieldListConsumer = createEventConsumer(analytic).orElse(new NullFieldListConsumer());
            fieldListConsumerHolder.setFieldListConsumer(fieldListConsumer);

            fieldListConsumer.start();
        }
    }

    public void end() {
        if (fieldListConsumer != null) {
            fieldListConsumer.end();
        }
    }


    private Optional<AnalyticFieldListConsumer> createEventConsumer(final StreamingAnalytic analytic) {
        // Create field index.
        final SearchRequest searchRequest = analytic.searchRequest();
        final ExpressionContext expressionContext = expressionContextFactory
                .createContext(searchRequest);
        final TableSettings tableSettings = searchRequest.getResultRequests().get(0).getMappings().get(0);
        final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
        final CompiledFields compiledFields = CompiledFields.create(expressionContext,
                tableSettings.getFields(),
                paramMap);
        final FieldIndex fieldIndex = compiledFields.getFieldIndex();

        // Determine if notifications have been disabled.
        final NotificationState notificationState = notificationStateService.getState(analytic.analyticRuleDoc);
        // Only execute if the state is enabled.
        notificationState.enableIfPossible();
        if (notificationState.isEnabled()) {
            try {
                final Provider<DetectionConsumer> detectionConsumerProvider =
                        detectionConsumerFactory.create(analytic.analyticRuleDoc);
                detectionConsumerProxy.setAnalyticRuleDoc(analytic.analyticRuleDoc());
                detectionConsumerProxy.setCompiledFields(compiledFields);
                detectionConsumerProxy.setFieldIndex(fieldIndex);
                detectionConsumerProxy.setDetectionsConsumerProvider(detectionConsumerProvider);

                return Optional.of(new StreamingAnalyticFieldListConsumer(
                        searchRequest,
                        fieldIndex,
                        notificationState,
                        detectionConsumerProxy,
                        memoryIndex,
                        null,
                        detectionConsumerProxy));

            } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }
        return Optional.empty();
    }

    private StreamingAnalytic loadStreamingAnalytic(final DocRef analyticRule) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Loading rule");
        final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(analyticRule);

        ViewDoc viewDoc;

        // Try and get view.
        final String ruleIdentity = AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc);
        final SearchRequest searchRequest = analyticRuleSearchRequestHelper
                .create(analyticRuleDoc);
        final DocRef dataSource = searchRequest.getQuery().getDataSource();

        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
            throw new RuntimeException("Error: Rule needs to reference a view");

        } else {
            // Load view.
            viewDoc = analyticHelper.loadViewDoc(ruleIdentity, dataSource);
        }

        if (!(analyticRuleDoc.getAnalyticProcessConfig()
                instanceof StreamingAnalyticProcessConfig)) {
            LOGGER.debug("Error: Invalid process config {}", ruleIdentity);
            throw new RuntimeException("Error: Invalid process config.");

        } else {
            info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
            return new StreamingAnalytic(
                    ruleIdentity,
                    analyticRuleDoc,
                    (StreamingAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig(),
                    searchRequest,
                    viewDoc);
        }
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
    }

    private record StreamingAnalytic(String ruleIdentity,
                                     AnalyticRuleDoc analyticRuleDoc,
                                     StreamingAnalyticProcessConfig streamingAnalyticProcessConfig,
                                     SearchRequest searchRequest,
                                     ViewDoc viewDoc) {

    }

    private static class NullFieldListConsumer implements AnalyticFieldListConsumer {

        @Override
        public void start() {

        }

        @Override
        public void end() {

        }

        @Override
        public void accept(final List<FieldValue> fieldValues) {

        }
    }
}
