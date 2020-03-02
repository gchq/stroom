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
    @JsonProperty
    private ProcessConfig process;
    @JsonProperty
    @JsonPropertyDescription("The URL of hosted help files.")
    private String helpUrl;
    @JsonProperty
    private ThemeConfig theme;
    @JsonProperty
    private QueryConfig query;
    @JsonProperty
    @JsonPropertyDescription("The regex pattern for entity names")
    @ValidRegex
    private String namePattern;
    @JsonProperty
    private String htmlTitle;
    @JsonProperty
    private String oncontextmenu;
    @JsonProperty
    private SplashConfig splash;
    @JsonProperty
    private ActivityConfig activity;
    @JsonProperty
    private UrlConfig url;

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
                    @JsonProperty("process") final ProcessConfig process,
                    @JsonProperty("helpUrl") final String helpUrl,
                    @JsonProperty("theme") final ThemeConfig theme,
                    @JsonProperty("query") final QueryConfig query,
                    @JsonProperty("namePattern") @ValidRegex final String namePattern,
                    @JsonProperty("htmlTitle") final String htmlTitle,
                    @JsonProperty("oncontextmenu") final String oncontextmenu,
                    @JsonProperty("splash") final SplashConfig splash,
                    @JsonProperty("activity") final ActivityConfig activity,
                    @JsonProperty("url") final UrlConfig url) {
        this.welcomeHtml = welcomeHtml;
        this.aboutHtml = aboutHtml;
        this.buildInfo = buildInfo;
        this.nodeName = nodeName;
        this.maintenanceMessage = maintenanceMessage;
        this.defaultMaxResults = defaultMaxResults;
        this.process = process;
        this.helpUrl = helpUrl;
        this.theme = theme;
        this.query = query;
        this.namePattern = namePattern;
        this.htmlTitle = htmlTitle;
        this.oncontextmenu = oncontextmenu;
        this.splash = splash;
        this.activity = activity;
        this.url = url;

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
        if (process == null) {
            process = new ProcessConfig();
        }
        if (theme == null) {
            theme = new ThemeConfig();
        }
        if (query == null) {
            query = new QueryConfig();
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
        if (splash == null) {
            splash = new SplashConfig();
        }
        if (activity == null) {
            activity = new ActivityConfig();
        }
        if (url == null) {
            url = new UrlConfig();
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

    public ProcessConfig getProcess() {
        return process;
    }

    public void setProcess(final ProcessConfig process) {
        this.process = process;
    }

    public String getHelpUrl() {
        return helpUrl;
    }

    public void setHelpUrl(final String helpUrl) {
        this.helpUrl = helpUrl;
    }

    public ThemeConfig getTheme() {
        return theme;
    }

    public void setTheme(final ThemeConfig theme) {
        this.theme = theme;
    }

    public QueryConfig getQuery() {
        return query;
    }

    public void setQuery(final QueryConfig query) {
        this.query = query;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(final String namePattern) {
        this.namePattern = namePattern;
    }

    public SplashConfig getSplash() {
        return splash;
    }

    public void setSplash(final SplashConfig splash) {
        this.splash = splash;
    }

    public ActivityConfig getActivity() {
        return activity;
    }

    public void setActivity(final ActivityConfig activity) {
        this.activity = activity;
    }

    public UrlConfig getUrl() {
        return url;
    }

    public void setUrl(final UrlConfig url) {
        this.url = url;
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
