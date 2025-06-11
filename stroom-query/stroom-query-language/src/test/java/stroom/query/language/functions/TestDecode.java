package stroom.query.language.functions;

import java.util.stream.Stream;

public class TestDecode extends AbstractFunctionTest<Decode> {
    @Override
    Class<Decode> getFunctionType() {
        return Decode.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "match",
                        ValString.create("rgb(0,0,255)"),
                        ValString.create("blue"),
                        ValString.create("^red"),
                        ValString.create("rgb(255,0,0)"),
                        ValString.create("^blue"),
                        ValString.create("rgb(0,0,255)"),
                        ValString.create("rgb(0,255,0)")),
                TestCase.of(
                        "otherwise",
                        ValString.create("rgb(0,255,0)"),
                        ValString.create("green"),
                        ValString.create("^red"),
                        ValString.create("rgb(255,0,0)"),
                        ValString.create("^blue"),
                        ValString.create("rgb(0,0,255)"),
                        ValString.create("rgb(0,255,0)")),
                TestCase.of(
                        "returnsCaptureGroup",
                        ValString.create("blue"),
                        ValString.create("red, white and blue"),
                        ValString.create("^red, (\\w+) and (\\w+)"),
                        ValString.create("$2"),
                        ValString.create("NO MATCH FOUND"))
        );
    }
}
