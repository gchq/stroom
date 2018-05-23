/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.docref.SharedObject;

import javax.xml.bind.annotation.XmlTransient;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Utility class to match on sets of id's With concepts like match anything,
 * null, nothing and specific id's.
 * <p>
 * By default when created it has no criteria i.e. match anything. As soon as
 * you update it it will be restrictive until you setMatchAll
 */
public class CriteriaSet<T>
        implements SharedObject, Iterable<T>, Copyable<CriteriaSet<T>>, HasIsConstrained, Matcher<T>, Clearable {
    private static final long serialVersionUID = 1L;

    /**
     * By default the criteria will match anything. NULL matchAll implies match
     * anything when nothing is in the set or we are to not matchNull. By
     * setting match all it will match all regardless.
     */
    private Boolean matchAll = null;
    private Boolean matchNull = null;
    private Set<T> set;

    public CriteriaSet() {
        this(new HashSet<>());
    }

    public CriteriaSet(final Set<T> set) {
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

    public boolean isMatchNothing() {
        return Boolean.FALSE.equals(matchAll) && set.isEmpty() && !Boolean.TRUE.equals(matchNull);
    }

    @Override
    public boolean isConstrained() {
        if (Boolean.TRUE.equals(matchAll)) {
            return false;
        }
        if (isMatchNothing()) {
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

    public boolean isSingleItemMatch(final T item) {
        if (Boolean.TRUE.equals(matchNull) && item == null) {
            return true;
        }
        return set != null && set.size() == 1 && set.contains(item);
    }

    @Override
    public void copyFrom(final CriteriaSet<T> other) {
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

    @XmlTransient
    public T getSingleItem() {
        if (!isConstrained()) {
            return null;
        }
        if (set == null || set.size() != 1) {
            return null;
        }
        return set.iterator().next();
    }

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

    protected Set<T> getSet() {
        return set;
    }

    protected void setSet(final Set<T> set) {
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
        if (obj == null || !(obj instanceof CriteriaSet)) {
            return false;
        }

        @SuppressWarnings("unchecked") final CriteriaSet<T> criteriaSet = (CriteriaSet<T>) obj;

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
