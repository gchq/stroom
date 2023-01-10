package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Allows us to make other back-end config available to the UI while having one mechanism
 * to cache the config in the ui.
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ExtendedUiConfig extends UiConfig {

    @JsonProperty
    @JsonPropertyDescription("Whether authentication is provided by an external Open ID Connect identity " +
            "provider or not")
    private final boolean externalIdentityProvider;

    public ExtendedUiConfig() {
        super();
        externalIdentityProvider = false;
    }

    public ExtendedUiConfig(final UiConfig uiConfig,
                            final boolean externalIdentityProvider) {
        super(uiConfig.getWelcomeHtml(),
                uiConfig.getAboutHtml(),
                uiConfig.getMaintenanceMessage(),
                uiConfig.getDefaultMaxResults(),
                uiConfig.getProcess(),
                uiConfig.getHelpUrl(),
                uiConfig.getHelpSubPathJobs(),
                uiConfig.getHelpSubPathQuickFilter(),
                uiConfig.getHelpSubPathProperties(),
                uiConfig.getHelpSubPathExpressions(),
                uiConfig.getTheme(),
                uiConfig.getQuery(),
                uiConfig.getNamePattern(),
                uiConfig.getHtmlTitle(),
                uiConfig.getOncontextmenu(),
                uiConfig.getSplash(),
                uiConfig.getActivity(),
                uiConfig.getSource(),
                uiConfig.getRequireReactWrapper(),
                uiConfig.getApplicationInstanceKeepAliveIntervalMs());
        this.externalIdentityProvider = externalIdentityProvider;
    }

    @JsonCreator
    public ExtendedUiConfig(@JsonProperty("welcomeHtml") final String welcomeHtml,
                            @JsonProperty("aboutHtml") final String aboutHtml,
                            @JsonProperty("maintenanceMessage") final String maintenanceMessage,
                            @JsonProperty("defaultMaxResults") final String defaultMaxResults,
                            @JsonProperty("process") final ProcessConfig process,
                            @JsonProperty("helpUrl") final String helpUrl,
                            @JsonProperty("helpSubPathJobs") final String helpSubPathJobs,
                            @JsonProperty("helpSubPathQuickFilter") final String helpSubPathQuickFilter,
                            @JsonProperty("helpSubPathProperties") final String helpSubPathProperties,
                            @JsonProperty("helpSubPathExpressions") final String helpSubPathExpressions,
                            @JsonProperty("theme") final ThemeConfig theme,
                            @JsonProperty("query") final QueryConfig query,
                            @JsonProperty("namePattern") final String namePattern,
                            @JsonProperty("htmlTitle") final String htmlTitle,
                            @JsonProperty("oncontextmenu") final String oncontextmenu,
                            @JsonProperty("splash") final SplashConfig splash,
                            @JsonProperty("activity") final ActivityConfig activity,
                            @JsonProperty("source") final SourceConfig source,
                            @JsonProperty("requireReactWrapper") Boolean requireReactWrapper,
                            @JsonProperty("applicationInstanceKeepAliveIntervalMs") final int applicationInstanceKeepAliveIntervalMs,
                            @JsonProperty("externalIdentityProvider") final boolean externalIdentityProvider) {
        super(welcomeHtml,
                aboutHtml,
                maintenanceMessage,
                defaultMaxResults,
                process,
                helpUrl,
                helpSubPathJobs,
                helpSubPathQuickFilter,
                helpSubPathProperties,
                helpSubPathExpressions,
                theme,
                query,
                namePattern,
                htmlTitle,
                oncontextmenu,
                splash,
                activity,
                source,
                requireReactWrapper,
                applicationInstanceKeepAliveIntervalMs);
        this.externalIdentityProvider = externalIdentityProvider;
    }

    @JsonProperty
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
