package stroom.query.api.current;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates a text representation (a 'portrait') of a class's public methods.
 *
 * Only recurses over those classes that match a base package.
 */
public class ClassPhotographer {
    private static String newLine = System.getProperty("line.separator");

    public static String takePortraitOf(Class clazz, String basePackage) {
        Map<Class, List<String>> portrait = new HashMap<>();
        takePortraitOf(clazz, portrait, basePackage);
        List<String> flattenedPortrait = flattenPortrait(portrait);
        flattenedPortrait.sort(String::compareTo);
        String serialisedPortrait = flattenedPortrait.stream().collect(Collectors.joining(newLine));
        return serialisedPortrait;
    }

    private static void takePortraitOf(Class clazz, Map<Class, List<String>> portrait, String basePackage) {
        List<String> methodSignatures = new ArrayList<>();
        List<Class> classesForRecursion = new ArrayList<>();

        // We don't need to filter by 'public' because `getMethods()` only gets public methods.
        Arrays.asList(clazz.getMethods()).stream()
                .forEach(method -> {
                            methodSignatures.add(method.toGenericString());
                            classesForRecursion.addAll(getClassesForRecursion(basePackage, method));
                        });

        portrait.put(clazz, methodSignatures);

        // Time to recurse
        classesForRecursion.stream()
                .filter(typeToAnalyse -> !portrait.containsKey(typeToAnalyse))
                .forEach(typeToAnalyse -> takePortraitOf(typeToAnalyse, portrait, basePackage));
    }

    private static List<String> flattenPortrait(Map<Class, List<String>> portraitMap) {
        List<String> flattenedPortrait = new ArrayList<>();
        for (Class key : portraitMap.keySet()) {
            flattenedPortrait.addAll(portraitMap.get(key).stream()
                    .map(method -> String.format("%s - %s", key.toString(), method))
                    .collect(Collectors.toList()));
        }
        return flattenedPortrait;
    }

    /**
     * Looks at a method and gets any return or parameter types that match the basePackage.
     */
    private static List<Class> getClassesForRecursion(String basePackage, Method method){
        List<Class> classesForRecursion = new ArrayList<>();

        // Check regular return types
        if (method.getReturnType().toString().contains(basePackage)) {
            classesForRecursion.add(method.getReturnType());
        } else {
            // Check for generic return types
            if (method.getGenericReturnType() instanceof ParameterizedTypeImpl) {
                ParameterizedTypeImpl type = (ParameterizedTypeImpl) method.getGenericReturnType();
                Arrays.stream(type.getActualTypeArguments())
                        .filter(actualType -> actualType.getTypeName().contains(basePackage))
                        .forEach(actualType -> classesForRecursion.add((Class) actualType));
            }
        }

        // Check for parameter types
        Arrays.stream(method.getParameterTypes())
                .filter(paramType -> paramType.toString().contains(basePackage))
                .forEach(paramType -> classesForRecursion.add(paramType));

        return classesForRecursion;
    }
}
