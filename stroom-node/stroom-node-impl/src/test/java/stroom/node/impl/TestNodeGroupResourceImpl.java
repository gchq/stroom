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

package stroom.node.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the audit logging of {@link NodeGroupResourceImpl#updateNodeGroupState}.
 * <p>
 * See issue #5773's sibling, #5800. The auto logger cannot derive a before or after for this
 * method, so it produced no audit event at all and merely logged
 * "Either before or after must have a value".
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestNodeGroupResourceImpl {

    private static final int NODE_GROUP_ID = 7;
    private static final String TYPE_ID = "NodeGroupResourceImpl.updateNodeGroupState";

    @Mock
    private NodeGroupService mockNodeGroupService;
    @Mock
    private DocumentEventLog mockDocumentEventLog;

    private NodeGroupResourceImpl resource;

    @BeforeEach
    void setUp() {
        resource = new NodeGroupResourceImpl(() -> mockNodeGroupService, () -> mockDocumentEventLog);
    }

    @Test
    void testUpdateNodeGroupState_logsTheGroupAndItsMembershipEitherSideOfTheChange() {
        // This endpoint changes the group's name, enabled and invert selection flags as well as its
        // membership, so all of it has to appear in the event, not just the selected node ids.
        final NodeGroup beforeGroup = group("old-name", false);
        final NodeGroup afterGroup = group("new-name", true);
        when(mockNodeGroupService.fetchById(NODE_GROUP_ID))
                .thenReturn(beforeGroup, afterGroup);
        when(mockNodeGroupService.getNodeGroupState(NODE_GROUP_ID))
                .thenReturn(new NodeGroupState(Set.of(1, 2)), new NodeGroupState(Set.of(1, 2, 3)));
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenReturn(true);

        final Boolean result = resource.updateNodeGroupState(change(NODE_GROUP_ID));

        assertThat(result).isTrue();

        final NodeGroupChange before = captureBefore();
        final NodeGroupChange after = captureAfter();
        assertThat(before.getNodeGroup().getName()).isEqualTo("old-name");
        assertThat(before.getNodeGroup().isEnabled()).isFalse();
        assertThat(before.getSelectedNodes()).containsExactlyInAnyOrder(1, 2);
        assertThat(after.getNodeGroup().getName()).isEqualTo("new-name");
        assertThat(after.getNodeGroup().isEnabled()).isTrue();
        assertThat(after.getSelectedNodes()).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void testUpdateNodeGroupState_recordsAFlagChangeEvenWhenMembershipIsUnchanged() {
        // Disabling a group without touching the node selection must still be auditable. Logging
        // only the selected node ids would give an identical before and after here.
        when(mockNodeGroupService.fetchById(NODE_GROUP_ID))
                .thenReturn(group("test-group", true), group("test-group", false));
        when(mockNodeGroupService.getNodeGroupState(NODE_GROUP_ID))
                .thenReturn(new NodeGroupState(Set.of(1, 2)));
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenReturn(true);

        resource.updateNodeGroupState(change(NODE_GROUP_ID));

        assertThat(captureBefore().getNodeGroup().isEnabled()).isTrue();
        assertThat(captureAfter().getNodeGroup().isEnabled()).isFalse();
    }

    @Test
    void testUpdateNodeGroupState_namesTheGroupInTheEventDescription() {
        // Without this the event says only "NodeGroupChange" and never which group was changed.
        when(mockNodeGroupService.fetchById(NODE_GROUP_ID))
                .thenReturn(group("test-group", true));
        when(mockNodeGroupService.getNodeGroupState(NODE_GROUP_ID))
                .thenReturn(new NodeGroupState(Set.of(1)));
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenReturn(true);

        resource.updateNodeGroupState(change(NODE_GROUP_ID));

        final ArgumentCaptor<String> verbCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockDocumentEventLog).update(any(), any(), eq(TYPE_ID), verbCaptor.capture(), any());
        assertThat(verbCaptor.getValue())
                .contains("test-group")
                .contains(String.valueOf(NODE_GROUP_ID));
    }

    @Test
    void testUpdateNodeGroupState_alwaysLogsSomethingForBeforeOrAfter() {
        // This is the regression guard for the reported bug. DocumentEventLogImpl rejects an update
        // event where both before and after are null, dropping the audit event entirely, so we must
        // never call it that way.
        when(mockNodeGroupService.fetchById(anyInt()))
                .thenReturn(null);
        when(mockNodeGroupService.getNodeGroupState(any()))
                .thenReturn(null);
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenReturn(true);

        resource.updateNodeGroupState(change(NODE_GROUP_ID));

        assertBeforeOrAfterHasAValue();
    }

    @Test
    void testUpdateNodeGroupState_fallsBackToTheChangeWhenStateCannotBeRead() {
        when(mockNodeGroupService.fetchById(anyInt()))
                .thenThrow(new RuntimeException("no such group"));
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenReturn(true);
        final NodeGroupChange change = change(NODE_GROUP_ID);

        resource.updateNodeGroupState(change);

        verify(mockDocumentEventLog).update(eq(null), eq(change), eq(TYPE_ID), any(String.class), eq(null));
    }

    @Test
    void testUpdateNodeGroupState_missingIdStillProducesAnEvent() {
        // A change with no node group cannot be used to look up anything, but the attempt must
        // still be audited.
        final NodeGroupChange change = new NodeGroupChange(null, Set.of(1));
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenReturn(true);

        resource.updateNodeGroupState(change);

        verify(mockNodeGroupService, Mockito.never()).getNodeGroupState(any());
        verify(mockDocumentEventLog).update(eq(null), eq(change), eq(TYPE_ID), any(String.class), eq(null));
        assertBeforeOrAfterHasAValue();
    }

    @Test
    void testUpdateNodeGroupState_logsTheFailureAndRethrows() {
        when(mockNodeGroupService.fetchById(NODE_GROUP_ID))
                .thenReturn(group("test-group", true));
        when(mockNodeGroupService.getNodeGroupState(NODE_GROUP_ID))
                .thenReturn(new NodeGroupState(Set.of(1, 2)));
        final RuntimeException expected = new RuntimeException("update failed");
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenThrow(expected);

        assertThatThrownBy(() -> resource.updateNodeGroupState(change(NODE_GROUP_ID)))
                .isSameAs(expected);

        verify(mockDocumentEventLog).update(any(), any(), eq(TYPE_ID), any(String.class), eq(expected));
        assertBeforeOrAfterHasAValue();
    }

    @Test
    void testUpdateNodeGroupState_failureDoesNotPresentTheChangeAsTheAfter() {
        // The requested change was never applied, so it must not appear in the event's 'after'.
        when(mockNodeGroupService.fetchById(anyInt()))
                .thenReturn(null);
        when(mockNodeGroupService.getNodeGroupState(any()))
                .thenReturn(null);
        final RuntimeException expected = new RuntimeException("update failed");
        when(mockNodeGroupService.updateNodeGroupState(any()))
                .thenThrow(expected);
        final NodeGroupChange change = change(NODE_GROUP_ID);

        assertThatThrownBy(() -> resource.updateNodeGroupState(change))
                .isSameAs(expected);

        verify(mockDocumentEventLog).update(eq(change), eq(null), eq(TYPE_ID), any(String.class), eq(expected));
    }

    private void assertBeforeOrAfterHasAValue() {
        final ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        final ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mockDocumentEventLog).update(
                beforeCaptor.capture(),
                afterCaptor.capture(),
                any(String.class),
                any(String.class),
                any());

        assertThat(beforeCaptor.getValue() != null || afterCaptor.getValue() != null)
                .describedAs("Either before or after must have a value, or no audit event is logged")
                .isTrue();
    }

    private NodeGroupChange captureBefore() {
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(mockDocumentEventLog).update(
                captor.capture(), any(), any(String.class), any(String.class), any());
        return (NodeGroupChange) captor.getValue();
    }

    private NodeGroupChange captureAfter() {
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(mockDocumentEventLog).update(
                any(), captor.capture(), any(String.class), any(String.class), any());
        return (NodeGroupChange) captor.getValue();
    }

    private static NodeGroup group(final String name, final boolean enabled) {
        return NodeGroup.builder().id(NODE_GROUP_ID).name(name).enabled(enabled).build();
    }

    private static NodeGroupChange change(final int nodeGroupId) {
        return new NodeGroupChange(
                NodeGroup.builder().id(nodeGroupId).name("test-group").build(),
                Set.of(1, 2, 3));
    }
}
