/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.rest;

import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// The replies to the requests that Stroom makes when it starts and that many screens repeat:
/// the session info, the (extended) UI config, the user's preferences, their app permissions and
/// document permission checks. They are the equivalent of the React stories' `appApiFixture`
/// (`fetchSessionInfo`, `fetchUiConfig`, `fetchUserPreferences`, `fetchEffectiveAppPermissions`).
///
/// The [stroom.gwt.workbench.client.app.screen.ScreenHarness] adds them after the story's own
/// fixtures, so a story only overrides what it cares about, either with its own route (the first
/// matching route wins) or through the harness builder, e.g.
/// ```
/// ScreenHarness.builder(context, FIXTURES)
///         .user(ALICE)
///         .appPermissions(AppPermission.MANAGE_USERS_PERMISSION)
///         .uiConfig("{\"welcomeHtml\": \"<h1>Hello</h1>\"}")
///         .build();
/// ```
///
/// The defaults are trimmed from the gwt-suite corpus: the `admin` user with the `ADMINISTRATOR`
/// permission on node `node1a`, a UI config with the usual `welcomeHtml`/`aboutHtml`/help URLs,
/// the default preferences in UTC, and Stroom's document types (`GET /explorer/v2/fetchDocumentTypes`,
/// which Stroom caches app-wide).
public final class StartupFixtures {

    /// The path of `SessionInfoResource.get()`.
    public static final String SESSION_INFO_PATH = "/sessionInfo/v1";
    /// The path of `GlobalConfigResource.fetchExtendedUiConfig()`.
    public static final String EXTENDED_UI_CONFIG_PATH = "/config/v1/noauth/fetchExtendedUiConfig";
    /// The path of `UserPreferencesResource.fetch()`.
    public static final String USER_PREFERENCES_PATH = "/preferences/v1";
    /// The path of `AppPermissionResource.getEffectiveAppPermissions()`.
    public static final String APP_PERMISSIONS_PATH = "/permission/app/v1";
    /// The path of `ExplorerResource.fetchDocumentTypes()`, which Stroom's explorer, document
    /// permission screens and type pickers fetch.
    public static final String DOCUMENT_TYPES_PATH = "/explorer/v2/fetchDocumentTypes";
    /// The path of `DocPermissionResource.checkDocumentPermission()`.
    public static final String CHECK_DOCUMENT_PERMISSION_PATH = "/permission/doc/v1/checkDocumentPermission";

    /// The default user, `admin`.
    public static final UserRef ADMIN = new UserRef(
            "admin-uuid", "admin", "admin", "Administrator", false, true);

    /// The default UI config (`ExtendedUiConfig.uiConfig`).
    public static final String DEFAULT_UI_CONFIG = """
            {
              "aboutHtml": "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>",
              "welcomeHtml": "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>",
              "activity": {"chooseOnStartup": false, "enabled": false, "editorTitle": "Edit Activity",
                           "managerTitle": "Choose Activity"},
              "defaultApiKeyHashAlgorithm": "SHA3_256",
              "defaultMaxResults": "1000000,100,10,1",
              "helpUrl": "https://gchq.github.io/stroom-docs/7.13/docs",
              "helpSubPathDocumentation": "/user-guide/content/documentation/",
              "helpSubPathExpressions": "/reference-section/expressions/",
              "helpSubPathJobs": "/reference-section/jobs/",
              "helpSubPathProperties": "/user-guide/properties/",
              "helpSubPathQuickFilter": "/user-guide/content/finding-things/",
              "helpSubPathStroomQueryLanguage": "/user-guide/search/queries/stroom-query-language/",
              "htmlTitle": "Stroom",
              "maxEditorCompletionEntries": 1000,
              "namePattern": "^[a-zA-Z0-9_\\\\- \\\\.\\\\(\\\\)]{1,}$",
              "nestedIndexFieldsDelimiterPattern": "[.:]",
              "nodeMonitoring": {"pingMaxThreshold": 500, "pingWarnThreshold": 100},
              "process": {"defaultRecordLimit": 1000000, "defaultTimeLimit": 30},
              "query": {
                "dashboardPipelineSelectorIncludedTags": ["extraction"],
                "indexPipelineSelectorIncludedTags": ["extraction"],
                "infoPopup": {"enabled": false, "title": "Please Provide Query Info",
                              "validationRegex": "^[\\\\s\\\\S]{3,}$"},
                "viewPipelineSelectorIncludedTags": ["extraction"]
              },
              "referencePipelineSelectorIncludedTags": ["reference-loader"],
              "source": {"maxCharactersInPreviewFetch": 30000, "maxCharactersPerFetch": 80000,
                         "maxCharactersToCompleteLine": 10000, "maxHexDumpLines": 1000},
              "splash": {"enabled": false, "title": "Splash Screen", "version": "v0.1"},
              "theme": {}
            }
            """;

