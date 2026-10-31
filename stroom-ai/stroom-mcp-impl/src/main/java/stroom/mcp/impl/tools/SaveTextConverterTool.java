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
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/// `save_text_converter` - saves a text converter: creates it in the build, or replaces the code of one the tools
/// created (the Stroom MCP server's `tools/translation.py` `save_text_converter`, on [TranslationDocs]).
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

    private final TranslationDocs translationDocs;

    @Inject
    SaveTextConverterTool(final TranslationDocs translationDocs) {
        this.translationDocs = translationDocs;
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
                             + "Save a text converter: create it in the build (see stroom://guide/data-splitter; "
                             + "build_data_splitter writes one from a spec), or with uuid replace the code of one "
                             + "the agent created. For templates whose parser needs one (DSParser.textConverter, "
                             + "xmlFragmentParser.textConverter); not for JSON, which the JSONParser parses with no "
                             + "converter.")
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
        final String uuid = stringArg(call, UUID_ARG);
        if (uuid != null) {
            return translationDocs.updateTextConverter(context, uuid, code, stringArg(call, VERSION_ARG));
        }
        final String build = stringArg(call, BUILD_ARG);
        final String name = stringArg(call, NAME_ARG);
        if (build == null || name == null) {
            throw new ToolRefusal("Give build and name for a new text converter (or uuid to change one the agent "
                                  + "created).");
        }
        return translationDocs.createTextConverter(context, build, name, NullSafe.nonBlankStringElse(
                stringArg(call, CONVERTER_TYPE_ARG), "DATA_SPLITTER"), code);
    }
}
