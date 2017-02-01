/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.server;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;

import stroom.index.shared.Index;
import stroom.index.shared.Index.PartitionBy;
import stroom.index.shared.IndexShardKey;
import stroom.util.date.DateUtil;

public class TestIndexShardKeyUtil extends StroomUnitTest {
    @Test
    public void testMultishard() {
        final Index index = new Index();
        index.setShardsPerPartition(5);
        final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);

        Assert.assertEquals(index, indexShardKey.getIndex());
        Assert.assertTrue(indexShardKey.getShardNo() >= 0);
    }

    @Test
    public void testTimeBased() {
        final String date = "2013-03-07T12:33:44.000Z";
        final long millis = DateUtil.parseNormalDateTimeString(date);

        Assert.assertEquals("2013", getPartition(PartitionBy.YEAR, 1, millis));
        Assert.assertEquals("2013-03", getPartition(PartitionBy.MONTH, 1, millis));
        Assert.assertEquals("2013-03-07", getPartition(PartitionBy.DAY, 1, millis));
        Assert.assertEquals("2013-03-04", getPartition(PartitionBy.WEEK, 1, millis));

        Assert.assertEquals("2012", getPartition(PartitionBy.YEAR, 3, millis));
        Assert.assertEquals("2013-01", getPartition(PartitionBy.MONTH, 3, millis));
        Assert.assertEquals("2013-03-07", getPartition(PartitionBy.DAY, 3, millis));
        Assert.assertEquals("2013-02-18", getPartition(PartitionBy.WEEK, 3, millis));

        Assert.assertEquals("2010", getPartition(PartitionBy.YEAR, 5, millis));
        Assert.assertEquals("2012-12", getPartition(PartitionBy.MONTH, 5, millis));
        Assert.assertEquals("2013-03-06", getPartition(PartitionBy.DAY, 5, millis));
        Assert.assertEquals("2013-02-18", getPartition(PartitionBy.WEEK, 5, millis));

        Assert.assertEquals("2006", getPartition(PartitionBy.YEAR, 9, millis));
        Assert.assertEquals("2012-10", getPartition(PartitionBy.MONTH, 9, millis));
        Assert.assertEquals("2013-03-04", getPartition(PartitionBy.DAY, 9, millis));
        Assert.assertEquals("2013-02-18", getPartition(PartitionBy.WEEK, 9, millis));

        Assert.assertEquals("2010", getPartition(PartitionBy.YEAR, 10, millis));
        Assert.assertEquals("2012-07", getPartition(PartitionBy.MONTH, 10, millis));
        Assert.assertEquals("2013-03-06", getPartition(PartitionBy.DAY, 10, millis));
        Assert.assertEquals("2013-02-18", getPartition(PartitionBy.WEEK, 10, millis));
    }

    @Test
    public void testTimeBased2() {
        final String date = "2017-01-01T11:30:44.000Z";
        final long millis = DateUtil.parseNormalDateTimeString(date);
        Assert.assertEquals("2016-12-26", getPartition(PartitionBy.WEEK, 1, millis));
    }

    private String getPartition(final PartitionBy partitionBy, final int partitionSize, final long millis) {
        final Index index = new Index();
        index.setShardsPerPartition(5);
        index.setPartitionBy(partitionBy);
        index.setPartitionSize(partitionSize);
        final IndexShardKey indexShardKey = IndexShardKeyUtil.createTimeBasedPartition(index, millis);
        return indexShardKey.getPartition();
    }
}
