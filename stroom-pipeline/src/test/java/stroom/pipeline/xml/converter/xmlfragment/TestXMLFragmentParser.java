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


import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.StreamUtil;
import stroom.util.xml.XMLUtil;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestXMLFragmentParser extends StroomUnitTest {

    @Test
    void test() throws SAXException, IOException, TransformerConfigurationException {
        final String outerXML = "<?xml version=\"1.1\"?>" +
                "<!DOCTYPE Record [<!ENTITY fragment SYSTEM \"fragment\">]><records>&fragment;</records>";
        final String innerXML = "<record><data name=\"Test\" value=\"Test\"/></record>";
        final String expected = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>" +
                "<records><record><data name=\"Test\" value=\"Test\"/></record></records>";

        doParse(outerXML, innerXML, expected);
    }

    @Test
    void testLotsOfText() throws SAXException, IOException, TransformerConfigurationException {
        final String outerXML = "<?xml version=\"1.1\"?>" +
                "<!DOCTYPE Record [<!ENTITY fragment SYSTEM \"fragment\">]><Records>&fragment;</Records>";
        final String value = "This is a load of text ldkjsf slkdfjlkjsdflkjsdf sdlkfjsdf lkjsdflkjsdflkjsdf " +
                "sdflkjsdflkhj sdflkjsdf lkjsdf lkjsdfl sdflkjsfdlkjsdf lkjsdf lkjsdf lkjsdfl kjsdflkjsdf " +
                "lkjsdflkhjsdflkj sdfljhsdgflkhweripuweroijsdjfvnsv,jnsdfl hsdlfkj sdflkjhsdflkjwerlkhwef " +
                "dwsflkjsdf lkjwefrlkjhsdf sdflkjwef weflkjwef weflkjwef weflkjwe flkjwf";
        final String innerXML = "<Record><Data Name=\"Test\" Value=\"" + value + "\"/></Record>";
        final String expected = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>" +
                "<Records><Record><Data Name=\"Test\" Value=\"" + value + "\"/></Record></Records>";

        doParse(outerXML, innerXML, expected);
    }

    @Test
    void testBadChar() {
        assertThatThrownBy(() -> {
            final String outerXML = "<?xml version=\"1.1\"?>" +
                    "<!DOCTYPE Record [<!ENTITY fragment SYSTEM \"fragment\">]><records>&fragment;</records>";
            final String innerXML = "<record><data name=\"Test\" value=\"Test\u0092x\"/></record>";

            doParse(outerXML, innerXML, null);
        }).isInstanceOf(SAXParseException.class);
    }

    private void doParse(final String outerXML, final String innerXML, final String expectedXML)
            throws IOException, SAXException, TransformerConfigurationException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final TransformerHandler th = XMLUtil.createTransformerHandler(false);
        th.setResult(new StreamResult(outputStream));

        final XMLFragmentParser parser = new XMLFragmentParser(outerXML);
        parser.setContentHandler(th);

        parser.parse(new InputSource(StreamUtil.stringToStream(innerXML)));

        assertThat(outputStream.toString())
                .isEqualTo(expectedXML);
    }

}
