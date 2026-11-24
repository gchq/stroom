/*
 * Copyright 2024 Crown Copyright
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

import stroom.query.language.token.TimeLiteral;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public class ValDurationUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ValDurationUtil.class);
    // Numbers and exact units such as 1d2h30m, -12h or 1h 30m, as a duration is shown.
    private static final Pattern EXACT_PATTERN = Pattern.compile("\\s*-?\\s*(?:\\d++(?:ms|[wdhms])\\s*+)++");
    static final String PARSE_ERROR_MESSAGE = "Text cannot be parsed to a Duration";

    public static Val formatDuration(final Val value) {
        if (value == null) {
            return ValNull.INSTANCE;
        } else if (value.hasNumericValue()) {
            // This covers strings too
            final long milliseconds = value.toLong();
            return ValString.create(ModelStringUtil.formatDurationString(milliseconds, true));
        } else if (Type.STRING.equals(value.type())) {
            return formatDuration(value.toString());
        }
        return checkPeriod(value);
    }

    public static Val formatDuration(final String value) {
        if (NullSafe.isBlankString(value)) {
            return ValNull.INSTANCE;
        } else {
            try {
                final long milliseconds = parseToMilliseconds(value);
                return ValString.create(ModelStringUtil.formatDurationString(milliseconds, true));
            } catch (final Exception e) {
                return exceptionToValErr(e);
            }
        }
    }

    public static Val formatISODuration(final Val value) {
        if (value == null) {
            return ValNull.INSTANCE;
        } else if (isDuration(value)) {
            final long milliseconds = value.toLong();
            return ValString.create(Duration.ofMillis(milliseconds).toString());
        } else if (Type.STRING.equals(value.type())) {
            return formatISODuration(value.toString());
        }
        return checkPeriod(value);
    }

    public static Val formatISODuration(final String value) {
        if (NullSafe.isBlankString(value)) {
            return ValNull.INSTANCE;
        } else {
            try {
                final long milliseconds = parseToMilliseconds(value);
                return ValString.create(Duration.ofMillis(milliseconds).toString());
            } catch (final Exception e) {
                return exceptionToValErr(e);
            }
        }
    }

    public static Val parseDuration(final Val val) {
        if (Type.STRING == val.type()) {
            return parseDuration(val.toString());
        } else if (isDuration(val)) {
            if (Type.DURATION == val.type()) {
                return val;
            }
            final Long milliseconds = val.toLong();
            if (milliseconds != null) {
                return ValDuration.create(milliseconds);
            } else {
                // Should never have a numeric type with null toLong
                throw new RuntimeException(LogUtil.message("Numeric type {} has a null long value",
                        val.getClass().getSimpleName()));
            }
        }
        return checkPeriod(val);
    }

    public static Val parseDuration(final String value) {
        if (NullSafe.isBlankString(value)) {
            return ValNull.INSTANCE;
        } else {
            try {
                final long millis = parseToMilliseconds(value);
                return ValDuration.create(millis);
            } catch (final Exception e) {
                return exceptionToValErr(e);
            }
        }
    }

    public static Val parseISODuration(final Val val) {
        if (Type.STRING.equals(val.type())) {
            return parseISODuration(val.toString());
        } else if (isDuration(val)) {
            if (Type.DURATION.equals(val.type())) {
                return val;
            }
            final Long milliseconds = val.toLong();
            if (milliseconds != null) {
                return ValDuration.create(milliseconds);
            } else {
                // Should never have a numeric type with null toLong
                throw new RuntimeException(LogUtil.message("Numeric type {} has a null long value",
                        val.getClass().getSimpleName()));
            }
        }
        return checkPeriod(val);
    }

    public static Val parseISODuration(final String value) {
        if (NullSafe.isBlankString(value)) {
            return ValNull.INSTANCE;
        } else {
            try {
                return ValDuration.create(Duration.parse(value).toMillis());
            } catch (final Exception e) {
                return exceptionToValErr(e);
            }
        }
    }

    private static Val checkPeriod(final Val val) {
        return val instanceof ValPeriod
                ? ValErr.create("A period of months or years can't be used as a duration")
                : val;
    }

    /// Tells whether a constant parameter is already a length of time, so should be used as it is and not read
    /// from its text.
    ///
    /// @param param The parameter.
    /// @return True if it is a duration or period.
    static boolean isAmount(final Param param) {
        return param instanceof ValDuration || param instanceof ValPeriod;
    }

    // A period of only days can stand in for a duration.
    private static boolean isDuration(final Val val) {
        return val.type().isNumber()
               || (val instanceof final ValPeriod period && period.hasOnlyDays());
    }

    private static ValErr exceptionToValErr(final Exception e) {
        if (e instanceof final DateTimeParseException dtpe) {
            String msg = e.getMessage().stripTrailing();
            msg = msg.endsWith(".")
                    ? e.getMessage()
                    : e.getMessage() + ".";
            msg = msg + " Text: '" + dtpe.getParsedString() + "'.";
            return ValErr.create(msg);
        } else {
            return ValErr.create(e);
        }
    }

    /**
     * @throws DateTimeParseException if value is null or can't be parsed for any reason.
     */
    public static long parseToMilliseconds(final String value) {
        if (value == null) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, "null", 0);
        }
        if (NullSafe.isBlankString(value)) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, value, 0);
        }
        try {
            if (value.startsWith("P")) {
                // This is ISO 8601 format so use Duration to parse it
                return Duration.parse(value).toMillis();
            }
            if (EXACT_PATTERN.matcher(value).matches()) {
                return TimeLiteral.parseCompoundMillis(value);
            }
            // Not ISO 8601 so have a go with our ModelStringUtil format
            return ModelStringUtil.parseDurationString(value);
        } catch (final DateTimeParseException e) {
            throw e;
        } catch (final DateTimeException e) {
            // This says what is wrong in words that are fit for the query's author.
            throw new DateTimeParseException(e.getMessage(), value, 0, e);
        } catch (final Exception e) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, value, 0);
        }
    }
}
