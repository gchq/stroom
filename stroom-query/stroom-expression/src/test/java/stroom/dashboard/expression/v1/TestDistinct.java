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
                        ValString.create("abcdexyz"),  // expected result (values in input order)
                        List.of(                       // input values
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("d"),
                                ValString.create("e"),
                                ValString.create("z"),  // values out of order
                                ValString.create("y"),
                                ValString.create("x")
                        )),
                TestCase.ofAggregate(
                        "no delimiter 2",
                        ValString.create("abcd"),      // expected result
                        List.of(                       // input values
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
                        ValString.create(", ")            // delim param
                ),
                TestCase.ofAggregate(
                        "delim + limit",
                        ValString.create("a, b"),
                        List.of(
                                ValString.create("a"),
                                ValString.create("a"),
                                ValString.create("b"),
                                ValString.create("c"),
                                ValString.create("d"),
                                ValString.create("c"),
                                ValString.create("d")
                        ),
                        ValString.create(", "),          // delim param
                        ValInteger.create(2)             // limit param
                )
        );
    }
}
