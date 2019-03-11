package stroom.processor.impl.db;

import org.jooq.DSLContext;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.util.shared.HasIntCrud;

import java.util.Optional;

public interface ProcessorFilterTaskDao {
//    /**
//     * Creates the passes record object in the persistence implementation
//     * @param entity Object to persist.
//     * @return The persisted object including any changes such as auto IDs
//     */
//    ProcessorFilterTask create(final ProcessorFilterTask entity);
//
    /**
     * Fetch a record from the persistence implementation using its unique id value.
     * @param id The id to uniquely identify the required record with
     * @return The record associated with the id in the database, if it exists.
     */
    Optional<ProcessorFilterTask> fetch(final DSLContext context, final ProcessorFilterTask processorFilterTask);

    /**
     * Update the passed record in the persistence implementation
     * @param entity The record to update.
     * @return The record as it now appears in the persistence implementation
     */
    ProcessorFilterTask update(final DSLContext context, final ProcessorFilterTask processorFilterTask);

//    /**
//     * Delete the entity associated with the passed id value.
//     * @param id The unique identifier for the entity to delete.
//     * @return True if the entity was deleted. False if the id doesn't exist.
//     */
//    boolean delete(final int id);
}
