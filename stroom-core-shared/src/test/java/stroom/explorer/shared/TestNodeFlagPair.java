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

class TestNodeFlagPair {

    @TestFactory
    Stream<DynamicTest> testAddFlag() {
        final NodeFlagPair nodeFlagPair = NodeFlagGroups.FILTER_MATCH_PAIR;
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Boolean, Set<NodeFlag>>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Set<NodeFlag>>() {
                })
                .withTestFunction(testCase -> {
                    final boolean isOn = testCase.getInput()._1();
                    final Set<NodeFlag> nodeFlags = testCase.getInput()._2();
                    nodeFlagPair.addFlag(isOn, nodeFlags);
                    return nodeFlags;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(
                                true,
                                new HashSet<>()),
                        Set.of(nodeFlagPair.getOnFlag()))
                .addCase(Tuple.of(
                                false,
                                new HashSet<>()),
                        Set.of(nodeFlagPair.getOffFlag()))
                .addCase(Tuple.of(
                                true,
                                new HashSet<>(Set.of(nodeFlagPair.getOffFlag()))),
                        Set.of(nodeFlagPair.getOnFlag()))
                .addCase(Tuple.of(
                                false,
                                new HashSet<>(Set.of(nodeFlagPair.getOnFlag()))),
                        Set.of(nodeFlagPair.getOffFlag()))
                .build();
    }
}
