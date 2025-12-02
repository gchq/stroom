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


import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimePartition {

    @Test
    void testTimeBased() {
        final String date = "2013-03-07T12:33:44.000Z";
        final long millis = DateUtil.parseNormalDateTimeString(date);

        assertThat(getPartition(PartitionBy.YEAR, 1, millis)).isEqualTo("2013");
        assertThat(getPartition(PartitionBy.MONTH, 1, millis)).isEqualTo("2013-03");
        assertThat(getPartition(PartitionBy.DAY, 1, millis)).isEqualTo("2013-03-07");
        assertThat(getPartition(PartitionBy.WEEK, 1, millis)).isEqualTo("2013-03-04");

        assertThat(getPartition(PartitionBy.YEAR, 3, millis)).isEqualTo("2012");
        assertThat(getPartition(PartitionBy.MONTH, 3, millis)).isEqualTo("2013-01");
        assertThat(getPartition(PartitionBy.DAY, 3, millis)).isEqualTo("2013-03-07");
        assertThat(getPartition(PartitionBy.WEEK, 3, millis)).isEqualTo("2013-02-18");

        assertThat(getPartition(PartitionBy.YEAR, 5, millis)).isEqualTo("2010");
        assertThat(getPartition(PartitionBy.MONTH, 5, millis)).isEqualTo("2012-12");
        assertThat(getPartition(PartitionBy.DAY, 5, millis)).isEqualTo("2013-03-06");
        assertThat(getPartition(PartitionBy.WEEK, 5, millis)).isEqualTo("2013-02-18");

        assertThat(getPartition(PartitionBy.YEAR, 9, millis)).isEqualTo("2006");
        assertThat(getPartition(PartitionBy.MONTH, 9, millis)).isEqualTo("2012-10");
        assertThat(getPartition(PartitionBy.DAY, 9, millis)).isEqualTo("2013-03-04");
        assertThat(getPartition(PartitionBy.WEEK, 9, millis)).isEqualTo("2013-02-18");

        assertThat(getPartition(PartitionBy.YEAR, 10, millis)).isEqualTo("2010");
        assertThat(getPartition(PartitionBy.MONTH, 10, millis)).isEqualTo("2012-07");
        assertThat(getPartition(PartitionBy.DAY, 10, millis)).isEqualTo("2013-03-06");
        assertThat(getPartition(PartitionBy.WEEK, 10, millis)).isEqualTo("2013-02-18");

        // TODO : @66 TEST INCLUSIVE/EXCLUSIVE TIMES.....
    }

    @Test
    void testTimeBased2() {
        final String date = "2017-01-01T11:30:44.000Z";
        final long millis = DateUtil.parseNormalDateTimeString(date);
        assertThat(getPartition(PartitionBy.WEEK, 1, millis)).isEqualTo("2016-12-26");
    }

    private String getPartition(final PartitionBy partitionBy, final int partitionSize, final long millis) {
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .shardsPerPartition(5)
                .partitionBy(partitionBy)
                .partitionSize(partitionSize)
                .build();
        return new TimePartitionFactory().create(index, millis).getLabel();
    }
}
