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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.Optional;

/// `describe_template` - how this environment uses a Stroom pipeline template, before making a child of it: the
/// pipelines that inherit from it, each with what it overrides and the feeds it covers; the template's contract
/// (what a child's output must contain for its shared elements to work); and the shared XSLTs those pipelines'
/// XSLTs import, with each named template they call, where, and what it writes.
class DescribeTemplateTool extends AbstractBuiltInTool {

    static final String NAME = "describe_template";
    static final String TEMPLATE_UUID_ARG = "template_uuid";

    private final PipelineTemplates pipelineTemplates;
    private final DocumentAccess documentAccess;

    @Inject
    DescribeTemplateTool(final PipelineTemplates pipelineTemplates, final DocumentAccess documentAccess) {
        this.pipelineTemplates = pipelineTemplates;
        this.documentAccess = documentAccess;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .unlockedBy(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .note("The feeds each child covers come from its processor filters, which need the Manage "
                      + "Processors permission; without it, they are left empty.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("How this environment uses a Stroom pipeline template (not an Elasticsearch index "
                             + "template), before making a child of it: the pipelines that inherit from it, each "
                             + "with what it overrides (properties set, elements removed or re-linked, reference "
                             + "data added) and the feeds it covers, as examples; and the template's contract, "
                             + "what a child's output must contain for the shared elements (a decoration XSLT, "
                             + "say) to work: the elements and expressions they read, the schema group and the "
                             + "output stream type. Also the shared XSLTs (xsl:import) those pipelines' XSLTs "
                             + "use: each named template they call, where, and what it writes, to call the same "
                             + "way in the new XSLT.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(TEMPLATE_UUID_ARG, "UUID of a template pipeline "
                                                              + "(find_pipeline_templates).")
                        .required(TEMPLATE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String uuid = stringArg(call, TEMPLATE_UUID_ARG);
        if (uuid == null) {
            return JsonResults.refused("Give the template_uuid of a template pipeline (find_pipeline_templates).");
        }
        final Optional<DocRef> found = documentAccess.findDocRef(uuid);
        if (found.isEmpty()) {
            return JsonResults.refused(DocumentAccess.NO_PERMISSION);
        }
        if (!PipelineDoc.TYPE.equals(found.get().getType())) {
            return JsonResults.refused(context.links().document(found.get()) + " is a " + found.get().getType()
                                       + ", not a pipeline template: give the UUID of one from "
                                       + "find_pipeline_templates.");
        }
        try {
            final ToolResult result = JsonResults.of(pipelineTemplates.describe(found.get().getUuid()));
            documentAccess.logView(context.session(), found.get(), "Pipeline template read by the " + NAME
                                                                   + " tool");
            return result;
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }
}
