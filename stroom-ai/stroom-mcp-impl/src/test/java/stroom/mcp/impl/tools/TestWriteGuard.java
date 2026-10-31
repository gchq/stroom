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

import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// The write guard, on an in-memory explorer.
class TestWriteGuard {

    private static final ToolSession SESSION = new ToolSession(ToolSession.Kind.MCP, "s1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));
    private static final String WORKSPACE = "System/" + BuildWorld.WORKSPACE;

    private final BuildWorld world = new BuildWorld();
    private final WriteGuard guard = world.guard();

    @Test
    void foldersAreMadeUnderAFixedSetOfLocks() {
        // Before, a lock was kept for every parent and name a folder was ever made with, never removed.
        final Set<Object> locks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 1_000; i++) {
            locks.add(guard.folderLock("parent", "folder " + i));
            assertThat(guard.folderLock("parent", "folder " + i)).isSameAs(guard.folderLock("parent", "folder " + i));
        }
        assertThat(locks).hasSizeLessThanOrEqualTo(WriteGuard.FOLDER_LOCKS);
    }

    @Test
    void refusalsAreAudited() {
        final DocRef generatedOnly = world.add(XsltDoc.TYPE, "ACME-VPN-Events-V1.0", "0", "x-1",
                WriteGuard.GENERATED).docRef();
        assertThatThrownBy(() -> guard.checkManaged(SESSION, generatedOnly))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("XSLT 'ACME-VPN-Events-V1.0' was not created by this server. Make a working copy in a "
                            + "build (copy_pipeline) and change that instead; promote_build writes it back after "
                            + "approval.");
        Mockito.verify(world.auditLog).processed(eq(SESSION), eq(generatedOnly), eq("access_denied"),
                eq("not_managed"), anyString(), eq(false));

        final DocRef untagged = world.add(XsltDoc.TYPE, "Prod", "0", "x-2").docRef();
        assertThatThrownBy(() -> guard.checkBuilt(SESSION, untagged))
                .hasMessage("XSLT 'Prod' was not built by this server");
        Mockito.verify(world.auditLog).processed(eq(SESSION), eq(untagged), eq("access_denied"), eq("not_built"),
                anyString(), eq(false));
    }

    @Test
    void managedDocsPassWithoutAnAuditEvent() {
        final DocRef xslt = world.inBuild("acme-v1", XsltDoc.TYPE, "Acme", "x");
        assertThat(guard.checkManaged(SESSION, xslt)).contains(WriteGuard.MANAGED, WriteGuard.GENERATED);
        assertThat(guard.checkBuilt(SESSION, xslt)).contains(WriteGuard.MANAGED);
        assertThat(guard.checkInBuild(SESSION, xslt)).isEqualTo("acme-v1");
        Mockito.verify(world.auditLog, Mockito.never()).processed(any(), any(), anyString(), anyString(),
                anyString(), Mockito.anyBoolean());
    }

    @Test
    void aManagedDocMovedOutOfItsBuildIsChangedOnlyInABuild() {
        final DocRef pipeline = world.inBuild("acme-v1", PipelineDoc.TYPE, "Acme", "p");
        world.move("p", world.folder("System/Feeds").uuid);
        assertThatThrownBy(() -> guard.checkInBuild(SESSION, pipeline))
                .hasMessage("Pipeline 'Acme' is not in a build folder: it is changed only in a build. Make a "
                            + "working copy in one (copy_pipeline), or move it back into its build folder in "
                            + "Stroom.");
        Mockito.verify(world.auditLog).processed(eq(SESSION), eq(pipeline), eq("access_denied"), eq("not_in_build"),
                anyString(), eq(false));
    }

    @Test
    void resolveFolderFindsWhatExistsAndNamesWhatIsMissing() {
        world.folder("System/Feeds/Events");
        final WriteGuard.ResolvedFolder deep = guard.resolveFolder("System / Feeds/Events/Acme/V1/");
        assertThat(deep.folder().path()).isEqualTo("System/Feeds/Events");
        assertThat(deep.missing()).containsExactly("Acme", "V1");
        final WriteGuard.ResolvedFolder all = guard.resolveFolder("System/Feeds");
        assertThat(all.folder().path()).isEqualTo("System/Feeds");
        assertThat(all.missing()).isEmpty();
        assertThatThrownBy(() -> guard.resolveFolder(" / ")).hasMessageContaining("is no folder");
    }

