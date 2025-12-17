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

import stroom.pipeline.xml.converter.ParserFactory;
import stroom.util.io.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.LocatorImpl;

import java.io.InputStream;

public class XMLFragmentParserFactory implements ParserFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLFragmentParserFactory.class);

    private final String xml;

    private XMLFragmentParserFactory(final InputStream inputStream, final ErrorHandler errorHandler) {
        xml = StreamUtil.streamToString(inputStream);

        if (xml.trim().length() == 0) {
            try {
                errorHandler.fatalError(new SAXParseException("Invalid XML wrapper", new LocatorImpl()));
            } catch (final SAXException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public static XMLFragmentParserFactory create(final InputStream inputStream, final ErrorHandler errorHandler) {
        return new XMLFragmentParserFactory(inputStream, errorHandler);
    }

    @Override
    public XMLReader getParser() {
        return new XMLFragmentParser(xml);
    }
}
