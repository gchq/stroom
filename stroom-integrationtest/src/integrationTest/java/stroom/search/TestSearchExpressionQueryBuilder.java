package stroom.search;

import org.junit.Assert;
import org.junit.Test;
import stroom.dictionary.server.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.index.server.LuceneVersionUtil;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.search.server.SearchExpressionQueryBuilder;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.time.ZoneOffset;

public class TestSearchExpressionQueryBuilder extends AbstractCoreIntegrationTest {
    @Inject
    private DictionaryStore dictionaryStore;

    @Test
    public void testDictionaryAlphaNumeric() {
        test(IndexField.AnalyzerType.ALPHA_NUMERIC);
    }

    @Test
    public void testDictionaryKeyword() {
        test(IndexField.AnalyzerType.KEYWORD);
    }

    private void test(IndexField.AnalyzerType analyzerType) {
        final DocRef dictionaryRef = dictionaryStore.createDocument("test", null);
        DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionaryRef);
        dictionaryDoc.setData("1\n2\n3\n4");
        dictionaryDoc = dictionaryStore.writeDocument(dictionaryDoc);


        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();
        indexFieldsMap.put(IndexField.createField("test", analyzerType));

        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(dictionaryStore,
                indexFieldsMap,
                1024,
                ZoneOffset.UTC.getId(),
                System.currentTimeMillis());

        final ExpressionOperator expressionOperator = new ExpressionOperator
                .Builder()
                .addDocRefTerm(
                        "test",
                        ExpressionTerm.Condition.IN_DICTIONARY,
                        dictionaryRef)
                .build();
        final SearchExpressionQueryBuilder.SearchExpressionQuery query1 = searchExpressionQueryBuilder.buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, expressionOperator);
        System.out.println(query1.toString());

        dictionaryDoc.setData("1\r\n2\r\n3\r\n4\r\n");
        dictionaryDoc = dictionaryStore.writeDocument(dictionaryDoc);

        final SearchExpressionQueryBuilder.SearchExpressionQuery query2 = searchExpressionQueryBuilder.buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, expressionOperator);
        System.out.println(query2.toString());

        Assert.assertEquals(query1.getQuery(), query2.getQuery());
    }
}
