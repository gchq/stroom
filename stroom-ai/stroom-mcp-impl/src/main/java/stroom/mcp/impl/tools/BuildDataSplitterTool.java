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
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.draft.SplitterXml;
import stroom.ai.impl.tools.generation.PythonRepr;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SampleRefusal;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// `build_data_splitter` - writes a Data Splitter (text converter) from the sample: its format profiled, the spec
/// inferred unless one is given, and the spec run on the sample, so the records and field names are seen before
/// anything is created (the Stroom MCP server's `tools/generation.py` `build_data_splitter` and `_fragment_wrapper`,
/// on [SplitterDryRun] and [SplitterXml]). For XML fragments, the wrapper the XMLFragmentParser puts round them.
/// Saves nothing unless save_as is given.
class BuildDataSplitterTool extends AbstractBuiltInTool {

    static final String NAME = "build_data_splitter";
    static final String SAMPLE_ARG = "sample";
    static final String SPEC_ARG = "spec";
    static final String SAVE_AS_ARG = "save_as";
    static final String BUILD_ARG = "build";
    static final String STREAM_IDS_ARG = "stream_ids";

    private static final String WHERE = "build_data_splitter save_as";

    private final SampleStreamTexts sampleStreamTexts;
    private final SampleProfiling sampleProfiling;
    private final TranslationDocs translationDocs;
    private final EventVersions eventVersions;
    private final BuildPlan plan;

    @Inject
    BuildDataSplitterTool(final SampleStreamTexts sampleStreamTexts,
                          final SampleProfiling sampleProfiling,
                          final TranslationDocs translationDocs,
                          final EventVersions eventVersions,
                          final BuildPlan plan) {
        this.sampleStreamTexts = sampleStreamTexts;
        this.sampleProfiling = sampleProfiling;
        this.translationDocs = translationDocs;
        this.eventVersions = eventVersions;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // save_as saves the converter.
        return false;
    }

    @Override
    public boolean isReadOnly(final ToolCall call) {
        return stringArg(call, SAVE_AS_ARG) == null;
    }

