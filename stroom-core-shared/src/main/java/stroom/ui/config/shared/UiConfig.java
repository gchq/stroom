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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.docref.SharedObject;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;

import javax.inject.Singleton;

@Singleton
public class UiConfig extends AbstractConfig implements SharedObject {
    private String welcomeHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
    private String aboutHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
    private BuildInfo buildInfo = new BuildInfo();
    private String nodeName;
    private String maintenanceMessage;
    private String defaultMaxResults = "1000000,100,10,1";
    private ProcessConfig processConfig = new ProcessConfig();
    private String helpUrl;
    private ThemeConfig themeConfig = new ThemeConfig();
    private QueryConfig queryConfig = new QueryConfig();
    private String namePattern = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    private String htmlTitle = "Stroom";
    private String oncontextmenu = "return false;";
    private SplashConfig splashConfig = new SplashConfig();
    private ActivityConfig activityConfig = new ActivityConfig();
    private UrlConfig urlConfig = new UrlConfig();

    private static final long serialVersionUID = 8717922468620533698L;

    public UiConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    @JsonPropertyDescription("HTML")
    public String getWelcomeHtml() {
        return welcomeHtml;
    }

    public void setWelcomeHtml(final String welcomeHtml) {
        this.welcomeHtml = welcomeHtml;
    }

    @JsonPropertyDescription("HTML")
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

    @JsonPropertyDescription("Provide a warning message to users about an outage or other significant event.")
    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public void setMaintenanceMessage(final String maintenanceMessage) {
        this.maintenanceMessage = maintenanceMessage;
    }

    @JsonPropertyDescription("The default maximum number of search results to return to the dashboard, unless the user requests lower values")
    public String getDefaultMaxResults() {
        return defaultMaxResults;
    }

    public void setDefaultMaxResults(final String defaultMaxResults) {
        this.defaultMaxResults = defaultMaxResults;
    }

    @JsonProperty("process")
    public ProcessConfig getProcessConfig() {
        return processConfig;
    }

    public void setProcessConfig(final ProcessConfig processConfig) {
        this.processConfig = processConfig;
    }

    @JsonPropertyDescription("The URL of hosted help files.")
    public String getHelpUrl() {
        return helpUrl;
    }

    public void setHelpUrl(final String helpUrl) {
        this.helpUrl = helpUrl;
    }

    @JsonProperty("theme")
    public ThemeConfig getThemeConfig() {
        return themeConfig;
    }

    public void setThemeConfig(final ThemeConfig themeConfig) {
        this.themeConfig = themeConfig;
    }

    @JsonProperty("query")
    public QueryConfig getQueryConfig() {
        return queryConfig;
    }

    public void setQueryConfig(final QueryConfig queryConfig) {
        this.queryConfig = queryConfig;
    }

    @ValidRegex
    @JsonPropertyDescription("The regex pattern for entity names")
    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(final String namePattern) {
        this.namePattern = namePattern;
    }

    @JsonProperty("splash")
    public SplashConfig getSplashConfig() {
        return splashConfig;
    }

    public void setSplashConfig(final SplashConfig splashConfig) {
        this.splashConfig = splashConfig;
    }

    @JsonProperty("activity")
    public ActivityConfig getActivityConfig() {
        return activityConfig;
    }

    public void setActivityConfig(final ActivityConfig activityConfig) {
        this.activityConfig = activityConfig;
    }

    @JsonProperty("url")
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

    //
//    @JsonPropertyDescription("The URL of Stroom as provided to the browser")
//    public String getAdvertisedUrl() {
//        return advertisedUrl;
//    }
//
//    public void setAdvertisedUrl(final String advertisedUrl) {
//        this.advertisedUrl = advertisedUrl;
//    }
//
//    @JsonPropertyDescription("The URL of the authentication service")
//    public String getAuthenticationServiceUrl() {
//        return authenticationServiceUrl;
//    }
//
//    public void setAuthenticationServiceUrl(final String authenticationServiceUrl) {
//        this.authenticationServiceUrl = authenticationServiceUrl;
//    }
//
//    public void put(final String key, final String value) {
//        map.put(key, value);
//    }
//
//    public String get(final String key) {
//        return map.get(key);
//    }
//
//    public String get(final String key, final String defaultValue) {
//        final String value = map.get(key);
//        if (value != null) {
//            try {
//                return value;
//            } catch (final RuntimeException e) {
//            }
//        }
//
//        return defaultValue;
//    }
//
//    public Boolean getBoolean(final String key, final boolean defaultValue) {
//        final String value = map.get(key);
//        if (value != null) {
//            try {
//                return Boolean.valueOf(value);
//            } catch (final RuntimeException e) {
//            }
//        }
//
//        return defaultValue;
//    }
//
//    public long getLong(final String key, final long defaultValue) {
//        final String value = map.get(key);
//        if (value != null) {
//            try {
//                return Long.valueOf(value);
//            } catch (final RuntimeException e) {
//            }
//        }
//
//        return defaultValue;
//    }
//
//    public Map<String, String> getLookupTable(final String listProp, final String base) {
//        final Map<String, String> result = new HashMap<>();
//
//        final String keyList = get(listProp);
//        if (null != keyList) {
//            final String[] keys = keyList.split(",");
//            for (final String key : keys) {
//                final String value = get(base + key);
//                result.put(key, value);
//            }
//        }
//
//        return result;
//    }
//
//    /**
//     * This method exists to stop a developer or IDE from making the map final as GWT requires access to it
//     */
//    @SuppressWarnings("unused")
//    private void setMap(final HashMap<String, String> map) {
//        this.map = map;
//    }
}
