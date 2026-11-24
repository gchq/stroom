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

package stroom.ai.impl.tools.streams;

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/// A segmented stream's segments (an Events stream: segment 0 its header, then an event a segment, the last its
/// footer), read by number as `fetch_events` reads an index's StreamId and EventId: the EventId is the event's
/// segment.
public final class EventSegments {

    private EventSegments() {
        // Static only.
    }

    /// @param provider A part of a stream.
    /// @return How many segments it has.
    /// @throws IOException if it can't be read.
    public static long count(final InputStreamProvider provider) throws IOException {
        try (final SegmentInputStream in = provider.get()) {
            return in.count();
        }
    }

    /// @param provider A part of a stream.
    /// @param segment  The segment's number.
    /// @param maxBytes The most bytes read.
    /// @return The segment's text, or null if it is larger than the limit.
    /// @throws IOException if it can't be read.
    public static String read(final InputStreamProvider provider,
                              final long segment,
                              final int maxBytes) throws IOException {
        try (final SegmentInputStream in = provider.get()) {
            in.include(segment);
            final byte[] bytes = in.readNBytes(maxBytes + 1);
            return bytes.length > maxBytes
                    ? null
                    : new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