    @Test
    void aPathWithoutSystemIsUnderSystemTheExplorersOneRoot() {
        // Seen (Delinea SecretServer): 'Feeds/Test/...' and '/Feeds/Test/...' refused for want of System/, though
        // Stroom's own folder tools take '/Feeds/Test' and refuse find_documents' 'System / Feeds / Test'.
        world.folder("System/Feeds/Test");
        for (final String given : List.of("Feeds/Test/Delinea", "/Feeds/Test/Delinea",
                "System / Feeds / Test / Delinea", "/System/Feeds/Test/Delinea")) {
            final WriteGuard.ResolvedFolder resolved = guard.resolveFolder(given);
            assertThat(resolved.folder().path()).as(given).isEqualTo("System/Feeds/Test");
            assertThat(resolved.missing()).as(given).containsExactly("Delinea");
        }
        assertThat(WriteGuard.folderParts("/Feeds")).containsExactly("System", "Feeds");
        assertThat(WriteGuard.folderParts("System")).containsExactly("System");
        assertThat(WriteGuard.explorerPath("System / Feeds / Test")).isEqualTo("/Feeds/Test");
        assertThat(WriteGuard.explorerPath("System")).isEqualTo("/");
    }

    @Test
    void aFolderAlreadyThereIsUsedNotTwinned() {
        // Stroom lets a parent hold several folders of the same name.
        final BuildWorld.Node events = world.folder("System/Feeds/Events");
        final WriteGuard.Folder feeds = guard.resolveFolder("System/Feeds").folder();
        final WriteGuard.Folder found = guard.createFolder(feeds, "Events", Set.of(WriteGuard.GENERATED));
        assertThat(found.uuid()).isEqualTo(events.uuid);
        assertThat(found.created()).isFalse();
        assertThat(world.created).isEmpty();
        Mockito.verify(world.explorerService, Mockito.never()).addTags(any(), any());

        final WriteGuard.Folder acme = guard.createFolder(feeds, "Acme", Set.of(WriteGuard.GENERATED));
        assertThat(acme.created()).isTrue();
        assertThat(acme.path()).isEqualTo("System/Feeds/Acme");
        assertThat(world.tags(acme.uuid())).containsExactly(WriteGuard.GENERATED);
    }

    @Test
    void foldersAskedForAtOnceAreMadeOnce() throws Exception {
        // Two calls at once (an agent's parallel tool calls) each found no folder and each made one.
        world.folder(WORKSPACE);
        final CountDownLatch start = new CountDownLatch(1);
        final CompletableFuture<WriteGuard.Folder> one = CompletableFuture.supplyAsync(() -> awaitThen(start));
        final CompletableFuture<WriteGuard.Folder> two = CompletableFuture.supplyAsync(() -> awaitThen(start));
        start.countDown();
        assertThat(one.get().uuid()).isEqualTo(two.get().uuid());
        assertThat(world.created).containsExactly("Folder:acme-v1");
    }

    @Test
    void twoDocsMadeOneAfterTheOtherShareOneBuildFolder() {
        // Seen in the translation suite: copy_pipeline made a pipeline and its XSLT in a new build, and the second
        // doc made a twin folder of the same name.
        world.folder(WORKSPACE);
        guard.create(PipelineDoc.TYPE, "ACME-Events-WORKING", "acme-fix", null, false, null);
        guard.create(XsltDoc.TYPE, "ACME-Events-WORKING", "acme-fix", null, false, null);
        assertThat(world.created.stream().filter(made -> made.startsWith("Folder:")).toList())
                .containsExactly("Folder:acme-fix");
    }

