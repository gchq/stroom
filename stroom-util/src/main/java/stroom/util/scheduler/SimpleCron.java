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

package stroom.util.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Class to figure out the next time we should fire based on a simple CRON
 * expression.
 * <p>
 * E.g. 0 * * Every Hour 1,30 * * Every Half Hour
 */
public class SimpleCron {
    /**
     * Token to mean ANY time matches.
     */
    public static final String CRON_ANY = "*";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private final String expression;

    /**
     * List of 3 CRON parts.
     */
    private final List<CronPart> cronParts;

    private SimpleCron(final String expression) {
        this.expression = expression;

        if (expression == null) {
            throw new MalformedCronException("Cron expression cannot be null");
        }

        final String[] parts = expression.split(" ");
        if (parts.length != 3) {
            throw new MalformedCronException("Cron expression must contain 3 parts");
        }

        cronParts = new ArrayList<>(3);
        cronParts.add(new CronPart(Calendar.MINUTE, parts[0]));
        cronParts.add(new CronPart(Calendar.HOUR_OF_DAY, parts[1]));
        cronParts.add(new CronPart(Calendar.DAY_OF_MONTH, parts[2]));
    }

    /**
     * Factory method used to compile a cron expression and create a new
     * SimpleCron object. Note that any errors in the expression will result in
     * a MalformedCronException being thrown.
     *
     * @param expression The cron expression to compile into this SimpleCron object.
     */
    public static SimpleCron compile(final String expression) {
        return new SimpleCron(expression);
    }

    /**
     * Creates a new scheduler to be used with this cron object.
     */
    public Scheduler createScheduler() {
        return new SimpleCronScheduler(this);
    }

    /**
     * Move the supplied time forward or backward to get the previous and next
     * execution times.
     */
    private Long move(final long millis, final boolean forward) {
        final Calendar time = Calendar.getInstance();
        time.setTimeZone(UTC);
        time.setTimeInMillis(millis);
        // Wind back to start of minute
        time.set(Calendar.MILLISECOND, 0);
        time.set(Calendar.SECOND, 0);

        boolean hasRolled = false;

        // If we are going back in time then we will have already rolled by
        // resetting the millis and second parameters unless the time just
        // happened to be exactly the same after resetting these values.
        if (!forward) {
            if (time.getTimeInMillis() != millis) {
                hasRolled = true;
            }
        }

        // Loop through and try and roll
        for (int i = 0; i < cronParts.size(); i++) {
            hasRolled = move(i, time, hasRolled, forward);
        }

        return time.getTime().getTime();
    }

    /**
     * Try and move time until we have rolled OK. hasRolled only gets set to
     * false for the first order CRON.
     */
    private boolean move(final int cronIndex, final Calendar time, final boolean hasRolled, final boolean forward) {
        final CronPart cronEntry = cronParts.get(cronIndex);
        final int start = time.get(cronEntry.getCalendarType());

        // Should we roll (no if we have already rolled)
        boolean roll = !hasRolled;
        int value;
        do {
            // Move forward?
            if (roll) {
                if (forward) {
                    time.add(cronEntry.getCalendarType(), 1);
                } else {
                    time.add(cronEntry.getCalendarType(), -1);
                }

                // Reset lower order CRONS
                reset(cronIndex - 1, time, forward);
            } else {
                // Loop done once ... now OK to start rolling
                roll = true;
            }
            value = time.get(cronEntry.getCalendarType());

            // As soon as we have wrapped exit loop
            if (forward) {
                if (value < start) {
                    break;
                }
            } else {
                if (value > start) {
                    break;
                }
            }

            if (!cronEntry.hasMatches()) {
                // Match on ANY - Return as soon as we have rolled
                if (value != start || hasRolled) {
                    return true;
                }
            } else {
                // Return if new start is in our set
                final Integer match = cronEntry.match(value, forward);
                if (match != null) {
                    // Reset previous fields if we need to.
                    if (match != value) {
                        // Reset lower order CRONS
                        reset(cronIndex - 1, time, forward);
                    }

                    final int diff = match - value;
                    time.add(cronEntry.getCalendarType(), diff);

                    return true;
                }
            }
        } while (true);

        // Wrapped ... reset.
        reset(cronIndex, time, forward);
        return true;
    }

