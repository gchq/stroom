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

package stroom.ai.impl.vis;

import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.visualisation.shared.VisualisationDoc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TestVisualisationActions {

    private static final DocRef CHART = new DocRef(VisualisationDoc.TYPE, "chart", "People");

    private final ChartLibraries chartLibraries = Mockito.mock(ChartLibraries.class);
    private final VisualisationStore store = Mockito.mock(VisualisationStore.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final VisualisationActions actions = new VisualisationActions(chartLibraries,
            () -> Mockito.mock(ExplorerNodeService.class), () -> Mockito.mock(ExplorerService.class), () -> store,
            securityContext);

    @Test
    void aGeneratedChartIsGivenTheSettingsAddedSinceKeepingWhatItShows() {
        final String current = EChartsTemplate.settingsJson(ChartKind.GRAPH,
                Map.of("source", "Sender", "target", "Recipient"), Map.of("directed", "true"), null);
        final String earlier = current.replace("\"clusterLinks\"", "\"retired\"");
        generated(earlier, true);

        assertThat(actions.updateSettings(CHART)).isNull();

        final ArgumentCaptor<VisualisationDoc> written = ArgumentCaptor.forClass(VisualisationDoc.class);
        Mockito.verify(store).writeDocument(written.capture());
        assertThat(written.getValue().getSettings()).isEqualTo(current);
    }

    @Test
    void oneAlreadyUpToDateIsLeftAsItIs() {
        generated(EChartsTemplate.settingsJson(ChartKind.BAR, Map.of("category", "Host"), Map.of(), null), true);

        assertThat(actions.updateSettings(CHART)).isNull();
        Mockito.verify(store, Mockito.never()).writeDocument(any());
    }

    @Test
    void itNeedsEditOnTheChart() {
        generated(EChartsTemplate.settingsJson(ChartKind.BAR, Map.of(), Map.of(), null), false);

        assertThat(actions.updateSettings(CHART)).contains("needs Edit permission on it");
        Mockito.verify(store, Mockito.never()).writeDocument(any());
    }

    @Test
    void onlyAChartStroomAiGeneratedIsGivenThem() {
        Mockito.when(chartLibraries.isGenerated(CHART)).thenReturn(false);

        assertThat(actions.updateSettings(CHART)).contains("isn't a chart Stroom AI generated");
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void oneWhoseKindCantBeReadIsLeft() {
        generated("{}", true);

        assertThat(actions.updateSettings(CHART)).contains("don't say which kind of chart it is");
        Mockito.verify(store, Mockito.never()).writeDocument(any());
    }

    private void generated(final String settings, final boolean canEdit) {
        Mockito.when(chartLibraries.isGenerated(CHART)).thenReturn(true);
        Mockito.when(securityContext.hasDocumentPermission(CHART, DocumentPermission.EDIT)).thenReturn(canEdit);
        Mockito.when(store.readDocument(CHART)).thenReturn(VisualisationDoc.builder()
                .uuid("chart")
                .name("People")
                .functionName(ChartLibrary.ECHARTS.functionName())
                .settings(settings)
                .build());
    }
}
