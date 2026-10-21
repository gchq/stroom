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
import stroom.search.elastic.ElasticWireMock;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/// Tests the field discovery of [ElasticSearchProvider] using the real Elasticsearch Java client against a
/// WireMock server standing in for an Elasticsearch cluster, so that the get-mapping request and the
/// deserialisation of its response are exercised end to end.
@ExtendWith(MockitoExtension.class)
class TestElasticSearchProviderWireMock {

    private static final String INDEX_PATTERN = "logs-*";
    private static final String MAPPING_PATH = "/" + INDEX_PATTERN + "/_mapping";
    private static final DocRef CLUSTER_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-uuid", "Test Cluster");

    // Modelled on a real get-mapping response for an index pattern that resolves to two daily indices. The
    // second index has a field (`Shared`) whose type differs from the first, plus a field of its own.
    private static final String MAPPING_RESPONSE = """
            {
              "logs-2026.10.20" : {
                "mappings" : {
                  "dynamic" : "true",
                  "properties" : {
                    "@timestamp" : {
                      "type" : "date"
                    },
                    "Embedding" : {
                      "type" : "dense_vector",
                      "dims" : 3,
                      "index" : true,
                      "similarity" : "cosine",
                      "index_options" : {
                        "type" : "int8_hnsw",
                        "m" : 16,
                        "ef_construction" : 100
                      }
                    },
                    "Host" : {
                      "properties" : {
                        "Ip" : {
                          "type" : "ip"
                        },
                        "Name" : {
                          "type" : "text",
                          "fields" : {
                            "keyword" : {
                              "type" : "keyword",
                              "ignore_above" : 256
                            }
                          }
                        }
                      }
                    },
                    "Location" : {
                      "type" : "geo_point"
                    },
                    "Message" : {
                      "type" : "text",
                      "fields" : {
                        "keyword" : {
                          "type" : "keyword",
                          "ignore_above" : 256
                        }
                      }
                    },
                    "RawPayload" : {
                      "type" : "keyword",
                      "index" : false,
                      "doc_values" : false
                    },
                    "Shared" : {
                      "type" : "keyword"
                    },
                    "Timestamp" : {
                      "type" : "alias",
                      "path" : "@timestamp"
                    },
                    "User" : {
                      "type" : "nested",
                      "properties" : {
                        "Address" : {
                          "type" : "nested",
                          "properties" : {
                            "City" : {
                              "type" : "keyword"
                            },
                            "PostCode" : {
                              "type" : "keyword",
                              "index" : false
                            }
                          }
                        },
                        "Id" : {
                          "type" : "keyword"
                        }
                      }
                    }
                  }
                }
              },
              "logs-2026.10.21" : {
                "mappings" : {
                  "dynamic" : "true",
                  "properties" : {
                    "@timestamp" : {
                      "type" : "date"
                    },
                    "Bytes" : {
                      "type" : "long"
                    },
                    "Message" : {
                      "type" : "text",
                      "fields" : {
                        "keyword" : {
                          "type" : "keyword",
                          "ignore_above" : 256
                        }
                      }
                    },
                    "Shared" : {
                      "type" : "text"
                    }
                  }
                }
              }
            }""";

    private static final String INDEX_NOT_FOUND_RESPONSE = """
            {
              "error" : {
                "root_cause" : [
                  {
                    "type" : "index_not_found_exception",
                    "reason" : "no such index [logs-*]",
                    "resource.type" : "index_or_alias",
                    "resource.id" : "logs-*",
                    "index_uuid" : "_na_",
                    "index" : "logs-*"
                  }
                ],
                "type" : "index_not_found_exception",
                "reason" : "no such index [logs-*]",
                "resource.type" : "index_or_alias",
                "resource.id" : "logs-*",
                "index_uuid" : "_na_",
                "index" : "logs-*"
              },
              "status" : 404
            }""";

    @RegisterExtension
    static final WireMockExtension ELASTIC = ElasticWireMock.createExtension();

    @Mock
    private ElasticClientCache elasticClientCache;
    @Mock
    private ElasticClusterStore elasticClusterStore;

    private ElasticsearchClient elasticClient;
    private ElasticSearchProvider elasticSearchProvider;

    @BeforeEach
    void setUp() {
        elasticClient = ElasticWireMock.createClient(ELASTIC);

        final ElasticClusterDoc cluster = ElasticClusterDoc.builder()
                .uuid(CLUSTER_REF.getUuid())
                .connection(new ElasticConnectionConfig(
                        List.of(ELASTIC.baseUrl()), null, null))
                .build();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(cluster);
        when(elasticClientCache.contextResult(eq(cluster.getConnection()), any()))
                .thenAnswer(invocation -> invocation
                        .<Function<ElasticsearchClient, ?>>getArgument(1)
                        .apply(elasticClient));

        elasticSearchProvider = new ElasticSearchProvider(
                null,
                null,
                null,
                null,
                null,
                null,
                elasticClientCache,
                elasticClusterStore,
                null,
                null,
                null,
                null,
                null);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (elasticClient != null) {
            elasticClient.close();
        }
    }

