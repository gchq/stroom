/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.proxy.app.handler.ZipDirScanner;
import stroom.proxy.repo.ProxyServices;
import stroom.receive.common.ReceiptIdGenerator;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ProxyLifecycle implements Managed {

    private final ProxyServices proxyServices;

    @Inject
    public ProxyLifecycle(final ProxyConfig proxyConfig,
                          final Provider<EventStore> eventStoreProvider,
                          final ZipDirScanner zipDirScanner,
                          final ProxyServices proxyServices,
                          final ReceiptIdGenerator receiptIdGenerator) {
        this.proxyServices = proxyServices;
        final EventStoreConfig eventStoreConfig = proxyConfig.getEventStoreConfig();
        final DirScannerConfig dirScannerConfig = proxyConfig.getDirScannerConfig();

        // Add executor to roll event store.
        final EventStore eventStore = eventStoreProvider.get();
        proxyServices.addFrequencyExecutor("Event Store - roll",
                () -> eventStore::tryRoll,
                eventStoreConfig.getRollFrequency().toMillis());

        // Add executor to forward event store.
        proxyServices.addFrequencyExecutor("Event Store - forward",
                () -> eventStore::forwardAll,
                eventStoreConfig.getRollFrequency().toMillis());

        // Add executor to scan dirs for proxy zips.
        proxyServices.addFrequencyExecutor("ZIP Dir Scanner",
                () -> zipDirScanner::scan,
                dirScannerConfig.getScanFrequency().toMillis());

        if (proxyConfig.getSqsConnectors() != null) {
            for (final SqsConnectorConfig sqsConnectorConfig : proxyConfig.getSqsConnectors()) {
                final SqsConnector sqsConnector = new SqsConnector(
                        eventStore, sqsConnectorConfig, receiptIdGenerator);
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
