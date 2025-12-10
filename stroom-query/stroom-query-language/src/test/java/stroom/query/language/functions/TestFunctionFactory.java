/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.language.functions;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


class TestFunctionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFunctionFactory.class);

    /**
     * This ia more of a QA to ensure all the annotations are set correctly for the functions
     */
    @TestFactory
    Stream<DynamicTest> ensureAllFunctionsAnnotated() {
        final Instant start = Instant.now();

        final FunctionFactory functionFactory = new FunctionFactory();

        LOGGER.info("Completed in {}", Duration.between(start, Instant.now()));

        // Scan the class path to find all the classes with @FunctionDef
        try (final ScanResult result = new ClassGraph()
                .acceptPackages(Function.class.getPackageName())
                .enableClassInfo()
                .ignoreClassVisibility()
                .enableAnnotationInfo()
                .scan()) {

            final List<Class<? extends Function>> allFunctionClasses = result.getAllClasses()
                    .stream()
                    .filter(classInfo -> classInfo.implementsInterface(Function.class.getName()))
                    .filter(classInfo -> !classInfo.hasAnnotation(ArchitecturalFunction.class.getName()))
                    .map(classInfo -> (Class<? extends Function>) classInfo.loadClass())
                    .collect(Collectors.toList());

            return allFunctionClasses.stream()
                    .map(functionClass ->
                            DynamicTest.dynamicTest(
                                    functionClass.getSimpleName(),
                                    () ->
                                            examineFunctionClass(functionClass)));
        }
    }

    @Test
    void printFunctionNames() {
        final List<FunctionDef> functionDefs = FunctionFactory.getFunctionDefinitions();
        final StringBuilder sb = new StringBuilder();
        System.out.println(
                functionDefs
                        .stream()
                        .map(FunctionDef::name)
                        .sorted().collect(Collectors.joining("|")));
    }

    private void examineFunctionClass(final Class<? extends Function> functionClass) {
        SoftAssertions.assertSoftly(softAssertions -> {
            final Optional<FunctionDef> optFuncDef = FunctionFactory.getFunctionDefinition(functionClass);
            final String className = functionClass.getSimpleName();

            if (Modifier.isAbstract(functionClass.getModifiers())) {
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
                                "Add an @FunctionDef annotation to it or mark is as @ArchitecturalFunction")
                        .isNotEmpty();

                optFuncDef.ifPresent(functionDef -> {
                    doSoftAssertions(softAssertions, functionDef, className);
                });
            }
            if (!softAssertions.wasSuccess()) {
                LOGGER.error("Found {} errors", softAssertions.errorsCollected().size());
            }
        });
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
                className,
                "FunctionDef.commonCategory",
                functionDef.commonCategory());

        assertMaxOneItemInArray(
                softAssertions,
                className,
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

        for (int i = 0; i < functionDef.signatures().length; i++) {
            doSignatureAssertions(
                    softAssertions,
                    functionDef,
                    className,
                    functionDef.signatures()[i],
                    i);
        }
    }

    private void doSignatureAssertions(final SoftAssertions softAssertions,
                                       final FunctionDef functionDef,
                                       final String className,
                                       final FunctionSignature signature,
                                       final int sigIndex) {
        final String classAndSigName = getName(className, sigIndex);

        assertMaxOneItemInArray(
                softAssertions,
                classAndSigName,
                "FunctionSignature.category",
                signature.category());

        assertMaxOneItemInArray(
                softAssertions,
                classAndSigName,
                "FunctionSignature.returnType",
                signature.returnType());

        final boolean hasCategory = functionDef.commonCategory().length > 0
                || signature.category().length > 0;

        softAssertions.assertThat(hasCategory)
                .withFailMessage(
                        classAndSigName + " - Either FunctionDef.commonCategory or " +
                                "FunctionSignature.category must be set")
                .isTrue();

        final boolean hasDescription = !functionDef.commonDescription().isEmpty()
                || !signature.description().isEmpty();

        softAssertions.assertThat(hasDescription)
                .withFailMessage(
                        classAndSigName + " - Either FunctionDef.commonDescription or " +
                                "FunctionSignature.description must be set")
                .isTrue();

        final boolean hasReturnType = functionDef.commonReturnType().length > 0
                || signature.returnType().length > 0;

        softAssertions.assertThat(hasReturnType)
                .withFailMessage(
                        classAndSigName + " - Either FunctionDef.commonReturnType or " +
                                "FunctionSignature.returnType must be set")
                .isTrue();

        softAssertions.assertThat(Arrays.stream(signature.args())
                        .filter(FunctionArg::isVarargs)
                        .count())
                .withFailMessage(classAndSigName +
                        " - Only one argument can be a varargs argument.")
                .isLessThanOrEqualTo(1);

        assertOptionalArgs(softAssertions, classAndSigName, signature.args());

        if (Arrays.stream(signature.args())
                .anyMatch(arg -> arg.isOptional() && arg.isVarargs())) {
            softAssertions.fail(classAndSigName +
                    " - Found an argument that is both optional and varargs. They are mutually exclusive.");
        }
    }

    private String getName(final String className, final int sigIndex) {
        return className + " (sig " + sigIndex + ")";
    }

    private void assertOptionalArgs(final SoftAssertions softAssertions,
                                    final String classAndSigName,
                                    final FunctionArg[] args) {

        boolean foundOptArg = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].isOptional()) {
                if (foundOptArg && !args[i].isOptional()) {
                    softAssertions.fail(classAndSigName +
                            " - found mandatory argument " + args[i].name() +
                            " at index " + i + " after optional arguments. All arguments after an optional one" +
                            " must also be optional");
                }
                foundOptArg = true;
            }
        }
    }


    private <T> void assertMaxOneItemInArray(final SoftAssertions softAssertions,
                                             final String className,
                                             final String name,
                                             final T[] arr) {
        softAssertions.assertThat(arr.length)
                .withFailMessage(className +
                        " - " +
                        name +
                        " is only allowed to have zero or one items.")
                .isLessThanOrEqualTo(1);
    }
}
