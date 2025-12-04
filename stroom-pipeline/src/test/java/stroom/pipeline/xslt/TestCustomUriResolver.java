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

package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
