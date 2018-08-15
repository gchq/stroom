package stroom.refdata.offheapstore;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import org.jvnet.fastinfoset.FastInfosetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.ByteBuffer;

public class FastInfosetByteBufferConsumer extends AbstractByteBufferConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FastInfosetByteBufferConsumer.class);

    private final SAXDocumentParser saxDocumentParser;

    FastInfosetByteBufferConsumer(final Receiver receiver, final PipelineConfiguration pipelineConfiguration) {
        super(receiver);

        final FastInfosetContentHandler fastInfosetContentHandler = new FastInfosetContentHandler();
        fastInfosetContentHandler.setPipelineConfiguration(pipelineConfiguration);
        fastInfosetContentHandler.setReceiver(super.getReceiver());

        //TODO should we re-use this saxparser object in some way? Ctor looks fairly cheap so prob not worth the bother
        saxDocumentParser = new SAXDocumentParser();
        saxDocumentParser.setContentHandler(fastInfosetContentHandler);
    }

    @Override
    public void consumeBytes(final Receiver receiver, final ByteBuffer byteBuffer) {
        LOGGER.trace("consumeBytes()");
        final ByteBufferInputStream inputStream = new ByteBufferInputStream(byteBuffer);
        try {
            // do the parsing which will output to the tinyBuilder
            saxDocumentParser.parse(inputStream);
        } catch (IOException | FastInfosetException | SAXException e) {
            throw new RuntimeException("Error parsing fastinfoset bytes, " + e.getMessage(), e);
        }

        //TODO do we need to reset this here
        saxDocumentParser.reset();
    }

    public static class Factory implements AbstractByteBufferConsumer.Factory {

        @Override
        public RefDataValueByteBufferConsumer create(
                final Receiver receiver,
                final PipelineConfiguration pipelineConfiguration) {

            return new FastInfosetByteBufferConsumer(receiver, pipelineConfiguration);
        }
    }
}
