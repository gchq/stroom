/*
 * Copyright 2020 Crown Copyright
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

package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XsltCompiler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.StringReader;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;

class TestCustomUriResolver {
    @Test
    void testParseDocRef() {
        DocRef docRef = CustomURIResolver.parseDocRef("test-uuid");
        assertThat(docRef.getUuid()).isEqualTo("test-uuid");
        assertThat(docRef.getName()).isNull();

        docRef = new DocRef(PipelineDoc.TYPE, "test-uuid", "test-name");
        final DocRef parsed = CustomURIResolver.parseDocRef(docRef.toString());
        assertThat(parsed).isEqualTo(docRef);
    }

    @Test
    void testGetPart() {
        final DocRef docRef = new DocRef(PipelineDoc.TYPE, "test-uuid", "test-name");
        String docRefString = docRef.toString();
        docRefString = docRefString.replaceAll("\"", "'");

        String value = CustomURIResolver.getPart("type", docRefString, null);
        assertThat(value).isEqualTo(PipelineDoc.TYPE);

        value = CustomURIResolver.getPart("uuid", docRefString, null);
        assertThat(value).isEqualTo("test-uuid");

        value = CustomURIResolver.getPart("name", docRefString, null);
        assertThat(value).isEqualTo("test-name");

        docRefString = docRefString.replaceAll("'", "\"");

        value = CustomURIResolver.getPart("type", docRefString, null);
        assertThat(value).isEqualTo(PipelineDoc.TYPE);

        value = CustomURIResolver.getPart("uuid", docRefString, null);
        assertThat(value).isEqualTo("test-uuid");

        value = CustomURIResolver.getPart("name", docRefString, null);
        assertThat(value).isEqualTo("test-name");

        docRefString = "type=Pipeline, uuid=test-uuid, name=test-name";

        value = CustomURIResolver.getPart("type", docRefString, null);
        assertThat(value).isEqualTo(PipelineDoc.TYPE);

        value = CustomURIResolver.getPart("uuid", docRefString, null);
        assertThat(value).isEqualTo("test-uuid");

        value = CustomURIResolver.getPart("name", docRefString, null);
        assertThat(value).isEqualTo("test-name");
    }

    @Test
    void anXsltImportedTwiceInOneStylesheetCompiles() {
        // Two imported XSLTs each importing a third (an aggregated feed's source type groups each importing its
        // common module): without a system id of its own, Saxon took the third for a stylesheet importing itself.
        final Map<String, String> docs = Map.of("A", sheet("C"), "B", sheet("C"), "C", sheet());
        final XsltImports imports = Mockito.mock(XsltImports.class);
        Mockito.when(imports.resolve(anyString())).thenAnswer(inv -> {
            final String href = inv.getArgument(0);
            return XsltImports.Resolution.found(XsltDoc.builder().uuid("uuid-" + href).name(href)
                    .data(docs.get(href)).build());
        });
        final XsltCompiler compiler = new Processor(false).newXsltCompiler();
        compiler.setURIResolver(new CustomURIResolver(imports));

        assertThatCode(() -> compiler.compile(new StreamSource(new StringReader(sheet("A", "B")))))
                .doesNotThrowAnyException();
        assertThat(CustomURIResolver.systemId(XsltDoc.builder().uuid("u-1").build()))
                .isEqualTo("urn:stroom:xslt:u-1");
    }

    private static String sheet(final String... imports) {
        final StringBuilder out = new StringBuilder("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                                    + "version=\"3.0\">");
        for (final String href : imports) {
            out.append("<xsl:import href=\"").append(href).append("\"/>");
        }
        return out.append("<xsl:template name=\"t").append(imports.length).append("\"/></xsl:stylesheet>")
                .toString();
    }
}
