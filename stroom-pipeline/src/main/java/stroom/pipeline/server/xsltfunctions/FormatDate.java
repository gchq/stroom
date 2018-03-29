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

package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.state.StreamHolder;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

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
import java.util.Locale;

@Component
@Scope(StroomScope.PROTOTYPE)
class FormatDate extends StroomExtensionFunctionCall {
    private static final String GMT_BST_GUESS = "GMT/BST";
    private static final ZoneId EUROPE_LONDON_TIME_ZONE = ZoneId.of("Europe/London");
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
        } catch (final Exception e) {
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

        } catch (final Throwable e) {
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
        final String date = getSafeString(functionName, context, arguments, 0);
        final String pattern = getSafeString(functionName, context, arguments, 1);
        String timeZone = null;
        if (arguments.length == 3) {
            timeZone = getSafeString(functionName, context, arguments, 2);
        }

        // Parse the supplied date.
        long ms = -1;
        try {
            // If the incoming pattern doesn't contain year then we might need to figure the year out for ourselves.
            ms = parseDate(context, timeZone, pattern, date);
        } catch (final Throwable e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Failed to parse date: \"");
            sb.append(date);
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
        final String date = getSafeString(functionName, context, arguments, 0);
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
            ms = parseDate(context, timeZoneIn, patternIn, date);
        } catch (final Throwable e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Failed to parse date: \"");
            sb.append(date);
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
            if (zoneId != null) {
                try {
                    // Now format the date using the specified pattern and time
                    // zone.
                    final ZonedDateTime dateTime = Instant.ofEpochMilli(ms).atZone(zoneId);
                    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(patternOut);
                    result = dateTimeFormatter.format(dateTime);
                } catch (final Throwable e) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("Failed to format date: \"");
                    sb.append(date);
                    sb.append("\" (Pattern: ");
                    sb.append(patternOut);
                    sb.append(", Time Zone: ");
                    sb.append(timeZoneOut);
                    sb.append(")");
                    outputWarning(context, sb, e);
                }
            }
        }

        return result;
    }

    long parseDate(final XPathContext context, final String timeZone, final String pattern, final String date) {
        final ZoneId zoneId = getTimeZone(context, timeZone);
        final ZonedDateTime referenceDateTime = getBaseTime().atZone(zoneId);
        final DateTimeFormatter parseFormatter = new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .parseDefaulting(ChronoField.YEAR_OF_ERA, referenceDateTime.get(ChronoField.YEAR_OF_ERA))
                .parseDefaulting(ChronoField.MONTH_OF_YEAR, referenceDateTime.get(ChronoField.MONTH_OF_YEAR))
                .parseDefaulting(ChronoField.DAY_OF_MONTH, referenceDateTime.get(ChronoField.DAY_OF_MONTH))
                .toFormatter(Locale.ENGLISH);

        // Parse the date as best we can.
        ZonedDateTime dateTime;
        final TemporalAccessor temporalAccessor = parseFormatter.parseBest(date, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
        if (temporalAccessor instanceof ZonedDateTime) {
            dateTime = ((ZonedDateTime) temporalAccessor).withZoneSameInstant(zoneId);
        } else if (temporalAccessor instanceof LocalDateTime) {
            dateTime = ((LocalDateTime) temporalAccessor).atZone(zoneId);
        } else {
            dateTime = ((LocalDate) temporalAccessor).atStartOfDay(zoneId);
        }

        // Subtract a year if the date appears to be after our reference time.
        if (dateTime.isAfter(referenceDateTime)) {
            if (!pattern.contains("y")) {
                if (!pattern.contains("M")) {
                    dateTime = dateTime.minusMonths(1);
                } else {
                    dateTime = dateTime.minusYears(1);
                }
            }
        }
        return dateTime.toInstant().toEpochMilli();
    }

    private ZoneId getTimeZone(final XPathContext context, final String timeZone) {
        ZoneId dateTimeZone = null;
        try {
            if (timeZone != null) {
                if (GMT_BST_GUESS.equals(timeZone)) {
                    dateTimeZone = EUROPE_LONDON_TIME_ZONE;
                } else {
                    dateTimeZone = ZoneId.of(timeZone);
                }
            } else {
                dateTimeZone = ZoneOffset.UTC;
            }
        } catch (final IllegalArgumentException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Time Zone '");
            sb.append(timeZone);
            sb.append("' is not recognised");
            outputWarning(context, sb, e);
        }

        return dateTimeZone;
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
}
