package stroom.servicediscovery;

import com.google.common.base.Preconditions;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.properties.StroomPropertyService;
import stroom.util.lifecycle.StroomShutdown;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ServiceDiscoveryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryManager.class);

    public static final String PROP_KEY_PREFIX = "stroom.serviceDiscovery.";
    public static final String PROP_KEY_ZOOKEEPER_QUORUM = PROP_KEY_PREFIX + "zookeeperUrl";
    public static final String PROP_KEY_CURATOR_BASE_SLEEP_TIME_MS = PROP_KEY_PREFIX + "curator.baseSleepTimeMs";
    public static final String PROP_KEY_CURATOR_MAX_SLEEP_TIME_MS = PROP_KEY_PREFIX + "curator.maxSleepTimeMs";
    public static final String PROP_KEY_CURATOR_MAX_RETRIES = PROP_KEY_PREFIX + "curator.maxRetries";
    public static final String PROP_KEY_ZOOKEEPER_BASE_PATH = PROP_KEY_PREFIX + "zookeeperBasePath";
    public static final String PROP_KEY_SERVICE_DISCOVERY_ENABLED = PROP_KEY_PREFIX + "enabled";

    private final StroomPropertyService stroomPropertyService;
    private final String zookeeperUrl;

    private final AtomicReference<CuratorFramework> curatorFrameworkRef = new AtomicReference<>();
    private final AtomicReference<ServiceDiscovery<String>> serviceDiscoveryRef = new AtomicReference<>();
    private final List<Consumer<ServiceDiscovery<String>>> curatorStartupListeners = new ArrayList<>();

    private final Deque<Closeable> closeables = new LinkedList<>();

    @SuppressWarnings("unused")
    @Inject
    ServiceDiscoveryManager(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
        this.zookeeperUrl = stroomPropertyService.getProperty(PROP_KEY_ZOOKEEPER_QUORUM);


        boolean isServiceDiscoveryEnabled = stroomPropertyService.getBooleanProperty(
                PROP_KEY_SERVICE_DISCOVERY_ENABLED,
                false);

        if (isServiceDiscoveryEnabled) {
            //try and start the connection with ZK in another thread to prevent connection problems from stopping the bean
            //creation and application startup, then start ServiceDiscovery and notify any listeners
            CompletableFuture.runAsync(this::startCurator)
                    .thenRun(this::startServiceDiscovery)
                    .thenRun(this::notifyListeners)
                    .exceptionally(throwable -> {
                        LOGGER.error("Error initialising service discovery", throwable);
                        return null;
                    });
        } else {
            LOGGER.info("Service discovery is disabled, won't attempt to connect to Zookeeper");
        }
    }

    public void registerStartupListener(final Consumer<ServiceDiscovery<String>> listener) {
        if (serviceDiscoveryRef.get() != null) {
            //already started so call the listener now
            listener.accept(serviceDiscoveryRef.get());
        } else {
            curatorStartupListeners.add(Preconditions.checkNotNull(listener));
        }
    }

    private void startCurator() {
        int baseSleepTimeMs = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_BASE_SLEEP_TIME_MS, 5_000);
        int maxSleepTimeMs = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_MAX_SLEEP_TIME_MS, 300_000);
        int maxRetries = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_MAX_RETRIES, 100);

        RetryPolicy retryPolicy = new BoundedExponentialBackoffRetry(baseSleepTimeMs, maxSleepTimeMs, maxRetries);

        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(zookeeperUrl, retryPolicy);
        LOGGER.info("Starting Curator client using Zookeeper at '{}'", zookeeperUrl);
        curatorFramework.start();
        try {
            curatorFramework.blockUntilConnected();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for connection to zookeeper");
        }
        closeables.push(curatorFramework);

        boolean wasSet = curatorFrameworkRef.compareAndSet(null, curatorFramework);
        if (!wasSet) {
            LOGGER.error("Attempt to set curatorFrameworkRef when already set");
        } else {
            LOGGER.info("Started Curator client using Zookeeper at '{}'", zookeeperUrl);
        }
    }

    private void startServiceDiscovery() {
        String basePath = Preconditions.checkNotNull(stroomPropertyService.getProperty(PROP_KEY_ZOOKEEPER_BASE_PATH));

        ServiceDiscovery<String> serviceDiscovery = ServiceDiscoveryBuilder
                .builder(String.class)
                .client(Preconditions.checkNotNull(curatorFrameworkRef.get(), "curatorFramework should not be null at this point"))
                .basePath(basePath)
                .build();

        try {
            serviceDiscovery.start();
            //push to the top of the queue to ensure this gets closed before the curator framework else it
            //won't work as it has no client
            closeables.push(serviceDiscovery);
            boolean wasSet = serviceDiscoveryRef.compareAndSet(null, serviceDiscovery);
            if (!wasSet) {
                LOGGER.error("Attempt to set serviceDiscoveryRef when already set");
            } else {
                LOGGER.info("Successfully started ServiceDiscovery on path " + basePath);
            }

        } catch (final Exception e) {
            throw new RuntimeException(String.format("Error starting ServiceDiscovery with base path %s", basePath), e);
        }
    }

    private void notifyListeners() {

        if (serviceDiscoveryRef.get() != null) {
            //now notify all listeners
            curatorStartupListeners.forEach(listener -> listener.accept(serviceDiscoveryRef.get()));
        } else {
            LOGGER.error("Unable to notify listeners of serviceDiscovery starting, serviceDiscovery is null");
        }
    }

    @StroomShutdown
    public void shutdown() {

        closeables.forEach(closeable -> {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (final IOException e) {
                    LOGGER.error("Error while closing {}", closeable.getClass().getCanonicalName(), e);
                }
            }
        });
    }

}
