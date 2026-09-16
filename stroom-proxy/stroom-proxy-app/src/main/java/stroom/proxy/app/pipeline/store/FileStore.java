/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.store;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Durable storage for file groups.
 * <p>
 * A queue carries a small message that names a file group; the store holds the group itself. A
 * file group is a directory tree, and a group in a store is committed, complete and immutable:
 * either {@link #resolve} hands back the whole thing or it reports that nothing is there.
 * </p>
 * <p>
 * The contract, in full, is {@code designs/infrastructure/file-stores.md} §3. The points a caller
 * must rely on:
 * </p>
 * <ul>
 *     <li>{@link FileStoreWrite#commit()} does not return until the group is durable by the
 *     storage's own guarantee, and the location it returns is safe to publish.</li>
 *     <li>At the moment a location is published its group is readable by every node the
 *     deployment mode allows to consume it. That is a guarantee about publish time only: the
 *     consumer deletes the input before it acknowledges, so a redelivered message may name a
 *     group that is gone because the work was done.</li>
 *     <li>{@link #resolve} therefore reports absence exactly, by throwing
 *     {@link FileGroupNotFoundException}, and never returns an empty or partial directory.</li>
 *     <li>{@link #delete} is idempotent.</li>
 *     <li>Nothing depends on {@link #close()} running.</li>
 * </ul>
 */
public interface FileStore extends AutoCloseable {

    /**
     * @return The configured store name, which every location this store issues carries.
     */
    String getName();

    /**
     * Begin a new file group. The handle's directory is private to the caller until
     * {@link FileStoreWrite#commit()} publishes it.
     */
    FileStoreWrite newWrite() throws IOException;

    /**
     * Resolve a location to a local directory holding a complete file group.
     * <p>
     * The directory belongs to the caller, which may move it, delete it or publish it onward, and
     * the store does not touch it afterwards. It is private in the sense that no one else is
     * reading it: a committed group has exactly one reader, the holder of the one claimed message
     * that names it. A filesystem store lends the committed directory itself; an object store
     * downloads a copy.
     * </p>
     *
     * @throws FileGroupNotFoundException If the location names no complete file group. This is
     *                                    an expected state, not a fault: it means the work the
     *                                    message describes was already done.
     * @throws IOException                If the location does not belong to this store.
     */
    Path resolve(FileStoreLocation location) throws IOException;

    /**
     * Delete the file group at a location. Deleting what is not there is success.
     *
     * @throws IOException If the location does not belong to this store, or the group is present
     *                     and cannot be removed.
     */
    void delete(FileStoreLocation location) throws IOException;

    default HealthCheck.Result healthCheck() {
        return HealthCheck.Result.healthy();
    }

    /**
     * Release connections. Correctness never rests on this having run.
     */
    @Override
    default void close() throws Exception {
        // A filesystem store holds nothing between operations.
    }
}
