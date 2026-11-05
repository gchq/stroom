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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.StreamReadException;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/// Streams a build remembers (a survey's) that are gone: deleted, or no longer there for the user. Seen in testing:
/// a feed's streams deleted part-way through a build and uploaded again; the survey kept pointing at the deleted
/// ones.
final class GoneStreams {

    private GoneStreams() {
        // Static only.
    }

    /// @param streamReader Reads streams' meta.
    /// @param streamIds    Streams.
    /// @return Those deleted, or that can't be found.
    static Set<Long> of(final StreamReader streamReader, final Collection<Long> streamIds) {
        final Set<Long> gone = new TreeSet<>();
        for (final long streamId : streamIds) {
            try {
                final Meta meta = streamReader.meta(streamId);
                if (meta != null && meta.getStatus() == Status.DELETED) {
                    gone.add(streamId);
                }
            } catch (final StreamReadException e) {
                gone.add(streamId);
            }
        }
        return gone;
    }
}
