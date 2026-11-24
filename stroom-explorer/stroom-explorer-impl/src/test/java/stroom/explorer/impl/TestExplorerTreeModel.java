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

package stroom.explorer.impl;

import stroom.docstore.api.DocDependencyService;
import stroom.explorer.api.ExplorerDecorator;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestExplorerTreeModel {

    @Mock
    private ExplorerTreeDao mockExplorerTreeDao;
    @Mock
    private ExplorerSession mockExplorerSession;
    @Mock
    private Executor mockExecutor;
    @Mock
    private TaskContextFactory mockTaskContextFactory;
    @Mock
    private DocDependencyService mockDocDependencyService;
    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ExplorerDecorator mockExplorerDecorator;

    private ExplorerTreeModel explorerTreeModel;

    @BeforeEach
    void setUp() {
        when(mockExplorerSession.getMinExplorerTreeModelId())
                .thenReturn(Optional.empty());
        when(mockSecurityContext.asProcessingUserResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        when(mockExplorerTreeDao.createModel(anyLong(), anyLong()))
                .thenAnswer(invocation -> new TreeModel(
                        invocation.getArgument(0),
                        invocation.getArgument(1)));

        explorerTreeModel = new ExplorerTreeModel(
                mockExplorerTreeDao,
                mockExplorerSession,
                mockExecutor,
                mockTaskContextFactory,
                mockDocDependencyService,
                mockSecurityContext,
                ExplorerConfig::new,
                mockExplorerDecorator);
    }

    @Test
    void testGetModel_reusesCachedModel() {
        final UnmodifiableTreeModel model1 = explorerTreeModel.getModel();
        final UnmodifiableTreeModel model2 = explorerTreeModel.getModel();

        assertThat(model2)
                .isSameAs(model1);
        verify(mockExplorerTreeDao, times(1))
                .createModel(anyLong(), anyLong());
    }

    @Test
    void testRebuildAndGetModel_rebuildsCachedModel() {
        // E.g. a change made on another node whose entity event has not yet arrived here,
        // so nothing has told this node that its cached model is stale.
        final UnmodifiableTreeModel cachedModel = explorerTreeModel.getModel();

        final UnmodifiableTreeModel rebuiltModel = explorerTreeModel.rebuildAndGetModel();

        assertThat(rebuiltModel)
                .isNotSameAs(cachedModel);
        assertThat(rebuiltModel.getId())
                .isGreaterThan(cachedModel.getId());
        verify(mockExplorerTreeDao, times(2))
                .createModel(anyLong(), anyLong());

        // The rebuilt model must become the one served to everyone else.
        assertThat(explorerTreeModel.getModel())
                .isSameAs(rebuiltModel);
    }

    @Test
    void testRebuildAndGetModel_noCurrentModel() {
        final UnmodifiableTreeModel rebuiltModel = explorerTreeModel.rebuildAndGetModel();

        assertThat(rebuiltModel)
                .isNotNull();
        assertThat(explorerTreeModel.getModel())
                .isSameAs(rebuiltModel);
        verify(mockExplorerTreeDao, times(1))
                .createModel(anyLong(), anyLong());
    }
}
