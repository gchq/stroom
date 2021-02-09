package stroom.dashboard.expression.v1;

import java.util.List;
import java.util.stream.Stream;

class TestJoining extends AbstractFunctionTest<Joining> {

    @Override
    Class<Joining> getFunctionType() {
        return Joining.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "no delim",
                        ValString.create("abcd"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d")
                        )),
                TestCase.ofAggregate(
                        "delim",
                        ValString.create("a, b, c, d"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d")
                        ),
                        ValString.create(", ")
                ),
                TestCase.ofAggregate(
                        "delim + limit",
                        ValString.create("a, b"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d")
                        ),
                        ValString.create(", "),
                        ValInteger.create(2)
                )
        );
    }
}
