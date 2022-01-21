package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.repo.AggregateForwarder;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.FrequencyExecutor;
import stroom.proxy.repo.ParallelExecutor;
import stroom.proxy.repo.ProxyRepo;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScanner;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.SourceForwarder;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

public class ProxyLifecycle implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyLifecycle.class);

    private final List<Managed> services = new ArrayList<>();

    @Inject
    public ProxyLifecycle(final ProxyRepoConfig proxyRepoConfig,
                          final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig,
                          final AggregatorConfig aggregatorConfig,
                          final ForwarderConfig forwarderConfig,
                          final ThreadConfig threadConfig,
                          final Provider<ProxyRepo> proxyRepoProvider,
                          final Provider<ProxyRepoFileScanner> proxyRepoFileScannerProvider,
                          final Provider<RepoSourceItems> proxyRepoSourceEntriesProvider,
                          final Provider<Aggregator> aggregatorProvider,
                          final Provider<AggregateForwarder> aggregatorForwarderProvider,
                          final Provider<SourceForwarder> sourceForwarderProvider,
                          final Provider<Cleanup> cleanupProvider) {

        // If storing isn't enabled then no lifecycle startup is needed.
        if (proxyRepoConfig.isStoringEnabled()) {
            final Cleanup cleanup = cleanupProvider.get();
            final ProxyRepo proxyRepo = proxyRepoProvider.get();

            // Create a service to cleanup the repo to remove empty dirs and stale lock files.
            addFrequencyExecutor("ProxyRepo - clean",
                    () -> proxyRepo::clean,
                    proxyRepoConfig.getCleanupFrequency().toMillis());

            if (proxyRepoFileScannerConfig.isScanningEnabled()) {
                // Add executor to scan proxy files from a repo where a repo is not populated by receiving data.
                final ProxyRepoFileScanner proxyRepoFileScanner = proxyRepoFileScannerProvider.get();
                addFrequencyExecutor("ProxyRepoFileScanner - scan",
                        () -> proxyRepoFileScanner::scan,
                        proxyRepoFileScannerConfig.getScanFrequency().toMillis());
            }

            // If we aren't forwarding then don't do anything else other than running repo cleanups.
            if (forwarderConfig.isForwardingEnabled() &&
                    forwarderConfig.getForwardDestinations() != null &&
                    forwarderConfig.getForwardDestinations().size() > 0) {
                if (aggregatorConfig.isEnabled()) {
                    // We are going to do aggregate forwarding so reset source forwarder.
                    cleanup.resetSourceForwarder();

                    final RepoSourceItems repoSourceItems = proxyRepoSourceEntriesProvider.get();
                    final Aggregator aggregator = aggregatorProvider.get();
                    final AggregateForwarder aggregateForwarder = aggregatorForwarderProvider.get();

                    // Only examine source files if we are aggregating.

                    // Add executor to open source files and scan entries
                    addParallelExecutor("RepoSourceItems - examine",
                            () -> repoSourceItems::examineNext,
                            threadConfig.getExamineSourceThreadCount());

                    // Just keep trying to aggregate based on a frequency and not changes to source entries.
                    addParallelExecutor("Aggregator - aggregate",
                            () -> aggregator::aggregateNext,
                            threadConfig.getAggregatorThreadCount());

                    // Periodically close old aggregates.
                    addFrequencyExecutor("Aggregator - closeOldAggregates",
                            () -> aggregator::closeOldAggregates,
                            aggregatorConfig.getAggregationFrequency().toMillis());

                    // Create forward records.
                    addParallelExecutor("AggregateForwarder - createForwardRecord",
                            () -> aggregateForwarder::createNextForwardRecord,
                            threadConfig.getCreateForwardRecordThreadCount());

                    // Forward records.
                    addParallelExecutor("AggregateForwarder - forwardNext",
                            () -> aggregateForwarder::forwardNext,
                            threadConfig.getForwardThreadCount());

                    // Retry forward records.
                    final long retryFrequency = forwarderConfig.getRetryFrequency().toMillis();
                    addParallelExecutor("AggregateForwarder - forwardRetry",
                            () -> {
                                final long oldest = System.currentTimeMillis() - retryFrequency;
                                return () -> aggregateForwarder.forwardRetry(oldest);
                            },
                            threadConfig.getForwardRetryThreadCount());

                } else {
                    // We are going to do source forwarding so reset aggregate forwarder.
                    cleanup.resetAggregateForwarder();

                    final SourceForwarder sourceForwarder = sourceForwarderProvider.get();
                    // Create forward records.
                    addParallelExecutor(
                            "SourceForwarder - createForwardRecord",
                            () -> sourceForwarder::createNextForwardRecord,
                            threadConfig.getCreateForwardRecordThreadCount());

                    // Forward records.
                    addParallelExecutor("SourceForwarder - forwardNext",
                            () -> sourceForwarder::forwardNext,
                            threadConfig.getForwardThreadCount());

                    // Retry forward records.
                    final long retryFrequency = forwarderConfig.getRetryFrequency().toMillis();
                    addParallelExecutor("SourceForwarder - forwardRetry",
                            () -> {
                                final long oldest = System.currentTimeMillis() - retryFrequency;
                                return () -> sourceForwarder.forwardRetry(oldest);
                            },
                            threadConfig.getForwardRetryThreadCount());
                }

                addFrequencyExecutor("Cleanup - cleanupSources",
                        () -> cleanup::cleanupSources,
                        proxyRepoConfig.getCleanupFrequency().toMillis());
            }
        }
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
