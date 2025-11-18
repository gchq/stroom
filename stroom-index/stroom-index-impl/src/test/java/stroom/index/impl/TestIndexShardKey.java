/*
 * Copyright 2017 Crown Copyright
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

package stroom.index.impl;


import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardKey {

    @Test
    void testMultishard() {
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .shardsPerPartition(5)
                .build();
        final IndexShardKey indexShardKey = IndexShardKey.createKey(index);

        assertThat(indexShardKey.getIndexUuid()).isEqualTo(index.getUuid());
    }
}
