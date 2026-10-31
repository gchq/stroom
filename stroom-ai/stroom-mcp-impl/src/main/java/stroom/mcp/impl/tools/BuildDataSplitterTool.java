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
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.draft.SplitterXml;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.StroomSplitterRun;
import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.pipeline.xml.converter.ds3.RootFactory;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

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
    static final String BUFFER_SIZE_ARG = "buffer_size";

    private static final String WHERE = "build_data_splitter save_as";
    // The most differences between the dry run and Stroom's parser shown.
    private static final int MAX_DIFFERENCES = 5;

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
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
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
                             + "use come back (give the same spec to build_translation_xslt as splitter). The "
                             + "converter is also run by Stroom's own parser on the sample (stroom_check): its errors "
                             + "and any record it writes differently are what processing would do. JSON and XML need "
                             + "no converter. Saves nothing unless save_as is given.")
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
                                + "'key_value'}}; {'kind': 'fields', 'fields': {'user': 'user=(\\S+)', 'src': "
                                + "'src=(\\S+)'}} (each field found anywhere, in any order). Options: escape: '\\\\' "
                                + "(delimited: a character escaping the delimiter or quote); spaced_values: true "
                                + "(key_value: values hold spaces unquoted, read from the end; inferred when needed); "
                                + "blank_line_records: true (a record is a block of lines ended by a blank line); "
                                + "catch_all: true (a record the spec doesn't match is kept whole as 'unparsed' "
                                + "instead of an error and lost: agree it with the user)."))
                        .addStringProperty(SAVE_AS_ARG, "Also save the converter in the build under this document "
                                                        + "name (the build this session is on, or `build`), when "
                                                        + "every line parsed.")
                        .addStringProperty(BUILD_ARG, "With save_as: the build; defaults to the one this session is "
                                                      + "on.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                DraftTranslationMappingTool.SAMPLE_STREAMS))
                        .addIntegerProperty(BUFFER_SIZE_ARG, "The Data Splitter's read buffer (bufferSize), in "
                                                             + "characters: only a record up to HALF of it is sure "
                                                             + "to fit (Stroom refills the buffer once half is "
                                                             + "read); a longer one matches nothing and is lost. "
                                                             + "Stroom's default is "
                                                             + RootFactory.DEFAULT_BUFFER_SIZE + "; set from the "
                                                             + "sample's longest record when that needs more. Give it "
                                                             + "when the feed holds longer records than the sample "
                                                             + "(e.g. records lost in processing, or a line the user "
                                                             + "says is longer): about four times the longest, at "
                                                             + "most " + RootFactory.MAX_BUFFER_SIZE + ".")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(build(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PatternTimeoutException e) {
            // A pattern of the agent's took too long on the sample: saying so is the answer, not a fault.
            return JsonResults.refused(e.getMessage());
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
            SampleTexts.checkSample(sample, SAMPLE_ARG);
        }
        final Map<String, Object> spec = ToolArgs.map(call, SPEC_ARG);
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
        final OptionalInt bufferSize = bufferSize(call, chosen, sample, others);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("spec", chosen.toMap());
        result.put("inferred", spec == null);
        final String converter = SplitterXml.converter(chosen);
        result.put("converter", bufferSize.isPresent()
                ? SplitterXml.withBufferSize(converter, bufferSize.getAsInt())
                : converter);
        bufferSize.ifPresent(size -> result.put("buffer_size", size));
        result.put("converter_type", "DATA_SPLITTER");
        if (profiled != null) {
            result.put("format", profiled.get("format"));
        }
        final List<String> texts = new ArrayList<>();
        if (sample != null) {
            texts.add(sample);
            texts.addAll(others);
        }
        Map<String, Object> stroomCheck = null;
        final Optional<String> carriageReturns = carriageReturnsOnly(texts);
        if (!texts.isEmpty()) {
            final List<SplitterDryRun.DryRun> runs = dryRun(result, chosen, texts);
            stroomCheck = stroomCheck((String) result.get("converter"), texts, runs);
            result.put("stroom_check", stroomCheck);
            if (!Boolean.TRUE.equals(stroomCheck.get("matches"))) {
                result.put("hint", "Stroom's own parser doesn't agree with the dry run (stroom_check): "
                                   + stroomCheck.get("summary") + " Processing would do what Stroom's parser does: fix "
                                   + "the spec until stroom_check matches. " + result.get("hint"));
            }
        }
        carriageReturns.ifPresent(note -> result.put("line_endings", note));
        if (saveAs != null) {
            if (result.get("unmatched_count") instanceof final Integer unmatched && unmatched > 0) {
                result.put("not_saved", unmatched + " line(s) match nothing: fix the spec (or confirm they are "
                                        + "noise) before saving");
            } else if (carriageReturns.isPresent()) {
                result.put("not_saved", carriageReturns.get());
            } else if (stroomCheck != null && Boolean.TRUE.equals(stroomCheck.get("loses_records"))) {
                result.put("not_saved", "Stroom's own parser " + stroomCheck.get("summary") + " Fix the spec first "
                                        + "(stroom_check).");
            } else {
                result.put("saved", translationDocs.createTextConverter(context, plan.resolveBuild(context,
                        stringArg(call, BUILD_ARG), WHERE), saveAs, "DATA_SPLITTER", (String) result.get("converter")));
            }
        }
        return result;
    }

    // The Data Splitter's read buffer: the one given, else enough for the sample's longest record (its longest line,
    // or a record over several lines whole), else Stroom's default (empty). Seen in testing: CSV lines of about
    // 20,000 characters lost in processing, and the agent, told the DSParser element had no buffer property,
    // couldn't go on.
    private OptionalInt bufferSize(final ToolCall call,
                                   final SplitterSpec spec,
                                   final String sample,
                                   final List<String> others) {
        final Integer given = intArg(call, BUFFER_SIZE_ARG);
        if (given != null) {
            if (given < RootFactory.MIN_BUFFER_SIZE || given > RootFactory.MAX_BUFFER_SIZE) {
                throw new ToolRefusal(BUFFER_SIZE_ARG + " is from " + RootFactory.MIN_BUFFER_SIZE + " (Stroom's "
                                      + "default) to " + RootFactory.MAX_BUFFER_SIZE + " characters, not " + given);
            }
            return OptionalInt.of(given);
        }
        final List<String> texts = new ArrayList<>(others);
        if (sample != null) {
            texts.add(sample);
        }
        int longest = 0;
        for (final String text : texts) {
            longest = Math.max(longest, Math.max(SplitterXml.longestLine(text),
                    SplitterDryRun.longestRecord(spec, text)));
        }
        return SplitterXml.bufferSizeFor(longest);
    }

    // The spec run on the sample: every sample stream must parse, not just the first.
    private static List<SplitterDryRun.DryRun> dryRun(final Map<String, Object> result,
                                                      final SplitterSpec chosen,
                                                      final List<String> texts) {
        final List<Map<String, String>> records = new ArrayList<>();
        final List<String> unmatched = new ArrayList<>();
        final List<SplitterDryRun.DryRun> runs = new ArrayList<>();
        for (final String text : texts) {
            final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(chosen, text);
            runs.add(run);
            records.addAll(run.records());
            unmatched.addAll(run.unmatchedLines());
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
        return runs;
    }

    // The converter run by Stroom's own parser on each sample text, beside the dry run: what it reports, and the
    // records it writes differently. Seen in testing: the dry run parsed lines Stroom lost (over its read buffer,
    // a key=value value holding a space), and kept white space Stroom trims.
    private static Map<String, Object> stroomCheck(final String converter,
                                                   final List<String> texts,
                                                   final List<SplitterDryRun.DryRun> runs) {
        final List<String> errors = new ArrayList<>();
        final List<Map<String, Object>> differences = new ArrayList<>();
        final List<String> counts = new ArrayList<>();
        int records = 0;
        for (int i = 0; i < texts.size(); i++) {
            final String which = texts.size() == 1
                    ? ""
                    : "sample " + (i + 1) + ": ";
            final StroomSplitterRun.Result run = StroomSplitterRun.run(converter, texts.get(i));
            records += run.records().size();
            run.errors().forEach(error -> errors.add(which + error));
            final List<Map<String, String>> dry = runs.get(i).records();
            // A dry run of a long sample reads records spread over it: only a whole one is compared record by
            // record.
            if (dry.size() >= SplitterDryRun.MAX_RECORDS) {
                continue;
            }
            if (run.records().size() != dry.size()) {
                counts.add(which + "Stroom wrote " + run.records().size() + " record(s) where the dry run found "
                           + dry.size());
                continue;
            }
            for (int n = 0; n < dry.size() && differences.size() < MAX_DIFFERENCES; n++) {
                final Map<String, Object> difference = difference(dry.get(n), run.records().get(n));
                if (!difference.isEmpty()) {
                    difference.put("record", which + (n + 1));
                    differences.add(difference);
                }
            }
        }
        final boolean losesRecords = !errors.isEmpty() || !counts.isEmpty();
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("matches", !losesRecords && differences.isEmpty());
        check.put("records", records);
        if (!errors.isEmpty()) {
            check.put("errors", errors.subList(0, Math.min(10, errors.size())));
            check.put("error_hints", errorHints(errors));
        }
        if (!counts.isEmpty()) {
            check.put("record_counts", counts);
        }
        if (!differences.isEmpty()) {
            check.put("differences", differences);
        }
        check.put("loses_records", losesRecords);
        check.put("summary", losesRecords
                ? (errors.isEmpty()
                        ? String.join("; ", counts) + "."
                        : "reports " + errors.size() + " error(s), e.g. " + errors.getFirst() + ".")
                : differences.isEmpty()
                        // Only of the text read: seen in testing, "parses exactly as Stroom's own parser does" was
                        // taken for the whole feed, where longer records and quoted line breaks further in were lost.
                        ? "writes the same " + records + " record(s), of the " + StreamReader.thousands(
                                texts.stream().mapToLong(String::length).sum()) + " characters of sample read. "
                          + "Records further in, longer or laid out differently, are not checked: processing the "
                          + "whole feed is the check of those."
                        : "writes " + differences.size() + " record(s) differently, e.g. record "
                          + differences.getFirst().get("record") + ".");
        return check;
    }

    // What the errors mean, as stepping's triage says it (the bundled error rules), each said once.
    private static List<String> errorHints(final List<String> errors) {
        final ErrorTriage triage = ErrorTriage.bundled();
        final Set<String> hints = new LinkedHashSet<>();
        for (final String error : errors) {
            final ErrorTriage.Classified classified = triage.classify("ERROR", "dsParser", true, error);
            if (ErrorTriage.BLOCKING.equals(classified.classification())) {
                hints.add(classified.reason());
            }
        }
        return List.copyOf(hints);
    }

    // The fields two readings of a record give differently: each side's value of those fields.
    private static Map<String, Object> difference(final Map<String, String> dry, final Map<String, String> stroom) {
        final Set<String> names = new TreeSet<>(dry.keySet());
        names.addAll(stroom.keySet());
        final Map<String, Object> dryValues = new LinkedHashMap<>();
        final Map<String, Object> stroomValues = new LinkedHashMap<>();
        for (final String name : names) {
            if (!Objects.equals(dry.get(name), stroom.get(name))) {
                dryValues.put(name, dry.get(name));
                stroomValues.put(name, stroom.get(name));
            }
        }
        final Map<String, Object> difference = new LinkedHashMap<>();
        if (!dryValues.isEmpty()) {
            difference.put("dry_run", dryValues);
            difference.put("stroom", stroomValues);
        }
        return difference;
    }

    // Lines ended by a carriage return alone: Stroom's Data Splitter takes carriage returns out, so such a
    // stream is one line to it, and writes no records, with no error (seen in Stroom).
    private static Optional<String> carriageReturnsOnly(final List<String> texts) {
        return texts.stream()
                .filter(text -> text.indexOf('\r') >= 0 && text.indexOf('\n') < 0)
                .findFirst()
                .map(text -> "The sample's lines end with a carriage return alone (no new line). Stroom's Data "
                             + "Splitter takes carriage returns out, so it would read each stream as one line and "
                             + "write no records, with no error. Ask the user to have the source end lines with a "
                             + "new line, or put a FindReplaceFilter before the parser in the pipeline (find '\\r', "
                             + "regex true, replacement a new line); not saved until then.");
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
                              + "name per group, e.g. " + PyText.repr(SplitterXml.EXAMPLES.get("unknown text"))
                              + ", written for lines like " + PyText.repr(example));
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
                                  + PyText.repr(example) + "; or leave spec out and it is inferred from the "
                                  + "sample.");
        }
    }

    private static SplitterDryRun.Inferred infer(final String sample) {
        return SplitterDryRun.inferSpec(sample);
    }
}
