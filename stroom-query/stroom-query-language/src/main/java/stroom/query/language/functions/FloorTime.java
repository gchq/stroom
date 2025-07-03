package stroom.query.language.functions;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@FunctionDef(
        name = FloorTime.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.ROUND_SUB_CATEGORY,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures =
        @FunctionSignature(
                description = "Floors the supplied time to the nearest duration.",
                args = {
                        @FunctionArg(
                                name = "time",
                                description = "The time to floor.",
                                argType = ValDate.class),
                        @FunctionArg(
                                name = "duration",
                                description = "The duration to floor by in ISO-8601 format " +
                                              "(e.g., 'PT5M' for 5 minutes).",
                                argType = ValString.class)
                })
)
class FloorTime extends AbstractRoundDateTime {

    static final String NAME = "floorTime";

    public FloorTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 2, 2);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        return zonedDateTime -> {
            final long duration = parseDuration(params[1], "duration").toMillis();
            final long millis = zonedDateTime.toInstant().toEpochMilli();
            final long remainder = millis % duration;
            final long flooredMillis = millis - remainder;
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(flooredMillis), zoneId);
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
