package stroom.explorer.shared;

import stroom.explorer.shared.NodeFlag.NodeFlagGroups;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

class TestNodeFlagGroup {

    @TestFactory
    Stream<DynamicTest> testAddFlag() {
        final NodeFlagGroup expanderGroup = NodeFlagGroups.EXPANDER_GROUP;
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<NodeFlag, Set<NodeFlag>>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Set<NodeFlag>>() {
                })
                .withTestFunction(testCase -> {
                    final NodeFlag nodeFlag = testCase.getInput()._1();
                    final Set<NodeFlag> nodeFlags = testCase.getInput()._2();
                    expanderGroup.addFlag(nodeFlag, nodeFlags);
                    return nodeFlags;
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(Tuple.of(
                                NodeFlag.FAVOURITE,
                                new HashSet<>()),
                        IllegalArgumentException.class)
                .addCase(Tuple.of(
                                NodeFlag.OPEN,
                                new HashSet<>()),
                        Set.of(NodeFlag.OPEN))
                // CLOSED replaced with OPEN, FOLDER untouched
                .addCase(Tuple.of(
                                NodeFlag.OPEN,
                                new HashSet<>(Set.of(NodeFlag.CLOSED, NodeFlag.FOLDER))),
                        Set.of(NodeFlag.OPEN, NodeFlag.FOLDER))
                .build();
    }

}
