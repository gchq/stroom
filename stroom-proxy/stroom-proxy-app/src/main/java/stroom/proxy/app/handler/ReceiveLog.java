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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.util.io.ByteCountInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * The receive audit log's two success lines, and the drain a dropped body gets so that the sender
 * sees a normal response. Failures are logged by the entry point, which knows the status code.
 */
final class ReceiveLog {

    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private ReceiveLog() {
    }

    static void received(final LogStream logStream,
                         final AttributeMap attributeMap,
                         final String source,
                         final long bytes,
                         final Instant startTime) {
        log(logStream, attributeMap, EventType.RECEIVE, source, bytes, startTime);
    }

    static void dropped(final LogStream logStream,
                        final AttributeMap attributeMap,
                        final String source,
                        final long bytes,
                        final Instant startTime) {
        log(logStream, attributeMap, EventType.DROP, source, bytes, startTime);
    }

    /**
     * Read a body to its end and discard it, so a dropped request completes like any other.
     *
     * @return The number of bytes read.
     */
    static long drain(final InputStreamSupplier body) throws IOException {
        try (final ByteCountInputStream in = new ByteCountInputStream(body.get())) {
            final byte[] buffer = LocalByteBuffer.get();
            while (in.read(buffer) >= 0) {
                // Discard.
            }
            return in.getCount();
        }
    }

    private static void log(final LogStream logStream,
                            final AttributeMap attributeMap,
                            final EventType eventType,
                            final String source,
                            final long bytes,
                            final Instant startTime) {
        logStream.log(
                RECEIVE_LOG,
                attributeMap,
                eventType,
                source,
                StroomStatusCode.OK,
                attributeMap.get(StandardHeaderArguments.RECEIPT_ID),
                bytes,
                Duration.between(startTime, Instant.now()).toMillis());
    }
}
