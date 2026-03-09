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
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestDBPersistence extends AbstractCoreIntegrationTest {

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    @Inject
    private Persistence persistence;

    @Test
    void test() throws IOException {
        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef = new DocRef("test-type", "test-uuid", "test-name");

        // Ensure the doc doesn't exist.
        if (persistence.exists(docRef)) {
            persistence.delete(docRef);
        }

        // Create
        Map<String, byte[]> data = new HashMap<>();
        data.put("meta", uuid1.getBytes(CHARSET));
        persistence.write(docRef, false, data);

        // Exists
        assertThat(persistence.exists(docRef)).isTrue();

        // Read
        data = persistence.read(docRef);
        assertThat(new String(data.get("meta"), CHARSET)).isEqualTo(uuid1);

        // Update
        data = new HashMap<>();
        data.put("meta", uuid2.getBytes(CHARSET));
        persistence.write(docRef, true, data);

        // Read
        data = persistence.read(docRef);
        assertThat(new String(data.get("meta"), CHARSET)).isEqualTo(uuid2);

        // List
        final List<DocRef> refs = persistence.list(docRef.getType());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.get(0)).isEqualTo(docRef);

        // Delete
        persistence.delete(docRef);
    }

    @Test
    void findDocRefsEmbeddedIn() throws Exception {
        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef1 = new DocRef("test-type1", uuid1, "test-name1");
        final DocRef docRef2 = new DocRef("test-type2", uuid2, "test-name2");

        persistence.delete(docRef1);
        persistence.delete(docRef2);

        final String metaJson1 = """
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

        final String metaJson2 = """
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

        // Create
        final Map<String, byte[]> data1 = new HashMap<>();
        data1.put("meta", String.format(metaJson1, uuid1, uuid2).getBytes(CHARSET));
        persistence.write(docRef1, false, data1);

        final Map<String, byte[]> data2 = new HashMap<>();
        data2.put("meta", String.format(metaJson2, uuid2).getBytes(CHARSET));
        persistence.write(docRef2, false, data2);

        final List<DocRef> docRefs = persistence.findDocRefsEmbeddedIn(docRef2);
        assertThat(docRefs.size()).isEqualTo(1);
        assertThat(docRefs.get(0)).isEqualTo(docRef1);
    }
}
