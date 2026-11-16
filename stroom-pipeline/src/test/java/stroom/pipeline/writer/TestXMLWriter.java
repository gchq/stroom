/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.writer;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.xml.converter.json.JSONParserFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

class TestXMLWriter {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aNulDecodedFromJsonDoesNotSwitchOffEscaping(final boolean preventEscapeSwitching) throws Exception {
        // The JSON escape is six printable characters in the raw data; the parser decodes it to U+0000.
        final String json = "{\"m\": \"a\\u0000<x/>&nbsp;\\u0000<y a='1'/>\"}";

        final String xml = parseJsonAndWriteXml(json, preventEscapeSwitching);

        assertThat(xml)
                .doesNotContain("<x/>", "&nbsp;", "<y ", "&#x0;", "\u0000")
                .contains("a&lt;x/&gt;&amp;nbsp;&lt;y a='1'/&gt;");
        final Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        assertThat(document.getDocumentElement().getTextContent()).isEqualTo("a<x/>&nbsp;<y a='1'/>");
    }

    private String parseJsonAndWriteXml(final String json, final boolean preventEscapeSwitching) throws Exception {
        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final OutputStreamAppender appender = new OutputStreamAppender(errorReceiverProxy, outputStream);

        final XMLWriter xmlWriter = new XMLWriter(
                errorReceiverProxy,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        xmlWriter.setPreventEscapeSwitching(preventEscapeSwitching);
        xmlWriter.setTarget(appender);

        final JSONParserFactory parserFactory = new JSONParserFactory();
        parserFactory.setAddRootObject(false);
        final XMLReader reader = parserFactory.getParser();
        reader.setContentHandler(xmlWriter);

        xmlWriter.startProcessing();
        try {
            reader.parse(new InputSource(new StringReader(json)));
        } finally {
            xmlWriter.endProcessing();
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
