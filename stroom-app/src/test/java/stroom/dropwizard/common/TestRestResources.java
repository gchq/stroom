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

package stroom.dropwizard.common;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.event.logging.rs.impl.AnnotationUtil;
import stroom.security.api.SecurityContext;
import stroom.util.ConsoleColour;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.FetchWithLongId;
import stroom.util.shared.FetchWithTemplate;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RestResource;
import stroom.util.shared.Unauthenticated;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple4;
import jakarta.inject.Provider;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestRestResources {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestRestResources.class);


    //    @Disabled // Temp while REST resource refactoring / annotation work is ongoing.
    @TestFactory
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> buildQualityAssuranceTests() {
        try (final ScanResult result = new ClassGraph()
                .acceptPackages("stroom")
                .enableClassInfo()
                .ignoreClassVisibility()
                .enableAnnotationInfo()
                .scan()) {

            final List<? extends Class<? extends RestResource>> classes = result.getAllClasses()
                    .stream()
                    .filter(classInfo -> classInfo.implementsInterface(RestResource.class.getName()))
                    .map(classInfo -> (Class<? extends RestResource>) classInfo.loadClass())
                    .filter(clazz ->
                            !clazz.getName().contains("Test"))
                    .sorted(Comparator.comparing(Class::getName))
                    .toList();

            LOGGER.info("Found {} classes to test", classes.size());

            return classes.stream()
                    .map(resourceClass ->
                            DynamicTest.dynamicTest(
                                    resourceClass.getSimpleName()
                                    + " ("
                                    + resourceClass.getPackageName()
                                    + ")",
                                    () ->
                                            doResourceClassAsserts(resourceClass)));
        }
    }

    //    @Disabled // manually run only
    @Test
    @SuppressWarnings("unchecked")
    void listMethods() {

        try (final ScanResult result = new ClassGraph()
                .acceptPackages("stroom")
                .enableClassInfo()
                .ignoreClassVisibility()
                .enableAnnotationInfo()
                .scan()) {

            final List<? extends Class<? extends RestResource>> classes = result.getAllClasses()
                    .stream()
                    .filter(classInfo -> classInfo.implementsInterface(RestResource.class.getName()))
                    .map(classInfo -> (Class<? extends RestResource>) classInfo.loadClass())
                    .sorted(Comparator.comparing(Class::getName))
                    .toList();

            LOGGER.info("Found {} classes to test", classes.size());

            final Map<Tuple2<String, String>, List<Tuple4<String, String, String, String>>> results = classes.stream()
                    .flatMap(clazz ->
                            Arrays.stream(clazz.getMethods())
                                    .map(method -> Tuple.of(clazz, method)))
                    .filter(clazzMethod -> hasJaxRsAnnotation(clazzMethod._1, clazzMethod._2, false))
                    .map(clazzMethod -> Tuple.of(
                            clazzMethod._2.getName(),
                            getJaxRsHttpMethod(clazzMethod._2),
                            getMethodSig(clazzMethod._1, clazzMethod._2),
                            getJaxRsPath(clazzMethod._1, clazzMethod._2)))
                    .collect(Collectors.groupingBy(
                            tuple ->
                                    Tuple.of(tuple._1, tuple._2),
                            Collectors.toList()));

            final StringBuilder stringBuilder = new StringBuilder();
            results.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
                    .forEach(entry -> {
                        final Tuple2<String, String> key = entry.getKey();
                        final List<Tuple4<String, String, String, String>> value = entry.getValue();
                        stringBuilder
                                .append(key._1)
                                .append(" (")
                                .append(ConsoleColour.yellow(key._2))
                                .append(")\n");

                        value.stream()
                                .sorted()
                                .forEach(tuple4 -> {
                                    final String path = Strings.padEnd(tuple4._4, 50, ' ');
                                    stringBuilder
                                            .append("  ")
                                            .append(ConsoleColour.blue(path))
                                            .append(" ")
                                            .append(tuple4._3)
                                            .append("\n");
                                });
                    });
//            LOGGER.info("Methods:\n{}", stringBuilder);
            System.out.println(stringBuilder);
        }
    }

    @Test
    void testGetFromMethodOrSuper_onSuper() {

        final Method methodOne = Arrays.stream(MyClassOne.class.getMethods())
                .filter(method -> method.getName().equals("methodOne"))
                .findAny()
                .orElse(null);

        assertThat(methodOne)
                .isNotNull();

        final Boolean result = RestResources.getFromMethodOrSuper(MyClassOne.class, methodOne, method -> {
            return method.isAnnotationPresent(Unauthenticated.class)
                    ? true
                    : null;
        });

        assertThat(result)
                .isEqualTo(true);
    }

    @Test
    void testGetFromMethodOrSuper_onClass() {

        final Method methodTwo = Arrays.stream(MyClassOne.class.getMethods())
                .filter(method -> method.getName().equals("methodTwo"))
                .findAny()
                .orElse(null);

        assertThat(methodTwo)
                .isNotNull();

        final Boolean result = RestResources.getFromMethodOrSuper(MyClassOne.class, methodTwo, method -> {
            return method.isAnnotationPresent(Unauthenticated.class)
                    ? true
                    : null;
        });

        assertThat(result)
                .isEqualTo(true);
    }

    @Test
    void testGetFromMethodOrSuper_notPresent() {

        final Method methodThree = Arrays.stream(MyClassOne.class.getMethods())
                .filter(method -> method.getName().equals("methodThree"))
                .findAny()
                .orElse(null);

        assertThat(methodThree)
                .isNotNull();

        final Boolean result = RestResources.getFromMethodOrSuper(MyClassOne.class, methodThree, method -> {
            return method.isAnnotationPresent(Unauthenticated.class)
                    ? true
                    : null;
        });

        assertThat(result)
                .isNull();
    }

    private String getMethodSig(final Class<?> clazz,
                                final Method method) {
        final String methodeSig = method.getReturnType().getSimpleName() +
                                  " " +
                                  ConsoleColour.yellow(method.getName()) +
                                  "(" +
                                  Arrays.stream(method.getParameters())
                                          .map(parameter ->
                                                  parameter.getType().getSimpleName())
                                          .collect(Collectors.joining(", ")) +
                                  ")";

        return Strings.padEnd(methodeSig, 80, ' ') +
               " [" +
               ConsoleColour.cyan(clazz.getSimpleName()) +
               "]";
    }

    private String getJaxRsPath(final Class<? extends RestResource> clazz,
                                final Method method) {
        final String basePath = NullSafe.getOrElse(clazz.getAnnotation(Path.class), Path::value, "");
        final String subPath = NullSafe.getOrElse(method.getAnnotation(Path.class), Path::value, "/");
        return basePath + subPath;
    }

    private String getJaxRsHttpMethod(final Method method) {
        final Set<Class<? extends Annotation>> httpMethodAnnos = Set.of(
                GET.class,
                PUT.class,
                DELETE.class,
                HEAD.class,
                PATCH.class,
                POST.class,
                OPTIONS.class);

        return Arrays.stream(method.getAnnotations())
                .filter(anno -> httpMethodAnnos.contains(anno.annotationType()))
                .findFirst()
                .map(annotation -> annotation.annotationType().getSimpleName())
                .orElse("???");
//                .orElseThrow(() -> new RuntimeException("Method " + method + "has no jaxrs anno, e.g. GET/PUT/etc."));
    }

    private void doResourceClassAsserts(final Class<? extends RestResource> resourceClass) {

        final boolean isInterface = resourceClass.isInterface();
        final String typeName = isInterface
                ? "interface"
                : "class";

        SoftAssertions.assertSoftly(softAssertions -> {
            if (isInterface) {
                testInterface(resourceClass, typeName, softAssertions);
            } else {
                testImplementation(resourceClass, typeName, softAssertions);
            }
        });
    }

    private void testInterface(final Class<? extends RestResource> resourceClass,
                               final String typeName,
                               final SoftAssertions softAssertions) {
        LOGGER.info("Doing @Api... asserts");

        // Check that the interface has no @Autologged annotations.
        checkForUnexpectedAnnotations(
                resourceClass,
                softAssertions,
                annotationClass -> annotationClass.equals(AutoLogged.class),
                AutoLogged.class.getName());

        // Check that the interface has no @Timed annotations.
        checkForUnexpectedAnnotations(
                resourceClass,
                softAssertions,
                annotationClass -> annotationClass.equals(Timed.class),
                Timed.class.getName());

        final boolean classHasTagAnnotation = resourceClass.isAnnotationPresent(Tag.class);
        final String[] apiAnnotationTags = classHasTagAnnotation
                ? Arrays
                .stream(resourceClass.getAnnotationsByType(Tag.class))
                .map(Tag::name)
                .toArray(String[]::new)
                : new String[0];

        softAssertions.assertThat(classHasTagAnnotation)
                .withFailMessage(() -> typeName + " must have class annotation like " +
                                       "@Tag(tags = \"Nodes\")")
                .isTrue();

        if (classHasTagAnnotation) {
            LOGGER.info("Class has @Tag annotation");
            softAssertions.assertThat(apiAnnotationTags.length)
                    .withFailMessage(() -> "@Tag must have tags property set, e.g. @Api(tags = \"Nodes\")")
                    .isGreaterThanOrEqualTo(1);
            if (apiAnnotationTags.length >= 1) {
                softAssertions.assertThat(apiAnnotationTags[0])
                        .withFailMessage(() -> "@Tag must have tags property set, e.g. @Api(tags = \"Nodes\")")
                        .isNotEmpty();
            }
        } else {
            LOGGER.info("Class doesn't have @Tag annotation");
        }

        // We need to get a set of unique methods otherwise we end up seeing duplicates from inherited interfaces.
        final Set<MethodSignature> uniqueMethods = Arrays
                .stream(resourceClass.getMethods())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .filter(method -> hasJaxRsAnnotation(resourceClass, method, true))
                .map(method -> new MethodSignature(method.getName(), method.getParameterTypes()))
                .collect(Collectors.toSet());

        uniqueMethods
                .forEach(methodSignature -> {
                    try {
                        final Method method = resourceClass.getMethod(methodSignature.getName(),
                                methodSignature.getParameterTypes());
                        final List<Class<? extends Annotation>> methodAnnotationTypes = Arrays
                                .stream(method.getAnnotations())
                                .map(Annotation::annotationType)
                                .collect(Collectors.toList());

                        LOGGER.debug("Found annotations {}", methodAnnotationTypes);

                        final boolean hasOperationAnno = methodAnnotationTypes.contains(Operation.class);

                        softAssertions
                                .assertThat(hasOperationAnno)
                                .withFailMessage(() ->
                                        "Method '" + method.getName() + "' must be annotated " +
                                        "with @Operation(summary = \"Some description of what the method does\")")
                                .isTrue();

                        if (hasOperationAnno) {
                            final Class<?> methodReturnClass = method.getReturnType();
                            final Operation operation = AnnotationUtil
                                    .getInheritedMethodAnnotation(Operation.class, method);
                            final ApiResponse[] responses = operation.responses();

                            final Map<String, String> operationIdMap = new HashMap<>();

                            softAssertions
                                    .assertThat(operation.operationId())
                                    .withFailMessage(() ->
                                            "Method '" +
                                            method.getName() +
                                            "' declares an @Operation annotation but has no `operationId`")
                                    .isNotBlank();

                            if (!operation.operationId().isBlank()) {
                                final String existing = operationIdMap.put(
                                        operation.operationId(),
                                        resourceClass.getName() + "::" + methodSignature.getName());

                                final String existingOperation = existing != null
                                        ? existing
                                        : resourceClass.getName() + "::" + methodSignature.getName();

                                if (!existingOperation.equals(resourceClass.getName() + "::" +
                                                              methodSignature.getName())) {
                                    LOGGER.warn("Method '" +
                                                method.getName() +
                                                "' does not have a globally unique `operationId` '" +
                                                operation.operationId() +
                                                "'" +
                                                " exists in " +
                                                existingOperation);
                                }

                                softAssertions
                                        .assertThat(existingOperation)
                                        .withFailMessage(() ->
                                                "Method '" +
                                                method.getName() +
                                                "' does not have a globally unique `operationId` '" +
                                                operation.operationId() +
                                                "'" +
                                                " exists in " +
                                                existingOperation)
                                        .isEqualTo(resourceClass.getName() + "::" + methodSignature.getName());
                            }

                            // Only need to set response when Response is used
                            if (Response.class.equals(methodReturnClass)) {
                                softAssertions
                                        .assertThat(responses.length)
                                        .withFailMessage(() ->
                                                "Method '" + method.getName() + "' must have response " +
                                                "set in @Operation, e.g. @Operation(summary = \"xxx\" " +
                                                "response = Node.class)")
                                        .isNotZero();
                            }
                        }
                    } catch (final NoSuchMethodException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                });
    }

    private void testImplementation(final Class<? extends RestResource> resourceClass,
                                    final String typeName,
                                    final SoftAssertions softAssertions) {
        final boolean superImplementsRestResource = Arrays.stream(resourceClass.getInterfaces())
                .filter(iface ->
                        !RestResource.class.equals(iface))
                .anyMatch(iface ->
                        Arrays.asList(iface.getInterfaces())
                                .contains(RestResource.class));

        LOGGER.info("Inspecting {} {}, superImplementsRestResource; {}",
                typeName, resourceClass.getName(), superImplementsRestResource);

        // Check that this is an implementation of a rest resource interface.
        softAssertions.assertThat(superImplementsRestResource)
                .withFailMessage(
                        "Unexpected class that is not an interface and does not implement " +
                        RestResource.class.getName())
                .isTrue();

        // Check that we have no JAX_RS annotations.
        checkForUnexpectedAnnotations(
                resourceClass,
                softAssertions,
                annotationClass -> annotationClass.getPackageName().equals(Path.class.getPackageName()),
                "JAX RS");

        // Check that we have no swagger annotations.
        checkForUnexpectedAnnotations(
                resourceClass,
                softAssertions,
                annotationClass -> annotationClass.getPackageName().contains("swagger"),
                "Swagger");

        // Check auto logging
        final boolean classIsAutoLogged = resourceClass.isAnnotationPresent(AutoLogged.class);
        LOGGER.info("classIsAutoLogged: {}", classIsAutoLogged);


        // Check that all member variables are providers.
        assertProviders(resourceClass, softAssertions);
        // Check that resource doesn't attempt to handle security
        assertNoSecurityContext(resourceClass, softAssertions);

        Arrays.stream(resourceClass.getMethods())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .filter(method -> hasJaxRsAnnotation(resourceClass, method, true))
                .forEach(method -> {
                    final boolean methodIsAutoLogged = method.isAnnotationPresent(AutoLogged.class);

                    softAssertions.assertThat(classIsAutoLogged || methodIsAutoLogged)
                            .withFailMessage(() -> "Method " + method.getName() +
                                                   "(...) or its class must be annotated with @AutoLogged")
                            .isTrue();

                    OperationType effectiveLoggingType = null;
                    if (classIsAutoLogged) {
                        effectiveLoggingType = resourceClass.getAnnotation(AutoLogged.class).value();
                    }
                    if (methodIsAutoLogged) {
                        effectiveLoggingType = method.getAnnotation(AutoLogged.class).value();
                    }

                    if (method.getReturnType().equals(Void.TYPE)) {
                        softAssertions.assertThat(effectiveLoggingType)
                                .withFailMessage(() -> "Method " + method.getName() +
                                                       "(...) returns void, so autologger can't operate on it. " +
                                                       "Either change the return type, manually log and annotate" +
                                                       " with @AutoLogged(MANUALLY_LOGGED), or " +
                                                       "annotate with @AutoLogged(UNLOGGED).")
                                .isIn(OperationType.MANUALLY_LOGGED, OperationType.UNLOGGED);
                    }
                });

        assertFetchDeclared(resourceClass, softAssertions);
    }

    private void assertFetchDeclared(final Class<? extends RestResource> resourceClass,
                                     final SoftAssertions softAssertions) {
        final boolean fetchMethodPresent = Arrays.stream(resourceClass.getMethods())
                .anyMatch(m -> m.getName().equals("fetch") && m.getParameterCount() == 1);
        final boolean updateOrDeleteMethodPresent = Arrays.stream(resourceClass.getMethods())
                .anyMatch(m -> m.getName().equals("update") || m.getName().equals("delete"));
        if (fetchMethodPresent && updateOrDeleteMethodPresent) {
            if (!FetchWithUuid.class.isAssignableFrom(resourceClass) &&
                !FetchWithIntegerId.class.isAssignableFrom(resourceClass) &&
                !FetchWithLongId.class.isAssignableFrom(resourceClass) &&
                !FetchWithTemplate.class.isAssignableFrom(resourceClass)) {
                softAssertions.fail("Resource classes that support fetch() should" +
                                    " declare the appropriate FetchWithSomething<FetchedThing> interface");
            }
        }
    }

    private void assertProviders(final Class<? extends RestResource> resourceClass,
                                 final SoftAssertions softAssertions) {
        final List<Class<?>> nonProvidedFields = Arrays.stream(resourceClass.getDeclaredFields())
                .filter(field ->
                        Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .filter(clazz ->
                        !Provider.class.equals(clazz))
                .collect(Collectors.toList());

        if (!nonProvidedFields.isEmpty()) {
            LOGGER.error("Non provided fields {}", nonProvidedFields);
            softAssertions.assertThat(nonProvidedFields)
                    .withFailMessage("Resource implementations/classes must inject all objects " +
                                     "via Providers.")
                    .isEmpty();
        }
    }

    private void assertNoSecurityContext(final Class<? extends RestResource> resourceClass,
                                         final SoftAssertions softAssertions) {
        final List<Field> securityContextFields = Arrays.stream(resourceClass.getDeclaredFields())
                .filter(field -> {
                    if (SecurityContext.class.isAssignableFrom(field.getType())) {
                        return true;
                    } else if (Provider.class.isAssignableFrom(field.getType())) {
                        //Really would like to check for presence of Provider<SecurityContext>
                        //but not possible in Java.
                        // Check for what we can manage, which is a field of type that extends Provider<SecurityContext>
                        if (field.getType().getGenericSuperclass() instanceof final ParameterizedType providerType) {
                            return Arrays.stream(providerType.getActualTypeArguments())
                                    .anyMatch(type ->
                                            SecurityContext.class.isAssignableFrom(type.getClass()));
                        }
                    }
                    return false;

                })
                .collect(Collectors.toList());

        if (!securityContextFields.isEmpty()) {
            LOGGER.warn("SecurityContext fields {}", securityContextFields);
            softAssertions.assertThat(securityContextFields)
                    .withFailMessage(
                            "Resource implementations/classes should delegate SecurityContext " +
                            "operations to an internal service.")
                    .isEmpty();
        }
    }

    private boolean hasJaxRsAnnotation(final Class<?> clazz, final Method method, final boolean checkInterfaces) {
        final boolean thisMethodHasJaxRs = Arrays.stream(method.getAnnotations())
                .anyMatch(annotation ->
                        annotation.annotationType().getPackageName().equals("jakarta.ws.rs"));
        if (!checkInterfaces) {
            return thisMethodHasJaxRs;
        } else {
            if (!thisMethodHasJaxRs) {
                final Class<?> restInterface = Arrays.stream(clazz.getInterfaces())
                        .filter(iface -> Arrays.asList(iface.getInterfaces()).contains(RestResource.class))
                        .findAny()
                        .orElse(null);

                if (restInterface == null) {
                    return false;
                } else {
                    // now find the same method on the interface
                    final Optional<Method> optIfaceMethod = Arrays.stream(restInterface.getMethods())
                            .filter(ifaceMethod -> areMethodsEqual(method, ifaceMethod))
                            .findAny();

                    return optIfaceMethod.map(ifaceMethod -> hasJaxRsAnnotation(restInterface,
                                    ifaceMethod,
                                    checkInterfaces))
                            .orElse(false);
                }
            } else {
                return true;
            }
        }
    }

    private boolean areMethodsEqual(final Method method1, final Method method2) {
        if (method1.equals(method2)) {
            return true;
        } else {
            return method1.getName().equals(method2.getName())
                   && method1.getReturnType().equals(method2.getReturnType())
                   && method1.getGenericReturnType().equals(method2.getGenericReturnType())
                   && Arrays.equals(method1.getParameterTypes(), method2.getParameterTypes())
                   && Arrays.equals(method1.getGenericParameterTypes(), method2.getGenericParameterTypes());
        }
    }

    private void checkForUnexpectedAnnotations(final Class<? extends RestResource> resourceClass,
                                               final SoftAssertions softAssertions,
                                               final Function<Class<? extends Annotation>, Boolean> testFunction,
                                               final String annotationType) {
        // Check that we have no JAX_RS annotations.
        final boolean hasClassAnnotation = Arrays.stream(resourceClass.getAnnotations())
                .anyMatch(annotation ->
                        testFunction.apply(annotation.annotationType()));

        softAssertions.assertThat(hasClassAnnotation)
                .withFailMessage(
                        "Class " +
                        resourceClass.getName() +
                        " name Unexpected " +
                        annotationType +
                        " annotations")
                .isFalse();

        // 'default' methods effectively drag iface annos onto the impl, so ignore those methods
        Arrays.stream(resourceClass.getMethods())
                .filter(method -> !method.isDefault())
                .forEach(method -> {
                    final boolean hasMethodAnnotation = Arrays.stream(method.getAnnotations())
                            .anyMatch(annotation ->
                                    testFunction.apply(annotation.annotationType()));

                    softAssertions.assertThat(hasMethodAnnotation)
                            .withFailMessage(
                                    "Class " +
                                    resourceClass.getName() +
                                    " name Unexpected " +
                                    annotationType +
                                    " annotations on " +
                                    method.getName())
                            .isFalse();
                });
    }


    // --------------------------------------------------------------------------------


    private static class MethodSignature {

        private final String name;
        private final Class<?>[] parameterTypes;

        public MethodSignature(final String name, final Class<?>[] parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        public String getName() {
            return name;
        }

        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MethodSignature that = (MethodSignature) o;
            return Objects.equals(name, that.name) && Arrays.equals(parameterTypes, that.parameterTypes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name);
            result = 31 * result + Arrays.hashCode(parameterTypes);
            return result;
        }

        @Override
        public String toString() {
            return "MethodSignature{" +
                   "name='" + name + '\'' +
                   ", parameterTypes=" + Arrays.toString(parameterTypes) +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    private interface MyInterfaceOne extends RestResource {

        @Unauthenticated
        String methodOne();

        String methodTwo();

        String methodThree();
    }


    // --------------------------------------------------------------------------------


    private static class MyClassOne implements MyInterfaceOne {

        @Override
        public String methodOne() {
            return null;
        }

        @Unauthenticated
        @Override
        public String methodTwo() {
            return null;
        }

        @Override
        public String methodThree() {
            return null;
        }
    }
}
