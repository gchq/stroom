package stroom.proxy.app;

import stroom.proxy.app.event.EventStore;
import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.repo.AggregateForwarder;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.RepoSources;
import stroom.proxy.repo.SourceForwarder;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.shared.Flushable;

import io.dropwizard.lifecycle.Managed;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;

public class ProxyLifecycle implements Managed {

    private final ProxyServices proxyServices;

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
                          final Provider<EventStore> eventStoreProvider,
                          final ProxyServices proxyServices) {
        this.proxyServices = proxyServices;

        // Get forwarding destinations.
        final List<ForwardConfig> forwardDestinations = proxyConfig.getForwardDestinations();

        // If we aren't storing and forwarding then don't do anything.
        if (proxyRepoConfig.isStoringEnabled() &&
                forwardDestinations != null &&
                forwardDestinations.size() > 0) {

            final long dbFlushFrequencyMs = proxyDbConfig.getFlushFrequency().toMillis();

            // Start looking at file store to add sources to the DB.
            final SequentialFileStore sequentialFileStore = sequentialFileStoreProvider.get();
            proxyServices.addParallelExecutor(
                    "Add sources",
                    () -> () -> proxyRepoSourcesProvider.get().addSources(sequentialFileStore),
                    1);

            final Cleanup cleanup = cleanupProvider.get();

            // Add file scanners.
            fileScannersProvider.get().register(proxyServices);

            // Start flushing the DB.
            final Set<Flushable> flushables = flushableProvider.get();
            proxyServices.addFrequencyExecutor("Flush DB records",
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
                proxyServices.addBatchExecutor("RepoSourceItems - examine",
                        threadConfig.getExamineSourceThreadCount(),
                        repoSources::getNewSources,
                        repoSourceItems::examineSource);

                // Just keep trying to aggregate based on a frequency and not changes to source entries.
                proxyServices.addFrequencyExecutor("Aggregator - aggregate",
                        () -> aggregator::aggregateAll,
                        dbFlushFrequencyMs);

                // Periodically close old aggregates.
                proxyServices.addFrequencyExecutor("Aggregator - closeOldAggregates",
                        () -> aggregator::closeOldAggregates,
                        aggregationFrequencyMs);

                // Create forward records.
                proxyServices.addFrequencyExecutor("AggregateForwarder - createForwardRecord",
                        () -> aggregateForwarder::createAllForwardAggregates,
                        dbFlushFrequencyMs);

                // Forward records.
                proxyServices.addBatchExecutor("AggregateForwarder - forwardNext",
                        threadConfig.getForwardThreadCount(),
                        aggregateForwarder::getNewForwardAggregates,
                        aggregateForwarder::forward);

                // Retry forward records.
                proxyServices.addBatchExecutor("AggregateForwarder - forwardRetry",
                        threadConfig.getForwardRetryThreadCount(),
                        aggregateForwarder::getRetryForwardAggregates,
                        aggregateForwarder::forwardRetry);

            } else {
                // We are going to do source forwarding so reset aggregate forwarder.
                cleanup.resetAggregateForwarder();

                final SourceForwarder sourceForwarder = sourceForwarderProvider.get();

                // Create forward records.
                proxyServices.addFrequencyExecutor(
                        "SourceForwarder - createForwardRecord",
                        () -> sourceForwarder::createAllForwardSources,
                        dbFlushFrequencyMs);

                // Forward records.
                proxyServices.addBatchExecutor("SourceForwarder - forwardNext",
                        threadConfig.getForwardThreadCount(),
                        sourceForwarder::getNewForwardSources,
                        sourceForwarder::forward);

                // Retry forward records.
                proxyServices.addBatchExecutor("SourceForwarder - forwardRetry",
                        threadConfig.getForwardRetryThreadCount(),
                        sourceForwarder::getRetryForwardSources,
                        sourceForwarder::forwardRetry);
            }

            proxyServices.addFrequencyExecutor("Cleanup - cleanupSources",
                    () -> cleanup::cleanupSources,
                    proxyDbConfig.getCleanupFrequency().toMillis());
        }

        // Add executor to roll event store.
        final EventStore eventStore = eventStoreProvider.get();
        proxyServices.addFrequencyExecutor("Event Store - roll",
                () -> eventStore::tryRoll,
                eventStoreConfig.getRollFrequency().toMillis());
        // Add executor to forward event store.
        proxyServices.addFrequencyExecutor("Event Store - forward",
                () -> eventStore::forwardAll,
                eventStoreConfig.getRollFrequency().toMillis());

        if (proxyConfig.getSqsConnectors() != null) {
            for (final SqsConnectorConfig sqsConnectorConfig : proxyConfig.getSqsConnectors()) {
                final SqsConnector sqsConnector = new SqsConnector(eventStore, sqsConnectorConfig);
                // Add executor to forward event store.
                proxyServices.addFrequencyExecutor("SQS - poll",
                        () -> sqsConnector::poll,
                        sqsConnectorConfig.getPollFrequency().toMillis());
            }
        }
    }

    @Override
    public void start() throws Exception {
        proxyServices.start();
    }

    @Override
    public void stop() {
        proxyServices.stop();
    }
}
