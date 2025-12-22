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

package stroom.proxy.app.jersey;

import stroom.dropwizard.common.AbstractJerseyClientFactory;
import stroom.proxy.app.Config;
import stroom.util.io.PathCreator;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.NullSafe;

import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@SuppressWarnings("unused")
@Singleton // Caches shared jersey clients
class ProxyJerseyClientFactoryImpl extends AbstractJerseyClientFactory {

    // This name is used by dropwizard metrics
    private static final String JERSEY_CLIENT_NAME_PREFIX = "stroom-proxy_jersey_client_";
    private static final String JERSEY_CLIENT_USER_AGENT_PREFIX = "stroom-proxy/";

    @SuppressWarnings("unused")
    @Inject
    public ProxyJerseyClientFactoryImpl(final Config config,
                                        final Provider<BuildInfo> buildInfoProvider,
                                        final Environment environment,
                                        final PathCreator pathCreator) {
        super(buildInfoProvider,
                environment,
                pathCreator,
                NullSafe.map(config.getJerseyClients()));
    }

    public String getJerseyClientNamePrefix() {
        return JERSEY_CLIENT_NAME_PREFIX;
    }

    public String getJerseyClientUserAgentPrefix() {
        return JERSEY_CLIENT_USER_AGENT_PREFIX;
    }
}