    /// The default user preferences.
    public static final String DEFAULT_USER_PREFERENCES = """
            {
              "dateTimePattern": "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
              "density": "Default",
              "editorKeyBindings": "STANDARD",
              "editorLiveAutoCompletion": "OFF",
              "editorTheme": "chrome",
              "enableTransparency": true,
              "font": "Roboto",
              "fontSize": "Medium",
              "theme": "Light",
              "timeZone": {"use": "UTC"}
            }
            """;

    // The document types of the gwt-suite corpus (group, type, display type, icon), in its order
    private static final String[][] DOCUMENT_TYPES = {
            {"STRUCTURE", "Folder", "Folder", "FOLDER"},
            {"DATA_PROCESSING", "Feed", "Feed", "DOCUMENT_FEED"},
            {"DATA_PROCESSING", "Pipeline", "Pipeline", "DOCUMENT_PIPELINE"},
            {"TRANSFORMATION", "XMLSchema", "XML Schema", "DOCUMENT_XMLSCHEMA"},
            {"TRANSFORMATION", "XSLT", "XSL Translation", "DOCUMENT_XSLT"},
            {"TRANSFORMATION", "TextConverter", "Text Converter", "DOCUMENT_TEXT_CONVERTER"},
            {"SEARCH", "Query", "Query", "DOCUMENT_QUERY"},
            {"SEARCH", "Dashboard", "Dashboard", "DOCUMENT_DASHBOARD"},
            {"SEARCH", "AnalyticRule", "Analytic Rule", "DOCUMENT_ANALYTIC_RULE"},
            {"SEARCH", "DataGen", "Data Generator", "DOCUMENT_DATA_GEN"},
            {"SEARCH", "Report", "Report", "DOCUMENT_REPORT"},
            {"INDEXING", "PlanB", "Plan B", "DOCUMENT_PLAN_B"},
            {"INDEXING", "SolrIndex", "Solr Index", "DOCUMENT_SOLR_INDEX"},
            {"INDEXING", "Index", "Lucene Index", "DOCUMENT_INDEX"},
            {"INDEXING", "ElasticIndex", "Elastic Index", "DOCUMENT_ELASTIC_INDEX"},
            {"INDEXING", "StatisticStore", "Statistic Store", "DOCUMENT_STATISTIC_STORE"},
            {"INDEXING", "Pathways", "Pathways", "DOCUMENT_PATHWAYS"},
            {"INDEXING", "View", "View", "DOCUMENT_VIEW"},
            {"CONFIGURATION", "Dictionary", "Dictionary", "DOCUMENT_DICTIONARY"},
            {"CONFIGURATION", "GitRepo", "Git Repo", "DOCUMENT_GIT_REPO_FOLDER"},
            {"CONFIGURATION", "OpenAIModel", "OpenAI Model", "DOCUMENT_OPEN_AI"},
            {"CONFIGURATION", "S3Config", "S3 Configuration", "DOCUMENT_S3"},
            {"CONFIGURATION", "Script", "Script", "DOCUMENT_SCRIPT"},
            {"CONFIGURATION", "Documentation", "Documentation", "DOCUMENT_DOCUMENTATION"},
            {"CONFIGURATION", "Visualisation", "Visualisation", "DOCUMENT_VISUALISATION"},
            {"CONFIGURATION", "KafkaConfig", "Kafka Configuration", "DOCUMENT_KAFKA_CONFIG"},
            {"CONFIGURATION", "ElasticCluster", "Elastic Cluster", "DOCUMENT_ELASTIC_CLUSTER"}
    };

    /// The default document types, as `ExplorerResource.fetchDocumentTypes()` returns them (all
    /// visible), recorded in the gwt-suite corpus.
    public static final String DEFAULT_DOCUMENT_TYPES = documentTypesJson();

    private StartupFixtures() {
        // Static utility
    }

    /// @return The default start-up fixtures.
    public static RestFixtures defaults() {
        return builder().build();
    }

    /// @return A builder of start-up fixtures, starting from the defaults.
    public static Builder builder() {
        return new Builder();
    }

    private static String documentTypesJson() {
        final StringBuilder types = new StringBuilder("[");
        for (final String[] type : DOCUMENT_TYPES) {
            if (types.length() > 1) {
                types.append(',');
            }
            types.append("{\"group\":").append(RestReply.quote(type[0]))
                    .append(",\"type\":").append(RestReply.quote(type[1]))
                    .append(",\"displayType\":").append(RestReply.quote(type[2]))
                    .append(",\"icon\":").append(RestReply.quote(type[3]))
                    .append('}');
        }
        types.append(']');
        return "{\"types\":" + types + ",\"visibleTypes\":" + types + "}";
    }

    /// @param userRef A user.
    /// @return The user as JSON, as Stroom sends it.
    public static String userRefJson(final UserRef userRef) {
        return "{\"uuid\":" + RestReply.quote(userRef.getUuid())
                + ",\"subjectId\":" + RestReply.quote(userRef.getSubjectId())
                + ",\"displayName\":" + RestReply.quote(userRef.getDisplayName())
                + ",\"fullName\":" + RestReply.quote(userRef.getFullName())
                + ",\"group\":" + userRef.isGroup()
                + ",\"enabled\":" + userRef.isEnabled() + "}";
    }

