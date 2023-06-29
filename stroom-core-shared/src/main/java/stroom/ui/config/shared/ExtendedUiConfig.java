package stroom.ui.config.shared;

import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

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

    public ExtendedUiConfig() {
        this.externalIdentityProvider = false;
        this.uiConfig = new UiConfig();
    }

    @JsonCreator
    public ExtendedUiConfig(
            @JsonProperty("uiConfig") final UiConfig uiConfig,
            @JsonProperty("externalIdentityProvider") final boolean externalIdentityProvider) {

        this.uiConfig = uiConfig;
        this.externalIdentityProvider = externalIdentityProvider;
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
    public Boolean getRequireReactWrapper() {
        return uiConfig.getRequireReactWrapper();
    }

    @JsonIgnore
    public boolean isExternalIdentityProvider() {
        return externalIdentityProvider;
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
