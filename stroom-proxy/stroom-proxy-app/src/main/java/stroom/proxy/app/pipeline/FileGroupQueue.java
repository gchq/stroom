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
import java.util.Optional;

/**
 * Message-based queue contract for proxy file groups.
 * <p>
 * Implementations transport {@link FileGroupQueueMessage} instances only. They
 * must not move or mutate the referenced file-group data in the associated
 * {@link FileStore}.
 * </p>
 */
public interface FileGroupQueue extends AutoCloseable {

    /**
     * @return The logical queue name configured for this queue.
     */
    String getName();

    /**
     * @return The queue implementation type.
     */
    QueueType getType();

    /**
     * Publish a reference message to the queue.
     *
     * @param message The message to publish.
     * @throws IOException If publication cannot be persisted or sent.
     */
    void publish(FileGroupQueueMessage message) throws IOException;

    /**
     * Lease the next available queue item, if one is currently available.
     *
     * @return The next leased queue item, or empty if no item is available.
     * @throws IOException If the queue cannot be read.
     */
    Optional<FileGroupQueueItem> next() throws IOException;

    /**
     * Close any transport resources held by the queue implementation.
     *
     * @throws IOException If close fails.
     */
    @Override
    void close() throws IOException;

    /**
     * Perform a health check on this queue's backend connectivity.
     * <p>
     * The default implementation returns healthy. Queue implementations that
     * depend on external backends (SQS, Kafka) should override this to verify
     * connectivity and report queue-level status.
     * </p>
     *
     * @return A Dropwizard health check result.
     */
    default HealthCheck.Result healthCheck() {
        return HealthCheck.Result.healthy();
    }
}
