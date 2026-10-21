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

import java.util.List;
import java.util.Objects;

/// An Elasticsearch connection with all credential names replaced by the secrets they refer to.
///
/// This holds secrets, so it must never be serialised, sent to the UI or logged. It exists only to build
/// a client and to key the client cache, so that a change to a credential results in a new client.
///
/// @param connectionUrls          The URLs of the cluster nodes.
/// @param apiKey                  The base64 encoded Elasticsearch API key, or `null` if none is used.
/// @param httpClientConfiguration The HTTP client configuration, with key/trust stores resolved.
public record ResolvedElasticConnection(List<String> connectionUrls,
                                        String apiKey,
                                        HttpClientConfiguration httpClientConfiguration) {

    public ResolvedElasticConnection {
        Objects.requireNonNull(connectionUrls, "Null connection URLs");
        Objects.requireNonNull(httpClientConfiguration, "Null HTTP client configuration");
        connectionUrls = List.copyOf(connectionUrls);
    }

    @Override
    public String toString() {
        // Never include the API key or the HTTP client configuration, as the latter holds key store passwords
        return "ResolvedElasticConnection{" +
               "connectionUrls=" + connectionUrls +
               ", apiKey=" + (apiKey == null
                ? "null"
                : "<redacted>") +
               '}';
    }
}
