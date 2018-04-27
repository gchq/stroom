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
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.trans.XPathException;
import org.jvnet.fastinfoset.FastInfosetException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

//public class FastInfosetConsumer extends DefaultHandler {
public class FastInfosetConsumer {
    private static final Location NULL_LOCATION = new NullLocation();

    private static final String EMPTY = "";
    private final Receiver receiver;
    private final PipelineConfiguration pipe;
    private final SAXDocumentParser saxDocumentParser;
//    private final NamePool pool;

    private final Map<Integer, Integer> codeMap = new HashMap<>();

    public FastInfosetConsumer(final Receiver receiver, final PipelineConfiguration pipe) {
        this.receiver = receiver;
        this.pipe = pipe;

        FastInfosetContentHandler fastInfosetContentHandler = new FastInfosetContentHandler();
        fastInfosetContentHandler.setPipelineConfiguration(pipe);
        fastInfosetContentHandler.setReceiver(receiver);

//        pool = pipe.getConfiguration().getNamePool();
        SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
        saxDocumentParser.setContentHandler(fastInfosetContentHandler);
        this.saxDocumentParser = saxDocumentParser;
    }

    public void start() throws XPathException {
        receiver.setPipelineConfiguration(pipe);
        receiver.open();
        receiver.startDocument(0);
    }

    public void end() throws XPathException {
        receiver.endDocument();
        receiver.close();
    }


//    @Override
//    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
//        NodeInfo nodeInfo = new N
////        receiver.startElement();
//
//    }
//
//    @Override
//    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
//
//    }
//
//    @Override
//    public void characters(final char[] ch, final int start, final int length) throws SAXException {
//
//    }


    public void consume(final ByteBuffer byteBuffer) throws FastInfosetException, SAXException, IOException {

        ByteBufferInputStream inputStream = new ByteBufferInputStream(byteBuffer);
        saxDocumentParser.parse(inputStream);
        saxDocumentParser.reset();
    }

    private static class NullLocation implements Location {
        @Override
        public String getSystemId() {
            return null;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public int getLineNumber() {
            return 0;
        }

        @Override
        public int getColumnNumber() {
            return 0;
        }

        @Override
        public Location saveLocation() {
            return this;
        }
    }

}
