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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `profile_sample` - profiles raw data: its format, record structure and fields, which parser and template to use,
/// and whether a text converter is needed (the Stroom MCP server's `tools/feeds.py` `profile_sample`). It only
/// reads: the samples given as text, or sample streams read as the user.
class ProfileSampleTool extends AbstractBuiltInTool {

    static final String NAME = "profile_sample";
    static final String SAMPLE_ARG = "sample";
    static final String SAMPLES_ARG = "samples";
    static final String STREAM_IDS_ARG = "stream_ids";

    private final SampleProfiling sampleProfiler;
    private final SampleStreamTexts sampleStreamTexts;

    @Inject
    ProfileSampleTool(final SampleProfiling sampleProfiler, final SampleStreamTexts sampleStreamTexts) {
        this.sampleProfiler = sampleProfiler;
        this.sampleStreamTexts = sampleStreamTexts;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Sample text is profiled as it is given; sample streams (stream_ids) are read as the user, "
                      + "who needs View Data and View on their feeds.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Profile raw data (nothing is saved in Stroom): its format (XML document or "
                             + "fragments, JSON array or lines, delimited with or without a header, RFC 3164/5424 "
                             + "syslog, key=value), record structure, and each field's fill rate, inferred type and "
                             + "examples. Timestamps get a stroom:format-date pattern inferred from their values; "
                             + "string fields holding JSON are flagged. Says which parser and template to use, "
                             + "whether a text converter is needed, and for JSON the parser setting. With several "
                             + "files, also what differs between them. Onboarding a source, don't call this first: "
                             + "start_onboarding profiles the samples itself, and each call sends the text again.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(SAMPLE_ARG, "A representative sample of the raw data, to tell the format "
                                                       + "and fields from: " + SampleTexts.SAMPLE_GUIDE
                                                       + ". Never trimmed further, completed or repaired. The "
                                                       + "files themselves go to Stroom whole with upload_sample "
                                                       + "files=[their paths], never as this text.")
                        .addProperty(SAMPLES_ARG, ArraySchema.builder()
                                .items(StringSchema.builder().build())
                                .description("Several sample files of the same source: their texts, by file name "
                                             + "or as a list. Profiled each and together: fields and timestamp "
                                             + "shapes only some files have are reported, as a mapping built from "
                                             + "one file breaks on the others. Prefer this whenever the user has "
                                             + "more than one file.")
                                .build())
                        .addProperty(STREAM_IDS_ARG, ArraySchema.builder()
                                .items(new IntegerSchema(ToolDescription.of("A stream id.")))
                                .description("Sample streams to read the data from, in place of its text (e.g. "
                                             + "after upload_sample), so the text need not be sent again; the "
                                             + "server reads them itself.")
                                .build())
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(profile(call));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> profile(final ToolCall call) {
        final String sample = rawArg(call, SAMPLE_ARG);
        final Object samples = argsMap(call).get(SAMPLES_ARG);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        List<String> notes = List.of();
        final Map<String, String> named;
        if (!streamIds.isEmpty() && sample == null && samples == null) {
            final SampleStreamTexts.Texts read = sampleStreamTexts.read(streamIds);
            named = read.texts();
            notes = read.notes();
        } else {
            named = SampleTexts.asNamedSamples(samples, sample);
        }
        if (named.isEmpty()) {
            throw new ToolRefusal("Give sample (the file's text), samples (several files' texts), or stream_ids");
        }
        // XML fragments with the environment's own wrapper.
        final Map<String, Object> result = new LinkedHashMap<>(sampleProfiler.profileAll(named));
        final Optional<String> tooLong = streamIds.isEmpty()
                ? Optional.ofNullable(SampleTexts.longSamplesNote(named))
                : Optional.empty();
        tooLong.ifPresent(note -> result.put("sample_note", note));
        if (!notes.isEmpty()) {
            result.put("read", notes);
        }
        return result;
    }
}
