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
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.TraceSettings;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may do what to a trace store.
 *
 * <p>{@link TracesDocStoreImpl} extends {@code AbstractDocumentStore}, which is where the
 * authorisation contract for a document type lives — the {@link Store} beneath is a persistence layer
 * that does as it is asked. These check that every way into the document goes through that contract,
 * including the two the base class deliberately leaves open and the one place that bypasses it on
 * purpose.
 */
class TestTracesDocStorePermissions {

    private static final String UUID = "6a0f2c11-9a34-4a1e-8f77-2b05c3d4e5f6";

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
    @Mock
    private SecurityContext securityContext;

    private TracesDocStoreImpl storeImpl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doReturn(store).when(storeFactory).createStore(any(), any(), any(), any(), any());
        // AbstractDocumentStore asks the store for the type when authorising a write.
        when(store.getType()).thenReturn(TracesDoc.TYPE);
        final Provider<ClusterLockService> lockServiceProvider = () -> clusterLockService;
        storeImpl = new TracesDocStoreImpl(storeFactory, securityContext, serialiser, lockServiceProvider);
    }

    @Test
    void writeIsRefusedWithoutEdit() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> storeImpl.writeDocument(doc()))
                .isInstanceOf(PermissionException.class);
        verify(store, never()).writeDocument(any());
    }

    @Test
    void writeIsRefusedWithViewOnly() {
        when(securityContext.hasDocumentPermission(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1) == DocumentPermission.VIEW);

        assertThatThrownBy(() -> storeImpl.writeDocument(doc()))
                .isInstanceOf(PermissionException.class);
        verify(store, never()).writeDocument(any());
    }

    @Test
    void writeIsAllowedWithEdit() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(true);
        final TracesDoc doc = doc();
        when(store.readDocument(any())).thenReturn(null);
        when(store.writeDocument(any())).thenReturn(doc);

        storeImpl.writeDocument(doc);

        verify(store).writeDocument(any());
    }

    @Test
    void deleteIsRefusedWithoutDelete() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> storeImpl.deleteDocument(docRef()))
                .isInstanceOf(PermissionException.class);
        verify(store, never()).deleteDocument(any());
    }

    @Test
    void readIsRefusedWithoutView() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);
        when(store.readDocument(any())).thenReturn(doc());

        assertThatThrownBy(() -> storeImpl.readDocument(docRef()))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void readIsAllowedWithView() {
        when(securityContext.hasDocumentPermission(any(), eq(DocumentPermission.VIEW))).thenReturn(true);
        when(store.readDocument(any())).thenReturn(doc());

        assertThat(storeImpl.readDocument(docRef())).isNotNull();
    }

    @Test
    void renameIsRefusedWithoutEdit() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> storeImpl.renameDocument(docRef(), "new_name"))
                .isInstanceOf(PermissionException.class);
        verify(store, never()).renameDocument(any(), any());
    }

    @Test
    void copyIsRefusedWithoutView() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> storeImpl.copyDocument(docRef(), "copy", true, Set.of()))
                .isInstanceOf(PermissionException.class);
        verify(store, never()).copyDocument(any(), any());
    }

    @Test
    void moveIsRefusedWithoutView() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> storeImpl.moveDocument(docRef()))
                .isInstanceOf(PermissionException.class);
        verify(store, never()).moveDocument(any());
    }

    @Test
    void remapIsRefusedWithoutEdit() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> storeImpl.remapDependencies(docRef(), Map.of()))
                .isInstanceOf(PermissionException.class);
        verify(store, never()).remapDependencies(any(), any());
    }

    @Test
    void exportIsRefusedWithoutView() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> storeImpl.exportDocument(docRef(), true, new ArrayList<>()))
                .isInstanceOf(PermissionException.class);
    }

    /**
     * The housekeeping sweep must see every trace store, whoever is asking.
     *
     * <p>{@code SharedFileStoreCleaner} trashes any shared path missing from this answer, so filtering
     * it by what the caller may view would delete live data. This is the one place that reaches past
     * the contract on purpose, so it is the one that most needs a test saying so.
     */
    @Test
    void housekeepingSeesEveryStoreEvenWithNoPermissions() {
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);
        when(store.list()).thenReturn(List.of(docRef()));
        when(store.readDocument(any())).thenReturn(doc());

        assertThat(storeImpl.getLiveSharedPathData().values())
                .as("a caller who may see nothing still gets the whole live set")
                .containsExactly(Set.of(UUID));
    }

    private static DocRef docRef() {
        return DocRef.builder()
                .type(TracesDoc.TYPE)
                .uuid(UUID)
                .name("test_name")
                .build();
    }

    private TracesDoc doc() {
        return TracesDoc.tracesBuilder()
                .uuid(UUID)
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(
                                1, tempDir.resolve("shared").toString()))
                        .build())
                .build();
    }
}
