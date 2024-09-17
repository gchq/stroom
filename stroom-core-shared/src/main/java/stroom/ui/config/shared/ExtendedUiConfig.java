/*
 * Copyright 2024 Crown Copyright
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

package stroom.ui.config.shared;

import stroom.security.shared.HashAlgorithm;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

/**
 * Allows us to make other back-end config available to the UI while having one mechanism
 * to cache the config in the ui.
 */
@NotInjectableConfig // Only meant for use on the client side
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ExtendedUiConfig {

    @JsonProperty
    @JsonPropertyDescription("Whether authentication is provided by an external Open ID Connect identity " +
            "provider or not")
    private final UiConfig uiConfig;

    @JsonProperty
    @JsonPropertyDescription("Whether authentication is provided by an external Open ID Connect identity " +
            "provider or not")
    private final boolean externalIdentityProvider;

    @JsonProperty
    @JsonPropertyDescription(
            "Enables warning indicators in the explorer tree for documents with broken dependencies")
    private final boolean dependencyWarningsEnabled;

    @JsonProperty
    @JsonPropertyDescription("The maximum expiry age for new API keys in millis. Defaults to 365 days.")
    private final long maxApiKeyExpiryAgeMs;


    public ExtendedUiConfig() {
        this.externalIdentityProvider = false;
        this.uiConfig = new UiConfig();
        this.dependencyWarningsEnabled = false;
        this.maxApiKeyExpiryAgeMs = 365L * 24 * 60 * 60 * 1_000;
    }

    @JsonCreator
    public ExtendedUiConfig(
            @JsonProperty("uiConfig") final UiConfig uiConfig,
            @JsonProperty("externalIdentityProvider") final boolean externalIdentityProvider,
            @JsonProperty("dependencyWarningsEnabled") final boolean dependencyWarningsEnabled,
            @JsonProperty("maxApiKeyExpiryAgeMs") final long maxApiKeyExpiryAgeMs) {

        this.uiConfig = uiConfig;
        this.externalIdentityProvider = externalIdentityProvider;
        this.dependencyWarningsEnabled = dependencyWarningsEnabled;
        this.maxApiKeyExpiryAgeMs = maxApiKeyExpiryAgeMs;
    }

    public UiConfig getUiConfig() {
        return uiConfig;
    }

    @JsonIgnore
    public String getWelcomeHtml() {
        return uiConfig.getWelcomeHtml();
    }

    @JsonIgnore
    public String getAboutHtml() {
        return uiConfig.getAboutHtml();
    }

    @JsonIgnore
    public String getMaintenanceMessage() {
        return uiConfig.getMaintenanceMessage();
    }

    @JsonIgnore
    public String getAuthErrorMessage() {
        return uiConfig.getAuthErrorMessage();
    }

    @JsonIgnore
    public String getDefaultMaxResults() {
        return uiConfig.getDefaultMaxResults();
    }

    @JsonIgnore
    public ProcessConfig getProcess() {
        return uiConfig.getProcess();
    }

    @JsonIgnore
    public String getHelpUrl() {
        return uiConfig.getHelpUrl();
    }

    @JsonIgnore
    public String getHelpSubPathJobs() {
        return uiConfig.getHelpSubPathJobs();
    }

    @JsonIgnore
    public String getHelpSubPathQuickFilter() {
        return uiConfig.getHelpSubPathQuickFilter();
    }

    @JsonIgnore
    public String getHelpSubPathProperties() {
        return uiConfig.getHelpSubPathProperties();
    }

    @JsonIgnore
    public String getHelpSubPathExpressions() {
        return uiConfig.getHelpSubPathExpressions();
    }

    @JsonIgnore
    public String getHelpUrlJobs() {
        return uiConfig.getHelpUrlJobs();
    }

    @JsonIgnore
    public String getHelpUrlQuickFilter() {
        return uiConfig.getHelpUrlQuickFilter();
    }

    @JsonIgnore
    public String getHelpUrlProperties() {
        return uiConfig.getHelpUrlProperties();
    }

    @JsonIgnore
    public String getHelpUrlExpressions() {
        return uiConfig.getHelpUrlExpressions();
    }

    @JsonIgnore
    public String getHelpUrlDocumentation() {
        return uiConfig.getHelpUrlDocumentation();
    }

    @JsonIgnore
    public String getHelpUrlStroomQueryLanguage() {
        return uiConfig.getHelpUrlStroomQueryLanguage();
    }

    @JsonIgnore
    public ThemeConfig getTheme() {
        return uiConfig.getTheme();
    }

    @JsonIgnore
    public QueryConfig getQuery() {
        return uiConfig.getQuery();
    }

    @JsonIgnore
    public String getNamePattern() {
        return uiConfig.getNamePattern();
    }

    @JsonIgnore
    public SplashConfig getSplash() {
        return uiConfig.getSplash();
    }

    @JsonIgnore
    public ActivityConfig getActivity() {
        return uiConfig.getActivity();
    }

    @JsonIgnore
    public String getHtmlTitle() {
        return uiConfig.getHtmlTitle();
    }

    @JsonIgnore
    public String getOncontextmenu() {
        return uiConfig.getOncontextmenu();
    }

    @JsonIgnore
    public SourceConfig getSource() {
        return uiConfig.getSource();
    }

    @JsonIgnore
    public NodeMonitoringConfig getNodeMonitoring() {
        return uiConfig.getNodeMonitoring();
    }

    @JsonIgnore
    public AnalyticUiDefaultConfig getAnalyticUiDefaultConfig() {
        return uiConfig.getAnalyticUiDefaultConfig();
    }

    @JsonIgnore
    public String getNestedIndexFieldsDelimiterPattern() {
        return uiConfig.getNestedIndexFieldsDelimiterPattern();
    }

    @JsonIgnore
    public Set<String> getReferencePipelineSelectorIncludedTags() {
        return uiConfig.getReferencePipelineSelectorIncludedTags();
    }

    @JsonIgnore
    public HashAlgorithm getDefaultApiKeyHashAlgorithm() {
        return uiConfig.getDefaultApiKeyHashAlgorithm();
    }

    @JsonIgnore
    public boolean isExternalIdentityProvider() {
        return externalIdentityProvider;
    }

    @JsonIgnore
    public boolean isDependencyWarningsEnabled() {
        return dependencyWarningsEnabled;
    }

    public long getMaxApiKeyExpiryAgeMs() {
        return maxApiKeyExpiryAgeMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExtendedUiConfig that = (ExtendedUiConfig) o;
        return externalIdentityProvider == that.externalIdentityProvider && Objects.equals(uiConfig,
                that.uiConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uiConfig, externalIdentityProvider);
    }

    @Override
    public String toString() {
        return "ExtendedUiConfig{" +
                "uiConfig=" + uiConfig +
                ", externalIdentityProvider=" + externalIdentityProvider +
                '}';
    }
}
