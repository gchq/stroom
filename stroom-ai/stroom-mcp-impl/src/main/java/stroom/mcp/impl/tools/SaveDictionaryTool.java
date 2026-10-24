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

import java.util.Map;

/// `save_dictionary` - saves a Dictionary doc a translation reads at run time with stroom:dictionary(): creates it
/// in the build, or replaces the entries of one the tools created (the Stroom MCP server's `tools/translation.py`
/// `save_dictionary`, on [TranslationDocs]).
class SaveDictionaryTool extends AbstractBuiltInTool {

    static final String NAME = "save_dictionary";
    static final String BUILD_ARG = "build";
    static final String NAME_ARG = "name";
    static final String TEXT_ARG = "text";
    static final String DESCRIPTION_ARG = "description";
    static final String UUID_ARG = "uuid";
    static final String VERSION_ARG = "version";

    private final TranslationDocs translationDocs;

    @Inject
    SaveDictionaryTool(final TranslationDocs translationDocs) {
        this.translationDocs = translationDocs;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It creates or changes a Dictionary doc in the build.
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
                .note("The Dictionary doc is created in the build folder, or changed, as the user, who needs Create "
                      + "there and Edit on it.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Save a Dictionary doc: a small static table a translation reads at run time with "
                             + "stroom:dictionary() (the mapping's `dictionary` for key=value lines, "
                             + "`in_dictionary` for a list). Create it in the build, or with uuid replace the "
                             + "entries of one this server created. Data that changes or is large belongs in "
                             + "reference data instead (find_reference_data).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, TranslationArgs.BUILD)
                        .addStringProperty(NAME_ARG, "Dictionary name, as the mapping's `dictionary` / "
                                                     + "`in_dictionary` name it.")
                        .addStringProperty(TEXT_ARG, "One entry per line: key=value lines for a value map, or "
                                                     + "plain lines for a list. Blank lines and whitespace round "
                                                     + "keys and values are ignored.")
                        .addStringProperty(DESCRIPTION_ARG, "What the entries are and where they came from.")
                        .addStringProperty(UUID_ARG, TranslationArgs.UUID)
                        .addStringProperty(VERSION_ARG, TranslationArgs.VERSION)
                        .required(BUILD_ARG, NAME_ARG, TEXT_ARG)
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
        final String text = NullSafe.string(rawArg(call, TEXT_ARG));
        final String uuid = stringArg(call, UUID_ARG);
        if (uuid != null) {
            return translationDocs.updateDictionary(context, uuid, text, stringArg(call, VERSION_ARG));
        }
        final String build = stringArg(call, BUILD_ARG);
        final String name = stringArg(call, NAME_ARG);
        if (build == null || name == null) {
            throw new ToolRefusal("Give build and name for a new Dictionary (or uuid to change one this server "
                                  + "created).");
        }
        return translationDocs.createDictionary(build, name, text, NullSafe.string(rawArg(call, DESCRIPTION_ARG)));
    }
}
