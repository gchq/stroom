package stroom.query.language.functions;

import stroom.query.language.token.Param;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@FunctionDef(
        name = RoundTime.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = RoundDate.ROUND_SUB_CATEGORY,
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
                                description = "The duration to round by in ISO-8601 format (e.g., 'PT5M' for 5 minutes).",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "offset",
                                description = "The offset to apply in ISO-8601 format (e.g., 'PT1M' for 1 minute).",
                                argType = ValString.class)
                }))
class RoundTime extends RoundDate {

    static final String NAME = "roundTime";

    public RoundTime(final String name) {
        super(name);
    }

    @Override
    protected RoundCalculator getCalculator() {
        return new Calc(getParams());
    }

    static class Calc extends RoundDateCalculator {

        private final Param[] params;

        public Calc(final Param[] params) {
            this.params = params;
        }

        @Override
        public Val calc(final Val value) {
            return calc(value, params);
        }

        private Duration parseDuration(final Param param, final String paramName) {
            if (param == null) {
                return Duration.ZERO;
            }
            try {
                return Duration.parse(param.toString());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid " + paramName + " format: " + param.toString());
            }
        }

        public Val calc(final Val value, final Param[] params) {
            final Long val = value.toLong();
            if (val == null) {
                return ValNull.INSTANCE;
            }

            try {
                final Duration duration = parseDuration(params[1], "duration");
                final Duration offset = parseDuration(params[2], "offset");

                long epochMillis = val;
                long durationMillis = duration.toMillis();
                long offsetMillis = offset.toMillis();

                // Adjust for offset
                epochMillis -= offsetMillis;

                // Calculate the remainder
                long remainder = epochMillis % durationMillis;

                // Round to the nearest duration
                if (remainder >= durationMillis / 2) {
                    epochMillis += (durationMillis - remainder);
                } else {
                    epochMillis -= remainder;
                }

                // Apply the offset adjustment
                epochMillis += offsetMillis;

                return ValLong.create(epochMillis);
            } catch (IllegalArgumentException e) {
                return ValErr.create(e.getMessage());
            }
        }

        @Override
        protected LocalDateTime adjust(final LocalDateTime dateTime) {
            throw new UnsupportedOperationException("Use calc(Val, Param[]) instead.");
        }
    }
}