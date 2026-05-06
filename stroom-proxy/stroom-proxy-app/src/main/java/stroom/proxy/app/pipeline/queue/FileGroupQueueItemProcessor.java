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

package stroom.proxy.app.pipeline.queue;

import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;

/**
 * Processing contract for a leased {@link FileGroupQueueItem}.
 * <p>
 * Implementations contain the stage-specific work to perform for a queue item.
 * They should read the universal {@link FileGroupQueueMessage} via
 * {@link FileGroupQueueItem#getMessage()}, resolve the referenced
 * {@link FileStoreLocation} through the appropriate {@link FileStore}, and then
 * perform the stage work.
 * </p>
 * <p>
 * Implementations should not normally call {@link FileGroupQueueItem#acknowledge()}
 * or {@link FileGroupQueueItem#fail(Throwable)} directly. A queue worker should
 * own acknowledgement so all stages get consistent at-least-once processing,
 * error handling, logging, and metrics behaviour.
 * </p>
 */

@FunctionalInterface
public interface FileGroupQueueItemProcessor {

    /**
     * Process a leased queue item.
     *
     * @param item The leased queue item to process.
     * @throws Exception If processing fails. The caller should translate this
     *                   exception into the queue implementation's fail/retry behaviour.
     */
    void process(FileGroupQueueItem item) throws Exception;
}
