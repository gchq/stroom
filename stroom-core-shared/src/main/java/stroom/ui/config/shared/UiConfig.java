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

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.security.shared.HashAlgorithm;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.AllMatchPattern;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class UiConfig extends AbstractConfig implements IsStroomConfig {

    private static final String DEFAULT_USER_GUIDE_BASE_SUB_PATH = "/user-guide";

    @JsonProperty
    @JsonPropertyDescription("The welcome message that is displayed in the welcome tab when logging in to Stroom. " +
                             "The welcome message is in HTML format.")
    private final String welcomeHtml;

    @JsonProperty
    @JsonPropertyDescription("The about message that is displayed when selecting Help -> About. " +
                             "The about message is in HTML format.")
    private final String aboutHtml;

    @JsonProperty
    @JsonPropertyDescription("Provide a warning message to users about an outage or other significant event.")
    private final String maintenanceMessage;

    @JsonProperty
    @JsonPropertyDescription("Provide a generic message to the users about an authentication error, " +
                             "e.g. 'Contact support on 01234 567890.'. This message will be displayed in addition to " +
                             "the actual authentication error message. The message is in HTML format. Can be null.")
    private final String authErrorMessage;

    @JsonProperty
    @JsonPropertyDescription("The default maximum number of search results that new dashboard tables will request.")
    private final String defaultMaxResults;

    @JsonProperty
    private final ProcessConfig process;

    @JsonProperty
    @JsonPropertyDescription("The URL of hosted help files.")
    private final String helpUrl;

    @JsonProperty
    @JsonPropertyDescription("The sub-path for the help page for stroom jobs. Appended to helpUrl.")
    private final String helpSubPathJobs;

    @JsonProperty
    @JsonPropertyDescription("The sub-path for the help page for the quick filter. Appended to helpUrl.")
    private final String helpSubPathQuickFilter;

    @JsonProperty
    @JsonPropertyDescription("The sub-path for the help page for the properties. Appended to helpUrl.")
    private final String helpSubPathProperties;

    @JsonProperty
    @JsonPropertyDescription("The sub-path for the help page for the dashboard expressions. Appended to helpUrl.")
    private final String helpSubPathExpressions;

    @JsonProperty
    @JsonPropertyDescription("The sub-path for the help page for documentation. Appended to helpUrl.")
    private final String helpSubPathDocumentation;

    @JsonProperty
    @JsonPropertyDescription("The sub-path for the help page for Stroom Query Language. Appended to helpUrl.")
    private final String helpSubPathStroomQueryLanguage;

    @JsonProperty
    private final ThemeConfig theme;

    @JsonProperty
    private final QueryConfig query;

    @ValidRegex
    @JsonProperty
    @JsonPropertyDescription("The regex pattern for entity names.")
    private final String namePattern;

    @JsonProperty
    @JsonPropertyDescription("The title to use for the application in the browser.")
    private final String htmlTitle;

    @Pattern(regexp = "^return (true|false);$")
    @JsonProperty
    @JsonPropertyDescription("Determines the behaviour of the browser built-in context menu. This property is " +
                             "for developer use only. Set to 'return false;' to see Stroom's context menu. Set to " +
                             "'return true;' to see the standard browser menu.")
    private final String oncontextmenu;

    @JsonProperty
    private final SplashConfig splash;

    @JsonProperty
    private final ActivityConfig activity;

    @JsonProperty
    private final SourceConfig source;

    @JsonProperty
    private final NodeMonitoringConfig nodeMonitoring;

    @JsonProperty
    private final AnalyticUiDefaultConfig analyticUiDefaultConfig;

    @JsonProperty
    private final ReportUiDefaultConfig reportUiDefaultConfig;

    @JsonProperty
    @JsonPropertyDescription("This regex pattern defines the delimiter to use for nesting index fields in the query " +
                             "helper. This is useful when dealing with large numbers of dynamic fields. e.g. if " +
                             "every element is made into a field with its name being something similar to its xpath. " +
                             "For example, if the delimiter is '.', then the field " +
                             "'Events.Event.EventTime.TimeCreated' would be displayed as 'TimeCreated' within three " +
                             "nested categories. The default pattern is '[:.]' to also categorise the special " +
                             "'annotation:XXX' fields. Set it to null or an empty string prevent nesting.")
    private final String nestedIndexFieldsDelimiterPattern;

    @AllMatchPattern(pattern = ExplorerNode.TAG_PATTERN_STR)
    @JsonProperty
    @JsonPropertyDescription("Set of explorer tags to use as a filter on the Reference Pipeline selector of " +
                             "an XSLTFilter within a pipeline." +
                             "Explorer nodes will only be included if they have at least all the tags in this " +
                             "property. " +
                             "This property should contain a sub set of the tags in property " +
                             "stroom.explorer.suggestedTags")
    private final Set<String> referencePipelineSelectorIncludedTags;

    @JsonProperty
    @JsonPropertyDescription("The default hash algorithm for hashing API keys. API keys are not stored, only their " +
                             "hash and prefix are. Different hash algorithm offer different levels of performance " +
                             "and security. " +
                             "If not set 'SHA3_256' will be used. Possible values are 'SHA3_256', 'SHA2_256', " +
                             "'BCRYPT' and 'ARGON2'. " +
                             "This property controls the default value of a selection box, but the user select a " +
                             "different one.")
    private final HashAlgorithm defaultApiKeyHashAlgorithm;

    @JsonProperty
    @JsonPropertyDescription("The maximum number of code completion entries to show in the popup when using " +
                             "ctrl-space or live autocompletion.")
    private final int maxEditorCompletionEntries;

    public UiConfig() {
        welcomeHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
        aboutHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
        maintenanceMessage = null;
        authErrorMessage = null;
        defaultMaxResults = "1000000,100,10,1";
        process = new ProcessConfig();
        helpUrl = "https://gchq.github.io/stroom-docs/7.5/docs";
        helpSubPathJobs = "/reference-section/jobs/";
        helpSubPathQuickFilter = DEFAULT_USER_GUIDE_BASE_SUB_PATH + "/finding-things/";
        helpSubPathProperties = DEFAULT_USER_GUIDE_BASE_SUB_PATH + "/properties/";
        helpSubPathExpressions = DEFAULT_USER_GUIDE_BASE_SUB_PATH + "/dashboards/expressions/";
        helpSubPathDocumentation = DEFAULT_USER_GUIDE_BASE_SUB_PATH + "/content/documentation/";
        helpSubPathStroomQueryLanguage = DEFAULT_USER_GUIDE_BASE_SUB_PATH + "/dashboards/stroom-query-language/";
        theme = new ThemeConfig();
        query = new QueryConfig();
        namePattern = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
        htmlTitle = "Stroom";
        oncontextmenu = "return false;";
        splash = new SplashConfig();
        activity = new ActivityConfig();
        source = new SourceConfig();
        nodeMonitoring = new NodeMonitoringConfig();
        analyticUiDefaultConfig = new AnalyticUiDefaultConfig();
        reportUiDefaultConfig = new ReportUiDefaultConfig();
        nestedIndexFieldsDelimiterPattern = "[.:]"; // : is to split the special annotation:XXX fields
        referencePipelineSelectorIncludedTags = StandardExplorerTags.asTagNameSet(
                StandardExplorerTags.REFERENCE_LOADER);
        defaultApiKeyHashAlgorithm = HashAlgorithm.SHA3_256;
        maxEditorCompletionEntries = 1_000;
    }

    @JsonCreator
    @SuppressWarnings("checkstyle:LineLength")
    public UiConfig(@JsonProperty("welcomeHtml") final String welcomeHtml,
                    @JsonProperty("aboutHtml") final String aboutHtml,
                    @JsonProperty("maintenanceMessage") final String maintenanceMessage,
                    @JsonProperty("authErrorMessage") final String authErrorMessage,
                    @JsonProperty("defaultMaxResults") final String defaultMaxResults,
                    @JsonProperty("process") final ProcessConfig process,
                    @JsonProperty("helpUrl") final String helpUrl,
                    @JsonProperty("helpSubPathJobs") final String helpSubPathJobs,
                    @JsonProperty("helpSubPathQuickFilter") final String helpSubPathQuickFilter,
                    @JsonProperty("helpSubPathProperties") final String helpSubPathProperties,
                    @JsonProperty("helpSubPathExpressions") final String helpSubPathExpressions,
                    @JsonProperty("helpSubPathDocumentation") final String helpSubPathDocumentation,
                    @JsonProperty("helpSubPathStroomQueryLanguage") final String helpSubPathStroomQueryLanguage,
                    @JsonProperty("theme") final ThemeConfig theme,
                    @JsonProperty("query") final QueryConfig query,
                    @JsonProperty("namePattern") final String namePattern,
                    @JsonProperty("htmlTitle") final String htmlTitle,
                    @JsonProperty("oncontextmenu") final String oncontextmenu,
                    @JsonProperty("splash") final SplashConfig splash,
                    @JsonProperty("activity") final ActivityConfig activity,
                    @JsonProperty("source") final SourceConfig source,
                    @JsonProperty("nodeMonitoring") final NodeMonitoringConfig nodeMonitoring,
                    @JsonProperty("analyticUiDefaultConfig") final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                    @JsonProperty("reportUiDefaultConfig") final ReportUiDefaultConfig reportUiDefaultConfig,
                    @JsonProperty("nestedIndexFieldsDelimiterPattern") final String nestedIndexFieldsDelimiterPattern,
                    @JsonProperty("referencePipelineSelectorIncludedTags") final Set<String> referencePipelineSelectorIncludedTags,
                    @JsonProperty("defaultApiKeyHashAlgorithm") final HashAlgorithm defaultApiKeyHashAlgorithm,
                    @JsonProperty("maxEditorCompletionEntries") final int maxEditorCompletionEntries) {
        this.welcomeHtml = welcomeHtml;
        this.aboutHtml = aboutHtml;
        this.maintenanceMessage = maintenanceMessage;
        this.authErrorMessage = authErrorMessage;
        this.defaultMaxResults = defaultMaxResults;
        this.process = process;
        this.helpUrl = helpUrl;
        this.helpSubPathJobs = helpSubPathJobs;
        this.helpSubPathQuickFilter = helpSubPathQuickFilter;
        this.helpSubPathProperties = helpSubPathProperties;
        this.helpSubPathExpressions = helpSubPathExpressions;
        this.helpSubPathDocumentation = helpSubPathDocumentation;
        this.helpSubPathStroomQueryLanguage = helpSubPathStroomQueryLanguage;
        this.theme = theme;
        this.query = query;
        this.namePattern = namePattern;
        this.htmlTitle = htmlTitle;
        this.oncontextmenu = oncontextmenu;
        this.splash = splash;
        this.activity = activity;
        this.source = source;
        this.nodeMonitoring = nodeMonitoring;
        this.analyticUiDefaultConfig = analyticUiDefaultConfig;
        this.reportUiDefaultConfig = reportUiDefaultConfig;
        this.nestedIndexFieldsDelimiterPattern = nestedIndexFieldsDelimiterPattern;
        this.referencePipelineSelectorIncludedTags = referencePipelineSelectorIncludedTags;
        this.defaultApiKeyHashAlgorithm = defaultApiKeyHashAlgorithm;
        this.maxEditorCompletionEntries = maxEditorCompletionEntries;
    }

    public String getWelcomeHtml() {
        return welcomeHtml;
    }

    public String getAboutHtml() {
        return aboutHtml;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public String getAuthErrorMessage() {
        return authErrorMessage;
    }

    public String getDefaultMaxResults() {
        return defaultMaxResults;
    }

    public ProcessConfig getProcess() {
        return process;
    }

    public String getHelpUrl() {
        return helpUrl;
    }

    public String getHelpSubPathJobs() {
        return helpSubPathJobs;
    }

    public String getHelpSubPathQuickFilter() {
        return helpSubPathQuickFilter;
    }

    public String getHelpSubPathProperties() {
        return helpSubPathProperties;
    }

    public String getHelpSubPathExpressions() {
        return helpSubPathExpressions;
    }

    public String getHelpSubPathDocumentation() {
        return helpSubPathDocumentation;
    }

    public String getHelpSubPathStroomQueryLanguage() {
        return helpSubPathStroomQueryLanguage;
    }

    private String appendHelpPath(final String subPath) {
        if (helpUrl == null) {
            // No point appending a path to a null url
            return null;
        } else {
            return helpUrl
                   + (subPath != null
                    ? subPath
                    : "");
        }
    }

    /**
     * @return The URL for the Jobs page in the help site.
     */
    @JsonIgnore
    public String getHelpUrlJobs() {
        return appendHelpPath(helpSubPathJobs);
    }

    /**
     * @return The URL for the quick filter page in the help site.
     */
    @JsonIgnore
    public String getHelpUrlQuickFilter() {
        return appendHelpPath(helpSubPathQuickFilter);
    }

    /**
     * @return The URL for the properties page in the help site.
     */
    @JsonIgnore
    public String getHelpUrlProperties() {
        return appendHelpPath(helpSubPathProperties);
    }

    /**
     * @return The URL for the dashboard expressions page in the help site.
     */
    @JsonIgnore
    public String getHelpUrlExpressions() {
        return appendHelpPath(helpSubPathExpressions);
    }

    /**
     * @return The URL for the documentation page in the help site.
     */
    @JsonIgnore
    public String getHelpUrlDocumentation() {
        return appendHelpPath(helpSubPathDocumentation);
    }

    /**
     * @return The URL for the Stroom Query Language page in the help site.
     */
    @JsonIgnore
    public String getHelpUrlStroomQueryLanguage() {
        return appendHelpPath(helpSubPathStroomQueryLanguage);
    }

    public ThemeConfig getTheme() {
        return theme;
    }

    public QueryConfig getQuery() {
        return query;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public SplashConfig getSplash() {
        return splash;
    }

    public ActivityConfig getActivity() {
        return activity;
    }

    public String getHtmlTitle() {
        return htmlTitle;
    }

    public String getOncontextmenu() {
        return oncontextmenu;
    }

    public SourceConfig getSource() {
        return source;
    }

    public NodeMonitoringConfig getNodeMonitoring() {
        return nodeMonitoring;
    }

    public AnalyticUiDefaultConfig getAnalyticUiDefaultConfig() {
        return analyticUiDefaultConfig;
    }

    public ReportUiDefaultConfig getReportUiDefaultConfig() {
        return reportUiDefaultConfig;
    }

    public String getNestedIndexFieldsDelimiterPattern() {
        return nestedIndexFieldsDelimiterPattern;
    }

    public Set<String> getReferencePipelineSelectorIncludedTags() {
        return referencePipelineSelectorIncludedTags;
    }

    public HashAlgorithm getDefaultApiKeyHashAlgorithm() {
        return defaultApiKeyHashAlgorithm;
    }

    public int getMaxEditorCompletionEntries() {
        return maxEditorCompletionEntries;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UiConfig uiConfig = (UiConfig) o;

        return Objects.equals(welcomeHtml, uiConfig.welcomeHtml)
               && Objects.equals(aboutHtml, uiConfig.aboutHtml)
               && Objects.equals(maintenanceMessage, uiConfig.maintenanceMessage)
               && Objects.equals(defaultMaxResults, uiConfig.defaultMaxResults)
               && Objects.equals(process, uiConfig.process)
               && Objects.equals(helpUrl, uiConfig.helpUrl)
               && Objects.equals(helpSubPathJobs, uiConfig.helpSubPathJobs)
               && Objects.equals(helpSubPathQuickFilter, uiConfig.helpSubPathQuickFilter)
               && Objects.equals(helpSubPathProperties, uiConfig.helpSubPathProperties)
               && Objects.equals(helpSubPathExpressions, uiConfig.helpSubPathExpressions)
               && Objects.equals(helpSubPathDocumentation, uiConfig.helpSubPathDocumentation)
               && Objects.equals(theme, uiConfig.theme)
               && Objects.equals(query, uiConfig.query)
               && Objects.equals(namePattern, uiConfig.namePattern)
               && Objects.equals(htmlTitle, uiConfig.htmlTitle)
               && Objects.equals(oncontextmenu, uiConfig.oncontextmenu)
               && Objects.equals(splash, uiConfig.splash)
               && Objects.equals(activity, uiConfig.activity)
               && Objects.equals(source, uiConfig.source)
               && Objects.equals(analyticUiDefaultConfig, uiConfig.analyticUiDefaultConfig)
               && Objects.equals(reportUiDefaultConfig, uiConfig.reportUiDefaultConfig)
               && Objects.equals(nodeMonitoring, uiConfig.nodeMonitoring)
               && Objects.equals(nestedIndexFieldsDelimiterPattern, uiConfig.nestedIndexFieldsDelimiterPattern)
               && Objects.equals(referencePipelineSelectorIncludedTags, uiConfig.referencePipelineSelectorIncludedTags)
               && Objects.equals(defaultApiKeyHashAlgorithm, uiConfig.defaultApiKeyHashAlgorithm)
               && Objects.equals(maxEditorCompletionEntries, uiConfig.maxEditorCompletionEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(welcomeHtml,
                aboutHtml,
                maintenanceMessage,
                defaultMaxResults,
                process,
                helpUrl,
                helpSubPathJobs,
                helpSubPathQuickFilter,
                helpSubPathProperties,
                helpSubPathExpressions,
                helpSubPathDocumentation,
                theme,
                query,
                namePattern,
                htmlTitle,
                oncontextmenu,
                splash,
                activity,
                source,
                nodeMonitoring,
                analyticUiDefaultConfig,
                reportUiDefaultConfig,
                nestedIndexFieldsDelimiterPattern,
                referencePipelineSelectorIncludedTags,
                defaultApiKeyHashAlgorithm,
                maxEditorCompletionEntries);
    }

    @Override
    public String toString() {
        return "UiConfig{" +
               "welcomeHtml='" + welcomeHtml + '\'' +
               ", aboutHtml='" + aboutHtml + '\'' +
               ", maintenanceMessage='" + maintenanceMessage + '\'' +
               ", defaultMaxResults='" + defaultMaxResults + '\'' +
               ", process=" + process +
               ", helpUrl='" + helpUrl + '\'' +
               ", helpSubPathJobs='" + helpSubPathJobs + '\'' +
               ", helpSubPathQuickFilter='" + helpSubPathQuickFilter + '\'' +
               ", helpSubPathProperties='" + helpSubPathProperties + '\'' +
               ", helpSubPathExpressions='" + helpSubPathExpressions + '\'' +
               ", helpSubPathDocumentation='" + helpSubPathDocumentation + '\'' +
               ", theme=" + theme +
               ", query=" + query +
               ", namePattern='" + namePattern + '\'' +
               ", htmlTitle='" + htmlTitle + '\'' +
               ", oncontextmenu='" + oncontextmenu + '\'' +
               ", splash=" + splash +
               ", activity=" + activity +
               ", source=" + source +
               ", nodeMonitoring=" + nodeMonitoring +
               ", analyticUiDefaultConfig=" + analyticUiDefaultConfig +
               ", reportUiDefaultConfig=" + reportUiDefaultConfig +
               ", nestedIndexFieldsDelimiterPattern=" + nestedIndexFieldsDelimiterPattern +
               ", referencePipelineSelectorIncludedTags=" + referencePipelineSelectorIncludedTags +
               ", defaultApiKeyHashAlgorithm=" + defaultApiKeyHashAlgorithm +
               ", maxEditorCompletionEntries=" + maxEditorCompletionEntries +
               '}';
    }
}
