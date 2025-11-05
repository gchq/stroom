package stroom.analytics.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DuplicateCheckRow {

    @JsonProperty
    private final List<String> values;

    @JsonCreator
    public DuplicateCheckRow(@JsonProperty("values") final List<String> values) {
        this.values = values;
    }

    public static DuplicateCheckRow of(final String... values) {
        return new DuplicateCheckRow(NullSafe.asList(values));
    }

    public List<String> getValues() {
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
        final DuplicateCheckRow that = (DuplicateCheckRow) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "DuplicateCheckRow{" +
               "values=" + values +
               '}';
    }
}
