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

package stroom.proxy.app.pipeline.queue;

import stroom.proxy.app.pipeline.store.FileStore;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.time.Duration;
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
     * As {@link #next()}, but may wait up to {@code maxWait} for an item to become available rather
     * than returning empty immediately.
     * <p>
     * Consumers are meant to wait for work, not poll for it. The remote backends already do: Kafka
     * polls the broker and SQS long-polls. The default here preserves the old non-waiting behaviour
     * for any implementation that has nothing to wait on.
     * </p>
     *
     * @param maxWait The longest to wait for an item before returning empty.
     * @return The next leased queue item, or empty if none became available within {@code maxWait}.
     * @throws IOException If the queue cannot be read.
     */
    default Optional<FileGroupQueueItem> next(final Duration maxWait) throws IOException {
        return next();
    }

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
