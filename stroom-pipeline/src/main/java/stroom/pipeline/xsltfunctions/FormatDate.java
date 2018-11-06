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

package stroom.pipeline.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import stroom.pipeline.state.StreamHolder;
import stroom.util.date.DateFormatterCache;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.WeekFields;
import java.util.Locale;

class FormatDate extends StroomExtensionFunctionCall {
    private static final Locale LOCALE = Locale.ENGLISH;
    private static final WeekFields WEEK_FIELDS = WeekFields.of(LOCALE);

    private static final String DAY_OF_WEEK = "DayOfWeek";
    private static final String WEEK_OF_MONTH = "WeekOfMonth";
    private static final String WEEK_OF_YEAR = "WeekOfYear";
    private static final String WEEK_OF_WEEK_BASED_YEAR = "WeekOfWeekBasedYear";
    private static final String WEEK_BASED_YEAR = "WeekBasedYear";

    private static final String YEAR_OF_ERA = "YearOfEra";
    private static final String YEAR = "Year";
    private static final String MONTH_OF_YEAR = "MonthOfYear";
    private static final String DAY_OF_MONTH = "DayOfMonth";

    private final StreamHolder streamHolder;

    private Instant baseTime;

    @Inject
    FormatDate(final StreamHolder streamHolder) {
        this.streamHolder = streamHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            if (arguments.length == 1) {
                result = convertMilliseconds(functionName, context, arguments);

            } else if (arguments.length >= 2 && arguments.length <= 3) {
                result = convertToStandardDateFormat(functionName, context, arguments);

            } else if (arguments.length >= 4 && arguments.length <= 5) {
                result = convertToSpecifiedDateFormat(functionName, context, arguments);
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }

    private String convertMilliseconds(final String functionName, final XPathContext context,
                                       final Sequence[] arguments) throws XPathException {
        String result = null;
        final String milliseconds = getSafeString(functionName, context, arguments, 0);

        try {
            final long ms = Long.parseLong(milliseconds);
            result = DateUtil.createNormalDateTimeString(ms);

        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Failed to parse date: \"");
            sb.append(milliseconds);
            sb.append('"');
            outputWarning(context, sb, e);
        }

        return result;
    }

    private String convertToStandardDateFormat(final String functionName, final XPathContext context,
                                               final Sequence[] arguments) throws XPathException {
        String result = null;
        final String value = getSafeString(functionName, context, arguments, 0);
        final String pattern = getSafeString(functionName, context, arguments, 1);
        String timeZone = null;
        if (arguments.length == 3) {
            timeZone = getSafeString(functionName, context, arguments, 2);
        }

        // Parse the supplied date.
        long ms = -1;
        try {
            // If the incoming pattern doesn't contain year then we might need to figure the year out for ourselves.
            ms = parseDate(context, value, pattern, timeZone);
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Failed to parse date: \"");
            sb.append(value);
            sb.append("\" (Pattern: ");
            sb.append(pattern);
            sb.append(", Time Zone: ");
            sb.append(timeZone);
            sb.append(")");
            outputWarning(context, sb, e);
        }

        if (ms != -1) {
            result = DateUtil.createNormalDateTimeString(ms);
        }

