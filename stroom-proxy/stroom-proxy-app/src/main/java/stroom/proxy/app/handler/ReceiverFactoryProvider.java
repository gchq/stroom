package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.util.NullSafe;

import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ReceiverFactoryProvider implements Provider<ReceiverFactory> {

    private ReceiverFactory receiverFactory;

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
        // Find out how many forward destinations are enabled.
        final long enabledForwardCount = Stream
                .concat(NullSafe.list(proxyConfig.getForwardHttpDestinations())
                                .stream()
                                .filter(ForwardHttpPostConfig::isEnabled),
                        NullSafe.list(proxyConfig.getForwardFileDestinations())
                                .stream()
                                .filter(ForwardFileConfig::isEnabled))
                .count();
        // Find out how many forward destinations are set for instant forwarding.
        final long instantForwardCount = Stream
                .concat(NullSafe.list(proxyConfig.getForwardHttpDestinations())
                                .stream()
                                .filter(ForwardHttpPostConfig::isInstant),
                        NullSafe.list(proxyConfig.getForwardFileDestinations())
                                .stream()
                                .filter(ForwardFileConfig::isInstant))
                .count();

        if (enabledForwardCount == 0) {
            throw new RuntimeException("No forward destinations are configured.");
        }

        if (instantForwardCount > 0) {
            if (enabledForwardCount > 1) {
                throw new RuntimeException("Storing is not enabled but more than one forward destination is " +
                        "configured.");
            }

            // See if we can create a direct HTTP POST forwarding receiver.
            if (proxyConfig.getForwardHttpDestinations() != null) {
                final Optional<ForwardHttpPostConfig> optional = proxyConfig
                        .getForwardHttpDestinations()
                        .stream()
                        .filter(ForwardHttpPostConfig::isEnabled)
                        .findFirst();
                // Create a direct forwarding HTTP POST receiver.
                optional.ifPresent(forwardHttpPostConfig ->
                        receiverFactory = instantForwardHttpPostProvider.get().get(forwardHttpPostConfig));
            }

            // See if we can create a direct file forwarding receiver.
            if (proxyConfig.getForwardFileDestinations() != null) {
                final Optional<ForwardFileConfig> optional = proxyConfig
                        .getForwardFileDestinations()
                        .stream()
                        .filter(ForwardFileConfig::isEnabled)
                        .findFirst();
                if (optional.isPresent()) {
                    // Create a direct forwarding file receiver.
                    optional.ifPresent(forwardFileConfig ->
                            receiverFactory = instantForwardFileProvider.get().get(forwardFileConfig));
                }
            }
        } else {
            // Create forwarder.
            final Forwarder forwarder = forwarderProvider.get();
            // Create the forwarding queue.
            final DirQueue forwardInputQueue = dirQueueFactory.create(
                    DirNames.FORWARDING_INPUT_QUEUE,
                    40,
                    "Forwarding Input Queue");
            // Move items from the forwarding queue to the forwarder.
            final DirQueueTransfer forwardingInputQueueTransfer =
                    new DirQueueTransfer(forwardInputQueue::next, forwarder::add);
            proxyServices.addParallelExecutor("Forwarding queue transfer", () ->
                    forwardingInputQueueTransfer, 1);

            if (proxyConfig.getAggregatorConfig() != null && proxyConfig.getAggregatorConfig().isEnabled()) {
                // If we are aggregating then create the aggregating moving parts.

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
                proxyServices.addParallelExecutor("Aggregate input queue transfer", () ->
                        aggregateInputQueueTransfer, 1);

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
                proxyServices.addParallelExecutor("Pre aggregate input queue transfer", () ->
                        preAggregateInputQueueTransfer, 1);

                // Create the receivers that will add data to the file store queue on receipt.
                final SimpleReceiver simpleReceiver = simpleReceiverProvider.get();
                simpleReceiver.setDestination(preAggregateInputQueue::add);
                final ZipReceiver zipReceiver = zipReceiverProvider.get();
                zipReceiver.setDestination(preAggregateInputQueue::add);

                receiverFactory = new StoringReceiverFactory(simpleReceiver, zipReceiver);

            } else {
                // If we aren't aggregating then we just need to queue items for forwarding.

                // Create the receivers that will add data to the forward queue on receipt.
                final SimpleReceiver simpleReceiver = simpleReceiverProvider.get();
                simpleReceiver.setDestination(forwardInputQueue::add);
                final ZipReceiver zipReceiver = zipReceiverProvider.get();
                zipReceiver.setDestination(forwardInputQueue::add);

                receiverFactory = new StoringReceiverFactory(simpleReceiver, zipReceiver);
            }
        }
    }

    @Override
    public ReceiverFactory get() {
        return receiverFactory;
    }
}
