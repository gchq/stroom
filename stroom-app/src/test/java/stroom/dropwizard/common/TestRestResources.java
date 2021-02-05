package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.shared.AutoLogged;
import stroom.util.shared.RestResource;

import com.google.common.base.Strings;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple4;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestRestResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRestResources.class);

    @TestFactory
    Stream<DynamicTest> buildQualityAssuranceTests() {
        try (ScanResult result = new ClassGraph()
                .whitelistPackages("stroom")
                .enableClassInfo()
                .ignoreClassVisibility()
                .enableAnnotationInfo()
                .scan()) {

            final List<? extends Class<? extends RestResource>> classes = result.getAllClasses()
                    .stream()
                    .filter(classInfo -> classInfo.implementsInterface(RestResource.class.getName()))
                    .map(classInfo -> (Class<? extends RestResource>) classInfo.loadClass())
                    .sorted(Comparator.comparing(Class::getName))
                    .collect(Collectors.toList());

            LOGGER.info("Found {} classes to test", classes.size());

            return classes.stream()
                    .map(resourceClass ->
                            DynamicTest.dynamicTest(
                                    resourceClass.getSimpleName() + " (" + resourceClass.getPackageName() + ")",
                                    () ->
                                            doResourceClassAsserts(resourceClass)));
        }
    }

    @Disabled // manually run only
    @Test
    void listMethods() {

        try (ScanResult result = new ClassGraph()
                .whitelistPackages("stroom")
                .enableClassInfo()
                .ignoreClassVisibility()
                .enableAnnotationInfo()
                .scan()) {

            final List<? extends Class<? extends RestResource>> classes = result.getAllClasses()
                    .stream()
                    .filter(classInfo -> classInfo.implementsInterface(RestResource.class.getName()))
                    .map(classInfo -> (Class<? extends RestResource>) classInfo.loadClass())
                    .sorted(Comparator.comparing(Class::getName))
                    .collect(Collectors.toList());

            LOGGER.info("Found {} classes to test", classes.size());

            final Map<Tuple2<String, String>, List<Tuple4<String, String, String, String>>> results = classes.stream()
                    .flatMap(clazz ->
                            Arrays.stream(clazz.getMethods())
                                    .map(method -> Tuple.of(clazz, method)))
                    .filter(clazzMethod -> hasJaxRsAnnotation(clazzMethod._1, clazzMethod._2))
                    .map(clazzMethod-> Tuple.of(
                            clazzMethod._2.getName(),
                            getJaxRsHttpMethod(clazzMethod._2),
                            getMethodSig(clazzMethod._1, clazzMethod._2),
                            getJaxRsPath(clazzMethod._2)))
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
                            final String path = Strings.padEnd(tuple4._4, 40, ' ');
                            stringBuilder
                                    .append("    ")
                                    .append(ConsoleColour.blue(path))
                                    .append(" ")
                                    .append(tuple4._3)
                                    .append("\n");
                        });
            });
            LOGGER.info("Methods:\n{}", stringBuilder.toString());
        }
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
    private String getJaxRsPath(final Method method) {
        return Arrays.stream(method.getAnnotations())
                .filter(anno -> Path.class.equals(anno.annotationType()))
                .findFirst()
                .map(annotation -> ((Path) annotation).value())
                .orElse("/");
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

        final boolean superImplementsRestResource = Arrays.stream(resourceClass.getInterfaces())
                .filter(iface ->
                        !RestResource.class.equals(iface))
                .anyMatch(iface ->
                        Arrays.asList(iface.getInterfaces())
                                .contains(RestResource.class));

        LOGGER.info("Inspecting {} {}, superImplementsRestResource; {}",
                typeName, resourceClass.getName(), superImplementsRestResource);

        SoftAssertions.assertSoftly(softAssertions -> {

            if (isInterface || !superImplementsRestResource) {
                // This is an iface or a class that implements RestResource with no iface of its own

                doSwaggerAnnotationAsserts(resourceClass, typeName, softAssertions);
            } else {
                LOGGER.info("@Api/@ApiOperation assertions handled by interface");
                // This is a class that implements an iface that extends RestResource so
                // that will be dealt with when it looks at that iface directly.
            }

            final boolean classIsAutoLogged = resourceClass.isAnnotationPresent(AutoLogged.class);
            LOGGER.info("classIsAutoLogged: {}", classIsAutoLogged);

            if (!isInterface) {
                // AutoLogged is only used on classes, not interfaces

                assertProviders(resourceClass, softAssertions);

                Arrays.stream(resourceClass.getMethods())
                        .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                        .filter(method -> hasJaxRsAnnotation(resourceClass, method))
                        .forEach(method -> {
                            final boolean methodIsAutoLogged = method.isAnnotationPresent(AutoLogged.class);

                            softAssertions.assertThat(classIsAutoLogged || methodIsAutoLogged)
                                    .withFailMessage(() -> "Method " + method.getName() + "(...) or its class must be " +
                                            "annotated with @AutoLogged")
                                    .isTrue();
                        });
            } else {
                softAssertions.assertThat(classIsAutoLogged)
                        .withFailMessage("@AutoLogged is not support on interfaces, only on impl.")
                        .isFalse();
            }
        });
    }

    private void assertProviders(final Class<? extends RestResource> resourceClass,
                                 final SoftAssertions softAssertions) {
        List<Class<?>> nonProvidedFields = Arrays.stream(resourceClass.getDeclaredFields())
                .filter(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers())
                                && !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .filter(clazz ->
                        !Provider.class.equals(clazz))
                .collect(Collectors.toList());

        if (!nonProvidedFields.isEmpty()) {
            LOGGER.warn("Non provided fields {}", nonProvidedFields);
            softAssertions.assertThat(nonProvidedFields)
                    .withFailMessage("Resource implementations/classes must inject all objects " +
                            "via Providers.")
                    .isEmpty();
        }
    }

    private boolean hasJaxRsAnnotation(final Class<?> clazz, final Method method) {
        boolean thisMethodHasJaxRs = Arrays.stream(method.getAnnotations())
                .anyMatch(annotation ->
                        annotation.annotationType().getPackageName().equals("javax.ws.rs"));
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

                return optIfaceMethod.map(ifaceMethod -> hasJaxRsAnnotation(restInterface, ifaceMethod))
                        .orElse(false);
            }
        } else {
            return true;
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

    private void doSwaggerAnnotationAsserts(final Class<? extends RestResource> resourceClass,
                                            final String typeName,
                                            final SoftAssertions softAssertions) {
        LOGGER.info("Doing @Api... asserts");

        final boolean classHasApiAnnotation = resourceClass.isAnnotationPresent(Api.class);
        final String[] apiAnnotationTags = classHasApiAnnotation
                ? resourceClass.getAnnotation(Api.class).tags()
                : new String[0];

        softAssertions.assertThat(classHasApiAnnotation)
                .withFailMessage(() -> typeName + " must have class annotation like " +
                        "@Api(tags = \"Nodes\")")
                .isTrue();

        if (classHasApiAnnotation) {
            LOGGER.info("Class has @Api annotation");
            softAssertions.assertThat(apiAnnotationTags.length)
                    .withFailMessage(() -> "@Api must have tags property set, e.g. @Api(tags = \"Nodes\")")
                    .isGreaterThanOrEqualTo(1);
            if (apiAnnotationTags.length >= 1) {
                softAssertions.assertThat(apiAnnotationTags[0])
                        .withFailMessage(() -> "@Api must have tags property set, e.g. @Api(tags = \"Nodes\")")
                        .isNotEmpty();
            }
        } else {
            LOGGER.info("Class doesn't have @Api annotation");
        }

        Arrays.stream(resourceClass.getMethods())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .filter(method -> hasJaxRsAnnotation(resourceClass, method))
                .forEach(method -> {

                    final List<Class<? extends Annotation>> methodAnnotationTypes = Arrays.stream(
                            method.getAnnotations()
                    )
                            .map(Annotation::annotationType)
                            .collect(Collectors.toList());

                    LOGGER.debug("Found annotations {}", methodAnnotationTypes);

                    final boolean hasApiOpAnno = methodAnnotationTypes.contains(ApiOperation.class);

                    softAssertions.assertThat(hasApiOpAnno)
                            .withFailMessage(() -> "Method " + method.getName() + "(...) must be annotated " +
                                    "with @ApiOperation(value = \"Some description of what the method does\")")
                            .isTrue();

                    if (hasApiOpAnno) {
                        final Class<?> methodReturnClass = method.getReturnType();
                        final ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
                        final Optional<? extends Class<?>> optApiOpResponseClass = Optional.of(apiOperation.response())
                                .filter(clazz -> !Void.class.equals(clazz));

                        // Only need to set response when Response is used
                        if (Response.class.equals(methodReturnClass)) {
                            softAssertions.assertThat(optApiOpResponseClass)
                                    .withFailMessage(() -> "Method " + method.getName() + "(...) must have response " +
                                            "set in @ApiOperation, e.g. @ApiOperation(value = \"xxx\" " +
                                            "response = Node.class)")
                                    .isPresent();
                        } else {
                            if (!Void.class.equals(methodReturnClass) && optApiOpResponseClass.isPresent()) {
                                softAssertions.fail("Method " + method.getName() + "(...) does not need " +
                                        "to have response set on @ApiOperation, remove it.");
                            }
                        }
                    }
                });
    }
}