package stroom.proxy.app;

import stroom.proxy.app.event.EventStore;
import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.repo.AggregateForwarder;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.FrequencyBatchExecutor;
import stroom.proxy.repo.FrequencyExecutor;
import stroom.proxy.repo.ParallelExecutor;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.RepoSources;
import stroom.proxy.repo.SourceForwarder;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.shared.Flushable;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

public class ProxyLifecycle implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyLifecycle.class);

    private final List<Managed> services = new ArrayList<>();

    @Inject
    public ProxyLifecycle(final ProxyConfig proxyConfig,
                          final ProxyDbConfig proxyDbConfig,
                          final ProxyRepoConfig proxyRepoConfig,
                          final AggregatorConfig aggregatorConfig,
                          final EventStoreConfig eventStoreConfig,
                          final ThreadConfig threadConfig,
                          final Provider<SequentialFileStore> sequentialFileStoreProvider,
                          final Provider<RepoSources> proxyRepoSourcesProvider,
                          final Provider<RepoSourceItems> proxyRepoSourceEntriesProvider,
                          final Provider<Aggregator> aggregatorProvider,
                          final Provider<AggregateForwarder> aggregatorForwarderProvider,
                          final Provider<SourceForwarder> sourceForwarderProvider,
                          final Provider<Cleanup> cleanupProvider,
                          final Provider<Set<Flushable>> flushableProvider,
                          final Provider<FileScanners> fileScannersProvider,
                          final Provider<EventStore> eventStoreProvider) {

        // Get forwarding destinations.
        final List<ForwardConfig> forwardDestinations = proxyConfig.getForwardDestinations();

        // If we aren't storing and forwarding then don't do anything.
        if (proxyRepoConfig.isStoringEnabled() &&
                forwardDestinations != null &&
                forwardDestinations.size() > 0) {

            final long dbFlushFrequencyMs = proxyDbConfig.getFlushFrequency().toMillis();

            // Start looking at file store to add sources to the DB.
            final SequentialFileStore sequentialFileStore = sequentialFileStoreProvider.get();
            addParallelExecutor(
                    "Add sources",
                    () -> () -> proxyRepoSourcesProvider.get().addSources(sequentialFileStore),
                    1);

            final Cleanup cleanup = cleanupProvider.get();

            // Add file scanners.
            services.add(fileScannersProvider.get());

            // Start flushing the DB.
            final Set<Flushable> flushables = flushableProvider.get();
            addFrequencyExecutor("Flush DB records",
                    () -> () -> flushables.forEach(Flushable::flush),
                    dbFlushFrequencyMs);

            if (aggregatorConfig.isEnabled()) {
                // We are going to do aggregate forwarding so reset source forwarder.
                cleanup.resetSourceForwarder();

                final RepoSources repoSources = proxyRepoSourcesProvider.get();
                final RepoSourceItems repoSourceItems = proxyRepoSourceEntriesProvider.get();
                final Aggregator aggregator = aggregatorProvider.get();
                final AggregateForwarder aggregateForwarder = aggregatorForwarderProvider.get();
                final long aggregationFrequencyMs = aggregatorConfig.getAggregationFrequency().toMillis();

                // Only examine source files if we are aggregating.

                // Add executor to open source files and scan entries
                addFrequencyBatchExecutor("RepoSourceItems - examine",
                        threadConfig.getExamineSourceThreadCount(),
                        repoSources::getNewSources,
                        repoSourceItems::examineSource,
                        dbFlushFrequencyMs);

                // Just keep trying to aggregate based on a frequency and not changes to source entries.
                addFrequencyExecutor("Aggregator - aggregate",
                        () -> aggregator::aggregateAll,
                        dbFlushFrequencyMs);

                // Periodically close old aggregates.
                addFrequencyExecutor("Aggregator - closeOldAggregates",
                        () -> aggregator::closeOldAggregates,
                        aggregationFrequencyMs);

                // Create forward records.
                addFrequencyExecutor("AggregateForwarder - createForwardRecord",
                        () -> aggregateForwarder::createAllForwardAggregates,
                        dbFlushFrequencyMs);

                // Forward records.
                addFrequencyBatchExecutor("AggregateForwarder - forwardNext",
                        threadConfig.getForwardThreadCount(),
                        aggregateForwarder::getNewForwardAggregates,
                        aggregateForwarder::forward,
                        dbFlushFrequencyMs);

                // Retry forward records.
                final long retryFrequency = proxyConfig.getRetryFrequency().toMillis();
                addFrequencyBatchExecutor("AggregateForwarder - forwardRetry",
                        threadConfig.getForwardRetryThreadCount(),
                        aggregateForwarder::getRetryForwardAggregates,
                        forwardAggregate -> aggregateForwarder.forwardRetry(forwardAggregate, retryFrequency),
                        dbFlushFrequencyMs);

            } else {
                // We are going to do source forwarding so reset aggregate forwarder.
                cleanup.resetAggregateForwarder();

                final SourceForwarder sourceForwarder = sourceForwarderProvider.get();

                // Create forward records.
                addFrequencyExecutor(
                        "SourceForwarder - createForwardRecord",
                        () -> sourceForwarder::createAllForwardSources,
                        dbFlushFrequencyMs);

                // Forward records.
                addFrequencyBatchExecutor("SourceForwarder - forwardNext",
                        threadConfig.getForwardThreadCount(),
                        sourceForwarder::getNewForwardSources,
                        sourceForwarder::forward,
                        dbFlushFrequencyMs);

                // Retry forward records.
                final long retryFrequency = proxyConfig.getRetryFrequency().toMillis();
                addFrequencyBatchExecutor("SourceForwarder - forwardRetry",
                        threadConfig.getForwardRetryThreadCount(),
                        sourceForwarder::getRetryForwardSources,
                        forwardSource -> sourceForwarder.forwardRetry(forwardSource, retryFrequency),
                        dbFlushFrequencyMs);
            }

            addFrequencyExecutor("Cleanup - cleanupSources",
                    () -> cleanup::cleanupSources,
                    proxyDbConfig.getCleanupFrequency().toMillis());
        }

        // Add executor to roll event store.
        final EventStore eventStore = eventStoreProvider.get();
        addFrequencyExecutor("Event Store - roll",
                () -> eventStore::tryRoll,
                eventStoreConfig.getRollFrequency().toMillis());
        // Add executor to forward event store.
        addFrequencyExecutor("Event Store - forward",
                () -> eventStore::forwardAll,
                eventStoreConfig.getRollFrequency().toMillis());
    }

    private void addParallelExecutor(final String threadName,
                                     final Supplier<Runnable> runnableSupplier,
                                     final int threadCount) {
        final ParallelExecutor executor = new ParallelExecutor(
                threadName,
                runnableSupplier,
                threadCount);
        services.add(executor);
    }

    private void addFrequencyExecutor(final String threadName,
                                      final Supplier<Runnable> runnableSupplier,
                                      final long frequencyMs) {
        final FrequencyExecutor executor = new FrequencyExecutor(
                threadName,
                runnableSupplier,
                frequencyMs);
        services.add(executor);
    }

    private <T> void addFrequencyBatchExecutor(final String threadName,
                                               final int threadCount,
                                               final Supplier<Batch<T>> supplier,
                                               final Consumer<T> consumer,
                                               final long frequencyMs) {
        final FrequencyBatchExecutor<T> executor = new FrequencyBatchExecutor<>(
                threadName,
                threadCount,
                supplier,
                consumer,
                frequencyMs);
        services.add(executor);
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("Starting Stroom Proxy");

        for (final Managed service : services) {
            service.start();
        }

        LOGGER.info("Started Stroom Proxy");
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping Stroom Proxy");

        for (int i = services.size() - 1; i >= 0; i--) {
            try {
                services.get(i).stop();
            } catch (final Exception e) {
                LOGGER.error("error", e);
            }
        }

        // This method is part of DW  Managed which is managed by Jersey so we need to ensure any interrupts
        // are cleared before it goes back to Jersey
        final boolean interrupted = Thread.interrupted();
        LOGGER.debug("Was interrupted = " + interrupted);

        LOGGER.info("Stopped Stroom Proxy");
    }
}
