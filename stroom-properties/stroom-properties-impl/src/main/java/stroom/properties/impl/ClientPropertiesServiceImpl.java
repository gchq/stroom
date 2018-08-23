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

package stroom.properties.impl;

import stroom.properties.shared.ClientProperties;
import stroom.properties.shared.ClientPropertiesService;
import stroom.util.config.StroomProperties;

import javax.inject.Singleton;

@Singleton
class ClientPropertiesServiceImpl implements ClientPropertiesService {
    @Override
    public ClientProperties getProperties() {
        final ClientProperties props = new ClientProperties();
        addProperty(props, ClientProperties.LOGIN_HTML);
        addProperty(props, ClientProperties.WELCOME_HTML);
        addProperty(props, ClientProperties.ABOUT_HTML);
        addProperty(props, ClientProperties.BUILD_DATE);
        addProperty(props, ClientProperties.BUILD_VERSION);
        addProperty(props, ClientProperties.NODE_NAME);
        addProperty(props, ClientProperties.MAINTENANCE_MESSAGE);
        addProperty(props, ClientProperties.UP_DATE);
        addProperty(props, ClientProperties.DEFAULT_MAX_RESULTS);
        addProperty(props, ClientProperties.PROCESS_TIME_LIMIT);
        addProperty(props, ClientProperties.PROCESS_RECORD_LIMIT);
        addProperty(props, ClientProperties.STATISTIC_ENGINES);
        addProperty(props, ClientProperties.LABEL_COLOURS);
        addProperty(props, ClientProperties.HELP_URL);
        addProperty(props, ClientProperties.QUERY_INFO_POPUP_ENABLED);
        addProperty(props, ClientProperties.QUERY_INFO_POPUP_TITLE);
        addProperty(props, ClientProperties.QUERY_INFO_POPUP_VALIDATION_REGEX);
        addProperty(props, ClientProperties.AUTHENTICATION_SERVICE_URL);
        addProperty(props, ClientProperties.ADVERTISED_HOST_URL);
        addProperty(props, ClientProperties.USERS_UI_URL);
        addProperty(props, ClientProperties.API_KEYS_UI_URL);
        addProperty(props, ClientProperties.STROOM_UI_URL);
        addProperty(props, ClientProperties.CHANGE_PASSWORD_UI_URL);
        addProperty(props, ClientProperties.URL_KIBANA_UI);

        final String urlList = StroomProperties.getProperty(ClientProperties.URL_LIST);
        props.put(ClientProperties.URL_LIST, urlList);
        if (null != urlList) {
            final String[] namedUrls = urlList.split(",");
            for (final String namedUrl : namedUrls) {
                addProperty(props, ClientProperties.URL_BASE + namedUrl);
            }
        }

        final String externalTypesList = StroomProperties.getProperty(ClientProperties.EXTERNAL_DOC_REF_TYPES);
        props.put(ClientProperties.EXTERNAL_DOC_REF_TYPES, externalTypesList);
        if (null != externalTypesList) {
            final String[] externalTypes = externalTypesList.split(",");
            for (final String externalType : externalTypes) {
                addProperty(props, ClientProperties.URL_DOC_REF_UI_BASE + externalType);
            }
        }

        return props;
    }

    private void addProperty(final ClientProperties props, final String key) {
        props.put(key, StroomProperties.getProperty(key));
    }
}
