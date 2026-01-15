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

package stroom.proxy.app;

import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;

import com.codahale.metrics.health.HealthCheck.Result;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

// TODO should not be a healthcheck, should be HasSystemInfo if we can get it to
//   work on the admin port as there is no auth in proxy
public class ProxyConfigHealthCheck implements HasHealthCheck {

    private final Provider<ProxyConfig> proxyConfigProvider;

    @Inject
    ProxyConfigHealthCheck(final Provider<ProxyConfig> proxyConfigProvider) {
        this.proxyConfigProvider = proxyConfigProvider;
    }

    @Override
    public Result getHealth() {
        final Map<String, Object> detailMap = HealthCheckUtils.beanToMap(proxyConfigProvider.get());

        // We don't really want passwords appearing on the admin page so mask them out.
        HealthCheckUtils.maskPasswords(detailMap);

        return Result.builder()
                .healthy()
                .withDetail("values", detailMap)
                .build();
    }
}
