package stroom.dashboard.expression.v1;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


class TestFunctionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFunctionFactory.class);

    /**
     * This ia more of a QA to ensure all the annotations are set correctly for the functions
     */
    @Test
    void ensureAllFunctionsAnnotated() {
        final Instant start = Instant.now();

        final FunctionFactory functionFactory = new FunctionFactory();

        LOGGER.info("Completed in {}", Duration.between(start, Instant.now()));

        // Scan the class path to find all the classes with @FunctionDef
        try (ScanResult result = new ClassGraph()
                .whitelistPackages(Function.class.getPackageName())
                .enableClassInfo()
                .ignoreClassVisibility()
                .scan()) {

            final List<Class<? extends Function>> allFunctionClasses = result.getAllClasses()
                    .stream()
                    .filter(classInfo ->  classInfo.implementsInterface(Function.class.getName()))
                    .map(classInfo -> (Class<? extends Function>) classInfo.loadClass())
                    .collect(Collectors.toList());

            SoftAssertions.assertSoftly(softAssertions -> {
                for (final Class<? extends Function> clazz : allFunctionClasses) {
                    final Optional<FunctionDef> optFuncDef = functionFactory.getFunctionDefinition(clazz);
                    final String className = clazz.getSimpleName();

                    if (clazz.getModifiers() == Modifier.ABSTRACT) {
                        softAssertions.assertThat(optFuncDef)
                                .withFailMessage("Function class " +
                                        className +
                                        " should not have @FunctionDef as it is abstract")
                                .isEmpty();
                    } else {
                        softAssertions.assertThat(optFuncDef)
                                .withFailMessage("Function class " +
                                        className +
                                        " is not in FunctionFactory. " +
                                        "Add an @FunctionDef annotation to it" )
                                .isNotEmpty();

                        optFuncDef.ifPresent(functionDef -> {
                            doSoftAssertions(softAssertions, functionDef, className);
                        });
                    }
                }
                if (!softAssertions.wasSuccess()) {
                    LOGGER.error("Found {} errors", softAssertions.errorsCollected().size());
                }
            });
        }
    }

    private void doSoftAssertions(final SoftAssertions softAssertions,
                                  final FunctionDef functionDef,
                                  final String className) {
        softAssertions.assertThat(functionDef.name())
                .withFailMessage(
                        "Function " + className + " needs a name")
                .isNotEmpty();

        assertMaxOneItemInArray(
                softAssertions,
                "FunctionDef.commonCategory",
                functionDef.commonCategory());

        assertMaxOneItemInArray(
                softAssertions,
                "FunctionDef.commonReturnType",
                functionDef.commonReturnType());

        softAssertions.assertThat(functionDef.signatures())
                .withFailMessage(
                        "Function " + className + " needs at least one @FunctionSignature")
                .isNotNull();

        softAssertions.assertThat(functionDef.signatures())
                .withFailMessage(
                        "Function " + className + " needs at least one @FunctionSignature")
                .isNotEmpty();

        for (final FunctionSignature signature : functionDef.signatures()) {

            assertMaxOneItemInArray(
                    softAssertions,
                    "FunctionSignature.category",
                    signature.category());

            assertMaxOneItemInArray(
                    softAssertions,
                    "FunctionSignature.returnType",
                    signature.returnType());

            final boolean hasCategory = functionDef.commonCategory().length > 0
                    || signature.category().length > 0;
            softAssertions.assertThat(hasCategory)
                    .withFailMessage(
                            "Either FunctionDef.commonCategory or " +
                                    "FunctionSignature.category must be set")
                    .isTrue();

            final boolean hasDescription = !functionDef.commonDescription().isEmpty()
                    || !signature.description().isEmpty();
            softAssertions.assertThat(hasDescription)
                    .withFailMessage(
                            "Either FunctionDef.commonDescription or " +
                                    "FunctionSignature.description must be set")
                    .isTrue();

            final boolean hasReturnType = functionDef.commonReturnType().length > 0
                    || signature.returnType().length > 0;
            softAssertions.assertThat(hasReturnType)
                    .withFailMessage(
                            "Either FunctionDef.commonReturnType or " +
                                    "FunctionSignature.returnType must be set")
                    .isTrue();
        }
    }

    private <T> void assertMaxOneItemInArray(final SoftAssertions softAssertions,
                                             final String name,
                                             final T[] arr) {
        softAssertions.assertThat(arr.length)
                .withFailMessage(name + " is only allowed to have zero or one items.")
                .isLessThanOrEqualTo(1);
    }
}