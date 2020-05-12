package stroom.alert.impl;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import stroom.alert.api.AlertProcessor;
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

import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;

import stroom.index.impl.IndexStructure;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.impl.analyzer.AnalyzerFactory;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AlertProcessorImpl implements AlertProcessor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AlertProcessorImpl.class);
    private final DocRef rulesFolder;
    private final ExplorerNodeService explorerNodeService;
    private final DashboardStore dashboardStore;
    private final WordListProvider wordListProvider;
    private final int maxBooleanClauseCount;

    private Map<String, Analyzer> analyzerMap = new ConcurrentHashMap<>();

    public AlertProcessorImpl (final List<String> folderPath,
                               final ExplorerNodeService explorerNodeService,
                               final DashboardStore dashboardStore,
                               final WordListProvider wordListProvider,
                               final int maxBooleanClauseCount){
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
        this.rulesFolder = findRulesFolder(folderPath);
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = maxBooleanClauseCount;
        System.out.println("Creating AlertProcessorImpl");
        loadRules();
    }

    @Override
    public void setFieldAnalyzers (final Map<String, Analyzer> analyzerMap){
        this.analyzerMap = analyzerMap;
    }

    @Override
    public void createAlerts(final Document document, final IndexDoc index) {
        MemoryIndex memoryIndex = new MemoryIndex();
        if (analyzerMap == null){
            throw new IllegalStateException("Analyzer Map Cannot be null");
        }
        System.out.println("Alerting " + document + " with rules from " + rulesFolder);
        for (IndexableField field : document){

            Analyzer fieldAnalyzer = analyzerMap.get(field.name());
            if (fieldAnalyzer != null){
                memoryIndex.addField(field.name(),fieldAnalyzer.tokenStream(field.name(), field.readerValue()));
            }
        }

        checkRules (index, memoryIndex);
    }

    private void checkRules (IndexDoc index, MemoryIndex memoryIndex){
        IndexSearcher indexSearcher = memoryIndex.createSearcher();
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getFields());

        //todo work out how to use the timezone in the query
        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                wordListProvider, indexFieldsMap, maxBooleanClauseCount, "UTC", System.currentTimeMillis());
        final SearchExpressionQueryBuilder.SearchExpressionQuery query = searchExpressionQueryBuilder
                .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, quickHack);

        try {
            //Todo remove hardcoded max results limit
            TopDocs docs = indexSearcher.search(query.getQuery(), 100);
            System.out.println ("Found " + docs.totalHits + " hits for rule");
        } catch (IOException ex){
            throw new RuntimeException("Unable to create alerts", ex);
        }

    }

    private final DocRef findRulesFolder(List<String> folderPath){

        ExplorerNode currentNode = explorerNodeService.getRoot().get();

        if (currentNode == null){
            throw new IllegalStateException("Unable to find root explorer node.");
        }

        for (String name : folderPath) {
            List <ExplorerNode> matchingChildren = explorerNodeService.getNodesByName(currentNode, name).stream().filter
                    (explorerNode -> ExplorerConstants.FOLDER.equals(explorerNode.getDocRef().getType())).collect(Collectors.toList());

            if (matchingChildren.size() == 0){
                LOGGER.error(()->"Unable to find folder called " + name + " when opening rules path " +
                        folderPath.stream().collect(Collectors.joining("/")));
                return null;
            } else if (matchingChildren.size() > 1){
                final ExplorerNode node = currentNode;
                LOGGER.warn(()->"There are multiple folders called " + name + " under " + node.getName()  +
                        " when opening rules path " + folderPath.stream().collect(Collectors.joining("/")) + " using first...");
            }
            currentNode = matchingChildren.get(0);
        }

        return currentNode.getDocRef();
    }

    //todo cache all the rules properly.
    private ExpressionOperator quickHack ;

    private void loadRules(){
        if (rulesFolder == null)
            return;
        List<ExplorerNode> childNodes = explorerNodeService.getChildren(rulesFolder);
        for (ExplorerNode childNode : childNodes){
            if (DashboardDoc.DOCUMENT_TYPE.equals(childNode.getDocRef().getType())){
                DashboardDoc dashboard = dashboardStore.readDocument(childNode.getDocRef());

                final List<ComponentConfig> componentConfigs = dashboard.getDashboardConfig().getComponents();
                for (ComponentConfig componentConfig : componentConfigs){
                    if (componentConfig.getSettings() instanceof QueryComponentSettings){
                        QueryComponentSettings queryComponentSettings = (QueryComponentSettings) componentConfig.getSettings();
                        quickHack = queryComponentSettings.getExpression();

                        System.out.println("Found query " + quickHack);
                    } else if (componentConfig.getSettings() instanceof TableComponentSettings){
                        TableComponentSettings tableComponentSettings = (TableComponentSettings) componentConfig.getSettings();

                        System.out.println ("Found table with " + tableComponentSettings.getFields().size()
                                +" fields for query " + tableComponentSettings.getQueryId());
                    }
                }
            }
        }
    }
}
