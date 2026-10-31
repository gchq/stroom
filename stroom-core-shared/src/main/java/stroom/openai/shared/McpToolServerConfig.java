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

import stroom.ai.shared.AiToolCategory;
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

    @JsonPropertyDescription("For 'OAuth2 passthrough' when Stroom is its own identity provider, the audience of " +
                             "the short-lived token Stroom issues each user for this server, e.g. stroom-mcp. " +
                             "The server must accept it, and for the server to call Stroom back with the token, " +
                             "Stroom's allowed audiences must include it.")
    @JsonProperty
    private final String audience;

    @JsonPropertyDescription("HTTP client settings for connecting to this server. If not set, a default " +
                             "configuration is used.")
    @JsonProperty
    private final HttpClientConfig httpClientConfiguration;

    @JsonPropertyDescription("Whether an admin trusts this server. The MCP tool annotations (e.g. readOnlyHint) of " +
                             "an untrusted server are ignored, so all of its tools ask the user before running, " +
                             "unless a tool policy says otherwise.")
    @JsonProperty
    private final boolean trusted;

    @JsonPropertyDescription("Rules setting what happens when the agent wants to call this server's tools: " +
                             "Allow (run without asking), Ask every time, or Deny (never offered to the model). " +
                             "The first rule whose regular expression matches the whole tool name, ignoring " +
                             "case, applies. Tools no rule matches run without asking if the server is trusted " +
                             "and the tool is read-only, and otherwise ask.")
    @JsonProperty
    private final List<McpToolPolicyRule> toolPolicies;

    @JsonPropertyDescription("The category of tool policy that grants this server's tools, for a tool that doesn't " +
                             "name a category itself (as its _meta.group). If not set, such tools are in Core.")
    @JsonProperty
    private final AiToolCategory toolCategory;

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
            @JsonProperty("trusted") final Boolean trusted,
            @JsonProperty("toolPolicies") final List<McpToolPolicyRule> toolPolicies,
            @JsonProperty("audience") final String audience,
            @JsonProperty("toolCategory") final AiToolCategory toolCategory) {
        this.enabled = Objects.requireNonNullElse(enabled, true);
        this.name = name;
        this.endpoint = endpoint;
        this.authMode = Objects.requireNonNullElse(authMode, McpAuthMode.NONE);
        this.credentialName = credentialName;
        this.apiKeyHeaderName = apiKeyHeaderName;
        this.apiKeyValuePrefix = apiKeyValuePrefix;
        this.httpClientConfiguration = httpClientConfiguration;
        this.trusted = Objects.requireNonNullElse(trusted, false);
        this.toolPolicies = toolPolicies;
        this.audience = audience;
        this.toolCategory = toolCategory;
    }

    /// @return The category of tool policy that grants this server's tools when a tool names none itself, or null
    /// for Core.
    public AiToolCategory getToolCategory() {
        return toolCategory;
    }

    public String getAudience() {
        return audience;
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
     * @return Whether an admin trusts this server, so its tools' MCP annotations are believed.
     */
    public boolean isTrusted() {
        return trusted;
    }

    /**
     * @return The tool policy rules, in the order they are checked, or null/empty for none.
     */
    public List<McpToolPolicyRule> getToolPolicies() {
        return toolPolicies;
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
               trusted == that.trusted &&
               Objects.equals(toolPolicies, that.toolPolicies) &&
               Objects.equals(audience, that.audience) &&
               toolCategory == that.toolCategory;
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
                trusted,
                toolPolicies,
                audience,
                toolCategory);
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
               ", trusted=" + trusted +
               ", toolPolicies=" + toolPolicies +
               ", audience='" + audience + '\'' +
               ", toolCategory=" + toolCategory +
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
        private boolean trusted;
        private List<McpToolPolicyRule> toolPolicies;
        private String audience;
        private AiToolCategory toolCategory;

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
            this.trusted = config.trusted;
            this.toolPolicies = config.toolPolicies;
            this.audience = config.audience;
            this.toolCategory = config.toolCategory;
        }

        public Builder toolCategory(final AiToolCategory toolCategory) {
            this.toolCategory = toolCategory;
            return self();
        }

        public Builder audience(final String audience) {
            this.audience = audience;
            return self();
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

        public Builder trusted(final boolean trusted) {
            this.trusted = trusted;
            return self();
        }

        public Builder toolPolicies(final List<McpToolPolicyRule> toolPolicies) {
            this.toolPolicies = toolPolicies;
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
                    trusted,
                    toolPolicies,
                    audience,
                    toolCategory);
        }
    }
}
