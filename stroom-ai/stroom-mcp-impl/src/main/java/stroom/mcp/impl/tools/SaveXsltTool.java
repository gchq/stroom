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
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// `save_xslt` - saves an XSLT written by hand, or an indexing XSLT from its plan: creates it in the build, or
/// replaces the code of one the tools created (the Stroom MCP server's `tools/translation.py` `save_xslt`,
/// `_plan_to_save` and `_generated`, on [TranslationDocs]). A translation generated from a mapping is saved by
/// build_translation_xslt.
class SaveXsltTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SaveXsltTool.class);

    static final String NAME = "save_xslt";
    static final String BUILD_ARG = "build";
    static final String NAME_ARG = "name";
    static final String CODE_ARG = "code";
    static final String INDEX_PLAN_ARG = "index_plan";
    static final String UUID_ARG = "uuid";
    static final String VERSION_ARG = "version";
    static final String CHANGE_ARG = "change";
    static final String DISCARD_HAND_EDIT_ARG = "discard_hand_edit";
    static final String HAND_EDIT_CHOICES_ARG = "hand_edit_choices";

    /// What `index_plan` is.
    static final String INDEX_PLAN = "For an indexing XSLT: the field plan draft_index_mapping drafted it from, "
                                     + "kept with the XSLT for the documentation. Omitted with no code: the plan "
                                     + "drafted (or amended) in this session, for the index the XSLT's kept plan "
                                     + "names, or the only one drafted.";

    private final TranslationDocs translationDocs;
    private final RequiredImports requiredImports;
    private final IndexingMemory indexingMemory;
    private final EventVersions eventVersions;
    private final WriteGuard guard;

    @Inject
    SaveXsltTool(final TranslationDocs translationDocs,
                 final RequiredImports requiredImports,
                 final IndexingMemory indexingMemory,
                 final EventVersions eventVersions,
                 final WriteGuard guard) {
        this.translationDocs = translationDocs;
        this.requiredImports = requiredImports;
        this.indexingMemory = indexingMemory;
        this.eventVersions = eventVersions;
        this.guard = guard;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It creates or changes an XSLT in the build.
        return false;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The XSLT is created in the build folder, or changed, as the user, who needs Create there and "
                      + "Edit on it; the XSLTs it imports are read as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Save an XSLT written by hand, or an indexing XSLT from its plan (index_plan, no "
                             + "code): create it in the build, or with uuid replace the code of one this server "
                             + "created (including working copies of production XSLTs; prove a hand edit with "
                             + "step_sample and draft_code first). It is checked with check_xslt and not saved if "
                             + "that fails. A translation generated from a mapping is saved by "
                             + "build_translation_xslt (build and name, or uuid), which keeps the mapping with it "
                             + "for the documentation. An edit the user made by hand since is kept: code that "
                             + "undoes it is refused, saying what it changed, to carry into the plan; where the "
                             + "plan changes a field the edit changed too, the user is asked whether to keep their "
                             + "edit or use the proposed field.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, TranslationArgs.BUILD)
                        .addStringProperty(NAME_ARG, TranslationArgs.NAME)
                        .addStringProperty(CODE_ARG, "The complete XSLT, for one written by hand. Omit it to save "
                                                     + "the indexing XSLT generated from the plan (index_plan, or "
                                                     + "the one kept for the session), without it passing through "
                                                     + "you.")
                        .addProperty(INDEX_PLAN_ARG, TranslationArgs.object(INDEX_PLAN))
                        .addStringProperty(UUID_ARG, TranslationArgs.UUID)
                        .addStringProperty(VERSION_ARG, TranslationArgs.VERSION)
                        .addStringProperty(CHANGE_ARG, TranslationArgs.CHANGE)
                        .addBooleanProperty(DISCARD_HAND_EDIT_ARG, TranslationArgs.DISCARD_HAND_EDIT)
                        .addProperty(HAND_EDIT_CHOICES_ARG, TranslationArgs.object(TranslationArgs.HAND_EDIT_CHOICES))
                        .required(BUILD_ARG, NAME_ARG)
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
        final String build = stringArg(call, BUILD_ARG);
        final String uuid = stringArg(call, UUID_ARG);
        String code = NullSafe.isBlankString(rawArg(call, CODE_ARG))
                ? null
                : rawArg(call, CODE_ARG);
        FieldPlan indexPlan = TranslationArgs.indexPlan(call);
        if (code == null && indexPlan == null) {
            indexPlan = planToSave(context, uuid);
        }
        final String said = NullSafe.nonBlankStringElse(stringArg(call, CHANGE_ARG), (uuid != null
                ? "Saved again"
                : "Created") + (indexPlan != null && code == null
                ? " from its index plan"
                : code != null
                        ? " by hand"
                        : ""));
        if (code == null && indexPlan != null) {
            indexPlan = withCardinality(context, withRequiredImports(context, indexPlan, build, uuid), build);
        }
        if (code == null) {
            code = generated(indexPlan);
        }
        final TranslationDocs.Source source = TranslationDocs.Source.of(indexPlan);
        if (uuid != null) {
            return translationDocs.updateXslt(call, context, uuid, code, stringArg(call, VERSION_ARG), source, said,
                    ToolArgs.flag(call, DISCARD_HAND_EDIT_ARG),
                    TranslationArgs.handEditChoices(call));
        }
        final String name = stringArg(call, NAME_ARG);
        if (build == null || name == null) {
            throw new ToolRefusal("Give build and name for a new XSLT (or uuid to change one this server created).");
        }
        return translationDocs.createXslt(context, build, name, code, source, said);
    }

    /// The indexing XSLT an index plan generates, for a save given no code.
    ///
    /// @param indexPlan The plan, or null.
    /// @return Its XSLT.
    /// @throws ToolRefusal if there is no plan.
    static String generated(final FieldPlan indexPlan) {
        if (indexPlan == null) {
            throw new ToolRefusal("Give code (an XSLT written by hand), or index_plan to save the indexing XSLT "
                                  + "generated from it (none is kept for this session to take: draft_index_mapping "
                                  + "drafted several, or none). A translation from a mapping is saved by "
                                  + "build_translation_xslt (build and name, or uuid).");
        }
        return indexPlan.xslt();
    }

    // With no code and no index_plan: the plan draft_index_mapping drafted or amended in this session, for the
    // index the XSLT's kept plan names (else that kept plan, regenerated), or with no XSLT yet, the only one drafted.
    private FieldPlan planToSave(final ToolCallContext context, final String uuid) {
        final Map<String, FieldPlan> drafted = indexingMemory.drafted(context.session());
        if (uuid == null) {
            return IndexingHelpers.planGiven(null, null, drafted, List.of());
        }
        final XsltDoc xslt = translationDocs.readXslt(uuid);
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(xslt.getDescription());
        if (kept.isEmpty() || !"index".equals(kept.get().kind())) {
            return null;
        }
        final FieldPlan named = drafted.get(NullSafe.get(kept.get().payload().get("index_name"), String::valueOf));
        if (named != null) {
            return named;
        }
        try {
            return FieldPlan.fromJson(kept.get().payload());
        } catch (final MappingValidationException e) {
            throw new ToolRefusal("The index plan kept with XSLT '" + xslt.getName() + "' no longer reads: "
                                  + e.getMessage());
        }
    }

    // The imports standing instructions require of indexing XSLTs, added as the generator writes it.
    private FieldPlan withRequiredImports(final ToolCallContext context,
                                          final FieldPlan indexPlan,
                                          final String build,
                                          final String uuid) {
        if (build == null && uuid == null) {
            return indexPlan;
        }
        final String where = build != null
                ? build
                : guard.buildOf(new DocRef(XsltDoc.TYPE, uuid)).orElse(null);
        final Set<String> have = new LinkedHashSet<>(NullSafe.list(indexPlan.imports()));
        NullSafe.list(indexPlan.shared()).stream().map(SharedTemplate::href).forEach(have::add);
        final List<String> required = requiredImports.requiredFor(context.session(), InstructionRules.INDEXING,
                        where, List.of()).stream()
                .map(RequiredImport::href)
                .filter(href -> !have.contains(href))
                .toList();
        if (required.isEmpty()) {
            return indexPlan;
        }
        final List<String> imports = new ArrayList<>(NullSafe.list(indexPlan.imports()));
        imports.addAll(required);
        return withImports(indexPlan, imports);
    }

    // Fields that can hold several values in an event, by the schema: a plan kept before they were marked too.
    private FieldPlan withCardinality(final ToolCallContext context, final FieldPlan indexPlan, final String build) {
        if (indexPlan.discovery() != null) {
            return indexPlan;
        }
        try {
            final EventSchema schema = eventVersions.eventSchema(eventVersions.chosenOrLatest(context, build));
            return IndexingHelpers.withCardinality(indexPlan, schema);
        } catch (final RuntimeException e) {
            // A Stroom without the schema: nothing marked.
            LOGGER.info(() -> "Fields not checked for several values: " + e.getMessage());
            return indexPlan;
        }
    }

    private static FieldPlan withImports(final FieldPlan plan, final List<String> imports) {
        return new FieldPlan(plan.backend(), plan.indexName(), plan.timeField(), plan.fields(), plan.dropWhen(),
                plan.discovery(), List.copyOf(imports), plan.shared(), plan.subobjects(), plan.convention(),
                plan.customNamespaces(), plan.style());
    }
}
