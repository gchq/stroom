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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
