package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * A path key that uses the names of all sub path nodes in the expected order.
 *
 * @param names
 */
@JsonInclude(Include.NON_NULL)
public final class NamesPathKey implements PathKey {

    @JsonProperty("names")
    private final List<String> names;

    @JsonCreator
    public NamesPathKey(@JsonProperty("names") final List<String> names) {
        this.names = names;
    }

    public List<String> getNames() {
        return names;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NamesPathKey that = (NamesPathKey) o;
        return Objects.equals(names, that.names);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(names);
    }

    @Override
    public String toString() {
        return String.join("|", names);
    }
}
