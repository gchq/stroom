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
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.List;

/// `find_pipeline_templates` - Stroom pipeline templates (pipelines a new pipeline inherits from) for a stage, best
/// first, each with its element chain, what a child must supply, what it already configures, its reference
/// loaders, backend, and how many children use it. Found by what each pipeline does, whatever it's called and
/// wherever it is. Only the pipelines the user can view are searched.
class FindPipelineTemplatesTool extends AbstractBuiltInTool {

    static final String NAME = "find_pipeline_templates";
    static final String STAGE_ARG = "stage";
    static final List<String> STAGES = List.of("translation", "indexing", "discovery", "reference", "records",
            "forwarding");

    private final PipelineTemplates pipelineTemplates;

    @Inject
    FindPipelineTemplatesTool(final PipelineTemplates pipelineTemplates) {
        this.pipelineTemplates = pipelineTemplates;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("Only the pipelines the user can view are searched. Each candidate's layers are read as the "
                      + "pipeline editor reads them, which needs Use on the pipelines it inherits from.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Stroom pipeline templates (pipelines a new pipeline inherits from; not Elasticsearch "
                             + "index templates, which propose_index_template builds): candidates for this stage, "
                             + "best first: configured template folders, then pipelines that working pipelines "
                             + "already inherit from, then Stroom's standard templates. For each: its element "
                             + "chain, what a child must supply (e.g. its XSLT), shared elements it already "
                             + "configures (e.g. a decoration XSLT or an Elastic cluster), reference loaders, "
                             + "backend, and how many children use it.")
                .parameters(ObjectSchema.builder()
                        .addEnumProperty(STAGE_ARG, STAGES, "translation (Raw Events to Events; always the first "
                                                            + "pipeline for a new source), indexing (Events to an "
                                                            + "index), discovery (raw structured data straight to "
                                                            + "an index), reference (a Raw Reference feed to the "
                                                            + "reference-data maps stroom:lookup() reads), records "
                                                            + "(a source to a Records stream of records:2 records) "
                                                            + "or forwarding (Events sent on: to Kafka, e.g. CEF "
                                                            + "for ArcSight, or as text). Found by what each "
                                                            + "pipeline does, whatever it's called and wherever it "
                                                            + "is.")
                        .required(STAGE_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String stage = stringArg(call, STAGE_ARG);
        if (stage == null || !STAGES.contains(stage)) {
            return JsonResults.refused("Give the stage: one of " + String.join(", ", STAGES) + ".");
        }
        return JsonResults.of(pipelineTemplates.find(stage));
    }
}
