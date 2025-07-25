package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

public class InstantForwardHttpPost {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InstantForwardHttpPost.class);

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
        final InstantForwardHttpPostReceiver directForwardHttpPostReceiver =
                new InstantForwardHttpPostReceiver(httpSender);
        return new InstantForwardHttpPostReceiverFactory(
                attributeMapFilterFactory.create(),
                directForwardHttpPostReceiver,
                dropReceiver);
    }


    // --------------------------------------------------------------------------------


    private static class InstantForwardHttpPostReceiverFactory implements ReceiverFactory {

        private final AttributeMapFilter attributeMapFilter;
        private final InstantForwardHttpPostReceiver receiver;
        private final DropReceiver dropReceiver;

        public InstantForwardHttpPostReceiverFactory(final AttributeMapFilter attributeMapFilter,
                                                     final InstantForwardHttpPostReceiver receiver,
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


    // --------------------------------------------------------------------------------


    private static class InstantForwardHttpPostReceiver implements Receiver {

        private final HttpSender httpSender;

        public InstantForwardHttpPostReceiver(final HttpSender httpSender) {
            this.httpSender = httpSender;
        }

        @Override
        public void receive(final Instant startTime,
                            final AttributeMap attributeMap,
                            final String requestUri,
                            final InputStreamSupplier inputStreamSupplier) {
            try {
                httpSender.send(attributeMap, inputStreamSupplier.get());
            } catch (final ForwardException e) {
                throw new RuntimeException(e);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
