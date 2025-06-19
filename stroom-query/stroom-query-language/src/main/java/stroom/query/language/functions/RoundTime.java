package stroom.query.language.functions;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@FunctionDef(
        name = RoundTime.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.ROUND_SUB_CATEGORY,
        commonReturnType = ValLong.class,
        commonReturnDescription = "The time as milliseconds since the epoch (1st Jan 1970).",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time to the nearest duration, applying an optional offset.",
                args = {
                        @FunctionArg(
                                name = "time",
                                description = "The time to round in milliseconds since the epoch.",
                                argType = ValLong.class),
                        @FunctionArg(
                                name = "duration",
                                description = "The duration to round by in ISO-8601 format " +
                                              "(e.g., 'PT5M' for 5 minutes).",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "offset",
                                description = "The offset to apply in ISO-8601 format (e.g., 'PT1M' for 1 minute).",
                                argType = ValString.class)
                }))
class RoundTime extends AbstractRoundDateTime {

    static final String NAME = "roundTime";

    public RoundTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

    }

    private Duration parseDuration(final Param param, final String paramName) {
        if (param == null) {
            return Duration.ZERO;
        }
        try {
            return Duration.parse(param.toString());
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid " + paramName + " format: " + param);
        }
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        return zonedDateTime -> {
            final long duration = parseDuration(params[1], "duration").toMillis();
            final long offset = parseDuration(params[2], "offset").toMillis();
            long millis = zonedDateTime.toInstant().toEpochMilli();
            millis = millis - offset;
            final long remainder = millis % duration;
            long roundedMillis = millis - remainder;
            if (remainder >= duration / 2) {
                roundedMillis += duration;
            }
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(roundedMillis + offset), zoneId);
        };
    }
}
