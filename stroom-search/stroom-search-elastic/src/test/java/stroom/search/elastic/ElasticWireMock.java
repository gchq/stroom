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

import stroom.util.http.HttpClientConfiguration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

/// Shared set-up for tests that use the real Elasticsearch Java client against a WireMock server standing in
/// for an Elasticsearch cluster.
///
/// Register the server in a test class with:
/// ```
/// @RegisterExtension
/// static final WireMockExtension ELASTIC = ElasticWireMock.createExtension();
/// ```
/// The extension starts the server before the first test and stops it after the last, and resets its stubs,
/// scenarios and request journal before each test.
public final class ElasticWireMock {

    private ElasticWireMock() {
        // Static utility class
    }

    /// Creates a WireMock JUnit 5 extension for a plain HTTP server on a dynamic port.
    ///
    /// @return The extension, to be registered on a static field with `@RegisterExtension`.
    public static WireMockExtension createExtension() {
        return WireMockExtension.newInstance()
                .options(WireMockConfiguration.wireMockConfig()
                        .dynamicPort()
                        // Elasticsearch only speaks HTTP/1.1
                        .http2PlainDisabled(true))
                .build();
    }

    /// Creates an Elasticsearch client, with default settings, that connects to the supplied server.
    ///
    /// @param server The running WireMock server.
    /// @return The client, which the caller must close.
    public static ElasticsearchClient createClient(final WireMockExtension server) {
        return new ElasticClientFactory().create(
                new ResolvedElasticConnection(
                        List.of(server.baseUrl()),
                        null,
                        HttpClientConfiguration.builder().build()),
                new ElasticClientConfig());
    }

    /// Creates a JSON response that the Elasticsearch client will accept as coming from Elasticsearch.
    ///
    /// @param status The HTTP status code.
    /// @param body   The JSON response body.
    /// @return The response definition.
    public static ResponseDefinitionBuilder elasticResponse(final int status, final String body) {
        return WireMock.aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                // Checked by the client, which refuses to talk to anything that does not claim to be
                // Elasticsearch
                .withHeader("X-Elastic-Product", "Elasticsearch")
                .withBody(body.getBytes(StandardCharsets.UTF_8));
    }
}
