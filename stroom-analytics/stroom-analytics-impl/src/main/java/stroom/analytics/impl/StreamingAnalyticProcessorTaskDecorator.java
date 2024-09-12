/*
 * Copyright 2017-2024 Crown Copyright
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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.core.dataprocess.ProcessorTaskDecorator;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldListConsumerHolder;
import stroom.search.extraction.FieldValue;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.FieldValueExtractorFactory;
import stroom.search.extraction.MemoryIndex;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StreamingAnalyticProcessorTaskDecorator implements ProcessorTaskDecorator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(StreamingAnalyticProcessorTaskDecorator.class);

    private final StreamingAnalyticCache streamingAnalyticCache;
    private final ExpressionContextFactory expressionContextFactory;
    private final MemoryIndex memoryIndex;
    private final DetectionConsumerFactory detectionConsumerFactory;
    private final DetectionConsumerProxy detectionConsumerProxy;
    private final FieldListConsumerHolder fieldListConsumerHolder;
    private final FieldValueExtractorFactory fieldValueExtractorFactory;

    private AnalyticFieldListConsumer fieldListConsumer;

    private StreamingAnalytic analytic;
    private String userDefinedErrorFeedName;

    @Inject
    public StreamingAnalyticProcessorTaskDecorator(final StreamingAnalyticCache streamingAnalyticCache,
                                                   final ExpressionContextFactory expressionContextFactory,
                                                   final MemoryIndex memoryIndex,
                                                   final DetectionConsumerFactory detectionConsumerFactory,
                                                   final DetectionConsumerProxy detectionConsumerProxy,
                                                   final FieldListConsumerHolder fieldListConsumerHolder,
                                                   final FieldValueExtractorFactory fieldValueExtractorFactory) {
        this.streamingAnalyticCache = streamingAnalyticCache;
        this.expressionContextFactory = expressionContextFactory;
        this.memoryIndex = memoryIndex;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.detectionConsumerProxy = detectionConsumerProxy;
        this.fieldListConsumerHolder = fieldListConsumerHolder;
        this.fieldValueExtractorFactory = fieldValueExtractorFactory;
    }

    @Override
    public void init(final ProcessorFilter processorFilter) {
        // Load rule.
        final DocRef analyticRuleRef = new DocRef(AnalyticRuleDoc.DOCUMENT_TYPE, processorFilter.getPipelineUuid());
        final Optional<StreamingAnalytic> optional = streamingAnalyticCache.get(analyticRuleRef);
        if (optional.isEmpty()) {
            throw new RuntimeException("Unable to get analytic from cache: " + analyticRuleRef);
        }
        analytic = optional.get();
        final AnalyticRuleDoc doc = analytic.analyticRuleDoc();
        if (doc != null && doc.getErrorFeed() != null) {
            userDefinedErrorFeedName = doc.getErrorFeed().getName();
        }
    }

    @Override
    public DocRef getPipeline() {
        return analytic.viewDoc().getPipeline();
    }

    @Override
    public String getErrorFeedName(final Meta meta) {
        if (userDefinedErrorFeedName != null) {
            return userDefinedErrorFeedName;
        }
        return meta.getFeedName();
    }

    @Override
    public void beforeProcessing() {
        fieldListConsumer = createEventConsumer(analytic).orElse(new NullFieldListConsumer());
        fieldListConsumerHolder.setFieldListConsumer(fieldListConsumer);
        fieldListConsumer.start();
    }

    @Override
    public void afterProcessing() {
        fieldListConsumer.end();
    }

    private Optional<AnalyticFieldListConsumer> createEventConsumer(final StreamingAnalytic analytic) {
        // Create field index.
        final SearchRequest searchRequest = analytic.searchRequest();
        final ExpressionContext expressionContext = expressionContextFactory
                .createContext(searchRequest);
        final TableSettings tableSettings = searchRequest.getResultRequests().getFirst().getMappings().getFirst();
        final Map<CIKey, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
        final CompiledColumns compiledColumns = CompiledColumns.create(expressionContext,
                tableSettings.getColumns(),
                paramMap);
        final FieldIndex fieldIndex = compiledColumns.getFieldIndex();

        try {
            final Provider<DetectionConsumer> detectionConsumerProvider =
                    detectionConsumerFactory.create(analytic.analyticRuleDoc());
            detectionConsumerProxy.setAnalyticRuleDoc(analytic.analyticRuleDoc());
            detectionConsumerProxy.setCompiledColumns(compiledColumns);
            detectionConsumerProxy.setDetectionsConsumerProvider(detectionConsumerProvider);

            final FieldValueExtractor fieldValueExtractor = fieldValueExtractorFactory
                    .create(searchRequest.getQuery().getDataSource(), fieldIndex);

            return Optional.of(new StreamingAnalyticFieldListConsumer(
                    searchRequest,
                    fieldIndex,
                    fieldValueExtractor,
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

    private static class NullFieldListConsumer implements AnalyticFieldListConsumer {

        @Override
        public void start() {

        }

        @Override
        public void end() {

        }

        @Override
        public void acceptFieldValues(final List<FieldValue> fieldValues) {

        }

        @Override
        public void acceptStringValues(final List<StringFieldValue> stringValues) {

        }
    }
}
