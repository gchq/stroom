package stroom.refdata.store.offheapstore;

import com.google.inject.assistedinject.Assisted;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.RefDataValueByteBufferConsumer;
import stroom.refdata.store.AbstractConsumer;
import stroom.refdata.store.RefDataValueProxy;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import java.util.Map;
import java.util.Objects;


public class OffHeapRefDataValueProxyConsumer
        extends AbstractConsumer
        implements RefDataValueProxyConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OffHeapRefDataValueProxyConsumer.class);

    // injected map of typeId to the appropriate bytebuffer consumer factory
    private final Map<Integer, RefDataValueByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap;

    @Inject
    public OffHeapRefDataValueProxyConsumer(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final Map<Integer, RefDataValueByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap) {

        super(pipelineConfiguration, receiver);
        this.typeToByteBufferConsumerFactoryMap = typeToByteBufferConsumerFactoryMap;

    }

    @Override
    public boolean consume(final RefDataValueProxy refDataValueProxy) throws XPathException {
        LOGGER.trace("consume({})", refDataValueProxy);

        //
        return refDataValueProxy.consumeBytes(typedByteBuffer -> {

            // find out what type of value we are dealing with
            final int typeId = typedByteBuffer.getTypeId();

            // work out which byteBufferConsumer to use based on the typeId in the value byteBuffer
            final RefDataValueByteBufferConsumer.Factory consumerFactory = typeToByteBufferConsumerFactoryMap.get(typeId);

            Objects.requireNonNull(consumerFactory, () -> LambdaLogger.buildMessage("No factory found for typeId {}", typeId));
            final RefDataValueByteBufferConsumer consumer = consumerFactory.create(receiver, pipelineConfiguration);

            Objects.requireNonNull(consumer, () -> LambdaLogger.buildMessage("No consumer for typeId {}", typeId));

            // now we have the appropriate consumer for the value type, consume the value
            consumer.consumeBytes(receiver, typedByteBuffer.getByteBuffer());
        });
    }

    public interface Factory {
        OffHeapRefDataValueProxyConsumer create(final Receiver receiver,
                                                final PipelineConfiguration pipelineConfiguration);
    }

}
