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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolTestFixture;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.AgentInstructions.Standing;
import stroom.ai.impl.tools.instructions.IndexFieldNames.IndexFields;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.query.DataSourceDocumentation.FeedingPlans;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// `check_instructions`: the AGENTS docs that apply in a folder, or a draft of one, checked against the indexes
/// there and their index plans.
class TestCheckInstructionsTool {

    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "endpoint-uuid", "Endpoint");
    private static final DocRef INDEX = new DocRef("ElasticIndex", "index-uuid", "Endpoint");
    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipeline-uuid", "Endpoint indexing");

    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final IndexFieldNames indexFieldNames = Mockito.mock(IndexFieldNames.class);
    private final DataSourceDocumentation documentation = Mockito.mock(DataSourceDocumentation.class);
    private final TimelineFields timelineFields = Mockito.mock(TimelineFields.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final List<InstructionDoc> docs = new ArrayList<>();
    private CheckInstructionsTool tool;

    @BeforeEach
    void setUp() {
        Mockito.when(agentInstructions.load(Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> new Standing(List.copyOf(docs)));
        Mockito.when(agentInstructions.folderPath(FOLDER)).thenReturn("/Endpoint");
        Mockito.when(documentAccess.findFolder("/Endpoint")).thenReturn(Optional.of(FOLDER));
        Mockito.when(indexFieldNames.indexes(Mockito.anyString())).thenReturn(List.of(new IndexFields(INDEX,
                "/Endpoint", Set.of("actor.login", "user.name", "host.name"))));
        Mockito.when(documentation.feedingPlans(INDEX)).thenReturn(new FeedingPlans(List.of(PIPELINE),
                Map.of(PIPELINE, List.of(FieldPlan.of(FieldPlan.ELASTICSEARCH, "endpoint", "@timestamp", List.of(
                        new PlannedField("user.name", "keyword", "EventSource/User/Id", "", null, false,
                                List.of(), null)))))));
        Mockito.when(timelineFields.conventions(Mockito.anyString())).thenReturn(Map.of());
        tool = new CheckInstructionsTool(new InstructionFacts(agentInstructions, indexFieldNames, () -> documentation,
                () -> timelineFields), documentAccess);
    }

    @Test
    void theDocsThatApplyInAFolderAreCheckedMostGeneralFirst() {
        docs.add(doc("/", "root", "## Fields\n- host: host.name\n"));
        docs.add(doc("/Endpoint", "endpoint", "## Field names\n- user: actor.login\n"));
        docs.add(doc("/Proxy", "proxy", "## Fields\n- user: nonsense\n"));

        final String result = run(Map.of("folder", "/Endpoint"));

        assertThat(result).startsWith("2 AGENTS doc(s) apply in /Endpoint, most general first.")
                .contains("The doc in / (uuid root):\nNo problems or suggestions.",
                        "The doc in /Endpoint (uuid endpoint):\n- Problem (heading): Heading '## Field names' isn't "
                        + "'## Fields'",
                        "The doc with the problems' fixes applied (suggestions left for the user), to propose with "
                        + "propose_instructions:\n## Fields\n- user: actor.login\n")
                // The doc in another folder doesn't apply there.
                .doesNotContain("nonsense");
    }

    @Test
    void aDraftIsCheckedAgainstTheIndexPlansOfTheFolder() {
        final String result = run(Map.of("folder", "/Endpoint", "markdown", "## Fields\n- user: actor.login\n"));

        assertThat(result).startsWith("Draft AGENTS doc for /Endpoint:\n- Suggestion (plan_disagrees): '- user: "
                                      + "actor.login' gives actor.login for index Endpoint, but the index plan of "
                                      + "'Endpoint indexing' maps EventSource/User/Id to user.name.")
                .doesNotContain("fixes applied");
    }

    @Test
    void aDraftWithNoFolderHasOnlyItsFormChecked() {
        assertThat(run(Map.of("markdown", "## Required imports\n- indexng: Common-Index-V1\n")))
                .startsWith("Draft AGENTS doc (no folder given, so only its form is checked):\n- Problem "
                            + "(unknown_key)")
                .contains("- indexing: Common-Index-V1");
    }

    @Test
    void aFolderWithNoDocOfItsOwnIsOfferedWhatOneCouldSay() {
        Mockito.when(documentation.feedingPlans(INDEX)).thenReturn(new FeedingPlans(List.of(PIPELINE),
                Map.of(PIPELINE, List.of())));
        Mockito.when(timelineFields.conventions("EventSource/User/Id")).thenReturn(Map.of("ecs", "user.name"));

        assertThat(run(Map.of("folder", "/Endpoint"))).isEqualTo("""
                No AGENTS docs apply in /Endpoint.

                /Endpoint has no AGENTS doc of its own; one could say:
                - Suggestion (convention_only): Index Endpoint would find the user only by a field convention \
                (user.name), a guess. Fix: add '- user: user.name' under '## Fields' if user.name is the user \
                field (ask the user).""");
    }

    @Test
    void anUnknownFolderOrNothingGivenIsAnError() {
        assertThat(run(Map.of("folder", "nope-uuid"))).startsWith("Error: no folder 'nope-uuid'");
        assertThat(run(Map.of())).startsWith("Error: give the folder");
    }

    private String run(final Map<String, Object> arguments) {
        return tool.execute(ToolTestFixture.request(CheckInstructionsTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), ToolCalls.inChat(1)).text();
    }

    private static InstructionDoc doc(final String folder, final String uuid, final String text) {
        return new InstructionDoc(new DocRef(DocumentationDoc.TYPE, uuid, AgentInstructions.DOC_NAME), folder, text,
                List.of());
    }
}
