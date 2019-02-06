package stroom.entity;

import javax.annotation.Nonnull;
import java.util.Optional;

public interface BasicIntCrudDao<EntityType> {

    /**
     * Persist the passes record object
     * @param record Object to persist.
     * @return The persisted object including any changes such as auto IDs
     */
    EntityType create(@Nonnull final EntityType record);

    /**
     * Update the passed record in the database
     * @param record The record to update.
     * @return The record as it now appears in the database
     */
    EntityType update(@Nonnull final EntityType record);

    /**
     * Delete the record associated with the passed id value.
     * @param id The unique identifier for the record to delete.
     * @return The number of records deleted.
     */
    int delete(final int id);

    /**
     * Fetch a record from the database using its unique id value.
     * @param id The id to uniquely identify the required record with
     * @return The record associated with the id in the database, if it exists.
     */
    Optional<EntityType> fetch(final int id);
}
