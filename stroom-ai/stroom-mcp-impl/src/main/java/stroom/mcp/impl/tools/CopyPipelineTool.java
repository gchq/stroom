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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.shared.AbstractDoc;
import stroom.mcp.impl.tools.PipelineWrites.Typed;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// `copy_pipeline` - copies an existing source's pipeline into the build with exactly the original's structure and
/// settings, its XSLTs and text converters copied with it. A template is refused: templates are inherited
/// (create_pipeline), never copied.
class CopyPipelineTool extends AbstractBuiltInTool {

    static final String NAME = "copy_pipeline";
    static final String BUILD_ARG = "build";
    static final String SOURCE_UUID_ARG = "source_uuid";
    static final String NEW_NAME_ARG = "new_name";
    static final String RENAME_ARG = "rename";
    static final String SET_PROPERTIES_ARG = "set_properties";
    static final String WORKING_COPY_ARG = "working_copy";

    private final PipelineWrites writes;
    private final PipelineDocs pipelineDocs;
    private final PipelineShapes pipelineShapes;
    private final PipelineTemplates templates;
    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final BuildRecords records;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    CopyPipelineTool(final PipelineWrites writes,
                     final PipelineDocs pipelineDocs,
                     final PipelineShapes pipelineShapes,
                     final PipelineTemplates templates,
                     final DocumentAccess documentAccess,
                     final WriteGuard guard,
                     final BuildRecords records,
                     final Consent consent,
                     final BuildPlan plan) {
        this.writes = writes;
        this.pipelineDocs = pipelineDocs;
        this.pipelineShapes = pipelineShapes;
        this.templates = templates;
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.records = records;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It creates a pipeline and the documents it owns.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the names in a form before anything is copied.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The copies are created in the build folder as the user, who needs View on the pipeline "
                      + "and the XSLTs and text converters it owns, Create in the build folder, and Edit on the "
                      + "copies.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Copy an existing source's pipeline (not a template: those are inherited with "
                             + "create_pipeline) into the build with exactly the original's structure and "
                             + "settings: same parent template, same element changes (removed or re-linked steps, "
                             + "extra XSLT steps), reference loaders and property values. The XSLTs and text "
                             + "converters it owns are copied too and the copy is rewired to them; other documents "
                             + "(shared libraries, indexes, clusters) stay shared. The user confirms the names "
                             + "first.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(SOURCE_UUID_ARG, "Pipeline to copy.")
                        .addStringProperty(NEW_NAME_ARG, "Name of the copy, e.g. 'Acme-Door-V1.3-Events'.")
                        .addProperty(RENAME_ARG, ObjectSchema.builder()
                                .description("Text replacements applied to the names of the copied XSLTs and "
                                             + "text converters, e.g. {'V1.2': 'V1.3'}.")
                                .build())
                        .addProperty(SET_PROPERTIES_ARG, CreatePipelineTool.propertiesSchema(
                                "Properties to change on the copy, as objects, e.g. [{\"element\": "
                                + "\"elasticIndexingFilter\", \"name\": \"indexName\", \"value\": "
                                + "\"ecs-acme-v2\"}] for a new index version."))
                        .addBooleanProperty(WORKING_COPY_ARG, "True when the copy will be written back over the "
                                                              + "original on promotion (an in-place change); "
                                                              + "False for a new version that is promoted "
                                                              + "alongside it.")
                        .required(BUILD_ARG, SOURCE_UUID_ARG, NEW_NAME_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(copy(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused("Stroom can't load the pipeline: " + e.getMessage());
        }
    }

    private Map<String, Object> copy(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String sourceUuid = required(call, SOURCE_UUID_ARG);
        final String newName = required(call, NEW_NAME_ARG);
        final Map<String, String> rename = PipelineArguments.textMap(argsMap(call).get(RENAME_ARG), RENAME_ARG);
        final List<PropertyValue> changes = PropertyValue.listOf(argsMap(call).get(SET_PROPERTIES_ARG),
                SET_PROPERTIES_ARG);
        final boolean workingCopy = ToolArgs.flag(call, WORKING_COPY_ARG);
        final PipelineDoc source = pipelineDocs.read(sourceUuid);
        final String reason = templates.templateReason(sourceUuid);
        if (reason != null) {
            throw new ToolRefusal("'" + source.getName() + "' is a template (" + reason + "): templates are "
                                  + "inherited, not copied. A new source's pipeline is a child of it: "
                                  + "create_pipeline(name=..., template_uuid='" + sourceUuid + "'), which keeps "
                                  + "the template's structure and takes later fixes to it. copy_pipeline is for a "
                                  + "new version or a working copy of a source's own pipeline (e.g. "
                                  + "Acme-Door-V1.2-Events).");
        }
        final List<DocRef> owned = owned(source, changes);
        final Map<String, String> names = new LinkedHashMap<>();
        for (final DocRef entity : owned) {
            String name = entity.getName();
            for (final Map.Entry<String, String> replacement : rename.entrySet()) {
                name = name.replace(replacement.getKey(), replacement.getValue());
            }
            names.put(entity.getUuid(), name);
        }
        final Typed typed = changes.stream().anyMatch(property -> property.docUuid() == null)
                ? writes.typedProperties(writes.layout(sourceUuid).elements(), changes)
                : new Typed(changes, Map.of());
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("build", build);
        details.put("copy of", source.getName());
        details.put("pipeline name", newName);
        details.put("copied documents", List.copyOf(new TreeSet<>(names.values())));
        details.put("working copy", workingCopy);
        details.put("changes", changes.stream().map(PropertyValue::dotted).toList());
        consent.require(call, context, Consent.Request.confirmation(NAME,
                "Copy pipeline '" + source.getName() + "' as '" + newName + "'", details));

        final String role = pipelineShapes.role(sourceUuid);
        final Map<String, DocRef> copies = new LinkedHashMap<>();
        for (final DocRef entity : owned) {
            if (!copies.containsKey(entity.getUuid())) {
                copies.put(entity.getUuid(), copyDocument(entity, names.get(entity.getUuid()), build, workingCopy,
                        role));
            }
        }
        final List<PipelineProperty> rewired = new ArrayList<>();
        for (final PipelineProperty property : PipelineDocs.addedProperties(source)) {
            final DocRef entity = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity);
            final boolean copied = entity != null && copies.containsKey(entity.getUuid());
            rewired.add(copied
                    ? rewire(property, copies.get(entity.getUuid()))
                    : property);
        }
        PipelineDoc data = PipelineDocs.withAddedProperties(source, rewired);
        for (final PropertyValue property : typed.properties()) {
            data = PipelineDocs.withProperty(data, property.element(), property.name(),
                    writes.value(property, typed.keys().get(property.dotted())));
        }
        final PipelineDoc changed = data;
        final DocRef ref = guard.create(PipelineDoc.TYPE, newName, build, workingCopy
                ? sourceUuid
                : null, false, role);
        if (!workingCopy) {
            records.update(build, ref, entry -> entry.put("version_of", sourceUuid));
        }
        final PipelineDoc doc = pipelineDocs.write(pipelineDocs.read(ref.getUuid()).copy()
                .parentPipeline(source.getParentPipeline())
                .description(source.getDescription())
                .pipelineData(changed.getPipelineData())
                .build());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", PipelineDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("copy_of", source.getName());
        result.put("working_copy", workingCopy);
        result.put("copied_documents", copies.values().stream().map(MergedPipeline::docRef).toList());
        return plan.withNext(context, build, result);
    }

    // The XSLTs and text converters the pipeline's own layer names, but not those set_properties replaces: a copy
    // nothing uses, and its name may be the replacement's (seen: v2's XSLT made first, then V1's copied and renamed
    // to the same name, which the build refuses).
    private static List<DocRef> owned(final PipelineDoc source, final List<PropertyValue> changes) {
        final Set<String> replaced = new HashSet<>();
        changes.forEach(property -> replaced.add(property.dotted()));
        return PipelineDocs.addedProperties(source).stream()
                .filter(property -> !replaced.contains(property.getElement() + "." + property.getName()))
                .map(property -> NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity))
                .filter(entity -> entity != null && PipelineWrites.OWNED_TYPES.contains(entity.getType()))
                .toList();
    }