    @Test
    void aDocumentWhoseFillingFailsIsDeletedNotLeftEmpty() {
        assertThatThrownBy(() -> guard.createFilled(DocumentationDoc.TYPE, "ACME-VPN-Events", "acme-v1", ref -> {
            throw new ToolRefusal("Stroom rejected the request (500)");
        }, false, null)).hasMessageContaining("500");
        assertThat(guard.folderContents("acme-v1")).isEmpty();

        final String filled = guard.createFilled(DocumentationDoc.TYPE, "ACME-VPN-Events", "acme-v1",
                ref -> "# ACME", false, null);
        assertThat(filled).isEqualTo("# ACME");
        assertThat(guard.folderContents("acme-v1")).extracting(WriteGuard.BuildDoc::name)
                .containsExactly("ACME-VPN-Events");
    }

    @Test
    void aBuildFolderIsRemovedOnlyWhenCompletelyEmpty() {
        // Empty: removed.
        world.folder(WORKSPACE + "/empty");
        assertThat(guard.removeBuildFolderIfEmpty("empty")).isTrue();
        assertThat(guard.buildFolder("empty", false)).isEmpty();
        // Only empty role subfolders: removed with them.
        world.folder(WORKSPACE + "/roles/Events");
        assertThat(guard.removeBuildFolderIfEmpty("roles")).isTrue();
        // A subfolder of its own is left: kept.
        world.folder(WORKSPACE + "/kept/Notes");
        assertThat(guard.removeBuildFolderIfEmpty("kept")).isFalse();
        // A doc the user can't see would go with it: kept.
        final BuildWorld.Node secret = world.add(XsltDoc.TYPE, "Secret", world.folder(WORKSPACE + "/hidden").uuid,
                "secret");
        world.hidden.add(secret.uuid);
        assertThat(guard.removeBuildFolderIfEmpty("hidden")).isFalse();
        // Docs are left: kept.
        world.inBuild("full", XsltDoc.TYPE, "Acme", "x");
        assertThat(guard.removeBuildFolderIfEmpty("full")).isFalse();
        // No folder at all.
        assertThat(guard.removeBuildFolderIfEmpty("none")).isFalse();
    }

    @Test
    void readingABuildDoesNotCreateItsFolder() {
        world.folder(WORKSPACE);
        assertThat(guard.folderContents("acme-v1")).isEmpty();
        assertThat(world.created).isEmpty();
        // Writes still do.
        assertThat(guard.buildFolder("acme-v1").path()).isEqualTo(WORKSPACE + "/acme-v1");
        assertThat(world.created).containsExactly("Folder:acme-v1");
    }

    @Test
    void theWorkspaceAndBuildFoldersAreTaggedAsTheTools() {
        final WriteGuard.Folder folder = guard.buildFolder("acme-v1");
        assertThat(world.tags(folder.uuid())).containsExactlyInAnyOrder(WriteGuard.MANAGED, WriteGuard.GENERATED);
        assertThat(guard.findChildFolder(guard.systemFolder(), BuildWorld.WORKSPACE)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "-acme", "acme v1", "acme/v1"})
    void buildNamesAreChecked(final String build) {
        assertThatThrownBy(() -> guard.buildFolder(build))
                .hasMessage("Build names are 2 to 64 letters, digits, '.', '_' or '-', e.g. 'keycloak-v1.3'");
    }

    @Test
    void theRecordIsLeftOutOfTheBuildsDocumentsUnlessAskedFor() {
        world.inBuild("b1", XsltDoc.TYPE, "X", "x");
        world.inBuild("b1", DocumentationDoc.TYPE, BuildRecords.NAME, "r");
        world.inBuild("b2", PipelineDoc.TYPE, "B", "b");
        assertThat(guard.folderContents("b1")).extracting(WriteGuard.BuildDoc::uuid).containsExactly("x");
        assertThat(guard.folderContents("b1", true)).extracting(WriteGuard.BuildDoc::uuid)
                .containsExactly("x", "r");
    }

    @Test
    void aRoleSubfoldersDocumentsAreInTheBuild() {
        final DocRef xslt = world.inBuild("b1", XsltDoc.TYPE, "X", "x");
        world.inBuild("b1", PipelineDoc.TYPE, "P", "p");
        assertThat(guard.moveToRole("b1", xslt, "Events")).isTrue();
        assertThat(guard.moveToRole("b1", xslt, "Events")).isFalse();
        final WriteGuard.BuildDoc moved = guard.folderContents("b1").stream()
                .filter(doc -> doc.uuid().equals("x"))
                .findFirst()
                .orElseThrow();
        assertThat(moved.role()).isEqualTo("Events");
        assertThat(moved.path()).isEqualTo(WORKSPACE + "/b1/Events");
        assertThat(guard.buildOf(xslt)).contains("b1");
        assertThatThrownBy(() -> guard.roleFolder("b1", "Other"))
                .hasMessage("Unknown role 'Other': one of Events, Records, Indexing, CEF, Reference");
    }

