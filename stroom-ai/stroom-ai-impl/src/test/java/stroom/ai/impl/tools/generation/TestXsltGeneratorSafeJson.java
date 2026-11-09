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

package stroom.ai.impl.tools.generation;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.Xslt30Transformer;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

/// The generated XSLT's guarded json-to-xml ([XsltGenerator#SAFE_JSON]), run by Saxon: JSON in a field read, JSON
/// encoded twice read again, and text that isn't JSON giving nothing rather than stopping the pipeline.
class TestXsltGeneratorSafeJson {

    private static final String SHEET = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                        + "xmlns:mcp=\"" + MappingFacts.MCP_NS + "\" version=\"3.0\">"
                                        + XsltGenerator.SAFE_JSON
                                        + "<xsl:param name=\"text\"/>"
                                        + "<xsl:template name=\"xsl:initial-template\">"
                                        + "<xsl:value-of select=\"string(mcp:json-to-xml($text)/*/*[@key='action'])\"/>"
                                        + "</xsl:template></xsl:stylesheet>";

    @Test
    void jsonInAFieldIsRead() throws SaxonApiException {
        assertThat(action("{\"action\": \"login\"}")).isEqualTo("login");
    }

    @Test
    void jsonEncodedTwiceIsReadAgain() throws SaxonApiException {
        // A JSON string whose text is the object, as some shippers write a record they were given as JSON.
        assertThat(action("\"{\\\"action\\\": \\\"logout\\\"}\"")).isEqualTo("logout");
    }

    @Test
    void aJsonStringThatIsNotJsonIsLeftAsAString() throws SaxonApiException {
        assertThat(action("\"just text\"")).isEmpty();
    }

    @Test
    void textThatIsNotJsonGivesNothing() throws SaxonApiException {
        assertThat(action("not json")).isEmpty();
        assertThat(action("")).isEmpty();
    }

    private static String action(final String text) throws SaxonApiException {
        final Processor processor = new Processor(false);
        final Xslt30Transformer transformer = processor.newXsltCompiler()
                .compile(new StreamSource(new StringReader(SHEET))).load30();
        transformer.setStylesheetParameters(Map.of(new QName("text"), new XdmAtomicValue(text)));
        final StringWriter out = new StringWriter();
        final Serializer serializer = processor.newSerializer(out);
        serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
        serializer.setOutputProperty(Serializer.Property.METHOD, "text");
        transformer.callTemplate(null, serializer);
        return out.toString();
    }
}
