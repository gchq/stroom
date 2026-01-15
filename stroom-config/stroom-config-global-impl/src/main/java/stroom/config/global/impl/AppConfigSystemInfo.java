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

package stroom.config.global.impl;

import stroom.config.app.AppConfig;
import stroom.util.HealthCheckUtils;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;

import java.util.Map;

/**
 * Allows us to see the {@link AppConfig} tree on a node
 */
public class AppConfigSystemInfo implements HasSystemInfo {

    private final AppConfig appConfig;

    @Inject
    public AppConfigSystemInfo(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public SystemInfoResult getSystemInfo() {

        // TODO 19/11/2021 AT: Change this to walk the config tree and build a hash map
        final Map<String, Object> detailMap = HealthCheckUtils.beanToMap(appConfig);

        // We don't really want passwords appearing so mask them out.
        HealthCheckUtils.maskPasswords(detailMap);

        return new SystemInfoResult("Stroom config", null, detailMap);
    }
}
