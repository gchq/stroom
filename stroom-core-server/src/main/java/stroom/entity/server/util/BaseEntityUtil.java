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

package stroom.entity.server.util;

import org.springframework.beans.BeanUtils;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.NamedEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BaseEntityUtil {
    @SuppressWarnings("unchecked")
    public static <T extends BaseEntity> T clone(final T t) {
        T r;
        try {
            r = (T) t.getClass().newInstance();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        BeanUtils.copyProperties(t, r);
        return r;
    }

    /**
     * All entities support no argument constructors.
     */
    public static <T extends BaseEntity> T newInstance(Class<T> entityClass) {
        try {
            return entityClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compare 2 collections to see if they contain the same items (ignore
     * duplicates and order)
     */
    public static <T extends BaseEntity> boolean equalsEntity(final Collection<T> lhs, final Collection<T> rhs) {
        if (lhs == null && rhs == null) {
            return true;
        }
        if (lhs == null || rhs == null) {
            return false;
        }

        final Set<Long> lhsSet = new HashSet<>();
        final Set<Long> rhsSet = new HashSet<>();
        lhsSet.addAll(lhs.stream().map(T::getId).collect(Collectors.toList()));
        rhsSet.addAll(rhs.stream().map(T::getId).collect(Collectors.toList()));
        return lhsSet.equals(rhsSet);

    }

    /**
     * Find a entity in a collection by it's id.
     */
    public static <T extends BaseEntity> T find(final Collection<T> collection, final long pk) {
        for (final T entity : collection) {
            if (entity.getId() == pk) {
                return entity;
            }
        }
        return null;
    }

    public static <T extends BaseEntity> void sort(final List<T> list) {
        Collections.sort(list, (o1, o2) -> Long.compare(o1.getId(), o2.getId()));
    }

    public static <T extends NamedEntity> Map<String, T> toNameMap(final Collection<T> collection) {
        final Map<String, T> map = new HashMap<>();
        for (final T e : collection) {
            map.put(e.getName(), e);
        }
        return map;
    }

    /**
     * Find a entity in a collection by it's id.
     */
    public static <T extends NamedEntity> T findByName(final Collection<T> collection, final String name) {
        for (final T entity : collection) {
            if (name.equals(entity.getName())) {
                return entity;
            }
        }
        return null;
    }
}
