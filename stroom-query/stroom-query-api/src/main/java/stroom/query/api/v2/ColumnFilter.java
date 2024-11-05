package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ColumnFilter {
    @JsonProperty
    private final String filter;
    @JsonProperty
    private final boolean caseSensitive;

    @JsonCreator
    public ColumnFilter(@JsonProperty("filter") final String filter,
                        @JsonProperty("caseSensitive") final boolean caseSensitive) {
        this.filter = filter;
        this.caseSensitive = caseSensitive;
    }

    public String getFilter() {
        return filter;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ColumnFilter that = (ColumnFilter) o;
        return caseSensitive == that.caseSensitive &&
                Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, caseSensitive);
    }

    @Override
    public String toString() {
        return "ColumnFilter{" +
                "filter='" + filter + '\'' +
                ", caseSensitive=" + caseSensitive +
                '}';
    }
}
