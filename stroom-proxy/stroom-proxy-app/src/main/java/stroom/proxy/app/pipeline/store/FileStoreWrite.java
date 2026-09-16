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

import java.io.IOException;
import java.nio.file.Path;

/**
 * A file group being created in a {@link FileStore}.
 * <p>
 * Fill {@link #getPath()} with the group's tree, then {@link #commit()}. Closing a handle that was
 * never committed discards the write.
 * </p>
 */
public interface FileStoreWrite extends AutoCloseable {

    /**
     * A private directory to fill. Any tree is accepted: the store holds directory trees, and a
     * nested group is just a deeper one.
     */
    Path getPath();

    /**
     * Publish the group. Does not return until the group is durable by the storage's own
     * guarantee, and returns the same location on a second call.
     *
     * @return The location to publish onto a queue.
     */
    FileStoreLocation commit() throws IOException;

    /**
     * Discard the write if it was not committed. Never throws for a failure to remove staging:
     * the directory is unreferenced either way and is cleared by the store's own housekeeping.
     */
    @Override
    void close() throws IOException;
}
