package stroom.search;

import org.junit.jupiter.api.Test;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.impl.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.shared.AnalyzerType;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSearchExpressionQueryBuilder extends AbstractCoreIntegrationTest {
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private WordListProvider wordListProvider;

    @Test
    public void testDictionaryAlphaNumeric() {
        test(AnalyzerType.ALPHA_NUMERIC);
    }

    @Test
    public void testDictionaryKeyword() {
        test(AnalyzerType.KEYWORD);
    }

    private void test(AnalyzerType analyzerType) {
        final DocRef dictionaryRef = dictionaryStore.createDocument("test");
        DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionaryRef);
        dictionaryDoc.setData("1\n2\n3\n4");
        dictionaryDoc = dictionaryStore.writeDocument(dictionaryDoc);


        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();
        indexFieldsMap.put(IndexField.createField("test", analyzerType));

        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(wordListProvider,
                indexFieldsMap,
                1024,
                ZoneOffset.UTC.getId(),
                System.currentTimeMillis());

        final ExpressionOperator expressionOperator = new ExpressionOperator
                .Builder()
                .addTerm(
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

        assertThat(query2.getQuery()).isEqualTo(query1.getQuery());
    }
}
