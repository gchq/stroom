package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public final class StringSet implements Constraint {

    @JsonProperty
    private final Set<String> set;

    @JsonCreator
    public StringSet(@JsonProperty("set") final Set<String> set) {
        this.set = set;
    }

    public Set<String> getSet() {
        return set;
    }

    public boolean validate(final String value) {
        return getSet().contains(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StringSet that = (StringSet) o;
        return Objects.equals(set, that.set);
    }

    @Override
    public int hashCode() {
        return Objects.hash(set);
    }
}
