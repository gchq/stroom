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

package stroom.pipeline.xsltfunctions;

import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.MetaHolder;
import stroom.util.date.DateFormatterCache;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.DateTimeValue;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

class ParseDateTime extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "parse-dateTime";

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

    private final MetaHolder metaHolder;

    private final Map<Key, Function<String, Instant>> cachedParsers = new HashMap<>();

    private Instant baseTime;

    @Inject
    ParseDateTime(final MetaHolder metaHolder) {
        this.metaHolder = metaHolder;
    }

    @Override
    void configure(final ErrorReceiver errorReceiver,
                   final LocationFactory locationFactory,
                   final List<PipelineReference> pipelineReferences) {
        super.configure(errorReceiver, locationFactory, pipelineReferences);

        // Reset the parser cache.
        cachedParsers.clear();
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        DateTimeValue result = null;

        try {
            if (arguments.length >= 1 && arguments.length <= 3) {
                result = convertToStandardDateFormat(functionName, context, arguments);
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return result;
    }

    private DateTimeValue convertToStandardDateFormat(final String functionName, final XPathContext context,
                                               final Sequence[] arguments) throws XPathException {
        DateTimeValue result = null;
        final String value = getSafeString(functionName, context, arguments, 0);

        String pattern = null;
        if (arguments.length >= 2) {
            pattern = getSafeString(functionName, context, arguments, 1);
        }

        String timeZone = null;
        if (arguments.length == 3) {
            timeZone = getSafeString(functionName, context, arguments, 2);
        }

        // Parse the supplied date.
        Instant instant = null;
        try {
            // If the incoming pattern doesn't contain year then we might need to figure the year out for ourselves.
            instant = parseDate(context, value, pattern, timeZone);
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

        if (instant != null) {
            result = DateTimeValue.fromJavaInstant(instant);
        }

        return result;
    }

    private Instant parseDate(final XPathContext context, final String value, final String pattern,
                              final String timeZone) {
        // no pattern means iso 8061 format
        if (pattern == null) {
            return DateUtil.parseNormalDateTimeStringToInstant(value);
        }

        final Key key = new Key(pattern, timeZone);
        final Function<String, Instant> parser = cachedParsers.computeIfAbsent(key,
                k -> createParser(context, k.pattern, k.timeZone));
        return parser.apply(value);
    }

    private Function<String, Instant> createParser(final XPathContext context,
                                                final String pattern,
                                                final String timeZone) {
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
            || fieldSet.contains(WEEK_OF_MONTH)) {
            // Week based date parsing.
            return createWeekBasedParser(fieldSet, builder, zoneId);
        } else if (fieldSet.contains(DAY_OF_WEEK) && !fieldSet.contains(DAY_OF_MONTH)) {
            // Week based date parsing.
            return createWeekBasedParser(fieldSet, builder, zoneId);
        } else {
            // Regular date parsing.
            return createRegularParser(fieldSet, builder, zoneId);
        }
    }

    /**
     * Parsing dates that use week fields has to be done differently as week based parsing does not seem to
     * cope with conflicting default values for fields.
     */
    private Function<String, Instant> createWeekBasedParser(final FieldSet fieldSet,
                                                         final DateTimeFormatterBuilder builder,
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
                builder.parseDefaulting(WEEK_FIELDS.weekOfWeekBasedYear(),
                        referenceDateTime.get(WEEK_FIELDS.weekOfWeekBasedYear()));
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
        return new WeekBasedParser(fieldSet, formatter, zoneId, referenceDateTime);
    }

    private Function<String, Instant> createRegularParser(final FieldSet fieldSet,
                                                       final DateTimeFormatterBuilder builder,
                                                       final ZoneId zoneId) {
        // Don't use the defaulting formatter if we can help it.
        if ((fieldSet.contains(YEAR) || fieldSet.contains(YEAR_OF_ERA))
            && fieldSet.contains(MONTH_OF_YEAR)
            && fieldSet.contains(DAY_OF_MONTH)) {
            final DateTimeFormatter formatter = builder.toFormatter(LOCALE);
            return new RegularParser(formatter, zoneId);

        } else {
            final ZonedDateTime referenceDateTime = getBaseTime().atZone(zoneId);

            builder.parseDefaulting(ChronoField.YEAR_OF_ERA, referenceDateTime.get(ChronoField.YEAR_OF_ERA));
            builder.parseDefaulting(ChronoField.MONTH_OF_YEAR, referenceDateTime.get(ChronoField.MONTH_OF_YEAR));
            builder.parseDefaulting(ChronoField.DAY_OF_MONTH, referenceDateTime.get(ChronoField.DAY_OF_MONTH));

            final DateTimeFormatter formatter = builder.toFormatter(LOCALE);

            return new RegularParserWithReferenceTime(fieldSet, formatter, zoneId, referenceDateTime);
        }
    }

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

    // Pkg private for testing
    Instant getBaseTime() {
        if (baseTime == null) {
            baseTime = NullSafe.getOrElseGet(
                    metaHolder,
                    MetaHolder::getMeta,
                    Meta::getCreateMs,
                    Instant::ofEpochMilli,
                    Instant::now);
        }
        return baseTime;
    }

    private static ZonedDateTime parseBest(final String value, final DateTimeFormatter formatter, final ZoneId zoneId) {
        final TemporalAccessor temporalAccessor = formatter.parseBest(value,
                ZonedDateTime::from,
                LocalDateTime::from,
                LocalDate::from);
        if (temporalAccessor instanceof ZonedDateTime) {
            return ((ZonedDateTime) temporalAccessor).withZoneSameInstant(zoneId);
        }
        if (temporalAccessor instanceof LocalDateTime) {
            return ((LocalDateTime) temporalAccessor).atZone(zoneId);
        }
        return ((LocalDate) temporalAccessor).atStartOfDay(zoneId);
    }


    // --------------------------------------------------------------------------------


    private static class FieldSet {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FieldSet.class);

        private final String resolvedPattern;

        FieldSet(final DateTimeFormatter parseFormatter) {
            this.resolvedPattern = parseFormatter.toString();
            LOGGER.debug("resolvedPattern: {}", resolvedPattern);
        }

        private boolean contains(final String fieldName) {
            return resolvedPattern.contains("(" + fieldName + ",");
        }
    }


    // --------------------------------------------------------------------------------


    private static class Key {

        private final String pattern;
        private final String timeZone;

        Key(final String pattern, final String timeZone) {
            this.pattern = pattern;
            this.timeZone = timeZone;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = (Key) o;
            return Objects.equals(pattern, key.pattern) &&
                   Objects.equals(timeZone, key.timeZone);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pattern, timeZone);
        }
    }


    // --------------------------------------------------------------------------------


    private static class RegularParser implements Function<String, Instant> {

        private final DateTimeFormatter formatter;
        private final ZoneId zoneId;

        RegularParser(final DateTimeFormatter formatter,
                      final ZoneId zoneId) {
            this.formatter = formatter;
            this.zoneId = zoneId;
        }

        @Override
        public Instant apply(final String value) {
            // Parse the date as best we can.
            final ZonedDateTime dateTime = parseBest(value, formatter, zoneId);
            return dateTime.toInstant();
        }
    }


    // --------------------------------------------------------------------------------


    private static class RegularParserWithReferenceTime implements Function<String, Instant> {

        private final DateTimeFormatter formatter;
        private final ZoneId zoneId;
        private final ZonedDateTime referenceDateTime;
        private final Function<ZonedDateTime, ZonedDateTime> adjustment;

        RegularParserWithReferenceTime(final FieldSet fieldSet,
                                       final DateTimeFormatter formatter,
                                       final ZoneId zoneId,
                                       final ZonedDateTime referenceDateTime) {
            this.formatter = formatter;
            this.zoneId = zoneId;
            this.referenceDateTime = referenceDateTime;

            if (!fieldSet.contains(YEAR_OF_ERA)
                && !fieldSet.contains(YEAR)) {
                if (!fieldSet.contains(MONTH_OF_YEAR)) {
                    // Subtract a month if the date appears to be after our reference time and no month is provided.
                    adjustment = value -> value.minusMonths(1);
                } else {
                    // Subtract a year if the date appears to be after our reference time and no year is provided.
                    adjustment = value -> value.minusYears(1);
                }
            } else {
                adjustment = value -> value;
            }
        }

        @Override
        public Instant apply(final String value) {
            // Parse the date as best we can.
            ZonedDateTime dateTime = parseBest(value, formatter, zoneId);

            // Subtract a year if the date appears to be after our reference time.
            if (dateTime.isAfter(referenceDateTime)) {
                dateTime = adjustment.apply(dateTime);
            }

            return dateTime.toInstant();
        }
    }


    // --------------------------------------------------------------------------------


    private static class WeekBasedParser implements Function<String, Instant> {

        private final DateTimeFormatter formatter;
        private final ZoneId zoneId;
        private final ZonedDateTime referenceDateTime;
        private final Function<ZonedDateTime, ZonedDateTime> adjustment;

        WeekBasedParser(final FieldSet fieldSet,
                        final DateTimeFormatter formatter,
                        final ZoneId zoneId,
                        final ZonedDateTime referenceDateTime) {
            this.formatter = formatter;
            this.zoneId = zoneId;
            this.referenceDateTime = referenceDateTime;

            if (!fieldSet.contains(YEAR_OF_ERA)
                && !fieldSet.contains(YEAR)
                && !fieldSet.contains(WEEK_BASED_YEAR)) {
                if (!fieldSet.contains(MONTH_OF_YEAR)
                    && !fieldSet.contains(WEEK_OF_WEEK_BASED_YEAR)
                    && !fieldSet.contains(WEEK_OF_YEAR)) {
                    adjustment = value -> value.minusWeeks(1);
                } else {
                    adjustment = value -> value.minusWeeks(52);
                }
            } else {
                adjustment = value -> value;
            }
        }

        @Override
        public Instant apply(final String value) {
            // Parse the date as best we can.
            ZonedDateTime dateTime = parseBest(value, formatter, zoneId);

            // Subtract a year if the date appears to be after our reference time.
            if (dateTime.isAfter(referenceDateTime)) {
                dateTime = adjustment.apply(dateTime);
            }
            return dateTime.toInstant();
        }
    }
}
