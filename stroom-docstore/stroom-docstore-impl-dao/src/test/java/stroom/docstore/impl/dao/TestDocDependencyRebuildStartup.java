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

package stroom.docstore.impl.dao;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyProvider;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.impl.dao.DocDependencyDao.DocVersion;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class TestDocDependencyRebuildStartup {

    private static final String PIPELINE_TYPE = "Pipeline";
    private static final String VIEW_TYPE = "View";

    private static final DocRef PIPELINE_1 = new DocRef(PIPELINE_TYPE, "pipeline-1-uuid", "Pipeline 1");
    private static final DocRef PIPELINE_2 = new DocRef(PIPELINE_TYPE, "pipeline-2-uuid", "Pipeline 2");
    private static final DocRef VIEW = new DocRef(VIEW_TYPE, "view-uuid", "View");
    private static final DocRef PARENT_PIPELINE = new DocRef(PIPELINE_TYPE, "parent-uuid", "Parent");

    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private DocDependencyDao docDependencyDao;
    @Mock
    private DocDependencyService docDependencyService;
    @Mock
    private DocDependencyProvider pipelineProvider;

    private DocDependencyRebuildStartup startup;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(pipelineProvider.getType()).thenReturn(PIPELINE_TYPE);
        startup = new DocDependencyRebuildStartup(
                clusterLockService,
                new MockSecurityContext(),
                docDependencyDao,
                docDependencyService,
                () -> Set.of(pipelineProvider));
    }

    @Test
    void testRun_lockHeldByAnotherNode() {
        // tryLock does not run the runnable when another node holds the lock

        startup.run();

        Mockito.verify(clusterLockService).tryLock(ArgumentMatchers.eq(DocDependencyRebuildStartup.LOCK_NAME),
                ArgumentMatchers.any());
        Mockito.verifyNoInteractions(docDependencyDao, docDependencyService, pipelineProvider);
    }

    @Test
    void testRun_nothingStale() {
        givenLockAcquired();
        Mockito.when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of());

        startup.run();

        Mockito.verify(docDependencyDao, Mockito.never()).setDependenciesIfUnchanged(ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any());
        Mockito.verifyNoInteractions(docDependencyService);
    }

    @Test
    void testRun_rebuildsStaleDocs() {
        givenLockAcquired();
        Mockito.when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(PIPELINE_1, "v1"),
                new DocVersion(PIPELINE_2, "v2")));
        Mockito.when(pipelineProvider.getDependencies(PIPELINE_1)).thenReturn(Set.of(PARENT_PIPELINE));
        Mockito.when(pipelineProvider.getDependencies(PIPELINE_2)).thenReturn(Set.of());
        Mockito.when(docDependencyDao.setDependenciesIfUnchanged(ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(true);

        startup.run();

        Mockito.verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_1, "v1", Set.of(PARENT_PIPELINE));
        Mockito.verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_2, "v2", Set.of());
        Mockito.verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_docChangedDuringRebuild() {
        givenLockAcquired();
        Mockito.when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(PIPELINE_1, "v1"),
                new DocVersion(PIPELINE_2, "v2")));
        Mockito.when(pipelineProvider.getDependencies(ArgumentMatchers.any())).thenReturn(Set.of(PARENT_PIPELINE));
        // The first doc was saved after it was found, so the guarded write is refused
        Mockito.when(docDependencyDao.setDependenciesIfUnchanged(ArgumentMatchers.eq(PIPELINE_1),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(false);
        Mockito.when(docDependencyDao.setDependenciesIfUnchanged(ArgumentMatchers.eq(PIPELINE_2),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(true);

        startup.run();

        Mockito.verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_2, "v2", Set.of(PARENT_PIPELINE));
        Mockito.verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_errorOnOneDocContinues() {
        givenLockAcquired();
        Mockito.when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(PIPELINE_1, "v1"),
                new DocVersion(PIPELINE_2, "v2")));
        Mockito.when(pipelineProvider.getDependencies(PIPELINE_1)).thenThrow(new DocumentNotFoundException(PIPELINE_1));
        Mockito.when(pipelineProvider.getDependencies(PIPELINE_2)).thenReturn(Set.of(PARENT_PIPELINE));
        Mockito.when(docDependencyDao.setDependenciesIfUnchanged(ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(true);

        startup.run();

        Mockito.verify(docDependencyDao, Mockito.never()).setDependenciesIfUnchanged(ArgumentMatchers.eq(PIPELINE_1),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any());
        Mockito.verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_2, "v2", Set.of(PARENT_PIPELINE));
        Mockito.verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_typeWithNoProviderIsSkipped() {
        givenLockAcquired();
        Mockito.when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(VIEW, "v1"),
                new DocVersion(PIPELINE_1, "v2")));
        Mockito.when(pipelineProvider.getDependencies(PIPELINE_1)).thenReturn(Set.of(PARENT_PIPELINE));
        Mockito.when(docDependencyDao.setDependenciesIfUnchanged(ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(true);

        startup.run();

        Mockito.verify(docDependencyDao, Mockito.never()).setDependenciesIfUnchanged(ArgumentMatchers.eq(VIEW),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any());
        Mockito.verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_1, "v2", Set.of(PARENT_PIPELINE));
    }

    private void givenLockAcquired() {
        Mockito.doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(clusterLockService).tryLock(ArgumentMatchers.eq(DocDependencyRebuildStartup.LOCK_NAME),
                ArgumentMatchers.any());
    }
}
