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

/**
 * Thrown by {@link FileStore#resolve(FileStoreLocation)} when the location names no complete file
 * group — because it was never written, because it was already consumed and deleted, or because a
 * write was interrupted before it could publish.
 * <p>
 * <strong>This is how a store expresses absence</strong> (contracts.md R10, §3.3), and it is a
 * distinct type rather than a bare {@link java.io.IOException} because absence and failure need
 * different answers. A store must throw it, and never return an empty or partial directory, for
 * that difference to be trusted.
 * </p>
 * <p>
 * Absence is not an error. Under R12 a queue item that resolves to nothing is acknowledged: the
 * pipeline deletes an input only after its output is durable and published, so the input being
 * gone is proof the work was done - routinely, in shared mode, when a message is redelivered after
 * another node consumed it. {@code FileGroupQueueWorker} and the aggregate stage's claimer
 * acknowledge on this exception; a processor lets it propagate.
 * </p>
 */
public class FileGroupNotFoundException extends java.io.IOException {

    private final FileStoreLocation location;

    public FileGroupNotFoundException(final FileStoreLocation location, final String message) {
        super(message);
        this.location = location;
    }

    /**
     * @return The location that resolved to nothing.
     */
    public FileStoreLocation getLocation() {
        return location;
    }
}
