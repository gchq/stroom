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

import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.meta.shared.MetaRow;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/// The builds' tools' world (write_documentation, promote_build): the translation tools' world
/// ([TranslationWorld]: the in-memory explorer, a build plan whose facts the test sets, the event-logging schema),
/// with the Field mapping section, the pipelines' shapes and their kept mappings as the test gives them (as the
/// Stroom MCP server's tests patched `field_mapping_section`, `_shape_stage` and `kept_mapping`).
final class DocumentationWorld {

    final TranslationWorld translation = new TranslationWorld();
    final BuildWorld world = translation.world;
    final FakeBuildFacts facts = translation.facts;
    final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    final StreamSummaries streamSummaries = Mockito.mock(StreamSummaries.class);
    final FieldMappingSections sections = Mockito.mock(FieldMappingSections.class);
    final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    final PipelineShapes pipelineShapes = Mockito.mock(PipelineShapes.class);
    final IndexSurveys indexSurveys = Mockito.mock(IndexSurveys.class);
    final IndexFields indexFields = Mockito.mock(IndexFields.class);
    final DocumentPlaces places = new DocumentPlaces(world.documentAccess, pipelineIndex);
    final DocumentWrites writes = new DocumentWrites(world.documentAccess);
    final BuildDocumentation docs = new BuildDocumentation(world.guard(), facts, world.records(),
            world.documentAccess, places, writes, translation.streamReader, streamSummaries,
            translation.agentInstructions, translation.builds, translation.history);
    final IndexDocumentation indexDocumentation = new IndexDocumentation(docs, world.documentAccess, places, writes,
            pipelineShapes, translation.consent, indexSurveys, indexFields, translation.eventVersions,
            world.guard());

    DocumentationWorld() {
        Mockito.when(translation.streamReader.find(any(), anyInt(), anyBoolean()))
                .thenReturn(new ResultPage<>(List.<MetaRow>of()));
        Mockito.when(translation.streamReader.meta(Mockito.anyLong())).thenThrow(new IllegalStateException("none"));
        Mockito.when(pipelineShapes.shape(anyString())).thenAnswer(inv -> shape(facts.stages.getOrDefault(
                (String) inv.getArgument(0), "translation")));
        Mockito.when(keptMappings.keptXslt(anyString())).thenReturn(Optional.empty());
    }

    /// @return write_documentation, wired on the world.
    WriteDocumentationTool documentationTool() {
        return new WriteDocumentationTool(docs, sections, indexDocumentation, keptMappings, pipelineShapes,
                MappingDrift.NONE, world.documentAccess, places, writes, world.guard(), translation.consent,
                translation.plan);
    }

    /// @return A pipeline in the build, listed in its documents.
    DocRef pipeline(final String build, final String name, final String uuid) {
        final DocRef made = world.inBuild(build, "Pipeline", name, uuid);
        facts.docs.add(FakeBuildFacts.doc("Pipeline", uuid, name));
        return made;
    }

    /// @return A Documentation doc in the build, with its text, listed in its documents.
    DocRef documentation(final String build, final String name, final String uuid, final String text) {
        final DocRef made = world.inBuild(build, "Documentation", name, uuid);
        world.docs.put(uuid, ((DocumentationDoc) world.docs.get(uuid)).copy()
                .data(text).build());
        facts.docs.add(FakeBuildFacts.doc("Documentation", uuid, name));
        return made;
    }

    /// @return The tool's result, as JSON, failing the test if it refused.
    Map<String, Object> call(final StroomTool tool, final ToolCallContext context, final String arguments) {
        final ToolResult result = tool.execute(PortedToolCalls.call(tool.definition().name(), arguments), context);
        if (result.isError()) {
            throw new AssertionError(result.text());
        }
        return PortedToolCalls.json(result.text());
    }

    /// @return The refusal the tool gave, failing the test if it didn't refuse.
    String refusal(final StroomTool tool, final ToolCallContext context, final String arguments) {
        final ToolResult result = tool.execute(PortedToolCalls.call(tool.definition().name(), arguments), context);
        if (!result.isError()) {
            throw new AssertionError("Not refused: " + result.text());
        }
        return result.text();
    }

    /// @return Arguments as JSON.
    static String args(final Map<String, Object> arguments) {
        return JsonUtil.writeValueAsString(arguments, false);
    }

    private static PipelineShapes.Shape shape(final String stage) {
        return new PipelineShapes.Shape(stage, null, List.of(), null, List.of(), List.of(), List.of(), Map.of());
    }
}
