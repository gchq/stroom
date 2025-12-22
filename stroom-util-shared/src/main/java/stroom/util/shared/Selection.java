/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class to match on sets of id's with concepts like match anything and specific id's.
 */
@JsonInclude(Include.NON_NULL)
public class Selection<T> implements Iterable<T>, Copyable<Selection<T>>, Matcher<T>, Clearable {

    @JsonProperty
    private boolean matchAll;
    @JsonProperty
    private Set<T> set;

    public static <T> Selection<T> selectAll() {
        return new Selection<>(true, new HashSet<>());
    }

    public static <T> Selection<T> selectNone() {
        return new Selection<>(false, new HashSet<>());
    }

    @JsonCreator
    public Selection(@JsonProperty("matchAll") final boolean matchAll,
                     @JsonProperty("set") final Set<T> set) {
        this.matchAll = matchAll;
        this.set = set;
    }

    public boolean isMatchAll() {
        return matchAll;
    }

    public void setMatchAll(final boolean matchAll) {
        this.matchAll = matchAll;
    }

    @JsonIgnore
    public boolean isMatchNothing() {
        return !matchAll && set.isEmpty();
    }

    @Override
    public boolean isMatch(final T item) {
        if (matchAll) {
            return true;
        }
        return set.contains(item);
    }

    public static <T_IN, T_OUT> Selection<T_OUT> convert(final Selection<T_IN> in,
                                                         final Function<T_IN, T_OUT> converter) {
        if (in == null) {
            return null;
        }

        final Selection<T_OUT> out = Selection.selectNone();
        out.matchAll = in.matchAll;
        if (in.set != null) {
            out.set = in.set.stream().map(converter).collect(Collectors.toSet());
        }

        return out;
    }

    @Override
    public void copyFrom(final Selection<T> other) {
        this.set.clear();
        this.set.addAll(other.set);
        this.matchAll = other.matchAll;
    }

    public void add(final T id) {
        if (id == null) {
            throw new IllegalArgumentException("CriteriaSet does not allow adding null - use setMatchNull if required");
        }
        matchAll = false;
        set.add(id);
    }

    public void addAll(final Collection<T> set) {
        matchAll = false;
        this.set.addAll(set);
    }

    public boolean contains(final T id) {
        return set.contains(id);
    }

    public boolean remove(final T id) {
        matchAll = false;
        return set.remove(id);
    }

    public boolean removeAll(final Collection<T> ids) {
        matchAll = false;
        return set.removeAll(ids);
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
        matchAll = false;
    }

    public int size() {
        return set.size();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Selection)) {
            return false;
        }
        final Selection<?> that = (Selection<?>) o;
        return matchAll == that.matchAll &&
                Objects.equals(set, that.set);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchAll, set);
    }

    @Override
    public String toString() {
        if (matchAll) {
            return "any";
        }
        return set.toString();
    }

    @Override
    public Iterator<T> iterator() {
        return set.iterator();
    }
}
