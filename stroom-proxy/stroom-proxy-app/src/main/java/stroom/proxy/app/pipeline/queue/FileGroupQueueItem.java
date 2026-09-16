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

import java.io.IOException;

/**
 * A claimed message. Exactly one of {@link #acknowledge()} and {@link #fail(Throwable)} completes
 * it; {@link #close()} without either releases the claim so the message is delivered again.
 */
public interface FileGroupQueueItem extends AutoCloseable {

    /**
     * The backend's own identity for this delivery, for logs.
     */
    String getId();

    FileGroupQueueMessage getMessage();

    /**
     * How many times this message has been delivered, counting this one. 1 on first delivery.
     */
    int getDeliveryAttempt();

    /**
     * The work is done; the message is gone. Idempotent.
     */
    void acknowledge() throws IOException;

    /**
     * The work failed. The message is delivered again later, at the tail, with the attempt
     * counted - or, once the attempts are exhausted, leaves the queue for the mode's dead-letter
     * mechanism. Idempotent.
     */
    void fail(Throwable error) throws IOException;

    /**
     * Release the claim. If the item was neither acknowledged nor failed, the message becomes
     * deliverable again; a consumer that stops holding an item without completing it has released
     * it. Always called, in a {@code finally}.
     */
    @Override
    void close() throws IOException;
}
