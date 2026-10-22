/*
 * Copyright 2021 Crown Copyright
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

package stroom.search.elastic.shared;

import stroom.util.shared.http.HttpClientConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// The connection details for an Elasticsearch cluster.
///
/// Secrets are never held here. The API key and any key/trust stores are referred to by the name of a
/// Stroom credential and are resolved on the server at the point of use, so this object is safe to send to
/// the UI and to export.
@JsonPropertyOrder({
        "connectionUrls",
        "apiKeyName",
        "httpClientConfiguration"})
@JsonInclude(Include.NON_NULL)
public class ElasticConnectionConfig implements Serializable {

    @JsonProperty
    private List<String> connectionUrls = new ArrayList<>();

    // The name of an Access Token credential holding the base64 encoded Elasticsearch API key
    @JsonProperty
    private String apiKeyName;

    // HTTP client settings, including TLS (key/trust store credential names) and proxy configuration
    @JsonProperty
    private HttpClientConfig httpClientConfiguration;

    public ElasticConnectionConfig() {
    }

    @JsonCreator
    public ElasticConnectionConfig(@JsonProperty("connectionUrls") final List<String> connectionUrls,
                                   @JsonProperty("apiKeyName") final String apiKeyName,
                                   @JsonProperty("httpClientConfiguration")
                                   final HttpClientConfig httpClientConfiguration) {
        this.connectionUrls = connectionUrls;
        this.apiKeyName = apiKeyName;
        this.httpClientConfiguration = httpClientConfiguration;
    }

    public List<String> getConnectionUrls() {
        return connectionUrls;
    }

    public void setConnectionUrls(final List<String> connectionUrls) {
        this.connectionUrls = connectionUrls;
    }

    /// @return The name of the Access Token credential holding the encoded Elasticsearch API key, or `null`
    /// if the cluster does not require authentication.
    public String getApiKeyName() {
        return apiKeyName;
    }

    /// @param apiKeyName The name of the Access Token credential holding the encoded Elasticsearch API key,
    ///                   or `null` if the cluster does not require authentication.
    public void setApiKeyName(final String apiKeyName) {
        this.apiKeyName = apiKeyName;
    }

    /// @return The HTTP client settings to connect with, or `null` to use the defaults.
    public HttpClientConfig getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    /// @param httpClientConfiguration The HTTP client settings to connect with, or `null` to use the defaults.
    public void setHttpClientConfiguration(final HttpClientConfig httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof final ElasticConnectionConfig that)) {
            return false;
        }
        return Objects.equals(connectionUrls, that.connectionUrls) &&
               Objects.equals(apiKeyName, that.apiKeyName) &&
               Objects.equals(httpClientConfiguration, that.httpClientConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionUrls, apiKeyName, httpClientConfiguration);
    }

    @Override
    public String toString() {
        return "ElasticConnectionConfig{" +
               "connectionUrls=" + connectionUrls +
               ", apiKeyName='" + apiKeyName + '\'' +
               ", httpClientConfiguration=" + httpClientConfiguration +
               '}';
    }
}