    // The property, naming the copy of its document.
    private static PipelineProperty rewire(final PipelineProperty property, final DocRef copy) {
        return new PipelineProperty(property.getElement(), property.getName(), new PipelinePropertyValue(copy));
    }

    // A copy of an XSLT or text converter in the build, its content as the original's.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private DocRef copyDocument(final DocRef original,
                                final String name,
                                final String build,
                                final boolean workingCopy,
                                final String role) {
        final Object content = documentAccess.read(original)
                .orElseThrow(() -> new ToolRefusal(original.getType() + " " + original.getUuid() + " can't be "
                                                   + "read: it doesn't exist, or you don't have permission to "
                                                   + "view it"));
        final DocumentActionHandler handler = documentAccess.handler(original.getType())
                .orElseThrow(() -> new ToolRefusal("Stroom has no " + original.getType() + " store"));
        final DocRef ref = guard.create(original.getType(), name, build, workingCopy
                ? original.getUuid()
                : null, false, role);
        if (!workingCopy) {
            records.update(build, ref, entry -> entry.put("version_of", original.getUuid()));
        } else if (content instanceof final AbstractDoc copied && copied.getVersion() != null) {
            // The original's version when copied: promotion asks before writing the copy back over an original
            // changed since (asked for by the user: a production edit made after the copy was lost).
            records.update(build, ref, entry -> entry.put(BuildRecords.COPY_OF_VERSION, copied.getVersion()));
        }
        final Object made = documentAccess.read(ref)
                .orElseThrow(() -> new ToolRefusal(original.getType() + " '" + name + "' was made but can't be "
                                                   + "read"));
        final Object filled = switch (content) {
            case final XsltDoc xslt -> ((XsltDoc) made).copy()
                    .data(xslt.getData())
                    .description(xslt.getDescription())
                    .build();
            case final TextConverterDoc converter -> ((TextConverterDoc) made).copy()
                    .data(converter.getData())
                    .converterType(converter.getConverterType())
                    .description(converter.getDescription())
                    .build();
            default -> made;
        };
        final Object saved = handler.writeDocument((AbstractDoc) filled);
        final String savedName = saved instanceof final AbstractDoc doc
                ? doc.getName()
                : name;
        return new DocRef(original.getType(), ref.getUuid(), savedName);
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }
}
