package stroom.util.shared;

import java.util.Optional;

public interface HasIntCrud<T_Entity> {
    /**
     * Creates the passes record object in the persistence implementation
     *
     * @param entity Object to persist.
     * @return The persisted object including any changes such as auto IDs
     */
    T_Entity create(T_Entity entity);

    /**
     * Fetch a record from the persistence implementation using its unique id value.
     *
     * @param id The id to uniquely identify the required record with
     * @return The record associated with the id in the database, if it exists.
     */
    Optional<T_Entity> fetch(int id);

    /**
     * Update the passed record in the persistence implementation
     *
     * @param entity The record to update.
     * @return The record as it now appears in the persistence implementation
     */
    T_Entity update(T_Entity entity);

    /**
     * Delete the entity associated with the passed id value.
     *
     * @param id The unique identifier for the entity to delete.
     * @return True if the entity was deleted. False if the id doesn't exist.
     */
    boolean delete(int id);
}
