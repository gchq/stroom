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


package stroom.contentindex;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class TestLuceneContentIndex {

    @TempDir
    Path tempDir;

    private final Executor executor = Mockito.mock(Executor.class);

    @Test
    void anIndexNotYetBuiltIsNotReady() {
        final LuceneContentIndex index = index(tempDir.resolve("content_index"));

        assertThat(index.isReady()).isFalse();
        // Asking never starts building it.
        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void anIndexBeingBuiltIsNotReadyAndIsNotWaitedFor() throws IOException {
        final Path dir = tempDir.resolve("content_index");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("segments_1"), "x");
        // An index on disk is rebuilt in the background when the node starts, which this executor never runs.
        final LuceneContentIndex index = index(dir);
        Mockito.verify(executor).execute(Mockito.any());

        final long startNanos = System.nanoTime();
        assertThat(index.isReady()).isFalse();
        assertThat(index.isReady()).isFalse();
        // Well under the time a search waits for the index.
        assertThat(System.nanoTime() - startNanos).isLessThan(1_000_000_000L);
        Mockito.verifyNoMoreInteractions(executor);
    }

    private LuceneContentIndex index(final Path dir) {
        final PathCreator pathCreator = Mockito.mock(PathCreator.class);
        Mockito.when(pathCreator.toAppPath(Mockito.anyString())).thenReturn(dir);
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(executorProvider.get(Mockito.any())).thenReturn(executor);
        final ContentIndexConfig config = new ContentIndexConfig();
        return new LuceneContentIndex(Mockito.mock(TempDirProvider.class),
                pathCreator,
                () -> config,
                Set.of(),
                Mockito.mock(SecurityContext.class),
                Mockito.mock(TaskContextFactory.class),
                Mockito.mock(ExplorerNodeService.class),
                executorProvider,
                Mockito.mock(ClusterLockService.class),
                Mockito.mock(NodeInfo.class),
                Mockito.mock(ClusterNodeManager.class));
    }
}
