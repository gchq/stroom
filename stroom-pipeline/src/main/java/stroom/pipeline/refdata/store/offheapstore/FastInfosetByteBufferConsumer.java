package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.RefDataValueByteBufferConsumer;
import stroom.pipeline.refdata.util.ByteBufferUtils;

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

public class FastInfosetByteBufferConsumer implements RefDataValueByteBufferConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FastInfosetByteBufferConsumer.class);

    private final SAXDocumentParser saxDocumentParser;

    public FastInfosetByteBufferConsumer(final Receiver receiver, final PipelineConfiguration pipelineConfiguration) {

        final FastInfosetContentHandler fastInfosetContentHandler = new FastInfosetContentHandler();
        fastInfosetContentHandler.setPipelineConfiguration(pipelineConfiguration);
        fastInfosetContentHandler.setReceiver(receiver);

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
            throw new RuntimeException("Error parsing fastinfoset bytes, "
                    + ByteBufferUtils.byteBufferInfo(inputStream.getByteBuffer()) + " "
                    + e.getMessage(), e);
        }

        saxDocumentParser.reset();
    }

    public static class Factory implements RefDataValueByteBufferConsumer.Factory {

        @Override
        public RefDataValueByteBufferConsumer create(
                final Receiver receiver,
                final PipelineConfiguration pipelineConfiguration) {

            return new FastInfosetByteBufferConsumer(receiver, pipelineConfiguration);
        }
    }
}
