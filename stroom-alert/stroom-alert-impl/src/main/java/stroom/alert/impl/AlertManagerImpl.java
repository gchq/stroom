package stroom.alert.impl;


import stroom.alert.api.AlertManager;


import stroom.alert.api.AlertProcessor;
import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.impl.TableSettingsUtil;
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
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.TableSettings;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.impl.SearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class AlertManagerImpl implements AlertManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AlertManagerImpl.class);

    private final ExplorerNodeService explorerNodeService;
    private final DashboardStore dashboardStore;
    private final int maxBooleanClauseCount;
    private final WordListProvider wordListProvider;
    private final IndexStructureCache indexStructureCache;
    private final ExtractionDecoratorFactory extractionDecoratorFactory;
    private Map<DocRef, List<RuleConfig>> indexToRules = new HashMap<>();
    //todo make into a clearable cache, and periodically refresh

    private boolean initialised = false;

    @Inject
    AlertManagerImpl (final ExplorerNodeService explorerNodeService,
                      final DashboardStore dashboardStore,
                      final WordListProvider wordListProvider,
                      final SearchConfig searchConfig,
                      final IndexStructureCache indexStructureCache,
                      final ExtractionDecoratorFactory extractionDecoratorFactory){
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
        this.indexStructureCache = indexStructureCache;
        this.extractionDecoratorFactory = extractionDecoratorFactory;
    }

    @Override
    public Optional<AlertProcessor> createAlertProcessor(final DocRef indexDocRef) {
        if (!initialised){
            initialiseCache();
            initialised = true;
        }

        IndexStructure indexStructure = indexStructureCache.get(indexDocRef);

        if (indexStructure == null) {
            LOGGER.warn ("Unable to locate index " + indexDocRef + " specified in rule");
            return Optional.empty();
        }
        else {
            AlertProcessorImpl processor = new AlertProcessorImpl(extractionDecoratorFactory, indexToRules.get(indexDocRef), indexStructure,
                    wordListProvider, maxBooleanClauseCount);
            return Optional.of(processor);
        }

    }

    private final DocRef findRulesFolder(){

        final Optional<ExplorerNode> folder = explorerNodeService.getRoot();

        if (folder.isEmpty()){
            throw new IllegalStateException("Unable to find root folder explorer node.");
        }

        ExplorerNode currentNode = folder.get();
        final String [] path = {"Rules","Active"};
        for (String name : path) {
            List <ExplorerNode> matchingChildren = explorerNodeService.getNodesByName(currentNode, name).stream().filter
                    (explorerNode -> ExplorerConstants.FOLDER.equals(explorerNode.getDocRef().getType())).collect(Collectors.toList());

            if (matchingChildren.size() == 0){
                LOGGER.error(()->"Unable to find folder called " + name + " when opening rules path ");
                return null;
            } else if (matchingChildren.size() > 1){
                final ExplorerNode node = currentNode;
                LOGGER.warn(()->"There are multiple folders called " + name + " under " + node.getName()  +
                        " when opening rules path " + path + " using first...");
            }
            currentNode = matchingChildren.get(0);
        }

        return currentNode.getDocRef();
    }

    private static Map<String, String> parseParms (String paramExpression){
        //todo implement this!
        if (paramExpression != null && paramExpression.trim().length() > 0){
            LOGGER.error("Error currently unable to use parameters in dashboards used as rules");
        }

        return new HashMap<>();
    }

    private void initialiseCache(){
        Map<DocRef, List<RuleConfig>> indexToRules = new HashMap<>();

        DocRef rulesFolder = findRulesFolder();
        if (rulesFolder == null)
            return;

        List<ExplorerNode> childNodes = explorerNodeService.getChildren(rulesFolder);
        for (ExplorerNode childNode : childNodes){
            if (DashboardDoc.DOCUMENT_TYPE.equals(childNode.getDocRef().getType())){
                DashboardDoc dashboard = dashboardStore.readDocument(childNode.getDocRef());

                Map<String, String> paramMap = parseParms (dashboard.getDashboardConfig().getParameters());

                final List<ComponentConfig> componentConfigs = dashboard.getDashboardConfig().getComponents();
                for (ComponentConfig componentConfig : componentConfigs){
                    if (componentConfig.getSettings() instanceof QueryComponentSettings){
                        QueryComponentSettings queryComponentSettings = (QueryComponentSettings) componentConfig.getSettings();
                        final String queryId = componentConfig.getId();
                        ExpressionOperator expression = queryComponentSettings.getExpression();
                        DocRef dataSource = queryComponentSettings.getDataSource();

                        Map<DocRef, List<TableSettings>> pipelineTableSettings = new HashMap<>();

                        //Find all the tables associated with this query
                        for (ComponentConfig associatedComponentConfig : componentConfigs){
                            if (associatedComponentConfig.getSettings() instanceof TableComponentSettings){
                                TableComponentSettings tableComponentSettings = (TableComponentSettings) associatedComponentConfig.getSettings();

                                TableSettings tableSetting = TableSettingsUtil.mapTableSettings(tableComponentSettings);
                                DocRef pipeline = tableSetting.getExtractionPipeline();
                                if (pipeline != null && tableSetting.getQueryId().equals(queryId)){
                                    if (!pipelineTableSettings.containsKey(pipeline)){
                                        pipelineTableSettings.put(pipeline, new ArrayList<>());
                                    }
                                    pipelineTableSettings.get(pipeline).add(tableSetting);
                                }
                            }
                        }

                        //Now split out by pipeline
                        for (DocRef pipeline : pipelineTableSettings.keySet()){
                            final RuleConfig rule = new RuleConfig(queryId, expression, pipeline,
                                    pipelineTableSettings.get(pipeline), paramMap);
                            if (!indexToRules.containsKey(dataSource))
                                indexToRules.put(dataSource, new ArrayList<>());
                            indexToRules.get(dataSource).add(rule);
                        }
                    }
                }
            }
        }

        this.indexToRules = indexToRules;
        //Create AlertProcessorImpls for each Datasource (index)
//        for (DocRef dataSourceRef : indexToRules.keySet()){
//            IndexStructure indexStructure = indexStructureCache.get(dataSourceRef);
//
//            if (indexStructure == null) {
//               LOGGER.warn ("Unable to locate index " + dataSourceRef + " specified in rule");
//            }
//            else {
//                AlertProcessorImpl processor = new AlertProcessorImpl(indexToRules.get(dataSourceRef), indexStructure,
//                        wordListProvider, maxBooleanClauseCount);
//                alertProcessorMap.put(dataSourceRef, processor);
//            }
//        }

    }
}
