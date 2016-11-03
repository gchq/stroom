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

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;

import stroom.index.shared.Index;
import stroom.index.shared.Index.PartitionBy;
import stroom.index.shared.IndexShardKey;
import stroom.util.concurrent.AtomicSequence;
import stroom.util.date.DateUtil;

public final class IndexShardKeyUtil {
    private static final String ALL = "all";
    private static final AtomicSequence SEQUENCE = new AtomicSequence();

    private IndexShardKeyUtil() {
        // Utility class
    }

    public static IndexShardKey createTestKey(final Index index) {
        final String partition = ALL;
        final int shardNo = SEQUENCE.next(index.getShardsPerPartition());

        return new IndexShardKey(index, partition, null, null, shardNo);
    }

    public static IndexShardKey createTimeBasedPartition(final Index index, final long timeMs) {
        final int shardNo = SEQUENCE.next(index.getShardsPerPartition());
        return createTimeBasedKey(index, timeMs, shardNo);
    }

    public static IndexShardKey createTimeBasedKey(final Index index, final long timeMs, final int shardNo) {
        String partition = ALL;
        DateTime dateFrom = null;
        DateTime dateTo = null;

        if (index.getPartitionBy() != null && index.getPartitionSize() > 0) {
            dateFrom = new DateTime(timeMs);
            if (PartitionBy.YEAR.equals(index.getPartitionBy())) {
                int year = dateFrom.get(DateTimeFieldType.year());
                year = fix(year, index.getPartitionSize());

                dateFrom = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                dateTo = dateFrom.plusYears(index.getPartitionSize());
                partition = DateUtil.createFileDateTimeString(dateFrom.getMillis());
                partition = partition.substring(0, 4);

            } else if (PartitionBy.MONTH.equals(index.getPartitionBy())) {
                final int year = dateFrom.get(DateTimeFieldType.year());
                int month = dateFrom.get(DateTimeFieldType.monthOfYear());
                month = fix(month, index.getPartitionSize());
                if (month < 1) {
                    month = 1;
                }

                dateFrom = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                dateFrom = dateFrom.plusMonths(month - 1);
                dateTo = dateFrom.plusMonths(index.getPartitionSize());
                partition = DateUtil.createFileDateTimeString(dateFrom.getMillis());
                partition = partition.substring(0, 7);

            } else if (PartitionBy.WEEK.equals(index.getPartitionBy())) {
                final int year = dateFrom.get(DateTimeFieldType.year());
                int week = dateFrom.get(DateTimeFieldType.weekOfWeekyear());
                week = fix(week, index.getPartitionSize());
                if (week < 1) {
                    week = 1;
                }

                dateFrom = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                dateFrom = dateFrom.plusWeeks(week - 1);
                dateTo = dateFrom.plusWeeks(index.getPartitionSize());
                partition = DateUtil.createFileDateTimeString(dateFrom.getMillis());
                partition = partition.substring(0, 10);

            } else if (PartitionBy.DAY.equals(index.getPartitionBy())) {
                final int year = dateFrom.get(DateTimeFieldType.year());
                int day = dateFrom.get(DateTimeFieldType.dayOfYear());
                day = fix(day, index.getPartitionSize());
                if (day < 1) {
                    day = 1;
                }

                dateFrom = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                dateFrom = dateFrom.plusDays(day - 1);
                dateTo = dateFrom.plusDays(index.getPartitionSize());
                partition = DateUtil.createFileDateTimeString(dateFrom.getMillis());
                partition = partition.substring(0, 10);
            }
        }

        Long partitionFromTime = null;
        if (dateFrom != null) {
            partitionFromTime = dateFrom.getMillis();
        }

        Long partitionToTime = null;
        if (dateTo != null) {
            partitionToTime = dateTo.getMillis();
        }

        return new IndexShardKey(index, partition, partitionFromTime, partitionToTime, shardNo);
    }

    private static String getTimeBasedPartitionName(final Index index, final long timeMs) {
        String partition = ALL;
        if (index.getPartitionBy() != null && index.getPartitionSize() > 0) {
            DateTime dateTime = new DateTime(timeMs);

            if (PartitionBy.YEAR.equals(index.getPartitionBy())) {
                int year = dateTime.get(DateTimeFieldType.year());
                year = fix(year, index.getPartitionSize());

                dateTime = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                partition = DateUtil.createFileDateTimeString(dateTime.getMillis());
                partition = partition.substring(0, 4);

            } else if (PartitionBy.MONTH.equals(index.getPartitionBy())) {
                final int year = dateTime.get(DateTimeFieldType.year());
                int month = dateTime.get(DateTimeFieldType.monthOfYear());
                month = fix(month, index.getPartitionSize());
                if (month < 1) {
                    month = 1;
                }

                dateTime = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                dateTime = dateTime.plusMonths(month - 1);
                partition = DateUtil.createFileDateTimeString(dateTime.getMillis());
                partition = partition.substring(0, 7);

            } else if (PartitionBy.WEEK.equals(index.getPartitionBy())) {
                final int year = dateTime.get(DateTimeFieldType.year());
                int week = dateTime.get(DateTimeFieldType.weekOfWeekyear());
                week = fix(week, index.getPartitionSize());
                if (week < 1) {
                    week = 1;
                }

                dateTime = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                dateTime = dateTime.plusWeeks(week - 1);
                partition = DateUtil.createFileDateTimeString(dateTime.getMillis());
                partition = partition.substring(0, 10);

            } else if (PartitionBy.DAY.equals(index.getPartitionBy())) {
                final int year = dateTime.get(DateTimeFieldType.year());
                int day = dateTime.get(DateTimeFieldType.dayOfYear());
                day = fix(day, index.getPartitionSize());
                if (day < 1) {
                    day = 1;
                }

                dateTime = new DateTime(year, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                dateTime = dateTime.plusDays(day - 1);
                partition = DateUtil.createFileDateTimeString(dateTime.getMillis());
                partition = partition.substring(0, 10);
            }
        }

        return partition;
    }

    private static int fix(int value, final int partitionSize) {
        value = value / partitionSize;
        value = value * partitionSize;
        return value;
    }
}
