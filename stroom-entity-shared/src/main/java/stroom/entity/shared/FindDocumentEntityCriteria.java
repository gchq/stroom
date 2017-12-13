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

public abstract class FindDocumentEntityCriteria extends FindNamedEntityCriteria
        implements Copyable<FindDocumentEntityCriteria> {
    private static final long serialVersionUID = -970306839701196839L;

    private String requiredPermission;

    public FindDocumentEntityCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindDocumentEntityCriteria(final String name) {
        super(name);
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(final String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(requiredPermission);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof FindDocumentEntityCriteria)) {
            return false;
        }

        final FindDocumentEntityCriteria criteria = (FindDocumentEntityCriteria) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(requiredPermission, criteria.requiredPermission);
        return builder.isEquals();
    }

    @Override
    public void copyFrom(final FindDocumentEntityCriteria other) {
        this.requiredPermission = other.requiredPermission;
        super.copyFrom(other);
    }
}