    /**
     * Used when the time wraps to set the time to the best CRON match. e.g. 2,8
     * for hours would move 0H to 2H.
     * <p>
     * Also recurses into lower order CRON's.
     */
    private void reset(final int cronIndex, final Calendar time, final boolean forward) {
        // Do we have a CRON do to ?
        if (cronIndex >= 0) {
            final CronPart cronEntry = cronParts.get(cronIndex);
            if (forward) {
                if (cronEntry.hasMatches()) {
                    // Use the first CRON match
                    time.set(cronEntry.getCalendarType(), cronEntry.first());
                } else {
                    // Start will be fine.
                    time.set(cronEntry.getCalendarType(), 0);
                }
            } else {
                if (cronEntry.hasMatches()) {
                    // Use the last CRON match
                    time.set(cronEntry.getCalendarType(), cronEntry.last());
                } else {
                    // End will be fine.
                    if (cronEntry.getCalendarType() == Calendar.MINUTE) {
                        time.set(Calendar.MINUTE, 59);
                    } else if (cronEntry.getCalendarType() == Calendar.HOUR_OF_DAY) {
                        time.set(Calendar.HOUR_OF_DAY, 23);
                    } else if (cronEntry.getCalendarType() == Calendar.DAY_OF_MONTH) {
                        // We don't know how many days there are in the month so
                        // just keep subtracting months.
                        int month = 0;
                        do {
                            month = time.get(Calendar.DAY_OF_MONTH);
                            time.add(Calendar.DAY_OF_MONTH, -1);
                        } while (month != 0);
                    }
                }
            }

            // Recurse
            reset(cronIndex - 1, time, forward);
        }
    }

    /**
     * Given a time when should we run next.
     */
    public Long getNextTime(final Long millis) {
        return move(millis, true);
    }

    /**
     * Given a time when should we have last run.
     */
    public Long getLastTime(final Long millis) {
        return move(millis, false);
    }

    @Override
    public String toString() {
        return expression;
    }

    /**
     * Internal Class to hold CRON per calendar type.
     */
    private static class CronPart {
        private static final Pattern reNumericList = Pattern.compile("^(\\d+)(,\\d+)*$");
        private static final Pattern reInterval = Pattern.compile("^\\*\\/(\\d{1,2})$");
        private static final Pattern reRange = Pattern.compile("^(\\d+)-(\\d+)$");

        private final int calendarType;

        /**
         * CRON matches OR null if ANY match
         */
        private final int[] matches;

        public CronPart(final int calendarType, final String expression) {
            this.calendarType = calendarType;
            this.matches = buildMatches(expression);
        }

        /**
         * Build the CRON matches
         */
        @SuppressWarnings(value = "PZLA_PREFER_ZERO_LENGTH_ARRAYS") // private array ... designed to allow null
        private int[] buildMatches(final String expression) {
            if (CRON_ANY.equals(expression)) {
                return null;
            } else {
                // A comma-separated list of numeric values (e.g. 10,20,30)
                final Matcher numericListMatcher = reNumericList.matcher(expression);
                if (numericListMatcher.matches()) {
                    return buildMatchesFromList(expression);
                }

                // Expression is an interval like `*/10`
                final Matcher intervalMatcher = reInterval.matcher(expression);
                if (intervalMatcher.matches()) {
                    final int interval = Integer.parseInt(intervalMatcher.group(1));
                    return buildMatchesFromInterval(interval);
                }

                // A numeric range (e.g. 1-25)
                final Matcher rangeMatcher = reRange.matcher(expression);
                if (rangeMatcher.matches()) {
                    final int start = Integer.parseInt(rangeMatcher.group(1));
                    final int end = Integer.parseInt(rangeMatcher.group(2));
                    return buildMatchesFromRange(expression, start, end);
                }

                throw new MalformedCronException(
                    "Cron expression \"" + expression + "\" must be a list of comma-separated numbers," +
                            "an interval or a wildcard (*)");
            }
        }

