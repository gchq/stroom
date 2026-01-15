/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.lucene;

import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.common.v2.MockIndexFieldCache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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

    private void test(final AnalyzerType analyzerType) {
//        final DocRef dictionaryRef = dictionaryStore.createDocument("test");
//        DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionaryRef);
//        dictionaryDoc.setData("1\n2\n3\n4");
//        dictionaryDoc = dictionaryStore.writeDocument(dictionaryDoc);

        final DocRef dictionaryRef = new DocRef(DictionaryDoc.TYPE, "test", "test");

        final WordListProvider wordListProvider = new WordListProvider() {
            @Override
            public List<DocRef> findByName(final String name) {
                return List.of(dictionaryRef);
            }

            @Override
            public Optional<DocRef> findByUuid(final String uuid) {
                return Optional.empty();
            }

            @Override
            public String getCombinedData(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public String[] getWords(final DocRef dictionaryRef) {
                return "1\n2\n3\n4".split("\n");
            }

            @Override
            public WordList getCombinedWordList(final DocRef dictionaryRef,
                                                final DocRefDecorator docRefDecorator) {
                return WordList.builder(true)
                        .addWord("1", dictionaryRef)
                        .addWord("2", dictionaryRef)
                        .addWord("3", dictionaryRef)
                        .addWord("4", dictionaryRef)
                        .build();
            }
        };

        final MockIndexFieldCache indexFieldCache = new MockIndexFieldCache();
        indexFieldCache.put("test", LuceneIndexField.createField("test", analyzerType));

        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                new DocRef("test", "test"),
                indexFieldCache,
                wordListProvider,
                DateTimeSettings.builder().build().withoutReferenceTime(),
                null);

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
