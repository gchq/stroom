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
import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "inex")
@XmlType(name = "inex", propOrder = { "include", "exclude" })
public class IncludeExcludeEntityIdSet<T extends BaseEntity>
        implements Copyable<IncludeExcludeEntityIdSet<T>>, HasIsConstrained, Matcher<T>, Clearable, SharedObject {
    private static final long serialVersionUID = 7153300977968635056L;

    private EntityIdSet<T> include;
    private EntityIdSet<T> exclude;

    public IncludeExcludeEntityIdSet() {
        // Default constructor necessary for GWT serialisation.
    }

    public IncludeExcludeEntityIdSet(final EntityIdSet<T> include, final EntityIdSet<T> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    public void setInclude(final EntityIdSet<T> include) {
        this.include = include;
    }

    @XmlElement(name = "include")
    public EntityIdSet<T> getInclude() {
        return include;
    }

    public EntityIdSet<T> obtainInclude() {
        if (getInclude() == null) {
            setInclude(new EntityIdSet<T>());
        }
        return getInclude();
    }

    public void setExclude(final EntityIdSet<T> exclude) {
        this.exclude = exclude;
    }

    @XmlElement(name = "exclude")
    public EntityIdSet<T> getExclude() {
        return exclude;
    }

    public EntityIdSet<T> obtainExclude() {
        if (getExclude() == null) {
            setExclude(new EntityIdSet<T>());
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
    public void copyFrom(final IncludeExcludeEntityIdSet<T> other) {
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
        } else if (!(o instanceof IncludeExcludeEntityIdSet)) {
            return false;
        }

        final IncludeExcludeEntityIdSet<T> other = (IncludeExcludeEntityIdSet<T>) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.include, other.include);
        builder.append(this.exclude, other.exclude);
        return builder.isEquals();
    }
}
