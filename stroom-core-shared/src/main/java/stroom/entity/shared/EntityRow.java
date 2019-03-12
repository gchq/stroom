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

import stroom.docref.SharedObject;

public class EntityRow<E extends BaseEntity> implements SharedObject, HasEntity<E> {
    private static final long serialVersionUID = 3085808847358588309L;

    private E entity;

    public EntityRow() {
        // Default constructor necessary for GWT serialisation.
    }

    public EntityRow(final E entity) {
        this.entity = entity;
    }

    @Override
    public E getEntity() {
        return entity;
    }

    public void setEntity(final E entity) {
        this.entity = entity;
    }

    @Override
    public final int hashCode() {
        return entity.hashCode();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public final boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof EntityRow)) {
            return false;
        }
        return ((EntityRow) obj).entity.equals(entity);
    }
}
