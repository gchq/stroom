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

package stroom.pipeline.stepping;

import stroom.docref.DocRef;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xslt.XsltStore;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSavedElementCode {

    private static final DocRef XSLT = new DocRef(XsltDoc.TYPE, "xslt-uuid", "Translate");
    private static final DocRef CONVERTER = new DocRef(TextConverterDoc.TYPE, "tc-uuid", "Splitter");
    private static final DocRef FEED = new DocRef("Feed", "feed-uuid", "FEED");

    private final XsltStore xsltStore = Mockito.mock(XsltStore.class);
    private final TextConverterStore textConverterStore = Mockito.mock(TextConverterStore.class);
    private final SavedElementCode savedElementCode = new SavedElementCode(xsltStore, textConverterStore);

    @Test
    void eachElementGetsTheSavedVersionsOfTheCodeItRefersTo() {
        Mockito.when(xsltStore.readDocument(XSLT)).thenReturn(XsltDoc.builder().uuid(XSLT.getUuid())
                .version("x1").build());
        Mockito.when(textConverterStore.readDocument(CONVERTER)).thenReturn(TextConverterDoc.builder()
                .uuid(CONVERTER.getUuid()).version("t1").build());

        assertThat(savedElementCode.versions(pipeline(), Map.of()))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "translationFilter", "{xslt=x1}",
                        "parser", "{textConverter=t1}"));
    }

    @Test
    void anElementWithInjectedCodeRunsThatInstead() {
        Mockito.when(textConverterStore.readDocument(CONVERTER)).thenReturn(TextConverterDoc.builder()
                .uuid(CONVERTER.getUuid()).version("t1").build());

        assertThat(savedElementCode.versions(pipeline(), Map.of("translationFilter", "<xsl:stylesheet/>")))
                .containsOnlyKeys("parser");
        Mockito.verifyNoInteractions(xsltStore);
    }

    @Test
    void codeThatCantBeReadIsLeftOut() {
        Mockito.when(xsltStore.readDocument(XSLT)).thenThrow(new RuntimeException("No permission"));
        Mockito.when(textConverterStore.readDocument(CONVERTER)).thenReturn(null);

        assertThat(savedElementCode.versions(pipeline(), null)).isEmpty();
    }

    @Test
    void documentsThatArentCodeAreIgnored() {
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("reader", "Reader"))
                .addProperty(new PipelineProperty("reader", "feed", new PipelinePropertyValue(FEED)))
                .addProperty(new PipelineProperty("reader", "encoding", new PipelinePropertyValue("UTF-8")))
                .build();

        assertThat(savedElementCode.versions(data, Map.of())).isEmpty();
        Mockito.verifyNoInteractions(xsltStore, textConverterStore);
    }

    private static PipelineData pipeline() {
        return new PipelineDataBuilder()
                .addElement(new PipelineElement("parser", "CombinedParser"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                .addProperty(new PipelineProperty("parser", "textConverter", new PipelinePropertyValue(CONVERTER)))
                .addProperty(new PipelineProperty("translationFilter", "xslt", new PipelinePropertyValue(XSLT)))
                .addLink("parser", "translationFilter")
                .build();
    }
}
