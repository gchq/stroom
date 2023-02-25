package stroom.index.impl;

import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexDoc.PartitionBy;
import stroom.index.shared.TimePartition;

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

public class TimePartitionFactory {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
    private static final TemporalField DAY_OF_WEEK = WeekFields.of(Locale.UK).dayOfWeek();

    public TimePartition create(final IndexDoc index, final long timeMs) {
        final PartitionBy partitionBy = index.getPartitionBy();
        final int partitionSize = index.getPartitionSize() == null
                ? 1
                : Math.max(1, index.getPartitionSize());

        LocalDate dateFrom = Instant.ofEpochMilli(timeMs).atZone(UTC).toLocalDate();
        LocalDate dateTo;
        String label;

        if (PartitionBy.YEAR.equals(partitionBy)) {
            // Truncate to first day of the year.
            dateFrom = dateFrom.withDayOfYear(1);
            // Round down to number of years since epoch.
            dateFrom = roundDown(dateFrom, ChronoUnit.YEARS, partitionSize);

            dateTo = dateFrom.plusYears(partitionSize);
            label = dateFrom.format(YEAR_FORMAT);

        } else if (PartitionBy.MONTH.equals(partitionBy)) {
            // Truncate to first day of the month.
            dateFrom = dateFrom.withDayOfMonth(1);

            // Round down to number of months since epoch.
            dateFrom = roundDown(dateFrom, ChronoUnit.MONTHS, partitionSize);

            dateTo = dateFrom.plusMonths(partitionSize);
            label = dateFrom.format(MONTH_FORMAT);

        } else if (PartitionBy.WEEK.equals(partitionBy)) {
            // Adjust to first day of the week.
            dateFrom = dateFrom.with(DAY_OF_WEEK, 1);

            // Round down to number of weeks since epoch.
            dateFrom = roundDown(dateFrom, ChronoUnit.WEEKS, partitionSize);

            dateTo = dateFrom.plusWeeks(partitionSize);
            label = dateFrom.format(DAY_FORMAT);

        } else if (PartitionBy.DAY.equals(partitionBy)) {
            // Round down to number of days since epoch.
            dateFrom = roundDown(dateFrom, ChronoUnit.DAYS, partitionSize);

            dateTo = dateFrom.plusDays(partitionSize);
            label = dateFrom.format(DAY_FORMAT);
        } else {
            throw new RuntimeException("Unknown partition by setting: " + partitionBy);
        }

        final long partitionFromTime = dateFrom.atStartOfDay(UTC).toInstant().toEpochMilli();
        final long partitionToTime = dateTo.atStartOfDay(UTC).toInstant().toEpochMilli();

        return new TimePartition(
                partitionBy,
                partitionSize,
                partitionFromTime,
                partitionToTime,
                label);
    }

    private LocalDate roundDown(final LocalDate dateTime, final TemporalUnit temporalUnit, final int size) {
        LocalDate epoch = LocalDate.ofEpochDay(0);
        long count = temporalUnit.between(epoch, dateTime);
        long round = count / size * size;
        long diff = round - count;
        return dateTime.plus(diff, temporalUnit);
    }
}
