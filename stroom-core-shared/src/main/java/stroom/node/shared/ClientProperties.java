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

package stroom.node.shared;

import stroom.util.shared.SharedObject;

import java.util.HashMap;
import java.util.Map;

public class ClientProperties implements SharedObject {
    public static final String LOGIN_HTML = "stroom.loginHTML";
    public static final String WELCOME_HTML = "stroom.welcomeHTML";
    public static final String ABOUT_HTML = "stroom.aboutHTML";
    public static final String BUILD_DATE = "buildDate";
    public static final String BUILD_VERSION = "buildVersion";
    public static final String NODE_NAME = "stroom.node";
    public static final String UP_DATE = "upDate";
    public static final String MAINTENANCE_MESSAGE = "stroom.maintenance.message";
    //TODO this one should be stroom.dashboard
    public static final String DEFAULT_MAX_RESULTS = "stroom.dashboard.defaultMaxResults";
    public static final String PROCESS_TIME_LIMIT = "stroom.search.process.defaultTimeLimit";
    public static final String PROCESS_RECORD_LIMIT = "stroom.search.process.defaultRecordLimit";
    public static final String STATISTIC_ENGINES = "stroom.statistics.common.statisticEngines";
    public static final String NAME_PATTERN = "stroom.namePattern";
    public static final String LABEL_COLOURS = "stroom.theme.labelColours";
    public static final String HELP_URL = "stroom.helpUrl";
    public static final String QUERY_INFO_POPUP_ENABLED = "stroom.query.infoPopup.enabled";
    public static final String QUERY_INFO_POPUP_TITLE = "stroom.query.infoPopup.title";
    public static final String QUERY_INFO_POPUP_VALIDATION_REGEX = "stroom.query.infoPopup.validationRegex";
    public static final String AUTHENTICATION_SERVICE_URL = "stroom.auth.authentication.service.url";
    public static final String USERS_UI_URL = "stroom.users.ui.url";
    public static final String API_KEYS_UI_URL = "stroom.apikeys.ui.url";
    public static final String CHANGE_PASSWORD_UI_URL = "stroom.changepassword.url";
    public static final String ADVERTISED_HOST_URL = "stroom.advertisedUrl";

    public static final String URL_KIBANA_UI = "stroom.url.kibana-ui";
    public static final String AUTH_SERVICE_URL = "stroom.auth.service.url";
    public static final String AUTH_UI_URL = "stroom.auth.ui.url";

    public static final String URL_LIST = "stroom.url.list";
    public static final String URL_BASE = "stroom.url.";

    public static final String EXTERNAL_DOC_REF_TYPES = "stroom.docref.types";
    public static final String URL_DOC_REF_UI_BASE = "stroom.docref.url.ui.";
    public static final String URL_DOC_REF_SERVICE_BASE = "stroom.docref.url.service.";

    private static final long serialVersionUID = 8717922468620533698L;
    private HashMap<String, String> map;

    public ClientProperties() {
        map = new HashMap<>();
    }

    public void put(final String key, final String value) {
        map.put(key, value);
    }

    public String get(final String key) {
        return map.get(key);
    }

    public String get(final String key, final String defaultValue) {
        final String value = map.get(key);
        if (value != null) {
            try {
                return value;
            } catch (final RuntimeException e) {
            }
        }

        return defaultValue;
    }

    public Boolean getBoolean(final String key, final boolean defaultValue) {
        final String value = map.get(key);
        if (value != null) {
            try {
                return Boolean.valueOf(value);
            } catch (final RuntimeException e) {
            }
        }

        return defaultValue;
    }

    public long getLong(final String key, final long defaultValue) {
        final String value = map.get(key);
        if (value != null) {
            try {
                return Long.valueOf(value);
            } catch (final RuntimeException e) {
            }
        }

        return defaultValue;
    }

    public Map<String, String> getLookupTable(final String listProp, final String base) {
        final Map<String, String> result = new HashMap<>();

        final String keyList = get(listProp);
        if (null != keyList) {
            final String[] keys = keyList.split(",");
            for (final String key : keys) {
                final String value = get(base + key);
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * This method exists to stop a developer or IDE from making the map final as GWT requires access to it
     */
    @SuppressWarnings("unused")
    private void setMap(final HashMap<String, String> map) {
        this.map = map;
    }
}
