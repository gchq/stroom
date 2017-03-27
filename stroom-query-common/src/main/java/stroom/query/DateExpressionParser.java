/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateExpressionParser {
    private static final Pattern DURATION_PATTERN = Pattern.compile("[+-]?[ ]*\\d+[ ]*[smhdwMy]");

    private DateExpressionParser() {
    }

    public static Optional<ZonedDateTime> parse(final String expression, final long nowEpochMilli) {
        return parse(expression, ZoneOffset.UTC.getId(), nowEpochMilli);
    }

    public static Optional<ZonedDateTime> parse(final String expression, final String timeZoneId, final long nowEpochMilli) {
        final char[] chars = expression.toCharArray();
        final Part[] parts = new Part[chars.length];

        parseConstants(chars, parts, nowEpochMilli);
        parseDurations(chars, parts);

        // Find index of any remaining date.
        int index = -1;
        for (int i = 0; i < chars.length && index == -1; i++) {
            if (chars[i] != ' ') {
                index = i;
            }
        }

        if (index != -1) {
            final String trimmed = new String(chars).trim();
            ZonedDateTime time;

            try {
                // Assume a timezone is specified on the string.
                time = ZonedDateTime.parse(trimmed);
            } catch (final DateTimeParseException e) {
                ZoneId zoneId = ZoneId.systemDefault();

                try {
                    if (timeZoneId != null) {
                        zoneId = ZoneId.of(timeZoneId);
                    }
                } catch (final Exception ex) {
                }

                // If no time zone was specified then try and parse as a local datetime.
                time = LocalDateTime.parse(trimmed).atZone(zoneId);
            }

            parts[index] = new Part(trimmed, time);
        }

        // Now validate and try and perform date calculation.
        ZonedDateTime time = null;

        Sign sign = Sign.PLUS;
        for (final Part part : parts) {
            if (part != null) {
                if (part.getObject() instanceof ZonedDateTime) {
                    if (time != null) {
                        throw new DateTimeException("Attempt to set the date and time twice with '" + part.toString() + "'. You cannot have more than one declaration of date and time");
                    }

                    time = (ZonedDateTime) part.getObject();

                } else if (part.getObject() instanceof MyDuration) {
                    if (time == null) {
                        throw new DateTimeException("You must specify a time or time constant before adding or subtracting duration '" + part.toString() + "'.");
                    }

                    final MyDuration duration = (MyDuration) part.getObject();
                    if (Sign.PLUS.equals(sign)) {
                        switch (duration.getType()) {
                            case 's':
                                time = time.plusSeconds(duration.getValue());
                                break;
                            case 'm':
                                time = time.plusMinutes(duration.getValue());
                                break;
                            case 'h':
                                time = time.plusHours(duration.getValue());
                                break;
                            case 'd':
                                time = time.plusDays(duration.getValue());
                                break;
                            case 'w':
                                time = time.plusWeeks(duration.getValue());
                                break;
                            case 'M':
                                time = time.plusMonths(duration.getValue());
                                break;
                            case 'y':
                                time = time.plusYears(duration.getValue());
                                break;
                            default:
                                throw new DateTimeException("Unknown duration type '" + duration.getType() + "'.");
                        }
                    } else {
                        switch (duration.getType()) {
                            case 's':
                                time = time.minusSeconds(duration.getValue());
                                break;
                            case 'm':
                                time = time.minusMinutes(duration.getValue());
                                break;
                            case 'h':
                                time = time.minusHours(duration.getValue());
                                break;
                            case 'd':
                                time = time.minusDays(duration.getValue());
                                break;
                            case 'w':
                                time = time.minusWeeks(duration.getValue());
                                break;
                            case 'M':
                                time = time.minusMonths(duration.getValue());
                                break;
                            case 'y':
                                time = time.minusYears(duration.getValue());
                                break;
                            default:
                                throw new DateTimeException("Unknown duration type '" + duration.getType() + "'.");
                        }
                    }

                } else if (part.getObject() instanceof Sign) {
                    sign = (Sign) part.getObject();
                }
            }
        }

        return Optional.ofNullable(time);
    }

    private static void parseConstants(final char[] chars, final Part[] parts, final long nowEpochMilli) {
        final ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMilli), ZoneOffset.UTC);
        final String expression = new String(chars);
        for (final DatePoint datePoint : DatePoint.values()) {
            final String function = datePoint.getFunction();

            int start = expression.indexOf(function, 0);
            while (start != -1) {
                final int end = start + function.length();

                // Obliterate the matched part of the expression so it can't be matched by any other matcher.
                Arrays.fill(chars, start, end, ' ');

                ZonedDateTime time = null;
                switch (datePoint) {
                    case NOW:
                        time = now;
                        break;
                    case SECOND:
                        time = now.truncatedTo(ChronoUnit.SECONDS);
                        break;
                    case MINUTE:
                        time = now.truncatedTo(ChronoUnit.MINUTES);
                        break;
                    case HOUR:
                        time = now.truncatedTo(ChronoUnit.HOURS);
                        break;
                    case DAY:
                        time = now.truncatedTo(ChronoUnit.DAYS);
                        break;
                    case WEEK:
                        TemporalField fieldISO = WeekFields.of(Locale.UK).dayOfWeek();
                        time = now.with(fieldISO, 1); // Monday
                        time = time.truncatedTo(ChronoUnit.DAYS);
                        break;
                    case MONTH:
                        time = ZonedDateTime.of(now.getYear(), now.getMonthValue(), 1, 0, 0, 0, 0, now.getZone());
                        break;
                    case YEAR:
                        time = ZonedDateTime.of(now.getYear(), 1, 1, 0, 0, 0, 0, now.getZone());
                        break;
                }

                parts[start] = new Part(function, time);

                start = expression.indexOf(function, end);
            }
        }
    }

    private static void parseDurations(final char[] chars, final Part[] parts) {
        final Matcher matcher = DURATION_PATTERN.matcher(new String(chars));
        while (matcher.find()) {
            final int start = matcher.start();
            final int end = matcher.end();

            // Find out if there is a sign.
            int index = start;
            char sign = chars[start];
            if (sign == '+') {
                parts[start] = new Part("+", Sign.PLUS);
                index++;
            } else if (sign == '-') {
                parts[start] = new Part("-", Sign.MINUS);
                index++;
            }

            // Advance past whitespace.
            while (chars[index] == ' ') {
                index++;
            }

            final String section = new String(chars, index, end - index);

            // Obliterate the matched part of the expression so it can't be matched by any other matcher.
            Arrays.fill(chars, start, end, ' ');


            final MyDuration duration = parseDuration(section);
            parts[index] = new Part(section, duration);
        }
    }

    private static MyDuration parseDuration(final String string) {
        int start = 0;
        char[] chars = string.toCharArray();

        // Get digits.
        int numStart = start;
        while (Character.isDigit(chars[start])) {
            start++;
        }
        long num = Long.parseLong(new String(chars, numStart, start - numStart));

        // Get duration type.
        final char type = chars[chars.length - 1];

        // Create my duration.
        return new MyDuration(num, type);
    }

    private enum DatePoint {
        NOW("now()"), SECOND("second()"), MINUTE("minute()"), HOUR("hour()"), DAY("day()"), WEEK("week()"), MONTH("month()"), YEAR("year()");

        private final String function;

        DatePoint(final String function) {
            this.function = function;
        }

        public String getFunction() {
            return function;
        }
    }

    private enum Sign {
        PLUS, MINUS
    }

    private static class Part {
        private final String string;
        private final Object object;

        Part(final String string, final Object object) {
            this.string = string;
            this.object = object;
        }

        public Object getObject() {
            return object;
        }

        @Override
        public String toString() {
            return string;
        }
    }

    private static class MyDuration {
        private final long value;
        private final char type;

        MyDuration(final long value, final char type) {
            this.value = value;
            this.type = type;
        }

        public long getValue() {
            return value;
        }

        public char getType() {
            return type;
        }
    }
}
