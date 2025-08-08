package stroom.pathways.shared.otel.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        return "ArrayValue{" +
               "values=" + values +
               '}';
    }
}
