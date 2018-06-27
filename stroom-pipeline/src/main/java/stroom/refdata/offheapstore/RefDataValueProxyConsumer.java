package stroom.refdata.offheapstore;

import com.google.inject.assistedinject.Assisted;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.serdes.RefDataValueSerde;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class RefDataValueProxyConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataValueProxyConsumer.class);

    private final Receiver receiver;
    private final PipelineConfiguration pipelineConfiguration;

    // injected map of typeId to the appropriate bytebuffer consumer factory
    private final Map<Integer, AbstractByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap;
    // map to hold onto the
    private final Map<Integer, RefDataValueByteBufferConsumer> typeToConsumerMap = new HashMap<>();

    @Inject
    public RefDataValueProxyConsumer(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final Map<Integer, AbstractByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap) {
        this.receiver = receiver;
        this.pipelineConfiguration = pipelineConfiguration;
        this.typeToByteBufferConsumerFactoryMap = typeToByteBufferConsumerFactoryMap;

    }

    public void startDocument() throws XPathException {
        LOGGER.trace("startDocument");
        receiver.setPipelineConfiguration(pipelineConfiguration);
        receiver.open();
        receiver.startDocument(0);
    }

    public void endDocument() throws XPathException {
        LOGGER.trace("endDocument");
        receiver.endDocument();
        receiver.close();
    }

    public boolean consume(final RefDataValueProxy refDataValueProxy) throws XPathException {
        LOGGER.trace("consume({})", refDataValueProxy);

        //
        return refDataValueProxy.consumeBytes(byteBuffer -> {

            // find out what type of value we are dealing with
            final Integer typeId = RefDataValueSerde.getTypeId(byteBuffer);

            // work out which byteBufferConsumer to use based on the typeId in the value byteBuffer
            final RefDataValueByteBufferConsumer consumer = typeToConsumerMap.computeIfAbsent(
                    typeId, typeIdKey ->
                            typeToByteBufferConsumerFactoryMap.get(typeIdKey)
                                    .create(receiver, pipelineConfiguration)
            );

            Objects.requireNonNull(consumer, () -> LambdaLogger.buildMessage("No consumer for typeId {}", typeId));

            // now we have the appropriate consumer for the value type, consume the value
            consumer.consumeBytes(receiver, byteBuffer);
        });
    }

    public interface Factory {
        RefDataValueProxyConsumer create(final Receiver receiver,
                                         final PipelineConfiguration pipelineConfiguration);
    }

}
