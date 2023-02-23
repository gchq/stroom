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

import stroom.alert.api.AlertDefinition;
import stroom.alert.api.AlertManager;
import stroom.alert.api.AlertProcessor;
import stroom.alert.rule.impl.AlertRuleStore;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.index.impl.IndexStructure;
import stroom.index.impl.IndexStructureCache;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.shared.QueryContext;
import stroom.search.extraction.ExtractionTaskHandler;
import stroom.search.impl.SearchConfig;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class AlertManagerImpl implements AlertManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AlertManagerImpl.class);

    private final TaskContext taskContext;
    private final ExplorerNodeService explorerNodeService;
    private final DashboardStore dashboardStore;
    private final WordListProvider wordListProvider;
    private final IndexStructureCache indexStructureCache;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final MultiValuesReceiverFactory multiValuesReceiverFactory;
    private final Provider<AlertConfig> alertConfigProvider;
    private final Provider<SearchConfig> searchConfigProvider;
    private final Provider<AlertRuleStore> alertRuleStoreProvider;
    private final AlertRuleSearchRequestHelper alertRuleSearchRequestHelper;
    private final Provider<ViewStore> viewStoreProvider;

    private volatile Map<DocRef, List<RuleConfig>> currentRules = new HashMap<>();
    private volatile Instant lastCacheUpdateTimeMs;

    @Inject
    AlertManagerImpl(final TaskContext taskContext,
                     final Provider<AlertConfig> alertConfigProvider,
                     final ExplorerNodeService explorerNodeService,
                     final DashboardStore dashboardStore,
                     final WordListProvider wordListProvider,
                     final Provider<SearchConfig> searchConfigProvider,
                     final IndexStructureCache indexStructureCache,
                     final PipelineStore pipelineStore,
                     final PipelineDataCache pipelineDataCache,
                     final Provider<ExtractionTaskHandler> handlerProvider,
                     final Provider<DetectionsWriter> detectionsWriterProvider,
                     final MultiValuesReceiverFactory multiValuesReceiverFactory,
                     final Provider<AlertRuleStore> alertRuleStoreProvider,
                     final AlertRuleSearchRequestHelper alertRuleSearchRequestHelper,
                     final Provider<ViewStore> viewStoreProvider) {
        this.taskContext = taskContext;
        this.alertConfigProvider = alertConfigProvider;
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
        this.wordListProvider = wordListProvider;
        this.searchConfigProvider = searchConfigProvider;
        this.indexStructureCache = indexStructureCache;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.handlerProvider = handlerProvider;
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.multiValuesReceiverFactory = multiValuesReceiverFactory;
        this.alertRuleStoreProvider = alertRuleStoreProvider;
        this.alertRuleSearchRequestHelper = alertRuleSearchRequestHelper;
        this.viewStoreProvider = viewStoreProvider;
    }

    @Override
    public Optional<AlertProcessor> createAlertProcessor(final DocRef indexDocRef) {
        final List<RuleConfig> rules = getRules().get(indexDocRef);
        if (rules != null && rules.size() > 0) {
            IndexStructure indexStructure = indexStructureCache.get(indexDocRef);

            if (indexStructure == null) {
                LOGGER.warn("Unable to locate index " + indexDocRef + " specified in rule");
                return Optional.empty();
            } else {

                final AlertProcessorImpl processor = new AlertProcessorImpl(
                        taskContext,
                        handlerProvider,
                        detectionsWriterProvider,
                        multiValuesReceiverFactory,
                        rules,
                        indexStructure,
                        pipelineStore,
                        pipelineDataCache,
                        wordListProvider,
                        searchConfigProvider.get().getMaxBooleanClauseCount(),
                        DateTimeSettings.builder()
                                .localZoneId(alertConfigProvider.get().getTimezone())
                                .build());
                return Optional.of(processor);
            }
        }
        return Optional.empty();
    }

    private Map<DocRef, List<RuleConfig>> getRules() {
        Map<DocRef, List<RuleConfig>> rules = currentRules;
        if (lastCacheUpdateTimeMs == null) {
            rules = updateRules();
        }
        return rules;
    }

    @Override
    public void refreshRules() {
        updateRules();
    }

    private synchronized Map<DocRef, List<RuleConfig>> updateRules() {
        Map<DocRef, List<RuleConfig>> rules = currentRules;
        if (lastCacheUpdateTimeMs == null
                || lastCacheUpdateTimeMs.isBefore(Instant.now().minus(Duration.ofMinutes(1)))) {
            rules = new HashMap<>();
            // Add the special folder rules.
            addSpecialFolderRules(rules);
            // Add rules based on alert rule entities.
            addAlertRules(rules);

            currentRules = rules;
            lastCacheUpdateTimeMs = Instant.now();
        }
        return rules;
    }

    private List<String> findRulesPaths() {
        return alertConfigProvider.get().getRulesFolderList();
    }

    private DocRef getFolderForPath(String folderPath) {
        if (folderPath == null || folderPath.length() < 3) {
            return null;
        }

        final String[] path = folderPath.trim().split("/");

        final Optional<ExplorerNode> folder = explorerNodeService.getNodeWithRoot();

        if (folder.isEmpty()) {
            throw new IllegalStateException("Unable to find root folder explorer node.");
        }
        ExplorerNode currentNode = folder.get();

        for (final String name : path) {
            List<ExplorerNode> matchingChildren =
                    explorerNodeService.getNodesByName(currentNode, name).stream().filter(explorerNode ->
                                    ExplorerConstants.FOLDER.equals(explorerNode.getDocRef().getType()))
                            .toList();

            if (matchingChildren.size() == 0) {
                return null;
            } else if (matchingChildren.size() > 1) {
                final ExplorerNode node = currentNode;
                LOGGER.warn(() -> "There are multiple folders called " + name + " under " + node.getName() +
                        " when opening rules path " + Arrays.toString(path) + " using first...");
            }
            currentNode = matchingChildren.get(0);
        }

        return currentNode.getDocRef();
    }

    private static Map<String, String> parseParms(String paramExpression) {
        //todo implement this!
        if (paramExpression != null && paramExpression.trim().length() > 0) {
            LOGGER.error("Error currently unable to use parameters in dashboards used as rules");
        }

        return new HashMap<>();
    }

    private void addSpecialFolderRules(final Map<DocRef, List<RuleConfig>> indexToRules) {
        List<String> rulesPaths = findRulesPaths();
        if (rulesPaths == null) {
            return;
        }

        LOGGER.debug("Refreshing rules");
        for (String rulesPath : rulesPaths) {
            final DocRef rulesFolder = getFolderForPath(rulesPath);

            if (rulesFolder == null) {
                LOGGER.warn("The specified rule folder \"" + rulesPath + "\" does not exist.");
                continue;
            }
            LOGGER.info("Loading alerting rules from " + rulesPath);

            List<ExplorerNode> childNodes = explorerNodeService.getDescendants(rulesFolder);

            for (ExplorerNode childNode : childNodes) {
                if (DashboardDoc.DOCUMENT_TYPE.equals(childNode.getDocRef().getType())) {
                    DashboardDoc dashboard = dashboardStore.readDocument(childNode.getDocRef());
                    String childPath = explorerNodeService
                            .getPath(childNode.getDocRef())
                            .stream()
                            .map(ExplorerNode::getName)
                            .collect(Collectors.joining("/"));
                    Map<String, String> paramMap = parseParms(dashboard.getDashboardConfig().getParameters());

                    final List<ComponentConfig> componentConfigs = dashboard.getDashboardConfig().getComponents();
                    for (ComponentConfig componentConfig : componentConfigs) {
                        if (componentConfig.getSettings() instanceof QueryComponentSettings) {
                            QueryComponentSettings queryComponentSettings =
                                    (QueryComponentSettings) componentConfig.getSettings();
                            final String queryId = componentConfig.getId();
                            ExpressionOperator expression = queryComponentSettings.getExpression();
                            DocRef dataSource = queryComponentSettings.getDataSource();

                            Map<DocRef, List<AlertDefinition>> pipelineTableSettings = new HashMap<>();

                            // Find all the tables associated with this query
                            for (ComponentConfig associatedComponentConfig : componentConfigs) {
                                if (associatedComponentConfig.getSettings() instanceof TableComponentSettings) {
                                    final TableComponentSettings tableComponentSettings =
                                            (TableComponentSettings) associatedComponentConfig.getSettings();

                                    DocRef pipeline = tableComponentSettings.getExtractionPipeline();
                                    if (pipeline != null && tableComponentSettings.getQueryId().equals(queryId)) {
                                        if (!pipelineTableSettings.containsKey(pipeline)) {
                                            pipelineTableSettings.put(pipeline, new ArrayList<>());
                                        }

                                        final TableSettings tableSettings = TableSettings.builder()
                                                .queryId(tableComponentSettings.getQueryId())
                                                .addFields(tableComponentSettings.getFields())
                                                .extractValues(tableComponentSettings.extractValues())
                                                .extractionPipeline(tableComponentSettings.getExtractionPipeline())
                                                .addMaxResults(tableComponentSettings.getMaxResults())
                                                .showDetail(tableComponentSettings.getShowDetail())
                                                .build();

                                        AlertDefinition alertDefinition = new AlertDefinition(tableSettings,
                                                Map.of(AlertManager.DASHBOARD_NAME_KEY, dashboard.getName(),
                                                        AlertManager.RULES_FOLDER_KEY, childPath,
                                                        AlertManager.TABLE_NAME_KEY,
                                                        associatedComponentConfig.getName()));
                                        pipelineTableSettings.get(pipeline).add(alertDefinition);
                                    }
                                }
                            }

                            // Now split out by pipeline
                            for (DocRef pipeline : pipelineTableSettings.keySet()) {
                                final DashboardRuleConfig rule = new DashboardRuleConfig(
                                        dashboard.getUuid(),
                                        queryId,
                                        paramMap,
                                        expression,
                                        pipeline,
                                        pipelineTableSettings.get(pipeline));
                                if (!indexToRules.containsKey(dataSource)) {
                                    indexToRules.put(dataSource, new ArrayList<>());
                                }
                                indexToRules.get(dataSource).add(rule);
                            }
                        }
                    }
                }
            }
        }
    }

    private void addAlertRules(final Map<DocRef, List<RuleConfig>> indexToRules) {
        final AlertRuleStore alertRuleStore = alertRuleStoreProvider.get();
        final List<DocRef> list = alertRuleStore.list();
        for (final DocRef docRef : list) {
            try {
                final AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(docRef);
                addAlertRule(alertRuleDoc, indexToRules);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private void addAlertRule(final AlertRuleDoc alertRule,
                              final Map<DocRef, List<RuleConfig>> indexToRules) {
        if (alertRule.isEnabled()) {
            final SearchRequest searchRequest = alertRuleSearchRequestHelper.create(alertRule);

            // If the datasource is a view then resolve underlying data source and extraction.
            DocRef dataSource = searchRequest.getQuery().getDataSource();
            DocRef extractionPipeline = null;
            if (dataSource != null) {
                if (ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                    final ViewDoc viewDoc = viewStoreProvider.get().readDocument(dataSource);
                    if (viewDoc != null) {
                        dataSource = viewDoc.getDataSource();
                        extractionPipeline = viewDoc.getPipeline();
                    }
                }

                final QueryContext queryContext = QueryContext.builder().build();
                ExpressionOperator expression = searchRequest.getQuery().getExpression();

                if (extractionPipeline != null) {
                    final TableSettings tableSettings = searchRequest.getResultRequests().get(0).getMappings().get(0);
                    final AlertRuleConfig rule = new AlertRuleConfig(
                            alertRule,
                            queryContext,
                            expression,
                            extractionPipeline,
                            tableSettings,
                            searchRequest);
                    indexToRules.computeIfAbsent(dataSource, k -> new ArrayList<>()).add(rule);
                }
            }
        }
    }
}
