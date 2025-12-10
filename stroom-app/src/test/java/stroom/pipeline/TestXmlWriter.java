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

package stroom.pipeline;

import stroom.util.xml.FatalErrorListener;
import stroom.util.xml.SAXParserFactoryFactory;
import stroom.util.xml.XMLUtil;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

class TestXmlWriter {

    @Test
    void testUnicodeHandling() throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (int i = '\u0001'; i <= '\uffff'; i++) {
            final char c = (char) i;
            if (!UTF16CharacterSet.isSurrogate(c)) {
                sb.append(c);
            }
        }

        final String text = sb.toString();
        final char[] characters = text.toCharArray();
        final AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "att", "att", "string", text);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final TransformerHandler th = XMLUtil.createTransformerHandler(new FatalErrorListener(), true);
        th.setResult(new StreamResult(baos));

        th.startDocument();
        th.startElement("", "test", "test", attributes);
        th.characters(characters, 0, characters.length);
        th.endElement("", "test", "test");
        th.endDocument();

        final byte[] bytes = baos.toByteArray();
        final String string = new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);

        final XMLReader xmlReader = SAXParserFactoryFactory.newInstance().newSAXParser().getXMLReader();
        xmlReader.parse(new InputSource(new StringReader(string)));
    }
}
