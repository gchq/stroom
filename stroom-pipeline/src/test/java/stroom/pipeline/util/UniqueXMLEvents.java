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

package stroom.pipeline.util;

import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ElementId;
import stroom.util.xml.SAXParserFactoryFactory;
import stroom.util.xml.XMLUtil;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

public class UniqueXMLEvents {

    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    public static void main(final String[] args) {
        if (args.length != 2) {
            System.out.println("Bad arguments - provide input and output XSD files.");
        }

        final Path inputXsd = Paths.get(args[0]);
        final Path outputXsd = Paths.get(args[1]);

        try (final Reader reader = Files.newBufferedReader(inputXsd, StreamUtil.DEFAULT_CHARSET);
                final Writer writer = Files.newBufferedWriter(outputXsd, StreamUtil.DEFAULT_CHARSET)) {
            final TransformerHandler th = XMLUtil.createTransformerHandler(true);
            th.setResult(new StreamResult(writer));

            final SAXParser parser;
            try {
                parser = PARSER_FACTORY.newSAXParser();
            } catch (final ParserConfigurationException e) {
                throw ProcessException.wrap(e);
            }

            final UniqueEventFilter filter = new UniqueEventFilter();
            filter.setContentHandler(th);

            final LocationFactory locationFactory = new DefaultLocationFactory();
            final ErrorHandlerAdaptor errorHandler = new ErrorHandlerAdaptor(
                    new ElementId("XMLReader"), locationFactory, new FatalErrorReceiver());
            final XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setContentHandler(filter);
            xmlReader.setErrorHandler(errorHandler);
            xmlReader.parse(new InputSource(reader));

        } catch (final SAXException | TransformerConfigurationException | IOException e) {
            throw ProcessException.wrap(e);
        }
    }

    private static class UniqueEventFilter extends BufferFilter {

        private final StringBuilder content = new StringBuilder();
        private final Set<String> idSet = new HashSet<>();
        private boolean duplicate = false;

        @Override
        public void startDocument() throws SAXException {
            startBuffer();
            super.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            super.endDocument();
            stopBuffer(getContentHandler());
        }

        @Override
        public void startElement(final String uri, final String localName, final String name, final Attributes atts)
                throws SAXException {
            outputChars();

            super.startElement(uri, localName, name, atts);
        }

        @Override
        public void endElement(final String uri, final String localName, final String name) throws SAXException {
            if (localName.equals("EventID")) {
                final String id = content.toString().trim();
                if (idSet.contains(id)) {
                    duplicate = true;
                } else {
                    idSet.add(id);
                }
            }

            outputChars();
            super.endElement(uri, localName, name);

            if (localName.equals("Event")) {
                if (duplicate) {
                    duplicate = false;
                } else {
                    stopBuffer(getContentHandler());
                }

                startBuffer();
            }
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) {
            content.append(ch, start, length);
        }

        private void outputChars() throws SAXException {
            final char[] ch = content.toString().toCharArray();
            super.characters(ch, 0, ch.length);
            content.setLength(0);
        }
    }
}
