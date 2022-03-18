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

import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BuildInfo;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Properties;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class BuildInfoProvider implements Provider<BuildInfo> {

    private static final long upDate = System.currentTimeMillis();
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildInfoProvider.class);
    private static final String BUILD_PROPERTIES = "META-INF/stroom-util-build.properties";

    private static final BuildInfo BUILD_INFO;

    static {
        final Properties properties = new Properties();
        try {
            properties.load(
                    BuildInfoProvider.class.getClassLoader().getResourceAsStream(BUILD_PROPERTIES));
        } catch (final IOException e) {
            LOGGER.error("Unable to load {}", BUILD_PROPERTIES, e);
        }
        final String buildVersion = properties.getProperty("buildVersion");
        final String buildDate = properties.getProperty("buildDate");

        if (buildVersion == null || buildVersion.isBlank()) {
            throw new RuntimeException("Build version is null/blank. It should be set in " + BUILD_PROPERTIES);
        }
        if (buildDate == null || buildDate.isBlank()) {
            throw new RuntimeException("Build date is null/blank. It should be set in " + BUILD_PROPERTIES);
        }

        long buildTime = 0;
        try {
            buildTime = ZonedDateTime.parse(buildDate).toInstant().toEpochMilli();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            try {
                buildTime = DateUtil.parseNormalDateTimeString(buildDate);
            } catch (final RuntimeException e2) {
                LOGGER.debug(e2.getMessage(), e2);
            }
        }
        BUILD_INFO = new BuildInfo(upDate, buildVersion, buildTime);
    }

    // For use by Guice
    @Override
    public BuildInfo get() {
        return BUILD_INFO;
    }

    // Allows us to get it statically when not using guice
    public static BuildInfo getBuildInfo() {
        return BUILD_INFO;
    }
}
