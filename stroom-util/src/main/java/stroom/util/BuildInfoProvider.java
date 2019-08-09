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

package stroom.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.date.DateUtil;
import stroom.util.shared.BuildInfo;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Properties;

@Singleton
class BuildInfoProvider implements Provider<BuildInfo> {
    private static final String upDate = DateUtil.createNormalDateTimeString();
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfoProvider.class);

    private BuildInfo buildInfo;

    public BuildInfo get() {
        if (buildInfo == null) {
            Properties properties = new Properties();
            try {
                properties.load(
                        BuildInfoProvider.class.getClassLoader().getResourceAsStream("META-INF/stroom-util-build.properties"));
            } catch (final IOException e) {
                LOGGER.error("Unable to load stroom-util-build.properties!", e);
            }
            final String buildVersion = properties.getProperty("buildVersion");
            final String buildDate = properties.getProperty("buildDate");
            buildInfo = new BuildInfo(upDate, buildVersion, buildDate);
        }
        return buildInfo;
    }
}
