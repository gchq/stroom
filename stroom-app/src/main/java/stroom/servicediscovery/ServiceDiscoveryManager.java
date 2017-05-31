package stroom.servicediscovery;

import com.google.common.base.Preconditions;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.spring.StroomShutdown;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
@Singleton
public class ServiceDiscoveryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryManager.class);

    public static final String PROP_KEY_ZOOKEEPER_QUORUM = "stroom.serviceDiscovery.zookeeperUrl";
    public static final String PROP_KEY_CURATOR_BASE_SLEEP_TIME_MS = "stroom.serviceDiscovery.curator.baseSleepTimeMs";
    public static final String PROP_KEY_CURATOR_MAX_SLEEP_TIME_MS = "stroom.serviceDiscovery.curator.maxSleepTimeMs";
    public static final String PROP_KEY_CURATOR_MAX_RETRIES = "stroom.serviceDiscovery.curator.maxRetries";

    private final StroomPropertyService stroomPropertyService;
    private final String zookeeperUrl;

    private final AtomicReference<CuratorFramework> curatorFrameworkRef = new AtomicReference<>();
    private final List<Consumer<CuratorFramework>> curatorStartupListeners = new ArrayList<>();

    @SuppressWarnings("unused")
    public ServiceDiscoveryManager(final StroomPropertyService stroomPropertyService) {

        this.stroomPropertyService = stroomPropertyService;
        this.zookeeperUrl = stroomPropertyService.getProperty(PROP_KEY_ZOOKEEPER_QUORUM);

        //try and start the connection with ZK in another thread to prevent connection problems from stopping the bean
        //creation and application startup
        CompletableFuture.runAsync(this::startCurator);
    }

    public Optional<CuratorFramework> getCuratorFramework() {
       return Optional.ofNullable(curatorFrameworkRef.get());
    }

    public void registerStartupListener(final Consumer<CuratorFramework> listener) {
        curatorStartupListeners.add(Preconditions.checkNotNull(listener));
    }

    private void startCurator() {
        int baseSleepTimeMs = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_BASE_SLEEP_TIME_MS, 5_000);
        int maxSleepTimeMs = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_MAX_SLEEP_TIME_MS, 300_000);
        int maxRetries = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_MAX_RETRIES, 100);

        RetryPolicy retryPolicy = new BoundedExponentialBackoffRetry(baseSleepTimeMs, maxSleepTimeMs, maxRetries);

        LOGGER.info("Starting Curator client using Zookeeper at '{}'", zookeeperUrl);
        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(zookeeperUrl, retryPolicy);
        boolean wasSet = curatorFrameworkRef.compareAndSet(null, curatorFramework);
        if (!wasSet) {
            LOGGER.error("Attempt to set curatorFrameworkRef when already set");
        } else {
            curatorFramework.start();
            LOGGER.info("Started Curator client using Zookeeper at '{}'", zookeeperUrl);

            //now notify all listeners
            curatorStartupListeners.forEach(listener -> listener.accept(curatorFramework));
        }
    }

    @StroomShutdown
    public void shutdown() {
        CuratorFramework curatorFramework = curatorFrameworkRef.get();

        if (curatorFramework != null) {
            try {
                curatorFramework.close();
            } catch (Exception e) {
                LOGGER.error("Error while closing curator framework", e);
            }
        }
    }

}
