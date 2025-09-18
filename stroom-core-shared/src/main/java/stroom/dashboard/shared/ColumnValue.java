package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ColumnValue {

    @JsonProperty
    private final String value;
    @JsonProperty
    private final String matchingRule;

    @JsonCreator
    public ColumnValue(@JsonProperty("value") final String value,
                       @JsonProperty("matchingRule") final String matchingRule) {
        this.value = value;
        this.matchingRule = matchingRule;
    }

    public String getValue() {
        return value;
    }

    public String getMatchingRule() {
        return matchingRule;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ColumnValue that = (ColumnValue) o;
        return Objects.equals(value, that.value) && Objects.equals(matchingRule, that.matchingRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, matchingRule);
    }

    @Override
    public String toString() {
        return "ColumnValue{" +
               "value='" + value + '\'' +
               ", matchingRule='" + matchingRule + '\'' +
               '}';
    }
}