        return result;
    }

    private String convertToSpecifiedDateFormat(final String functionName, final XPathContext context,
                                                final Sequence[] arguments) throws XPathException {
        String result = null;
        final String value = getSafeString(functionName, context, arguments, 0);
        final String patternIn = getSafeString(functionName, context, arguments, 1);
        final String timeZoneIn = getSafeString(functionName, context, arguments, 2);
        final String patternOut = getSafeString(functionName, context, arguments, 3);
        String timeZoneOut = null;
        if (arguments.length == 5) {
            timeZoneOut = getSafeString(functionName, context, arguments, 4);
        }

        // Parse the supplied date.
        long ms = -1;
        try {
            // If the incoming pattern doesn't contain year then we might need to figure the year out for ourselves.
            ms = parseDate(context, value, patternIn, timeZoneIn);
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Failed to parse date: \"");
            sb.append(value);
            sb.append("\" (Pattern: ");
            sb.append(patternIn);
            sb.append(", Time Zone: ");
            sb.append(timeZoneIn);
            sb.append(")");
            outputWarning(context, sb, e);
        }

        if (ms != -1) {
            // Resolve the output time zone.
            final ZoneId zoneId = getTimeZone(context, timeZoneOut);
            try {
                // Now format the date using the specified pattern and time
                // zone.
                final ZonedDateTime dateTime = Instant.ofEpochMilli(ms).atZone(zoneId);
                final DateTimeFormatter dateTimeFormatter = DateFormatterCache.getFormatter(patternOut);
                result = dateTimeFormatter.format(dateTime);
            } catch (final RuntimeException e) {
                final StringBuilder sb = new StringBuilder();
                sb.append("Failed to format date: \"");
                sb.append(value);
                sb.append("\" (Pattern: ");
                sb.append(patternOut);
                sb.append(", Time Zone: ");
                sb.append(timeZoneOut);
                sb.append(")");
                outputWarning(context, sb, e);
            }
        }

        return result;
    }

    // TODO : @66 Reinstate date format caching.

    long parseDate(final XPathContext context, final String timeZone, final String pattern, final String value) {
        final ZoneId zoneId = getTimeZone(context, timeZone);
        final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
                .parseLenient()
                .parseCaseInsensitive()
                .appendPattern(pattern);

        final DateTimeFormatter parseFormatter = builder.toFormatter(LOCALE);
        final FieldSet fieldSet = new FieldSet(parseFormatter);

        // See if we need to deal with week based date parsing.
        if (fieldSet.contains(WEEK_BASED_YEAR)
                || fieldSet.contains(WEEK_OF_WEEK_BASED_YEAR)
                || fieldSet.contains(WEEK_OF_YEAR)
                || fieldSet.contains(WEEK_OF_MONTH)
                || fieldSet.contains(DAY_OF_WEEK)) {

            // Week based date parsing.
            return parseWeekBasedDate(fieldSet, builder, value, zoneId);
        }

        // Regular date parsing.
        return parseRegularDate(fieldSet, builder, value, zoneId);
    }

    /**
     * Parsing dates that use week fields has to be done differently as week based parsing does not seem to cope with conflicting default values for fields.
     */
    private long parseWeekBasedDate(final FieldSet fieldSet,
                                    final DateTimeFormatterBuilder builder,
                                    final String value,
                                    final ZoneId zoneId) {
        final ZonedDateTime referenceDateTime = getBaseTime().atZone(zoneId);

        if (!fieldSet.contains(WEEK_BASED_YEAR)
                && !fieldSet.contains(YEAR_OF_ERA)
                && !fieldSet.contains(YEAR)) {
            builder.parseDefaulting(WEEK_FIELDS.weekBasedYear(), referenceDateTime.get(WEEK_FIELDS.weekBasedYear()));
        }
        if (!fieldSet.contains(WEEK_OF_WEEK_BASED_YEAR)) {
            if (!fieldSet.contains(WEEK_OF_YEAR)
                    && !fieldSet.contains(WEEK_OF_MONTH)) {
                builder.parseDefaulting(WEEK_FIELDS.weekOfWeekBasedYear(), referenceDateTime.get(WEEK_FIELDS.weekOfWeekBasedYear()));
            } else if (fieldSet.contains(WEEK_OF_MONTH)) {
                builder.parseDefaulting(ChronoField.MONTH_OF_YEAR, referenceDateTime.get(ChronoField.MONTH_OF_YEAR));
                builder.parseDefaulting(ChronoField.YEAR_OF_ERA, referenceDateTime.get(ChronoField.YEAR_OF_ERA));
            } else {
                builder.parseDefaulting(ChronoField.YEAR_OF_ERA, referenceDateTime.get(ChronoField.YEAR_OF_ERA));
            }
        }
        if (!fieldSet.contains(DAY_OF_WEEK)) {
            builder.parseDefaulting(WEEK_FIELDS.dayOfWeek(), referenceDateTime.get(WEEK_FIELDS.dayOfWeek()));
        }

        final DateTimeFormatter formatter = builder.toFormatter(LOCALE);

        // Parse the date as best we can.
        ZonedDateTime dateTime = parseBest(value, formatter, zoneId);

        // Subtract a year if the date appears to be after our reference time.
        if (dateTime.isAfter(referenceDateTime)) {
            if (!fieldSet.contains(YEAR_OF_ERA)
                    && !fieldSet.contains(YEAR)
                    && !fieldSet.contains(WEEK_BASED_YEAR)) {
                if (!fieldSet.contains(MONTH_OF_YEAR)
                        && !fieldSet.contains(WEEK_OF_WEEK_BASED_YEAR)
                        && !fieldSet.contains(WEEK_OF_YEAR)) {
                    dateTime = dateTime.minusWeeks(1);
                } else {
                    dateTime = dateTime.minusWeeks(52);
                }
            }
        }
        return dateTime.toInstant().toEpochMilli();
    }

    private long parseRegularDate(final FieldSet fieldSet,
                                  final DateTimeFormatterBuilder builder,
                                  final String value,
                                  final ZoneId zoneId) {
        ZonedDateTime dateTime;

        // Don't use the defaulting formatter if we can help it.
        if ((fieldSet.contains(YEAR) || fieldSet.contains(YEAR_OF_ERA))
                && fieldSet.contains(MONTH_OF_YEAR)
                && fieldSet.contains(DAY_OF_MONTH)) {
            final DateTimeFormatter formatter = builder.toFormatter(LOCALE);

            // Parse the date as best we can.
            dateTime = parseBest(value, formatter, zoneId);

        } else {
            final ZonedDateTime referenceDateTime = getBaseTime().atZone(zoneId);

            builder.parseDefaulting(ChronoField.YEAR_OF_ERA, referenceDateTime.get(ChronoField.YEAR_OF_ERA));
            builder.parseDefaulting(ChronoField.MONTH_OF_YEAR, referenceDateTime.get(ChronoField.MONTH_OF_YEAR));
            builder.parseDefaulting(ChronoField.DAY_OF_MONTH, referenceDateTime.get(ChronoField.DAY_OF_MONTH));

            final DateTimeFormatter formatter = builder.toFormatter(LOCALE);

            // Parse the date as best we can.
            dateTime = parseBest(value, formatter, zoneId);

            // Subtract a year if the date appears to be after our reference time.
            if (dateTime.isAfter(referenceDateTime)) {
                if (!fieldSet.contains(YEAR_OF_ERA)
                        && !fieldSet.contains(YEAR)) {
                    if (!fieldSet.contains(MONTH_OF_YEAR)) {
                        dateTime = dateTime.minusMonths(1);
                    } else {
                        dateTime = dateTime.minusYears(1);
                    }
                }
            }
        }

        return dateTime.toInstant().toEpochMilli();
    }

