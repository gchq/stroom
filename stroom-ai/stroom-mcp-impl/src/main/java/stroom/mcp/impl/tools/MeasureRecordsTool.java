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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.PartLengths;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.draft.SplitterXml;
import stroom.ai.impl.tools.sample.RecordLengths;
import stroom.ai.shared.AiToolCategory;
import stroom.pipeline.xml.converter.ds3.RootFactory;
import stroom.security.shared.AppPermission;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/// `measure_records` - the longest record of whole raw streams, read through once on the server, and the Data
/// Splitter `bufferSize` that holds it ([StreamReader#recordLengths]). Asked for by the user: an agent whose
/// converter lost records longer than half its buffer, in a 159 MB stream, had no way to find the longest and tried
/// to work it out from a sample.
class MeasureRecordsTool extends AbstractBuiltInTool {

    static final String NAME = "measure_records";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String QUOTE_ARG = "quote";

    private static final int MAX_STREAMS = 10;

    private final StreamReader streamReader;

    @Inject
    MeasureRecordsTool(final StreamReader streamReader) {
        this.streamReader = streamReader;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .note("The streams are read as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Measure the longest record of whole raw streams (every part, read through on the "
                             + "server, not a sample of their start), and give the Data Splitter bufferSize that "
                             + "holds it: only a record up to half the buffer is sure to fit, and a longer one is "
                             + "lost (the dsParser error 'exhausted the buffer'). A record is a line, or, with "
                             + "quote (delimited text whose quoted values hold new lines), the lines a quoted value "
                             + "spans joined. A record over several lines of another kind (one starting at a marked "
                             + "line, or a block ended by a blank line) isn't measured: its longest line understates "
                             + "it. Then build_data_splitter with buffer_size, and save it over the converter.")
                .parameters(ObjectSchema.builder()
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                "The raw streams to measure (1 to " + MAX_STREAMS + "): those the converter "
                                + "reads, e.g. the sample streams, or the inputs of records it lost."))
                        .addStringProperty(QUOTE_ARG, "Only for delimited text whose quoted values may hold new "
                                                      + "lines (the spec's quoted_newlines): its quote character, "
                                                      + "e.g. \". Without it each line is a record.")
                        .required(STREAM_IDS_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(measure(call));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> measure(final ToolCall call) {
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        if (streamIds.isEmpty() || streamIds.size() > MAX_STREAMS) {
            throw new ToolRefusal("Give 1 to " + MAX_STREAMS + " stream_ids to measure");
        }
        final Character quote = quote(stringArg(call, QUOTE_ARG));
        final List<Map<String, Object>> streams = new ArrayList<>();
        int longest = 0;
        boolean complete = true;
        for (final long streamId : streamIds) {
            final PartLengths lengths;
            try {
                lengths = streamReader.recordLengths(streamId, quote);
            } catch (final StreamReader.StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
            streams.add(stream(streamId, lengths, quote != null));
            longest = Math.max(longest, lengths.longest());
            complete = complete && lengths.complete();
            if (!lengths.complete()) {
                // The call's time ran out: the rest is for another call.
                break;
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("streams", streams);
        result.put("longest", longest);
        result.put("complete", complete);
        final OptionalInt bufferSize = SplitterXml.bufferSizeFor(longest);
        if ((long) longest * 2 > RootFactory.MAX_BUFFER_SIZE) {
            result.put("buffer_size", RootFactory.MAX_BUFFER_SIZE);
            result.put("hint", "The longest record is over half of Stroom's largest buffer ("
                               + RootFactory.MAX_BUFFER_SIZE + " characters), so no Data Splitter buffer is sure "
                               + "to hold it: tell the user, as a Data Splitter can't read this data whole.");
        } else if (bufferSize.isPresent()) {
            result.put("buffer_size", bufferSize.getAsInt());
            result.put("hint", "build_data_splitter with buffer_size=" + bufferSize.getAsInt() + " (four times "
                               + "the longest record, so longer ones not yet seen fit too), then save it over the "
                               + "converter (save_text_converter uuid=), and step and process the streams again.");
        } else {
            result.put("hint", "Stroom's default buffer (" + RootFactory.DEFAULT_BUFFER_SIZE + " characters) "
                               + "holds every record measured: a lost record has another cause.");
        }
        if (!complete) {
            result.put("not_measured", "The call's time ran out before every stream was read through, so a longer "
                                       + "record may be further in: measure the streams not read through again, "
                                       + "one a call.");
        }
        return result;
    }

    // One stream's longest line and record, over its parts, with where each starts.
    private static Map<String, Object> stream(final long streamId,
                                              final PartLengths lengths,
                                              final boolean quoted) {
        final Map<String, Object> stream = new LinkedHashMap<>();
        stream.put("stream_id", streamId);
        stream.put("parts", lengths.parts());
        long lines = 0;
        long chars = 0;
        long overLines = 0;
        Map<String, Object> longestLine = null;
        Map<String, Object> longestRecord = null;
        final List<RecordLengths.Measured> measured = lengths.measured();
        for (int part = 0; part < measured.size(); part++) {
            final RecordLengths.Measured m = measured.get(part);
            lines += m.lines();
            chars += m.chars();
            overLines += m.recordsOverLines();
            longestLine = longer(longestLine, m.longestLine(), part, lengths.parts());
            if (m.longestRecord() != null) {
                longestRecord = longer(longestRecord, m.longestRecord(), part, lengths.parts());
            }
        }
        stream.put("lines", lines);
        stream.put("chars", chars);
        stream.put("longest_line", longestLine);
        if (quoted) {
            stream.put("longest_record", longestRecord);
            stream.put("records_over_lines", overLines);
        }
        stream.put("complete", lengths.complete());
        return stream;
    }

    // The longer of what was found and this part's, with where it starts (its part only when there are several).
    private static Map<String, Object> longer(final Map<String, Object> found,
                                              final RecordLengths.Span span,
                                              final int part,
                                              final int parts) {
        if (found != null && (int) found.get("chars") >= span.chars()) {
            return found;
        }
        final Map<String, Object> longest = new LinkedHashMap<>();
        longest.put("chars", span.chars());
        if (parts > 1) {
            longest.put("part", part);
        }
        longest.put("line", span.line());
        return longest;
    }

    private static Character quote(final String quote) {
        if (quote == null || quote.isEmpty()) {
            return null;
        }
        if (quote.length() != 1) {
            throw new ToolRefusal(QUOTE_ARG + " is one character, e.g. \", not '" + quote + "'");
        }
        return quote.charAt(0);
    }
}
