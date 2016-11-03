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

import java.util.Set;

public interface EntityService<E extends Entity> {
    E load(E entity) throws RuntimeException;

    E load(E entity, Set<String> fetchSet) throws RuntimeException;

    /**
     * Save the entity.
     *
     * @return The persisted entity.
     * @throws RuntimeException
     *             If a DB error occurred during deletion such as an optimistic
     *             lock exception or entity constraint.
     */
    E save(E entity) throws RuntimeException;

    /**
     * Delete an entity.
     *
     * @return True if the entity was deleted successfully.
     * @throws RuntimeException
     *             If a DB error occurred during deletion such as an optimistic
     *             lock exception or entity constraint.
     */
    Boolean delete(E entity) throws RuntimeException;

    /**
     * Get the type of entity class associated with this service.
     *
     * @return The entity class associated with this service.
     */
    Class<E> getEntityClass();

    /**
     * Gets the type associated with this entity service.
     *
     * @return The string type of this entity service.
     */
    String getEntityType();
}
