package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.receive.common.AttributeMapFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import javax.inject.Inject;

public class InstantForwardHttpPost {

    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final HttpSenderFactory httpSenderFactory;
    private final DropReceiver dropReceiver;

    @Inject
    public InstantForwardHttpPost(final AttributeMapFilterFactory attributeMapFilterFactory,
                                  final HttpSenderFactory httpSenderFactory,
                                  final DropReceiver dropReceiver) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.httpSenderFactory = httpSenderFactory;
        this.dropReceiver = dropReceiver;
    }

    public ReceiverFactory get(final ForwardHttpPostConfig forwardHttpPostConfig) {
        // Create a direct forwarding HTTP POST receiver.
        final HttpSender httpSender = httpSenderFactory.create(forwardHttpPostConfig);
        final DirectForwardHttpPostReceiver directForwardHttpPostReceiver =
                new DirectForwardHttpPostReceiver(httpSender);
        return new DirectForwardHttpPostReceiverFactory(
                attributeMapFilterFactory.create(),
                directForwardHttpPostReceiver,
                dropReceiver);
    }

    private static class DirectForwardHttpPostReceiverFactory implements ReceiverFactory {

        private final AttributeMapFilter attributeMapFilter;
        private final DirectForwardHttpPostReceiver receiver;
        private final DropReceiver dropReceiver;

        public DirectForwardHttpPostReceiverFactory(final AttributeMapFilter attributeMapFilter,
                                                    final DirectForwardHttpPostReceiver receiver,
                                                    final DropReceiver dropReceiver) {
            this.attributeMapFilter = attributeMapFilter;
            this.receiver = receiver;
            this.dropReceiver = dropReceiver;
        }

        @Override
        public Receiver get(final AttributeMap attributeMap) {
            if (attributeMapFilter.filter(attributeMap)) {
                return receiver;
            } else {
                return dropReceiver;
            }
        }
    }

    private static class DirectForwardHttpPostReceiver implements Receiver {

        private final HttpSender httpSender;

        public DirectForwardHttpPostReceiver(final HttpSender httpSender) {
            this.httpSender = httpSender;
        }

        @Override
        public void receive(final Instant startTime,
                            final AttributeMap attributeMap,
                            final String requestUri,
                            final InputStreamSupplier inputStreamSupplier) {
            try {
                httpSender.send(attributeMap, inputStreamSupplier.get());
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
