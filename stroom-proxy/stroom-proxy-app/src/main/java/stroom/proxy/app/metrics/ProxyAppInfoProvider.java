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

package stroom.proxy.app.metrics;

import stroom.dropwizard.common.prometheus.AbstractAppInfoProvider;
import stroom.proxy.app.handler.ProxyId;

import jakarta.inject.Inject;

import java.util.Map;

public class ProxyAppInfoProvider extends AbstractAppInfoProvider {

    public static final String PROXY_ID_KEY = "proxy_id";
    private final ProxyId proxyId;

    @Inject
    public ProxyAppInfoProvider(final ProxyId proxyId) {
        this.proxyId = proxyId;
    }

    @Override
    protected Map<String, String> getAdditionalAppInfo() {
        return Map.of(PROXY_ID_KEY, proxyId.getId());
    }

    @Override
    public Map<String, String> getNodeLabels() {
        return Map.of(PROXY_ID_KEY, proxyId.getId());
    }
}
