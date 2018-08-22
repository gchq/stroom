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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UiConfig implements SharedObject {
    private String welcomeHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
    private String aboutHtml = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
    private String buildDate = "TBD";
    private String buildVersion = "TBD";
    private String nodeName;
    private String upDate;
    private String maintenanceMessage;
    private String defaultMaxResults = "1000000,100,10,1";
    private ProcessConfig processConfig;
    private String helpUrl;
    private ThemeConfig themeConfig;
    private QueryConfig queryConfig;
    private String namePattern = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    private String htmlTitle = "Stroom";
    private String oncontextmenu = "return false;";

    private UrlConfig urlConfig;

//    //TODO this one should be stroom.dashboard
//    public static final String USERS_UI_URL = "stroom.users.ui.url";
//    public static final String API_KEYS_UI_URL = "stroom.apikeys.ui.url";
//    public static final String CHANGE_PASSWORD_UI_URL = "stroom.changepassword.url";
//
//    public static final String URL_KIBANA_UI = "stroom.url.kibana-ui";
//    public static final String STROOM_UI_URL = "stroom.ui.url";
//
//    public static final String URL_LIST = "stroom.url.list";
//    public static final String URL_BASE = "stroom.url.";
//
//    public static final String EXTERNAL_DOC_REF_TYPES = "stroom.docref.types";
//    public static final String URL_DOC_REF_UI_BASE = "stroom.docref.url.ui.";
//    public static final String URL_DOC_REF_SERVICE_BASE = "stroom.docref.url.service.";

    private static final long serialVersionUID = 8717922468620533698L;
//    private HashMap<String, String> map;

    public UiConfig() {
//        map = new HashMap<>();
        processConfig = new ProcessConfig();
        themeConfig = new ThemeConfig();
        queryConfig = new QueryConfig();
        urlConfig = new UrlConfig();
    }

    @Inject
    public UiConfig(final ProcessConfig processConfig,
                    final ThemeConfig themeConfig,
                    final QueryConfig queryConfig,
                    final UrlConfig urlConfig) {
        this.processConfig = processConfig;
        this.themeConfig = themeConfig;
        this.queryConfig = queryConfig;
        this.urlConfig = urlConfig;
    }

    //@JsonPropertyDescription("HTML")
//    public String getLoginHtml() {
//        return loginHtml;
//    }
//
//    public void setLoginHtml(final String loginHtml) {
//        this.loginHtml = loginHtml;
//    }

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

    public String getBuildDate() {
        return buildDate;
    }

    public void setBuildDate(final String buildDate) {
        this.buildDate = buildDate;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(final String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public String getUpDate() {
        return upDate;
    }

    public void setUpDate(final String upDate) {
        this.upDate = upDate;
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

    @JsonPropertyDescription("The regex pattern for entity names")
    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(final String namePattern) {
        this.namePattern = namePattern;
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
