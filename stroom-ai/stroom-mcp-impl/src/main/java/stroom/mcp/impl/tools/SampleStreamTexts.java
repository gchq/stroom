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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamCharsets;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.RawText;
import stroom.ai.impl.tools.StreamReader.StreamReadException;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Sample streams' raw text, read in place of text sent by the client, so a sample passes through the model once
/// (upload_sample) however many tools then read it.
class SampleStreamTexts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SampleStreamTexts.class);

    /// What is said to a user without the View Data permission.
    static final String NO_VIEW_DATA = "You don't have permission to view data, so the sample streams can't be "
                                       + "read.";

    private final StreamReader streamReader;
    private final DocumentAccess documentAccess;

    @Inject
    SampleStreamTexts(final StreamReader streamReader, final DocumentAccess documentAccess) {
        this.streamReader = streamReader;
        this.documentAccess = documentAccess;
    }

    /// @param streamIds Sample streams.
    /// @return Their text by name (`stream <id>`), and notes on what was cut short or read through its bytes.
    /// @throws ToolRefusal if the user may not view data, or a stream can't be read.
    Texts read(final List<Long> streamIds) {
        if (!documentAccess.canViewData()) {
            throw new ToolRefusal(NO_VIEW_DATA);
        }
        final Map<String, String> texts = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();
        for (final long streamId : streamIds) {
            final RawText text;
            try {
                streamReader.meta(streamId);
                text = streamReader.cachedRawText(streamId, StreamReader.MAX_SAMPLE_CHARS);
            } catch (final StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
            texts.put("stream " + streamId, text.text());
            if (text.truncated()) {
                notes.add("stream " + streamId + " is read up to its first "
                          + String.format(Locale.ROOT, "%,d", text.text().length())
                          + " characters (the most a sample is read to)");
            }
            final Map<String, Object> report = streamReader.charsetReport(streamId);
            if (report != null) {
                notes.add(StreamCharsets.note(streamId, report));
            }
        }
        return new Texts(texts, notes);
    }

    /// @param streamId A sample stream.
    /// @return Its receipt headers (what `stroom:meta()` reads, e.g. `RemoteFile`), or none when they can't be read.
    Map<String, String> receiptHeaders(final long streamId) {
        try {
            final Object attributes = streamReader.streamAttributes(streamId).get("attributes");
            final Map<String, String> headers = new LinkedHashMap<>();
            if (attributes instanceof final Map<?, ?> map) {
                map.forEach((key, value) -> headers.put(String.valueOf(key), String.valueOf(value)));
            }
            return headers;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Stream {}'s receipt headers can't be read: {}", streamId,
                    e.getMessage()));
            return Map.of();
        }
    }


    // --------------------------------------------------------------------------------


    /// Sample streams' text.
    ///
    /// @param texts Each stream's text, by name (`stream <id>`).
    /// @param notes What was cut short, or read through its bytes.
    record Texts(Map<String, String> texts, List<String> notes) {

    }
}
