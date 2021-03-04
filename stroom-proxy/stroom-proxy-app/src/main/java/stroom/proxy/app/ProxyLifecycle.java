package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ChangeListenerExecutor;
import stroom.proxy.repo.Cleanup;
import stroom.proxy.repo.CleanupConfig;
import stroom.proxy.repo.Forwarder;
import stroom.proxy.repo.FrequencyExecutor;
import stroom.proxy.repo.ProxyRepo;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScanner;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.ProxyRepoSourceEntries;
import stroom.proxy.repo.ProxyRepoSources;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class ProxyLifecycle implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyLifecycle.class);

    private final List<Managed> services;
    private final ProxyRepoSourceEntries proxyRepoSourceEntries;
    private final Forwarder forwarder;

    @Inject
    public ProxyLifecycle(final ProxyRepoConfig proxyRepoConfig,
                          final ProxyRepo proxyRepo,
                          final ProxyRepoFileScanner proxyRepoFileScanner,
                          final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig,
                          final ProxyRepoSources proxyRepoSources,
                          final ProxyRepoSourceEntries proxyRepoSourceEntries,
                          final Aggregator aggregator,
                          final AggregatorConfig aggregatorConfig,
                          final Forwarder forwarder,
                          final ForwarderConfig forwarderConfig,
                          final Cleanup cleanup,
                          final CleanupConfig cleanupConfig) {
        this.proxyRepoSourceEntries = proxyRepoSourceEntries;
        this.forwarder = forwarder;

        services = new ArrayList<>();

        if (proxyRepoConfig.isStoringEnabled()) {
            // Create a service to cleanup the repo to remove empty dirs and stale lock files.
            final FrequencyExecutor cleanupRepoExecutor = new FrequencyExecutor(
                    ProxyRepo.class.getSimpleName(),
                    () -> proxyRepo.clean(false),
                    cleanupConfig.getCleanupFrequency().toMillis());
            services.add(cleanupRepoExecutor);

            // Add executor to open source files and scan entries
            final ChangeListenerExecutor proxyRepoSourceEntriesExecutor = new ChangeListenerExecutor(
                    ProxyRepoSourceEntries.class.getSimpleName(),
                    proxyRepoSourceEntries::examine,
                    100);
            proxyRepoSources.addChangeListener((sourceId, sourcePath) -> proxyRepoSourceEntriesExecutor.onChange());
            services.add(proxyRepoSourceEntriesExecutor);

            if (proxyRepoFileScannerConfig.isScanningEnabled()) {
                // Add executor to scan proxy files from a repo where a repo is not populated by receiving data.
                final FrequencyExecutor proxyRepoFileScannerExecutor = new FrequencyExecutor(
                        ProxyRepoFileScanner.class.getSimpleName(),
                        proxyRepoFileScanner::scan,
                        proxyRepoFileScannerConfig.getScanFrequency().toMillis());
                services.add(proxyRepoFileScannerExecutor);
            }

            if (forwarderConfig.isForwardingEnabled() &&
                    forwarderConfig.getForwardDestinations() != null &&
                    forwarderConfig.getForwardDestinations().size() > 0) {
                final FrequencyExecutor aggregatorExecutor = new FrequencyExecutor(
                        Aggregator.class.getSimpleName(),
                        aggregator::aggregate,
                        aggregatorConfig.getAggregationFrequency().toMillis());
                services.add(aggregatorExecutor);

                final ChangeListenerExecutor forwarderExecutor = new ChangeListenerExecutor(
                        Forwarder.class.getSimpleName(),
                        forwarder::forward,
                        100);
                // Forward whenever we have new aggregates.
                aggregator.addChangeListener(count -> forwarderExecutor.onChange());
                services.add(forwarderExecutor);

                final ChangeListenerExecutor cleanupExecutor = new ChangeListenerExecutor(
                        Cleanup.class.getSimpleName(),
                        cleanup::cleanup,
                        cleanupConfig.getCleanupFrequency().toMillis());
                // Cleanup whenever we have forwarded data.
                forwarder.addChangeListener(cleanupExecutor::onChange);
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

        // Make sure all other async activity completes and shuts down.
        proxyRepoSourceEntries.shutdown();
        forwarder.shutdown();

        // This method is part of DW  Managed which is managed by Jersey so we need to ensure any interrupts
        // are cleared before it goes back to Jersey
        final boolean interrupted = Thread.interrupted();
        LOGGER.debug("Was interrupted = " + interrupted);

        LOGGER.info("Stopped Stroom Proxy");
    }
}
