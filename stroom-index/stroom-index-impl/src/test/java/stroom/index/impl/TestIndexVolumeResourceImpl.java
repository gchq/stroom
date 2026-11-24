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

import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.ValidationResult;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.WebTargetFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/// Tests the validation of shared volumes by [IndexVolumeResourceImpl], which has to ask every node whether
/// it can use the volume's path.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestIndexVolumeResourceImpl {

    private static final String THIS_NODE = "node1";
    private static final String NODE_2 = "node2";
    private static final String NODE_3 = "node3";

    @Mock
    private IndexVolumeService indexVolumeService;
    @Mock
    private NodeService nodeService;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private WebTargetFactory webTargetFactory;

    @BeforeEach
    void beforeEach() {
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(THIS_NODE);
        Mockito.when(nodeService.getEnabledNodesByPriority()).thenReturn(List.of(THIS_NODE, NODE_2, NODE_3));
    }

    @Test
    void testSharedVolume_everyNodeCanUsePath() {
        final IndexVolume volume = newSharedVolume();
        Mockito.when(indexVolumeService.validate(volume)).thenReturn(ValidationResult.ok());
        nodeAnswers(NODE_2, ValidationResult.ok());
        nodeAnswers(NODE_3, ValidationResult.ok());

        assertThat(createResource().validate(volume).isOk()).isTrue();

        // This node has already checked the path as part of the full validation.
        verifyNodeAsked(THIS_NODE, 0);
        verifyNodeAsked(NODE_2, 1);
        verifyNodeAsked(NODE_3, 1);
    }

    @Test
    void testSharedVolume_nodeCannotUsePath() {
        final IndexVolume volume = newSharedVolume();
        Mockito.when(indexVolumeService.validate(volume)).thenReturn(ValidationResult.ok());
        nodeAnswers(NODE_2, ValidationResult.error("no such mount"));
        // An unreachable node does not turn the error into a warning that can be accepted.
        nodeIsUnreachable(NODE_3);

        final ValidationResult result = createResource().validate(volume);

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage())
                .contains("'" + NODE_2 + "'")
                .contains("no such mount")
                .doesNotContain(NODE_3);
    }

    @Test
    void testSharedVolume_unreachableNodeIsAWarning() {
        final IndexVolume volume = newSharedVolume();
        Mockito.when(indexVolumeService.validate(volume)).thenReturn(ValidationResult.ok());
        nodeAnswers(NODE_2, ValidationResult.ok());
        nodeIsUnreachable(NODE_3);

        final ValidationResult result = createResource().validate(volume);

        assertThat(result.isWarning()).isTrue();
        assertThat(result.getMessage())
                .contains("'" + NODE_3 + "'")
                .contains("could not be reached")
                .doesNotContain(NODE_2);
    }

    @Test
    void testSharedVolume_unreachableNodeAddsToLocalWarning() {
        final IndexVolume volume = newSharedVolume();
        Mockito.when(indexVolumeService.validate(volume)).thenReturn(ValidationResult.warning("will merge volumes"));
        nodeAnswers(NODE_2, ValidationResult.ok());
        nodeIsUnreachable(NODE_3);

        final ValidationResult result = createResource().validate(volume);

        assertThat(result.isWarning()).isTrue();
        assertThat(result.getMessage())
                .contains("will merge volumes")
                .contains("'" + NODE_3 + "'");
    }

    @Test
    void testSharedVolume_localErrorIsReturnedWithoutAskingOtherNodes() {
        final IndexVolume volume = newSharedVolume();
        Mockito.when(indexVolumeService.validate(volume)).thenReturn(ValidationResult.error("bad path"));

        final ValidationResult result = createResource().validate(volume);

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).isEqualTo("bad path");
        verifyNodeAsked(NODE_2, 0);
        verifyNodeAsked(NODE_3, 0);
    }

    @Test
    void testSharedVolume_otherNodesNotAskedWhenPathAndTypeUnchanged() {
        // Editing something else about a shared volume, such as its limit, does not need the path checking
        // again, so a node that is down does not get in the way.
        final IndexVolume existing = newSharedVolume().copy().id(5).build();
        final IndexVolume edited = existing.copy().bytesLimit(1000L).build();
        Mockito.when(indexVolumeService.read(5)).thenReturn(existing);
        Mockito.when(indexVolumeService.validate(edited)).thenReturn(ValidationResult.ok());
        nodeIsUnreachable(NODE_2);
        nodeIsUnreachable(NODE_3);

        assertThat(createResource().validate(edited).isOk()).isTrue();

        verifyNodeAsked(NODE_2, 0);
    }

    @Test
    void testSharedVolume_otherNodesAskedWhenLocalVolumeIsMarkedShared() {
        final IndexVolume existing = newSharedVolume().copy()
                .id(5)
                .volumeType(VolumeType.LOCAL)
                .nodeName(THIS_NODE)
                .build();
        final IndexVolume markedShared = existing.copy().volumeType(VolumeType.SHARED).nodeName(null).build();
        Mockito.when(indexVolumeService.read(5)).thenReturn(existing);
        Mockito.when(indexVolumeService.validate(markedShared)).thenReturn(ValidationResult.ok());
        nodeAnswers(NODE_2, ValidationResult.ok());
        nodeAnswers(NODE_3, ValidationResult.ok());

        assertThat(createResource().validate(markedShared).isOk()).isTrue();

        verifyNodeAsked(NODE_2, 1);
        verifyNodeAsked(NODE_3, 1);
    }

    @Test
    void testValidatePathIsCheckedOnThisNode() {
        final IndexVolume volume = newSharedVolume();
        Mockito.when(indexVolumeService.validatePath(volume)).thenReturn(ValidationResult.error("cannot write"));

        assertThat(createResource().validatePath(volume).getMessage()).isEqualTo("cannot write");
    }

    private IndexVolume newSharedVolume() {
        return IndexVolume.builder()
                .indexVolumeGroupId(1)
                .path("/mnt/share/index")
                .volumeType(VolumeType.SHARED)
                .build();
    }

    private void nodeAnswers(final String nodeName, final ValidationResult result) {
        Mockito.when(nodeService.remoteRestResult(eq(nodeName), eq(ValidationResult.class), any(), any(), any()))
                .thenReturn(result);
    }

    private void nodeIsUnreachable(final String nodeName) {
        Mockito.when(nodeService.remoteRestResult(eq(nodeName), eq(ValidationResult.class), any(), any(), any()))
                .thenThrow(new RuntimeException("Unable to connect to node " + nodeName));
    }

    private void verifyNodeAsked(final String nodeName, final int times) {
        Mockito.verify(nodeService, Mockito.times(times))
                .remoteRestResult(eq(nodeName), eq(ValidationResult.class), any(), any(), any());
    }

    private IndexVolumeResourceImpl createResource() {
        return new IndexVolumeResourceImpl(
                () -> indexVolumeService,
                () -> nodeService,
                () -> nodeInfo,
                () -> webTargetFactory);
    }
}
