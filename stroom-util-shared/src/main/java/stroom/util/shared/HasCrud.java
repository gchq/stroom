/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.shared;

import java.util.Optional;

public interface HasCrud<T_ENTITY, T_ID> {

    /**
     * Creates the passed entity object in the persistence implementation
     *
     * @param entity entity to persist.
     * @return The created object including any changes such as auto IDs
     */
    T_ENTITY create(final T_ENTITY entity);

    /**
     * Fetch a record from the persistence implementation using its unique id value.
     *
     * @param id The id to uniquely identify the required record with
     * @return The record associated with the id in the persistence implementation, if it exists.
     */
    Optional<T_ENTITY> fetch(final T_ID id);

    /**
     * Update the passed record in the persistence implementation
     *
     * @param entity The entity to update.
     * @return The record as it now appears in the persistence implementation
     */
    T_ENTITY update(final T_ENTITY entity);

    /**
     * Delete the entity associated with the passed id value.
     *
     * @param id The unique identifier for the entity to delete.
     * @return True if the entity was deleted. False if the id doesn't exist.
     */
    boolean delete(final T_ID id);
}
