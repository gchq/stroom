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

package stroom.core.db.migration._V07_00_00.streamstore.shared;

import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_BaseEntity;

import stroom.util.shared.Clearable;
import stroom.util.shared.Copyable;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HasIsConstrained;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.Matcher;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@Deprecated
@XmlRootElement(name = "inex")
@XmlType(name = "inex", propOrder = {"include", "exclude"})
public class _V07_00_00_IncludeExcludeEntityIdSet<T extends _V07_00_00_BaseEntity>
        implements Copyable<_V07_00_00_IncludeExcludeEntityIdSet<T>>, HasIsConstrained, Matcher<T>, Clearable {
    private static final long serialVersionUID = 7153300977968635056L;

    private _V07_00_00_EntityIdSet<T> include;
    private _V07_00_00_EntityIdSet<T> exclude;

    public _V07_00_00_IncludeExcludeEntityIdSet() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_IncludeExcludeEntityIdSet(final _V07_00_00_EntityIdSet<T> include, final _V07_00_00_EntityIdSet<T> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    @XmlElement(name = "include")
    public _V07_00_00_EntityIdSet<T> getInclude() {
        return include;
    }

    public void setInclude(final _V07_00_00_EntityIdSet<T> include) {
        this.include = include;
    }

    public _V07_00_00_EntityIdSet<T> obtainInclude() {
        if (getInclude() == null) {
            setInclude(new _V07_00_00_EntityIdSet<>());
        }
        return getInclude();
    }

    @XmlElement(name = "exclude")
    public _V07_00_00_EntityIdSet<T> getExclude() {
        return exclude;
    }

    public void setExclude(final _V07_00_00_EntityIdSet<T> exclude) {
        this.exclude = exclude;
    }

    public _V07_00_00_EntityIdSet<T> obtainExclude() {
        if (getExclude() == null) {
            setExclude(new _V07_00_00_EntityIdSet<>());
        }
        return getExclude();
    }

    @Override
    public boolean isConstrained() {
        if (getInclude() != null && getInclude().isConstrained()) {
            return true;
        }
        return getExclude() != null && getExclude().isConstrained();
    }

    @Override
    public boolean isMatch(final T e) {
        if (getInclude() != null) {
            if (!getInclude().isMatch(e)) {
                return false;
            }
        }
        if (getExclude() != null) {
            if (getExclude().isMatch(e)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void clear() {
        if (getInclude() != null) {
            getInclude().clear();
        }
        if (getExclude() != null) {
            getExclude().clear();
        }
    }

    @Override
    public void copyFrom(final _V07_00_00_IncludeExcludeEntityIdSet<T> other) {
        if (other.getInclude() == null) {
            setInclude(null);
        } else {
            this.obtainInclude().copyFrom(other.getInclude());
        }

        if (other.getExclude() == null) {
            setExclude(null);
        } else {
            this.obtainExclude().copyFrom(other.getExclude());
        }
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(include);
        builder.append(exclude);

        return builder.toHashCode();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof _V07_00_00_IncludeExcludeEntityIdSet)) {
            return false;
        }

        final _V07_00_00_IncludeExcludeEntityIdSet<T> other = (_V07_00_00_IncludeExcludeEntityIdSet<T>) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.include, other.include);
        builder.append(this.exclude, other.exclude);
        return builder.isEquals();
    }
}
