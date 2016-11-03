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

package stroom.pipeline.processor.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

public class SetId implements SharedObject {
    private static final long serialVersionUID = 6406438993391927186L;

    private String id;
    private String entityType;

    public SetId() {
    }

    public SetId(final String id, final String entityType) {
        this.id = id;
        this.entityType = entityType;
    }

    public String getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(id);
        builder.append(entityType);

        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof SetId)) {
            return false;
        }

        final SetId other = (SetId) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.id, other.id);
        builder.append(this.entityType, other.entityType);
        return builder.isEquals();
    }
}
