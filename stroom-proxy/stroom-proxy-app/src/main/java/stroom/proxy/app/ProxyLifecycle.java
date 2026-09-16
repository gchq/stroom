/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app;

import stroom.proxy.app.event.EventStore;
import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.app.execution.Phase;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.guice.ProxyCoreModule;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.handler.ZipDirScanner;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineRuntime;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.security.api.CommonSecurityContext;
import stroom.security.common.impl.RefreshManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;

/**
 * The proxy's Dropwizard {@code Managed}: on start, start the token refresh the forwarders call
 * into, assemble the pipeline - which registers its consumer loops - and start the work registry;
 * on stop, stop the registry, close the runtime's queues and stores, and only then stop the token
 * refresh. Every background thread in the proxy is the registry's, started and stopped in the
 * order the data flows ({@code designs/infrastructure/execution.md}).
 * <p>
 * The token refresh is started and stopped here rather than as a {@code Managed} bean of its own
 * because Dropwizard's order for those is alphabetical by class name, which is not an order.
 * </p>
 */
public class ProxyLifecycle implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyLifecycle.class);

    private final WorkRegistry workRegistry;
    private final EventStore eventStore;
    private final RemoteFeedStatusService remoteFeedStatusService;
    private final RefreshManager refreshManager;
    private final Provider<ProxyPipelineAssembler> pipelineAssemblerProvider;
    private final List<EventStoreSqsConnector> sqsConnectors = new ArrayList<>();
    private final boolean instantForwarding;
    private volatile ProxyPipelineRuntime runtime;

    @Inject
    public ProxyLifecycle(final ProxyConfig proxyConfig,
                          final Provider<EventStore> eventStoreProvider,
                          final ZipDirScanner zipDirScanner,
                          final WorkRegistry workRegistry,
                          final ReceiptIdGenerator receiptIdGenerator,
                          final CommonSecurityContext securityContext,
                          final RemoteFeedStatusService remoteFeedStatusService,
                          final RefreshManager refreshManager,
                          final Provider<ProxyPipelineAssembler> pipelineAssemblerProvider) {
        this.workRegistry = workRegistry;
        this.remoteFeedStatusService = remoteFeedStatusService;
        this.refreshManager = refreshManager;
        this.pipelineAssemblerProvider = pipelineAssemblerProvider;
        this.instantForwarding = ProxyCoreModule.isInstantForwarding(proxyConfig);

        final EventStoreConfig eventStoreConfig = proxyConfig.getEventStoreConfig();
        final DirScannerConfig dirScannerConfig = proxyConfig.getDirScannerConfig();
        this.eventStore = eventStoreProvider.get();

        // Sources feed the pipeline, so they stop first and start last. Within the phase the SQS
        // pollers feed the event store, whose roll - which receives what they wrote - is registered
        // after them.
        workRegistry.schedule("zip-dir-scanner", Phase.INGRESS,
                dirScannerConfig.getScanFrequency().getDuration(), zipDirScanner::scan);
        if (proxyConfig.getSqsConnectors() != null) {
            for (final SqsConnectorConfig sqsConnectorConfig : proxyConfig.getSqsConnectors()) {
                final EventStoreSqsConnector connector = new EventStoreSqsConnector(
                        eventStore, sqsConnectorConfig, receiptIdGenerator, securityContext);
                sqsConnectors.add(connector);
                workRegistry.schedule("sqs-poll", Phase.INGRESS,
                        sqsConnectorConfig.getPollFrequency().getDuration(), connector::poll);
            }
        }
        workRegistry.schedule("event-store-roll", Phase.INGRESS,
                eventStoreConfig.getRollFrequency().getDuration(), eventStore::tryRoll);
        // Receipt reads what is cached; this keeps it fresh, and stops after everything that reads it.
        workRegistry.schedule("feed-status-refresh", Phase.HOUSEKEEPING,
                RemoteFeedStatusService.REFRESH_INTERVAL, remoteFeedStatusService::refreshStale);
    }

    @Override
    public void start() throws Exception {
        // What the forwarders call into is up before anything can call it.
        refreshManager.start();
        if (instantForwarding) {
            // Instant forwarding relays data straight to the destination during receipt; there is
            // nothing for the pipeline to do and no queue or store should be created for it.
            LOGGER.info("Instant forwarding is configured - the pipeline is not assembled");
        } else {
            LOGGER.info("Assembling the pipeline");
            runtime = pipelineAssemblerProvider.get().getRuntime();
        }
        workRegistry.start();
    }

    @Override
    public void stop() {
        workRegistry.stop();
        // Every thread that wrote to or rolled the event store has now stopped.
        sqsConnectors.forEach(EventStoreSqsConnector::close);
        eventStore.close();
        final ProxyPipelineRuntime toClose = runtime;
        if (toClose != null) {
            // Nothing depends on this running (R8); it releases group memberships, clients and
            // their threads promptly rather than at process exit.
            try {
                toClose.close();
            } catch (final Exception e) {
                LOGGER.error(() -> "Error closing the pipeline runtime: " + e.getMessage(), e);
            }
        }
        // Nothing is left that could call it.
        try {
            refreshManager.stop();
        } catch (final Exception e) {
            LOGGER.error(() -> "Error stopping the refresh manager: " + e.getMessage(), e);
        }
    }
}
