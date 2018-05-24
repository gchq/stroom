package stroom.refdata.offheapstore;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;

import java.util.Map;


class RefDataValueProxyConsumer {

    private final Receiver receiver;
    private final PipelineConfiguration pipelineConfiguration;

//    private final Map<Class<? extends RefDataValue>, RefDataValueByteBufferConsumer> typeToConsumerMap =
//            Maps.

    RefDataValueProxyConsumer(final Receiver receiver, final PipelineConfiguration pipelineConfiguration) {
        this.receiver = receiver;
        this.pipelineConfiguration = pipelineConfiguration;
    }

    public void startDocument() throws XPathException {
        receiver.setPipelineConfiguration(pipelineConfiguration);
        receiver.open();
        receiver.startDocument(0);
    }

    public void endDocument() throws XPathException {
        receiver.endDocument();
        receiver.close();
    }

    public void consume(final RefDataValueProxy refDataValueProxy) throws XPathException {

        //
        refDataValueProxy.consumeBytes(byteBuffer -> {
            byte bTypeId = byteBuffer.get();

            final Class<? extends RefDataValue> valueClass = RefDataValueSerde.determineType(bTypeId);

            //TODO determine which consumer class to use based on the type

            final RefDataValueByteBufferConsumer byteBufferConsumer;


            byteBufferConsumer.consumeBytes(receiver, byteBuffer);
        });
    }

}
