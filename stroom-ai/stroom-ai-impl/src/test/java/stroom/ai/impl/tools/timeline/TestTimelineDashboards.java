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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.vis.EChartsTemplate;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.script.shared.ScriptDoc;
import stroom.visualisation.shared.VisualisationDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// What the create_timeline_dashboard button writes, as the user: a new Data Connection and chart in the folder, or
/// an earlier run's rewritten and renamed; nothing if the table changed since the button was offered; a new Data
/// Connection that can't be filled deleted again.
@SuppressWarnings({"unchecked", "rawtypes"})
class TestTimelineDashboards {

    private static final int CHAT = 4;
    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid", "Case 12");
    private static final ExplorerNode FOLDER_NODE = ExplorerNode.builder().docRef(FOLDER).build();
    private static final DocRef SCRIPT = new DocRef(ScriptDoc.TYPE, "echarts-uuid", "ECharts");
    private static final DocRef ADAPTER = new DocRef(ScriptDoc.TYPE, "adapter-uuid", "ECharts Generic");
    private static final String NAME = "Timeline jsmith 2026-11-09 to 2026-11-13 (spans)";
    private static final String SPANS = "/outputs/timelines/jsmith/spans.csv";
    private static final String CSV = String.join(",", TimelineTables.SPAN_COLUMNS) + "\r\n"
                                      + "s1,2026-11-10T09:00:00Z,2026-11-10T09:20:00Z,WINWORD.EXE,ws-01,focus,,,"
                                      + "ws-01,Endpoint,3,focus,\r\n";

