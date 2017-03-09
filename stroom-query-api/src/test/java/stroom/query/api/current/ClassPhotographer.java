package stroom.query.api.current;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassPhotographer {

    public static String takePortraitOf(Class clazz, String basePackage) {
        Map<Class, List<String>> classAccumulator = new HashMap<>();
        takePortraitOf(clazz, classAccumulator, basePackage);
        List<String> flattenedPortrait = flattenPortrait(classAccumulator);
        flattenedPortrait.sort(String::compareTo);
        String serialisedPortrait = serialisePortrait(flattenedPortrait);
        return serialisedPortrait;
    }

    private static void takePortraitOf(Class clazz, Map<Class, List<String>> classAccumulator, String basePackage) {
        List<String> publics = new ArrayList<>();
        List<Class> typesToAnalyse = new ArrayList<>();
        // We don't need to filter by 'public' because `getMethods()` only gets public methods.
        Arrays.asList(clazz.getMethods()).stream()
                .forEach(method -> {
                            publics.add(method.toGenericString());
                            if (method.getReturnType().toString().contains(basePackage)) {
                                typesToAnalyse.add(method.getReturnType());
                            } else {
                                if (method.getGenericReturnType() instanceof ParameterizedTypeImpl) {
                                    ParameterizedTypeImpl type = (ParameterizedTypeImpl) method.getGenericReturnType();
                                    Arrays.stream(type.getActualTypeArguments())
                                            .filter(actualType -> actualType.getTypeName().contains(basePackage))
                                            .forEach(actualType -> typesToAnalyse.add((Class) actualType));
                                }
                            }
                            Arrays.stream(method.getParameterTypes())
                                    .filter(paramType -> paramType.toString().contains(basePackage))
                                    .forEach(paramType -> typesToAnalyse.add(paramType));
                        }
                );
        classAccumulator.put(clazz, publics);
        typesToAnalyse.stream()
                .filter(typeToAnalyse -> !classAccumulator.containsKey(typeToAnalyse))
                .forEach(typeToAnalyse -> takePortraitOf(typeToAnalyse, classAccumulator, basePackage));
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

    private static String serialisePortrait(List<String> flattenedPortrait) {
        String portrait = "";
        for (String line : flattenedPortrait) {
            portrait += line + "\r";
        }
        return portrait;
    }
}
