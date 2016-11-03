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

package stroom.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import stroom.util.xml.SAXParserFactoryFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import stroom.entity.server.util.XMLUtil;
import stroom.pipeline.server.DefaultLocationFactory;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.server.errorhandler.FatalErrorReceiver;
import stroom.pipeline.server.errorhandler.ProcessException;

public class UniqueXMLEvents {
    private static final SAXParserFactory PARSER_FACTORY;

	static {
		PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
		PARSER_FACTORY.setNamespaceAware(true);
	}

    public static void main(final String[] args) {
        try {
            if (args.length != 2) {
                System.out.println("Bad arguments - provide input and output XSD files.");
            }

            final File inputXsd = new File(args[0]);
            final File outputXsd = new File(args[1]);

            final TransformerHandler th = XMLUtil.createTransformerHandler(true);
            th.setResult(new StreamResult(new FileOutputStream(outputXsd)));

            SAXParser parser = null;
            try {
                parser = PARSER_FACTORY.newSAXParser();
            } catch (final ParserConfigurationException e) {
                throw ProcessException.wrap(e);
            }

            final UniqueEventFilter filter = new UniqueEventFilter();
            filter.setContentHandler(th);

            final LocationFactory locationFactory = new DefaultLocationFactory();
            final ErrorHandlerAdaptor errorHandler = new ErrorHandlerAdaptor("XMLReader", locationFactory,
                    new FatalErrorReceiver());
            final XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setContentHandler(filter);
            xmlReader.setErrorHandler(errorHandler);
            xmlReader.parse(new InputSource(new InputStreamReader(new FileInputStream(inputXsd), "UTF8")));

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
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            content.append(ch, start, length);
        }

        private void outputChars() throws SAXException {
            final char[] ch = content.toString().toCharArray();
            super.characters(ch, 0, ch.length);
            content.setLength(0);
        }
    }
}
