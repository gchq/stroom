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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import stroom.entity.util.XMLUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestXMLFragmentParser extends StroomUnitTest {
    @Test
    public void test() throws Exception {
        final String outerXML = "<?xml version=\"1.1\"?><!DOCTYPE Record [<!ENTITY fragment SYSTEM \"fragment\">]><records>&fragment;</records>";
        final String innerXML = "<record><data name=\"Test\" value=\"Test\"/></record>";
        final String expected = "<?xml version=\"1.1\" encoding=\"UTF-8\"?><records><record><data name=\"Test\" value=\"Test\"/></record></records>";

        doParse(outerXML, innerXML, expected);
    }

    @Test
    public void testLotsOfText() throws Exception {
        final String outerXML = "<?xml version=\"1.1\"?><!DOCTYPE Record [<!ENTITY fragment SYSTEM \"fragment\">]><Records>&fragment;</Records>";
        final String value = "This is a load of text ldkjsf slkdfjlkjsdflkjsdf sdlkfjsdf lkjsdflkjsdflkjsdf sdflkjsdflkhj sdflkjsdf lkjsdf lkjsdfl sdflkjsfdlkjsdf lkjsdf lkjsdf lkjsdfl kjsdflkjsdf lkjsdflkhjsdflkj sdfljhsdgflkhweripuweroijsdjfvnsv,jnsdfl hsdlfkj sdflkjhsdflkjwerlkhwef dwsflkjsdf lkjwefrlkjhsdf sdflkjwef weflkjwef weflkjwef weflkjwe flkjwf";
        final String innerXML = "<Record><Data Name=\"Test\" Value=\"" + value + "\"/></Record>";
        final String expected = "<?xml version=\"1.1\" encoding=\"UTF-8\"?><Records><Record><Data Name=\"Test\" Value=\""
                + value + "\"/></Record></Records>";

        doParse(outerXML, innerXML, expected);
    }

    @Test(expected = SAXParseException.class)
    public void testBadChar() throws Exception {
        final String outerXML = "<?xml version=\"1.1\"?><!DOCTYPE Record [<!ENTITY fragment SYSTEM \"fragment\">]><records>&fragment;</records>";
        final String innerXML = "<record><data name=\"Test\" value=\"Test\u0092x\"/></record>";

        doParse(outerXML, innerXML, null);
    }

    private void doParse(final String outerXML, final String innerXML, final String expectedXML)
            throws IOException, SAXException, TransformerConfigurationException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final TransformerHandler th = XMLUtil.createTransformerHandler(false);
        th.setResult(new StreamResult(outputStream));

        final XMLFragmentParser parser = new XMLFragmentParser(outerXML);
        parser.setContentHandler(th);

        parser.parse(new InputSource(StreamUtil.stringToStream(innerXML)));

        Assert.assertEquals(expectedXML, outputStream.toString());
    }

}
