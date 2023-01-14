package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyPathConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.repo.FileScannerConfig;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.io.PathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
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
        final ProxyDbConfig dbConfig = proxyConfigProvider.getConfigObject(ProxyDbConfig.class);

        Assertions.assertThat(pathConfig.getHome())
                .isNull();
        // null => non null
        final String newPath = "foo";

        final StroomDuration flushFrequency = dbConfig.getFlushFrequency();
        Assertions.assertThat(flushFrequency)
                .isNotNull();
        final StroomDuration newFlushFrequency = StroomDuration.of(
                flushFrequency.getDuration().plus(Duration.ofMinutes(5)));

        Assertions.assertThat(proxyConfig.getFileScanners())
                .isNotNull()
                .isEmpty();
        final FileScannerConfig fileScannerConfig = new FileScannerConfig(
                "somePath",
                StroomDuration.ofMinutes(5));
        final List<FileScannerConfig> newFileScanners = List.of(fileScannerConfig);

        final PropertyPath homePath = ProxyConfig.ROOT_PROPERTY_PATH
                .merge(ProxyConfig.PROP_NAME_PATH, PathConfig.PROP_NAME_HOME);
        final PropertyPath dbFlushPath = ProxyConfig.ROOT_PROPERTY_PATH
                .merge(ProxyConfig.PROP_NAME_DB, "flushFrequency");
        final PropertyPath fileScannersPath = ProxyConfig.ROOT_PROPERTY_PATH.merge(ProxyConfig.PROP_NAME_FILE_SCANNERS);

        // Create a new config tree with the new values
        final ProxyConfig mutatedConfig = AbstractConfigUtil.mutateTree(
                proxyConfig,
                ProxyConfig.ROOT_PROPERTY_PATH,
                Map.of(
                        homePath, newPath,
                        dbFlushPath, newFlushFrequency,
                        fileScannersPath, newFileScanners));

        Assertions.assertThat(mutatedConfig.getPathConfig().getHome())
                .isEqualTo(newPath);
        Assertions.assertThat(mutatedConfig.getProxyDbConfig().getFlushFrequency())
                .isEqualTo(newFlushFrequency);
        Assertions.assertThat(mutatedConfig.getFileScanners())
                .containsExactly(fileScannerConfig);

        // Now rebuild the config instances
        proxyConfigProvider.rebuildConfigInstances(mutatedConfig);

        // Make sure the provided config objects have the new values
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyPathConfig.class).getHome())
                .isEqualTo(newPath);
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyPathConfig.class))
                .isNotSameAs(pathConfig);
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyDbConfig.class).getFlushFrequency())
                .isEqualTo(newFlushFrequency);
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyDbConfig.class))
                .isNotSameAs(dbConfig);
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyConfig.class).getFileScanners())
                .containsExactly(fileScannerConfig);
        Assertions.assertThat(proxyConfigProvider.getConfigObject(ProxyConfig.class))
                .isNotSameAs(proxyConfig);
    }
}
