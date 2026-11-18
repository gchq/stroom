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
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.sample.DataSplitterSchema;
import stroom.ai.impl.tools.sample.StroomSplitterRun;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `save_text_converter` - saves a text converter: creates it in the build, or replaces the code of one the tools
/// created (on [TranslationDocs]).
class SaveTextConverterTool extends AbstractBuiltInTool {

    static final String NAME = "save_text_converter";
    static final String BUILD_ARG = "build";
    static final String NAME_ARG = "name";
    static final String CONVERTER_TYPE_ARG = "converter_type";
    static final String CODE_ARG = "code";
    static final String UUID_ARG = "uuid";
    static final String VERSION_ARG = "version";

    /// What `converter_type` is.
    static final String CONVERTER_TYPE = "DATA_SPLITTER for text (CSV, syslog, key=value); XML_FRAGMENT: the "
                                         + "wrapper for XML fragments (several root elements), read by an "
                                         + "XMLFragmentParser. JSON and single-document XML sources take no text "
                                         + "converter: their template's parser reads them.";

    /// The most of a build's sample streams a Data Splitter is run on before it is saved.
    static final int SAMPLE_STREAMS = 3;

    private final TranslationDocs translationDocs;
    private final BuildPlan plan;
    private final SampleStreamTexts sampleStreamTexts;
    private final DataSplitterSchema dataSplitterSchema;

