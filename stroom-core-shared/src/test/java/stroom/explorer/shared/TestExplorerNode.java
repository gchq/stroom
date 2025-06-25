package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.explorer.shared.NodeFlag.NodeFlagGroups;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.explorer.shared.NodeFlag.NodeFlagGroups.EXPANDER_GROUP;

class TestExplorerNode {

    @Test
    void testSerDeser() {
        final ExplorerNode node = ExplorerNode.builder()
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
        final ExplorerNode node = ExplorerNode.builder()
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
        final ExplorerNode node = ExplorerNode.builder()
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
        final ExplorerNode node = ExplorerNode.builder()
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
        final ExplorerNode node = ExplorerNode.builder()
                .addNodeFlag(NodeFlag.FAVOURITE)
                .build();

        assertThat(node.hasNodeFlagGroup(EXPANDER_GROUP))
                .isFalse();
        assertThat(node.hasNodeFlagGroup(NodeFlagGroups.FILTER_MATCH_PAIR))
                .isFalse();
    }

    @TestFactory
    Stream<DynamicTest> testBuildDocRefPathString() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    if (testCase.getInput() == null) {
                        return ExplorerNode.buildDocRefPathString(null);
                    } else {
                        final List<DocRef> docRefs = testCase.getInput().stream()
                                .map(name -> DocRef.builder()
                                        .type("myType")
                                        .randomUuid()
                                        .name(name)
                                        .build())
                                .collect(Collectors.toList());
                        return ExplorerNode.buildDocRefPathString(docRefs);
                    }
                })
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase(List.of(), "")
                .addCase(List.of("foo"), "foo")
                .addCase(List.of("foo", "bar"), "foo / bar")
                .addCase(List.of("a", "b", "c"), "a / b / c")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBuildNodePathString() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    if (testCase.getInput() == null) {
                        return ExplorerNode.buildDocRefPathString(null);
                    } else {
                        final List<ExplorerNode> explorerNodes = testCase.getInput().stream()
                                .map(name -> ExplorerNode.builder()
                                        .type("myType")
                                        .name(name)
                                        .build())
                                .collect(Collectors.toList());
                        return ExplorerNode.buildNodePathString(explorerNodes);
                    }
                })
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase(List.of(), "")
                .addCase(List.of("foo"), "foo")
                .addCase(List.of("foo", "bar"), "foo / bar")
                .addCase(List.of("a", "b", "c"), "a / b / c")
                .build();
    }
}
