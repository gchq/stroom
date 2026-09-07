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

package stroom.pathways.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportState;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.fs.SharedFileStore;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.TraceSettings;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Provider;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A trace's bucket is located from the shard count, so changing it once data exists leaves that data
 * where nothing will look for it again. The guard that prevents this asks whether the store already
 * holds data — and what it does when it cannot find out is the part that matters.
 */
class TestTracesDocStoreShardCountGuard {

    private static final String UUID = "b1c2d3e4-5f60-4718-9a2b-3c4d5e6f7081";

    @TempDir
    Path tempDir;

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private Store<TracesDoc> store;
    @Mock
    private TracesDocSerialiser serialiser;
    @Mock
    private ClusterLockService clusterLockService;

    private TracesDocStoreImpl storeImpl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doReturn(store).when(storeFactory).createStore(any(), any(), any(), any(), any());
        final Provider<ClusterLockService> lockServiceProvider = () -> clusterLockService;
        storeImpl = new TracesDocStoreImpl(storeFactory, serialiser, lockServiceProvider);
    }

    private static TracesDoc doc(final int shardCount, final Path sharedPath) {
        return TracesDoc.tracesBuilder()
                .uuid(UUID)
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(shardCount, sharedPath.toString()))
                        .build())
                .build();
    }

    // Attempts to change the shard count from 4 to 8 against a store rooted at sharedRoot.
    private void changeShardCount(final Path sharedRoot) {
        final TracesDoc oldDoc = doc(4, sharedRoot);
        final TracesDoc newDoc = doc(8, sharedRoot);
        when(store.readDocument(any())).thenReturn(oldDoc);
        when(store.writeDocument(any())).thenReturn(newDoc);
        storeImpl.writeDocument(newDoc);
    }

    @Test
    void allowsTheChangeWhenTheStoreHasNoData() throws IOException {
        final Path sharedRoot = Files.createDirectories(tempDir.resolve("empty"));

        changeShardCount(sharedRoot);

        // Asserted on what reached the store rather than on the returned document, which is only
        // whatever the mock was told to hand back and would say nothing about the guard.
        final ArgumentCaptor<TracesDoc> written = ArgumentCaptor.forClass(TracesDoc.class);
        verify(store).writeDocument(written.capture());
        assertThat(SharedFileStore.shardCountOf(written.getValue())).isEqualTo(8);
    }

    /**
     * A document the node has never held has no shard count to preserve. The document store reports
     * that by throwing rather than by returning null, so the guard has to expect it — treating it as
     * a failure would refuse the save outright.
     */
    @Test
    void allowsASaveOfAStoreTheNodeHasNeverHeld() throws IOException {
        final Path sharedRoot = Files.createDirectories(tempDir.resolve("save-new"));
        final TracesDoc newDoc = doc(8, sharedRoot);
        when(store.readDocument(any()))
                .thenThrow(new DocumentNotFoundException(newDoc.asDocRef()));
        when(store.writeDocument(any())).thenReturn(newDoc);

        storeImpl.writeDocument(newDoc);

        final ArgumentCaptor<TracesDoc> written = ArgumentCaptor.forClass(TracesDoc.class);
        verify(store).writeDocument(written.capture());
        assertThat(SharedFileStore.shardCountOf(written.getValue())).isEqualTo(8);
    }

    @Test
    void refusesTheChangeWhenTheStoreHasData() throws IOException {
        final Path sharedRoot = Files.createDirectories(tempDir.resolve("populated"));
        Files.createDirectories(
                sharedRoot.resolve(PlanBConstants.ARCHIVE_DIR_NAME).resolve(UUID));

        assertThatThrownBy(() -> changeShardCount(sharedRoot))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("Cannot change shard count");
    }

    /**
     * The case the fix exists for. When the shared store cannot be read — here because permissions
     * deny it, standing in for the unreachable mount that happens in production — the guard has no
     * way to know whether data is there, and must refuse rather than assume the store is empty.
     */
    @Test
    void refusesTheChangeWhenItCannotTellWhetherDataExists() throws IOException {
        final Path sharedRoot = Files.createDirectories(tempDir.resolve("unreadable"));
        Files.createDirectories(sharedRoot.resolve(PlanBConstants.ARCHIVE_DIR_NAME));

        // Running as root defeats permission removal, so there would be nothing to observe.
        Assumptions.assumeThat(System.getProperty("user.name")).isNotEqualTo("root");
        sharedRoot.toFile().setReadable(false, false);
        sharedRoot.toFile().setExecutable(false, false);
        try {
            Assumptions.assumeThat(Files.isReadable(sharedRoot.resolve(PlanBConstants.ARCHIVE_DIR_NAME)))
                    .as("permissions took effect")
                    .isFalse();

            assertThatThrownBy(() -> changeShardCount(sharedRoot))
                    .as("an unreadable shared store must not be mistaken for an empty one")
                    .isInstanceOf(EntityServiceException.class)
                    .hasMessageContaining("Cannot change shard count");
        } finally {
            sharedRoot.toFile().setExecutable(true, false);
            sharedRoot.toFile().setReadable(true, false);
        }
    }

    // ---------------------------------------------------------------------
    // Import goes through the same guard, whether or not it replaces an existing document
    // ---------------------------------------------------------------------

    private DocRef importShardCountChange(final Path sharedRoot, final ImportState importState)
            throws IOException {
        final DocRef target = DocRef.builder().type(TracesDoc.TYPE).uuid(UUID).name("test_name").build();
        final TracesDoc oldDoc = doc(4, sharedRoot);
        final TracesDoc incoming = doc(8, sharedRoot);
        when(store.readDocument(any())).thenReturn(oldDoc);
        when(serialiser.read(any(ImportExportDocument.class))).thenReturn(incoming);
        when(store.importDocument(any(), any(), any(), any())).thenReturn(target);
        return storeImpl.importDocument(
                target, new ImportExportDocument(), importState, null);
    }

    @Test
    void importAllowsTheChangeWhenTheStoreHasNoData() throws IOException {
        final Path sharedRoot = Files.createDirectories(tempDir.resolve("import-empty"));
        final ImportState importState = new ImportState(
                DocRef.builder().type(TracesDoc.TYPE).uuid(UUID).build(), "test");

        importShardCountChange(sharedRoot, importState);

        verify(store).importDocument(any(), any(), any(), any());
        assertThat(importState.getMessageList()).isEmpty();
    }

    /**
     * The ordinary case for a content pack: the node has never held this store, so there is no shard
     * count to preserve. The document store reports that by throwing rather than by returning null,
     * so the guard has to expect it — treating it as a failure would refuse every first-time import.
     */
    @Test
    void importAllowsAStoreTheNodeHasNeverHeld() throws IOException {
        final Path sharedRoot = Files.createDirectories(tempDir.resolve("import-new"));
        final DocRef target = DocRef.builder().type(TracesDoc.TYPE).uuid(UUID).name("test_name").build();
        final ImportState importState = new ImportState(target, "test");

        when(store.readDocument(any()))
                .thenThrow(new DocumentNotFoundException(target));
        when(serialiser.read(any(ImportExportDocument.class))).thenReturn(doc(8, sharedRoot));
        when(store.importDocument(any(), any(), any(), any())).thenReturn(target);

        storeImpl.importDocument(target, new ImportExportDocument(), importState, null);

        verify(store).importDocument(any(), any(), any(), any());
        assertThat(importState.getMessageList()).isEmpty();
    }

    /**
     * An import that changes the shard count over a store holding data would leave every existing
     * bucket unreachable, exactly as a save would. Reported onto the import state rather than thrown,
     * which is how the import screen surfaces a refusal.
     */
    @Test
    void importRefusesTheChangeWhenTheStoreHasData() throws IOException {
        final Path sharedRoot = Files.createDirectories(tempDir.resolve("import-populated"));
        Files.createDirectories(sharedRoot.resolve(PlanBConstants.ARCHIVE_DIR_NAME).resolve(UUID));
        final ImportState importState = new ImportState(
                DocRef.builder().type(TracesDoc.TYPE).uuid(UUID).build(), "test");

        importShardCountChange(sharedRoot, importState);

        verify(store, never()).importDocument(any(), any(), any(), any());
        assertThat(importState.getMessageList())
                .anyMatch(m -> m.getMessage().contains("Cannot change shard count"));
    }
}
