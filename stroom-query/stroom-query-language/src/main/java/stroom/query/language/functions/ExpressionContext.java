package stroom.query.language.functions;

import stroom.expression.api.DateTimeSettings;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

public class ExpressionContext {

    private final int maxStringLength;
    private final DateTimeSettings dateTimeSettings;
    private final LookupProvider lookupProvider;

    public ExpressionContext() {
        this.maxStringLength = 100;
        this.dateTimeSettings = DateTimeSettings.builder().build();
        this.lookupProvider = (map, key, effectiveTimeMs) -> ValNull.INSTANCE;
    }

    @JsonCreator
    public ExpressionContext(final int maxStringLength,
                             final DateTimeSettings dateTimeSettings,
                             final LookupProvider lookupProvider) {
        this.maxStringLength = maxStringLength;
        this.dateTimeSettings = dateTimeSettings;
        this.lookupProvider = lookupProvider;
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    public LookupProvider getLookupProvider() {
        return lookupProvider;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExpressionContext that = (ExpressionContext) o;
        return maxStringLength == that.maxStringLength &&
                Objects.equals(dateTimeSettings, that.dateTimeSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxStringLength, dateTimeSettings);
    }

    @Override
    public String toString() {
        return "ExpressionContext{" +
                "maxStringLength=" + maxStringLength +
                ", dateTimeSettings=" + dateTimeSettings +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private int maxStringLength;
        private DateTimeSettings dateTimeSettings;
        private LookupProvider lookupProvider;

        private Builder() {
        }

        private Builder(final ExpressionContext expressionContext) {
            this.maxStringLength = expressionContext.maxStringLength;
            this.dateTimeSettings = expressionContext.dateTimeSettings;
            this.lookupProvider = expressionContext.lookupProvider;
        }

        public Builder maxStringLength(final int maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        public Builder dateTimeSettings(final DateTimeSettings dateTimeSettings) {
            this.dateTimeSettings = dateTimeSettings;
            return this;
        }

        public Builder lookupProvider(final LookupProvider lookupProvider) {
            this.lookupProvider = lookupProvider;
            return this;
        }

        public ExpressionContext build() {
            return new ExpressionContext(maxStringLength, dateTimeSettings, lookupProvider);
        }
    }
}
