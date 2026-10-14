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
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.security.api.SecurityContext;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Provider;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pathway's place is decided by the shared path and the shard count — the path is the root of the
 * model and the queue, the count is what the operation name is hashed against. Changing either once
 * anything has been written leaves it where nothing will look for it again, and the failure is silent:
 * a search still finds the name and then asks the wrong shard for it.
 *
 * <p>The editor locks both fields. These cover the check behind that, for an import or a direct API
 * call that never saw the screen.
 */
class TestPathwaysStoreSharedFileStoreGuard {

    private static final String UUID = "c37f9502-0614-4bb3-a237-cc5a6b60f500";

    @TempDir
    Path tempDir;

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private Store<PathwaysDoc> store;
    @Mock
    private PathwaysSerialiser serialiser;
    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private SecurityContext securityContext;

    private PathwaysStoreImpl storeImpl;
    private Path shared;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        doReturn(store).when(storeFactory).createStore(any(), any(), any(), any(), any());
        when(store.getType()).thenReturn(PathwaysDoc.TYPE);
        // These tests are about the guard, so the user always holds the permission.
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(true);
        final Provider<ClusterLockService> lockServiceProvider = () -> clusterLockService;
        // These tests are about the guard, which never looks for a traces store.
        storeImpl = new PathwaysStoreImpl(storeFactory, securityContext, serialiser, lockServiceProvider,
                () -> null);
        shared = Files.createDirectories(tempDir.resolve("pathways_shared"));
    }

    @Test
    void theShardCountMayChangeWhileNothingHasBeenWritten() {
        when(store.readDocument(any())).thenReturn(doc(4, shared));
        when(store.writeDocument(any())).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> storeImpl.writeDocument(doc(8, shared))).doesNotThrowAnyException();
        verify(store).writeDocument(any());
    }

    @Test
    void theShardCountIsPinnedOnceAModelExists() throws IOException {
        givenAModel();
        when(store.readDocument(any())).thenReturn(doc(4, shared));

        assertThatThrownBy(() -> storeImpl.writeDocument(doc(8, shared)))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("shard count");
        verify(store, never()).writeDocument(any());
    }

    @Test
    void theSharedPathIsPinnedOnceAModelExists() throws IOException {
        givenAModel();
        when(store.readDocument(any())).thenReturn(doc(4, shared));

        assertThatThrownBy(() -> storeImpl.writeDocument(doc(4, tempDir.resolve("somewhere_else"))))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("shared path");
        verify(store, never()).writeDocument(any());
    }

    @Test
    void aQueuedTraceIsEnoughToPinThemToo() throws IOException {
        // The queue holds traces already routed by the current shard count, so it counts as data even
        // before any of them has reached a model.
        Files.createDirectories(shared.resolve(PlanBConstants.QUEUE_DIR_NAME).resolve(UUID));
        when(store.readDocument(any())).thenReturn(doc(4, shared));

        assertThatThrownBy(() -> storeImpl.writeDocument(doc(8, shared)))
                .isInstanceOf(EntityServiceException.class);
    }

    @Test
    void everythingElseAboutTheDocumentStaysEditable() throws IOException {
        givenAModel();
        when(store.readDocument(any())).thenReturn(doc(4, shared));
        when(store.writeDocument(any())).thenAnswer(i -> i.getArgument(0));

        final PathwaysDoc renamed = doc(4, shared).copy().description("changed").build();

        assertThatCode(() -> storeImpl.writeDocument(renamed)).doesNotThrowAnyException();
    }

    @Test
    void theFlagSaysWhetherAnythingHasBeenWritten() throws IOException {
        when(store.readDocument(any())).thenReturn(doc(4, shared));
        assertThat(storeImpl.hasSharedFileStoreData(UUID)).isFalse();

        givenAModel();
        assertThat(storeImpl.hasSharedFileStoreData(UUID)).isTrue();
    }

    @Test
    void theChangeIsRefusedWhenItCannotTellWhetherDataExists() throws IOException {
        // "Could not tell" must not read as "no data": unlocking the settings on an unreachable mount
        // is how a model gets orphaned by a change that looked safe.
        final Path unreadable = Files.createDirectories(tempDir.resolve("unreadable"));
        Files.createDirectories(unreadable.resolve(PlanBConstants.SHARDS_DIR_NAME));

        // Running as root defeats permission removal, so there would be nothing to observe.
        Assumptions.assumeThat(System.getProperty("user.name")).isNotEqualTo("root");
        unreadable.toFile().setReadable(false, false);
        try {
            when(store.readDocument(any())).thenReturn(doc(4, unreadable));

            assertThatThrownBy(() -> storeImpl.writeDocument(doc(8, unreadable)))
                    .isInstanceOf(EntityServiceException.class);
        } finally {
            unreadable.toFile().setReadable(true, true);
        }
    }

    private void givenAModel() throws IOException {
        Files.createDirectories(shared.resolve(PlanBConstants.SHARDS_DIR_NAME).resolve(UUID));
    }

    private static PathwaysDoc doc(final int shardCount, final Path sharedPath) {
        return PathwaysDoc.builder()
                .uuid(UUID)
                .name("Test Pathways")
                .sharedFileStore(new SharedFileStoreSettings(shardCount, sharedPath.toString()))
                .build();
    }
}
