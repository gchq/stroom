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

package stroom.pipeline.xml.bug;

import stroom.util.xml.SAXParserFactoryFactory;

import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.Reader;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;

public class SaxUtil {

    public static void parse(final Reader input,
                             final ContentHandler contentHandler,
                             final EntityResolver resolver) throws Exception {
        final SAXParser parser = SAXParserFactoryFactory.newInstance().newSAXParser();

        final XMLReader xmlReader = parser.getXMLReader();
        xmlReader.setEntityResolver(resolver);
        xmlReader.setContentHandler(contentHandler);
//        xmlReader.setErrorHandler(getErrorHandler());
        xmlReader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);

        final InputSource inputSource = new InputSource(input);
        inputSource.setEncoding("UTF-8");
        xmlReader.parse(inputSource);
    }
}
