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

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyPathConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.io.PathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PropertyPath;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class TestProxyConfigProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyConfigProvider.class);

    @Test
    void testMutateTree_1() {
        final ProxyConfig proxyConfig = new ProxyConfig();
        Assertions.assertThat(proxyConfig.getFeedStatusConfig().getFeedStatusCache().getMaximumSize())
                .isNotNull();
        final long maxSize = proxyConfig.getFeedStatusConfig().getFeedStatusCache().getMaximumSize();

        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(proxyConfig);

        final FeedStatusConfig feedStatusConfig = proxyConfigProvider.getConfigObject(FeedStatusConfig.class);

        Assertions.assertThat(feedStatusConfig.getFeedStatusCache().getMaximumSize())
                .isEqualTo(maxSize);

        final long newMaxSize = maxSize + 10;

        final PropertyPath path = ProxyConfig.ROOT_PROPERTY_PATH.merge(
                "feedStatus",
                "feedStatusCache",
                CacheConfig.PROP_NAME_MAXIMUM_SIZE);

        // Create a new config tree with the new values
        final ProxyConfig mutatedConfig = AbstractConfigUtil.mutateTree(
                proxyConfig,
                ProxyConfig.ROOT_PROPERTY_PATH,
                Map.of(path, newMaxSize));

        Assertions.assertThat(mutatedConfig.getFeedStatusConfig().getFeedStatusCache().getMaximumSize())
                .isEqualTo(newMaxSize);

        // Now rebuild the config instances
        proxyConfigProvider.rebuildConfigInstances(mutatedConfig);

        // Make sure the provided config obj has the new value
        Assertions.assertThat(proxyConfigProvider.getConfigObject(FeedStatusConfig.class)
                        .getFeedStatusCache()
                        .getMaximumSize())
                .isEqualTo(newMaxSize);
    }

    @Test
    void testMutateTree_2() {
        final ProxyConfig proxyConfig = new ProxyConfig();

        final ProxyConfigProvider proxyConfigProvider = new ProxyConfigProvider(proxyConfig);
        final PathConfig pathConfig = proxyConfigProvider.getConfigObject(ProxyPathConfig.class);

        Assertions.assertThat(pathConfig.getHome())
                .isNull();
        // null => non null
        final String newPath = "foo";

        final PropertyPath homePath = ProxyConfig.ROOT_PROPERTY_PATH
                .merge(ProxyConfig.PROP_NAME_PATH, PathConfig.PROP_NAME_HOME);

        // Create a new config tree with the new values
        final ProxyConfig mutatedConfig = AbstractConfigUtil.mutateTree(
                proxyConfig,
                ProxyConfig.ROOT_PROPERTY_PATH,
                Map.of(
                        homePath, newPath));

        Assertions.assertThat(mutatedConfig.getPathConfig().getHome())
                .isEqualTo(newPath);

        // Now rebuild the config instances
        proxyConfigProvider.rebuildConfigInstances(mutatedConfig);

        // Make sure the provided config objects have the new values
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyPathConfig.class).getHome())
                .isEqualTo(newPath);
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyPathConfig.class))
                .isNotSameAs(pathConfig);
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyConfig.class))
                .isNotSameAs(proxyConfig);
    }
}
