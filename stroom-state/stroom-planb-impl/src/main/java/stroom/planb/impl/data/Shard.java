package stroom.planb.impl.data;

import stroom.planb.impl.db.AbstractDb;
import stroom.planb.shared.PlanBDoc;

import java.nio.file.Path;
import java.util.function.Function;

public interface Shard {

    String SNAPSHOT_INFO_FILE_NAME = "snapshot.txt";

    /**
     * Merge data from the source dir into this shard.
     *
     * @param sourceDir The source dir to merge data from.
     */
    void merge(Path sourceDir);

    /**
     * Condense data in the shard.
     */
    void condense(PlanBDoc doc);

    /**
     * Determine if we are allowed to create a snapshot or if the snapshot we have is already the latest.
     *
     * @param request The request to create a snapshot.
     */
    void checkSnapshotStatus(SnapshotRequest request);

    /**
     * Create a snapshot ready to be streamed to a requesting node.
     */
    void createSnapshot();

    /**
     * Get data from this shard.
     *
     * @param function
     * @param <R>
     * @return
     */
    <R> R get(Function<AbstractDb<?, ?>, R> function);

    /**
     * Close the DB if it isn't currently in use for read or write.
     */
    void cleanup();


    /**
     * Delete the DB if the associated doc has been deleted.
     */
    void delete();

    /**
     * Get the Plan B doc associated with this shard.
     *
     * @return The Plan B doc associated with this shard.
     */
    PlanBDoc getDoc();
}
