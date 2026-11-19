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

package stroom.index.shared;

import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestLuceneIndexDoc {

    /// Every partition setting, including no partition, must survive being sent to the server and saved.
    @ParameterizedTest
    @EnumSource(PartitionBy.class)
    void testSerialisation_partitionBy(final PartitionBy partitionBy) {
        final LuceneIndexDoc doc = LuceneIndexDoc.builder()
                .uuid("uuid")
                .name("name")
                .partitionBy(partitionBy)
                .build();

        final String json = JsonUtil.writeValueAsString(doc);
        final LuceneIndexDoc deserialised = JsonUtil.readValue(json, LuceneIndexDoc.class);

        assertThat(deserialised.getPartitionBy()).isEqualTo(partitionBy);
        assertThat(deserialised).isEqualTo(doc);
    }

    /// Documents saved without a partition setting have always been partitioned by month.
    @Test
    void testDeserialisation_noPartitionBy() {
        final String json = """
                {"type":"Index","uuid":"uuid","name":"name"}""";

        final LuceneIndexDoc deserialised = JsonUtil.readValue(json, LuceneIndexDoc.class);

        assertThat(deserialised.getPartitionBy()).isEqualTo(PartitionBy.MONTH);
    }

    @Test
    void testBuilder_defaultPartitionBy() {
        final LuceneIndexDoc doc = LuceneIndexDoc.builder()
                .uuid("uuid")
                .name("name")
                .build();

        assertThat(doc.getPartitionBy()).isEqualTo(PartitionBy.MONTH);
    }

    @Test
    void testPartitionBy_noneDisplayValue() {
        assertThat(PartitionBy.NONE.getDisplayValue()).isEqualTo("No partition");
    }

    @Test
    void testPartitionBy_isTimeBased() {
        assertThat(PartitionBy.NONE.isTimeBased()).isFalse();
        assertThat(PartitionBy.DAY.isTimeBased()).isTrue();
        assertThat(PartitionBy.WEEK.isTimeBased()).isTrue();
        assertThat(PartitionBy.MONTH.isTimeBased()).isTrue();
        assertThat(PartitionBy.YEAR.isTimeBased()).isTrue();
    }
}
