package stroom.pathways.model.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class KeyValueList {

    @JsonProperty("values")
    private final List<KeyValue> values;

    @JsonCreator
    public KeyValueList(@JsonProperty("values") final List<KeyValue> values) {
        this.values = values;
    }

    public List<KeyValue> getValues() {
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
        final KeyValueList that = (KeyValueList) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }

    @Override
    public String toString() {
        return "KeyValueList{" +
               "values=" + values +
               '}';
    }
}
