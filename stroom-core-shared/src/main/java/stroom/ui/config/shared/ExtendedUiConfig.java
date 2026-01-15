/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.security.shared.HashAlgorithm;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.collection.GwtCollectionUtil;

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

    @JsonProperty
    @JsonPropertyDescription("The set of fields used in data receipt policy checking whose values are obfuscated " +
                             "when sent to a proxy.")
    private final Set<String> obfuscatedFields;

    @JsonProperty
    @JsonPropertyDescription(
            "The type of check performed on received data.")
    private final ReceiptCheckMode receiptCheckMode;

    @JsonProperty
    @JsonPropertyDescription(
            "The last time an annotation was updated.")
    private final long lastAnnotationChangeTime;

    public ExtendedUiConfig() {
        this.externalIdentityProvider = false;
        this.uiConfig = new UiConfig();
        this.dependencyWarningsEnabled = false;
        this.maxApiKeyExpiryAgeMs = 365L * 24 * 60 * 60 * 1_000;
        // This set of values comes from
        // stroom.receive.rules.impl.StroomReceiptPolicyConfig.DEFAULT_OBFUSCATED_FIELDS,
        // and it MUST be in alphabetic order.
        this.obfuscatedFields = GwtCollectionUtil.asUnmodifiabledConsistentOrderSet(
                "AccountId",
                "AccountName",
                "Component",
                "Feed",
                "ReceivedPath",
                "RemoteDN",
                "RemoteHost",
                "System",
                "UploadUsername",
                "UploadUserId",
                "X-Forwarded-For");
        this.receiptCheckMode = ReceiptCheckMode.getDefault();
        this.lastAnnotationChangeTime = 0;
    }

    @JsonCreator
    public ExtendedUiConfig(
            @JsonProperty("uiConfig") final UiConfig uiConfig,
            @JsonProperty("externalIdentityProvider") final boolean externalIdentityProvider,
            @JsonProperty("dependencyWarningsEnabled") final boolean dependencyWarningsEnabled,
            @JsonProperty("maxApiKeyExpiryAgeMs") final long maxApiKeyExpiryAgeMs,
            @JsonProperty("obfuscatedFields") final Set<String> obfuscatedFields,
            @JsonProperty("receiptCheckMode") final ReceiptCheckMode receiptCheckMode,
            @JsonProperty("lastAnnotationChangeTime") final long lastAnnotationChangeTime) {

        this.uiConfig = uiConfig;
        this.externalIdentityProvider = externalIdentityProvider;
        this.dependencyWarningsEnabled = dependencyWarningsEnabled;
        this.maxApiKeyExpiryAgeMs = maxApiKeyExpiryAgeMs;
        // Ensures serialisation tests work
        this.obfuscatedFields = GwtCollectionUtil.asUnmodifiabledConsistentOrderSet(obfuscatedFields);
        this.receiptCheckMode = NullSafe.requireNonNullElse(receiptCheckMode, ReceiptCheckMode.getDefault());
        this.lastAnnotationChangeTime = lastAnnotationChangeTime;
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
    public ReportUiDefaultConfig getReportUiDefaultConfig() {
        return uiConfig.getReportUiDefaultConfig();
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
    public int getMaxEditorCompletionEntries() {
        return uiConfig.getMaxEditorCompletionEntries();
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

    public Set<String> getObfuscatedFields() {
        return obfuscatedFields;
    }

    public ReceiptCheckMode getReceiptCheckMode() {
        return receiptCheckMode;
    }

    public long getLastAnnotationChangeTime() {
        return lastAnnotationChangeTime;
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
        return externalIdentityProvider == that.externalIdentityProvider
               && dependencyWarningsEnabled == that.dependencyWarningsEnabled
               && maxApiKeyExpiryAgeMs == that.maxApiKeyExpiryAgeMs
               && Objects.equals(uiConfig, that.uiConfig)
               && Objects.equals(obfuscatedFields, that.obfuscatedFields)
               && Objects.equals(receiptCheckMode, that.receiptCheckMode)
               && Objects.equals(lastAnnotationChangeTime, that.lastAnnotationChangeTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uiConfig,
                externalIdentityProvider,
                dependencyWarningsEnabled,
                maxApiKeyExpiryAgeMs,
                obfuscatedFields,
                receiptCheckMode,
                lastAnnotationChangeTime);
    }

    @Override
    public String toString() {
        return "ExtendedUiConfig{" +
               "uiConfig=" + uiConfig +
               ", externalIdentityProvider=" + externalIdentityProvider +
               ", dependencyWarningsEnabled=" + dependencyWarningsEnabled +
               ", maxApiKeyExpiryAgeMs=" + maxApiKeyExpiryAgeMs +
               ", obfuscatedFields=" + obfuscatedFields +
               ", receiptCheckMode=" + receiptCheckMode +
               ", lastAnnotationChangeTime=" + lastAnnotationChangeTime +
               '}';
    }
}
