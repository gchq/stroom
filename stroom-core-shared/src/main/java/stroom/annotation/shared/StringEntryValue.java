package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class StringEntryValue implements EntryValue {

    @JsonProperty
    private final String value;

    @JsonCreator
    public StringEntryValue(@JsonProperty("value") final String value) {
        this.value = value;
    }

    public static StringEntryValue of(final String value) {
        return new StringEntryValue(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public String asUiValue() {
        return value;
    }

    @Override
    public String asPersistedValue() {
        return value;
    }

    @Override
    public String toString() {
        return "StringEntryValue{" +
                "value='" + value + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StringEntryValue that = (StringEntryValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
