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

    private final ServiceDiscoveryConfig serviceDiscoveryConfig;
    private final String zookeeperUrl;

    private final AtomicReference<CuratorFramework> curatorFrameworkRef = new AtomicReference<>();
    private final AtomicReference<ServiceDiscovery<String>> serviceDiscoveryRef = new AtomicReference<>();
    private final List<Consumer<ServiceDiscovery<String>>> curatorStartupListeners = new ArrayList<>();

    private final Deque<Closeable> closeables = new LinkedList<>();

    @SuppressWarnings("unused")
    @Inject
    ServiceDiscoveryManager(final ServiceDiscoveryConfig serviceDiscoveryConfig) {
        this.serviceDiscoveryConfig = serviceDiscoveryConfig;
        this.zookeeperUrl = serviceDiscoveryConfig.getZookeeperUrl();


        boolean isServiceDiscoveryEnabled = serviceDiscoveryConfig.isEnabled();

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
        int baseSleepTimeMs = serviceDiscoveryConfig.getCuratorBaseSleepTimeMs();
        int maxSleepTimeMs = serviceDiscoveryConfig.getCuratorMaxSleepTimeMs();
        int maxRetries = serviceDiscoveryConfig.getCuratorMaxRetries();

        RetryPolicy retryPolicy = new BoundedExponentialBackoffRetry(baseSleepTimeMs, maxSleepTimeMs, maxRetries);

        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(zookeeperUrl, retryPolicy);
        LOGGER.info("Starting Curator client using Zookeeper at '{}'", zookeeperUrl);
        curatorFramework.start();
        try {
            curatorFramework.blockUntilConnected();
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
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
        String basePath = Preconditions.checkNotNull(serviceDiscoveryConfig.getZookeeperBasePath());
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
