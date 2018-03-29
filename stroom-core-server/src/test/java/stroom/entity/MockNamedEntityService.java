/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;

import java.util.Set;

public abstract class MockNamedEntityService<E extends NamedEntity, C extends FindNamedEntityCriteria>
        extends MockSystemEntityService<E, C> implements NamedEntityService<E> {
    @Override
    public boolean isMatch(final C criteria, final E entity) {
        return super.isMatch(criteria, entity) && (criteria.getName() == null || criteria.getName().isMatch(entity.getName()));
    }

    @Override
    public E create(final String name) {
        try {
            final E entity = getEntityClass().newInstance();
            entity.setName(name);
            return save(entity);
        } catch (final InstantiationException | IllegalAccessException | RuntimeException e) {
            throw new EntityServiceException(e.getMessage());
        }
    }

    @Override
    public E loadByName(final String name) {
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
    public E loadByName(final String name, final Set<String> fetchSet) {
        return loadByName(name);
    }

    @Override
    public String getNamePattern() {
        return null;
    }
}
