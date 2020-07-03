package stroom.alert.impl;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import stroom.alert.api.AlertProcessor;
import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.impl.TableSettingsUtil;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexableFieldType;
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
import stroom.query.api.v2.Field;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledDepths;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;

import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.task.api.TaskContext;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Provider;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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

        loadRules(indexStructure.getIndex().getUuid());
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

        checkRules (createVals(document), memoryIndex);
    }

    private Val[] createVals (final Document document){

        //todo allow a different search extraction pipeline to be used.
        //Currently the indexing pipeline must contain a superset of the values as the extraction pipeline
        //This is possible when the same translation is used.

        Val[] result = new Val[document.getFields().size()];
        int fieldIndex = 0;
        //See SearchResultOutputFilter for creation of Values
        for (IndexableField field : document.getFields()){
            if (field.numericValue() != null){
                result[fieldIndex] = ValLong.create(field.numericValue().longValue());
            } else {
                result[fieldIndex] = ValString.create(field.stringValue());
            }
            fieldIndex++;
        }
        return result;
    }

    private void checkRules (final Val[] inputVals, final MemoryIndex memoryIndex){
        IndexSearcher indexSearcher = memoryIndex.createSearcher();
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexStructure.getIndex().getFields());
        try {
            for (RuleConfig rule : rules) {
                if (matchQuery(indexSearcher, indexFieldsMap, rule.getExpression())){
                    //This query matches - now apply filters

                    //First get the original event XML

   //                 System.out.println ("Found a matching query rule");


                    //Now apply filters and formatting
                    final String[] filteredVals = mapHit(rule, inputVals);


                    //A match
                    if (filteredVals != null){
                        System.out.println ("Got a filtered match ");
                        for (String val : filteredVals){
                            if (val != null){
                                System.out.println("***" + val);
                            }
                        }
                    }

                } else {
     //               System.out.println ("This doesn't match");
                }
            }
        } catch (IOException ex){
            throw new RuntimeException("Unable to create alerts", ex);
        }
    }

    private boolean matchQuery (final IndexSearcher indexSearcher, final IndexFieldsMap indexFieldsMap,
                               final ExpressionOperator query) throws IOException {

        try {

            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider, indexFieldsMap, maxBooleanClauseCount, DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH, System.currentTimeMillis());
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
            LOGGER.warn("Unable to create alerts for rule " + query + " in folder " + getFolderPath() + " due to " + se.getMessage());
        }
        return false;
    }

    private String getFolderPath (){
        return folderPath;
    }

    //todo  work out how to use the timezone in the query
    private final String DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH = "UTC";

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

    //Todo fix rules and tables to allow multiple tables per query and notice missing rules, etc.
    List <RuleConfig> rules = new ArrayList<>();

    private void loadRules(final String indexUuid){
        if (rulesFolder == null) {
            return;
        }

        List<ExplorerNode> childNodes = explorerNodeService.getChildren(rulesFolder);
        for (ExplorerNode childNode : childNodes){
            if (DashboardDoc.DOCUMENT_TYPE.equals(childNode.getDocRef().getType())){
                DashboardDoc dashboard = dashboardStore.readDocument(childNode.getDocRef());

                //todo support multiple queries by holding a map between queryid and expresionoperator/datasource
                //but how to find the queryid?
                int numberOfQueries = 0;
                ExpressionOperator expression = null;
                DocRef dataSource = null;
                List<TableSettings> tableSettingsList = new ArrayList<>();
                Map<String, String> paramMap = parseParms (dashboard.getDashboardConfig().getParameters());

                final List<ComponentConfig> componentConfigs = dashboard.getDashboardConfig().getComponents();
                for (ComponentConfig componentConfig : componentConfigs){
                    if (componentConfig.getSettings() instanceof QueryComponentSettings){
                        QueryComponentSettings queryComponentSettings = (QueryComponentSettings) componentConfig.getSettings();

                        if (expression == null){
                            //First query in the dashboard
                            expression = queryComponentSettings.getExpression();
                            dataSource = queryComponentSettings.getDataSource();
                        } else {
                            //This dashboard contains more than one query (currently not allowed)
                            LOGGER.error("Multiple queries found in rules dashboard - this is not currently supported");
                        }

                        numberOfQueries++;
                    } else if (componentConfig.getSettings() instanceof TableComponentSettings){
                        TableComponentSettings tableComponentSettings = (TableComponentSettings) componentConfig.getSettings();
                        TableSettings tableSetting = TableSettingsUtil.mapTableSettings(tableComponentSettings);
                        tableSettingsList.add(tableSetting);
                    }
                }

                //Create rules where valid
                if (numberOfQueries == 1 && indexUuid.equals(dataSource.getUuid())){
                    for (TableSettings tableSettings : tableSettingsList){
                        rules.add(new RuleConfig(dataSource, expression, tableSettings, paramMap));
                    }
                }

            }
        }
    }

    private static Map<String, String> parseParms (String paramExpression){
        if (paramExpression != null && paramExpression.trim().length() > 0){
            LOGGER.error("Error currently unable to use parameters in dashboards used as rules");
        }

        return new HashMap<>();
    }


    private String[] mapHit (final RuleConfig ruleConfig, final Val[] inputVals) {
        final List<Field> fields = ruleConfig.getTableSettings().getFields();

        FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
        final FieldFormatter fieldFormatter = new FieldFormatter(new FormatterFactory(DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH));
        //See CoprocessorsFactory for creation of field Index Map
        final CompiledFields compiledFields = new CompiledFields(fields, fieldIndexMap, ruleConfig.getParamMap());
        final Generator[] generators = new Generator[ruleConfig.getTableSettings().getFields().size()];

        final String[] output = new String [ruleConfig.getTableSettings().getFields().size()];

        for (final CompiledField compiledField : compiledFields) {
            final Expression expression = compiledField.getExpression();

            if (expression != null) {
                if (expression.hasAggregate()) {
                    LOGGER.error("Aggregate functions not supported for dashboards in rules");
                } else {
                    final Generator generator = expression.createGenerator();
                    generator.set(inputVals);

                    final int index = fieldIndexMap.get(compiledField.getField().getId());

                    if (index >= 0){
                        generators[index] = generator;

                        if (compiledField.getCompiledFilter() != null) {
                            // If we are filtering then we need to evaluate this field
                            // now so that we can filter the resultant value.
                            Val value = generator.eval();
                            output[index] = fieldFormatter.format(compiledField.getField(), value); //From TableResultCreator
                            if (compiledField.getCompiledFilter() != null && value != null && !compiledField.getCompiledFilter().match(value.toString())) {
                                // We want to exclude this item.
                                return null;
                            }
                        }
                    }
                }
            }

        }

        return output;

    }

    private static class RuleConfig {
        private final DocRef dataSource;
        private final ExpressionOperator expression;
        private final TableSettings tableSettings;
        private final Map<String, String> params;

        RuleConfig(final DocRef dataSource,
                   final ExpressionOperator expression,
                   final TableSettings tableSettings,
                   final Map<String, String> params){
            this.dataSource = dataSource;
            this.expression = expression;
            this.tableSettings = tableSettings;
            this.params = params;
        }

        public ExpressionOperator getExpression() {
            return expression;
        }

        public TableSettings getTableSettings() {
            return tableSettings;
        }

        public Map<String, String> getParamMap() {
            return params;
        }

        public DocRef getDataSource(){
            return dataSource;
        }
    }
}
