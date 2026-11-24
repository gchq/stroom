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

package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestXsltImports {

    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final XsltStore xsltStore = Mockito.mock(XsltStore.class);
    private final XsltImports xsltImports = new XsltImports(docFinder, xsltStore);

    @Test
    void findsImportAndIncludeHrefsInEveryForm() {
        final String xslt = """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:import href="By Name"/>
                  <xsl:import href='Single Quoted'/>
                  <xsl:include
                      href="0f9a5d1e-uuid"/>
                  <xsl:import href="uuid='abc', name='Doc Ref'"/>
                  <x:include href="Other Prefix"/>
                  <include href="No Prefix"/>
                  <xsl:template match="/"/>
                </xsl:stylesheet>
                """;

        assertThat(XsltImports.findHrefs(xslt)).containsExactly(
                "By Name",
                "Single Quoted",
                "0f9a5d1e-uuid",
                "uuid='abc', name='Doc Ref'",
                "Other Prefix",
                "No Prefix");
        assertThat(XsltImports.hasImports(xslt)).isTrue();
    }

    @Test
    void anXsltWithoutImportsHasNone() {
        final String xslt = """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><important href="not an import"/></xsl:template>
                </xsl:stylesheet>
                """;

        assertThat(XsltImports.findHrefs(xslt)).isEmpty();
        assertThat(XsltImports.hasImports(xslt)).isFalse();
        assertThat(XsltImports.findHrefs(null)).isEmpty();
        assertThat(XsltImports.hasImports("  ")).isFalse();
    }

    @Test
    void resolvesByNameFirst() {
        final DocRef docRef = new DocRef(XsltDoc.TYPE, "named-uuid", "Common");
        final XsltDoc xsltDoc = XsltDoc.builder().uuid("named-uuid").name("Common").build();
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "Common", false)).thenReturn(List.of(docRef));
        Mockito.when(xsltStore.readDocument(docRef)).thenReturn(xsltDoc);

        final XsltImports.Resolution resolution = xsltImports.resolve("Common");

        assertThat(resolution.isFound()).isTrue();
        assertThat(resolution.xsltDoc()).isSameAs(xsltDoc);
    }

    @Test
    void resolvesByUuidWhenNoXsltHasTheName() {
        final XsltDoc xsltDoc = XsltDoc.builder().uuid("the-uuid").name("Common").build();
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "the-uuid", false)).thenReturn(List.of());
        Mockito.when(xsltStore.readDocument(new DocRef(XsltDoc.TYPE, "the-uuid", null))).thenReturn(xsltDoc);

        assertThat(xsltImports.resolve("the-uuid").xsltDoc()).isSameAs(xsltDoc);
    }

    @Test
    void resolvesADocRefString() {
        final String href = "uuid='the-uuid', name='Common'";
        final XsltDoc xsltDoc = XsltDoc.builder().uuid("the-uuid").name("Common").build();
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, href, false)).thenReturn(List.of());
        Mockito.when(xsltStore.readDocument(new DocRef(XsltDoc.TYPE, "the-uuid", "Common"))).thenReturn(xsltDoc);

        assertThat(xsltImports.resolve(href).xsltDoc()).isSameAs(xsltDoc);
    }

    @Test
    void anHrefThatIsNeitherANameNorAUuidIsNotFound() {
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "Missing", false)).thenReturn(List.of());
        Mockito.when(xsltStore.readDocument(Mockito.any()))
                .thenThrow(new DocumentNotFoundException(new DocRef(XsltDoc.TYPE, "Missing")));

        final XsltImports.Resolution resolution = xsltImports.resolve("Missing");

        assertThat(resolution.isFound()).isFalse();
        assertThat(resolution.isAmbiguous()).isFalse();
    }

    @Test
    void aNameSharedByMoreThanOneXsltIsAmbiguous() {
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "Shared", false)).thenReturn(List.of(
                new DocRef(XsltDoc.TYPE, "uuid-1", "Shared"),
                new DocRef(XsltDoc.TYPE, "uuid-2", "Shared")));

        final XsltImports.Resolution resolution = xsltImports.resolve("Shared");

        assertThat(resolution.isFound()).isFalse();
        assertThat(resolution.isAmbiguous()).isTrue();
        assertThat(resolution.matchCount()).isEqualTo(2);
        Mockito.verifyNoInteractions(xsltStore);
    }
}
