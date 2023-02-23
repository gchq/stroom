/*
 * Copyright 2020 Crown Copyright
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

package stroom.alert.impl;

import stroom.alert.api.AlertProcessor;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStructure;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.impl.analyzer.AnalyzerFactory;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.ErrorConsumer;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.search.extraction.ExtractionException;
import stroom.search.extraction.ExtractionTaskHandler;
import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Provider;

public class AlertProcessorImpl implements AlertProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AlertProcessorImpl.class);

    private static final DocRef NULL_SELECTION =
            DocRef.builder().uuid("").name("None").type("").build();

    private final AlertQueryHits alertQueryHits;

    private final WordListProvider wordListProvider;
    private final int maxBooleanClauseCount;
    private final IndexStructure indexStructure;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;

    private final List<RuleConfig> rules;

    private final Map<String, Analyzer> analyzerMap;

    private final TaskContext taskContext;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final MultiValuesReceiverFactory multiValuesReceiverFactory;

    private Long currentStreamId = null;

    private final DateTimeSettings dateTimeSettings;

    private final Map<DocRef, PipelineData> pipelineDataMap = new ConcurrentHashMap<>();

    public AlertProcessorImpl(final TaskContext taskContext,
                              final Provider<ExtractionTaskHandler> handlerProvider,
                              final Provider<DetectionsWriter> detectionsWriterProvider,
                              final MultiValuesReceiverFactory multiValuesReceiverFactory,
                              final List<RuleConfig> rules,
                              final IndexStructure indexStructure,
                              final PipelineStore pipelineStore,
                              final PipelineDataCache pipelineDataCache,
                              final WordListProvider wordListProvider,
                              final int maxBooleanClauseCount,
                              final DateTimeSettings dateTimeSettings) {
        this.rules = rules;
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = maxBooleanClauseCount;
        this.indexStructure = indexStructure;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.analyzerMap = new HashMap<>();
        if (indexStructure.getIndexFields() != null) {
            for (final IndexField indexField : indexStructure.getIndexFields()) {
                // Add the field analyser.
                final Analyzer analyzer = AnalyzerFactory.create(indexField.getAnalyzerType(),
                        indexField.isCaseSensitive());
                analyzerMap.put(indexField.getFieldName(), analyzer);
            }
        }
        alertQueryHits = new AlertQueryHits();
        this.taskContext = taskContext;
        this.handlerProvider = handlerProvider;
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.multiValuesReceiverFactory = multiValuesReceiverFactory;
        this.dateTimeSettings = dateTimeSettings;
    }

    @Override
    public void addIfNeeded(final Document document) {

        Long streamId = findStreamId(document);
        if (streamId == null) {
            LOGGER.warn("Unable to locate StreamId for document, alerting disabled for this stream");
            return;
        }
        if (currentStreamId == null) {
            currentStreamId = streamId;
        }
        if (currentStreamId.longValue() != streamId.longValue()) {
            throw new IllegalStateException("Unable to reuse AlertProcessorImpl for more than single stream" +
                    " was created with streamid " + currentStreamId +
                    " now applied to streamid " + streamId);
        }

        MemoryIndex memoryIndex = new MemoryIndex();
        if (analyzerMap == null || analyzerMap.size() == 0) {
            //Don't create alerts if index isn't configured
            return;
        }

        Long eventId = findEventId(document);
        if (eventId == null) {
            LOGGER.warn("Unable to locate event id processing alerts for stream " + streamId);
            return;
        }

        for (IndexableField field : document) {

            Analyzer fieldAnalyzer = analyzerMap.get(field.name());

            if (fieldAnalyzer != null) {
                TokenStream tokenStream = field.tokenStream(fieldAnalyzer, null);
                if (tokenStream != null) {
                    memoryIndex.addField(field.name(), tokenStream, field.boost());
                }
            }
        }

        checkRules(eventId, memoryIndex);
    }

    private void checkRules(final long eventId, final MemoryIndex memoryIndex) {
        if (rules == null) {
            return;
        }

        IndexSearcher indexSearcher = memoryIndex.createSearcher();
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexStructure.getIndex().getFields());
        try {
            for (RuleConfig rule : rules) {
                if (matchQuery(indexSearcher, indexFieldsMap, rule.getExpression())) {
                    //This query matches - now apply filters

                    //First get the original event XML

                    //                 System.out.println ("Found a matching query rule");

                    alertQueryHits.addQueryHitForRule(rule, eventId);
                    LOGGER.debug(() -> LogUtil.message(
                            "Adding {}:{} to rule {}",
                            currentStreamId,
                            eventId, rule.getName()));
                } else {
                    LOGGER.trace(() -> LogUtil.message(
                            "Not adding {}:{} to rule {}",
                            currentStreamId,
                            eventId, rule.getName()));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to create alerts", ex);
        }
    }

    @Override
    public void createAlerts() {
        LOGGER.debug("Creating alerts...");

        int numTasks = 0;
        for (DocRef pipeline : alertQueryHits.getExtractionPipelines()) {
            LOGGER.trace("Iterating pipeline {}", pipeline.getName());
            Collection<RuleConfig> rulesForPipeline = alertQueryHits.getRulesForPipeline(pipeline);
            for (RuleConfig ruleConfig : rulesForPipeline) {
                LOGGER.trace("--Iterating ruleConfig {}", ruleConfig.getName());
                long[] eventIds = alertQueryHits.getSortedQueryHitsForRule(ruleConfig);
                if (eventIds != null && eventIds.length > 0) {
                    final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                    final MultiValuesConsumer multiValuesConsumer =
                            multiValuesReceiverFactory.create(ruleConfig, detectionsWriter);

                    final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
                    try {
                        detectionsWriter.start();
                        final PipelineData pipelineData = getPipelineData(pipeline);
                        handlerProvider.get().extract(
                                taskContext,
                                new QueryKey("alert"),
                                currentStreamId,
                                eventIds,
                                pipeline,
                                multiValuesConsumer,
                                errorConsumer,
                                pipelineData);
                        numTasks++;
                    } finally {
                        detectionsWriter.end();
                    }
                }
            }

        }
        LOGGER.debug("Created {} search extraction tasks for stream id {}", numTasks, currentStreamId);
        alertQueryHits.clearHits();
    }

    private PipelineData getPipelineData(final DocRef pipelineRef) {
        return pipelineDataMap.computeIfAbsent(pipelineRef, k -> {
            // Check the pipelineRef is not our 'NULL SELECTION'
            if (pipelineRef == null || NULL_SELECTION.compareTo(pipelineRef) == 0) {
                throw new ExtractionException("Extraction is enabled, but no extraction pipeline is configured.");
            }

            // Get the translation that will be used to display results.
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            if (pipelineDoc == null) {
                throw new ExtractionException("Unable to find result pipeline: " + pipelineRef);
            }

            // Create the parser.
            return pipelineDataCache.get(pipelineDoc);
        });
    }

    private boolean matchQuery(final IndexSearcher indexSearcher,
                               final IndexFieldsMap indexFieldsMap,
                               final ExpressionOperator query) throws IOException {

        try {
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider,
                    indexFieldsMap,
                    maxBooleanClauseCount,
                    dateTimeSettings,
                    System.currentTimeMillis());
            final SearchExpressionQueryBuilder.SearchExpressionQuery luceneQuery = searchExpressionQueryBuilder
                    .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, query);

            TopDocs docs = indexSearcher.search(luceneQuery.getQuery(), 100);

            if (docs.totalHits == 0) {
                return false; //Doc does not match
            } else if (docs.totalHits == 1) {
                return true; //Doc matches
            } else {
                LOGGER.error("Unexpected number of documents {}  found by rule, should be 1 or 0.", docs.totalHits);
            }

        } catch (SearchException se) {
            LOGGER.warn("Unable to create alerts for rule " + query + " due to " + se.getMessage());
        }

        return false;
    }

    private static Long findEventId(final Document document) {
        try {
            return document.getField("EventId").numericValue().longValue();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Long findStreamId(final Document document) {
        try {
            return document.getField("StreamId").numericValue().longValue();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
