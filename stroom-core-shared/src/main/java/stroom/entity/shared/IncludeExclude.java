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

public class IncludeExclude<T> implements SharedObject {
    private static final long serialVersionUID = 592176582746352453L;

    private T include;
    private T exclude;

    public IncludeExclude() {
        // Default constructor necessary for GWT serialisation.
    }

    public IncludeExclude(final T include, final T exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    public T getInclude() {
        return include;
    }

    public void setInclude(final T include) {
        this.include = include;
    }

    public T getExclude() {
        return exclude;
    }

    public void setExclude(final T exclude) {
        this.exclude = exclude;
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
        } else if (!(o instanceof IncludeExclude)) {
            return false;
        }

        final IncludeExclude<T> other = (IncludeExclude<T>) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.include, other.include);
        builder.append(this.exclude, other.exclude);
        return builder.isEquals();
    }
}
