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

package stroom.proxy.app.pipeline;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Stable storage for proxy file groups.
 * <p>
 * A {@link FileStore} owns the durable data location used by a pipeline stage.
 * Queue implementations should only transport {@link FileStoreLocation}
 * references to data that has already been successfully written to a store.
 * </p>
 */
public interface FileStore {

    /**
     * @return The configured store name used in queue messages.
     */
    String getName();

    /**
     * Begin writing a new file group.
     * <p>
     * Implementations should provide an isolated writable directory and should
     * only expose the final {@link FileStoreLocation} once {@link FileStoreWrite#commit()}
     * has succeeded.
     * </p>
     *
     * @return A writable file-group handle.
     * @throws IOException If a writable location cannot be created.
     */
    FileStoreWrite newWrite() throws IOException;

    /**
     * Resolve a store location to a local filesystem path.
     * <p>
     * This method is intended for the initial local/shared filesystem
     * implementation. Future object-store implementations may require a richer
     * access abstraction.
     * </p>
     *
     * @param location The location to resolve.
     * @return The local filesystem path for the supplied location.
     * @throws IOException If the location cannot be resolved by this store.
     */
    Path resolve(FileStoreLocation location) throws IOException;

    /**
     * Delete the file group at the given location.
     * <p>
     * This is the ownership-transfer operation: once a stage has durably
     * written its output and published a downstream queue message, it should
     * call this method to release the consumed input. The implementation
     * must remove the file-group directory and its contents but must not
     * remove the writer root or store root.
     * </p>
     * <p>
     * Deleting a location that has already been deleted (or never existed)
     * should be treated as a no-op so that at-least-once replay is safe.
     * </p>
     *
     * @param location The location to delete.
     * @throws IOException If the location belongs to a different store or
     * cannot be deleted.
     */
    void delete(FileStoreLocation location) throws IOException;

    /**
     * Check whether a file-group location represents a fully committed write.
     * <p>
     * A location is considered complete if it exists and contains a
     * completeness marker written by {@link FileStoreWrite#commit()}.
     * This is used for idempotency: if a stage's output already exists
     * and is complete, the stage can skip re-processing and re-publish
     * the onward queue message instead.
     * </p>
     * <p>
     * A location that does not exist, or that exists but has no completeness
     * marker, is considered incomplete (e.g. a partial write from a crash).
     * </p>
     *
     * @param location The location to check.
     * @return {@code true} if the location is fully committed.
     * @throws IOException If the location cannot be checked.
     */
    boolean isComplete(FileStoreLocation location) throws IOException;

    /**
     * Begin writing a new file group at a deterministic path derived from
     * the given file-group ID.
     * <p>
     * Unlike {@link #newWrite()}, which allocates a sequential path, this
     * method always resolves the same output path for the same
     * {@code fileGroupId}. This enables idempotent processing: if the
     * output already exists and is complete, the caller can skip writing
     * and use the existing location.
     * </p>
     *
     * @param fileGroupId A stable identifier for the file group
     *                    (typically the queue message's fileGroupId).
     * @return A writable file-group handle targeting the deterministic path,
     *         or a pre-committed handle if the output already exists.
     * @throws IOException If a writable location cannot be created.
     */
    FileStoreWrite newDeterministicWrite(String fileGroupId) throws IOException;

    /**
     * Perform a health check on this file store's backend.
     * <p>
     * The default implementation returns healthy. Implementations with
     * external backends (S3) or filesystem dependencies should override
     * this to verify accessibility and report store-level status.
     * </p>
     *
     * @return A Dropwizard health check result.
     */
    default HealthCheck.Result healthCheck() {
        return HealthCheck.Result.healthy();
    }

}
