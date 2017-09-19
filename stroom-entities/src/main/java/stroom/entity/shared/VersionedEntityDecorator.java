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

public class VersionedEntityDecorator<T extends BaseEntity> {
    private final T entity;
    private final String user;

    public VersionedEntityDecorator(final T entity, final String user) {
        this.entity = entity;
        this.user = user;
    }

    public T getEntity() {
        return entity;
    }

    @Override
    public int hashCode() {
        int hash = entity.hashCode();
        hash = 31 * hash + entity.getVersion();
        hash = 31 * hash + (user != null ? user.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj != null && !(obj instanceof VersionedEntityDecorator<?>)) {
            return false;
        }

        final VersionedEntityDecorator<?> entityDecorator = (VersionedEntityDecorator<?>) obj;
        if (entity == null || entityDecorator.entity == null) {
            return entity == null && entityDecorator.entity == null;
        }

        if (!entity.getType().equals(entityDecorator.entity.getType())) {
            return false;
        }

        if (!entity.equals(entityDecorator.entity)) {
            return false;
        }

        if (entity.getVersion() != entityDecorator.entity.getVersion()) {
            return false;
        }

        return user != null ? user.equals(entityDecorator.user) : entityDecorator.user == null;
    }
}
