/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dropwizard.common.prometheus;

import stroom.util.BuildInfoProvider;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAppInfoProvider implements AppInfoProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractAppInfoProvider.class);

    public static final Map<String, String> BASE_APP_INFO;

    static {
        // Static info about the app and jvm
        final Map<String, String> labels = new HashMap<>();
        final BuildInfo buildInfo = BuildInfoProvider.getBuildInfo();
        labels.put("build_version", buildInfo.getBuildVersion());
        labels.put("build_date", DateUtil.createNormalDateTimeString(buildInfo.getBuildTime()));
        labels.put("up_time", DateUtil.createNormalDateTimeString(buildInfo.getUpTime()));
        labels.put("java_version", System.getProperty("java.version"));
        labels.put("java_vendor", System.getProperty("java.vendor"));
        labels.put("java_vendor_version", System.getProperty("java.vendor.version"));
        BASE_APP_INFO = Collections.unmodifiableMap(labels);
    }

    @Override
    public Map<String, String> getAppInfo() {
        final Map<String, String> map = new HashMap<>(BASE_APP_INFO);

        try {
            final Map<String, String> additionalAppInfo = getAdditionalAppInfo();
            additionalAppInfo.forEach((key, val) -> {
                final String prevVal = map.put(key, val);
                if (prevVal != null) {
                    LOGGER.warn("Sub-class has over-written an entry with key: '{}'. Previous val: '{}', new val: '{}'",
                            key, prevVal, val);
                }
            });
        } catch (final Exception e) {
            LOGGER.error("Error fetching additionalAppInfo: {}", LogUtil.exceptionMessage(e), e);
        }
        return Collections.unmodifiableMap(map);
    }

    protected abstract Map<String, String> getAdditionalAppInfo();
}
