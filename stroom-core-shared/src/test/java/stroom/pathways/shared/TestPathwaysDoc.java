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

package stroom.pathways.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a stored Pathways document still loads once a property it was saved with has gone.
 *
 * <p>Read through {@link JsonUtil#getMapper()} because that is the mapper {@code JsonSerialiser2}
 * hands the docstore, so what passes here is what the docstore will accept off disk.
 */
class TestPathwaysDoc {

    @Test
    void propertiesThisDocumentNoLongerHasAreIgnored() throws Exception {
        // As a saved document reads on disk: settings that are still live, alongside two the document
        // no longer carries. Refusing the file over those would make every existing document unopenable.
        final String json = """
                {
                  "type" : "Pathways",
                  "uuid" : "6c3f38b2-0f1f-4f0f-9a2e-6f6f1f7b0a11",
                  "name" : "Self Monitoring",
                  "description" : "Watches Stroom",
                  "allowPathwayCreation" : false,
                  "tracesDocRef" : {
                    "type" : "Traces",
                    "uuid" : "1b4d9a44-1d0f-4a35-9a8e-2c7a2e1f9d30",
                    "name" : "OTEL Traces"
                  },
                  "processingNode" : "node1a"
                }
                """;

        final PathwaysDoc doc = JsonUtil.getMapper().readValue(json, PathwaysDoc.class);

        assertThat(doc.getName()).isEqualTo("Self Monitoring");
        assertThat(doc.getDescription()).isEqualTo("Watches Stroom");
        assertThat(doc.isAllowPathwayCreation())
                .as("a setting saved beside the dropped ones still arrives")
                .isFalse();
    }

    @Test
    void whatTheDocumentWritesDoesNotMentionThem() throws Exception {
        final PathwaysDoc doc = PathwaysDoc.builder()
                .uuid("6c3f38b2-0f1f-4f0f-9a2e-6f6f1f7b0a11")
                .name("Self Monitoring")
                .build();

        final String json = JsonUtil.getMapper().writeValueAsString(doc);

        assertThat(json).doesNotContain("tracesDocRef", "processingNode");
    }
}
