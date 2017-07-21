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

import stroom.index.shared.Index;
import stroom.index.shared.Index.PartitionBy;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.concurrent.AtomicSequence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class IndexShardKeyUtil {
    private static final String ALL = "all";
    private static final AtomicSequence SEQUENCE = new AtomicSequence();

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final TemporalField DAY_OF_WEEK = WeekFields.of(Locale.UK).dayOfWeek();

    private IndexShardKeyUtil() {
        // Utility class
    }

    public static IndexShardKey createKey(final IndexShard indexShard) {
        final int shardNo = SEQUENCE.next(indexShard.getIndex().getShardsPerPartition());
        return new IndexShardKey(indexShard.getIndex(), indexShard.getPartition(), indexShard.getPartitionFromTime(), indexShard.getPartitionToTime(), shardNo);
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

        LocalDate dateFrom = null;
        LocalDate dateTo = null;

        if (index.getPartitionBy() != null && index.getPartitionSize() > 0) {
            dateFrom = Instant.ofEpochMilli(timeMs).atZone(UTC).toLocalDate();

            if (PartitionBy.YEAR.equals(index.getPartitionBy())) {
                // Truncate to first day of the year.
                dateFrom = dateFrom.withDayOfYear(1);
                // Round down to number of years since epoch.
                dateFrom = roundDown(dateFrom, ChronoUnit.YEARS, index.getPartitionSize());

                dateTo = dateFrom.plusYears(index.getPartitionSize());
                partition = dateFrom.format(YEAR_FORMAT);

            } else if (PartitionBy.MONTH.equals(index.getPartitionBy())) {
                // Truncate to first day of the month.
                dateFrom = dateFrom.withDayOfMonth(1);

                // Round down to number of months since epoch.
                dateFrom = roundDown(dateFrom, ChronoUnit.MONTHS, index.getPartitionSize());

                dateTo = dateFrom.plusMonths(index.getPartitionSize());
                partition = dateFrom.format(MONTH_FORMAT);

            } else if (PartitionBy.WEEK.equals(index.getPartitionBy())) {
                // Adjust to first day of the week.
                dateFrom = dateFrom.with(DAY_OF_WEEK, 1);

                // Round down to number of weeks since epoch.
                dateFrom = roundDown(dateFrom, ChronoUnit.WEEKS, index.getPartitionSize());

                dateTo = dateFrom.plusWeeks(index.getPartitionSize());
                partition = dateFrom.format(DAY_FORMAT);

            } else if (PartitionBy.DAY.equals(index.getPartitionBy())) {
                // Round down to number of days since epoch.
                dateFrom = roundDown(dateFrom, ChronoUnit.DAYS, index.getPartitionSize());

                dateTo = dateFrom.plusDays(index.getPartitionSize());
                partition = dateFrom.format(DAY_FORMAT);
            }
        }

        Long partitionFromTime = null;
        if (dateFrom != null) {
            partitionFromTime = dateFrom.atStartOfDay(UTC).toInstant().toEpochMilli();
        }

        Long partitionToTime = null;
        if (dateTo != null) {
            partitionToTime = dateTo.atStartOfDay(UTC).toInstant().toEpochMilli();
        }

        return new IndexShardKey(index, partition, partitionFromTime, partitionToTime, shardNo);
    }

    private static LocalDate roundDown(final LocalDate dateTime, final TemporalUnit temporalUnit, final int size) {
        LocalDate epoch = LocalDate.ofEpochDay(0);
        long count = temporalUnit.between(epoch, dateTime);
        long round = count / size * size;
        long diff = round - count;
        return dateTime.plus(diff, temporalUnit);
    }
}
