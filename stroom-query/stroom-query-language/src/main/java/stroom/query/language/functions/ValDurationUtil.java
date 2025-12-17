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

package stroom.query.language.functions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.format.DateTimeParseException;

public class ValDurationUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ValDurationUtil.class);
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
        return value;
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
        } else if (value.type().isNumber()) {
            final long milliseconds = value.toLong();
            return ValString.create(Duration.ofMillis(milliseconds).toString());
        } else if (Type.STRING.equals(value.type())) {
            return formatISODuration(value.toString());
        }
        return value;
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
        } else if (val.type().isNumber()) {
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
        return val;
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
        } else if (val.type().isNumber()) {
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
        return val;
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
            // Not ISO 8601 so have a go with our ModelStringUtil format
            return ModelStringUtil.parseDurationString(value);
        } catch (final DateTimeParseException e) {
            throw e;
        } catch (final Exception e) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, value, 0);
        }
    }
}
