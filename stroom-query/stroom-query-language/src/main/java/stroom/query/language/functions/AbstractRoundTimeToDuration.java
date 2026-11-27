/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.language.functions.TimeRounder.Mode;
import stroom.query.language.functions.TimeRounder.Origin;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.format.DateTimeParseException;

/// The shared part of `floorTime`, `ceilingTime` and `roundTime`, which move a time onto a
/// multiple of a duration or period given as their second argument.
///
/// Multiples are counted from an origin given as their third argument, or from midnight on 1 January 1970 in
/// the query's time zone, not in UTC, so a duration of a day starts at local midnight just as `floorDay` does.
///
/// The duration is read and checked once, when the generator is created, rather than for every
/// row. A bad duration still gives an error value in every row, as it always has, because an
/// error value is how a function reports a problem with its arguments.
abstract class AbstractRoundTimeToDuration extends AbstractRoundDateTime {

    private static final String DURATION_PARAM_NAME = "duration";

    /// @param expressionContext the context, which supplies the query's time zone
    /// @param name              the name the function was called by
    AbstractRoundTimeToDuration(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 2, 3);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        try {
            final TimeRounder timeRounder = createTimeRounder();
            return timeRounder::round;
        } catch (final IllegalArgumentException e) {
            return zonedDateTime -> {
                throw e;
            };
        }
    }

    /// Gets which multiple of the duration to move a time to.
    ///
    /// @return The mode.
    abstract Mode getMode();

    private TimeRounder createTimeRounder() {
        final Param param = params[1];
        final Val val = ConstantParam.getValue(param);
        if (val == null) {
            throw new IllegalArgumentException(
                    "Invalid " + DURATION_PARAM_NAME + ": " + param + " - " + ConstantParam.whyNotConstant(param));
        }
        final Origin origin = ConstantParam.getOrigin(params.length > 2
                ? params[2]
                : null);
        final Val multiple = val instanceof ValDuration || val instanceof ValPeriod
                ? val
                : parseMultiple(val.toString().trim());
        return multiple instanceof final ValPeriod period
                ? TimeRounder.ofPeriod(getMode(), period.toPeriod(), origin)
                : TimeRounder.ofDuration(getMode(), multiple.toLong(), origin);
    }

    private Val parseMultiple(final String value) {
        // ISO text is read as a duration if it can be, so P7D is 168 hours and not a week from a Monday.
        try {
            return ValDuration.create(parseDurationMillis(value));
        } catch (final DateTimeParseException e) {
            try {
                return TimeRounder.parseMultiple(value);
            } catch (final DateTimeException | ArithmeticException e2) {
                throw new IllegalArgumentException("Invalid " + DURATION_PARAM_NAME + " format: " + value, e2);
            }
        }
    }

    private long parseDurationMillis(final String value) {
        final Duration duration = Duration.parse(value);
        final long durationMillis;
        try {
            durationMillis = duration.toMillis();
        } catch (final ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Invalid " + DURATION_PARAM_NAME + ": " + value + " - it is too long");
        }
        // A duration under a millisecond is zero once in milliseconds, so it is refused too.
        if (durationMillis <= 0) {
            throw new IllegalArgumentException(
                    "Invalid " + DURATION_PARAM_NAME + ": " + value + " - it must be at least one millisecond");
        }
        return durationMillis;
    }
}
