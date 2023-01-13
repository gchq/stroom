package stroom.dashboard.expression.v1;

import java.util.List;
import java.util.stream.Stream;

class TestDistinct extends AbstractFunctionTest<Distinct> {

    @Override
    Class<Distinct> getFunctionType() {
        return Distinct.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "no delimiter 1",
                        ValString.create("abcdezyx"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("d"),
                                ValString.create("e"),
                                ValString.create("z"),
                                ValString.create("y"),
                                ValString.create("x")
                        )),
                TestCase.ofAggregate(
                        "no delimiter 2",
                        ValString.create("abcd"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d")
                        )),
                TestCase.ofAggregate(
                        "delim",
                        ValString.create("a, b, c, d"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d")
                        ),
                        ValString.create(", ")
                ),
                TestCase.ofAggregate(
                        "delim + limit",
                        ValString.create("d|a|b"),
                        List.of(
                                ValString.create("d"),
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d")
                        ),
                        ValString.create("|"),
                        ValInteger.create(3)
                )
        );
    }
}
