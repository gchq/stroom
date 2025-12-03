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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.RefDataValueByteBufferConsumer;

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

    public FastInfosetByteBufferConsumer(final Receiver receiver,
                                         final PipelineConfiguration pipelineConfiguration) {

        final FastInfosetContentHandler fastInfosetContentHandler = new FastInfosetContentHandler();
        fastInfosetContentHandler.setPipelineConfiguration(pipelineConfiguration);
        fastInfosetContentHandler.setReceiver(receiver);

        saxDocumentParser = new SAXDocumentParser();
        saxDocumentParser.setContentHandler(fastInfosetContentHandler);
    }

    @Override
    public void consumeBytes(final ByteBuffer byteBuffer) {
        LOGGER.trace("consumeBytes()");
        try (final ByteBufferInputStream inputStream = new ByteBufferInputStream(byteBuffer)) {
            // do the parsing which will output to the tinyBuilder
            saxDocumentParser.parse(inputStream);
        } catch (final IOException | FastInfosetException | SAXException e) {
            throw new RuntimeException("Error parsing fastInfoset bytes, "
                                       + ByteBufferUtils.byteBufferInfo(byteBuffer) + " "
                                       + e.getMessage(), e);
        }
        saxDocumentParser.reset();
    }


    // --------------------------------------------------------------------------------


    public static class Factory implements RefDataValueByteBufferConsumer.Factory {

        @Override
        public RefDataValueByteBufferConsumer create(
                final Receiver receiver,
                final PipelineConfiguration pipelineConfiguration) {

            return new FastInfosetByteBufferConsumer(receiver, pipelineConfiguration);
        }
    }
}
