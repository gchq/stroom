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

        cronParts = new ArrayList<CronPart>(3);
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
            int[] matches = null;

            if (!CRON_ANY.equals(expression)) {
                final String[] parts = expression.split(",");
                if (parts.length == 0) {
                    throw new MalformedCronException(
                            "Cron expression \"" + expression + "\" must be a list of comma separated numbers or an *");
                }

                matches = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try {
                        final int val = Integer.valueOf(parts[i]);
                        switch (calendarType) {
                            case Calendar.MINUTE:
                                if (val < 0 || val > 59) {
                                    throw new MalformedCronException(
                                            "Cron expression \"" + expression + "\" must be in the range 0-59 or an *");
                                }
                                break;
                            case Calendar.HOUR_OF_DAY:
                                if (val < 0 || val > 59) {
                                    throw new MalformedCronException(
                                            "Cron expression \"" + expression + "\" must be in the range 0-59 or an *");
                                }
                                break;
                            case Calendar.DAY_OF_MONTH:
                                if (val < 1 || val > 31) {
                                    throw new MalformedCronException(
                                            "Cron expression \"" + expression + "\" must be in the range 1-31 or an *");
                                }
                                break;
                        }

                        matches[i] = val;
                    } catch (final NumberFormatException e) {
                        throw new MalformedCronException("Cron expression \"" + expression
                                + "\" must be a list of comma separated numbers or an *");
                    }
                }

                Arrays.sort(matches);
            }

            return matches;
        }

        public int getCalendarType() {
            return calendarType;
        }

        public boolean hasMatches() {
            return matches != null;
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