    @Override
    public boolean hasReadOnlyCalls() {
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Sample streams (stream_ids) are read as the user; with save_as the converter is created in "
                      + "the build folder as the user, who needs Create there.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Write a Data Splitter (text converter) from the sample (its text, or stream_ids to "
                             + "read it from the sample streams; with several, the spec comes from the first and is "
                             + "run on each): its format is profiled (delimited with or without a header line, "
                             + "key=value pairs, syslog RFC 5424 or 3164 with the message parsed further) and the "
                             + "spec inferred, unless one is given; the spec is run on the sample locally and the "
                             + "records it produces, the lines that match nothing and the field names a mapping may "
                             + "use come back (give the same spec to build_translation_xslt as splitter). JSON and "
                             + "XML need no converter. Saves nothing: save_text_converter saves it.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(SAMPLE_ARG, "The raw sample text (every line of a file). The spec is "
                                                       + "inferred from it when none is given, and run on it, so "
                                                       + "the records and field names are seen before anything is "
                                                       + "created.")
                        .addProperty(SPEC_ARG, TranslationArgs.object(
                                "Only when the inferred spec is not right, or for free text: how a record divides "
                                + "into fields. {'kind': 'delimited', 'delimiter': ',', 'header': true} (or header: "
                                + "[names]); {'kind': 'key_value', 'delimiter': ' ', 'pair_separator': '=', "
                                + "'quote': '\"'}; {'kind': 'regex', 'pattern': '^(\\S+) (.*)$', 'names': ['time', "
                                + "'message']}; {'kind': 'syslog', 'rfc': 'rfc3164', 'body': {'kind': "
                                + "'key_value'}}."))
                        .addStringProperty(SAVE_AS_ARG, "Also save the converter in the build under this document "
                                                        + "name (the build this session is on, or `build`), when "
                                                        + "every line parsed.")
                        .addStringProperty(BUILD_ARG, "With save_as: the build; defaults to the one this session is "
                                                      + "on.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                DraftTranslationMappingTool.SAMPLE_STREAMS))
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(build(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> build(final ToolCall call, final ToolCallContext context) {
        String sample = rawArg(call, SAMPLE_ARG);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        final List<String> others = new ArrayList<>();
        if (sample == null && !streamIds.isEmpty()) {
            final List<String> texts = new ArrayList<>(sampleStreamTexts.read(streamIds).texts().values());
            if (!texts.isEmpty()) {
                sample = texts.getFirst();
                others.addAll(texts.subList(1, texts.size()));
            }
        } else if (sample != null) {
            SampleArguments.whyNotData(sample, SAMPLE_ARG).ifPresent(why -> {
                throw new ToolRefusal(why);
            });
        }
        final Map<String, Object> spec = TranslationArgs.object(call, SPEC_ARG);
        if (sample == null && spec == null) {
            throw new ToolRefusal("Give the sample (the file's text, or stream_ids): the spec is inferred from it");
        }
        final String saveAs = stringArg(call, SAVE_AS_ARG);
        Map<String, Object> profiled = null;
        final SplitterSpec chosen;
        if (spec == null) {
            final SplitterDryRun.Inferred inferred = infer(sample);
            if (inferred.spec() == null) {
                return notInferred(context, inferred.profile(), saveAs, stringArg(call, BUILD_ARG));
            }
            chosen = inferred.spec();
            profiled = inferred.profile();
        } else {
            chosen = given(spec, sample);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("spec", chosen.toMap());
        result.put("inferred", spec == null);
        result.put("converter", SplitterXml.generateSplitter(chosen));
        result.put("converter_type", "DATA_SPLITTER");
        if (profiled != null) {
            result.put("format", profiled.get("format"));
        }
        if (sample != null) {
            dryRun(result, chosen, sample, others);
        }
        if (saveAs != null) {
            if (result.get("unmatched_count") instanceof final Integer unmatched && unmatched > 0) {
                result.put("not_saved", unmatched + " line(s) match nothing: fix the spec (or confirm they are "
                                        + "noise) before saving");
            } else {
                result.put("saved", translationDocs.createTextConverter(context, plan.resolveBuild(context,
                        stringArg(call, BUILD_ARG), WHERE), saveAs, "DATA_SPLITTER", (String) result.get("converter")));
            }
        }
        return result;
    }

    // The spec run on the sample: every sample stream must parse, not just the first.
    private static void dryRun(final Map<String, Object> result,
                               final SplitterSpec chosen,
                               final String sample,
                               final List<String> others) {
        final List<Map<String, String>> records = new ArrayList<>();
        final List<String> unmatched = new ArrayList<>();
        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(chosen, sample);
        records.addAll(run.records());
        unmatched.addAll(run.unmatchedLines());
        for (final String text : others) {
            final SplitterDryRun.DryRun more = SplitterDryRun.dryRun(chosen, text);
            records.addAll(more.records());
            unmatched.addAll(more.unmatchedLines());
        }
        result.put("records", records.size());
        result.put("fields", SampleProfiler.inventory(records));
        result.put("first_records", records.subList(0, Math.min(5, records.size())));
        result.put("unmatched_lines", unmatched.subList(0, Math.min(10, unmatched.size())));
        result.put("unmatched_count", unmatched.size());
        if (!unmatched.isEmpty() && records.isEmpty()) {
            result.put("hint", "No line matched the spec: check the pattern or delimiter against the lines shown.");
        } else if (!unmatched.isEmpty()) {
            result.put("hint", unmatched.size() + " line(s) match nothing and would produce no record: widen the "
                               + "spec, or confirm with the user that they are noise.");
        } else {
            result.put("hint", "Every line parsed. Use these field names in the mapping, with this spec as "
                               + "splitter; save_text_converter saves the converter (or call again with "
                               + "save_as=<name>).");
        }
    }

    // A sample no spec is inferred for: XML fragments take their wrapper; JSON and XML need no converter.
    private Map<String, Object> notInferred(final ToolCallContext context,
                                            final Map<String, Object> profiled,
                                            final String saveAs,
                                            final String build) {
        final String format = String.valueOf(profiled.get("format"));
        if (SampleProfiler.XML_FRAGMENTS.equals(format)) {
            // Fragments do take a converter: the wrapper the XMLFragmentParser puts round them. Seen: the plan's
            // converter step sent here, told "no Data Splitter", went on without one.
            return fragmentWrapper(context, profiled, saveAs, build);
        }
        if (List.of("json array", "json lines", "xml").contains(format)) {
            throw new ToolRefusal("The sample is " + format + ": it needs no Data Splitter. "
                                  + profiled.get("suggested_parser") + ". Go on to build_translation_xslt (input "
                                  + (format.startsWith("json")
                    ? "json"
                    : format.replace(' ', '_')) + ").");
        }
        final Object examples = profiled.get("examples");
        final String example = examples instanceof final List<?> list && !list.isEmpty()
                ? String.valueOf(list.getFirst())
                : "";
        throw new ToolRefusal("The sample's format could not be inferred (" + format + "): give spec, a regex with a "
                              + "name per group, e.g. " + PythonRepr.repr(SplitterXml.EXAMPLES.get("unknown text"))
                              + ", written for lines like " + PythonRepr.repr(example));
    }

    // build_data_splitter for XML fragments: the wrapper converter (the environment's own when it has one), saved
    // with save_as, and the namespace the mapping then reads the fragments in.
    @SuppressWarnings("unchecked")
    private Map<String, Object> fragmentWrapper(final ToolCallContext context,
                                                final Map<String, Object> profiled,
                                                final String saveAs,
                                                final String build) {
        final Map<String, Object> setup = sampleProfiling.withFragmentSetup(profiled,
                eventVersions.chosen(context, build).orElse(null));
        final Map<String, Object> converter = (Map<String, Object>) setup.get("text_converter");
        final Map<String, Object> input = (Map<String, Object>) setup.get("xslt_input");
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", SampleProfiler.XML_FRAGMENTS);
        result.put("converter_type", "XML_FRAGMENT");
        result.put("converter", converter.get("code"));
        for (final String key : List.of("environment", "other_wrappers")) {
            if (converter.containsKey(key)) {
                result.put(key, converter.get(key));
            }
        }
        result.put("records", profiled.get("records"));
        result.put("xslt_input", input);
        result.put("hint", "The wrapper the XMLFragmentParser puts round the fragments: " + converter.get("note")
                           + " In the mapping set input 'xml_fragments', record '" + profiled.get("record_element")
                           + "' and xml_namespace '" + input.get("namespace") + "', the namespace the fragments are "
                           + "read in.");
        if (saveAs != null) {
            result.put("saved", translationDocs.createTextConverter(context, plan.resolveBuild(context, build, WHERE),
                    saveAs, "XML_FRAGMENT", String.valueOf(converter.get("code"))));
        }
        return result;
    }

    // The spec given, or what a spec looks like for the sample's format.
    private SplitterSpec given(final Map<String, Object> spec, final String sample) {
        try {
            return SplitterSpec.fromMap(spec);
        } catch (final RuntimeException e) {
            String format = null;
            if (sample != null) {
                try {
                    format = NullSafe.get(sampleProfiling.profile(sample).get("format"), String::valueOf);
                } catch (final ToolRefusal refusal) {
                    format = null;
                }
            }
            final Object example = SplitterXml.EXAMPLES.getOrDefault(NullSafe.string(format),
                    SplitterXml.EXAMPLES.get("delimited"));
            throw new ToolRefusal("spec is not a splitter spec (" + e.getMessage() + "). For "
                                  + NullSafe.nonBlankStringElse(format, "this") + " data it looks like "
                                  + PythonRepr.repr(example) + "; or leave spec out and it is inferred from the "
                                  + "sample.");
        }
    }

    private static SplitterDryRun.Inferred infer(final String sample) {
        try {
            return SplitterDryRun.inferSpec(sample);
        } catch (final SampleRefusal e) {
            throw new ToolRefusal(e.getMessage());
        }
    }
}
