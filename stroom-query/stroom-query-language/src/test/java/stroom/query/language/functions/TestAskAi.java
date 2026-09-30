package stroom.query.language.functions;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class TestAskAi extends AbstractFunctionTest<AskAi> {

    @Override
    Class<AskAi> getFunctionType() {
        return AskAi.class;
    }

    /**
     * Override so the function under test gets an {@link AiProvider} that echoes what it was asked rather
     * than the no-op provider that {@link ExpressionContext}'s no-args constructor supplies.
     */
    @Override
    Supplier<AskAi> getFunctionSupplier() {
        final ExpressionContext expressionContext = ExpressionContext.builder()
                .aiProvider((modelNameOrUuid, systemPrompt, message) -> {
                    if ("BAD_MODEL".equals(modelNameOrUuid)) {
                        return ValErr.create("AI model not found with name or UUID 'BAD_MODEL'");
                    }
                    return ValString.create(modelNameOrUuid + "|" + systemPrompt + "|" + message);
                })
                .build();
        return () -> new AskAi(expressionContext, AskAi.NAME);
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "modelAndMessage",
                        ValString.create("myModel|null|What is this?"),
                        ValString.create("myModel"),
                        ValString.create("What is this?")),
                TestCase.of(
                        "modelMessageAndSystemPrompt",
                        ValString.create("myModel|You are a log parser|What is this?"),
                        ValString.create("myModel"),
                        ValString.create("What is this?"),
                        ValString.create("You are a log parser")),
                TestCase.of(
                        "unknownModel",
                        ValErr.create("AI model not found with name or UUID 'BAD_MODEL'"),
                        ValString.create("BAD_MODEL"),
                        ValString.create("What is this?")),
                TestCase.of(
                        "nullMessage",
                        ValNull.INSTANCE,
                        ValString.create("myModel"),
                        ValNull.INSTANCE)
        );
    }
}
