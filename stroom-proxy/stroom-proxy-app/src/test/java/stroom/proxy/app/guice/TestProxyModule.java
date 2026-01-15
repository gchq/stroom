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

package stroom.proxy.app.guice;

import stroom.proxy.app.Config;
import stroom.proxy.app.ProxyConfig;
import stroom.test.common.util.guice.GuiceTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.Module;
import io.dropwizard.core.setup.Environment;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;

class TestProxyModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyModule.class);

    @Test
    void dumpGuiceModulesTree() {
        final Module proxyModule = getModule();
        final String dump = GuiceTestUtil.dumpGuiceModuleHierarchy(proxyModule);
        LOGGER.info("\n{}", dump);
    }

    @Test
    void dumpGuiceBindsSortedByKey() {
        final Module proxyModule = getModule();
        final String dump = GuiceTestUtil.dumpBindsSortedByKey(proxyModule);
        LOGGER.info("\n{}", dump);
    }

    @NotNull
    private Module getModule() {
        final Environment environmentMock = Mockito.mock(Environment.class);
        Mockito.when(environmentMock.healthChecks())
                .thenReturn(new HealthCheckRegistry());
        Mockito.when(environmentMock.metrics())
                .thenReturn(new MetricRegistry());

        final Config config = new Config();
        config.setProxyConfig(new ProxyConfig());

        return new ProxyModule(config, environmentMock, Path.of("DUMMY"));
    }
}
