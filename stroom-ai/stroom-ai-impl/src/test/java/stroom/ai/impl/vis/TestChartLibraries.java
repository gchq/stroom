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

import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.script.shared.ScriptDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.visualisation.shared.VisualisationDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestChartLibraries {

    private static final String ECHARTS_6 = "/*\n* Licensed to the Apache Software Foundation (ASF)\n*/\n"
                                            + "!function(t){t.echarts={}}(this);var zr={version:\"6.1.0\"};"
                                            + "t.dependencies={zrender:\"6.1.0\"},t.version=\"6.1.0\";";
    private static final String ECHARTS_4 = "!function(t){t.echarts={}}(this);"
                                            + "t.version=\"4.9.0\",t.dependencies={zrender:\"4.3.2\"};";

    private final ScriptStore scriptStore = Mockito.mock(ScriptStore.class);
    private final VisualisationStore visualisationStore = Mockito.mock(VisualisationStore.class);
    private final ExplorerService explorerService = Mockito.mock(ExplorerService.class);
    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final DocumentPermissionService documentPermissionService =
            Mockito.mock(DocumentPermissionService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private ChartLibraries chartLibraries;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(securityContext.asProcessingUserResult(any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(0)).get());
        chartLibraries = new ChartLibraries(() -> scriptStore, () -> visualisationStore, () -> explorerService,
                () -> explorerNodeService, () -> documentPermissionService, securityContext);
    }

    @Test
    void recognisesAnyEChartsSixOrLaterBuild() {
        assertThat(ChartLibraries.echartsVersion(ECHARTS_6)).contains("6.1.0");
        // A newer release, with no new Stroom release.
        assertThat(ChartLibraries.echartsVersion(ECHARTS_6.replace("6.1.0", "7.0.2"))).contains("7.0.2");
        assertThat(ChartLibraries.echartsVersion(ECHARTS_6.replace("6.1.0", "6.2.0-rc.1"))).contains("6.2.0");
        // Saved by an editor that changed its line endings or dropped its leading blank line.
        assertThat(ChartLibraries.echartsVersion(ECHARTS_6.replace("\n", "\r\n").strip())).contains("6.1.0");
        // The unminified build declares its version differently; zrender's version, before it, isn't taken.
        assertThat(ChartLibraries.echartsVersion("""
                var version = '6.0.0';
                var version$1 = '6.1.0';
                var dependencies = {
                  zrender: '6.0.0'
                };""")).contains("6.1.0");
    }

    @Test
    void ignoresOlderOrOtherScripts() {
        assertThat(ChartLibraries.echartsVersion(ECHARTS_4)).isEmpty();
        // ECharts 5, before the minimum of 6.
        assertThat(ChartLibraries.echartsVersion(ECHARTS_6.replace("6.1.0", "5.6.0"))).isEmpty();
        assertThat(ChartLibraries.echartsVersion("var version$1 = '5.5.1';\nvar dependencies = {zrender: '5.6.0'};"))
                .isEmpty();
        // A version but no zrender dependency: not ECharts.
        assertThat(ChartLibraries.echartsVersion("lib.version=\"6.1.0\";")).isEmpty();
        // zrender alone declares its version as `version:"..."`.
        assertThat(ChartLibraries.echartsVersion("var zr={version:\"6.1.0\"};t.dependencies={zrender:\"6.1.0\"}"))
                .isEmpty();
        assertThat(ChartLibraries.echartsVersion("")).isEmpty();
        assertThat(ChartLibraries.echartsVersion(null)).isEmpty();
    }

    @Test
    void normalisesLineEndingsAndOuterWhiteSpace() {
        assertThat(ChartLibraries.normalise(" \r\nabc\r\ndef\n  \n")).isEqualTo("abc\ndef");
    }

    @Test
    void namesScriptsThatLookLikeEChartsButArentRecognised() {
        final DocRef older = script("ECharts", "1");
        final DocRef other = script("Library", "2");
        Mockito.when(scriptStore.list()).thenReturn(List.of(older, other));
        Mockito.when(scriptStore.readDocument(older)).thenReturn(doc(older, ECHARTS_4, List.of()));

        assertThat(chartLibraries.findECharts()).isEmpty();
        assertThat(chartLibraries.findUnrecognisedECharts()).containsExactly(older);
    }

    @Test
    void comparesVersions() {
        assertThat(ChartLibraries.compareVersions("6.1.0", "5.6.0")).isPositive();
        assertThat(ChartLibraries.compareVersions("6.1.0", "6.1.0")).isZero();
        assertThat(ChartLibraries.compareVersions("6.0.10", "6.1")).isNegative();
    }

    @Test
    void findsEChartsByNameFirstThenByContent() {
        final DocRef named = script("ECharts", "1");
        final DocRef other = script("Library", "2");
        Mockito.when(scriptStore.list()).thenReturn(List.of(other, named));
        Mockito.when(scriptStore.readDocument(named)).thenReturn(doc(named, ECHARTS_6, List.of()));
        Mockito.when(scriptStore.readDocument(other)).thenReturn(doc(other, ECHARTS_6, List.of()));

        assertThat(chartLibraries.findECharts()).contains(new EChartsInstall(named, "6.1.0"));
        Mockito.verify(scriptStore, Mockito.never()).readDocument(other);
    }

    @Test
    void findsEChartsUnderAnotherName() {
        final DocRef other = script("Charts", "2");
        Mockito.when(scriptStore.list()).thenReturn(List.of(other));
        Mockito.when(scriptStore.readDocument(other)).thenReturn(doc(other, ECHARTS_6, List.of()));

        assertThat(chartLibraries.findECharts()).contains(new EChartsInstall(other, "6.1.0"));
    }

    @Test
    void findsNothingWhenNotInstalled() {
        final DocRef d3 = script("D3", "3");
        Mockito.when(scriptStore.list()).thenReturn(List.of(d3));
        Mockito.when(scriptStore.readDocument(d3)).thenReturn(doc(d3, "var d3 = {version: \"3.4.8\"};", List.of()));

        assertThat(chartLibraries.findECharts()).isEmpty();
    }

    @Test
    void createsTheAdapterBesideEChartsWithItsPermissions() {
        final DocRef echarts = script("ECharts", "1");
        final DocRef folder = new DocRef("Folder", "f1", "ECharts");
        final DocRef created = script(ChartLibraries.ADAPTER_NAME, "new");
        final ExplorerNode folderNode = ExplorerNode.builder().docRef(folder).build();
        Mockito.when(explorerNodeService.getParent(echarts)).thenReturn(Optional.of(folderNode));
        Mockito.when(explorerNodeService.getChildren(folder)).thenReturn(List.of(
                ExplorerNode.builder().docRef(echarts).build()));
        Mockito.when(scriptStore.readDocument(echarts)).thenReturn(doc(echarts, ECHARTS_6, List.of()));
        Mockito.when(explorerService.create(eq(ScriptDoc.TYPE), eq(ChartLibraries.ADAPTER_NAME), eq(folderNode),
                eq(PermissionInheritance.DESTINATION))).thenReturn(ExplorerNode.builder().docRef(created).build());
        Mockito.when(scriptStore.readDocument(created)).thenReturn(doc(created, null, null));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(created);

        final ArgumentCaptor<ScriptDoc> written = ArgumentCaptor.forClass(ScriptDoc.class);
        Mockito.verify(scriptStore).writeDocument(written.capture());
        assertThat(written.getValue().getData()).isEqualTo(chartLibraries.adapterSource());
        assertThat(written.getValue().getDependencies()).containsExactly(echarts);
        Mockito.verify(documentPermissionService).addDocumentPermissions(echarts, created);
    }

    @Test
    void reusesAnUpToDateAdapter() {
        final DocRef echarts = script("ECharts", "1");
        final DocRef folder = new DocRef("Folder", "f1", "ECharts");
        final DocRef adapter = script(ChartLibraries.ADAPTER_NAME, "a1");
        Mockito.when(explorerNodeService.getParent(echarts)).thenReturn(Optional.of(
                ExplorerNode.builder().docRef(folder).build()));
        Mockito.when(explorerNodeService.getChildren(folder)).thenReturn(List.of(
                ExplorerNode.builder().docRef(echarts).build(), ExplorerNode.builder().docRef(adapter).build()));
        Mockito.when(scriptStore.readDocument(echarts)).thenReturn(doc(echarts, ECHARTS_6, List.of()));
        Mockito.when(scriptStore.readDocument(adapter)).thenReturn(doc(adapter, chartLibraries.adapterSource(),
                List.of(echarts)));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(adapter);
        Mockito.verify(scriptStore, Mockito.never()).writeDocument(any());
        Mockito.verifyNoInteractions(explorerService);
        // Its permissions are kept in step with the library's.
        Mockito.verify(documentPermissionService).addDocumentPermissions(echarts, adapter);
    }

    @Test
    void rewritesAnEditedAdapterWhateverItsVersion() {
        final DocRef echarts = script("ECharts", "1");
        final DocRef folder = new DocRef("Folder", "f1", "ECharts");
        final DocRef adapter = script(ChartLibraries.ADAPTER_NAME, "a1");
        Mockito.when(explorerNodeService.getParent(echarts)).thenReturn(Optional.of(
                ExplorerNode.builder().docRef(folder).build()));
        Mockito.when(explorerNodeService.getChildren(folder)).thenReturn(List.of(
                ExplorerNode.builder().docRef(adapter).build()));
        Mockito.when(scriptStore.readDocument(adapter)).thenReturn(doc(adapter,
                "// " + ChartLibraries.ADAPTER_MARKER + " 99\nfetch('/api/...')", List.of(echarts)));

        chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"));

        final ArgumentCaptor<ScriptDoc> written = ArgumentCaptor.forClass(ScriptDoc.class);
        Mockito.verify(scriptStore).writeDocument(written.capture());
        assertThat(written.getValue().getData()).isEqualTo(chartLibraries.adapterSource());
    }

    @Test
    void putsBackTheAdaptersOnlyDependency() {
        final DocRef echarts = script("ECharts", "1");
        final DocRef planted = script("Planted", "p");
        final DocRef folder = new DocRef("Folder", "f1", "ECharts");
        final DocRef adapter = script(ChartLibraries.ADAPTER_NAME, "a1");
        Mockito.when(explorerNodeService.getParent(echarts)).thenReturn(Optional.of(
                ExplorerNode.builder().docRef(folder).build()));
        Mockito.when(explorerNodeService.getChildren(folder)).thenReturn(List.of(
                ExplorerNode.builder().docRef(adapter).build()));
        Mockito.when(scriptStore.readDocument(adapter)).thenReturn(doc(adapter, chartLibraries.adapterSource(),
                List.of(planted, echarts)));

        chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"));

        final ArgumentCaptor<ScriptDoc> written = ArgumentCaptor.forClass(ScriptDoc.class);
        Mockito.verify(scriptStore).writeDocument(written.capture());
        assertThat(written.getValue().getDependencies()).containsExactly(echarts);
    }

    @Test
    void updatesAnOlderAdapter() {
        final DocRef echarts = script("ECharts", "1");
        final DocRef folder = new DocRef("Folder", "f1", "ECharts");
        final DocRef adapter = script(ChartLibraries.ADAPTER_NAME, "a1");
        Mockito.when(explorerNodeService.getParent(echarts)).thenReturn(Optional.of(
                ExplorerNode.builder().docRef(folder).build()));
        Mockito.when(explorerNodeService.getChildren(folder)).thenReturn(List.of(
                ExplorerNode.builder().docRef(adapter).build()));
        Mockito.when(scriptStore.readDocument(adapter)).thenReturn(doc(adapter,
                "// " + ChartLibraries.ADAPTER_MARKER + " 0\n", List.of(echarts)));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(adapter);
        final ArgumentCaptor<ScriptDoc> written = ArgumentCaptor.forClass(ScriptDoc.class);
        Mockito.verify(scriptStore).writeDocument(written.capture());
        assertThat(written.getValue().getData()).isEqualTo(chartLibraries.adapterSource());
    }

    @Test
    void findsTheContentPackD3VisualisationsByUuid() {
        final DocRef force = new DocRef(VisualisationDoc.TYPE, D3Visualisation.GRAPH.uuid(), "Force");
        final DocRef bar = new DocRef(VisualisationDoc.TYPE, "copy", "My bar chart");
        Mockito.when(visualisationStore.list()).thenReturn(List.of(force, bar));
        Mockito.when(visualisationStore.readDocument(bar)).thenReturn(VisualisationDoc.builder()
                .uuid("copy").name("My bar chart").functionName("visualisations.BarChart").build());
        Mockito.when(visualisationStore.readDocument(force)).thenReturn(VisualisationDoc.builder()
                .uuid(force.getUuid()).name("Force").functionName("visualisations.Force").build());

        final Map<ChartKind, DocRef> found = chartLibraries.findD3();

        assertThat(found).containsEntry(ChartKind.GRAPH, force).containsEntry(ChartKind.BAR, bar).hasSize(2);
    }

    private static DocRef script(final String name, final String uuid) {
        return new DocRef(ScriptDoc.TYPE, uuid, name);
    }

    private static ScriptDoc doc(final DocRef ref, final String data, final List<DocRef> dependencies) {
        return ScriptDoc.builder()
                .uuid(ref.getUuid())
                .name(ref.getName())
                .data(data)
                .dependencies(dependencies)
                .build();
    }
}
