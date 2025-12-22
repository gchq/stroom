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
}
