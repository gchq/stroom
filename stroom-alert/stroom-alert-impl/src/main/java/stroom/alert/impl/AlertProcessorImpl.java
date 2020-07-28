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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;

import stroom.alert.api.AlertDefinition;
import stroom.alert.api.AlertManager;
import stroom.alert.api.AlertProcessor;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.impl.TableSettingsUtil;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;

import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;

import stroom.index.impl.IndexStructure;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.impl.analyzer.AnalyzerFactory;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.ExpressionOperator;

import stroom.query.api.v2.Field;
import stroom.query.common.v2.CompiledFields;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.Values;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AlertProcessorImpl implements AlertProcessor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AlertProcessorImpl.class);

    private final AlertQueryHits alertQueryHits;

    private final WordListProvider wordListProvider;
    private final int maxBooleanClauseCount;
    private final IndexStructure indexStructure;

    private final List <RuleConfig> rules;

    private final Map<String, Analyzer> analyzerMap;

    private final ExtractionDecoratorFactory extractionDecoratorFactory;

    private Long currentStreamId = null;

    private final String locale;

    public AlertProcessorImpl (final ExtractionDecoratorFactory extractionDecoratorFactory,
                               final List<RuleConfig> rules,
                               final IndexStructure indexStructure,
                               final WordListProvider wordListProvider,
                               final int maxBooleanClauseCount,
                               final String locale){
        this.rules = rules;
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = maxBooleanClauseCount;
        this.indexStructure = indexStructure;
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
        this.extractionDecoratorFactory = extractionDecoratorFactory;
        this.locale = locale;
    }

    @Override
    public void addIfNeeded(final Document document) {

        Long streamId = findStreamId(document);
        if (streamId == null){
            LOGGER.warn("Unable to locate StreamId for document, alerting disabled for this stream");
            return;
        }
        if (currentStreamId == null){
            currentStreamId = streamId;
        }
        if (currentStreamId.longValue() != streamId.longValue()){
            throw new IllegalStateException("Unable to reuse AlertProcessorImpl for more than single stream" +
                    " was created with streamid " + currentStreamId +
                    " now applied to streamid " + streamId);
        }

        MemoryIndex memoryIndex = new MemoryIndex();
        if (analyzerMap == null || analyzerMap.size() == 0){
            //Don't create alerts if index isn't configured
            return;
        }

        Long eventId = findEventId (document);
        if (eventId == null) {
            LOGGER.warn("Unable to locate event id processing alerts for stream " + streamId);
            return;
        }

        for (IndexableField field : document){

            Analyzer fieldAnalyzer = analyzerMap.get(field.name());

            if (fieldAnalyzer != null){
                TokenStream tokenStream = field.tokenStream(fieldAnalyzer, null);
                if (tokenStream != null) {
                    memoryIndex.addField(field.name(),tokenStream, field.boost());
                }
            }
        }

        checkRules (eventId, memoryIndex);
    }

    private void checkRules (final long eventId, final MemoryIndex memoryIndex){
        if (rules == null) {
            return;
        }

        IndexSearcher indexSearcher = memoryIndex.createSearcher();
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexStructure.getIndex().getFields());
        try {
            for (RuleConfig rule : rules) {
                if (matchQuery(indexSearcher, indexFieldsMap, rule.getExpression())){
                    //This query matches - now apply filters

                    //First get the original event XML

   //                 System.out.println ("Found a matching query rule");

                    alertQueryHits.addQueryHitForRule(rule,eventId);
                    LOGGER.debug("Adding {}:{} to rule {} from dashboards {}", currentStreamId, eventId, rule.getQueryId(),
                            rule.getAlertDefinitions().stream()
                                .map(a -> a.getAttributes().get(AlertManager.DASHBOARD_NAME_KEY))
                                .collect(Collectors.joining(", ")));
                    ;
                } else {
                    LOGGER.trace("Not adding {}:{} to rule {} from dashboards {}", currentStreamId, eventId, rule.getQueryId(),
                            rule.getAlertDefinitions().stream()
                                    .map(a -> a.getAttributes().get(AlertManager.DASHBOARD_NAME_KEY))
                                    .collect(Collectors.joining(", ")));
                }
            }
        } catch (IOException ex){
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
            for (RuleConfig ruleConfig : rulesForPipeline){
                LOGGER.trace("--Iterating ruleConfig {}", ruleConfig.getQueryId());
                long[] eventIds = alertQueryHits.getSortedQueryHitsForRule(ruleConfig);
                if (eventIds != null && eventIds.length > 0) {
                    final Receiver receiver = new AlertProcessorReceiver(ruleConfig.getAlertDefinitions(),
                            ruleConfig.getParams());
                    extractionDecoratorFactory.createAlertExtractionTask(receiver,
                                currentStreamId, eventIds, pipeline,
                                ruleConfig.getAlertDefinitions(), ruleConfig.getParams());
                    numTasks++;
                }
            }

        }
        LOGGER.debug("Created {} search extraction tasks for stream id {}", numTasks, currentStreamId);
        alertQueryHits.clearHits();

    }

    private boolean matchQuery (final IndexSearcher indexSearcher, final IndexFieldsMap indexFieldsMap,
                                final ExpressionOperator query) throws IOException {

        try {
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider, indexFieldsMap, maxBooleanClauseCount, locale, System.currentTimeMillis());
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
        }
        catch (SearchException se){
            LOGGER.warn("Unable to create alerts for rule " + query + " due to " + se.getMessage());
        }
        return false;
    }

    private static Long findEventId(final Document document){
        try {
            return document.getField("EventId").numericValue().longValue();
        } catch (RuntimeException ex){
            return null;
        }
    }

    private static Long findStreamId(final Document document){
        try {
            return document.getField("StreamId").numericValue().longValue();
        } catch (RuntimeException ex){
            return null;
        }
    }

    private static class AlertProcessorReceiver implements Receiver {
        private final CompiledFields compiledFields;
        private final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);

        AlertProcessorReceiver (final List<AlertDefinition> alertDefinitions,
                                final Map<String, String> paramMap){

            final List<Field> fields = TableSettingsUtil.mapFields(
                    alertDefinitions.stream().map(a -> a.getTableComponentSettings().getFields())
                    .reduce(new ArrayList<>(), (a,b)->{a.addAll(b); return a;}));

            compiledFields = new CompiledFields(fields, fieldIndexMap, paramMap);
        }

        @Override
        public FieldIndexMap getFieldIndexMap() {
            return fieldIndexMap;
        }

        @Override
        public Consumer<Values> getValuesConsumer() {
            return values -> {};
        }

        @Override
        public Consumer<Error> getErrorConsumer() {
            return error -> LOGGER.error(error.getMessage(), error.getThrowable());
        }

        @Override
        public Consumer<Long> getCompletionCountConsumer() {
            return count -> {};
        }
    };
}
