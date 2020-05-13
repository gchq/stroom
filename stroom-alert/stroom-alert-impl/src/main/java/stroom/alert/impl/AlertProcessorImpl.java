package stroom.alert.impl;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
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
import stroom.index.impl.IndexStructureCache;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.impl.analyzer.AnalyzerFactory;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    private final IndexStructure indexStructure;
    private final String folderPath;

    private final Map<String, Analyzer> analyzerMap;

    public AlertProcessorImpl (final List<String> folderPath,
                               final IndexStructure indexStructure,
                               final ExplorerNodeService explorerNodeService,
                               final DashboardStore dashboardStore,
                               final WordListProvider wordListProvider,
                               final int maxBooleanClauseCount){
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
        this.rulesFolder = findRulesFolder(folderPath);
        this.folderPath = folderPath.stream().collect(Collectors.joining("/"));
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

        loadRules();
    }

    @Override
    public void createAlerts(final Document document) {
        MemoryIndex memoryIndex = new MemoryIndex();
        if (analyzerMap == null || analyzerMap.size() == 0){
            //Don't create alerts if index isn't configured
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

        checkRules (memoryIndex);
    }

    private void checkRules (MemoryIndex memoryIndex){
        IndexSearcher indexSearcher = memoryIndex.createSearcher();
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexStructure.getIndex().getFields());
        try {
            for (ExpressionOperator query : ruleQueries) {
                if (matchQuery(indexSearcher, indexFieldsMap, query)){
                    //This query matches - now apply filters
                    System.out.println ("Found a matching query rule");
                } else {
                    System.out.println ("This doesn't match");
                }
            }
        } catch (IOException ex){
            throw new RuntimeException("Unable to create alerts", ex);
        }
    }

    private boolean matchQuery (final IndexSearcher indexSearcher, final IndexFieldsMap indexFieldsMap,
                               final ExpressionOperator query) throws IOException {

        try {
            //todo work out how to use the timezone in the query
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider, indexFieldsMap, maxBooleanClauseCount, "UTC", System.currentTimeMillis());
            final SearchExpressionQueryBuilder.SearchExpressionQuery lucenetQuery = searchExpressionQueryBuilder
                    .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, query);

            TopDocs docs = indexSearcher.search(lucenetQuery.getQuery(), 100);

            if (docs.totalHits == 0) {
                return false; //Doc does not match
            } else if (docs.totalHits == 1) {
                return true; //Doc matches
            } else {
                LOGGER.error("Unexpected number of documents (" + docs.totalHits + " found by rule, should be 1 or 0 ");
            }
        }
        catch (SearchException se){
            LOGGER.warn("Unable to create alerts for rule " + query + " in folder " + rulesFolder);
        }
        return false;
    }

    private String getFolderPath (){
        return folderPath;
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
                        " when opening rules path " + getFolderPath() + " using first...");
            }
            currentNode = matchingChildren.get(0);
        }

        return currentNode.getDocRef();
    }

    private Collection<ExpressionOperator> ruleQueries = new ArrayList();

    private void loadRules(){
        if (rulesFolder == null) {
            return;
        }

        List<ExplorerNode> childNodes = explorerNodeService.getChildren(rulesFolder);
        for (ExplorerNode childNode : childNodes){
            if (DashboardDoc.DOCUMENT_TYPE.equals(childNode.getDocRef().getType())){
                DashboardDoc dashboard = dashboardStore.readDocument(childNode.getDocRef());

                final List<ComponentConfig> componentConfigs = dashboard.getDashboardConfig().getComponents();
                for (ComponentConfig componentConfig : componentConfigs){
                    if (componentConfig.getSettings() instanceof QueryComponentSettings){
                        QueryComponentSettings queryComponentSettings = (QueryComponentSettings) componentConfig.getSettings();
                        ruleQueries.add(queryComponentSettings.getExpression());
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
