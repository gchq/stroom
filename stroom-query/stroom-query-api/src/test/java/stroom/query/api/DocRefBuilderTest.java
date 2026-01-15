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

package stroom.query.api;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocRefBuilderTest {

    @Test
    void doesBuild() {
        final String name = "someName";
        final String type = "someType";
        final String uuid = UUID.randomUUID().toString();

        final DocRef docRef = DocRef
                .builder()
                .name(name)
                .type(type)
                .uuid(uuid)
                .build();

        assertThat(docRef.getName()).isEqualTo(name);
        assertThat(docRef.getType()).isEqualTo(type);
        assertThat(docRef.getUuid()).isEqualTo(uuid);
    }
}
