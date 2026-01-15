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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.core.dataprocess.ProcessorTaskDecorator;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ParamUtil;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.common.v2.ValFilter;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
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

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final WordListProvider wordListProvider;

    private AnalyticFieldListConsumer fieldListConsumer;

    private StreamingAnalytic analytic;

    @Inject
    public StreamingAnalyticProcessorTaskDecorator(final StreamingAnalyticCache streamingAnalyticCache,
                                                   final ExpressionContextFactory expressionContextFactory,
                                                   final MemoryIndex memoryIndex,
                                                   final DetectionConsumerFactory detectionConsumerFactory,
                                                   final DetectionConsumerProxy detectionConsumerProxy,
                                                   final FieldListConsumerHolder fieldListConsumerHolder,
                                                   final FieldValueExtractorFactory fieldValueExtractorFactory,
                                                   final ExpressionPredicateFactory expressionPredicateFactory,
                                                   final WordListProvider wordListProvider) {
        this.streamingAnalyticCache = streamingAnalyticCache;
        this.expressionContextFactory = expressionContextFactory;
        this.memoryIndex = memoryIndex;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.detectionConsumerProxy = detectionConsumerProxy;
        this.fieldListConsumerHolder = fieldListConsumerHolder;
        this.fieldValueExtractorFactory = fieldValueExtractorFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.wordListProvider = wordListProvider;
    }

    @Override
    public void beforeProcessing(final ProcessorFilter processorFilter) {
        // Load rule.
        final DocRef analyticRuleRef = new DocRef(AnalyticRuleDoc.TYPE, processorFilter.getPipelineUuid());
        analytic = streamingAnalyticCache.get(analyticRuleRef);
        if (analytic == null) {
            throw new RuntimeException("Unable to get analytic from cache: " + analyticRuleRef);
        }

        fieldListConsumer = createEventConsumer(analytic).orElse(new NullFieldListConsumer());
        fieldListConsumerHolder.setFieldListConsumer(fieldListConsumer);
        fieldListConsumer.start();
    }

    @Override
    public void afterProcessing() {
        if (fieldListConsumer != null) {
            fieldListConsumer.end();
        }
    }

    @Override
    public DocRef getPipeline() {
        return analytic.viewDoc().getPipeline();
    }

    private Optional<AnalyticFieldListConsumer> createEventConsumer(final StreamingAnalytic analytic) {
        // Create field index.
        final SearchRequest searchRequest = analytic.searchRequest();
        final ExpressionContext expressionContext = expressionContextFactory
                .createContext(searchRequest);
        final TableSettings tableSettings = searchRequest.getResultRequests().getFirst().getMappings().getFirst();
        final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
        final CompiledColumns compiledColumns = CompiledColumns.create(expressionContext,
                tableSettings.getColumns(),
                paramMap);
        final FieldIndex fieldIndex = compiledColumns.getFieldIndex();

        // We need to filter values as we aren't using the LMDB consumer that normally does this for us.
        final Predicate<Val[]> valFilter = ValFilter.create(
                tableSettings.getValueFilter(),
                compiledColumns,
                searchRequest.getDateTimeSettings(),
                expressionPredicateFactory,
                paramMap, wordListProvider);

        try {
            final Provider<DetectionConsumer> detectionConsumerProvider =
                    detectionConsumerFactory.create(analytic.analyticRuleDoc());
            detectionConsumerProxy.setAnalyticRuleDoc(analytic.analyticRuleDoc());
            detectionConsumerProxy.setCompiledColumns(compiledColumns);
            detectionConsumerProxy.setDetectionsConsumerProvider(detectionConsumerProvider);
            detectionConsumerProxy.setValFilter(valFilter);

            final FieldValueExtractor fieldValueExtractor = fieldValueExtractorFactory
                    .create(searchRequest.getQuery().getDataSource(), fieldIndex);

            return Optional.of(new StreamingAnalyticFieldListConsumer(
                    searchRequest,
                    compiledColumns,
                    fieldValueExtractor,
                    detectionConsumerProxy,
                    memoryIndex,
                    null,
                    detectionConsumerProxy,
                    valFilter));

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