    // --------------------------------------------------------------------------------


    /// Builds start-up fixtures. Each part not set has its default.
    public static final class Builder {

        private UserRef user = ADMIN;
        private String nodeName = "node1a";
        private String buildVersion = "SNAPSHOT";
        private List<AppPermission> appPermissions = Collections.singletonList(AppPermission.ADMINISTRATOR);
        private String uiConfig = DEFAULT_UI_CONFIG;
        private String extendedUiConfig;
        private String userPreferences = DEFAULT_USER_PREFERENCES;
        private String sessionInfo;
        private boolean documentPermission = true;

        private Builder() {
        }

        /// @param user The current user, in the session info and app permissions.
        /// @return This builder.
        public Builder user(final UserRef user) {
            this.user = Objects.requireNonNull(user);
            return this;
        }

        /// @param nodeName The node name in the session info.
        /// @return This builder.
        public Builder nodeName(final String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        /// @param buildVersion The build version in the session info.
        /// @return This builder.
        public Builder buildVersion(final String buildVersion) {
            this.buildVersion = buildVersion;
            return this;
        }

        /// @param permissions The current user's app permissions. None for an ordinary user;
        ///                    `ADMINISTRATOR` implies all of them, as in Stroom.
        /// @return This builder.
        public Builder appPermissions(final AppPermission... permissions) {
            this.appPermissions = new ArrayList<>(Arrays.asList(permissions));
            return this;
        }

        /// @param uiConfigJson The `uiConfig` part of the extended UI config, replacing the default.
        /// @return This builder.
        public Builder uiConfig(final String uiConfigJson) {
            this.uiConfig = Objects.requireNonNull(uiConfigJson);
            return this;
        }

        /// @param json The whole extended UI config, replacing the default and [#uiConfig(String)].
        /// @return This builder.
        public Builder extendedUiConfig(final String json) {
            this.extendedUiConfig = json;
            return this;
        }

        /// @param json The user preferences, replacing the default.
        /// @return This builder.
        public Builder userPreferences(final String json) {
            this.userPreferences = Objects.requireNonNull(json);
            return this;
        }

        /// @param json The session info, replacing the one made from the user, node name and
        ///             build version.
        /// @return This builder.
        public Builder sessionInfo(final String json) {
            this.sessionInfo = json;
            return this;
        }

        /// @param allowed The reply to every document permission check made with Stroom's
        ///                `CurrentUser` (the harness's security context answers without asking).
        /// @return This builder.
        public Builder documentPermission(final boolean allowed) {
            this.documentPermission = allowed;
            return this;
        }

        /// @return The current user.
        public UserRef getUser() {
            return user;
        }

        /// @return The current user's app permissions.
        public List<AppPermission> getAppPermissions() {
            return Collections.unmodifiableList(new ArrayList<>(appPermissions));
        }

        /// @return The fixtures.
        public RestFixtures build() {
            return RestFixtures.builder()
                    .get(SESSION_INFO_PATH, RestReply.json(buildSessionInfo()))
                    .get(EXTENDED_UI_CONFIG_PATH, RestReply.json(buildExtendedUiConfig()))
                    .get(USER_PREFERENCES_PATH, RestReply.json(userPreferences))
                    .get(APP_PERMISSIONS_PATH, RestReply.json(buildAppPermissions()))
                    .post(CHECK_DOCUMENT_PERMISSION_PATH, RestReply.json(String.valueOf(documentPermission)))
                    .get(DOCUMENT_TYPES_PATH, RestReply.json(DEFAULT_DOCUMENT_TYPES))
                    .build();
        }

        private String buildSessionInfo() {
            if (sessionInfo != null) {
                return sessionInfo;
            }
            return "{\"userRef\":" + userRefJson(user)
                    + ",\"nodeName\":" + RestReply.quote(nodeName)
                    + ",\"buildInfo\":{\"upTime\":1710000000000,\"buildVersion\":" + RestReply.quote(buildVersion)
                    + ",\"buildTime\":1700000000000}}";
        }

        private String buildExtendedUiConfig() {
            if (extendedUiConfig != null) {
                return extendedUiConfig;
            }
            return "{\"uiConfig\":" + uiConfig
                    + ",\"externalIdentityProvider\":false,\"dependencyWarningsEnabled\":false"
                    + ",\"maxApiKeyExpiryAgeMs\":31536000000,\"obfuscatedFields\":[]"
                    + ",\"receiptCheckMode\":\"FEED_STATUS\",\"lastAnnotationChangeTime\":0}";
        }

        private String buildAppPermissions() {
            final StringBuilder sb = new StringBuilder("{\"userRef\":").append(userRefJson(user))
                    .append(",\"permissions\":[");
            for (int i = 0; i < appPermissions.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(RestReply.quote(appPermissions.get(i).name()));
            }
            return sb.append("],\"inherited\":[]}").toString();
        }
    }
}
