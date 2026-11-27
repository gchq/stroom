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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// The feeds a promotion gave new filters, kept in the session for build_status to report how they are selected
/// once the build is promoted (decided by the user, 2026-11-25).
class TestPromotedFeeds {

    private static final DocRef V11 = new DocRef(PipelineDoc.TYPE, "v11", "ACME-V1.1-Events");

    @Test
    void aPromotionsNewFiltersAreKeptInTheSessionAndReportedFromIt() {
        final ToolCallContext context = context(memoryWorkspace());
        final PromotionSwitchOver switchOver = Mockito.mock(PromotionSwitchOver.class);
        Mockito.when(switchOver.selectionReport(List.of("ACME-A", "ACME-B"), "Raw Events", Set.of(40)))
                .thenReturn(List.of("ACME-A (Raw Events) is selected twice"));
        final List<PromotionProcessing.Entry> processing = List.of(
                new PromotionProcessing.Entry(V11, List.of("ACME-A", "ACME-B"), "Raw Events", List.of()),
                // Events from a source pipeline: no feed to report.
                new PromotionProcessing.Entry(V11, List.of(), "Events", List.of()));
        final List<Map<String, Object>> made = List.of(Map.of("filter_id", 40), Map.of("filter_id", 41));

        assertThat(PromotedFeeds.remember(context, "bb", processing, made, switchOver))
                .containsExactly("ACME-A (Raw Events) is selected twice");

        assertThat(PromotedFeeds.recalled(context, "bb")).containsExactly(new PromotedFeeds.Promoted(40,
                "Raw Events", List.of("ACME-A", "ACME-B")));
        assertThat(PromotedFeeds.report(PromotedFeeds.recalled(context, "bb"), switchOver))
                .containsExactly("ACME-A (Raw Events) is selected twice");
        // Another build: nothing kept for it.
        assertThat(PromotedFeeds.recalled(context, "other")).isEmpty();
        // Without a switch-over, kept all the same, nothing said.
        assertThat(PromotedFeeds.remember(context, "cc", processing, made, null)).isEmpty();
        assertThat(PromotedFeeds.recalled(context, "cc")).hasSize(1);
    }

    @Test
    void aSessionWithoutAWorkspaceKeepsNothing() {
        final ToolCallContext context = context(null);

        PromotedFeeds.remember(context, "bb", List.of(new PromotionProcessing.Entry(V11, List.of("ACME-A"),
                "Raw Events", List.of())), List.of(Map.of("filter_id", 40)), null);

        assertThat(PromotedFeeds.recalled(context, "bb")).isEmpty();
    }

    @Test
    void buildStatusOfABuildPromotedHereSaysHowItsFeedsAreSelected() {
        assertThat(BuildStatusTool.promotedStatus("bb", List.of("ACME-A (Raw Events) is selected twice")))
                .containsEntry("status", "promoted")
                .containsEntry(PromotedFeeds.FEED_SELECTION, List.of("ACME-A (Raw Events) is selected twice"))
                .hasEntrySatisfying("next", next -> assertThat(String.valueOf(next)).contains("Report only",
                        "switch-over"));
        assertThat(BuildStatusTool.promotedStatus("bb", List.of()).get(PromotedFeeds.FEED_SELECTION)).asList()
                .singleElement().satisfies(line -> assertThat(String.valueOf(line)).contains("selected by one "
                                                                                             + "pipeline's"));
    }

    private static ToolCallContext context(final SessionWorkspace workspace) {
        final ToolCallContext.Builder builder = ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP,
                "mcp-session-1", new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")));
        return workspace == null
                ? builder.build()
                : builder.workspace(workspace).build();
    }

    // A session's workspace, its files kept in memory.
    private static SessionWorkspace memoryWorkspace() {
        final Map<String, String> files = new LinkedHashMap<>();
        final ChatWorkspace chat = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chat.write(Mockito.anyInt(), any(WorkspacePath.class), Mockito.anyString(), any()))
                .thenAnswer(inv -> {
                    files.put(String.valueOf((Object) inv.getArgument(1)), inv.getArgument(2));
                    return files.size();
                });
        Mockito.when(chat.find(Mockito.anyInt(), any(WorkspacePath.class))).thenAnswer(inv ->
                files.containsKey(String.valueOf((Object) inv.getArgument(1)))
                        ? Optional.of(1)
                        : Optional.empty());
        Mockito.when(chat.readLines(Mockito.anyInt(), any(WorkspacePath.class), Mockito.anyLong(),
                Mockito.anyInt())).thenAnswer(inv ->
                List.of(files.get(String.valueOf((Object) inv.getArgument(1))).split("\n", -1)));
        return new SessionWorkspace(chat, 1);
    }
}
