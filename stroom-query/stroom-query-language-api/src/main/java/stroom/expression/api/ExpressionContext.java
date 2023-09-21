package stroom.expression.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ExpressionContext {

    @JsonProperty
    private final int maxStringLength;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;

    public ExpressionContext() {
        this.maxStringLength = 100;
        this.dateTimeSettings = DateTimeSettings.builder().build();
    }

    @JsonCreator
    public ExpressionContext(@JsonProperty("maxStringLength") final int maxStringLength,
                             @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings) {
        this.maxStringLength = maxStringLength;
        this.dateTimeSettings = dateTimeSettings;
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
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

    public static final class Builder {

        private int maxStringLength;
        private DateTimeSettings dateTimeSettings;

        private Builder() {
        }

        private Builder(final ExpressionContext expressionContext) {
            this.maxStringLength = expressionContext.maxStringLength;
            this.dateTimeSettings = expressionContext.dateTimeSettings;
        }

        public Builder maxStringLength(final int maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        public Builder dateTimeSettings(final DateTimeSettings dateTimeSettings) {
            this.dateTimeSettings = dateTimeSettings;
            return this;
        }

        public ExpressionContext build() {
            return new ExpressionContext(maxStringLength, dateTimeSettings);
        }
    }
}
