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
import stroom.util.logging.StroomLogger;

import java.util.Properties;

public class BuildInfoUtil {
    private static StroomLogger LOGGER = StroomLogger.getLogger(BuildInfoUtil.class);

    public static final String upDate = DateUtil.createNormalDateTimeString();
    private static final String buildVersion;
    private static final String buildDate;

    static {
        Properties properties = new Properties();
        try {
            properties.load(
                    BuildInfoUtil.class.getClassLoader().getResourceAsStream("META-INF/stroom-util-build.properties"));
        } catch (Exception e) {
            LOGGER.error(e);
        }
        buildVersion = properties.getProperty("buildVersion");
        buildDate = properties.getProperty("buildDate");
    }

    public static String getBuildVersion() {
        return buildVersion;
    }

    public static String getBuildDate() {
        return buildDate;
    }

    public static String getUpDate() {
        return upDate;
    }
}
