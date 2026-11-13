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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        lenient().when(pipelineProvider.getType()).thenReturn(PIPELINE_TYPE);
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

        verify(clusterLockService).tryLock(eq(DocDependencyRebuildStartup.LOCK_NAME), any());
        verifyNoInteractions(docDependencyDao, docDependencyService, pipelineProvider);
    }

    @Test
    void testRun_nothingStale() {
        givenLockAcquired();
        when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of());

        startup.run();

        verify(docDependencyDao, never()).setDependenciesIfUnchanged(any(), anyString(), any());
        verifyNoInteractions(docDependencyService);
    }

    @Test
    void testRun_rebuildsStaleDocs() {
        givenLockAcquired();
        when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(PIPELINE_1, "v1"),
                new DocVersion(PIPELINE_2, "v2")));
        when(pipelineProvider.getDependencies(PIPELINE_1)).thenReturn(Set.of(PARENT_PIPELINE));
        when(pipelineProvider.getDependencies(PIPELINE_2)).thenReturn(Set.of());
        when(docDependencyDao.setDependenciesIfUnchanged(any(), anyString(), any())).thenReturn(true);

        startup.run();

        verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_1, "v1", Set.of(PARENT_PIPELINE));
        verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_2, "v2", Set.of());
        verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_docChangedDuringRebuild() {
        givenLockAcquired();
        when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(PIPELINE_1, "v1"),
                new DocVersion(PIPELINE_2, "v2")));
        when(pipelineProvider.getDependencies(any())).thenReturn(Set.of(PARENT_PIPELINE));
        // The first doc was saved after it was found, so the guarded write is refused
        when(docDependencyDao.setDependenciesIfUnchanged(eq(PIPELINE_1), anyString(), any())).thenReturn(false);
        when(docDependencyDao.setDependenciesIfUnchanged(eq(PIPELINE_2), anyString(), any())).thenReturn(true);

        startup.run();

        verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_2, "v2", Set.of(PARENT_PIPELINE));
        verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_errorOnOneDocContinues() {
        givenLockAcquired();
        when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(PIPELINE_1, "v1"),
                new DocVersion(PIPELINE_2, "v2")));
        when(pipelineProvider.getDependencies(PIPELINE_1)).thenThrow(new DocumentNotFoundException(PIPELINE_1));
        when(pipelineProvider.getDependencies(PIPELINE_2)).thenReturn(Set.of(PARENT_PIPELINE));
        when(docDependencyDao.setDependenciesIfUnchanged(any(), anyString(), any())).thenReturn(true);

        startup.run();

        verify(docDependencyDao, never()).setDependenciesIfUnchanged(eq(PIPELINE_1), anyString(), any());
        verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_2, "v2", Set.of(PARENT_PIPELINE));
        verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_typeWithNoProviderIsSkipped() {
        givenLockAcquired();
        when(docDependencyDao.findDocsWithStaleDependencies()).thenReturn(List.of(
                new DocVersion(VIEW, "v1"),
                new DocVersion(PIPELINE_1, "v2")));
        when(pipelineProvider.getDependencies(PIPELINE_1)).thenReturn(Set.of(PARENT_PIPELINE));
        when(docDependencyDao.setDependenciesIfUnchanged(any(), anyString(), any())).thenReturn(true);

        startup.run();

        verify(docDependencyDao, never()).setDependenciesIfUnchanged(eq(VIEW), anyString(), any());
        verify(docDependencyDao).setDependenciesIfUnchanged(PIPELINE_1, "v2", Set.of(PARENT_PIPELINE));
    }

    private void givenLockAcquired() {
        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(clusterLockService).tryLock(eq(DocDependencyRebuildStartup.LOCK_NAME), any());
    }
}
