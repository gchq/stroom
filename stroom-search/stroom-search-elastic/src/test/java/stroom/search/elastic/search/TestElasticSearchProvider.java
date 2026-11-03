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
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.suggest.ElasticSuggestConfig;
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
                null,
                ElasticSuggestConfig::new);
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
    void testRegisterField_matchOnlyTextMultiField() {
        final Property message = Property.of(p -> p.matchOnlyText(t -> t
                .fields("keyword", Property.of(k -> k.keyword(kw -> kw)))));

        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("Message", message, out);

        assertThat(out).containsOnlyKeys("Message", "Message.keyword");
        assertThat(getKind(out, "Message")).isEqualTo(Kind.MatchOnlyText);
        assertThat(getKind(out, "Message.keyword")).isEqualTo(Kind.Keyword);
    }

    @Test
    void testRegisterField_passthroughChildren() {
        final Property attributes = Property.of(p -> p.passthrough(pt -> pt
                .priority(10)
                .properties("Host", Property.of(h -> h.keyword(kw -> kw)))
                .properties("Http", Property.of(h -> h.object(o -> o
                        .properties("Status", Property.of(s -> s.long_(l -> l))))))));

        final NavigableMap<String, FieldMapping> out = new TreeMap<>();
        ElasticSearchProvider.registerField("Attributes", attributes, out);

        assertThat(out).containsOnlyKeys(
                "Attributes", "Attributes.Host", "Attributes.Http", "Attributes.Http.Status");
        assertThat(getKind(out, "Attributes.Host")).isEqualTo(Kind.Keyword);
        assertThat(getKind(out, "Attributes.Http.Status")).isEqualTo(Kind.Long);
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
                        tuple("Version", FieldType.KEYWORD, true));
    }

    @Test
    void testGetDataSourceFields_versionComparedLikeANumber() throws IOException {
        // A version is text, but can be compared like a number, as Elasticsearch orders versions by precedence
        givenMapping("""
                {
                  "Version": { "type": "version" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::getConditionSet)
                .containsExactly(tuple("Version", FieldType.KEYWORD, ConditionSet.ELASTIC_NUMERIC));
    }

    @Test
    void testGetDataSourceFields_booleanOnlyOfferedEqualsAndNotEquals() throws IOException {
        // A boolean field has only two values, so is only compared with one of them
        givenMapping("""
                {
                  "Flag": { "type": "boolean" }
                }""");

        final List<QueryField> fields = elasticSearchProvider.getDataSourceFields(createIndex());

        assertThat(fields)
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::getConditionSet)
                .containsExactly(tuple("Flag", FieldType.BOOLEAN, ConditionSet.ELASTIC_BOOLEAN));
        assertThat(fields.getFirst().getConditionSet().getConditionList())
                .containsExactly(Condition.EQUALS, Condition.NOT_EQUALS);
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
    void testGetDataSourceFields_matchOnlyTextIsText() throws IOException {
        givenMapping("""
                {
                  "Message": {
                    "type": "match_only_text",
                    "fields": {
                      "keyword": { "type": "keyword" }
                    }
                  }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::queryable)
                .containsExactly(
                        tuple("Message", FieldType.TEXT, true),
                        tuple("Message.keyword", FieldType.KEYWORD, true));
        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()).getFirst().getConditionSet())
                .isEqualTo(ConditionSet.ELASTIC_TEXT);
    }

    @Test
    void testGetDataSourceFields_queryOptionSets() throws IOException {
        givenMapping("""
                {
                  "Constant": { "type": "constant_keyword", "value": "foo" },
                  "Count": { "type": "long" },
                  "Message": { "type": "match_only_text" },
                  "Name": { "type": "keyword" },
                  "Pattern": { "type": "wildcard" },
                  "Title": { "type": "text" },
                  "Version": { "type": "version" }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getQueryOptionSet)
                .containsExactlyInAnyOrder(
                        tuple("Constant", null),
                        tuple("Count", null),
                        tuple("Message", ElasticQueryOptionSets.TEXT),
                        tuple("Name", ElasticQueryOptionSets.KEYWORD),
                        tuple("Pattern", ElasticQueryOptionSets.KEYWORD),
                        tuple("Title", ElasticQueryOptionSets.TEXT),
                        tuple("Version", null));
    }

    @Test
    void testGetDataSourceFields_supportsTopValues() throws IOException {
        givenMapping("""
                {
                  "Count": { "type": "long" },
                  "Flag": { "type": "boolean" },
                  "Ip": { "type": "ip" },
                  "Message": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword" }
                    }
                  },
                  "Name": { "type": "keyword" },
                  "Pattern": { "type": "wildcard" },
                  "Ratio": { "type": "double" },
                  "Time": { "type": "date" },
                  "Title": { "type": "text" },
                  "Users": {
                    "type": "nested",
                    "properties": {
                      "Name": { "type": "keyword" }
                    }
                  }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .filteredOn(QueryField::supportsTopValues)
                .extracting(QueryField::getFldName)
                // A field within a nested object lists its values, but the nested object itself has none
                .containsExactlyInAnyOrder(
                        "Count", "Flag", "Ip", "Message", "Message.keyword", "Name", "Pattern", "Users.Name");
    }

    @Test
    void testGetDataSourceFields_noTopValuesWhenValuePickerDisabled() throws IOException {
        givenMapping("""
                {
                  "Name": { "type": "keyword" }
                }""");
        final ElasticSearchProvider provider = new ElasticSearchProvider(
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
                null,
                () -> new ElasticSuggestConfig(false, null));

        assertThat(provider.getDataSourceFields(createIndex()))
                .extracting(QueryField::supportsTopValues)
                .containsExactly(false);
    }

    @Test
    void testGetDataSourceFields_passthroughDroppedChildrenKept() throws IOException {
        givenMapping("""
                {
                  "Attributes": {
                    "type": "passthrough",
                    "priority": 10,
                    "properties": {
                      "Host": { "type": "keyword" },
                      "Port": { "type": "long" }
                    }
                  }
                }""");

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::queryable)
                .containsExactly(
                        tuple("Attributes.Host", FieldType.KEYWORD, true),
                        tuple("Attributes.Port", FieldType.LONG, true));
        assertThat(elasticSearchProvider.getFieldsMap(createIndex()))
                .containsOnlyKeys("Attributes.Host", "Attributes.Port");
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
    void testGetDataSourceFields_mergedAcrossIndicesConflictingTypeHidden() throws IOException {
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

        // `Shared` is mapped to different types, so Stroom can't choose how to query it
        assertThat(elasticSearchProvider.getDataSourceFields(createIndex()))
                .extracting(QueryField::getFldName, QueryField::getFldType)
                .containsExactly(
                        tuple("OnlyInA", FieldType.LONG),
                        tuple("OnlyInB", FieldType.IPV4_ADDRESS));
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
    @Test
    void testGetFieldsMap_multiFieldParentAndMappingParameters() throws IOException {
        givenMapping("""
                {
                  "Title": {
                    "type": "text",
                    "analyzer": "english",
                    "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } }
                  },
                  "Vector": { "type": "dense_vector", "dims": 3, "similarity": "cosine" }
                }""");

        final Map<String, ElasticIndexField> fieldsMap = elasticSearchProvider.getFieldsMap(createIndex());

        assertThat(fieldsMap.get("Title").getMultiFieldParent()).isNull();
        assertThat(fieldsMap.get("Title").getMappingParameters())
                .containsExactly(Map.entry("analyzer", "english"));
        assertThat(fieldsMap.get("Title.keyword").getMultiFieldParent()).isEqualTo("Title");
        assertThat(fieldsMap.get("Title.keyword").getMappingParameters())
                .containsExactly(Map.entry("ignore_above", "256"));
        assertThat(fieldsMap.get("Vector").getMappingParameters())
                .containsEntry("dims", "3")
                .containsEntry("similarity", "cosine")
                .doesNotContainKey("type");
        assertThat(fieldsMap.get("Vector").getSourceIndices()).containsExactly(INDEX_NAME);
    }

    @Test
    void testGetFieldsMap_typeConflictAcrossIndices() throws IOException {
        givenMappingResponse("""
                {
                  "logs-a": { "mappings": { "properties": {
                    "Host": { "type": "keyword" },
                    "Port": { "type": "long" }
                  } } },
                  "logs-b": { "mappings": { "properties": {
                    "Host": { "type": "keyword" },
                    "Port": { "type": "keyword" },
                    "Extra": { "type": "keyword" }
                  } } }
                }""");

        final Map<String, ElasticIndexField> fieldsMap = elasticSearchProvider.getFieldsMap(createIndex());

        assertThat(fieldsMap.get("Port").getConflictingTypes())
                .containsExactly(Map.entry("logs-a", "long"), Map.entry("logs-b", "keyword"));
        assertThat(fieldsMap.get("Port").hasTypeConflict()).isTrue();
        assertThat(fieldsMap.get("Host").hasTypeConflict()).isFalse();
        assertThat(fieldsMap.get("Host").getSourceIndices()).containsExactly("logs-a", "logs-b");
        assertThat(fieldsMap.get("Extra").getSourceIndices()).containsExactly("logs-b");
    }

    @Test
    void testGetDataSourceFields_fieldSettings() throws IOException {
        givenMappingResponse("""
                {
                  "logs-a": { "mappings": { "properties": {
                    "Host": { "type": "keyword" },
                    "Port": { "type": "long" },
                    "Role": { "type": "keyword" },
                    "Title": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                    "User": { "type": "keyword" }
                  } } },
                  "logs-b": { "mappings": { "properties": {
                    "Port": { "type": "keyword" }
                  } } }
                }""");
        final ElasticIndexDoc index = createIndex().copy()
                .fieldSettings(List.of(
                        ElasticIndexFieldSettings.builder("User").enabled(false).build(),
                        ElasticIndexFieldSettings.builder("Title").enabled(false).build(),
                        ElasticIndexFieldSettings.builder("Category").virtualGroups(List.of()).build(),
                        ElasticIndexFieldSettings.builder("Hidden").virtualGroups(List.of()).enabled(false).build(),
                        ElasticIndexFieldSettings.builder("Role").virtualGroups(List.of()).build()))
                .build();

        // Disabled fields (and the multi-fields of disabled fields), conflicting fields and disabled virtual
        // fields are left out. A virtual field named like a mapped field is hidden by the mapped field.
        assertThat(elasticSearchProvider.getDataSourceFields(index))
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::getConditionSet)
                .containsExactly(
                        tuple("Category", FieldType.KEYWORD, ConditionSet.ELASTIC_VIRTUAL),
                        tuple("Host", FieldType.KEYWORD, ConditionSet.ELASTIC_TEXT),
                        tuple("Role", FieldType.KEYWORD, ConditionSet.ELASTIC_TEXT));
    }

    @Test
    void testGetIndexField_virtualField() throws IOException {
        givenMapping("""
                {
                  "Host": { "type": "keyword" }
                }""");
        final ElasticIndexDoc index = createIndex().copy()
                .fieldSettings(List.of(ElasticIndexFieldSettings.builder("Category")
                        .virtualGroups(List.of())
                        .enabled(false)
                        .build()))
                .build();
        when(elasticIndexStore.readDocument(INDEX_REF)).thenReturn(index);

        // Returned even though disabled, so query building can say why it can't be used
        final ElasticIndexField virtual = (ElasticIndexField) elasticSearchProvider.getIndexField(
                INDEX_REF, "Category");
        assertThat(virtual.isVirtual()).isTrue();
        assertThat(virtual.getFldType()).isEqualTo(FieldType.KEYWORD);
        assertThat(((ElasticIndexField) elasticSearchProvider.getIndexField(INDEX_REF, "Host")).isVirtual())
                .isFalse();
        assertThat(elasticSearchProvider.getIndexField(INDEX_REF, "Unknown")).isNull();
    }

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
