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

package stroom.xml.converter.xmlfragment;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import stroom.util.logging.StroomLogger;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.xml.converter.AbstractParser;
import stroom.util.xml.SAXParserFactoryFactory;

public class XMLFragmentParser extends AbstractParser {
	private static final StroomLogger LOGGER = StroomLogger.getLogger(XMLFragmentParser.class);

	private static final SAXParserFactory PARSER_FACTORY;

    private final String xml;

	static {
		PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
		PARSER_FACTORY.setNamespaceAware(true);
	}

    public XMLFragmentParser(final String xml) {
        this.xml = xml;
    }

	@Override
	public void parse(final InputSource input) throws IOException, SAXException {
		SAXParser parser = null;
		try {
			parser = PARSER_FACTORY.newSAXParser();
		} catch (final ParserConfigurationException e) {
			throw ProcessException.wrap(e);
		}
		final XMLReader xmlReader = parser.getXMLReader();
		xmlReader.setEntityResolver(new FragmentEntity(input));
		xmlReader.setContentHandler(getContentHandler());
		xmlReader.setErrorHandler(getErrorHandler());
		try {
			// This fragment parser wraps fragments by means of entity resolution in the
			// outer xml. As a result it then also scans the inner xml for entities.
			// If the inner xml is large and contains large amounts of text then it can blow
			// Limit.TOTAL_ENTITY_SIZE_LIMIT. Turning off FEATURE_SECURE_PROCESSING prevents this limit check.

			// TODO It may be preferable to change the way fragment wrapper works so that it doesn't use
			// entity resolution to acheive its goal, as the scanning for entities must add a fair
			// amount of overhead. A simpler and more crude approach may be better.
			xmlReader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
		} catch (SAXNotRecognizedException | SAXNotSupportedException e) {
			LOGGER.error("Unable to disable FEATURE_SECURE_PROCESSING on the SAX PARSER", e);
		}

        final InputSource inputSource = new InputSource(new StringReader(xml));
        inputSource.setEncoding("UTF-8");
        xmlReader.parse(inputSource);
    }

    private class FragmentEntity implements EntityResolver {
        private static final String FRAGMENT = "fragment";
        private final InputSource fragment;

        public FragmentEntity(final InputSource fragment) {
            this.fragment = fragment;
        }

        @Override
        public InputSource resolveEntity(final String publicId, final String systemId)
                throws SAXException, IOException {
            if ((publicId != null && publicId.endsWith(FRAGMENT))
                    || (systemId != null && systemId.endsWith(FRAGMENT))) {
                return fragment;
            }

            return null;
        }
    }
}
