/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic;

import stroom.ai.impl.tools.MisleadingSearches;
import stroom.ai.impl.tools.MisleadingSearches.NullSearch;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.ElasticEndToEndTestHelper.SearchResult;
import stroom.search.elastic.search.ElasticSearchProvider;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// What Stroom AI searches IS_NOT_NULL as on Elasticsearch (`MisleadingSearches.elasticsearchNullAs`), against a
/// real cluster: Stroom searches EQUALS '*' as `exists`, which a document holding an empty string meets, so on a
/// string field IS_NOT_NULL is MATCHES_REGEX '.+', which it doesn't. Seen in testing: told EQUALS '*' was any
/// value, the agent saw it would pass on empty strings, and dropped checks of fields that were populated.
///
/// Document 1 has a value in each field, document 2 an empty string in each string field (and no number), and
/// document 3 none.
class TestElasticEmptyValues_EndToEnd extends AbstractCoreIntegrationTest {

    private static final String FEED = "ELASTIC_EMPTY_VALUES";
    private static final String ID = "Id";
    private static final String INDEX_BODY = """
            {
              "settings": {"number_of_shards": 1, "number_of_replicas": 0},
              "mappings": {
                "dynamic": "strict",
                "properties": {
                  "StreamId": {"type": "long"},
                  "EventId": {"type": "long"},
                  "Id": {"type": "keyword"},
                  "Date": {"type": "date"},
                  "Keyword": {"type": "keyword"},
                  "Text": {"type": "text"},
                  "Long": {"type": "long"}
                }
              }
            }""";
    private static final String DOCUMENTS = """
            [
              {"_id": "1", "StreamId": 0, "EventId": 1, "Id": "1", "Date": "2026-01-01T00:00:00.000Z",
               "Keyword": "apple", "Text": "The red apple", "Long": 5},
              {"_id": "2", "StreamId": 0, "EventId": 2, "Id": "2", "Date": "2026-01-02T00:00:00.000Z",
               "Keyword": "", "Text": ""},
              {"_id": "3", "StreamId": 0, "EventId": 3, "Id": "3", "Date": "2026-01-03T00:00:00.000Z"}
            ]""";

    @Inject
    private ElasticEndToEndTestHelper helper;
    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private ElasticSearchProvider elasticSearchProvider;

    private ElasticTestNode node;
    private String indexName;

    @AfterEach
    void tearDown() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void isNotNullFindsOnlyANonEmptyValueWhereEqualsStarFindsAnEmptyStringToo() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("empty-values");
        node.createIndex(indexName, INDEX_BODY);
        // Stroom only returns the hits of streams that exist.
        final long streamId = helper.addEventsStream(FEED, ElasticEndToEndTestHelper.CONDITIONS_EVENTS);
        node.bulkIndex(indexName, JsonUtil.getMapper().readTree(DOCUMENTS
                .replace("\"StreamId\": 0,", "\"StreamId\": " + streamId + ",")));
        final DocRef indexRef = helper.createIndexDoc(helper.createCluster(node), indexName, "Date", 100);
        final Map<String, QueryField> fields = elasticSearchProvider
                .getDataSourceFields(elasticIndexStore.readDocument(indexRef))
                .stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));

        // Stroom's exists: the empty strings are found too.
        assertThat(ids(indexRef, "Keyword", Condition.EQUALS, "*")).containsExactlyInAnyOrder("1", "2");
        assertThat(ids(indexRef, "Text", Condition.EQUALS, "*")).containsExactlyInAnyOrder("1", "2");
        // IS_NOT_NULL as searched, by the field's type as Stroom lists it.
        for (final String field : List.of("Keyword", "Text", "Long")) {
            final NullSearch searched = MisleadingSearches.elasticsearchNullAs(Condition.IS_NOT_NULL,
                    fields.get(field).getFldType().name()).orElseThrow();
            assertThat(ids(indexRef, field, searched.condition(), searched.value()))
                    .as("%s IS_NOT_NULL, searched as %s '%s'", field, searched.condition(), searched.value())
                    .containsExactly("1");
        }
        // IS_NULL: no value, an empty string counting as one.
        final NullSearch none = MisleadingSearches.elasticsearchNullAs(Condition.IS_NULL,
                fields.get("Keyword").getFldType().name()).orElseThrow();
        assertThat(ids(indexRef, "Keyword", none.condition(), none.value())).containsExactly("3");
    }

    private List<String> ids(final DocRef indexRef,
                             final String field,
                             final Condition condition,
                             final String value) {
        final SearchResult result = helper.search(indexRef, ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder().field(field).condition(condition).value(value).build())
                .build(), List.of(ID));
        assertThat(result.errors()).as("Errors").isEmpty();
        return result.column(0);
    }
}
