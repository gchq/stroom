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

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Transport between stages for messages that name file groups.
 * <p>
 * A queue promises one thing: a published message is delivered, at least once, to some consumer of
 * the queue, until a consumer acknowledges it. The contract in full is
 * {@code designs/infrastructure/queues.md} §3.
 * </p>
 */
public interface FileGroupQueue extends AutoCloseable {

    String getName();

    QueueType getType();

    /**
     * Publish a message. Does not return until the publish is durable - forced to disk, or
     * acknowledged by the broker - so the caller may then delete its input.
     */
    void publish(FileGroupQueueMessage message) throws IOException;

    /**
     * Claim the next message, waiting up to {@code maxWait} for one to arrive. A claimed message is
     * held by this consumer alone until the item is acknowledged, failed or closed.
     */
    Optional<FileGroupQueueItem> next(Duration maxWait) throws IOException;

    /**
     * Claim the next message if one is available now.
     */
    default Optional<FileGroupQueueItem> next() throws IOException {
        return next(Duration.ZERO);
    }

    default HealthCheck.Result healthCheck() {
        return HealthCheck.Result.healthy();
    }

    /**
     * Release transport resources. Correctness never rests on this having run.
     */
    @Override
    void close() throws IOException;
}
