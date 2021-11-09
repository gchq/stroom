package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.repo.AggregateForwarder;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.FrequencyExecutor;
import stroom.proxy.repo.ProxyRepo;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScanner;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.SimpleExecutor;
import stroom.proxy.repo.SourceForwarder;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class ProxyLifecycle implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyLifecycle.class);

    private final List<Managed> services;

    @Inject
    public ProxyLifecycle(final ProxyRepoConfig proxyRepoConfig,
                          final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig,
                          final AggregatorConfig aggregatorConfig,
                          final ForwarderConfig forwarderConfig,
                          final Provider<ProxyRepo> proxyRepoProvider,
                          final Provider<ProxyRepoFileScanner> proxyRepoFileScannerProvider,
                          final Provider<RepoSourceItems> proxyRepoSourceEntriesProvider,
                          final Provider<Aggregator> aggregatorProvider,
                          final Provider<AggregateForwarder> aggregatorForwarderProvider,
                          final Provider<SourceForwarder> sourceForwarderProvider,
                          final Provider<Cleanup> cleanupProvider) {
        services = new ArrayList<>();

        // If storing isn't enabled then no lifecycle startup is needed.
        if (proxyRepoConfig.isStoringEnabled()) {
            // Create a service to cleanup the repo to remove empty dirs and stale lock files.
            final ProxyRepo proxyRepo = proxyRepoProvider.get();
            final FrequencyExecutor cleanupRepoExecutor = new FrequencyExecutor(
                    "ProxyRepo - clean",
                    () -> proxyRepo::clean,
                    proxyRepoConfig.getCleanupFrequency().toMillis());
            services.add(cleanupRepoExecutor);

            if (proxyRepoFileScannerConfig.isScanningEnabled()) {
                // Add executor to scan proxy files from a repo where a repo is not populated by receiving data.
                final ProxyRepoFileScanner proxyRepoFileScanner = proxyRepoFileScannerProvider.get();
                final FrequencyExecutor proxyRepoFileScannerExecutor = new FrequencyExecutor(
                        "ProxyRepoFileScanner - scan",
                        () -> proxyRepoFileScanner::scan,
                        proxyRepoFileScannerConfig.getScanFrequency().toMillis());
                services.add(proxyRepoFileScannerExecutor);
            }

            // If we aren't forwarding then don't do anything else other than running repo cleanups.
            if (forwarderConfig.isForwardingEnabled() &&
                    forwarderConfig.getForwardDestinations() != null &&
                    forwarderConfig.getForwardDestinations().size() > 0) {
                if (aggregatorConfig.isEnabled()) {
                    final RepoSourceItems repoSourceItems = proxyRepoSourceEntriesProvider.get();
                    final Aggregator aggregator = aggregatorProvider.get();
                    final AggregateForwarder aggregateForwarder = aggregatorForwarderProvider.get();

                    // Only examine source files if we are aggregating.

                    // Add executor to open source files and scan entries
                    final SimpleExecutor proxyRepoSourceEntriesExecutor = new SimpleExecutor(
                            "RepoSourceItems - examine",
                            () -> repoSourceItems::examineNext,
                            5);
                    services.add(proxyRepoSourceEntriesExecutor);

                    // Just kep trying to aggregate based on a frequency and not changes to source entries.
                    final SimpleExecutor aggregatorExecutor = new SimpleExecutor(
                            "Aggregator - aggregate",
                            () -> aggregator::aggregateNext,
                            5);
                    services.add(aggregatorExecutor);

                    // Periodically close old aggregates.
                    final long aggregateFrequency = aggregatorConfig.getAggregationFrequency().toMillis();
                    final FrequencyExecutor closeAggregatesExecutor = new FrequencyExecutor(
                            "Aggregator - closeOldAggregates",
                            () -> aggregator::closeOldAggregates,
                            aggregateFrequency);
                    services.add(closeAggregatesExecutor);

                    // Create forward records.
                    final SimpleExecutor createForwardRecordExecutor = new SimpleExecutor(
                            "AggregateForwarder - createForwardRecord",
                            () -> aggregateForwarder::createNextForwardRecord,
                            5);
                    services.add(createForwardRecordExecutor);

                    // Forward records.
                    final SimpleExecutor forwarderExecutor = new SimpleExecutor(
                            "AggregateForwarder = forwardNext",
                            () -> aggregateForwarder::forwardNext,
                            5);
                    services.add(forwarderExecutor);

                    // Retry forward records.
                    final long retryFrequency = forwarderConfig.getRetryFrequency().toMillis();
                    final SimpleExecutor retryExecutor = new SimpleExecutor(
                            "AggregateForwarder - forwardRetry",
                            () -> {
                                final long oldest = System.currentTimeMillis() - retryFrequency;
                                return () -> aggregateForwarder.forwardRetry(oldest);
                            },
                            5);
                    services.add(retryExecutor);

                } else {
                    final SourceForwarder sourceForwarder = sourceForwarderProvider.get();
                    // Create forward records.
                    final SimpleExecutor createForwardRecordExecutor = new SimpleExecutor(
                            "SourceForwarder - createForwardRecord",
                            () -> sourceForwarder::createNextForwardRecord,
                            5);
                    services.add(createForwardRecordExecutor);

                    // Forward records.
                    final SimpleExecutor forwarderExecutor = new SimpleExecutor(
                            "SourceForwarder - forwardNext",
                            () -> sourceForwarder::forwardNext,
                            5);
                    services.add(forwarderExecutor);

                    // Retry forward records.
                    final long retryFrequency = forwarderConfig.getRetryFrequency().toMillis();
                    final SimpleExecutor retryExecutor = new SimpleExecutor(
                            "SourceForwarder - forwardRetry",
                            () -> {
                                final long oldest = System.currentTimeMillis() - retryFrequency;
                                return () -> sourceForwarder.forwardRetry(oldest);
                            },
                            5);
                    services.add(retryExecutor);
                }

                final Cleanup cleanup = cleanupProvider.get();
                final FrequencyExecutor cleanupExecutor = new FrequencyExecutor(
                        "Cleanup - cleanupSources",
                        () -> cleanup::cleanupSources,
                        proxyRepoConfig.getCleanupFrequency().toMillis());
                services.add(cleanupExecutor);
            }
        }
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