        /**
         * Extract numeric values from a series of matches
         */
        private int[] buildMatchesFromList(final String expression) {
            return Arrays.stream(expression.split(",")).map(str -> {
                final int val = Integer.parseInt(str);

                switch (calendarType) {
                    case Calendar.MINUTE:
                        if (val < 0 || val > 59) {
                            throw new MalformedCronException(
                                    "Cron expression \"" + expression + "\" must be in the range 0-59 or *");
                        }
                        break;
                    case Calendar.HOUR_OF_DAY:
                        if (val < 0 || val > 23) {
                            throw new MalformedCronException(
                                    "Cron expression \"" + expression + "\" must be in the range 0-23 or *");
                        }
                        break;
                    case Calendar.DAY_OF_MONTH:
                        if (val < 1 || val > 31) {
                            throw new MalformedCronException(
                                    "Cron expression \"" + expression + "\" must be in the range 1-31 or *");
                        }
                        break;
                }

                return val;
            }).sorted().mapToInt(i -> i).toArray();
        }

        /**
         * Return a list of numbers, incrementing each iteration by the specified numeric interval
         */
        private int[] buildMatchesFromInterval(final int interval) {
            switch (calendarType) {
                case Calendar.MINUTE:
                    return IntStream.range(0, 59).filter(x -> x % interval == 0).toArray();
                case Calendar.HOUR_OF_DAY:
                    return IntStream.range(0, 23).filter(x -> x % interval == 0).toArray();
                case Calendar.DAY_OF_MONTH:
                    return IntStream.range(1, 31).filter(x -> x % interval == 0).toArray();
            }

            return null;
        }

        /**
         * Return a sequence of numbers between the specified start and end (inclusive), incrementing by one
         */
        private int[] buildMatchesFromRange(final String expression, final int start, final int end) {
            if (start > end) {
                throw new MalformedCronException(
                        "Cron expression range start (" + start + ") must be less than end (" + end + ")");
            }

            switch (calendarType) {
                case Calendar.MINUTE:
                    if (start < 0 || end > 59) {
                        throw new MalformedCronException(
                                "Cron expression \"" + expression + "\" must be in the range 0-59 or *");
                    }
                    return IntStream.range(start, end).toArray();
                case Calendar.HOUR_OF_DAY:
                    if (start < 0 || end > 23) {
                        throw new MalformedCronException(
                                "Cron expression \"" + expression + "\" must be in the range 0-23 or *");
                    }
                    return IntStream.range(start, end).toArray();
                case Calendar.DAY_OF_MONTH:
                    if (start < 1 || end > 31) {
                        throw new MalformedCronException(
                                "Cron expression \"" + expression + "\" must be in the range 1-31 or *");
                    }
                    return IntStream.range(start, end).toArray();
            }

            return null;
        }

        public int getCalendarType() {
            return calendarType;
        }

        public boolean hasMatches() {
            return matches != null && matches.length > 0;
        }

        public Integer match(final int value, final boolean forward) {
            if (forward) {
                for (int i = 0; i < matches.length; i++) {
                    if (matches[i] >= value) {
                        return matches[i];
                    }
                }
            } else {
                for (int i = matches.length - 1; i >= 0; i--) {
                    if (matches[i] <= value) {
                        return matches[i];
                    }
                }
            }

            return null;
        }

        public int first() {
            return matches[0];
        }

        public int last() {
            return matches[matches.length - 1];
        }
    }
}
