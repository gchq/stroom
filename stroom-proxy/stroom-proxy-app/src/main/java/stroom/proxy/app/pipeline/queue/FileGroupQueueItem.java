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
import java.util.Map;

/**
 * A leased item from a {@link FileGroupQueue}.
 * <p>
 * Queue implementations return this contract to consumers. The item contains the
 * universal reference message plus queue-implementation metadata needed for
 * diagnostics and acknowledgement. Consumers must explicitly acknowledge
 * successfully processed items, or fail items that could not be processed.
 * </p>
 * <p>
 * The item does not expose a filesystem path convenience method. Consumers must
 * use {@link #getMessage()} and resolve the message's {@link FileStoreLocation}
 * through the appropriate {@link FileStore}.
 * </p>
 */
public interface FileGroupQueueItem extends AutoCloseable {

    /**
     * @return The queue implementation's unique identifier for this leased item.
     */
    String getId();

    /**
     * @return The reference message published to the queue.
     */
    FileGroupQueueMessage getMessage();

    /**
     * @return Queue-implementation metadata associated with this item.
     */
    Map<String, String> getMetadata();

    /**
     * Acknowledge successful processing of this item.
     * <p>
     * After this method succeeds, the queue implementation should not make this
     * item visible for processing again.
     * </p>
     *
     * @throws IOException If the acknowledgement cannot be persisted or sent.
     */
    void acknowledge() throws IOException;

    /**
     * Mark this item as failed.
     * <p>
     * Queue implementations may make the item visible again, delay it for retry,
     * or move it to a failed/dead-letter location according to their retry
     * policy.
     * </p>
     *
     * @param error The processing error.
     * @throws IOException If the failure cannot be persisted or sent.
     */
    void fail(Throwable error) throws IOException;

    /**
     * Close the leased item, releasing any local resources held by the queue
     * implementation.
     * <p>
     * Closing an item is not a substitute for {@link #acknowledge()} or
     * {@link #fail(Throwable)}.
     * </p>
     *
     * @throws IOException If resources cannot be released.
     */
    @Override
    void close() throws IOException;
}
