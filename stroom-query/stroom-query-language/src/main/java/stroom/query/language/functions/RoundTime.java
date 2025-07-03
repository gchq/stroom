package stroom.query.language.functions;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@FunctionDef(
        name = RoundTime.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.ROUND_SUB_CATEGORY,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures =
        @FunctionSignature(
                description = "Rounds the supplied time to the nearest duration.",
                args = {
                        @FunctionArg(
                                name = "time",
                                description = "The time to round.",
                                argType = ValDate.class),
                        @FunctionArg(
                                name = "duration",
                                description = "The duration to round by in ISO-8601 format " +
                                              "(e.g., 'PT5M' for 5 minutes).",
                                argType = ValString.class)
                })
)
class RoundTime extends AbstractRoundDateTime {

    static final String NAME = "roundTime";

    public RoundTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 2, 2);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        return zonedDateTime -> {
            final long duration = parseDuration(params[1], "duration").toMillis();
            final long millis = zonedDateTime.toInstant().toEpochMilli();
            final long remainder = millis % duration;
            long roundedMillis = millis - remainder;
            if (remainder >= duration / 2) {
                roundedMillis += duration;
            }
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(roundedMillis), zoneId);
        };
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
}
