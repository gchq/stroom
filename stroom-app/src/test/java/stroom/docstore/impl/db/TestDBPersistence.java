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

package stroom.docstore.impl.db;


import stroom.docref.DocRef;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocDataType;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestDBPersistence extends AbstractCoreIntegrationTest {

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final String META_JSON_1 = """
            {
              "type": "XSLT",
              "uuid": "%s",
              "name": "EMBEDDED XSLT (translationFilter)",
              "version": "ba97a81f-c8e6-4d34-8ea8-a9808e89ced4",
              "createTimeMs": 1769686495941,
              "updateTimeMs": 1769686587789,
              "createUser": "admin",
              "updateUser": "admin",
              "embeddedIn": {
                "type": "Pipeline",
                "uuid": "%s",
                "name": "my pipeline"
              }
            }""";

    private static final String META_JSON_2 = """
            {
              "type": "Pipeline",
              "uuid": "%s",
              "name": "Pipeline",
              "version": "ba97a81f-c8e6-4d34-8ea8-a9808e89ced4",
              "createTimeMs": 1769686495941,
              "updateTimeMs": 1769686587789,
              "createUser": "admin",
              "updateUser": "admin"
            }""";


    @Inject
    private Persistence persistence;

    @Test
    void test() throws IOException {
        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef = new DocRef("test-type", "test-uuid", "test-name");
        final String json1 = String.format(META_JSON_1, uuid1, uuid2);
        final String json2 = String.format(META_JSON_2, uuid2);
        final JsonMapper mapper = JsonUtil.getMapper();

        // Ensure the doc doesn't exist.
        if (persistence.exists(docRef)) {
            persistence.delete(docRef, null);
        }

        // Create
        ImportExportDocument importExportDocument = new ImportExportDocument();
        importExportDocument.addExtAsset(
                new ByteArrayImportExportAsset("meta", DocDataType.JSON, json1.getBytes(CHARSET)));
        final String version1 = UUID.randomUUID().toString();
        persistence.write(docRef, AuditAction.CREATE, null, importExportDocument, null, version1);

        // Exists
        assertThat(persistence.exists(docRef)).isTrue();

        // Read
        importExportDocument = persistence.read(docRef);
        assertThat(mapper.readTree(importExportDocument.getExtAssetData("meta")))
                .isEqualTo(mapper.readTree(json1));

        // Update
        importExportDocument = new ImportExportDocument();
        importExportDocument.addExtAsset(
                new ByteArrayImportExportAsset("meta", DocDataType.JSON, json2.getBytes(CHARSET)));
        final String version2 = UUID.randomUUID().toString();
        persistence.write(docRef, AuditAction.UPDATE, null, importExportDocument, version1, version2);

        // Read
        importExportDocument = persistence.read(docRef);
        assertThat(mapper.readTree(importExportDocument.getExtAssetData("meta")))
                .isEqualTo(mapper.readTree(json2));

        // List
        final List<DocRef> refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.getFirst()).isEqualTo(docRef);

        // Delete
        persistence.delete(docRef, null);
    }

    @Test
    void findDocRefsEmbeddedIn() throws Exception {
        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef1 = new DocRef("test-type1", uuid1, "test-name1");
        final DocRef docRef2 = new DocRef("test-type2", uuid2, "test-name2");
        final String json1 = String.format(META_JSON_1, uuid1, uuid2);
        final String json2 = String.format(META_JSON_2, uuid2);

        persistence.delete(docRef1, null);
        persistence.delete(docRef2, null);

        // Create
        final ImportExportDocument ieDoc1 = new ImportExportDocument();
        ieDoc1.addExtAsset(new ByteArrayImportExportAsset("meta", DocDataType.JSON, json1.getBytes(CHARSET)));
        persistence.write(docRef1, AuditAction.CREATE, null, ieDoc1, null, UUID.randomUUID().toString());

        final ImportExportDocument ieDoc2 = new ImportExportDocument();
        ieDoc2.addExtAsset(new ByteArrayImportExportAsset("meta", DocDataType.JSON, json2.getBytes(CHARSET)));
        persistence.write(docRef2, AuditAction.CREATE, null, ieDoc2, null, UUID.randomUUID().toString());

        final List<DocRef> docRefs = persistence.findDocRefsEmbeddedIn(docRef2);
        assertThat(docRefs.size()).isEqualTo(1);
        assertThat(docRefs.getFirst()).isEqualTo(docRef1);
    }
}
