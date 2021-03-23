package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwardRetryExecutor;
import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.repo.AggregateForwarder;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ChangeListenerExecutor;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.Forwarder;
import stroom.proxy.repo.FrequencyExecutor;
import stroom.proxy.repo.HasShutdown;
import stroom.proxy.repo.ProxyRepo;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScanner;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.ProxyRepoSourceEntries;
import stroom.proxy.repo.ProxyRepoSources;
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
    private final List<HasShutdown> requireShutdown;

    @Inject
    public ProxyLifecycle(final ProxyRepoConfig proxyRepoConfig,
                          final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig,
                          final AggregatorConfig aggregatorConfig,
                          final ForwarderConfig forwarderConfig,
                          final Provider<ProxyRepo> proxyRepoProvider,
                          final Provider<ProxyRepoFileScanner> proxyRepoFileScannerProvider,
                          final Provider<ProxyRepoSources> proxyRepoSourcesProvider,
                          final Provider<ProxyRepoSourceEntries> proxyRepoSourceEntriesProvider,
                          final Provider<Aggregator> aggregatorProvider,
                          final Provider<AggregateForwarder> aggregatorForwarderProvider,
                          final Provider<SourceForwarder> sourceForwarderProvider,
                          final Provider<Cleanup> cleanupProvider) {
        services = new ArrayList<>();
        requireShutdown = new ArrayList<>();

        // If storing isn't enabled then no lifecycle startup is needed.
        if (proxyRepoConfig.isStoringEnabled()) {
            // Create a service to cleanup the repo to remove empty dirs and stale lock files.
            final ProxyRepo proxyRepo = proxyRepoProvider.get();
            final FrequencyExecutor cleanupRepoExecutor = new FrequencyExecutor(
                    ProxyRepo.class.getSimpleName(),
                    () -> proxyRepo.clean(false),
                    proxyRepoConfig.getCleanupFrequency().toMillis());
            services.add(cleanupRepoExecutor);

            // If we aren't forwarding then don't do anything else other than running repo cleanups.
            if (forwarderConfig.isForwardingEnabled() &&
                    forwarderConfig.getForwardDestinations() != null &&
                    forwarderConfig.getForwardDestinations().size() > 0) {
                final Forwarder forwarder = aggregatorConfig.isEnabled()
                        ? aggregatorForwarderProvider.get()
                        : sourceForwarderProvider.get();
                forwarder.cleanup();

                final ChangeListenerExecutor forwarderExecutor = new ChangeListenerExecutor(
                        Forwarder.class.getSimpleName(),
                        forwarder::forward,
                        100);
                services.add(forwarderExecutor);

                // Setup forwarding retries.
                final ForwardRetryExecutor forwardRetryExecutor = new ForwardRetryExecutor(
                        forwarderConfig.getRetryFrequency(),
                        forwarder,
                        forwarderExecutor);
                services.add(forwardRetryExecutor);

                if (proxyRepoFileScannerConfig.isScanningEnabled()) {
                    // Add executor to scan proxy files from a repo where a repo is not populated by receiving data.
                    final ProxyRepoFileScanner proxyRepoFileScanner = proxyRepoFileScannerProvider.get();
                    final FrequencyExecutor proxyRepoFileScannerExecutor = new FrequencyExecutor(
                            ProxyRepoFileScanner.class.getSimpleName(),
                            proxyRepoFileScanner::scan,
                            proxyRepoFileScannerConfig.getScanFrequency().toMillis());
                    services.add(proxyRepoFileScannerExecutor);
                }

                if (aggregatorConfig.isEnabled()) {
                    // Only examine source files if we are aggregating.
                    final ProxyRepoSources proxyRepoSources = proxyRepoSourcesProvider.get();
                    final ProxyRepoSourceEntries proxyRepoSourceEntries = proxyRepoSourceEntriesProvider.get();
                    final Aggregator aggregator = aggregatorProvider.get();

                    // Add executor to open source files and scan entries
                    final ChangeListenerExecutor proxyRepoSourceEntriesExecutor = new ChangeListenerExecutor(
                            ProxyRepoSourceEntries.class.getSimpleName(),
                            proxyRepoSourceEntries::examine,
                            100);
                    // Aggregate whenever we have new source entries.
                    proxyRepoSources.addChangeListener((source) ->
                            proxyRepoSourceEntriesExecutor.onChange());
                    services.add(proxyRepoSourceEntriesExecutor);

                    // Just kep trying to aggregate based on a frequency and not changes to source entries.
                    final FrequencyExecutor aggregatorExecutor = new FrequencyExecutor(
                            Aggregator.class.getSimpleName(),
                            aggregator::aggregate,
                            aggregatorConfig.getAggregationFrequency().toMillis());
                    // Forward whenever we have new aggregates.
                    aggregator.addChangeListener(count -> forwarderExecutor.onChange());
                    services.add(aggregatorExecutor);

                    final Cleanup cleanup = cleanupProvider.get();
                    final ChangeListenerExecutor cleanupExecutor = new ChangeListenerExecutor(
                            Cleanup.class.getSimpleName(),
                            () -> {
                                cleanup.deleteUnusedSourceEntries();
                                cleanup.deleteUnusedSources();
                            },
                            proxyRepoConfig.getCleanupFrequency().toMillis());
                    // Cleanup whenever we have forwarded data.
                    forwarder.addChangeListener(cleanupExecutor::onChange);
                    services.add(cleanupExecutor);

                    // Proxy repo source entries are extracted using an executor service so remember to shut it down.
                    requireShutdown.add(proxyRepoSourceEntries);

                } else {
                    // Forward when we have a new source to forward.
                    final ProxyRepoSources proxyRepoSources = proxyRepoSourcesProvider.get();
                    proxyRepoSources.addChangeListener((source) ->
                            forwarderExecutor.onChange());

                    final Cleanup cleanup = cleanupProvider.get();
                    final ChangeListenerExecutor cleanupExecutor = new ChangeListenerExecutor(
                            Cleanup.class.getSimpleName(),
                            cleanup::deleteUnusedSources,
                            proxyRepoConfig.getCleanupFrequency().toMillis());
                    // Cleanup whenever we have forwarded data.
                    forwarder.addChangeListener(cleanupExecutor::onChange);
                    services.add(cleanupExecutor);
                }

                // Forwarding is done using an executor service so remember to shut it down.
                requireShutdown.add(forwarder);
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

        // Make sure all other async activity completes and shuts down.
        requireShutdown.forEach(HasShutdown::shutdown);

        // This method is part of DW  Managed which is managed by Jersey so we need to ensure any interrupts
        // are cleared before it goes back to Jersey
        final boolean interrupted = Thread.interrupted();
        LOGGER.debug("Was interrupted = " + interrupted);

        LOGGER.info("Stopped Stroom Proxy");
    }
}
