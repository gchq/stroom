package stroom.servicediscovery;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Preconditions;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.spring.StroomShutdown;

import javax.inject.Singleton;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@Singleton
public class ServiceDiscoveryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryManager.class);

    public static final String PROP_KEY_ZOOKEEPER_QUORUM = "stroom.serviceDiscovery.zookeeperUrl";
    public static final String PROP_KEY_CURATOR_BASE_SLEEP_TIME_MS = "stroom.serviceDiscovery.curator.baseSleepTimeMs";
    public static final String PROP_KEY_CURATOR_MAX_SLEEP_TIME_MS = "stroom.serviceDiscovery.curator.maxSleepTimeMs";
    public static final String PROP_KEY_CURATOR_MAX_RETRIES = "stroom.serviceDiscovery.curator.maxRetries";
    public static final String PROP_KEY_ZOOKEEPER_BASE_PATH = "stroom.serviceDiscovery.zookeeperBasePath";

    private final StroomPropertyService stroomPropertyService;
    private final String zookeeperUrl;

    private final AtomicReference<CuratorFramework> curatorFrameworkRef = new AtomicReference<>();
    private final AtomicReference<ServiceDiscovery<String>> serviceDiscoveryRef = new AtomicReference<>();
    private final List<Consumer<ServiceDiscovery<String>>> curatorStartupListeners = new ArrayList<>();

    private volatile HealthCheck.Result health;
    private final List<Closeable> closeables = new ArrayList<>();

    @SuppressWarnings("unused")
    public ServiceDiscoveryManager(final StroomPropertyService stroomPropertyService) {

        this.stroomPropertyService = stroomPropertyService;
        this.zookeeperUrl = stroomPropertyService.getProperty(PROP_KEY_ZOOKEEPER_QUORUM);

        health = HealthCheck.Result.unhealthy("Initialising Curator Connection...");

        //try and start the connection with ZK in another thread to prevent connection problems from stopping the bean
        //creation and application startup, then start ServiceDiscovery and notify any listeners
        CompletableFuture.runAsync(this::startCurator)
                .thenRun(this::startServiceDiscovery)
                .thenRun(this::notifyListeners)
                .exceptionally(throwable -> {
                    LOGGER.error("Error initialising service discovery", throwable);
                    health = HealthCheck.Result.unhealthy("Failed to initialise service discovery due to error: " + throwable.getMessage());
                    return null;
                });
    }

    public Optional<ServiceDiscovery<String>> getServiceDiscovery() {
       return Optional.ofNullable(serviceDiscoveryRef.get());
    }

    public void registerStartupListener(final Consumer<ServiceDiscovery<String>> listener) {
        curatorStartupListeners.add(Preconditions.checkNotNull(listener));
    }


    private void startCurator() {
        int baseSleepTimeMs = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_BASE_SLEEP_TIME_MS, 5_000);
        int maxSleepTimeMs = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_MAX_SLEEP_TIME_MS, 300_000);
        int maxRetries = stroomPropertyService.getIntProperty(PROP_KEY_CURATOR_MAX_RETRIES, 100);

        RetryPolicy retryPolicy = new BoundedExponentialBackoffRetry(baseSleepTimeMs, maxSleepTimeMs, maxRetries);

        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(zookeeperUrl, retryPolicy);
        LOGGER.info("Starting Curator client using Zookeeper at '{}'", zookeeperUrl);
        curatorFramework.start();
        closeables.add(curatorFramework);

        boolean wasSet = curatorFrameworkRef.compareAndSet(null, curatorFramework);
        if (!wasSet) {
            LOGGER.error("Attempt to set curatorFrameworkRef when already set");
        } else {
            health = HealthCheck.Result.unhealthy("Curator client started, initialising service discovery...");
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
            closeables.add(serviceDiscovery);
            boolean wasSet = serviceDiscoveryRef.compareAndSet(null, serviceDiscovery);
            if (!wasSet) {
                LOGGER.error("Attempt to set serviceDiscoveryRef when already set");
            } else {
                LOGGER.info("Successfully started ServiceDiscovery on path " + basePath);
            }

        } catch (Exception e) {
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
                } catch (Exception e) {
                    LOGGER.error("Error while closing {}", closeable.getClass().getCanonicalName(), e);
                }
            }
        });
    }

    public HealthCheck.Result getHealth() {
        ServiceDiscovery<String> serviceDiscovery = serviceDiscoveryRef.get();
        if (serviceDiscovery != null) {
            try {
                String services = serviceDiscovery.queryForNames().stream()
                        .collect(Collectors.joining(", "));
                return HealthCheck.Result.healthy("Running. Services found: " + services);

            } catch (Exception e) {
                return HealthCheck.Result.unhealthy("Error while querying available services, %s", e.getMessage());
            }
        }
        return HealthCheck.Result.unhealthy("ServiceDiscovery is null");
    }

}
