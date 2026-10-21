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

package stroom.search.elastic.search;

import stroom.docref.DocRef;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.security.api.SecurityContext;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.FieldMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestElasticSearchProvider {

    private static final String INDEX_NAME = "test-index";
    private static final DocRef CLUSTER_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-uuid", "Test Cluster");
    private static final DocRef INDEX_REF = new DocRef(ElasticIndexDoc.TYPE, "index-uuid", "Test Index");

    @Mock
    private ElasticClientCache elasticClientCache;
    @Mock
    private ElasticClusterStore elasticClusterStore;
    @Mock
    private ElasticIndexStore elasticIndexStore;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ElasticsearchClient elasticClient;
    @Mock
    private ElasticsearchIndicesClient elasticIndicesClient;

    private ElasticSearchProvider elasticSearchProvider;

    @BeforeEach
    void setUp() {
        elasticSearchProvider = new ElasticSearchProvider(
                null,
                null,
                null,
                null,
                null,
                null,
                elasticClientCache,
                elasticClusterStore,
                elasticIndexStore,
                securityContext,
                null,
                null,
                null);
    }

    @Test
    void testRegisterField_topLevelMultiField() {
        final Property name = Property.of(p -> p.text(t -> t
                .fields("keyword", Property.of(k -> k.keyword(kw -> kw)))));

        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("Name", name, out);

        assertThat(out).containsOnlyKeys("Name", "Name.keyword");
        assertThat(getKind(out, "Name.keyword")).isEqualTo(Kind.Keyword);
    }

    @Test
    void testRegisterField_multiFieldWithinObject() {
        final Property user = Property.of(p -> p.object(o -> o
                .properties("Id", Property.of(id -> id.text(t -> t
                        .fields("keyword", Property.of(k -> k.keyword(kw -> kw))))))));

        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("User", user, out);

        assertThat(out).containsOnlyKeys("User", "User.Id", "User.Id.keyword");
        assertThat(getKind(out, "User.Id")).isEqualTo(Kind.Text);
        assertThat(getKind(out, "User.Id.keyword")).isEqualTo(Kind.Keyword);
    }

    @Test
    void testRegisterField_multiFieldWithinNested() {
        final Property user = Property.of(p -> p.nested(n -> n
                .properties("Name", Property.of(name -> name.text(t -> t
                        .fields("raw", Property.of(k -> k.keyword(kw -> kw))))))));

        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("User", user, out);

        assertThat(out).containsOnlyKeys("User", "User.Name", "User.Name.raw");
        assertThat(getKind(out, "User")).isEqualTo(Kind.Nested);
        assertThat(getKind(out, "User.Name.raw")).isEqualTo(Kind.Keyword);
    }

    @Test
    void testRegisterField_noMultiFields() {
        final Property count = Property.of(p -> p.long_(l -> l));

        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("Count", count, out);

        assertThat(out).containsOnlyKeys("Count");
        assertThat(getKind(out, "Count")).isEqualTo(Kind.Long);
    }

    @Test
    void testRegisterField_existingFieldNotReplaced() {
        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("Name", Property.of(p -> p.keyword(kw -> kw)), out);
        ElasticSearchProvider.registerField("Name", Property.of(p -> p.text(t -> t
                .fields("keyword", Property.of(k -> k.keyword(kw -> kw))))), out);

        // First definition wins, but multi-fields from a later definition are still added
        assertThat(getKind(out, "Name")).isEqualTo(Kind.Keyword);
        assertThat(out).containsOnlyKeys("Name", "Name.keyword");
    }

    @Test
    void testRegisterField_nestedWithinNested() {
        final Property user = Property.of(p -> p.nested(n -> n
                .properties("Address", Property.of(a -> a.nested(an -> an
                        .properties("City", Property.of(c -> c.keyword(kw -> kw))))))));

        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("User", user, out);

        assertThat(out).containsOnlyKeys("User", "User.Address", "User.Address.City");
        assertThat(getKind(out, "User.Address")).isEqualTo(Kind.Nested);
        assertThat(out.get("User.Address.City").fullName()).isEqualTo("User.Address.City");
    }

    @Test
    void testGetDataSourceFields_aliasResolvesToTargetType() throws IOException {
        givenMapping("""
                {
                  "EventTime": { "type": "date" },
                  "Timestamp": { "type": "alias", "path": "EventTime" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::queryable)
                .containsExactly(
                        tuple("EventTime", FieldType.DATE, true),
                        tuple("Timestamp", FieldType.DATE, true));
    }

    @Test
    void testGetFieldsMap_aliasResolvesToTargetType() throws IOException {
        givenMapping("""
                {
                  "EventTime": { "type": "date" },
                  "Timestamp": { "type": "alias", "path": "EventTime" }
                }""");

        final ElasticIndexField alias = elasticSearchProvider.getFieldsMap(createIndex()).get("Timestamp");

        assertThat(alias).isNotNull();
        assertThat(alias.getFldType()).isEqualTo(FieldType.DATE);
        assertThat(alias.getNativeType()).isEqualTo("date");
        assertThat(alias.isIndexed()).isTrue();
    }

    @Test
    void testGetDataSourceFields_aliasToMissingTargetDropped() throws IOException {
        givenMapping("""
                {
                  "Broken": { "type": "alias", "path": "DoesNotExist" },
                  "Name": { "type": "keyword" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName)
                .containsExactly("Name");
    }

    @Test
    void testGetFieldsMap_aliasToMissingTargetDropped() throws IOException {
        givenMapping("""
                {
                  "Broken": { "type": "alias", "path": "DoesNotExist" },
                  "Name": { "type": "keyword" }
                }""");

        assertThat(elasticSearchProvider.getFieldsMap(createIndex()))
                .containsOnlyKeys("Name");
    }

    @Test
    void testGetDataSourceFields_aliasToNonIndexedTargetNotQueryable() throws IOException {
        givenMapping("""
                {
                  "Payload": { "type": "keyword", "index": false },
                  "PayloadAlias": { "type": "alias", "path": "Payload" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::queryable)
                .containsExactly(
                        tuple("Payload", false),
                        tuple("PayloadAlias", false));
        assertThat(elasticSearchProvider.getFieldsMap(createIndex()).get("PayloadAlias").isIndexed())
                .isFalse();
    }

    @Test
    void testGetFieldsMap_aliasToNonIndexedTargetNotIndexed() throws IOException {
        givenMapping("""
                {
                  "Payload": { "type": "keyword", "index": false },
                  "PayloadAlias": { "type": "alias", "path": "Payload" }
                }""");

        final ElasticIndexField alias = elasticSearchProvider.getFieldsMap(createIndex()).get("PayloadAlias");

        assertThat(alias).isNotNull();
        assertThat(alias.getFldType()).isEqualTo(FieldType.KEYWORD);
        assertThat(alias.getNativeType()).isEqualTo("keyword");
        assertThat(alias.isIndexed()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "text", "boolean", "date", "long", "integer", "double", "scaled_float", "ip"})
    void testGetDataSourceFields_indexFalseNotQueryable(final String nativeType) throws IOException {
        givenMapping("""
                {
                  "Indexed": { "type": "%s" },
                  "IndexedExplicitly": { "type": "%s", "index": true },
                  "NotIndexed": { "type": "%s", "index": false }
                }""".formatted(nativeType, nativeType, nativeType));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::queryable)
                .containsExactly(
                        tuple("Indexed", true),
                        tuple("IndexedExplicitly", true),
                        tuple("NotIndexed", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "text", "boolean", "date", "long", "integer", "double", "scaled_float", "ip"})
    void testGetFieldsMap_indexFalseNotIndexed(final String nativeType) throws IOException {
        givenMapping("""
                {
                  "Indexed": { "type": "%s" },
                  "NotIndexed": { "type": "%s", "index": false }
                }""".formatted(nativeType, nativeType));

        final Map<String, ElasticIndexField> fieldsMap = elasticSearchProvider.getFieldsMap(createIndex());

        assertThat(fieldsMap.get("Indexed").isIndexed()).isTrue();
        assertThat(fieldsMap.get("NotIndexed").isIndexed()).isFalse();
        assertThat(fieldsMap.get("NotIndexed").getNativeType()).isEqualTo(nativeType);
    }

    @Test
    void testGetFieldsMap_otherTypesAssumedIndexed() throws IOException {
        // The `index` parameter is only inspected for common data types; all others are assumed indexed
        givenMapping("""
                {
                  "Constant": { "type": "constant_keyword", "value": "foo" },
                  "Pattern": { "type": "wildcard" },
                  "Vector": { "type": "dense_vector", "dims": 3, "index": false },
                  "Version": { "type": "version" }
                }""");

        assertThat(elasticSearchProvider.getFieldsMap(createIndex()).values())
                .extracting(ElasticIndexField::getFldName, ElasticIndexField::getFldType,
                        ElasticIndexField::isIndexed)
                .containsExactlyInAnyOrder(
                        tuple("Constant", FieldType.KEYWORD, true),
                        tuple("Pattern", FieldType.KEYWORD, true),
                        tuple("Vector", FieldType.DENSE_VECTOR, true),
                        tuple("Version", FieldType.INTEGER, true));
    }

    @Test
    void testGetDataSourceFields_dateNanosIndexFalseNotQueryable() throws IOException {
        givenMapping("""
                {
                  "Time": { "type": "date_nanos", "index": false }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::queryable)
                .containsExactly(tuple("Time", FieldType.DATE, false));
    }

    @Test
    void testGetFieldsMap_dateNanosIndexFalseNotIndexed() throws IOException {
        givenMapping("""
                {
                  "Indexed": { "type": "date_nanos" },
                  "NotIndexed": { "type": "date_nanos", "index": false }
                }""");

        final Map<String, ElasticIndexField> fieldsMap = elasticSearchProvider.getFieldsMap(createIndex());

        assertThat(fieldsMap.get("Indexed").isIndexed()).isTrue();
        assertThat(fieldsMap.get("NotIndexed").isIndexed()).isFalse();
    }

    @Test
    void testGetDataSourceFields_unsupportedTypesDropped() throws IOException {
        givenMapping("""
                {
                  "Blob": { "type": "binary" },
                  "Labels": { "type": "flattened" },
                  "Location": { "type": "geo_point" },
                  "Name": { "type": "keyword" },
                  "Range": { "type": "integer_range" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName)
                .containsExactly("Name");
        assertThat(elasticSearchProvider.getFieldsMap(createIndex()))
                .containsOnlyKeys("Name");
    }

    @Test
    void testGetDataSourceFields_objectDroppedNestedKeptNotQueryable() throws IOException {
        givenMapping("""
                {
                  "Host": {
                    "properties": {
                      "Name": { "type": "keyword" }
                    }
                  },
                  "User": {
                    "type": "nested",
                    "properties": {
                      "Id": { "type": "keyword" },
                      "Address": {
                        "type": "nested",
                        "properties": {
                          "City": { "type": "keyword" }
                        }
                      }
                    }
                  }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::queryable)
                .containsExactly(
                        tuple("Host.Name", FieldType.KEYWORD, true),
                        tuple("User", FieldType.NESTED, false),
                        tuple("User.Address", FieldType.NESTED, false),
                        tuple("User.Address.City", FieldType.KEYWORD, true),
                        tuple("User.Id", FieldType.KEYWORD, true));
    }

    @Test
    void testGetFieldsMap_objectDroppedNestedKept() throws IOException {
        givenMapping("""
                {
                  "Host": {
                    "type": "object",
                    "properties": {
                      "Name": { "type": "keyword" }
                    }
                  },
                  "User": {
                    "type": "nested",
                    "properties": {
                      "Id": { "type": "keyword" }
                    }
                  }
                }""");

        assertThat(elasticSearchProvider.getFieldsMap(createIndex()).values())
                .extracting(ElasticIndexField::getFldName, ElasticIndexField::getFldType,
                        ElasticIndexField::getNativeType)
                .containsExactlyInAnyOrder(
                        tuple("Host.Name", FieldType.KEYWORD, "keyword"),
                        tuple("User", FieldType.NESTED, "nested"),
                        tuple("User.Id", FieldType.KEYWORD, "keyword"));
    }

    @Test
    void testGetDataSourceFields_multiFields() throws IOException {
        givenMapping("""
                {
                  "Message": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 },
                      "length": { "type": "token_count", "analyzer": "standard" }
                    }
                  },
                  "Status": {
                    "type": "keyword",
                    "fields": {
                      "text": { "type": "text" }
                    }
                  }
                }""");

        // token_count is unsupported, so is dropped
        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::queryable)
                .containsExactly(
                        tuple("Message", FieldType.TEXT, true),
                        tuple("Message.keyword", FieldType.KEYWORD, true),
                        tuple("Status", FieldType.KEYWORD, true),
                        tuple("Status.text", FieldType.TEXT, true));
    }

    @Test
    void testGetDataSourceFields_mergedAcrossIndicesFirstDefinitionWins() throws IOException {
        givenMappingResponse("""
                {
                  "index-a": {
                    "mappings": {
                      "properties": {
                        "OnlyInA": { "type": "long" },
                        "Shared": { "type": "keyword" }
                      }
                    }
                  },
                  "index-b": {
                    "mappings": {
                      "properties": {
                        "OnlyInB": { "type": "ip" },
                        "Shared": { "type": "text" }
                      }
                    }
                  }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType)
                .containsExactly(
                        tuple("OnlyInA", FieldType.LONG),
                        tuple("OnlyInB", FieldType.IPV4_ADDRESS),
                        tuple("Shared", FieldType.KEYWORD));
    }

    @Test
    void testGetDataSourceFields_indexWithoutMappings() throws IOException {
        givenMappingResponse("""
                {
                  "index-a": { "mappings": {} },
                  "index-b": {
                    "mappings": {
                      "properties": {
                        "Name": { "type": "keyword" }
                      }
                    }
                  }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName)
                .containsExactly("Name");
    }

    @Test
    void testGetDataSourceFields_caseInsensitiveOrder() throws IOException {
        givenMapping("""
                {
                  "zulu": { "type": "keyword" },
                  "Bravo": { "type": "keyword" },
                  "alpha": { "type": "keyword" },
                  "Charlie": { "type": "keyword" },
                  "@timestamp": { "type": "date" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName)
                .containsExactly("@timestamp", "alpha", "Bravo", "Charlie", "zulu");
    }

    @Test
    void testGetDataSourceFields_unknownTypeIgnored() throws IOException {
        // A type the client does not know about is deserialised as a custom property, which is not supported
        givenMapping("""
                {
                  "Future": { "type": "some_future_type" },
                  "Name": { "type": "keyword" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName)
                .containsExactly("Name");
        assertThat(elasticSearchProvider.getFieldsMap(createIndex()))
                .containsOnlyKeys("Name");
    }

    @Test
    void testGetDataSourceFields_noClusterRef() {
        final ElasticIndexDoc index = ElasticIndexDoc.builder()
                .uuid(INDEX_REF.getUuid())
                .indexName(INDEX_NAME)
                .build();

        assertThat(elasticSearchProvider.getDataSourceFields(index)).isEmpty();
        assertThat(elasticSearchProvider.getFieldsMap(index)).isEmpty();
        verifyNoInteractions(elasticClusterStore, elasticClientCache);
    }

    @Test
    void testGetDataSourceFields_clientFailure() throws IOException {
        givenClient();
        when(elasticIndicesClient.getMapping(any(GetMappingRequest.class)))
                .thenThrow(new IOException("Connection refused"));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
    }

    @Test
    void testGetDataSourceFields_clientCacheFailure() {
        final ElasticClusterDoc cluster = createCluster();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(cluster);
        when(elasticClientCache.contextResult(eq(cluster.getConnection()), any()))
                .thenThrow(new IllegalStateException("Unable to resolve credentials"));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
        assertThat(elasticSearchProvider.getFieldsMap(createIndex())).isEmpty();
    }

    @Test
    void testGetDataSourceFields_missingCluster() {
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(null);

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
    }

    @Test
    void testGetFields() throws IOException {
        givenMapping("""
                {
                  "Count": { "type": "integer" },
                  "Name": { "type": "keyword" }
                }""");

        assertThat(elasticSearchProvider.getFields(createIndex()))
                .extracting(ElasticIndexField::getFldName, ElasticIndexField::getFldType,
                        ElasticIndexField::getNativeType, ElasticIndexField::isIndexed)
                .containsExactlyInAnyOrder(
                        tuple("Count", FieldType.INTEGER, "integer", true),
                        tuple("Name", FieldType.KEYWORD, "keyword", true));
    }

    @Test
    void testGetIndexField() throws IOException {
        givenIndexDoc();
        givenMapping("""
                {
                  "Name": { "type": "keyword" }
                }""");

        assertThat(elasticSearchProvider.getIndexField(INDEX_REF, "Name"))
                .isInstanceOf(ElasticIndexField.class)
                .extracting(field -> field.getFldType())
                .isEqualTo(FieldType.KEYWORD);
    }

    @Test
    void testGetIndexField_missingIndex() {
        assertThat(elasticSearchProvider.getIndexField(INDEX_REF, "Name")).isNull();
    }

    @Test
    void testGetFieldCount() throws IOException {
        givenUseAsRead();
        givenIndexDoc();
        givenMapping("""
                {
                  "Count": { "type": "integer" },
                  "Location": { "type": "geo_point" },
                  "Name": { "type": "keyword" }
                }""");

        assertThat(elasticSearchProvider.getFieldCount(INDEX_REF)).isEqualTo(2);
    }

    @Test
    void testGetFieldCount_missingIndex() {
        givenUseAsRead();

        assertThat(elasticSearchProvider.getFieldCount(INDEX_REF)).isZero();
    }

    @Test
    void testGetTimeField() {
        givenUseAsRead();
        when(elasticIndexStore.readDocument(INDEX_REF))
                .thenReturn(createIndex().copy().timeField("EventTime").build());

        final Optional<QueryField> timeField = elasticSearchProvider.getTimeField(INDEX_REF);

        assertThat(timeField).isPresent();
        assertThat(timeField.get().getFldName()).isEqualTo("EventTime");
        assertThat(timeField.get().getFldType()).isEqualTo(FieldType.DATE);
    }

    @Test
    void testGetTimeField_whitespace() {
        givenUseAsRead();
        when(elasticIndexStore.readDocument(INDEX_REF))
                .thenReturn(createIndex().copy().timeField("  ").build());

        assertThat(elasticSearchProvider.getTimeField(INDEX_REF)).isEmpty();
    }

    @Test
    void testGetTimeField_defaultedWhenNotSet() {
        givenUseAsRead();
        // The index document defaults a null or empty time field to `@timestamp`
        when(elasticIndexStore.readDocument(INDEX_REF))
                .thenReturn(createIndex().copy().timeField(null).build());

        assertThat(elasticSearchProvider.getTimeField(INDEX_REF))
                .map(QueryField::getFldName)
                .hasValue("@timestamp");
    }

    @Test
    void testGetTimeField_missingIndex() {
        givenUseAsRead();

        assertThat(elasticSearchProvider.getTimeField(INDEX_REF)).isEmpty();
    }

    private void givenUseAsRead() {
        when(securityContext.useAsReadResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    }

    private void givenIndexDoc() {
        when(elasticIndexStore.readDocument(INDEX_REF)).thenReturn(createIndex());
    }

    /// Stubs the mapping response for a single index with the supplied properties.
    private void givenMapping(final String propertiesJson) throws IOException {
        givenMappingResponse("""
                {
                  "%s": {
                    "mappings": {
                      "properties": %s
                    }
                  }
                }""".formatted(INDEX_NAME, propertiesJson));
    }

    /// Stubs the full get-mapping response, which may contain several indices.
    private void givenMappingResponse(final String responseJson) throws IOException {
        givenClient();
        final GetMappingResponse response = GetMappingResponse.of(builder ->
                builder.withJson(new StringReader(responseJson)));
        when(elasticIndicesClient.getMapping(any(GetMappingRequest.class))).thenReturn(response);
    }

    private void givenClient() {
        final ElasticClusterDoc cluster = createCluster();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(cluster);
        when(elasticClientCache.contextResult(eq(cluster.getConnection()), any()))
                .thenAnswer(invocation -> invocation
                        .<Function<ElasticsearchClient, ?>>getArgument(1)
                        .apply(elasticClient));
        when(elasticClient.indices()).thenReturn(elasticIndicesClient);
    }

    private static ElasticClusterDoc createCluster() {
        return ElasticClusterDoc.builder()
                .uuid(CLUSTER_REF.getUuid())
                .connection(new ElasticConnectionConfig(List.of("http://localhost:9200"), null, null))
                .build();
    }

    private static ElasticIndexDoc createIndex() {
        return ElasticIndexDoc.builder()
                .uuid(INDEX_REF.getUuid())
                .clusterRef(CLUSTER_REF)
                .indexName(INDEX_NAME)
                .build();
    }

    private static Kind getKind(final NavigableMap<String, FieldMapping> mappings, final String fieldName) {
        return mappings.get(fieldName).mapping().values().iterator().next()._kind();
    }
}
