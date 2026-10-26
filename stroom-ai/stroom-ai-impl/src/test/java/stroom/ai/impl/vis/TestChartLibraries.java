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

import stroom.ai.impl.vis.ChartLibraries.D3Install;
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

import java.util.Arrays;
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
        Mockito.when(scriptStore.readDocument(d3)).thenReturn(doc(d3, "var d3 = {version: \"3.4.8\"};",
                List.of()));

        assertThat(chartLibraries.findECharts()).isEmpty();
    }

    @Test
    void createsTheAdapterBesideEChartsNamedForItsContent() {
        final DocRef echarts = script("ECharts", "1");
        final String name = ChartLibraries.adapterName(ChartLibrary.ECHARTS, chartLibraries.adapterSource());
        final DocRef created = script(name, "new");
        final ExplorerNode folderNode = folderOf(echarts, echarts);
        Mockito.when(explorerService.create(eq(ScriptDoc.TYPE), eq(name), eq(folderNode),
                eq(PermissionInheritance.DESTINATION))).thenReturn(ExplorerNode.builder().docRef(created).build());
        Mockito.when(scriptStore.readDocument(created)).thenReturn(doc(created, null, null));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(created);

        assertThat(name).matches(ChartLibraries.ADAPTER_NAME + " [0-9a-f]{6}");
        final ArgumentCaptor<ScriptDoc> written = ArgumentCaptor.forClass(ScriptDoc.class);
        Mockito.verify(scriptStore).writeDocument(written.capture());
        assertThat(written.getValue().getData()).isEqualTo(chartLibraries.adapterSource());
        assertThat(written.getValue().getDependencies()).containsExactly(echarts);
        Mockito.verify(documentPermissionService).addDocumentPermissions(echarts, created);
    }

    @Test
    void reusesTheAdapterWithThisContent() {
        final DocRef echarts = script("ECharts", "1");
        final DocRef adapter = script(ChartLibraries.ADAPTER_NAME, "a1");
        folderOf(echarts, echarts, adapter);
        Mockito.when(scriptStore.readDocument(adapter)).thenReturn(doc(adapter,
                chartLibraries.adapterSource().replace("\n", "\r\n") + "\n\n", List.of(echarts)));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(adapter);
        Mockito.verify(scriptStore, Mockito.never()).writeDocument(any());
        Mockito.verifyNoInteractions(explorerService);
        // Its permissions are kept in step with the library's.
        Mockito.verify(documentPermissionService).addDocumentPermissions(echarts, adapter);
    }

    @Test
    void neverChangesAnAdapterButCreatesAnother() {
        // Changing one would change the charts already drawn with it: one from an earlier (or later) version of
        // Stroom, which a node of another version still uses; one edited by hand; one depending on more than the
        // library.
        final DocRef echarts = script("ECharts", "1");
        final DocRef earlier = script(ChartLibraries.ADAPTER_NAME, "a1");
        final DocRef edited = script(ChartLibraries.ADAPTER_NAME, "a2");
        final DocRef planted = script(ChartLibraries.ADAPTER_NAME, "a3");
        final DocRef created = script(ChartLibraries.ADAPTER_NAME, "new");
        folderOf(echarts, echarts, earlier, edited, planted);
        Mockito.when(scriptStore.readDocument(earlier)).thenReturn(doc(earlier,
                "// " + ChartLibraries.ADAPTER_MARKER + " 0\n", List.of(echarts)));
        Mockito.when(scriptStore.readDocument(edited)).thenReturn(doc(edited,
                chartLibraries.adapterSource() + "\nfetch('/api/...')", List.of(echarts)));
        Mockito.when(scriptStore.readDocument(planted)).thenReturn(doc(planted, chartLibraries.adapterSource(),
                List.of(script("Planted", "p"), echarts)));
        Mockito.when(explorerService.create(eq(ScriptDoc.TYPE), any(), any(), any()))
                .thenReturn(ExplorerNode.builder().docRef(created).build());
        Mockito.when(scriptStore.readDocument(created)).thenReturn(doc(created, null, null));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(created);

        final ArgumentCaptor<ScriptDoc> written = ArgumentCaptor.forClass(ScriptDoc.class);
        Mockito.verify(scriptStore).writeDocument(written.capture());
        assertThat(written.getValue().getUuid()).isEqualTo(created.getUuid());
    }

    @Test
    void usesTheFirstByUuidOfTheSameAdapterCreatedTwice() {
        // Two nodes generating a chart at once can each create one.
        final DocRef echarts = script("ECharts", "1");
        final DocRef second = script(ChartLibraries.ADAPTER_NAME, "b2");
        final DocRef first = script(ChartLibraries.ADAPTER_NAME, "a7");
        folderOf(echarts, echarts, second, first);
        Mockito.when(scriptStore.readDocument(second)).thenReturn(doc(second, chartLibraries.adapterSource(),
                List.of(echarts)));
        Mockito.when(scriptStore.readDocument(first)).thenReturn(doc(first, chartLibraries.adapterSource(),
                List.of(echarts)));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(first);
        Mockito.verifyNoInteractions(explorerService);
    }

    @Test
    void takesTheAdapterAnotherNodeCreatedAtTheSameTime() {
        final DocRef echarts = script("ECharts", "1");
        final DocRef theirs = script(ChartLibraries.ADAPTER_NAME, "a0");
        final DocRef ours = script(ChartLibraries.ADAPTER_NAME, "new");
        final ExplorerNode folderNode = folderOf(echarts, echarts);
        // Seen as ours is created: theirs is there too.
        Mockito.when(explorerNodeService.getChildren(folderNode.getDocRef())).thenReturn(
                List.of(ExplorerNode.builder().docRef(echarts).build()),
                List.of(ExplorerNode.builder().docRef(echarts).build(), ExplorerNode.builder().docRef(theirs).build(),
                        ExplorerNode.builder().docRef(ours).build()));
        Mockito.when(explorerService.create(eq(ScriptDoc.TYPE), any(), any(), any()))
                .thenReturn(ExplorerNode.builder().docRef(ours).build());
        Mockito.when(scriptStore.readDocument(ours)).thenReturn(doc(ours, null, null),
                doc(ours, chartLibraries.adapterSource(), List.of(echarts)));
        Mockito.when(scriptStore.readDocument(theirs)).thenReturn(doc(theirs, chartLibraries.adapterSource(),
                List.of(echarts)));

        assertThat(chartLibraries.ensureAdapter(new EChartsInstall(echarts, "6.1.0"))).isEqualTo(theirs);
        Mockito.verify(documentPermissionService).addDocumentPermissions(echarts, theirs);
    }

    @Test
    void namesEachVersionOfAnAdapterForItsContent() {
        final String source = chartLibraries.adapterSource();
        assertThat(ChartLibraries.contentHash(source)).matches("[0-9a-f]{6}");
        // As compared: line endings and outer white space don't count.
        assertThat(ChartLibraries.contentHash(" \n" + source.replace("\n", "\r\n") + "\n"))
                .isEqualTo(ChartLibraries.contentHash(source));
        assertThat(ChartLibraries.contentHash(source + "\n// changed"))
                .isNotEqualTo(ChartLibraries.contentHash(source));
        assertThat(ChartLibraries.adapterName(ChartLibrary.D3, "x"))
                .isEqualTo(ChartLibrary.D3.adapterName() + " " + ChartLibraries.contentHash("x"));
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

        final Map<ChartKind, DocRef> found = chartLibraries.findContentPackD3();

        assertThat(found).containsEntry(ChartKind.GRAPH, force).containsEntry(ChartKind.BAR, bar).hasSize(2);
    }

    @Test
    void recognisesEachD3BuildFromThreeOn() {
        assertThat(ChartLibraries.d3Version("!function() {\n  var d3 = {\n    version: \"3.4.8\"\n  };"))
                .contains("3.4.8");
        assertThat(ChartLibraries.d3Version("!function(){var ao={version:\"3.5.17\"};this.d3=ao}();"))
                .contains("3.5.17");
        assertThat(ChartLibraries.d3Version("// https://d3js.org Version 4.13.0. Copyright 2018 Mike Bostock.\n"))
                .contains("4.13.0");
        assertThat(ChartLibraries.d3Version("// https://d3js.org v7.9.0 Copyright 2010-2023 Mike Bostock\n"))
                .contains("7.9.0");
    }

    @Test
    void ignoresScriptsThatArentD3() {
        // A version property alone isn't enough: it must be D3's.
        assertThat(ChartLibraries.d3Version("var lib = {version: \"3.1.0\"};")).isEmpty();
        assertThat(ChartLibraries.d3Version(ECHARTS_6)).isEmpty();
        assertThat(ChartLibraries.d3Version(null)).isEmpty();
        // Nor are Stroom's adapters D3, though the D3 one names the library.
        assertThat(ChartLibraries.d3Version(chartLibraries.adapterSource(ChartLibrary.D3))).isEmpty();
        assertThat(ChartLibraries.d3Version(
                "// " + ChartLibrary.D3.adapterMarker() + " 1\n// https://d3js.org v7.9.0")).isEmpty();
    }

    @Test
    void tellsTheAdaptersApart() {
        assertThat(ChartLibraries.adapterLibrary(chartLibraries.adapterSource(ChartLibrary.ECHARTS)))
                .contains(ChartLibrary.ECHARTS);
        assertThat(ChartLibraries.adapterLibrary(chartLibraries.adapterSource(ChartLibrary.D3)))
                .contains(ChartLibrary.D3);
        assertThat(ChartLibraries.adapterLibrary("var common = {};")).isEmpty();
        assertThat(ChartLibraries.adapterVersion(ChartLibrary.D3, chartLibraries.adapterSource(ChartLibrary.D3)))
                .contains(1);
    }

    @Test
    void findsTheNewestD3() {
        final DocRef contentPack = script("D3", "3");
        final DocRef installed = script("D3 7", "7");
        final DocRef echarts = script("ECharts", "1");
        Mockito.when(scriptStore.list()).thenReturn(List.of(contentPack, installed, echarts));
        Mockito.when(scriptStore.readDocument(contentPack)).thenReturn(doc(contentPack,
                "var d3 = {version: \"3.4.8\"};", List.of()));
        Mockito.when(scriptStore.readDocument(installed)).thenReturn(doc(installed,
                "// https://d3js.org v7.9.0 Copyright 2010-2023 Mike Bostock\n", List.of()));
        Mockito.when(scriptStore.readDocument(echarts)).thenReturn(doc(echarts, ECHARTS_6, List.of()));

        final Optional<D3Install> found = chartLibraries.findD3Library();

        assertThat(found).contains(new D3Install(installed, "7.9.0"));
        assertThat(found.orElseThrow().major()).isEqualTo(7);
    }

    @Test
    void fallsBackToTheContentPacksD3() {
        final DocRef contentPack = script("D3", "3");
        Mockito.when(scriptStore.list()).thenReturn(List.of(contentPack));
        Mockito.when(scriptStore.readDocument(contentPack)).thenReturn(doc(contentPack,
                "var d3 = {version: \"3.4.8\"};", List.of()));

        assertThat(chartLibraries.findD3Library()).contains(new D3Install(contentPack, "3.4.8"));
    }

    @Test
    void findsNoD3WhenNotInstalled() {
        final DocRef echarts = script("ECharts", "1");
        Mockito.when(scriptStore.list()).thenReturn(List.of(echarts));
        Mockito.when(scriptStore.readDocument(echarts)).thenReturn(doc(echarts, ECHARTS_6, List.of()));

        assertThat(chartLibraries.findD3Library()).isEmpty();
    }

    @Test
    void createsTheD3AdapterBesideD3() {
        final DocRef d3 = script("D3", "3");
        final String name = ChartLibraries.adapterName(ChartLibrary.D3, chartLibraries.adapterSource(ChartLibrary.D3));
        final DocRef created = script(name, "new");
        // The ECharts adapter in the same folder isn't taken for D3's.
        final DocRef echartsAdapter = script(ChartLibraries.ADAPTER_NAME, "e");
        final ExplorerNode folderNode = folderOf(d3, d3, echartsAdapter);
        Mockito.when(scriptStore.readDocument(d3)).thenReturn(doc(d3, "var d3 = {version: \"3.4.8\"};",
                List.of()));
        Mockito.when(scriptStore.readDocument(echartsAdapter)).thenReturn(doc(echartsAdapter,
                chartLibraries.adapterSource(ChartLibrary.ECHARTS), List.of(d3)));
        Mockito.when(explorerService.create(eq(ScriptDoc.TYPE), eq(name), eq(folderNode),
                eq(PermissionInheritance.DESTINATION))).thenReturn(ExplorerNode.builder().docRef(created).build());
        Mockito.when(scriptStore.readDocument(created)).thenReturn(doc(created, null, null));

        assertThat(chartLibraries.ensureAdapter(new D3Install(d3, "3.4.8"))).isEqualTo(created);

        final ArgumentCaptor<ScriptDoc> written = ArgumentCaptor.forClass(ScriptDoc.class);
        Mockito.verify(scriptStore).writeDocument(written.capture());
        assertThat(written.getValue().getData()).isEqualTo(chartLibraries.adapterSource(ChartLibrary.D3));
        assertThat(written.getValue().getDependencies()).containsExactly(d3);
        Mockito.verify(documentPermissionService).addDocumentPermissions(d3, created);
    }

    // Puts a library in a folder holding the given Script docs, and returns the folder.
    private ExplorerNode folderOf(final DocRef library, final DocRef... children) {
        final DocRef folder = new DocRef("Folder", "f1", "Libraries");
        final ExplorerNode folderNode = ExplorerNode.builder().docRef(folder).build();
        Mockito.when(explorerNodeService.getParent(library)).thenReturn(Optional.of(folderNode));
        Mockito.when(explorerNodeService.getChildren(folder)).thenReturn(Arrays.stream(children)
                .map(child -> ExplorerNode.builder().docRef(child).build())
                .toList());
        Mockito.when(scriptStore.readDocument(library)).thenReturn(doc(library, ECHARTS_6, List.of()));
        return folderNode;
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
