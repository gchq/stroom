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

package stroom.pipeline.xml.converter.xmlfragment;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.xml.converter.AbstractParser;
import stroom.util.xml.SAXParserFactoryFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class XMLFragmentParser extends AbstractParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLFragmentParser.class);

    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    private final String xml;

    XMLFragmentParser(final String xml) {
        this.xml = xml;
    }

    @Override
    public void parse(final InputSource input) throws IOException, SAXException {
        final SAXParser parser;
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
            // entity resolution to achieve its goal, as the scanning for entities must add a fair
            // amount of overhead. A simpler and more crude approach may be better.
            xmlReader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);

            // The fragment wrapper mechanism cannot work without a DOCTYPE + external general entity - it
            // injects the fragment as one - so, unlike the factory-level XXE hardening (a policy an operator
            // may relax via config), these two are a functional requirement and are re-enabled here
            // unconditionally. Security does NOT come from these being off (they cannot be); it comes from the
            // FragmentEntity resolver below denying every entity except the fragment, plus external parameter
            // entities and external DTD loading staying disabled. So the fragment parser is XXE-safe whether or
            // not the configurable hardening is enabled.
            xmlReader.setFeature(SAXParserFactoryFactory.FEATURE_DISALLOW_DOCTYPE, false);
            xmlReader.setFeature(SAXParserFactoryFactory.FEATURE_EXTERNAL_GENERAL_ENTITIES, true);
        } catch (final SAXNotRecognizedException | SAXNotSupportedException e) {
            LOGGER.error("Unable to configure the SAX parser for fragment parsing", e);
        }

        final InputSource inputSource = new InputSource(new StringReader(xml));
        inputSource.setEncoding("UTF-8");
        xmlReader.parse(inputSource);
    }


    // --------------------------------------------------------------------------------


    private class FragmentEntity implements EntityResolver {

        private static final String FRAGMENT = "fragment";
        private final InputSource fragment;

        FragmentEntity(final InputSource fragment) {
            this.fragment = fragment;
        }

        @Override
        public InputSource resolveEntity(final String publicId, final String systemId) {
            if ((publicId != null && publicId.endsWith(FRAGMENT))
                    || (systemId != null && systemId.endsWith(FRAGMENT))) {
                return fragment;
            }

            // Deny any other entity. Returning null would fall back to the default resolver, which would
            // fetch the systemId (an XXE / SSRF / file-read vector); an empty source resolves it to nothing.
            return new InputSource(new StringReader(""));
        }
    }
}
