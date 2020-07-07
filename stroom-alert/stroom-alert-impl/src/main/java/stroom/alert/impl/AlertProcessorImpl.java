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
import stroom.dashboard.impl.TableSettingsUtil;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;

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
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;

import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AlertProcessorImpl implements AlertProcessor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AlertProcessorImpl.class);


    private final WordListProvider wordListProvider;
    private final int maxBooleanClauseCount;
    private final IndexStructure indexStructure;
    //todo  work out how to use the timezone in the query
    private final String DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH = "UTC";

    private final List <RuleConfig> rules;

    private final Map<String, Analyzer> analyzerMap;

    public AlertProcessorImpl (final List<RuleConfig> rules,
                               final IndexStructure indexStructure,
                               final WordListProvider wordListProvider,
                               final int maxBooleanClauseCount){
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

        checkRules (document, memoryIndex);
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

    private void checkRules (final Document document, final MemoryIndex memoryIndex){
        IndexSearcher indexSearcher = memoryIndex.createSearcher();
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexStructure.getIndex().getFields());
        try {
            for (RuleConfig rule : rules) {
                if (matchQuery(indexSearcher, indexFieldsMap, rule.getExpression())){
                    //This query matches - now apply filters

                    //First get the original event XML

   //                 System.out.println ("Found a matching query rule");


                    //Now apply filters and formatting
                    final String[] filteredVals = mapHit(rule, document);


                    //A match
                    if (filteredVals != null){
                        System.out.println ("Got a filtered match ");
                        for (String val : filteredVals){
                            if (val != null){
                                System.out.println("***" + val);
                            } else {
                                System.out.println("*NULL*");
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
            LOGGER.warn("Unable to create alerts for rule " + query + " due to " + se.getMessage());
        }
        return false;
    }




    private String[] mapHit (final RuleConfig ruleConfig, final Document doc) {
        final List<Field> fields = ruleConfig.getTableSettings().getFields();

//        FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
        FieldIndexMap fieldIndexMap = FieldIndexMap.forFields
                (doc.getFields().stream().map(f -> f.name()).
                        collect(Collectors.toList()).toArray(new String[doc.getFields().size()]));
        final FieldFormatter fieldFormatter = new FieldFormatter(new FormatterFactory(DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH));
        //See CoprocessorsFactory for creation of field Index Map
        final CompiledFields compiledFields = new CompiledFields(fields, fieldIndexMap, ruleConfig.getParamMap());
//        final Generator[] generators = new Generator[ruleConfig.getTableSettings().getFields().size()];

        final String[] output = new String [ruleConfig.getTableSettings().getFields().size()];
        int index = 0;
        final Val[] inputVals = createVals (doc);
        for (final CompiledField compiledField : compiledFields) {
            final Expression expression = compiledField.getExpression();

            if (expression != null) {
                if (expression.hasAggregate()) {
                    LOGGER.error("Aggregate functions not supported for dashboards in rules");
                } else {
                    final Generator generator = expression.createGenerator();

                    generator.set(inputVals);
                    Val value = generator.eval();
                    output[index] = fieldFormatter.format(compiledField.getField(), value); //From TableResultCreator

                    if (compiledField.getCompiledFilter() != null) {
                        // If we are filtering then we need to evaluate this field
                        // now so that we can filter the resultant value.

                        if (compiledField.getCompiledFilter() != null && value != null && !compiledField.getCompiledFilter().match(value.toString())) {
                            // We want to exclude this item.
                            return null;
                        }
                    }
                }
            }

            index++;
        }

        return output;
    }

    private Val[] findVals(CompiledFields compiledFields, Document doc){
        Val[] output = new Val[compiledFields.size()];
        int index = 0;
        for (CompiledField field : compiledFields){
            IndexableField[] matchingFields = doc.getFields(field.getField().getName());
            if (matchingFields == null || matchingFields.length == 0)
            {
                LOGGER.warn("Unable to find field " + field.getField().getName());
            } else if (matchingFields.length > 1) {
                LOGGER.warn("Multiple field (" + matchingFields.length +") found for field " + field.getField().getName());
            }
            else {
                 if (matchingFields[0].numericValue() != null){
                     output[index] = ValLong.create(matchingFields[0].numericValue().longValue());
                 } else {
                    output[index] = ValString.create(matchingFields[0].stringValue());
                 }
            }

            index++;
        }

        return output;
    }

}
