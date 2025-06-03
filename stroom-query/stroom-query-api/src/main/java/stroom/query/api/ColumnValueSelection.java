package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class ColumnValueSelection {

    @JsonProperty
    @JsonPropertyDescription("Selected values to include unless inverted in which case these are the values to exclude")
    private final Set<String> values;
    @JsonProperty
    @JsonPropertyDescription("Is the selection inverted, " +
                             "i.e. are all values to be included except for the selected ones")
    private final boolean invert;

    @JsonCreator
    public ColumnValueSelection(@JsonProperty("values") final Set<String> values,
                                @JsonProperty("invert") final boolean invert) {
        this.values = values;
        this.invert = invert;
    }

    public Set<String> getValues() {
        return values;
    }

    public boolean isInvert() {
        return invert;
    }

    /**
     * This filter is enabled if any values are selected or if the filter is inverted so only the selected values are
     * included.
     *
     * @return True if this filter is enabled.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return !invert || (values != null && !values.isEmpty());
    }

    @Override
    public String toString() {
        return "ColumnValueSelection{" +
               "values=" + values +
               ", invert=" + invert +
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
        final ColumnValueSelection that = (ColumnValueSelection) o;
        return invert == that.invert && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, invert);
    }
}
