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
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SourceTypeMeta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The meta attributes of streams' parts (their receipt headers, what `stroom:meta()` reads), each part read once in
/// a call: for a source type field read from a part's meta ([SourceTypeMeta]), whose value every record of the part
/// has.
final class PartAttributes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PartAttributes.class);

    /// The most parts read to find one carrying the attribute named.
    static final int MOST_PARTS = 200;

    private final StreamReader streamReader;
    private final Map<String, Map<String, String>> read = new HashMap<>();

    /// @param streamReader Reads the parts' meta.
    PartAttributes(final StreamReader streamReader) {
        this.streamReader = streamReader;
    }

    /// @param stream A stream.
    /// @param part   One of its parts.
    /// @return The part's meta attributes; none when it carries none, or they can't be read.
    Map<String, String> of(final long stream, final int part) {
        return read.computeIfAbsent(stream + ":" + part, k -> {
            try {
                final Map<String, String> attributes = streamReader.partAttributes(stream, part);
                return attributes == null
                        ? Map.of()
                        : attributes;
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("No meta for stream {} part {}: {}", stream, part,
                        e.getMessage()));
                return Map.of();
            }
        });
    }

    /// @param field  The source type field, `meta:<Attribute>`.
    /// @param stream A stream.
    /// @param part   One of its parts.
    /// @return The fields laid over the part's records ([SourceTypeMeta#partFields]).
    Map<String, Object> fields(final String field, final long stream, final int part) {
        return SourceTypeMeta.partFields(field, of(stream, part));
    }

    /// The attribute the user named, as the streams' parts spell it: the first part carrying it (of at most
    /// [#MOST_PARTS], a stream's parts in order).
    ///
    /// @param given   The source type field as the user gave it (`meta:<Attribute>`).
    /// @param streams The streams to be surveyed.
    /// @param most    The most parts read of a stream.
    /// @return The source type field, its attribute spelt as the parts spell it.
    /// @throws ToolRefusal if no part read carries it, saying which attributes they do carry.
    String field(final String given, final List<Long> streams, final int most) {
        final String attribute = SourceTypeMeta.attribute(given);
        final List<Map<String, String>> seen = new ArrayList<>();
        for (final long stream : streams) {
            final int parts = partCount(stream);
            for (int part = 0; part < Math.min(parts, most) && seen.size() < MOST_PARTS; part++) {
                final Map<String, String> attributes = of(stream, part);
                final Optional<String> spelt = SourceTypeMeta.carried(attributes.keySet(), attribute);
                if (spelt.isPresent()) {
                    return SourceTypeMeta.field(spelt.get());
                }
                seen.add(attributes);
            }
        }
        final List<String> carried = SourceTypeMeta.commonest(seen);
        throw new ToolRefusal(SurveyFeedTool.SOURCE_TYPE_FIELD_ARG + " '" + given + "' names the stream meta "
                              + "attribute '" + attribute + "', which none of the " + seen.size() + " part(s) read of "
                              + streams.size() + " stream(s) carries" + (carried.isEmpty()
                ? " (they carry no meta attributes)"
                : "; they carry " + String.join(", ", carried)) + ". Ask the user which attribute they meant (or "
                              + "for a stream whose parts carry it, as stream_ids).");
    }

    // How many parts a stream has; one when that can't be told.
    private int partCount(final long stream) {
        try {
            return Math.max(1, streamReader.partCount(stream));
        } catch (final RuntimeException e) {
            return 1;
        }
    }
}