    @Test
    void testGetDataSourceFields() {
        stubMapping(ElasticWireMock.elasticResponse(200, MAPPING_RESPONSE));

        final List<QueryField> fields = elasticSearchProvider.getDataSourceFields(createIndex());

        // Ordered case-insensitively. The object container `Host`, the geo_point `Location` (unsupported)
        // are dropped, and the first definition of `Shared` wins
        assertThat(fields)
                .extracting(QueryField::getFldName, QueryField::getFldType, QueryField::queryable)
                .containsExactly(
                        tuple("@timestamp", FieldType.DATE, true),
                        tuple("Bytes", FieldType.LONG, true),
                        tuple("Embedding", FieldType.DENSE_VECTOR, true),
                        tuple("Host.Ip", FieldType.IPV4_ADDRESS, true),
                        tuple("Host.Name", FieldType.TEXT, true),
                        tuple("Host.Name.keyword", FieldType.KEYWORD, true),
                        tuple("Message", FieldType.TEXT, true),
                        tuple("Message.keyword", FieldType.KEYWORD, true),
                        tuple("RawPayload", FieldType.KEYWORD, false),
                        tuple("Shared", FieldType.KEYWORD, true),
                        tuple("Timestamp", FieldType.DATE, true),
                        tuple("User", FieldType.NESTED, false),
                        tuple("User.Address", FieldType.NESTED, false),
                        tuple("User.Address.City", FieldType.KEYWORD, true),
                        tuple("User.Address.PostCode", FieldType.KEYWORD, false),
                        tuple("User.Id", FieldType.KEYWORD, true));
    }

    @Test
    void testGetFieldsMap() {
        stubMapping(ElasticWireMock.elasticResponse(200, MAPPING_RESPONSE));

        final List<ElasticIndexField> fields = List.copyOf(
                elasticSearchProvider.getFieldsMap(createIndex()).values());

        assertThat(fields)
                .extracting(
                        ElasticIndexField::getFldName,
                        ElasticIndexField::getFldType,
                        ElasticIndexField::getNativeType,
                        ElasticIndexField::isIndexed)
                .containsExactlyInAnyOrder(
                        tuple("@timestamp", FieldType.DATE, "date", true),
                        tuple("Bytes", FieldType.LONG, "long", true),
                        tuple("Embedding", FieldType.DENSE_VECTOR, "dense_vector", true),
                        tuple("Host.Ip", FieldType.IPV4_ADDRESS, "ip", true),
                        tuple("Host.Name", FieldType.TEXT, "text", true),
                        tuple("Host.Name.keyword", FieldType.KEYWORD, "keyword", true),
                        tuple("Message", FieldType.TEXT, "text", true),
                        tuple("Message.keyword", FieldType.KEYWORD, "keyword", true),
                        tuple("RawPayload", FieldType.KEYWORD, "keyword", false),
                        tuple("Shared", FieldType.KEYWORD, "keyword", true),
                        // An alias takes the native type of the field it refers to
                        tuple("Timestamp", FieldType.DATE, "date", true),
                        tuple("User", FieldType.NESTED, "nested", true),
                        tuple("User.Address", FieldType.NESTED, "nested", true),
                        tuple("User.Address.City", FieldType.KEYWORD, "keyword", true),
                        tuple("User.Address.PostCode", FieldType.KEYWORD, "keyword", false),
                        tuple("User.Id", FieldType.KEYWORD, "keyword", true));
    }

    @Test
    void testGetFields() {
        stubMapping(ElasticWireMock.elasticResponse(200, MAPPING_RESPONSE));

        assertThat(elasticSearchProvider.getFields(createIndex()))
                .extracting(ElasticIndexField::getFldName)
                .hasSize(16)
                .contains("Host.Name.keyword", "User.Address.City", "Timestamp")
                .doesNotContain("Host", "Location");
    }

    @Test
    void testMappingRequest() {
        stubMapping(ElasticWireMock.elasticResponse(200, MAPPING_RESPONSE));

        elasticSearchProvider.getDataSourceFields(createIndex());

        // Only open indices are expanded, so closed indices matching the pattern do not contribute fields
        ELASTIC.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(MAPPING_PATH))
                .withQueryParam("expand_wildcards", WireMock.equalTo("open")));
    }

    @Test
    void testNoMatchingIndices() {
        // A wildcard that matches nothing returns an empty object rather than an error
        stubMapping(ElasticWireMock.elasticResponse(200, "{}"));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
        assertThat(elasticSearchProvider.getFieldsMap(createIndex())).isEmpty();
    }

    @Test
    void testIndexNotFound() {
        stubMapping(ElasticWireMock.elasticResponse(404, INDEX_NOT_FOUND_RESPONSE));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
        assertThat(elasticSearchProvider.getFieldsMap(createIndex())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 503})
    void testRequestFailure(final int status) {
        stubMapping(ElasticWireMock.elasticResponse(status, """
                {
                  "error" : {
                    "type" : "some_exception",
                    "reason" : "Something went wrong"
                  },
                  "status" : %d
                }""".formatted(status)));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
    }

    @Test
    void testMalformedResponse() {
        stubMapping(ElasticWireMock.elasticResponse(200, "{ \"logs-2026.10.20\" : { \"mappings\" : "));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
    }

    @Test
    void testNotElasticsearch() {
        // The client refuses responses that lack the Elasticsearch product header
        stubMapping(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(MAPPING_RESPONSE.getBytes(StandardCharsets.UTF_8)));

        assertThat(elasticSearchProvider.getDataSourceFields(createIndex())).isEmpty();
    }

    private static void stubMapping(final ResponseDefinitionBuilder response) {
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo(MAPPING_PATH)).willReturn(response));
    }

    private static ElasticIndexDoc createIndex() {
        return ElasticIndexDoc.builder()
                .uuid("index-uuid")
                .clusterRef(CLUSTER_REF)
                .indexName(INDEX_PATTERN)
                .build();
    }
}