//    long parseDate(final XPathContext context, final String value, final String pattern, final String timeZone) {
//        ZonedDateTime dateTime;
//
//        // Don't use the defaulting formatter if we can help it.
//        if (pattern.contains(FULL_YEAR_PATTERN) && pattern.contains(FULL_MONTH_PATTERN) && pattern.contains(FULL_DAY_PATTERN)) {
//            final DateTimeFormatter formatter = DateFormatterCache.getFormatter(pattern);
//            final ZoneId zoneId = getTimeZone(context, timeZone);
//
//            // Parse the date as best we can.
//            dateTime = DateUtil.parseBest(value, formatter, zoneId);
//
//        } else {
//            final ZoneId zoneId = getTimeZone(context, timeZone);
//            final ZonedDateTime referenceDateTime = getBaseTime();
//            final DateTimeFormatter formatter = DateFormatterCache.getDefaultingFormatter(pattern, referenceDateTime);
//
//            // Parse the date as best we can.
//            dateTime = DateUtil.parseBest(value, formatter, zoneId);
//
//            // Subtract a year if the date appears to be after our reference time.
//            if (dateTime.isAfter(referenceDateTime)) {
//                if (!pattern.contains("y")) {
//                    if (!pattern.contains("M")) {
//                        dateTime = dateTime.minusMonths(1);
//                    } else {
//                        dateTime = dateTime.minusYears(1);
//                    }
//                }
//            }
//        }
//
//        return dateTime.toInstant().toEpochMilli();
//    }

    private ZoneId getTimeZone(final XPathContext context, final String timeZone) {
        try {
            return DateFormatterCache.getZoneId(timeZone);
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Time Zone '");
            sb.append(timeZone);
            sb.append("' is not recognised, defaulting to UTC");
            outputWarning(context, sb, e);
        }
        return ZoneOffset.UTC;
    }

    private Instant getBaseTime() {
        if (baseTime == null) {
            Long createMs = null;
            if (streamHolder != null) {
                if (streamHolder.getStream() != null) {
                    createMs = streamHolder.getStream().getCreateMs();
                }
            }
            if (createMs != null) {
                baseTime = Instant.ofEpochMilli(createMs);
            } else {
                baseTime = Instant.now();
            }
        }
        return baseTime;
    }

    private ZonedDateTime parseBest(final String value, final DateTimeFormatter formatter, final ZoneId zoneId) {
        final TemporalAccessor temporalAccessor = formatter.parseBest(value, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
        if (temporalAccessor instanceof ZonedDateTime) {
            return ((ZonedDateTime) temporalAccessor).withZoneSameInstant(zoneId);
        }
        if (temporalAccessor instanceof LocalDateTime) {
            return ((LocalDateTime) temporalAccessor).atZone(zoneId);
        }
        return ((LocalDate) temporalAccessor).atStartOfDay(zoneId);
    }

    private static class FieldSet {
        private final String resolvedPattern;

        FieldSet(final DateTimeFormatter parseFormatter) {
            this.resolvedPattern = parseFormatter.toString();
        }

        private boolean contains(final String fieldName) {
            return resolvedPattern.contains("(" + fieldName + ",");
        }
    }
}
