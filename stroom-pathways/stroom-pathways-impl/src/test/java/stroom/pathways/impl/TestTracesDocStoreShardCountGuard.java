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
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.TraceSettings;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
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
        assertThat(true).isTrue();
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
}
