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

package stroom.importexport.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import stroom.entity.shared.Entity;
import stroom.entity.shared.NamedEntity;

/**
 * Class to all the generic call to contain the entity type and class so that
 * they can be security checked correctly
 */
public class ClassTypeMap implements Serializable {
    private static final long serialVersionUID = -3931661635840196195L;

    private final Map<String, Class<? extends Entity>> entityTypeMap = new HashMap<>();
    private final Map<Class<? extends Entity>, String> entityClassMap = new HashMap<>();
    private final Map<String, Integer> entityPriorityMap = new HashMap<>();
    private final List<String> entityTypeList = new ArrayList<>();

    private int priority = 1;

    public void registerEntity(final Class<? extends Entity> entityClass) {
        try {
            final String entityType = entityClass.newInstance().getType();

            entityTypeMap.put(entityType, entityClass);
            entityClassMap.put(entityClass, entityType);
            entityPriorityMap.put(entityType, priority++);
            entityTypeList.add(entityType);

        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void registerEntityReference(final Class<? extends Entity> entityClass) {
        try {
            final String entityType = entityClass.newInstance().getType();

            entityTypeMap.put(entityType, entityClass);
            entityClassMap.put(entityClass, entityType);

        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Class<? extends Entity> getEntityClass(final String entityType) {
        return entityTypeMap.get(entityType);
    }

    public String getEntityType(final Class<? extends Entity> clazz) {
        return entityClassMap.get(clazz);
    }

    public Integer getEntityPriority(final String entityType) {
        return entityPriorityMap.get(entityType);
    }

    public List<String> getEntityTypeList() {
        return entityTypeList;
    }
}
