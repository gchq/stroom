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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests the `reset` endpoint of [IndexResourceImpl], which deletes every shard of an index.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestIndexResourceImpl {

    private static final String INDEX_UUID = "index-uuid";
    private static final String THIS_NODE = "node1a";
    private static final DocRef INDEX_DOC_REF = DocRef.builder()
            .type(LuceneIndexDoc.TYPE)
            .uuid(INDEX_UUID)
            .build();

    @Mock
    private IndexShardService indexShardService;
    @Mock
    private IndexShardManager indexShardManager;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private SecurityContext securityContext;

    /// Regression test: there was no document-level permission check, so anyone who could reach
    /// the endpoint could delete an index's data.
    @Test
    void testResetWithoutDeletePermissionIsDenied() {
        Mockito.when(securityContext.hasDocumentPermission(INDEX_DOC_REF, DocumentPermission.DELETE))
                .thenReturn(false);

        assertThatThrownBy(() -> createResource().reset(INDEX_UUID))
                .isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(indexShardManager);
    }

    /// Regression test: it returned `true` whatever happened. It now says how many shards went.
    @Test
    void testResetReturnsTheNumberOfShardsDeleted() {
        Mockito.when(securityContext.hasDocumentPermission(INDEX_DOC_REF, DocumentPermission.DELETE))
                .thenReturn(true);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(THIS_NODE);
        Mockito.when(indexShardService.find(Mockito.any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(
                        IndexShard.builder().nodeName(THIS_NODE).build(),
                        IndexShard.builder().nodeName(THIS_NODE).build())));
        Mockito.when(indexShardManager.performAction(Mockito.any(), Mockito.eq(IndexShardAction.DELETE)))
                .thenReturn(2L);

        assertThat(createResource().reset(INDEX_UUID)).isEqualTo(2L);
    }

    @Test
    void testResetOfAnIndexWithNoShardsReturnsZero() {
        Mockito.when(securityContext.hasDocumentPermission(INDEX_DOC_REF, DocumentPermission.DELETE))
                .thenReturn(true);
        Mockito.when(indexShardService.find(Mockito.any()))
                .thenReturn(ResultPage.empty());

        assertThat(createResource().reset(INDEX_UUID)).isZero();
        Mockito.verifyNoInteractions(indexShardManager);
    }

    /// Regression test: from the name "reset" the auto-logger could only record an UNKNOWN event.
    @Test
    void testResetIsAuditedAsADelete() throws NoSuchMethodException {
        assertThat(IndexResourceImpl.class.getMethod("reset", String.class).getAnnotation(AutoLogged.class))
                .isNotNull()
                .extracting(AutoLogged::value)
                .isEqualTo(OperationType.DELETE);
    }

    private IndexResourceImpl createResource() {
        return new IndexResourceImpl(
                null,
                () -> indexShardService,
                () -> indexShardManager,
                null,
                null,
                () -> nodeInfo,
                null,
                () -> securityContext);
    }
}
