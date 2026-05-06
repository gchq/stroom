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
 * A write handle for a file group being created in a {@link FileStore}.
 * <p>
 * Implementations should provide a writable local path for the current
 * filesystem-backed implementation. Once the producer has finished writing all
 * file-group content, it must call {@link #commit()} before publishing the
 * returned {@link FileStoreLocation} to a queue.
 * </p>
 * <p>
 * Closing an uncommitted write should abandon the write and clean up any
 * temporary state where possible.
 * </p>
 */
public interface FileStoreWrite extends AutoCloseable {

    /**
     * The writable path allocated for this write.
     */
    Path getPath();

    /**
     * Commit this write and make it visible as a stable file-store location.
     *
     * @return The stable location suitable for queue publication.
     */
    FileStoreLocation commit() throws IOException;

    /**
     * @return True once this write has been successfully committed.
     */
    boolean isCommitted();

    /**
     * Close the write handle, cleaning up any uncommitted state where possible.
     */
    @Override
    void close() throws IOException;
}
