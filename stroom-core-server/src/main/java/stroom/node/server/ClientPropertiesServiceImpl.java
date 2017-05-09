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

package stroom.node.server;

import org.springframework.stereotype.Component;
import stroom.node.shared.ClientProperties;
import stroom.node.shared.ClientPropertiesService;
import stroom.util.BuildInfoUtil;
import stroom.util.config.StroomProperties;
import stroom.util.date.DateUtil;

@Component
public class ClientPropertiesServiceImpl implements ClientPropertiesService {
    private static final String upDate = DateUtil.createNormalDateTimeString();

    @Override
    public ClientProperties getProperties() {
        final ClientProperties props = new ClientProperties();
        addProperty(props, ClientProperties.LOGIN_HTML);
        addProperty(props, ClientProperties.WELCOME_HTML);
        addProperty(props, ClientProperties.ABOUT_HTML);
        props.put(ClientProperties.BUILD_DATE, BuildInfoUtil.getBuildDate());
        props.put(ClientProperties.BUILD_VERSION, BuildInfoUtil.getBuildVersion());
        addProperty(props, ClientProperties.NODE_NAME);
        addProperty(props, ClientProperties.MAINTENANCE_MESSAGE);
        props.put(ClientProperties.UP_DATE, upDate);
        addProperty(props, ClientProperties.MAX_RESULTS);
        addProperty(props, ClientProperties.PROCESS_TIME_LIMIT);
        addProperty(props, ClientProperties.PROCESS_RECORD_LIMIT);
        addProperty(props, ClientProperties.STATISTIC_ENGINES);
        addProperty(props, ClientProperties.LABEL_COLOURS);
        addProperty(props, ClientProperties.HELP_URL);

        return props;
    }

    private void addProperty(final ClientProperties props, final String key) {
        props.put(key, StroomProperties.getProperty(key));
    }
}
