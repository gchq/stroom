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
/**
 * Supported queue implementations for the proxy reference-message pipeline.
 * <p>
 * All queue types transport {@link FileGroupQueueMessage} instances that
 * reference file groups already written to a {@link FileStore}. Queue
 * implementations must not move the referenced file-group data.
 * </p>
 */
public enum QueueType {
    /**
     * A local/simple filesystem queue backed by persisted message files.
     */
    LOCAL_FILESYSTEM,

    /**
     * A Kafka-backed queue where each logical queue maps to a Kafka topic.
     */
    KAFKA,

    /**
     * An AWS SQS-backed queue where each logical queue maps to an SQS queue.
     */
    SQS
}
