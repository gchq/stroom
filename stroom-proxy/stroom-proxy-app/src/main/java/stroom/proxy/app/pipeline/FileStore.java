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


}
