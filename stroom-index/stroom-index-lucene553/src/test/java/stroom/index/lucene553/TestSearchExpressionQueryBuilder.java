package stroom.index.lucene553;

import stroom.datasource.api.v2.AnalyzerType;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.common.v2.MockIndexFieldCache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSearchExpressionQueryBuilder {

    @Test
    public void testDictionaryAlphaNumeric() {
        test(AnalyzerType.ALPHA_NUMERIC);
    }

    @Test
    public void testDictionaryKeyword() {
        test(AnalyzerType.KEYWORD);
    }

    private void test(AnalyzerType analyzerType) {
//        final DocRef dictionaryRef = dictionaryStore.createDocument("test");
//        DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionaryRef);
//        dictionaryDoc.setData("1\n2\n3\n4");
//        dictionaryDoc = dictionaryStore.writeDocument(dictionaryDoc);

        final DocRef dictionaryRef = new DocRef(DictionaryDoc.DOCUMENT_TYPE, "test", "test");

        final WordListProvider wordListProvider = new WordListProvider() {
            @Override
            public String getCombinedData(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public String[] getWords(final DocRef dictionaryRef) {
                return "1\n2\n3\n4".split("\n");
            }

            @Override
            public Set<DocRef> listDocuments() {
                return Set.of(dictionaryRef);
            }

            @Override
            public List<DocRef> findByNames(final List<String> names,
                                            final boolean allowWildCards,
                                            final boolean isCaseSensitive) {
                return List.of(dictionaryRef);
            }
        };

        final MockIndexFieldCache indexFieldCache = new MockIndexFieldCache();
        indexFieldCache.put("test", LuceneIndexField.createField("test", analyzerType));
        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                new DocRef("test", "test"),
                indexFieldCache,
                wordListProvider,
                1024,
                DateTimeSettings.builder().build());

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addDocRefTerm(
                        "test",
                        ExpressionTerm.Condition.IN_DICTIONARY,
                        dictionaryRef)
                .build();
        final SearchExpressionQueryBuilder.SearchExpressionQuery query1 = searchExpressionQueryBuilder.buildQuery(
                expressionOperator);
        System.out.println(query1.toString());

//        dictionaryDoc.setData("1\r\n2\r\n3\r\n4\r\n");
//        dictionaryDoc = dictionaryStore.writeDocument(dictionaryDoc);

        final SearchExpressionQueryBuilder.SearchExpressionQuery query2 = searchExpressionQueryBuilder.buildQuery(
                expressionOperator);
        System.out.println(query2.toString());

        assertThat(query2.getQuery()).isEqualTo(query1.getQuery());
    }
}
