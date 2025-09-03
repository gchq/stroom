package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ArrayValue {

    @JsonProperty("values")
    private final List<AnyValue> values;

    @JsonCreator
    public ArrayValue(@JsonProperty("values") final List<AnyValue> values) {
        this.values = values;
    }

    public List<AnyValue> getValues() {
        return values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ArrayValue that = (ArrayValue) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }

    @Override
    public String toString() {
        if (values == null) {
            return null;
        }
        return Arrays.toString(values.toArray(new AnyValue[0]));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<ArrayValue, Builder> {

        private List<AnyValue> values;

        private Builder() {
        }

        private Builder(final ArrayValue arrayValue) {
            this.values = arrayValue.values;
        }

        public Builder values(final List<AnyValue> values) {
            this.values = values;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ArrayValue build() {
            return new ArrayValue(
                    values
            );
        }
    }
}
