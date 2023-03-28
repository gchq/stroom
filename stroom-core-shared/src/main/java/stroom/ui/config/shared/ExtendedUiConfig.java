package stroom.ui.config.shared;

import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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

    public String getWelcomeHtml() {
        return uiConfig.getWelcomeHtml();
    }

    public String getAboutHtml() {
        return uiConfig.getAboutHtml();
    }

    public String getMaintenanceMessage() {
        return uiConfig.getMaintenanceMessage();
    }

    public String getDefaultMaxResults() {
        return uiConfig.getDefaultMaxResults();
    }

    public ProcessConfig getProcess() {
        return uiConfig.getProcess();
    }

    public String getHelpUrl() {
        return uiConfig.getHelpUrl();
    }

    public String getHelpSubPathJobs() {
        return uiConfig.getHelpSubPathJobs();
    }

    public String getHelpSubPathQuickFilter() {
        return uiConfig.getHelpSubPathQuickFilter();
    }

    public String getHelpSubPathProperties() {
        return uiConfig.getHelpSubPathProperties();
    }

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

    public ThemeConfig getTheme() {
        return uiConfig.getTheme();
    }

    public QueryConfig getQuery() {
        return uiConfig.getQuery();
    }

    public String getNamePattern() {
        return uiConfig.getNamePattern();
    }

    public SplashConfig getSplash() {
        return uiConfig.getSplash();
    }

    public ActivityConfig getActivity() {
        return uiConfig.getActivity();
    }

    public String getHtmlTitle() {
        return uiConfig.getHtmlTitle();
    }

    public String getOncontextmenu() {
        return uiConfig.getOncontextmenu();
    }

    public SourceConfig getSource() {
        return uiConfig.getSource();
    }

    public Boolean getRequireReactWrapper() {
        return uiConfig.getRequireReactWrapper();
    }

    public int getApplicationInstanceKeepAliveIntervalMs() {
        return uiConfig.getApplicationInstanceKeepAliveIntervalMs();
    }

    public boolean isExternalIdentityProvider() {
        return externalIdentityProvider;
    }

    @Override
    public String toString() {
        return "ExtendedUiConfig{" +
                "externalIdentityProvider=" + externalIdentityProvider +
                '}';
    }
}