    @Inject
    SaveTextConverterTool(final TranslationDocs translationDocs,
                          final BuildPlan plan,
                          final SampleStreamTexts sampleStreamTexts,
                          final DataSplitterSchema dataSplitterSchema) {
        this.translationDocs = translationDocs;
        this.plan = plan;
        this.sampleStreamTexts = sampleStreamTexts;
        this.dataSplitterSchema = dataSplitterSchema;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It creates or changes a text converter in the build.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The text converter is created in the build folder, or changed, as the user, who needs "
                      + "Create there and Edit on it.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Save a text converter: create it in the build (see stroom://guide/data-splitter), or "
                             + "with uuid replace the code of one the agent created. For templates whose parser "
                             + "needs one (DSParser.textConverter, xmlFragmentParser.textConverter); not for JSON, "
                             + "which the JSONParser parses with no converter. For a Data Splitter prefer "
                             + "build_data_splitter with a spec and save_as: it writes the converter and runs it "
                             + "through Stroom's parser on the sample. Write one by hand only for what no spec "
                             + "expresses: it gets the same checks (Stroom's data-splitter:3 schema, and Stroom's "
                             + "parser on the build's sample streams), and isn't saved while they fail.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, TranslationArgs.BUILD)
                        .addStringProperty(NAME_ARG, TranslationArgs.NAME)
                        .addEnumProperty(CONVERTER_TYPE_ARG, List.of("DATA_SPLITTER", "XML_FRAGMENT"),
                                CONVERTER_TYPE)
                        .addStringProperty(CODE_ARG, "The complete converter definition, e.g. a <dataSplitter> "
                                                     + "document.")
                        .addStringProperty(UUID_ARG, TranslationArgs.UUID)
                        .addStringProperty(VERSION_ARG, TranslationArgs.VERSION)
                        .required(CODE_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(save(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> save(final ToolCall call, final ToolCallContext context) {
        final String code = rawArg(call, CODE_ARG);
        if (NullSafe.isBlankString(code)) {
            throw new ToolRefusal("Give code: the complete converter definition.");
        }
        notJson(code);
        final String uuid = stringArg(call, UUID_ARG);
        if (uuid != null) {
            final String converterType = translationDocs.converterTypeOf(uuid);
            final Optional<String> sampleRun = sampleRun(converterType, code, uuid,
                    plan.buildOf(new DocRef(TextConverterDoc.TYPE, uuid)).orElse(null));
            return withSampleRun(translationDocs.updateTextConverter(context, uuid, code,
                    stringArg(call, VERSION_ARG)), sampleRun);
        }
        final String build = stringArg(call, BUILD_ARG);
        final String name = stringArg(call, NAME_ARG);
        if (build == null || name == null) {
            throw new ToolRefusal("Give build and name for a new text converter (or uuid to change one the agent "
                                  + "created).");
        }
        final String converterType = NullSafe.nonBlankStringElse(stringArg(call, CONVERTER_TYPE_ARG),
                "DATA_SPLITTER");
        final Optional<String> sampleRun = sampleRun(converterType, code, null, build);
        return withSampleRun(translationDocs.createTextConverter(context, build, name, converterType, code),
                sampleRun);
    }

    // A converter is XML: JSON (a spec, or the sample itself) is refused saying so. Seen in the chat 163 review: a
    // spec given as code came back as "Content is not allowed in prolog".
    private static void notJson(final String code) {
        final String start = code.stripLeading();
        if (start.startsWith("{") || start.startsWith("[")) {
            throw new ToolRefusal("code is JSON, not a converter: a text converter is XML (a <dataSplitter> or "
                                  + "<xmlFragment> document). For a Data Splitter from a spec, call "
                                  + BuildDataSplitterTool.NAME + " with the spec and save_as: it writes the "
                                  + "converter's XML, checks it on the sample and saves it.");
        }
    }

    // A Data Splitter is checked as the DSParser checks it, then run by Stroom's parser on the build's newest sample
    // streams: not saved if Stroom reports errors or writes no records. Seen in testing: a converter written by
    // hand, its fields named with elements Stroom ignores, saved, and its records had no fields. Empty for another
    // converter type; else what the run found, or why there was none.
    private Optional<String> sampleRun(final String converterType,
                                       final String code,
                                       final String uuid,
                                       final String build) {
        if (!TextConverterType.DATA_SPLITTER.name().equals(converterType)) {
            return Optional.empty();
        }
        // The code's own faults first: they say more than a failed run.
        translationDocs.checkTextConverter(converterType, code, uuid);
        final List<Long> streamIds = build == null
                ? List.of()
                : plan.rawSampleStreams(build, SAMPLE_STREAMS);
        if (streamIds.isEmpty()) {
            return Optional.of("Not run on a sample: the build has no raw sample stream of its own. Step the "
                               + "pipeline on a raw stream before processing.");
        }
        final Map<String, String> named;
        try {
            named = sampleStreamTexts.read(streamIds).texts();
        } catch (final ToolRefusal e) {
            return Optional.of("Not run on the sample streams " + streamIds + ": " + e.getMessage());
        }
        // A text per stream, or per part of a stream of several: each error names its own.
        final List<String> names = List.copyOf(named.keySet());
        final List<StroomSplitterRun.Result> runs = StroomSplitterRun.run(dataSplitterSchema, code,
                List.copyOf(named.values()));
        final List<String> errors = new ArrayList<>();
        int records = 0;
        for (int i = 0; i < runs.size(); i++) {
            final String name = names.get(i);
            records += runs.get(i).records().size();
            runs.get(i).errors().forEach(error -> errors.add(name + ": " + error));
        }
        if (!errors.isEmpty()) {
            throw new ToolRefusal("Not saved: Stroom's parser reports " + errors.size() + " error(s) running the "
                                  + "converter on the build's sample streams " + streamIds + ", e.g. "
                                  + String.join("; ", errors.subList(0, Math.min(5, errors.size())))
                                  + ". Processing would report the same. Prefer build_data_splitter with a spec and "
                                  + "save_as, whose dry run shows the records and the lines that match nothing.");
        }
        if (records == 0) {
            throw new ToolRefusal("Not saved: Stroom's parser wrote no records from the build's sample streams "
                                  + streamIds + ". Prefer build_data_splitter with a spec and save_as.");
        }
        return Optional.of("Stroom's parser wrote " + records + " record(s) from the sample streams " + streamIds
                           + " with no errors");
    }

    private static Map<String, Object> withSampleRun(final Map<String, Object> result,
                                                     final Optional<String> sampleRun) {
        if (sampleRun.isEmpty()) {
            return result;
        }
        final Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("sample_run", sampleRun.get());
        return out;
    }
}
