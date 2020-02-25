/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.validation.ValidRegex;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({"welcomeHtml", "aboutHtml", "buildInfo", "nodeName", "maintenanceMessage", "defaultMaxResults", "process", "helpUrl", "theme", "query", "namePattern", "htmlTitle", "oncontextmenu", "splash", "activity", "url"})
@JsonInclude(Include.NON_DEFAULT)
public class UiConfig extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("HTML")
    private String welcomeHtml;
    @JsonProperty
    @JsonPropertyDescription("HTML")
    private String aboutHtml;
    @JsonProperty
    private BuildInfo buildInfo;
    @JsonProperty
    private String nodeName;
    @JsonProperty
    @JsonPropertyDescription("Provide a warning message to users about an outage or other significant event.")
    private String maintenanceMessage;
    @JsonProperty
    @JsonPropertyDescription("The default maximum number of search results to return to the dashboard, unless the user requests lower values")
    private String defaultMaxResults;
    @JsonProperty("process")
    private ProcessConfig processConfig;
    @JsonProperty
    @JsonPropertyDescription("The URL of hosted help files.")
    private String helpUrl;
    @JsonProperty("theme")
    private ThemeConfig themeConfig;
    @JsonProperty("query")
    private QueryConfig queryConfig;
    @JsonProperty
    @JsonPropertyDescription("The regex pattern for entity names")
    @ValidRegex
    private String namePattern;
    @JsonProperty
    private String htmlTitle;
    @JsonProperty
    private String oncontextmenu;
    @JsonProperty("splash")
    private SplashConfig splashConfig;
    @JsonProperty("activity")
    private ActivityConfig activityConfig;
    @JsonProperty("url")
    private UrlConfig urlConfig;

    public UiConfig() {
        setDefaults();
    }

    @JsonCreator
    public UiConfig(@JsonProperty("welcomeHtml") final String welcomeHtml,
                    @JsonProperty("aboutHtml") final String aboutHtml,
                    @JsonProperty("buildInfo") final BuildInfo buildInfo,
                    @JsonProperty("nodeName") final String nodeName,
                    @JsonProperty("maintenanceMessage") final String maintenanceMessage,
                    @JsonProperty("defaultMaxResults") final String defaultMaxResults,
                    @JsonProperty("process") final ProcessConfig processConfig,
                    @JsonProperty("helpUrl") final String helpUrl,
                    @JsonProperty("theme") final ThemeConfig themeConfig,
                    @JsonProperty("query") final QueryConfig queryConfig,
                    @JsonProperty("namePattern") @ValidRegex final String namePattern,
                    @JsonProperty("htmlTitle") final String htmlTitle,
                    @JsonProperty("oncontextmenu") final String oncontextmenu,
                    @JsonProperty("splash") final SplashConfig splashConfig,
                    @JsonProperty("activity") final ActivityConfig activityConfig,
                    @JsonProperty("url") final UrlConfig urlConfig) {
        this.welcomeHtml = welcomeHtml;
        this.aboutHtml = aboutHtml;
        this.buildInfo = buildInfo;
        this.nodeName = nodeName;
        this.maintenanceMessage = maintenanceMessage;
        this.defaultMaxResults = defaultMaxResults;
        this.processConfig = processConfig;
        this.helpUrl = helpUrl;
        this.themeConfig = themeConfig;
        this.queryConfig = queryConfig;
        this.namePattern = namePattern;
        this.htmlTitle = htmlTitle;
        this.oncontextmenu = oncontextmenu;
        this.splashConfig = splashConfig;
        this.activityConfig = activityConfig;
        this.urlConfig = urlConfig;

        setDefaults();
    }

    private void setDefaults() {
        if (welcomeHtml == null) {
            welcomeHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
        }
        if (aboutHtml == null) {
            aboutHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
        }
        if (buildInfo == null) {
            buildInfo = new BuildInfo("TBD", "TBD", "TBD");
        }
        if (defaultMaxResults == null) {
            defaultMaxResults = "1000000,100,10,1";
        }
        if (processConfig == null) {
            processConfig = new ProcessConfig();
        }
        if (themeConfig == null) {
            themeConfig = new ThemeConfig();
        }
        if (queryConfig == null) {
            queryConfig = new QueryConfig();
        }
        if (namePattern == null) {
            namePattern = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
        }
        if (htmlTitle == null) {
            htmlTitle = "Stroom";
        }
        if (oncontextmenu == null) {
            oncontextmenu = "return false;";
        }
        if (splashConfig == null) {
            splashConfig = new SplashConfig();
        }
        if (activityConfig == null) {
            activityConfig = new ActivityConfig();
        }
        if (urlConfig == null) {
            urlConfig = new UrlConfig();
        }
    }

    public String getWelcomeHtml() {
        return welcomeHtml;
    }

    public void setWelcomeHtml(final String welcomeHtml) {
        this.welcomeHtml = welcomeHtml;
    }

    public String getAboutHtml() {
        return aboutHtml;
    }

    public void setAboutHtml(final String aboutHtml) {
        this.aboutHtml = aboutHtml;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void setBuildInfo(final BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public void setMaintenanceMessage(final String maintenanceMessage) {
        this.maintenanceMessage = maintenanceMessage;
    }

    public String getDefaultMaxResults() {
        return defaultMaxResults;
    }

    public void setDefaultMaxResults(final String defaultMaxResults) {
        this.defaultMaxResults = defaultMaxResults;
    }

    public ProcessConfig getProcessConfig() {
        return processConfig;
    }

    public void setProcessConfig(final ProcessConfig processConfig) {
        this.processConfig = processConfig;
    }

    public String getHelpUrl() {
        return helpUrl;
    }

    public void setHelpUrl(final String helpUrl) {
        this.helpUrl = helpUrl;
    }

    public ThemeConfig getThemeConfig() {
        return themeConfig;
    }

    public void setThemeConfig(final ThemeConfig themeConfig) {
        this.themeConfig = themeConfig;
    }

    public QueryConfig getQueryConfig() {
        return queryConfig;
    }

    public void setQueryConfig(final QueryConfig queryConfig) {
        this.queryConfig = queryConfig;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(final String namePattern) {
        this.namePattern = namePattern;
    }

    public SplashConfig getSplashConfig() {
        return splashConfig;
    }

    public void setSplashConfig(final SplashConfig splashConfig) {
        this.splashConfig = splashConfig;
    }

    public ActivityConfig getActivityConfig() {
        return activityConfig;
    }

    public void setActivityConfig(final ActivityConfig activityConfig) {
        this.activityConfig = activityConfig;
    }

    public UrlConfig getUrlConfig() {
        return urlConfig;
    }

    public void setUrlConfig(final UrlConfig urlConfig) {
        this.urlConfig = urlConfig;
    }

    public String getHtmlTitle() {
        return htmlTitle;
    }

    public void setHtmlTitle(final String htmlTitle) {
        this.htmlTitle = htmlTitle;
    }

    public String getOncontextmenu() {
        return oncontextmenu;
    }

    public void setOncontextmenu(final String oncontextmenu) {
        this.oncontextmenu = oncontextmenu;
    }

    @Override
    public String toString() {
        return "UiConfig{" +
                "welcomeHtml='" + welcomeHtml + '\'' +
                ", aboutHtml='" + aboutHtml + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", maintenanceMessage='" + maintenanceMessage + '\'' +
                ", defaultMaxResults='" + defaultMaxResults + '\'' +
                ", helpUrl='" + helpUrl + '\'' +
                ", namePattern='" + namePattern + '\'' +
                ", htmlTitle='" + htmlTitle + '\'' +
                ", oncontextmenu='" + oncontextmenu + '\'' +
                '}';
    }
}
