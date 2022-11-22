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


import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexDoc.PartitionBy;
import stroom.index.shared.IndexShardKey;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardKeyUtil extends StroomUnitTest {

    @Test
    void testMultishard() {
        final IndexDoc index = new IndexDoc();
        index.setUuid(UUID.randomUUID().toString());
        index.setShardsPerPartition(5);
        final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);

        assertThat(indexShardKey.getIndexUuid()).isEqualTo(index.getUuid());
        assertThat(indexShardKey.getShardNo() >= 0).isTrue();
    }
}
