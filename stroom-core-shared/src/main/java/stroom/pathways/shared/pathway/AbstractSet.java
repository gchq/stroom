package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public abstract class AbstractSet<T> {

    @JsonProperty
    private final Set<T> set;

    @JsonCreator
    public AbstractSet(@JsonProperty("set") final Set<T> set) {
        this.set = set;
    }

    public Set<T> getSet() {
        return set;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractSet<?> that = (AbstractSet<?>) o;
        return Objects.equals(set, that.set);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(set);
    }

    @Override
    public String toString() {
        return set.toString();
    }

    public boolean validate(final T value) {
        return set.contains(value);
    }
}
