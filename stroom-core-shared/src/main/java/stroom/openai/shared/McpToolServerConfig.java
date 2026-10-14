/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.openai.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.http.HttpClientConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * Defines a single HTTP MCP (Model Context Protocol) tool server that the "Ask Stroom AI" chat agent may
 * use while answering a question. The server is contacted over the MCP <em>Streamable HTTP</em> transport,
 * so both {@code http://} and {@code https://} endpoints are supported. An enabled server contributes both
 * its MCP tools and its MCP resources (the latter exposed to the agent as list_resources / get_resource
 * tools).
 * <p>
 * Only the parts of {@link #getHttpClientConfiguration()} that the MCP transport can honour are applied:
 * the response timeout and redirect handling for any endpoint, and the TLS settings for
 * {@code https://} endpoints. Other HTTP client settings (proxy, connection pooling, cookies, retries,
 * hostname-verification override) are ignored for MCP connections, as are the TLS settings for a plain
 * {@code http://} endpoint.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class McpToolServerConfig {

    public static final String DEFAULT_API_KEY_HEADER_NAME = "Authorization";

    @JsonPropertyDescription("Whether this tool server is active. Disabled servers are ignored.")
    @JsonProperty
    private final boolean enabled;

    @JsonPropertyDescription("A short name for this tool server. Used in the UI, to prefix tool names and " +
                             "in error messages.")
    @JsonProperty
    private final String name;

    @JsonPropertyDescription("The MCP Streamable HTTP endpoint URL, e.g. https://mcp.example.com/mcp . " +
                             "Both http and https are supported.")
    @JsonProperty
    private final String endpoint;

    @JsonPropertyDescription("How Stroom authenticates to this server.")
    @JsonProperty
    private final McpAuthMode authMode;

    @JsonPropertyDescription("The name of the stored credential to authenticate with. For 'Bearer token' " +
                             "and 'Custom API key header' this is an Access Token credential; for " +
                             "'Basic auth' it is a Username / Password credential. Not used for 'None' or " +
                             "'OAuth2 passthrough'.")
    @JsonProperty
    private final String credentialName;

    @JsonPropertyDescription("For 'Custom API key header', the HTTP header to send the key in. " +
                             "Default: Authorization")
    @JsonProperty
    private final String apiKeyHeaderName;

    @JsonPropertyDescription("For 'Custom API key header', an optional prefix placed before the key value, " +
                             "e.g. 'Bearer '.")
    @JsonProperty
    private final String apiKeyValuePrefix;

    @JsonPropertyDescription("HTTP client settings for connecting to this server. If not set, a default " +
                             "configuration is used.")
    @JsonProperty
    private final HttpClientConfig httpClientConfiguration;

    @JsonPropertyDescription("Patterns for the tools of this server that may be offered to the model. '*' matches " +
                             "any characters. If empty, all tools are allowed (apart from denied ones).")
    @JsonProperty
    private final List<String> allowedTools;

    @JsonPropertyDescription("Patterns for the tools of this server that must never be offered to the model. " +
                             "'*' matches any characters. Takes precedence over the allowed tools.")
    @JsonProperty
    private final List<String> deniedTools;

    @JsonCreator
    public McpToolServerConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("name") final String name,
            @JsonProperty("endpoint") final String endpoint,
            @JsonProperty("authMode") final McpAuthMode authMode,
            @JsonProperty("credentialName") final String credentialName,
            @JsonProperty("apiKeyHeaderName") final String apiKeyHeaderName,
            @JsonProperty("apiKeyValuePrefix") final String apiKeyValuePrefix,
            @JsonProperty("httpClientConfiguration") final HttpClientConfig httpClientConfiguration,
            @JsonProperty("allowedTools") final List<String> allowedTools,
            @JsonProperty("deniedTools") final List<String> deniedTools) {
        this.enabled = Objects.requireNonNullElse(enabled, true);
        this.name = name;
        this.endpoint = endpoint;
        this.authMode = Objects.requireNonNullElse(authMode, McpAuthMode.NONE);
        this.credentialName = credentialName;
        this.apiKeyHeaderName = apiKeyHeaderName;
        this.apiKeyValuePrefix = apiKeyValuePrefix;
        this.httpClientConfiguration = httpClientConfiguration;
        this.allowedTools = allowedTools;
        this.deniedTools = deniedTools;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public McpAuthMode getAuthMode() {
        return authMode;
    }

    public String getCredentialName() {
        return credentialName;
    }

    public String getApiKeyHeaderName() {
        return apiKeyHeaderName;
    }

    public String getApiKeyValuePrefix() {
        return apiKeyValuePrefix;
    }

    public HttpClientConfig getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    /**
     * @return Patterns for the tools that may be offered to the model, or null/empty for all of them.
     */
    public List<String> getAllowedTools() {
        return allowedTools;
    }

    /**
     * @return Patterns for the tools that must never be offered to the model, or null/empty for none.
     */
    public List<String> getDeniedTools() {
        return deniedTools;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final McpToolServerConfig that = (McpToolServerConfig) o;
        return enabled == that.enabled &&
               Objects.equals(name, that.name) &&
               Objects.equals(endpoint, that.endpoint) &&
               authMode == that.authMode &&
               Objects.equals(credentialName, that.credentialName) &&
               Objects.equals(apiKeyHeaderName, that.apiKeyHeaderName) &&
               Objects.equals(apiKeyValuePrefix, that.apiKeyValuePrefix) &&
               Objects.equals(httpClientConfiguration, that.httpClientConfiguration) &&
               Objects.equals(allowedTools, that.allowedTools) &&
               Objects.equals(deniedTools, that.deniedTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                name,
                endpoint,
                authMode,
                credentialName,
                apiKeyHeaderName,
                apiKeyValuePrefix,
                httpClientConfiguration,
                allowedTools,
                deniedTools);
    }

    @Override
    public String toString() {
        return "McpToolServerConfig{" +
               "enabled=" + enabled +
               ", name='" + name + '\'' +
               ", endpoint='" + endpoint + '\'' +
               ", authMode=" + authMode +
               ", credentialName='" + credentialName + '\'' +
               ", apiKeyHeaderName='" + apiKeyHeaderName + '\'' +
               ", httpClientConfiguration=" + httpClientConfiguration +
               ", allowedTools=" + allowedTools +
               ", deniedTools=" + deniedTools +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractBuilder<McpToolServerConfig, Builder> {

        private boolean enabled = true;
        private String name;
        private String endpoint;
        private McpAuthMode authMode = McpAuthMode.NONE;
        private String credentialName;
        private String apiKeyHeaderName;
        private String apiKeyValuePrefix;
        private HttpClientConfig httpClientConfiguration;
        private List<String> allowedTools;
        private List<String> deniedTools;

        private Builder() {
        }

        private Builder(final McpToolServerConfig config) {
            this.enabled = config.enabled;
            this.name = config.name;
            this.endpoint = config.endpoint;
            this.authMode = config.authMode;
            this.credentialName = config.credentialName;
            this.apiKeyHeaderName = config.apiKeyHeaderName;
            this.apiKeyValuePrefix = config.apiKeyValuePrefix;
            this.httpClientConfiguration = config.httpClientConfiguration;
            this.allowedTools = config.allowedTools;
            this.deniedTools = config.deniedTools;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder endpoint(final String endpoint) {
            this.endpoint = endpoint;
            return self();
        }

        public Builder authMode(final McpAuthMode authMode) {
            this.authMode = authMode;
            return self();
        }

        public Builder credentialName(final String credentialName) {
            this.credentialName = credentialName;
            return self();
        }

        public Builder apiKeyHeaderName(final String apiKeyHeaderName) {
            this.apiKeyHeaderName = apiKeyHeaderName;
            return self();
        }

        public Builder apiKeyValuePrefix(final String apiKeyValuePrefix) {
            this.apiKeyValuePrefix = apiKeyValuePrefix;
            return self();
        }

        public Builder httpClientConfiguration(final HttpClientConfig httpClientConfiguration) {
            this.httpClientConfiguration = httpClientConfiguration;
            return self();
        }

        public Builder allowedTools(final List<String> allowedTools) {
            this.allowedTools = allowedTools;
            return self();
        }

        public Builder deniedTools(final List<String> deniedTools) {
            this.deniedTools = deniedTools;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public McpToolServerConfig build() {
            return new McpToolServerConfig(
                    enabled,
                    name,
                    endpoint,
                    authMode,
                    credentialName,
                    apiKeyHeaderName,
                    apiKeyValuePrefix,
                    httpClientConfiguration,
                    allowedTools,
                    deniedTools);
        }
    }
}
