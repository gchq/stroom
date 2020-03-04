package stroom.importexport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.fusesource.restygwt.client.DirectRestService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.json.JsonUtil;
import stroom.util.shared.RestResource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1. Discover which classes are used by Resources and don't have JSON annotations
 * 2. Discover classes with JSON annotations that don't include @JsonInclude
 * 3. Discover classes with JSON annotations that don't include @JsonCreator
 * 4. Identify classes in .shared. packages that don't have JSON annotations.
 * 5. Ensure all JSON annotated classes default serialisation behaviour is consistent.
 * 6. Build complex JSON annotated objects and perform serialisation testing.
 * 7. Ensure all JSON classes that use Map have a string key
 */
class TestJsonSerialisation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestJsonSerialisation.class);
    private static final String PACKAGE_NAME = "stroom";
    private static final String PACKAGE_START = PACKAGE_NAME + ".";
//
//
//    @Test
//    void test() {
//        final ObjectMapper objectMapper = JsonUtil.getMapper();
//
//        String routeAnnotation = JsonCreator.class.getName();// "com.fasterxml.jackson.annotation.JsonCreator";
//        try (ScanResult scanResult =
//                     new ClassGraph()
//                             .enableAllInfo()             // Scan classes, methods, fields, annotations
//                             .whitelistPackages(PACKAGE_NAME)      // Scan com.xyz and subpackages (omit to scan all packages)
//                             .scan()) {                   // Start the scan
//            for (ClassInfo routeClassInfo : scanResult.getClassesWithMethodAnnotation(routeAnnotation)) {
//                try {
//                    Class<?> clazz = routeClassInfo.loadClass();
//                    Constructor<?> constructor = clazz.getConstructor();
//                    Object o = constructor.newInstance();
//
//                    String json1 = objectMapper.writeValueAsString(o);
//                    Object o2 = objectMapper.readValue(json1, clazz);
//                    String json2 = objectMapper.writeValueAsString(o2);
//
//                    if (json1.equals(json2)) {
//                        System.out.println(routeClassInfo.getName());
//                    } else {
//                        System.err.println(routeClassInfo.getName());
//                    }
////                    Assertions.assertThat(json1).isEqualTo(json2);
//
//                } catch (final NoSuchMethodException e) {
////                    System.err.println("No default constructor: " + routeClassInfo.getName());
//                } catch (final RuntimeException |
//                        IOException |
//                        InvocationTargetException |
//                        InstantiationException |
//                        IllegalAccessException e) {
//                    System.err.println(e.getMessage());
//                }
////                AnnotationInfo routeAnnotationInfo = routeClassInfo.getAnnotationInfo(routeAnnotation);
////                List<AnnotationParameterValue> routeParamVals = routeAnnotationInfo.getParameterValues();
////                // @com.xyz.Route has one required parameter
////                String route = (String) routeParamVals.get(0).getValue();
////                System.out.println(routeClassInfo.getName() + " is annotated with route " + route);
//            }
//        }
//    }

    /**
     * Tests that constructing an object with the default constructor, serialising, de-serialising, re-serialising,
     * looks the same.
     */
    @Test
    void testDefaultValues() {
        final ObjectMapper objectMapper = JsonUtil.getMapper();
        final Map<String, String> classErrors = new HashMap<>();
        for (final Class<?> clazz : getResourceRelatedClasses()) {
            final String className = clazz.getName();
            LOGGER.info(className);

            try {
                // Try and find the no args constructor if there is any.
                if (!Modifier.isInterface(clazz.getModifiers()) && !Modifier.isAbstract(clazz.getModifiers())) {
                    final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                    Constructor<?> noArgsConstructor = null;
                    for (final Constructor<?> constructor : constructors) {
                        if (constructor.getParameterCount() == 0) {
                            noArgsConstructor = constructor;
                        }
                    }

                    if (noArgsConstructor != null) {
                        noArgsConstructor.setAccessible(true);

                        Object o = noArgsConstructor.newInstance();
                        String json1 = objectMapper.writeValueAsString(o);
                        Object o2 = objectMapper.readValue(json1, clazz);
                        String json2 = objectMapper.writeValueAsString(o2);

                        if (!json1.equals(json2)) {
                            classErrors.put(className, json1 + " != " + json2);
                        }
                    }
                }
            } catch (final Exception e) {
                classErrors.put(className, e.getMessage());
            }
        }

        dumpErrors(classErrors);

        assertThat(classErrors.size()).isZero();
    }

    /**
     * Test that all objects that will be serialised as JSON and contain maps do not use anything other than String as
     * map keys as this isn't serialisable.
     */
    @Test
    void testNoComplexMaps() {
        final Map<String, String> classErrors = new HashMap<>();
        for (final Class<?> clazz : getResourceRelatedClasses()) {
            final String className = clazz.getName();
            LOGGER.info(className);

            try {
                // Try and find the no args constructor if there is any.
                if (!Modifier.isInterface(clazz.getModifiers())) {
                    final Field[] fields = clazz.getDeclaredFields();
                    for (final Field field : fields) {
                        if (Map.class.isAssignableFrom(field.getType())) {
                            final ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
                            final Type keyType = parameterizedType.getActualTypeArguments()[0];
                            if (!(keyType instanceof Class && ((Class<?>) keyType).isEnum()) &&
                                    !String.class.getName().equals(keyType.getTypeName())) {
                                classErrors.put(className, "Bad key Type");
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                classErrors.put(className, e.getMessage());
            }
        }

        dumpErrors(classErrors);

        assertThat(classErrors.size()).isZero();
    }

    /**
     * Test that classes that will be subject to JSON serialisation have no extra properties or redundant JSON
     * annotations.
     */
    @Test
    void testNoExtraProps() {
        final Map<String, String> classErrors = new HashMap<>();
        for (final Class<?> clazz : getResourceRelatedClasses()) {
            final String className = clazz.getName();
            LOGGER.info(className);

            try {
                // Try and find the no args constructor if there is any.
                if (!Modifier.isInterface(clazz.getModifiers()) && !clazz.isEnum()) {
                    final Set<String> fieldNames = new HashSet<>();
                    final Field[] fields = clazz.getDeclaredFields();
                    for (final Field field : fields) {
                        final JsonIgnore jsonIgnore = field.getDeclaredAnnotation(JsonIgnore.class);
                        if (jsonIgnore == null) {
                            final String fieldName = normaliseFieldName(field.getName());
                            fieldNames.add(fieldName);
                        }
                    }

                    final Set<String> getters = new HashSet<>();
                    final Set<String> setters = new HashSet<>();
                    final Set<String> uselessIgnore = new HashSet<>();
                    final Method[] methods = clazz.getDeclaredMethods();
                    for (final Method method : methods) {
                        final JsonIgnore jsonIgnore = method.getDeclaredAnnotation(JsonIgnore.class);
                        final String methodName = method.getName();
                        final boolean getter = isGetter(method);
                        final boolean setter = isSetter(method);
                        if (!getter && !setter) {
                            if (jsonIgnore != null) {
                                uselessIgnore.add(methodName);
                            }
                        } else if (jsonIgnore == null) {
                            final String fieldName = convertMethodToFieldName(methodName);
                            if (setter) {
                                setters.add(fieldName);
                            } else {
                                getters.add(fieldName);
                            }
                        }
                    }

                    final Set<String> additionalGetters = new HashSet<>(getters);
                    additionalGetters.removeAll(fieldNames);
                    if (additionalGetters.size() > 0) {
                        classErrors.put(className, "Additional getters: " + additionalGetters.toString());
                    }

                    final Set<String> additionalSetters = new HashSet<>(setters);
                    additionalSetters.removeAll(fieldNames);
                    if (additionalSetters.size() > 0) {
                        classErrors.put(className, "Additional setters: " + additionalSetters.toString());
                    }

                    if (uselessIgnore.size() > 0) {
                        classErrors.put(className, "Useless ignore: " + uselessIgnore.toString());
                    }
                }
            } catch (final Exception e) {
                classErrors.put(className, e.getMessage());
            }
        }

        dumpErrors(classErrors);

        assertThat(classErrors.size()).isZero();
    }

    private void dumpErrors(final Map<String, String> classErrors) {
        classErrors.forEach((className, msg) ->
            LOGGER.error("Class {} has error: {}", className, msg));
    }

    /**
     * Test that classes that will be subject to JSON serialisation have the full complement of JSON annotations.
     */
    @Test
    void testJsonAnnotations() {
        final Map<String, String> classErrors = new HashMap<>();
        for (final Class<?> clazz : getResourceRelatedClasses()) {
            final String className = clazz.getName();
            LOGGER.info(className);

            try {
                // Try and find the no args constructor if there is any.
                if (!Modifier.isInterface(clazz.getModifiers()) && !Modifier.isAbstract(clazz.getModifiers()) && !clazz.isEnum()) {
                    final boolean hasJsonInclude = clazz.getAnnotation(JsonInclude.class) != null;
                    final boolean hasJsonPropertyOrder = clazz.getAnnotation(JsonPropertyOrder.class) != null;
                    int jsonCreatorCount = 0;
                    final Set<String> fieldsWithoutAnnotations = new HashSet<>();
                    final Set<String> methodsWithAnnotations = new HashSet<>();

                    for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                        final JsonCreator jsonCreator = constructor.getAnnotation(JsonCreator.class);
                        if (jsonCreator != null) {
                            jsonCreatorCount++;
                        }
                    }

                    final Field[] fields = clazz.getDeclaredFields();
                    for (final Field field : fields) {
                        final JsonIgnore jsonIgnore = field.getDeclaredAnnotation(JsonIgnore.class);
                        final JsonProperty jsonProperty = field.getDeclaredAnnotation(JsonProperty.class);
                        if (jsonIgnore == null && jsonProperty == null && !Modifier.isStatic(field.getModifiers())) {
                            String fieldName = field.getName();
                            fieldsWithoutAnnotations.add(fieldName);
                        }
                    }

                    final Method[] methods = clazz.getDeclaredMethods();
                    for (final Method method : methods) {
                        final JsonProperty jsonProperty = method.getDeclaredAnnotation(JsonProperty.class);
                        if (jsonProperty != null) {
                            methodsWithAnnotations.add(method.getName());
                        }
                    }

                    final StringBuilder sb = new StringBuilder();
                    if (!hasJsonInclude) {
                        sb.append("\nNo JsonInclude");
                    }
//                    if (!hasJsonPropertyOrder) {
//                        sb.append("\nNo JsonPropertyOrder");
//                    }
                    if (jsonCreatorCount != 1) {
                        sb.append("\nJsonCreatorCount=");
                        sb.append(jsonCreatorCount);
                    }
                    if (fieldsWithoutAnnotations.size() > 0) {
                        sb.append("\nfieldsWithoutAnnotations=");
                        sb.append(fieldsWithoutAnnotations.toString());
                    }
                    if (methodsWithAnnotations.size() > 0) {
                        sb.append("\nmethodsWithAnnotations=");
                        sb.append(methodsWithAnnotations.toString());
                    }
                    if (sb.length() > 0) {
                        if (jsonCreatorCount != 1) {
                            // We have a non fully annotated class so check that we have all getters and setters.
                            final String check = checkAllGettersAndSetters(clazz);
                            if (check.length() > 0) {
                                classErrors.put(className, check);
                            }

                        } else {
                            classErrors.put(className, sb.toString().trim());
                        }
                    }
                }
            } catch (final Exception e) {
                classErrors.put(className, e.getMessage());
            }
        }

        dumpErrors(classErrors);

        assertThat(classErrors.size()).isZero();
    }

    @Test
    void testAllSharedAreResources() {
        final List<Class<?>> resourceRelatedClasses = getResourceRelatedClasses();
        final List<Class<?>> sharedClasses = getSharedClasses();

        sharedClasses.removeAll(resourceRelatedClasses);

        LOGGER.info(sharedClasses.toString());

    }

    private String checkAllGettersAndSetters(final Class<?> clazz) {
        final StringBuilder sb = new StringBuilder();

        final String className = clazz.getName();
        LOGGER.info(className);

        try {
            // Try and find the no args constructor if there is any.
            if (!Modifier.isInterface(clazz.getModifiers()) && !clazz.isEnum()) {
                final Set<String> fieldNames = new HashSet<>();
                final Field[] fields = clazz.getDeclaredFields();
                for (final Field field : fields) {
                    final JsonIgnore jsonIgnore = field.getDeclaredAnnotation(JsonIgnore.class);
                    if (jsonIgnore == null && !Modifier.isStatic(field.getModifiers())) {
                        final String fieldName = normaliseFieldName(field.getName());
                        fieldNames.add(fieldName);
                    }
                }

                final Set<String> getters = new HashSet<>();
                final Set<String> setters = new HashSet<>();
                final Method[] methods = clazz.getDeclaredMethods();
                for (final Method method : methods) {
                    final JsonIgnore jsonIgnore = method.getDeclaredAnnotation(JsonIgnore.class);
                    final String methodName = method.getName();
                    if (jsonIgnore == null) {
                        final boolean getter = isGetter(method);
                        final boolean setter = isSetter(method);
                        if (getter || setter) {
                            final String fieldName = convertMethodToFieldName(methodName);
                            if (setter) {
                                setters.add(fieldName);
                            } else {
                                getters.add(fieldName);
                            }
                        }
                    }
                }

                if (!getters.containsAll(fieldNames)) {
                    final Set<String> missing = new HashSet<>(fieldNames);
                    missing.removeAll(getters);
                    sb.append("\nMissing getters ");
                    sb.append(missing.toString());
                }

                if (!setters.containsAll(fieldNames)) {
                    final Set<String> missing = new HashSet<>(fieldNames);
                    missing.removeAll(setters);
                    sb.append("\nMissing setters ");
                    sb.append(missing.toString());
                }

                if (!fieldNames.containsAll(getters)) {
                    final Set<String> missing = new HashSet<>(fieldNames);
                    missing.removeAll(getters);
                    sb.append("\nOrphan fields ");
                    sb.append(missing.toString());
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return sb.toString().trim();
    }

    private String normaliseFieldName(final String fieldName) {
        if (fieldName.startsWith("_")) {
            return fieldName.substring(1);
        }
        return fieldName;
    }

    private boolean isGetter(final Method method) {
        return !Modifier.isAbstract(method.getModifiers()) &&
                Modifier.isPublic(method.getModifiers()) &&
                method.getParameterCount() == 0 &&
                (method.getName().startsWith("is") || method.getName().startsWith("get"));
    }

    private boolean isSetter(final Method method) {
        return !Modifier.isAbstract(method.getModifiers()) &&
                Modifier.isPublic(method.getModifiers()) &&
                method.getParameterCount() == 1 &&
                method.getName().startsWith("set");
    }

    private String convertMethodToFieldName(final String methodName) {
        String name = methodName;
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get")) {
            name = name.substring(3);
        } else if (name.startsWith("set")) {
            name = name.substring(3);
        }

        if (name.length() > 0) {
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        return name;
    }

    private void addPublicMethods(final Set<Class<?>> stroomClasses, final Class<?> clazz) {
        final Method[] methods = clazz.getMethods();
        for (final Method method : methods) {
            if (Modifier.isPublic(method.getModifiers()) && !method.getName().equals("getClass")) {
                // Add method parameters.
                final Class<?>[] parameterTypes = method.getParameterTypes();
                final Type[] genericParameterTypes = method.getGenericParameterTypes();

                for (int i = 0; i < parameterTypes.length; i++) {
                    addType(stroomClasses, genericParameterTypes[i]);
                }

                // Add return type.
                addType(stroomClasses, method.getGenericReturnType());
            }
        }
    }

    private void addType(final Set<Class<?>> stroomClasses, final Type type) {
        if (Class.class.isAssignableFrom(type.getClass())) {
            addClass(stroomClasses, (Class<?>) type, null);
        } else if (TypeVariable.class.isAssignableFrom(type.getClass())) {
            final Class<?> clazz = (Class<?>) ((TypeVariable<?>) type).getGenericDeclaration();
            addClass(stroomClasses, clazz, null);

        } else if (ParameterizedType.class.isAssignableFrom(type.getClass())) {
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            addType(stroomClasses, parameterizedType.getRawType());
            for (final Type arg : parameterizedType.getActualTypeArguments()) {
                addType(stroomClasses, arg);
            }

        } else {
            throw new RuntimeException("Didn't expect to get here");
        }
    }

    private void addClass(final Set<Class<?>> stroomClasses, final Class<?> clazz, final Type type) {
        if (clazz.isArray()) {
            addClass(stroomClasses, clazz.getComponentType(), null);

        } else if (type != null && Map.class.isAssignableFrom(clazz)) {
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            for (final Type arg : parameterizedType.getActualTypeArguments()) {
                addType(stroomClasses, arg);
            }

        } else if (type != null && Collection.class.isAssignableFrom(clazz)) {
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            for (final Type arg : parameterizedType.getActualTypeArguments()) {
                addType(stroomClasses, arg);
            }

        } else if (clazz.getName().startsWith(PACKAGE_START)) {
            // IF the class references sub classes then include those too.
            final JsonSubTypes jsonSubTypes = clazz.getAnnotation(JsonSubTypes.class);
            if (jsonSubTypes != null) {
                for (final JsonSubTypes.Type subType : jsonSubTypes.value()) {
                    final Class<?> subTypeClass = subType.value();
                    addClass(stroomClasses, subTypeClass, null);
                }
            }

            if (!stroomClasses.contains(clazz)) {
                stroomClasses.add(clazz);
                addFields(stroomClasses, clazz);
            }
        }
    }

    private void addFields(final Set<Class<?>> stroomClasses, final Class<?> parentClazz) {
        final Field[] fields = parentClazz.getDeclaredFields();
        for (final Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                addType(stroomClasses, field.getGenericType());
//                addClass(stroomClasses, field.getType(), field.getGenericType());
            }
        }

        final Class<?> superClazz = parentClazz.getSuperclass();
        if (superClazz != null && superClazz.getName().startsWith(PACKAGE_START)) {
            for (final Constructor<?> constructor : parentClazz.getDeclaredConstructors()) {
                final JsonCreator jsonCreator = constructor.getAnnotation(JsonCreator.class);
                if (jsonCreator != null) {
                    for (final Type parameterType : constructor.getGenericParameterTypes()) {
                        addType(stroomClasses, parameterType);
                    }
                }
            }
        }
    }

    private List<Class<?>> getResourceRelatedClasses() {
        final Set<Class<?>> stroomClasses = new HashSet<>();
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAllInfo()             // Scan classes, methods, fields, annotations
                             .whitelistPackages(PACKAGE_NAME)      // Scan com.xyz and subpackages (omit to scan all packages)
                             .scan()) {                   // Start the scan
            for (ClassInfo routeClassInfo : scanResult.getClassesImplementing(DirectRestService.class.getName())) {
                final Class<?> clazz = routeClassInfo.loadClass();
                addPublicMethods(stroomClasses, clazz);
            }
            for (ClassInfo routeClassInfo : scanResult.getClassesImplementing(RestResource.class.getName())) {
                final Class<?> clazz = routeClassInfo.loadClass();
                addPublicMethods(stroomClasses, clazz);
            }
        }

        return stroomClasses
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toList());
    }

    private List<Class<?>> getSharedClasses() {
        final Set<Class<?>> stroomClasses = new HashSet<>();
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAllInfo()
                             .whitelistPackages(PACKAGE_NAME)
                             .scan()) {
            for (ClassInfo routeClassInfo : scanResult.getAllClasses()) {
                if (routeClassInfo.getName().contains(".shared.") &&
                        !routeClassInfo.getName().contains("hadoopcommonshaded") &&
                        !routeClassInfo.getName().contains("Util") &&
                        !routeClassInfo.getName().contains("$") &&
                        !routeClassInfo.getName().contains("_")) {
                    try {
                        final Class<?> clazz = routeClassInfo.loadClass();
                        if (!Modifier.isInterface(clazz.getModifiers()) &&
                                !Modifier.isAbstract(clazz.getModifiers()) &&
                                !RestResource.class.isAssignableFrom(clazz) &&
                                !DirectRestService.class.isAssignableFrom(clazz)) {
                            stroomClasses.add(clazz);
                        }
                    } catch (final IllegalArgumentException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                }
            }
        }

        return stroomClasses
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toList());
    }
}

