package stroom.planb.impl.data;

import stroom.planb.impl.db.AbstractLmdb;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.function.Function;

interface Shard {

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
    void condense();

    /**
     * Determine if we are allowed to create a snapshot or if the snapshot we have is already the latest.
     *
     * @param request The request to create a snapshot.
     */
    void checkSnapshotStatus(SnapshotRequest request);

    /**
     * Actually create a snapshot and stream it to the supplied output stream.
     *
     * @param request      The request to create a snapshot.
     * @param outputStream The output stream to write the snapshot to.
     */
    void createSnapshot(SnapshotRequest request, OutputStream outputStream);

    /**
     * Get data from this shard.
     *
     * @param function
     * @param <R>
     * @return
     */
    <R> R get(Function<AbstractLmdb<?, ?>, R> function);

    /**
     * Close the DB if it isn't currently in use for read or write.
     */
    void cleanup();
}
