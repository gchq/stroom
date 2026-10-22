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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.ReferenceGenerator;
import stroom.ai.impl.tools.generation.ReferenceMapping;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// `build_reference_xslt` - writes the XSLT of a reference-data pipeline from a mapping: for each record and map, a
/// `<reference>` with the map name, key and value in reference-data:2 (the Stroom MCP server's
/// `tools/generation.py` `build_reference_xslt`, on [ReferenceGenerator]). It saves nothing: save_xslt saves it.
class BuildReferenceXsltTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildReferenceXsltTool.class);

    static final String NAME = "build_reference_xslt";
    static final String MAPPING_ARG = "mapping";
    static final String SCHEMA_VERSION_ARG = "schema_version";
    static final String BUILD_ARG = "build";

    private final HeldSchemas heldSchemas;
    private final RequiredImports requiredImports;

    @Inject
    BuildReferenceXsltTool(final HeldSchemas heldSchemas, final RequiredImports requiredImports) {
        this.heldSchemas = heldSchemas;
        this.requiredImports = requiredImports;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("The XML schemas, the standing instructions and the XSLTs they require are read as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Write the XSLT of a reference-data pipeline (a child of the reference-data "
                             + "template) from a mapping: for each record and map, a <reference> with the map name, "
                             + "key and value in reference-data:2. The events pipeline then names the feed as a "
                             + "pipeline reference and its mapping reads the map with lookup.")
                .parameters(ObjectSchema.builder()
                        .addProperty(MAPPING_ARG, TranslationArgs.object("The maps a reference feed provides: name, "
                                                                         + "key field and value fields per map."))
                        .addStringProperty(SCHEMA_VERSION_ARG, "reference-data schema version; defaults to the "
                                                               + "newest this Stroom holds.")
                        .addStringProperty(BUILD_ARG, "The build it will be saved in: the shared XSLTs standing "
                                                      + "instructions require of reference XSLTs there are "
                                                      + "imported.")
                        .required(MAPPING_ARG)
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
        final ReferenceMapping given = mapping(TranslationArgs.value(call, MAPPING_ARG));
        ReferenceMapping mapping = given;
        final String version = version(stringArg(call, SCHEMA_VERSION_ARG));
        final String build = stringArg(call, BUILD_ARG);
        final List<RequiredImport> required = build == null
                ? List.of()
                : requiredImports.requiredFor(context.session(), InstructionRules.REFERENCE, build, List.of())
                        .stream()
                        .filter(r -> !given.imports().contains(r.href()))
                        .toList();
        if (!required.isEmpty()) {
            final List<String> imports = new ArrayList<>(mapping.imports());
            required.forEach(r -> imports.add(r.href()));
            mapping = new ReferenceMapping(mapping.input(), mapping.jsonLayout(), mapping.root(), mapping.record(),
                    mapping.maps(), imports, mapping.dropWhen());
        }
        final ReferenceGenerator.Generated generated = ReferenceGenerator.generate(mapping, version);
        final Map<String, Object> result = new LinkedHashMap<>(generated.toMap());
        result.put("schema_version", version);
        if (!required.isEmpty()) {
            result.put("required_imports", requiredImports.importNotes(required, false));
        }
        result.put("hint", !generated.ok()
                ? "Fix the problems and call again."
                : "save_xslt it (give build here first, so the imports standing instructions require are in it); "
                  + "create_pipeline from the reference-data template (find_pipeline_templates stage=reference) "
                  + "with combinedParser.textConverter (if the feed is text) and translationFilter.xslt; "
                  + "create_processor_filter on the Raw Reference stream; wait_for_processing "
                  + "output_type='Reference'. Then give the events pipeline references=[{feed, loader_pipeline}].");
        return result;
    }

    // The reference-data schema version: the one given, else the newest this Stroom holds.
    private String version(final String given) {
        if (given != null) {
            return given;
        }
        try {
            final List<String> ids = heldSchemas.systemIds().stream()
                    .filter(id -> id.contains("reference-data"))
                    .toList();
            if (!ids.isEmpty()) {
                final String last = ids.getLast();
                final String version = last.substring(last.lastIndexOf("-v") + 2);
                return version.endsWith(".xsd")
                        ? version.substring(0, version.length() - ".xsd".length())
                        : version;
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "The XML schemas couldn't be listed: " + e.getMessage());
        }
        return ReferenceGenerator.DEFAULT_VERSION;
    }

    private static ReferenceMapping mapping(final Object given) {
        if (given == null) {
            throw new ToolRefusal("Give mapping: the maps a reference feed provides.");
        }
        try {
            return ReferenceMapping.parse(given instanceof final String text
                    ? PyJson.loads(text)
                    : given);
        } catch (final MappingValidationException | PyJson.JsonDecodeException e) {
            throw new ToolRefusal("mapping is not a reference mapping: " + e.getMessage());
        }
    }
}
