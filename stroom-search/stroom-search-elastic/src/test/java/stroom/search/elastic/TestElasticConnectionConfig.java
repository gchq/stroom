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

import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.json.JsonUtil;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpTlsConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticConnectionConfig {

    @Test
    void testReadLegacyDocument() {
        // A document saved before credentials were used. It must still load, but the inline secrets are
        // dropped and must not be written back out.
        final String json = """
                {
                  "type" : "ElasticCluster",
                  "uuid" : "f1c0a5e2-1b2c-4d5e-8f90-123456789abc",
                  "name" : "Test cluster",
                  "connection" : {
                    "connectionUrls" : [ "https://elastic:9200" ],
                    "caCertificate" : "-----BEGIN CERTIFICATE-----",
                    "useAuthentication" : true,
                    "apiKeyId" : "keyId",
                    "apiKeySecret" : "topSecret",
                    "connectionTimeoutMillis" : 3000,
                    "responseTimeoutMillis" : 0
                  }
                }""";

        final ElasticClusterDoc doc = JsonUtil.readValue(json, ElasticClusterDoc.class);

        assertThat(doc.getConnection().getConnectionUrls())
                .containsExactly("https://elastic:9200");
        assertThat(doc.getConnection().getApiKeyName())
                .isNull();
        assertThat(doc.getConnection().getHttpClientConfiguration())
                .isNull();

        final String written = JsonUtil.writeValueAsString(doc);
        assertThat(written)
                .doesNotContain("topSecret")
                .doesNotContain("keyId")
                .doesNotContain("CERTIFICATE");
    }

    @Test
    void testRoundTrip() {
        final ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig(
                List.of("https://elastic1:9200", "https://elastic2:9200"),
                "elastic-api-key",
                HttpClientConfig.builder()
                        .tlsConfiguration(HttpTlsConfig.builder()
                                .trustStoreName("elastic-trust-store")
                                .build())
                        .build());
        final ElasticClusterDoc doc = ElasticClusterDoc.builder()
                .uuid("f1c0a5e2-1b2c-4d5e-8f90-123456789abc")
                .name("Test cluster")
                .connection(connectionConfig)
                .build();

        final String json = JsonUtil.writeValueAsString(doc);
        final ElasticClusterDoc read = JsonUtil.readValue(json, ElasticClusterDoc.class);

        assertThat(read.getConnection())
                .isEqualTo(connectionConfig);
        assertThat(read)
                .isEqualTo(doc);
    }
}
