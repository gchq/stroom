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

package stroom.ai.impl.tools;

import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;

import event.logging.OtherObject;
import event.logging.ViewEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestXsltLookupsTool {

    private static final String ELEMENT = "xsltFilter";
    private static final String MAIN_XSLT = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:stroom="stroom" version="2.0">
              <xsl:include href="common"/>
              <xsl:template match="Event">
                <xsl:value-of select="stroom:lookup($emailMap, @user)"/>
                <xsl:value-of select="stroom:lookup('NO_SUCH_MAP', @user)"/>
                <xsl:value-of select="stroom:lookup(concat('HOST_', @site), @host)"/>
              </xsl:template>
            </xsl:stylesheet>
            """;
    private static final String COMMON_XSLT = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
              <xsl:variable name="emailMap" select="'USER_TO_EMAIL'"/>
            </xsl:stylesheet>
            """;

    private static final String EDITED_XSLT = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:stroom="stroom"
                            version="2.0">
              <xsl:template match="record">
                <xsl:value-of select="stroom:lookup('EDITED_MAP', @user)"/>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private DocRef mainXslt;
    private DocRef parent;
    private DocRef child;
    private DocRef hiddenLoader;
    private DocRef removedLoader;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        mainXslt = xslt("main-xslt", "Main XSLT", MAIN_XSLT, true);
        final DocRef common = xslt("common-xslt", "common", COMMON_XSLT, true);
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "common", false)).thenReturn(List.of(common));

        final DocRef userFeed = fixture.add(new DocRef(FeedDoc.TYPE, "user-feed", "USER-REF"), null, true);
        final DocRef userLoader = loader("user-loader", "User loader", "user-loader-xslt", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="reference-data:2"
                                version="2.0">
                  <xsl:template match="record">
                    <reference><map>USER_TO_EMAIL</map></reference>
                  </xsl:template>
                </xsl:stylesheet>
                """, true);
        final DocRef hostLoader = loader("host-loader", "Host loader", "host-loader-xslt", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="reference-data:2"
                                version="2.0">
                  <xsl:template match="record">
                    <reference><map>HOST_LONDON</map></reference>
                    <reference><map>UNUSED_MAP</map></reference>
                  </xsl:template>
                </xsl:stylesheet>
                """, true);
        hiddenLoader = loader("hidden-loader", "Secret loader", "hidden-loader-xslt", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="reference-data:2"
                                version="2.0">
                  <xsl:template match="record"><map>NO_SUCH_MAP</map></xsl:template>
                </xsl:stylesheet>
                """, false);
        removedLoader = loader("removed-loader", "Removed loader", "removed-loader-xslt", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="reference-data:2"
                                version="2.0">
                  <xsl:template match="record"><map>NO_SUCH_MAP</map></xsl:template>
                </xsl:stylesheet>
                """, true);

        // The parent uses the XSLT, with three loaders; the child removes one and adds another.
        parent = pipeline("parent", "Parent pipeline", null, new PipelineDataBuilder()
                .addElement(new PipelineElement(ELEMENT, "XSLTFilter"))
                .addProperty(new PipelineProperty(ELEMENT, "xslt", new PipelinePropertyValue(mainXslt)))
                .addPipelineReference(reference(userLoader, userFeed))
                .addPipelineReference(reference(hiddenLoader, null))
                .addPipelineReference(reference(removedLoader, null))
                .build());
        child = pipeline("child", "Child pipeline", parent, new PipelineDataBuilder()
                .removePipelineReference(reference(removedLoader, null))
                .addPipelineReference(reference(hostLoader, null))
                .build());
        fixture.link(parent, mainXslt);
        fixture.link(child, parent);
    }

    @Test
    void aPipelinesLookupsAreLinkedToItsEffectiveLoaders() {
        final String result = run("{\"uuids\":[\"child\"]}");

        assertThat(result)
                .contains("Analysed 1 XSLT(s) in 1 pipeline(s), with 2 reference loader(s): 3 lookup(s)")
                .contains("stroom:lookup of map 'USER_TO_EMAIL' (resolved), key @user")
                .contains("resolved through: $emailMap: global xsl:variable at line 2 of included XSLT 'common'")
                .contains("map 'USER_TO_EMAIL' is written by: loader pipeline 'User loader' (user-loader), "
                          + "feed 'USER-REF', stream type 'Reference', XSLT 'user-loader-xslt' line 4 (definite)")
                .contains("NO LOADER writes map 'NO_SUCH_MAP'")
                .contains("CAN'T TELL which loader it uses")
                .contains("maps its loaders write that no XSLT in the pipeline looks up")
                .contains("map 'UNUSED_MAP' (resolved) written by loader pipeline 'Host loader'")
                .contains("1 document(s) weren't visible to you");
        // The loader the user can't view, and the one the child removes, aren't mentioned.
        assertThat(result)
                .doesNotContain("Secret loader")
                .doesNotContain("hidden-loader")
                .doesNotContain("Removed loader");
        // HOST_LONDON may be what the dynamic lookup uses, so it isn't flagged as unused.
        assertThat(result).doesNotContain("map 'HOST_LONDON' (resolved) written by");
    }

    @Test
    void outsideAChatTheXsltsAreReadAsSaved() {
        // Found reviewing the tools: offered over /mcp, it looked for the chat's tab on every XSLT, and failed.
        final XsltLookupsTool tool = new XsltLookupsTool(fixture.workspace, () -> fixture.dependencyService,
                () -> docFinder, fixture.documentAccess(), fixture.auditLog(), tabSnapshots);

        // As McpTools makes an MCP session's call: its workspace is its hidden chat's.
        final ToolCallContext overMcp = ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP,
                        "mcp-session-1", new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .workspace(new SessionWorkspace(fixture.workspace, 9))
                .userQuestions(UserQuestions.NONE)
                .build();

        final String result = tool.run(ToolTestFixture.request(XsltLookupsTool.NAME, "{\"uuids\":[\"main-xslt\"]}"),
                overMcp).text();

        assertThat(result).contains("used by pipeline(s) 'Parent pipeline' (parent), 'Child pipeline' (child)");
        Mockito.verifyNoInteractions(tabSnapshots);
    }

    @Test
    void anXsltsPipelinesAreFoundThroughTheGraphAndInheritance() {
        final String result = run("{\"uuids\":[\"main-xslt\"]}");

        assertThat(result)
                .contains("used by pipeline(s) 'Parent pipeline' (parent), 'Child pipeline' (child)")
                .contains("Removed loader")
                .contains("Host loader");
    }

    @Test
    void withoutLoadersOnlyTheLookupsAreReported() {
        final String result = run("{\"uuids\":[\"main-xslt\"],\"loaders\":false,\"map\":\"HOST_PARIS\"}");

        assertThat(result)
                .contains("1 lookup(s) that are, or may be, of map 'HOST_PARIS'")
                .contains("dynamic: HOST_… (from concat('HOST_', @site))")
                .doesNotContain("USER_TO_EMAIL")
                .doesNotContain("loader pipeline");
    }

    @Test
    void anXsltTheUserCantViewIsTreatedAsMissing() {
        xslt("hidden-xslt", "Hidden", MAIN_XSLT, false);

        final String hidden = run("{\"uuids\":[\"hidden-xslt\"]}");
        final String missing = run("{\"uuids\":[\"no-such-xslt\"]}");

        assertThat(hidden.replace("hidden-xslt", "X")).isEqualTo(missing.replace("no-such-xslt", "X"));
        assertThat(hidden).contains(DocumentLinksTool.NOT_FOUND).doesNotContain("Hidden");
        assertThat(fixture.logged(ViewEventAction.class)).isEmpty();
    }

    @Test
    void anIncludeTheUserCantViewIsReportedWithoutItsContent() {
        fixture.visible.remove("common-xslt");

        final String result = run("{\"uuids\":[\"main-xslt\"],\"loaders\":false}");

        assertThat(result)
                .contains("Included XSLTs that couldn't be read (not found, or not visible to you)")
                .contains("$emailMap: not defined here or in an XSLT this one includes")
                .doesNotContain("USER_TO_EMAIL");
    }

    @Test
    void eachDocumentReadIsLoggedInAViewEvent() {
        run("{\"uuids\":[\"child\"]}");

        final List<ViewEventAction> events = fixture.logged(ViewEventAction.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getObjects())
                .map(object -> ((OtherObject) object).getId())
                .contains("child", "parent", "main-xslt", "common-xslt", "user-loader", "user-loader-xslt",
                        "host-loader")
                .doesNotContain("hidden-loader", "hidden-loader-xslt");
    }

    @Test
    void anXsltWithUnsavedChangesInTheActiveTabIsReadAsEdited() {
        Mockito.when(tabSnapshots.unsavedPart(Mockito.eq(1), Mockito.eq(mainXslt),
                        Mockito.eq(AiTabSnapshotPart.DATA)))
                .thenReturn(Optional.of(new UnsavedPart(EDITED_XSLT, false, "/tabs/1/code.xsl")));

        final String result = run("{\"uuids\":[\"main-xslt\"],\"loaders\":false}");

        assertThat(result)
                .contains("XSLT 'Main XSLT' (main-xslt) in /Lookups, as edited in the tab the user attached (unsaved)")
                .contains("stroom:lookup of map 'EDITED_MAP' (resolved)")
                .doesNotContain("USER_TO_EMAIL");
    }

    @Test
    void theSelectedSteppingElementsCodeIsReadAsEdited() {
        final AiSteppingContext stepping = new AiSteppingContext("FEED", 1L, "Raw Events", null, 0L, 1L,
                "translate", List.of(new AiSteppingElement("translate", "XSLTFilter", mainXslt, true, true, 0, 0,
                List.of())), List.of());
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, parent, "/", "Parent pipeline",
                "Stepping", false, null, "hash", 0L, List.of(), List.of(), stepping);
        Mockito.when(tabSnapshots.forLatestQuestion(1)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(1, snapshot, AiTabSnapshotPart.STEP_CODE))
                .thenReturn(Optional.of(new UnsavedPart(EDITED_XSLT, false, "/tabs/1/code.xsl")));

        final String result = run("{\"uuids\":[\"main-xslt\"],\"loaders\":false}");

        assertThat(result)
                .contains("as edited in the tab the user attached (unsaved)")
                .contains("stroom:lookup of map 'EDITED_MAP' (resolved)");
    }

    @Test
    void unsavedTextCutShortIsNotUsed() {
        Mockito.when(tabSnapshots.unsavedPart(Mockito.eq(1), Mockito.eq(mainXslt),
                        Mockito.eq(AiTabSnapshotPart.DATA)))
                .thenReturn(Optional.of(new UnsavedPart(EDITED_XSLT, true, "/tabs/1/code.xsl")));

        final String result = run("{\"uuids\":[\"main-xslt\"],\"loaders\":false}");

        assertThat(result)
                .doesNotContain("unsaved")
                .doesNotContain("EDITED_MAP");
    }

    private String run(final String arguments) {
        final XsltLookupsTool tool = new XsltLookupsTool(fixture.workspace, () -> fixture.dependencyService,
                () -> docFinder, fixture.documentAccess(), fixture.auditLog(), tabSnapshots);
        return tool.run(ToolTestFixture.request(XsltLookupsTool.NAME, arguments), ToolCalls.inChat(1,
                fixture.workspace)).text();
    }

    private DocRef xslt(final String uuid, final String name, final String text, final boolean canView) {
        return fixture.add(new DocRef(XsltDoc.TYPE, uuid, name),
                XsltDoc.builder().uuid(uuid).name(name).data(text).build(), canView);
    }

    private DocRef loader(final String uuid,
                          final String name,
                          final String xsltUuid,
                          final String xsltText,
                          final boolean canView) {
        final DocRef loaderXslt = xslt(xsltUuid, xsltUuid, xsltText, canView);
        return pipeline(uuid, name, null, new PipelineDataBuilder()
                .addElement(new PipelineElement(ELEMENT, "XSLTFilter"))
                .addProperty(new PipelineProperty(ELEMENT, "xslt", new PipelinePropertyValue(loaderXslt)))
                .build(), canView);
    }

    private DocRef pipeline(final String uuid, final String name, final DocRef parentRef, final PipelineData data) {
        return pipeline(uuid, name, parentRef, data, true);
    }

    private DocRef pipeline(final String uuid,
                            final String name,
                            final DocRef parentRef,
                            final PipelineData data,
                            final boolean canView) {
        return fixture.add(new DocRef(PipelineDoc.TYPE, uuid, name), PipelineDoc.builder()
                .uuid(uuid)
                .name(name)
                .parentPipeline(parentRef)
                .pipelineData(data)
                .build(), canView);
    }

    private static PipelineReference reference(final DocRef loader, final DocRef feed) {
        return new PipelineReference(ELEMENT, "pipelineReference", loader, feed, "Reference");
    }
}
