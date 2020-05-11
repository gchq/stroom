package stroom.core.db.migration._V07_00_00.streamstore.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.Clearable;
import stroom.util.shared.Copyable;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HasIsConstrained;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.Matcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class to match on sets of id's With concepts like match anything,
 * null, nothing and specific id's.
 * <p>
 * By default when created it has no criteria i.e. match anything. As soon as
 * you update it it will be restrictive until you setMatchAll
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class _V07_00_00_CriteriaSet<T>
        implements Iterable<T>, Copyable<_V07_00_00_CriteriaSet<T>>, HasIsConstrained, Matcher<T>, Clearable {
    /**
     * By default the criteria will match anything. NULL matchAll implies match
     * anything when nothing is in the set or we are to not matchNull. By
     * setting match all it will match all regardless.
     */
    @JsonProperty
    private Boolean matchAll;
    @JsonProperty
    private Boolean matchNull;
    @JsonProperty
    private Set<T> set;

    public _V07_00_00_CriteriaSet() {
        this(new HashSet<>());
    }

    public _V07_00_00_CriteriaSet(final Set<T> set) {
        this.set = set;
    }

    @JsonCreator
    public _V07_00_00_CriteriaSet(@JsonProperty("matchAll") final Boolean matchAll,
                                  @JsonProperty("matchNull") final Boolean matchNull,
                                  @JsonProperty("set") final Set<T> set) {
        this.matchAll = matchAll;
        this.matchNull = matchNull;
        this.set = set;
    }

    public Boolean getMatchAll() {
        return matchAll;
    }

    public void setMatchAll(final Boolean matchAll) {
        this.matchAll = matchAll;
    }

    public Boolean getMatchNull() {
        return matchNull;
    }

    public void setMatchNull(final Boolean matchNull) {
        this.matchNull = matchNull;
    }

    @JsonIgnore
    public boolean isMatchNothing() {
        return Boolean.FALSE.equals(matchAll) && set.isEmpty() && !Boolean.TRUE.equals(matchNull);
    }

    @Override
    @JsonIgnore
    public boolean isConstrained() {
        if (Boolean.TRUE.equals(matchAll)) {
            return false;
        }
        if (Boolean.FALSE.equals(matchAll) && set.isEmpty() && !Boolean.TRUE.equals(matchNull)) {
            return true;
        }
        return !set.isEmpty() || Boolean.TRUE.equals(matchNull);
    }

    @Override
    public boolean isMatch(final T item) {
        if (!isConstrained()) {
            return true;
        }
        if (item == null) {
            return Boolean.TRUE.equals(matchNull);
        }
        return set.contains(item);
    }

    public static <IN, OUT> _V07_00_00_CriteriaSet<OUT> convert(final _V07_00_00_CriteriaSet<IN> in, final Function<IN, OUT> converter) {
        if (in == null) {
            return null;
        }

        final _V07_00_00_CriteriaSet<OUT> out = new _V07_00_00_CriteriaSet<>();
        out.matchAll = in.matchAll;
        out.matchNull = in.matchNull;
        if (in.set != null) {
            out.set = in.set.stream().map(converter).collect(Collectors.toSet());
        }

        return out;
    }

    @Override
    public void copyFrom(final _V07_00_00_CriteriaSet<T> other) {
        this.set.clear();
        this.set.addAll(other.set);
        this.matchNull = other.matchNull;
        this.matchAll = other.matchAll;
    }

    public void add(final T id) {
        if (id == null) {
            throw new IllegalArgumentException("CriteriaSet does not allow adding null - use setMatchNull if required");
        }
        set.add(id);
    }

    @JsonIgnore
    public T getSingleItem() {
        if (!isConstrained()) {
            return null;
        }
        if (set == null || set.size() != 1) {
            return null;
        }
        return set.iterator().next();
    }

    @JsonIgnore
    public void setSingleItem(final T item) {
        clear();
        if (item != null) {
            add(item);
        }
    }

    public void addAll(final Collection<T> set) {
        this.set.addAll(set);
    }

    public boolean contains(final T id) {
        return set.contains(id);
    }

    public boolean remove(final T id) {
        return set.remove(id);
    }

    public Set<T> getSet() {
        return set;
    }

    public void setSet(final Set<T> set) {
        this.set = set;
    }

    @Override
    public void clear() {
        set.clear();
        matchNull = null;
        matchAll = null;
    }

    public int size() {
        return set.size();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !(obj instanceof _V07_00_00_CriteriaSet)) {
            return false;
        }

        @SuppressWarnings("unchecked") final _V07_00_00_CriteriaSet<T> criteriaSet = (_V07_00_00_CriteriaSet<T>) obj;

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.matchNull, criteriaSet.matchNull);
        builder.append(this.matchAll, criteriaSet.matchAll);
        builder.append(this.set, criteriaSet.set);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(set);
        hashCodeBuilder.append(matchAll);
        hashCodeBuilder.append(matchNull);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public String toString() {
        if (!isConstrained()) {
            return "any";
        }
        if (Boolean.TRUE.equals(matchNull)) {
            return "null," + set.toString();
        }
        return set.toString();
    }

    @Override
    public Iterator<T> iterator() {
        return set.iterator();
    }

}