    @Test
    void aDocumentsBuildIsTheFolderItIsIn() {
        // Not a tag: the user asked for only mcp-managed and mcp-generated on documents, so the tag list doesn't
        // grow with every build.
        final DocRef made = guard.create(XsltDoc.TYPE, "Acme", "acme-v1", null, false, null);
        assertThat(world.tags(made.getUuid())).containsExactlyInAnyOrder(WriteGuard.MANAGED, WriteGuard.GENERATED);
        assertThat(guard.buildOf(made)).contains("acme-v1");
        final DocRef elsewhere = world.add(XsltDoc.TYPE, "Prod", world.folder("System/Feeds").uuid, null)
                .docRef();
        assertThat(guard.buildOf(elsewhere)).isEmpty();
        assertThat(guard.buildOf(new DocRef(XsltDoc.TYPE, "gone", "Gone"))).isEmpty();
        final DocRef copied = guard.create(XsltDoc.TYPE, "Prod", "acme-v1", elsewhere.getUuid(), false, null);
        assertThat(guard.copyOf(copied)).contains(elsewhere.getUuid());
        assertThat(guard.copyOf(made)).isEmpty();
        assertThat(world.tags(copied.getUuid())).containsExactlyInAnyOrder(WriteGuard.MANAGED, WriteGuard.GENERATED);
    }

    @Test
    void aDocumentTheUserCantViewIsInNoBuild() {
        final DocRef xslt = world.inBuild("acme-v1", XsltDoc.TYPE, "Acme", "x");
        world.hidden.add("x");
        assertThat(guard.buildOf(xslt)).isEmpty();
        assertThat(guard.tags(xslt)).isEmpty();
    }

    @Test
    void aNameTheBuildAlreadyHasIsNotCreatedAgain() {
        // Seen: after Copilot summarised a long conversation, the agent created the same indexing pipeline twice.
        world.inBuild("b1", PipelineDoc.TYPE, "Firewall Indexing", "p1");
        assertThatThrownBy(() -> guard.create(PipelineDoc.TYPE, "Firewall Indexing", "b1", null, false, null))
                .hasMessage("A Pipeline named 'Firewall Indexing' is already in build b1 (uuid p1): carry on with "
                            + "that one (build_status lists the build's documents), or give the new one another "
                            + "name.");
        assertThat(world.created).isEmpty();
        // Another type may share the name.
        assertThat(guard.create(XsltDoc.TYPE, "Firewall Indexing", "b1", null, false, null).getType())
                .isEqualTo(XsltDoc.TYPE);
    }

    @Test
    void theRecordNameIsTheServersOwn() {
        assertThatThrownBy(() -> guard.create(DocumentationDoc.TYPE, BuildRecords.NAME, "b1", null, false, null))
                .hasMessage("'Build record' is the server's own record of the build: give the Documentation "
                            + "another name");
    }

    @Test
    void aDocumentMadeInARoleGoesInItsSubfolder() {
        final DocRef made = guard.create(PipelineDoc.TYPE, "Acme-Events", "b1", null, false, "Events");
        assertThat(guard.folderContents("b1")).singleElement()
                .satisfies(doc -> {
                    assertThat(doc.uuid()).isEqualTo(made.getUuid());
                    assertThat(doc.role()).isEqualTo("Events");
                });
        assertThat(world.nodes.get(world.nodes.get(made.getUuid()).parent).name).isEqualTo("Events");
        assertThat(ExplorerConstants.SYSTEM).isEqualTo(guard.systemFolder().path());
    }

    private WriteGuard.Folder awaitThen(final CountDownLatch start) {
        try {
            start.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return guard.buildFolder("acme-v1");
    }
}