    @TempDir
    Path tempDir;

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final ChartLibraries chartLibraries = Mockito.mock(ChartLibraries.class);
    private final ExplorerService explorerService = Mockito.mock(ExplorerService.class);
    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final VisualisationStore visualisationStore = Mockito.mock(VisualisationStore.class);
    private final DocumentActionHandler handler = Mockito.mock(DocumentActionHandler.class);
    private FakeTabWorkspace files;
    private TimelineDashboards dashboards;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        new SessionWorkspace(files.workspace(), CHAT).write(WorkspacePath.parse(SPANS), CSV, null);
        Mockito.when(explorerNodeService.getNode(FOLDER)).thenReturn(Optional.of(FOLDER_NODE));
        Mockito.when(explorerNodeService.getNode(Mockito.argThat(doc -> doc != null && !FOLDER.equals(doc))))
                .thenAnswer(invocation -> Optional.of(ExplorerNode.builder()
                        .docRef(invocation.<DocRef>getArgument(0)).build()));
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of());
        Mockito.when(documentAccess.handler(DataConnectionDoc.TYPE)).thenReturn(Optional.of(handler));
        Mockito.when(handler.readDocument(Mockito.any())).thenAnswer(invocation -> {
            final DocRef doc = invocation.getArgument(0);
            return DataConnectionDoc.builder().uuid(doc.getUuid()).name(doc.getName()).build();
        });
        Mockito.when(visualisationStore.readDocument(Mockito.any())).thenAnswer(invocation -> {
            final DocRef doc = invocation.getArgument(0);
            return VisualisationDoc.builder().uuid(doc.getUuid()).name(doc.getName()).build();
        });
        Mockito.when(explorerService.create(Mockito.anyString(), Mockito.anyString(), Mockito.eq(FOLDER_NODE),
                Mockito.eq(PermissionInheritance.DESTINATION))).thenAnswer(invocation -> ExplorerNode.builder()
                .docRef(new DocRef(invocation.getArgument(0), invocation.<String>getArgument(0).toLowerCase()
                                                              + "-new", invocation.getArgument(1)))
                .build());
        Mockito.when(explorerService.rename(Mockito.any(), Mockito.anyString())).thenAnswer(invocation -> {
            final ExplorerNode node = invocation.getArgument(0);
            return ExplorerNode.builder().docRef(node.getDocRef().copy().name(invocation.getArgument(1)).build())
                    .build();
        });
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.of(new EChartsInstall(SCRIPT, "6.1.0")));
        Mockito.when(chartLibraries.currentUserCanUse(SCRIPT)).thenReturn(true);
        Mockito.when(chartLibraries.ensureAdapter(Mockito.any(EChartsInstall.class))).thenReturn(ADAPTER);
        Mockito.when(chartLibraries.isGenerated(Mockito.any())).thenReturn(true);
        dashboards = new TimelineDashboards(files.workspace(), documentAccess, chartLibraries, () -> explorerService,
                () -> explorerNodeService, () -> visualisationStore);
    }

    @Test
    void aNewDataConnectionAndChartAreWrittenInTheFolder() {
        final TimelineDashboards.Written written = dashboards.write(CHAT, FOLDER, NAME, plan(CSV, null));

        assertThat(written.dataConnection()).isEqualTo(new DocRef(DataConnectionDoc.TYPE, "dataconnection-new",
                NAME));
        assertThat(written.visualisation()).isEqualTo(new DocRef(VisualisationDoc.TYPE, "visualisation-new", NAME));
        assertThat(written.replaced()).isNull();
        final ArgumentCaptor<DataConnectionDoc> doc = ArgumentCaptor.forClass(DataConnectionDoc.class);
        Mockito.verify(handler).writeDocument(doc.capture());
        assertThat(doc.getValue().getCsv().getData()).isEqualTo(CSV);
        assertThat(doc.getValue().getTimeField()).isEqualTo("start");
        assertThat(doc.getValue().getDescription()).isEqualTo("A snapshot.");
        final ArgumentCaptor<VisualisationDoc> chart = ArgumentCaptor.forClass(VisualisationDoc.class);
        Mockito.verify(visualisationStore).writeDocument(chart.capture());
        assertThat(chart.getValue().getScriptRef()).isEqualTo(ADAPTER);
        assertThat(chart.getValue().getFunctionName()).isEqualTo(EChartsTemplate.FUNCTION_NAME);
        assertThat(EChartsTemplate.defaults(chart.getValue().getSettings()))
                .containsEntry(EChartsTemplate.KIND_CONTROL, "timeline").containsEntry("lane", "lane");
        // The dashboard draws them, in the folder's dialog.
        final VisComponentSettings vis = (VisComponentSettings) written.config().getComponents().get(1)
                .getSettings();
        assertThat(vis.getVisualisation()).isEqualTo(written.visualisation());
        assertThat(written.folder().getDocRef()).isEqualTo(FOLDER);
        assertThat(written.folder().getRootNodeUuid()).isEqualTo(ExplorerConstants.SYSTEM_DOC_REF.getUuid());
        Mockito.verify(explorerService, Mockito.never()).rename(Mockito.any(), Mockito.anyString());
    }

    @Test
    void anEarlierRunsDataConnectionAndChartAreRewrittenAndRenamed() {
        final DocRef earlier = new DocRef(DataConnectionDoc.TYPE, "dc-old",
                "Timeline jsmith 2026-10-01 to 2026-10-07 (spans)");
        final DocRef earlierChart = new DocRef(VisualisationDoc.TYPE, "vis-old", earlier.getName());
        Mockito.when(documentAccess.node(earlier)).thenReturn(Optional.of(ExplorerNode.builder().docRef(earlier)
                .build()));
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of(
                ExplorerNode.builder().docRef(earlier).build(), ExplorerNode.builder().docRef(earlierChart).build()));

        final TimelineDashboards.Written written = dashboards.write(CHAT, FOLDER, NAME, plan(CSV, earlier));

        assertThat(written.replaced()).isEqualTo(earlier);
        assertThat(written.dataConnection()).isEqualTo(new DocRef(DataConnectionDoc.TYPE, "dc-old", NAME));
        assertThat(written.visualisation()).isEqualTo(new DocRef(VisualisationDoc.TYPE, "vis-old", NAME));
        Mockito.verify(explorerService, Mockito.never()).create(Mockito.anyString(), Mockito.anyString(),
                Mockito.any(), Mockito.any());
        Mockito.verify(explorerService, Mockito.times(2)).rename(Mockito.any(), Mockito.eq(NAME));
    }

    @Test
    void aSecondClickRewritesTheSameDocuments() {
        final DocRef written = new DocRef(DataConnectionDoc.TYPE, "dc-1", NAME);
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of(ExplorerNode.builder().docRef(written)
                .build(), ExplorerNode.builder().docRef(new DocRef(VisualisationDoc.TYPE, "vis-1", NAME)).build()));

        final TimelineDashboards.Written again = dashboards.write(CHAT, FOLDER, NAME, plan(CSV, null));

        assertThat(again.dataConnection()).isEqualTo(written);
        assertThat(again.visualisation().getUuid()).isEqualTo("vis-1");
        Mockito.verify(explorerService, Mockito.never()).create(Mockito.anyString(), Mockito.anyString(),
                Mockito.any(), Mockito.any());
    }

    @Test
    void aTableChangedSinceTheButtonWasOfferedWritesNothing() {
        assertThatThrownBy(() -> dashboards.write(CHAT, FOLDER, NAME, plan("other,csv\r\n", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("built again since this button was offered");
        Mockito.verifyNoInteractions(explorerService, handler, visualisationStore);
    }

    @Test
    void aNewDataConnectionThatCantBeFilledIsDeletedAgain() {
        Mockito.when(handler.writeDocument(Mockito.any())).thenThrow(new RuntimeException("The CSV is 9 MiB, more "
                                                                                          + "than the limit"));

        assertThatThrownBy(() -> dashboards.write(CHAT, FOLDER, NAME, plan(CSV, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The Data Connection couldn't be written: The CSV is 9 MiB, more than the limit");
        Mockito.verify(explorerService).delete(Mockito.argThat(nodes -> nodes.size() == 1
                                                                        && "dataconnection-new".equals(
                nodes.getFirst().getDocRef().getUuid())));
        Mockito.verifyNoInteractions(visualisationStore);
    }

    @Test
    void theUserMustBeAbleToUseECharts() {
        Mockito.when(chartLibraries.currentUserCanUse(SCRIPT)).thenReturn(false);

        assertThatThrownBy(() -> dashboards.write(CHAT, FOLDER, NAME, plan(CSV, null)))
                .hasMessageContaining("You don't have Use permission on Script 'ECharts'");
        Mockito.verifyNoInteractions(explorerService);
    }

    private static TimelineDashboardPlan plan(final String csv, final DocRef replace) {
        return new TimelineDashboardPlan("jsmith", TimelineDataConnection.SPANS, SPANS,
                ProposeCodeChangeTool.sha256(csv), 1, 0L, 1L, "A snapshot.",
                TimelineDashboardConfig.mappings(TimelineDataConnection.SPANS, null, false), replace);
    }
}
