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

package stroom.importexport.shared;

import stroom.docref.DocRef;
import stroom.util.json.JsonUtil;
import stroom.util.shared.DocRefs;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestExportContentRequest {

    private static final DocRef FOLDER_DOC_REF = new DocRef("Folder", "folder-uuid", "MyFolder");

    @Test
    void testSerDeser() {
        final ExportContentRequest request = new ExportContentRequest(
                Set.of(FOLDER_DOC_REF),
                Set.of("Feed", "XSLT"));

        final String json = JsonUtil.writeValueAsString(request);
        final ExportContentRequest request2 = JsonUtil.readValue(json, ExportContentRequest.class);

        assertThat(request2)
                .isEqualTo(request);
    }

    @Test
    void testDeserialiseLegacyDocRefsBody() {
        // Clients of the old API sent a DocRefs object, which must still be accepted
        final String json = JsonUtil.writeValueAsString(new DocRefs(Set.of(FOLDER_DOC_REF)));
        final ExportContentRequest request = JsonUtil.readValue(json, ExportContentRequest.class);

        assertThat(request.getDocRefs())
                .containsExactly(FOLDER_DOC_REF);
        assertThat(request.getIncludedTypes())
                .isNull();
    }

    @Test
    void testNullIncludedTypesNotSerialised() {
        final String json = JsonUtil.writeValueAsString(new ExportContentRequest(Set.of(FOLDER_DOC_REF), null));

        assertThat(json)
                .doesNotContain("includedTypes");
    }
}
