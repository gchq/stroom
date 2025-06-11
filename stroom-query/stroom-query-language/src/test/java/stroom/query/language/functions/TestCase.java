package stroom.query.language.functions;

import java.util.stream.Stream;

public class TestCase extends AbstractFunctionTest<Case> {
    @Override
    Class<Case> getFunctionType() {
        return Case.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "matchLong",
                        ValLong.create(200),
                        ValLong.create(2),
                        ValLong.create(1),
                        ValLong.create(100),
                        ValLong.create(2),
                        ValLong.create(200),
                        ValString.create("NO MATCH")),
                TestCase.of(
                        "matchString",
                        ValString.create("two"),
                        ValString.create("2"),
                        ValString.create("1"),
                        ValString.create("one"),
                        ValString.create("2"),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND")),
                TestCase.of(
                        "matchLongReturnString",
                        ValString.create("two"),
                        ValLong.create(2),
                        ValLong.create(1),
                        ValString.create("one"),
                        ValLong.create(2),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND")),
                TestCase.of(
                        "doNotMatchStringToLong",
                        ValString.create("NO MATCH FOUND"),
                        ValString.create("2"),
                        ValLong.create(1),
                        ValString.create("one"),
                        ValLong.create(2),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND")),
                TestCase.of(
                        "matchWithChildFunctions",
                        ValBoolean.TRUE,
                        ValLong.create(1),
                        ValLong.create(1),
                        new True("true"),
                        new False("false")),
                TestCase.of(
                        "otherwiseWithChildFunctions",
                        ValNull.INSTANCE,
                        ValLong.create(2),
                        ValLong.create(1),
                        new True("true"),
                        new Null("null")),
                TestCase.of(
                        "otherwiseNoMatchFound",
                        ValString.create("NO MATCH FOUND"),
                        ValLong.create(4),
                        ValLong.create(1),
                        ValString.create("one"),
                        ValLong.create(2),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND"))
        );
    }
}
