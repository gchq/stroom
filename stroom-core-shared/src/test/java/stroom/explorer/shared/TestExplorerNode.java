package stroom.explorer.shared;

import stroom.explorer.shared.NodeFlag.NodeFlagGroups;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.explorer.shared.NodeFlag.NodeFlagGroups.EXPANDER_GROUP;

class TestExplorerNode {

    @Test
    void testSerDeser() {
        ExplorerNode node = ExplorerNode.builder()
                .addNodeFlags(NodeFlag.CLOSED, NodeFlag.FOLDER, NodeFlag.FAVOURITE)
                .addNodeFlag(NodeFlag.FILTER_MATCH)
                .build();
        assertThat(node.getNodeFlags())
                .containsExactlyInAnyOrder(
                        NodeFlag.CLOSED, NodeFlag.FOLDER, NodeFlag.FAVOURITE, NodeFlag.FILTER_MATCH);

        TestUtil.testSerialisation(node, ExplorerNode.class);
    }

    @Test
    void testHasNodeFlag_false() {
        ExplorerNode node = ExplorerNode.builder()
                .build();

        assertThat(node.hasNodeFlag(NodeFlag.OPEN))
                .isFalse();
        assertThat(node.hasNodeFlags(NodeFlag.OPEN, NodeFlag.FAVOURITE))
                .isFalse();
        assertThat(node.isMissingNodeFlag(NodeFlag.OPEN))
                .isTrue();
        assertThat(node.isMissingNodeFlags(NodeFlag.OPEN, NodeFlag.FAVOURITE))
                .isTrue();
    }

    @Test
    void testHasNodeFlag_true() {
        ExplorerNode node = ExplorerNode.builder()
                .addNodeFlag(NodeFlag.OPEN)
                .addNodeFlag(NodeFlag.FAVOURITE)
                .addNodeFlag(NodeFlag.FOLDER)
                .build();

        assertThat(node.hasNodeFlag(NodeFlag.OPEN))
                .isTrue();
        assertThat(node.hasNodeFlags(NodeFlag.OPEN, NodeFlag.FAVOURITE))
                .isTrue();
        assertThat(node.isMissingNodeFlag(NodeFlag.OPEN))
                .isFalse();
        assertThat(node.isMissingNodeFlags(NodeFlag.OPEN, NodeFlag.FAVOURITE))
                .isFalse();
        assertThat(node.isMissingNodeFlags(NodeFlag.OPEN, NodeFlag.FAVOURITE, NodeFlag.LEAF))
                .isFalse();
    }

    @Test
    void testHasNodeGroup_true() {
        ExplorerNode node = ExplorerNode.builder()
                .addNodeFlag(NodeFlag.OPEN)
                .addNodeFlag(NodeFlag.FAVOURITE)
                .addNodeFlag(NodeFlag.FILTER_MATCH)
                .build();

        assertThat(node.hasNodeFlagGroup(EXPANDER_GROUP))
                .isTrue();
        assertThat(node.hasNodeFlagGroup(NodeFlagGroups.FILTER_MATCH_PAIR))
                .isTrue();
    }

    @Test
    void testHasNodeGroup_false() {
        ExplorerNode node = ExplorerNode.builder()
                .addNodeFlag(NodeFlag.FAVOURITE)
                .build();

        assertThat(node.hasNodeFlagGroup(EXPANDER_GROUP))
                .isFalse();
        assertThat(node.hasNodeFlagGroup(NodeFlagGroups.FILTER_MATCH_PAIR))
                .isFalse();
    }
}
