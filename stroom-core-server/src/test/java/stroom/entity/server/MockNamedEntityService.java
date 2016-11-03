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

package stroom.entity.server;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.NamedEntityService;

import java.util.Set;

public abstract class MockNamedEntityService<E extends NamedEntity, C extends FindNamedEntityCriteria>
        extends MockSystemEntityService<E, C> implements NamedEntityService<E> {
    @Override
    public E create(final String name) throws RuntimeException {
        try {
            final E entity = getEntityClass().newInstance();
            entity.setName(name);
            return save(entity);
        } catch (final Exception e) {
            throw new EntityServiceException(e.getMessage());
        }
    }

    @Override
    public E loadByName(final String name) throws RuntimeException {
        final BaseResultList<E> results = find(null);
        if (results == null) {
            return null;
        }

        for (final E entity : results) {
            boolean found = true;

            if (name != null && !name.equals(entity.getName())) {
                found = false;
            }

            if (found) {
                return entity;
            }
        }

        return null;
    }

    @Override
    public E loadByName(final String name, final Set<String> fetchSet) throws RuntimeException {
        return loadByName(name);
    }

    @Override
    public String getNamePattern() {
        return null;
    }
}
