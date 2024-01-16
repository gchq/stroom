package stroom.query.language.functions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import java.time.Duration;

public class ValDurationUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ValDurationUtil.class);

    public static Val formatDuration(final Val value) {
        if (value == null) {
            return ValNull.INSTANCE;
        } else if (value.type().isNumber()) {
            final long milliseconds = value.toLong();
            return ValString.create(ModelStringUtil.formatDurationString(milliseconds, true));
        } else if (Type.STRING.equals(value.type())) {
            return formatDuration(value.toString());
        }
        return value;
    }

    public static Val formatDuration(final String value) {
        if (value == null) {
            return ValNull.INSTANCE;
        }
        if (!value.isBlank()) {
            try {
                final Long milliseconds = parseToMilliseconds(value);
                if (milliseconds != null) {
                    return ValString.create(ModelStringUtil.formatDurationString(milliseconds, true));
                }
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
        return ValString.create(value);
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
        if (value == null) {
            return ValNull.INSTANCE;
        }
        if (!value.isBlank()) {
            try {
                final Long milliseconds = parseToMilliseconds(value);
                if (milliseconds != null) {
                    return ValString.create(Duration.ofMillis(milliseconds).toString());
                }
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
        return ValString.create(value);
    }

    public static Val parseDuration(final Val val) {
        if (Type.STRING.equals(val.type())) {
            return parseDuration(val.toString());
        } else if (val.type().isNumber()) {
            if (Type.DURATION.equals(val.type())) {
                return val;
            }
            final Long milliseconds = val.toLong();
            if (milliseconds != null) {
                return ValDuration.create(milliseconds);
            }
        }
        return val;
    }

    public static Val parseDuration(final String value) {
        if (value == null || value.isBlank()) {
            return ValNull.INSTANCE;
        } else {
            try {
                return ValDuration.create(parseToMilliseconds(value));
            } catch (final Exception e) {
                return ValErr.create(e.getMessage());
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
            }
        }
        return val;
    }

    public static Val parseISODuration(final String value) {
        if (value == null || value.isBlank()) {
            return ValNull.INSTANCE;
        } else {
            try {
                return ValDuration.create(Duration.parse(value).toMillis());
            } catch (final Exception e) {
                return ValErr.create(e.getMessage());
            }
        }
    }

    public static Long parseToMilliseconds(final String value) {
        if (value.startsWith("P")) {
            // This is ISO 8601 format so use Duration to parse it
            return Duration.parse(value).toMillis();
        }
        // Not ISO 8601 so have a go with our ModelStringUtil format
        return ModelStringUtil.parseDurationString(value);
    }
}
