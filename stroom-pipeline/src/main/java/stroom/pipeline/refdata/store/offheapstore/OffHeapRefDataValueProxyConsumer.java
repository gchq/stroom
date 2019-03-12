package stroom.pipeline.refdata.store.offheapstore;

import com.google.inject.assistedinject.Assisted;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.refdata.RefDataValueByteBufferConsumer;
import stroom.pipeline.refdata.store.AbstractConsumer;
import stroom.pipeline.refdata.store.ByteBufferConsumerId;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.util.logging.LambdaLogUtil;

import javax.inject.Inject;
import java.util.Map;
import java.util.Objects;


public class OffHeapRefDataValueProxyConsumer
        extends AbstractConsumer
        implements RefDataValueProxyConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OffHeapRefDataValueProxyConsumer.class);

    // injected map of typeId to the appropriate bytebuffer consumer factory
    private final Map<ByteBufferConsumerId, RefDataValueByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap;

    @Inject
    public OffHeapRefDataValueProxyConsumer(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final Map<ByteBufferConsumerId, RefDataValueByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap) {

        super(pipelineConfiguration, receiver);
        this.typeToByteBufferConsumerFactoryMap = typeToByteBufferConsumerFactoryMap;

    }

    @Override
    public boolean consume(final RefDataValueProxy refDataValueProxy) {
        LOGGER.trace("consume({})", refDataValueProxy);

        //
        return refDataValueProxy.consumeBytes(typedByteBuffer -> {

            // find out what type of value we are dealing with
            final int typeId = typedByteBuffer.getTypeId();

            // work out which byteBufferConsumer to use based on the typeId in the value byteBuffer
            final RefDataValueByteBufferConsumer.Factory consumerFactory = typeToByteBufferConsumerFactoryMap.get(new ByteBufferConsumerId(typeId));

            Objects.requireNonNull(consumerFactory, LambdaLogUtil.message("No factory found for typeId {}", typeId));
            final RefDataValueByteBufferConsumer consumer = consumerFactory.create(receiver, pipelineConfiguration);

            Objects.requireNonNull(consumer, LambdaLogUtil.message("No consumer for typeId {}", typeId));

            // now we have the appropriate consumer for the value type, consume the value
            consumer.consumeBytes(receiver, typedByteBuffer.getByteBuffer());
        });
    }

    public interface Factory {
        OffHeapRefDataValueProxyConsumer create(final Receiver receiver,
                                                final PipelineConfiguration pipelineConfiguration);
    }

}
