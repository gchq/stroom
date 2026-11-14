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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.generation.ReferenceGenerator;
import stroom.ai.impl.tools.generation.ReferenceMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// A lookup's key and its map's loader's key, of as many fields ([LookupKeyCounts]).
class TestLookupKeyCounts {

    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final LookupKeyCounts counts = new LookupKeyCounts(pipelineIndex, documentAccess);

    @Test
    void aLookupOfAsManyFieldsAsTheLoadersKeyIsNotWarnedOf() {
        loader(List.of("a", "b"));
        assertThat(counts.mismatches(mapping(List.of("x", "y")))).isEmpty();
    }

    @Test
    void aLookupOfOtherFieldsThanTheLoadersKeyIsWarnedOf() {
        loader(List.of("a", "b", "c"));
        assertThat(counts.mismatches(mapping(List.of("x", "y")))).singleElement().asString()
                .contains("lookup 'M' makes its key of 2 field(s)", "XSLT 'Loader' loads map M with a key of 3");
        loader(List.of("a"));
        assertThat(counts.mismatches(mapping(List.of("x", "y")))).singleElement().asString()
                .contains("with a key of 1");
    }

    @Test
    void aMappingWithNoLookupsReadsNothing() {
        assertThat(counts.mismatches(TranslationMapping.fromJson(Map.of("input", "json", "events",
                List.of(Map.of("name", "a", "fields", List.of(Map.of("path", "EventDetail/TypeId",
                        "value", "x")))))))).isEmpty();
        Mockito.verifyNoInteractions(pipelineIndex);
    }

    private void loader(final List<String> keyFields) {
        final ReferenceMapping reference = ReferenceMapping.fromJson(Map.of("input", "xml", "maps",
                List.of(Map.of("name", "M", "key_fields", keyFields, "values", List.of(Map.of("field", "v"))))));
        final XsltDoc doc = XsltDoc.builder().uuid("x1").name("Loader")
                .data(ReferenceGenerator.generate(reference).xslt()).build();
        final DocRef ref = new DocRef(XsltDoc.TYPE, "x1", "Loader");
        Mockito.when(pipelineIndex.containing(XsltDoc.TYPE, ReferenceData.REF_NS)).thenReturn(List.of(ref));
        Mockito.when(documentAccess.read(ref, XsltDoc.class)).thenReturn(Optional.of(doc));
    }

    private static TranslationMapping mapping(final List<String> keyFields) {
        return TranslationMapping.fromJson(Map.of("input", "json", "events", List.of(Map.of("name", "a",
                "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "x"),
                        Map.of("path", "EventDetail/Description", "lookup", Map.of("map", "M",
                                "key_fields", keyFields, "path", "v")))))));
    }
}
