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
        if (values == null) {
            return null;
        }
        return Arrays.toString(values.toArray(new KeyValue[0]));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<KeyValueList, Builder> {

        private List<KeyValue> values;

        private Builder() {
        }

        private Builder(final KeyValueList keyValueList) {
            this.values = keyValueList.values;
        }

        public Builder values(final List<KeyValue> values) {
            this.values = values;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public KeyValueList build() {
            return new KeyValueList(
                    values
            );
        }
    }
}
