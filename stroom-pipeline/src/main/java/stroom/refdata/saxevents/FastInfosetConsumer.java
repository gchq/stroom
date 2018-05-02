/*
 * Copyright 2016 Crown Copyright
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

package stroom.refdata.saxevents;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.jvnet.fastinfoset.FastInfosetException;
import org.xml.sax.SAXException;
import stroom.util.logging.LambdaLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

class FastInfosetConsumer extends EventListProxyConsumer {

    FastInfosetConsumer(final XPathContext context) {
        super(context);
    }
//    private final SAXDocumentParser saxDocumentParser;

//    private final Map<Integer, Integer> codeMap = new HashMap<>();

//    public FastInfosetConsumer(final Receiver receiver, final PipelineConfiguration pipe) {
//        super(receiver, pipe);
//
//        FastInfosetContentHandler fastInfosetContentHandler = new FastInfosetContentHandler();
//        fastInfosetContentHandler.setPipelineConfiguration(pipe);
//        fastInfosetContentHandler.setReceiver(receiver);
//
//        SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
//        saxDocumentParser.setContentHandler(fastInfosetContentHandler);
//        this.saxDocumentParser = saxDocumentParser;
//    }


//    @Override
//    public void consume(final ValueProxy<EventListValue> eventListProxy) {
//
//        Class valueClazz = eventListProxy.getValueClazz();
//        if (valueClazz != FastInfosetValue.class) {
//            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type {}", valueClazz.getCanonicalName()));
//        }
//        // get the value the proxy is proxying for and use it inside the transaction
//        eventListProxy.consumeValue(byteBuffer -> {
//            ByteBufferInputStream inputStream = new ByteBufferInputStream(byteBuffer);
//            try {
//                saxDocumentParser.parse(inputStream);
//            } catch (IOException | FastInfosetException | SAXException e) {
//                throw new RuntimeException("Error parsing fastinfoset bytes, " + e.getMessage(), e);
//            }
//            saxDocumentParser.reset();
//        });
//    }

    private Sequence convertByteBufferToSequence(final ByteBuffer byteBuffer) {
        // Initialise objects for de-serialising the bytebuffer
        final PipelineConfiguration pipelineConfiguration = buildPipelineConfguration();
        final TinyBuilder receiver = new TinyBuilder(pipelineConfiguration);
        try {
            startDocument(receiver, pipelineConfiguration);
        } catch (XPathException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error starting document"), e);
        }

        final FastInfosetContentHandler fastInfosetContentHandler = new FastInfosetContentHandler();
        fastInfosetContentHandler.setPipelineConfiguration(pipelineConfiguration);
        fastInfosetContentHandler.setReceiver(receiver);

        //TODO should we re-use this saxparser object in some way?
        final SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
        saxDocumentParser.setContentHandler(fastInfosetContentHandler);

        final ByteBufferInputStream inputStream = new ByteBufferInputStream(byteBuffer);
        try {
            // do the parsing which will output to the tinyBuilder
            saxDocumentParser.parse(inputStream);
        } catch (IOException | FastInfosetException | SAXException e) {
            throw new RuntimeException("Error parsing fastinfoset bytes, " + e.getMessage(), e);
        }
        saxDocumentParser.reset();
        try {
            endDocument(receiver);
        } catch (XPathException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error ending document"), e);
        }
        final Sequence sequence = receiver.getCurrentRoot();
        // Reset the builder, detaching it from the constructed document.
        receiver.reset();
        return sequence;
    }


    @Override
    public Sequence map(final ValueProxy<EventListValue> eventListProxy) {
        if (eventListProxy == null) {
            return EmptyAtomicSequence.getInstance();
        }

        Class valueClazz = eventListProxy.getValueClazz();
        if (valueClazz != FastInfosetValue.class) {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type {}", valueClazz.getCanonicalName()));
        }

        // Get the value of the proxy and if found map it
        Optional<Sequence> optSequence = eventListProxy.mapValue(this::convertByteBufferToSequence);

        return optSequence.orElseGet(EmptyAtomicSequence::getInstance);
    }

}
