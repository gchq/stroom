/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.TraceSettings;
import stroom.security.api.SecurityContext;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Provider;
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

class TestPlanBDocStore {

    @TempDir
    Path tempDir;

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private Store<PlanBDoc> store;
    @Mock
    private PlanBDocSerialiser serialiser;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ClusterLockService clusterLockService;

    private StatePaths statePaths;
    private PlanBDocStoreImpl storeImpl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doReturn(store).when(storeFactory).createStore(any(), any(), any(), any());

        statePaths = new StatePaths(tempDir.resolve("local_state"));
        final Provider<StatePaths> statePathsProvider = () -> statePaths;
        final Provider<ClusterLockService> lockServiceProvider = () -> clusterLockService;

        storeImpl = new PlanBDocStoreImpl(
                storeFactory,
                serialiser,
                securityContext,
                statePathsProvider,
                lockServiceProvider);
    }

    @Test
    void testWriteDocument_noOldDoc() {
        final PlanBDoc doc = PlanBDoc.builder()
                .uuid("test-uuid")
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(5, null))
                        .build())
                .build();

        when(store.readDocument(any())).thenReturn(null);
        when(store.writeDocument(any())).thenReturn(doc);

        final PlanBDoc result = storeImpl.writeDocument(doc);
        assertThat(result).isNotNull();
    }

    @Test
    void testWriteDocument_sameShardCount() {
        final PlanBDoc oldDoc = PlanBDoc.builder()
                .uuid("test-uuid")
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(5, null))
                        .build())
                .build();
        final PlanBDoc newDoc = PlanBDoc.builder()
                .uuid("test-uuid")
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(5, null))
                        .build())
                .build();

        when(store.readDocument(any())).thenReturn(oldDoc);
        when(store.writeDocument(any())).thenReturn(newDoc);

        final PlanBDoc result = storeImpl.writeDocument(newDoc);
        assertThat(result).isNotNull();
    }

    @Test
    void testWriteDocument_changeShardCount_noData() {
        final PlanBDoc oldDoc = PlanBDoc.builder()
                .uuid("test-uuid")
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(5, tempDir.resolve("shared").toString()))
                        .build())
                .build();
        final PlanBDoc newDoc = PlanBDoc.builder()
                .uuid("test-uuid")
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(6, tempDir.resolve("shared").toString()))
                        .build())
                .build();

        when(store.readDocument(any())).thenReturn(oldDoc);
        when(store.writeDocument(any())).thenReturn(newDoc);

        final PlanBDoc result = storeImpl.writeDocument(newDoc);
        assertThat(result).isNotNull();
    }

    @Test
    void testWriteDocument_changeShardCount_withSharedData_processing() throws IOException {
        final Path sharedPath = tempDir.resolve("shared");
        final String uuid = "test-uuid";

        final PlanBDoc oldDoc = PlanBDoc.builder()
                .uuid(uuid)
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(5, sharedPath.toString()))
                        .build())
                .build();
        final PlanBDoc newDoc = PlanBDoc.builder()
                .uuid(uuid)
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(6, sharedPath.toString()))
                        .build())
                .build();

        // Create the processing directory for the UUID to simulate that data was written
        Files.createDirectories(sharedPath.resolve("processing").resolve(uuid));

        when(store.readDocument(any())).thenReturn(oldDoc);

        assertThatThrownBy(() -> storeImpl.writeDocument(newDoc))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("Cannot change shard count: data has already been written");
    }

    @Test
    void testWriteDocument_changeShardCount_withLocalData() throws IOException {
        final String uuid = "test-uuid";

        final PlanBDoc oldDoc = PlanBDoc.builder()
                .uuid(uuid)
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(5, null))
                        .build())
                .build();
        final PlanBDoc newDoc = PlanBDoc.builder()
                .uuid(uuid)
                .name("test_name")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(6, null))
                        .build())
                .build();

        // Create the local shard directory for the UUID to simulate local data
        final Path shardDir = statePaths.getShardDir();
        Files.createDirectories(shardDir);
        Files.createFile(shardDir.resolve(uuid + "_some_shard_data"));

        when(store.readDocument(any())).thenReturn(oldDoc);

        assertThatThrownBy(() -> storeImpl.writeDocument(newDoc))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("Cannot change shard count: data has already been written");
    }
}
