package stroom.pathways.model.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class KeyValue {

    @JsonProperty("key")
    private final String key;

    @JsonProperty("value")
    private final AnyValue value;

    @JsonCreator
    public KeyValue(@JsonProperty("key") final String key,
                    @JsonProperty("value") final AnyValue value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public AnyValue getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyValue keyValue = (KeyValue) o;
        return Objects.equals(key, keyValue.key) &&
               Objects.equals(value, keyValue.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "KeyValue{" +
               "key='" + key + '\'' +
               ", value=" + value +
               '}';
    }
}
