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

package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ReceiverFactoryProvider implements Provider<ReceiverFactory> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiverFactoryProvider.class);

    private ReceiverFactory receiverFactory;
    private ThreadConfig threadConfig;

    @Inject
    public ReceiverFactoryProvider(final ProxyConfig proxyConfig,
                                   final Provider<InstantForwardHttpPost> instantForwardHttpPostProvider,
                                   final Provider<InstantForwardFile> instantForwardFileProvider,
                                   final Provider<Forwarder> forwarderProvider,
                                   final DirQueueFactory dirQueueFactory,
                                   final Provider<Aggregator> aggregatorProvider,
                                   final Provider<PreAggregator> preAggregatorProvider,
                                   final Provider<ZipReceiver> zipReceiverProvider,
                                   final Provider<SimpleReceiver> simpleReceiverProvider,
                                   final ProxyServices proxyServices) {
        this.threadConfig = proxyConfig.getThreadConfig();
        // TODO we should really be creating all forwarders regardless of state, so that
        //  they can be initialised in a paused state, then respond to a change to the enabled
        //  state. This is subject to fixing the hot loading of forwarder config changes.
        // Find out how many forward destinations are enabled.
        final long enabledForwardCount = proxyConfig.streamAllEnabledForwarders()
                .count();
        // Find out how many forward destinations are set for instant forwarding.
        final long enabledInstantForwardCount = proxyConfig.streamAllEnabledForwarders()
                .filter(ForwarderConfig::isInstant)
                .count();

        // Config validation should pick these things up, but just in case
        if (enabledForwardCount == 0) {
            throw new RuntimeException("No enabled forward destinations are configured.");
        }

        if (enabledInstantForwardCount > 0) {
            if (enabledForwardCount > 1) {
                throw new RuntimeException(LogUtil.message("At least one forward destination is set as " +
                                                           "instant=true. You cannot have other enabled forward " +
                                                           "destinations when using instant forwarding."));
            }

            createInstantForwarders(proxyConfig, instantForwardHttpPostProvider, instantForwardFileProvider);
        } else {
            // Create forwarder.
            final Forwarder forwarder = forwarderProvider.get();
            // Create the forwarding queue.
            final DirQueue forwardInputQueue = dirQueueFactory.create(
                    DirNames.FORWARDING_INPUT_QUEUE,
                    40,
                    "Forwarding Input Queue");
            // Move items from the forwarding queue to the forwarder(s).
            final DirQueueTransfer forwardingInputQueueTransfer =
                    new DirQueueTransfer(forwardInputQueue::next, forwarder::add);
            proxyServices.addParallelExecutor("Forwarding queue transfer", () ->
                    forwardingInputQueueTransfer, threadConfig.getForwardingInputQueueThreadCount());

            if (NullSafe.test(proxyConfig.getAggregatorConfig(), AggregatorConfig::isEnabled)) {
                // If we are aggregating then create the aggregating moving parts.
                createAggregatingReceiverFactory(
                        dirQueueFactory,
                        aggregatorProvider,
                        preAggregatorProvider,
                        zipReceiverProvider,
                        simpleReceiverProvider,
                        proxyServices,
                        forwardInputQueue);
            } else {
                // If we aren't aggregating then we just need to queue items for forwarding.
                createNonAggregatingReceiverFactory(
                        zipReceiverProvider,
                        simpleReceiverProvider,
                        forwardInputQueue);
            }
        }
    }

    private void createNonAggregatingReceiverFactory(final Provider<ZipReceiver> zipReceiverProvider,
                                                     final Provider<SimpleReceiver> simpleReceiverProvider,
                                                     final DirQueue forwardInputQueue) {
        // Create the receivers that will add data to the forward queue on receipt.
        final SimpleReceiver simpleReceiver = simpleReceiverProvider.get();
        simpleReceiver.setDestination(forwardInputQueue::add);
        final ZipReceiver zipReceiver = zipReceiverProvider.get();
        zipReceiver.setDestination(forwardInputQueue::add);

        receiverFactory = new StoringReceiverFactory(simpleReceiver, zipReceiver);
    }

    private void createAggregatingReceiverFactory(final DirQueueFactory dirQueueFactory,
                                                  final Provider<Aggregator> aggregatorProvider,
                                                  final Provider<PreAggregator> preAggregatorProvider,
                                                  final Provider<ZipReceiver> zipReceiverProvider,
                                                  final Provider<SimpleReceiver> simpleReceiverProvider,
                                                  final ProxyServices proxyServices,
                                                  final DirQueue forwardInputQueue) {
        // Create the aggregator.
        final Aggregator aggregator = aggregatorProvider.get();
        aggregator.setDestination(forwardInputQueue::add);

        final DirQueue aggregateInputQueue = dirQueueFactory.create(
                DirNames.AGGREGATE_INPUT_QUEUE,
                30,
                "Aggregate Input Queue");
        // Move items from the pre aggregate queue to the aggregator.
        // TODO : Could use more than one thread here.
        final DirQueueTransfer aggregateInputQueueTransfer =
                new DirQueueTransfer(aggregateInputQueue::next, aggregator::addDir);
        proxyServices.addParallelExecutor(
                "Aggregate input queue transfer",
                () -> aggregateInputQueueTransfer,
                threadConfig.getAggregateInputQueueThreadCount());

        // Create the pre aggregator.
        final PreAggregator preAggregator = preAggregatorProvider.get();
        preAggregator.setDestination(aggregateInputQueue::add);

        final DirQueue preAggregateInputQueue = dirQueueFactory.create(
                DirNames.PRE_AGGREGATE_INPUT_QUEUE,
                20,
                "Pre Aggregate Input Queue");
        // Move items from the file store to the pre aggregator.
        final DirQueueTransfer preAggregateInputQueueTransfer =
                new DirQueueTransfer(preAggregateInputQueue::next, preAggregator::addDir);
        proxyServices.addParallelExecutor(
                "Pre aggregate input queue transfer",
                () -> preAggregateInputQueueTransfer,
                threadConfig.getPreAggregateInputQueueThreadCount());

        // Create the receivers that will add data to the file store queue on receipt.
        final SimpleReceiver simpleReceiver = simpleReceiverProvider.get();
        simpleReceiver.setDestination(preAggregateInputQueue::add);
        final ZipReceiver zipReceiver = zipReceiverProvider.get();
        zipReceiver.setDestination(preAggregateInputQueue::add);

        receiverFactory = new StoringReceiverFactory(simpleReceiver, zipReceiver);
    }

    private void createInstantForwarders(final ProxyConfig proxyConfig,
                                         final Provider<InstantForwardHttpPost> instantForwardHttpPostProvider,
                                         final Provider<InstantForwardFile> instantForwardFileProvider) {

        final List<ForwarderConfig> instantForwarders = proxyConfig.streamAllEnabledForwarders()
                .filter(ForwarderConfig::isInstant)
                .toList();
        if (instantForwarders.size() != 1) {
            throw new RuntimeException("Expecting one enabled instant forwarder");
        }

        final ForwarderConfig forwarderConfig = instantForwarders.getFirst();
        receiverFactory = switch (forwarderConfig) {
            case final ForwardHttpPostConfig forwardHttpPostConfig -> instantForwardHttpPostProvider.get()
                    .get(forwardHttpPostConfig);
            case final ForwardFileConfig forwardFileConfig -> instantForwardFileProvider.get()
                    .get(forwardFileConfig);
        };
    }

    @Override
    public ReceiverFactory get() {
        return receiverFactory;
    }
}
