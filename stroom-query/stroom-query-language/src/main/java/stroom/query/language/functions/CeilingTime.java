package stroom.query.language.functions;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@FunctionDef(
        name = CeilingTime.NAME,
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
                                description = "The time to ceiling.",
                                argType = ValDate.class),
                        @FunctionArg(
                                name = "duration",
                                description = "The duration to ceiling by in ISO-8601 format " +
                                              "(e.g., 'PT5M' for 5 minutes).",
                                argType = ValString.class)
                })
)
class CeilingTime extends AbstractRoundDateTime {

    static final String NAME = "ceilingTime";

    public CeilingTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 2, 2);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        return zonedDateTime -> {
            final long duration = parseDuration(params[1], "duration").toMillis();
            final long millis = zonedDateTime.toInstant().toEpochMilli();

            final Instant t1 = Instant.ofEpochMilli(millis);

            final long remainder = millis % duration;
            long ceilingMillis = millis - remainder;

            final Instant t2 = Instant.ofEpochMilli(ceilingMillis);

            if (remainder > 0) {
                ceilingMillis += duration;
            }

            final Instant t3 = Instant.ofEpochMilli(ceilingMillis);

            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ceilingMillis), zoneId);
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
