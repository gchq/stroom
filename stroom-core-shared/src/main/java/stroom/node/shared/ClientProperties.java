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

public class ClientProperties implements SharedObject {
    private static final long serialVersionUID = 8717922468620533698L;

    public static final String LOGIN_HTML = "stroom.loginHTML";
    public static final String WELCOME_HTML = "stroom.welcomeHTML";
    public static final String ABOUT_HTML = "stroom.aboutHTML";
    public static final String BUILD_DATE = "buildDate";
    public static final String BUILD_VERSION = "buildVersion";
    public static final String NODE_NAME = "stroom.node";
    public static final String UP_DATE = "upDate";

    public static final String MAINTENANCE_MESSAGE = "stroom.maintenance.message";
    public static final String MAX_RESULTS = "stroom.search.maxResults";
    public static final String PROCESS_TIME_LIMIT = "stroom.search.process.defaultTimeLimit";
    public static final String PROCESS_RECORD_LIMIT = "stroom.search.process.defaultRecordLimit";
    public static final String STATISTIC_ENGINES = "stroom.statistics.common.statisticEngines";
    public static final String NAME_PATTERN = "stroom.namePattern";
    public static final String LABEL_COLOURS = "stroom.theme.labelColours";
    public static final String HELP_URL = "stroom.helpUrl";
    public static final String QUERY_INFO_POPUP_ENABLED = "stroom.query.infoPopup.enabled";
    public static final String QUERY_INFO_POPUP_TITLE = "stroom.query.infoPopup.title";
    public static final String QUERY_INFO_POPUP_VALIDATION_REGEX = "stroom.query.infoPopup.validationRegex";
    public static final String ACTIVITY_ENABLED = "stroom.activity.enabled";
    public static final String ACTIVITY_CHOOSE_ON_STARTUP = "stroom.activity.chooseOnStartup";
    public static final String ACTIVITY_MANAGER_TITLE = "stroom.activity.managerTitle";
    public static final String ACTIVITY_EDITOR_TITLE = "stroom.activity.editorTitle";
    public static final String ACTIVITY_EDITOR_BODY = "stroom.activity.editorBody";

    public static final String SPLASH_ENABLED = "stroom.splash.enabled";
    public static final String SPLASH_TITLE = "stroom.splash.title";
    public static final String SPLASH_BODY = "stroom.splash.body";

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
            } catch (final Exception e) {
            }
        }

        return defaultValue;
    }

    public Boolean getBoolean(final String key, final boolean defaultValue) {
        final String value = map.get(key);
        if (value != null) {
            try {
                return Boolean.valueOf(value);
            } catch (final Exception e) {
            }
        }

        return defaultValue;
    }

    public long getLong(final String key, final long defaultValue) {
        final String value = map.get(key);
        if (value != null) {
            try {
                return Long.valueOf(value);
            } catch (final Exception e) {
            }
        }

        return defaultValue;
    }

    /**
     * This method exists to stop a developer or IDE from making the map final as GWT requires access to it
     */
    @SuppressWarnings("unused")
    private void setMap(final HashMap<String, String> map) {
        this.map = map;
    }
}
