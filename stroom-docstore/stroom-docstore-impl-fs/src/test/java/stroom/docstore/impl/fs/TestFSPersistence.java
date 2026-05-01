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

package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AbstractDoc;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestFSPersistence {

    @Test
    void test() throws IOException {
        final Persistence persistence = new FSPersistence(
                Files.createTempDirectory("docstore").resolve("conf"));

        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef = new DocRef("test-type", "test-uuid", "test-name");

        // Ensure the doc doesn't exist.
        if (persistence.exists(docRef)) {
            persistence.delete(docRef);
        }

        GenericDoc doc = new GenericDoc(
                docRef.getUuid(),
                docRef.getName(),
                null,
                null,
                null,
                null,
                null);
        final ObjectMapper mapper = JsonUtil.getNoIndentMapper();
        byte[] bytes = mapper.writeValueAsBytes(doc);

        // Create
        final ImportExportDocument ieDoc = new ImportExportDocument();
        ieDoc.addExtAsset(new ByteArrayImportExportAsset("meta", bytes));
        persistence.write(docRef, false, ieDoc);

        // Exists
        assertThat(persistence.exists(docRef)).isTrue();

        // Read
        final ImportExportDocument ieDocRead = persistence.read(docRef);
        assertThat(ieDocRead.getExtAssetData("meta")).isEqualTo(bytes);

        // List
        List<DocRef> refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.getFirst()).isEqualTo(docRef);
        assertThat(refs.getFirst().getType()).isEqualTo(docRef.getType());
        assertThat(refs.getFirst().getUuid()).isEqualTo(docRef.getUuid());
        assertThat(refs.getFirst().getName()).isEqualTo(docRef.getName());

        // Update
        doc = doc.copy().name("New Name").build();
        bytes = mapper.writeValueAsBytes(doc);
        final ImportExportDocument ieDocNewName = new ImportExportDocument();
        ieDocNewName.addExtAsset(new ByteArrayImportExportAsset("meta", bytes));
        persistence.write(docRef, true, ieDocNewName);

        // Read
        final ImportExportDocument ieDocNewNameRead = persistence.read(docRef);
        assertThat(ieDocNewNameRead.getExtAssetData("meta")).isEqualTo(bytes);

        // List
        refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.getFirst()).isEqualTo(docRef);
        assertThat(refs.getFirst().getType()).isEqualTo(docRef.getType());
        assertThat(refs.getFirst().getUuid()).isEqualTo(docRef.getUuid());
        assertThat(refs.getFirst().getName()).isEqualTo("New Name");

        // Delete
        persistence.delete(docRef);
    }

    private static class GenericDoc extends AbstractDoc {

        public GenericDoc(@JsonProperty("uuid") final String uuid,
                          @JsonProperty("name") final String name,
                          @JsonProperty("version") final String version,
                          @JsonProperty("createTimeMs") final Long createTimeMs,
                          @JsonProperty("updateTimeMs") final Long updateTimeMs,
                          @JsonProperty("createUser") final String createUser,
                          @JsonProperty("updateUser") final String updateUser) {
            super("GenericDoc", uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder extends AbstractBuilder<GenericDoc, Builder> {

            private Builder() {
            }

            private Builder(final GenericDoc genericDoc) {
                super(genericDoc);
            }

            @Override
            protected Builder self() {
                return this;
            }

            public GenericDoc build() {
                return new GenericDoc(
                        uuid,
                        name,
                        version,
                        createTimeMs,
                        updateTimeMs,
                        createUser,
                        updateUser);
            }
        }
    }
}
