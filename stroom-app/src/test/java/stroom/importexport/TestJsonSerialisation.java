package stroom.importexport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import stroom.util.json.JsonUtil;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

class TestJsonSerialisation {
    @Test
    void test() {
        final ObjectMapper objectMapper = JsonUtil.getMapper();

        String pkg = "stroom";
        String routeAnnotation = JsonCreator.class.getCanonicalName();// "com.fasterxml.jackson.annotation.JsonCreator";
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAllInfo()             // Scan classes, methods, fields, annotations
                             .whitelistPackages(pkg)      // Scan com.xyz and subpackages (omit to scan all packages)
                             .scan()) {                   // Start the scan
            for (ClassInfo routeClassInfo : scanResult.getClassesWithMethodAnnotation(routeAnnotation)) {
                try {
                    Class<?> clazz = routeClassInfo.loadClass();
                    Constructor<?> constructor = clazz.getConstructor();
                    Object o = constructor.newInstance();

                    String json1 = objectMapper.writeValueAsString(o);
                    Object o2 = objectMapper.readValue(json1, clazz);
                    String json2 = objectMapper.writeValueAsString(o2);

                    if (json1.equals(json2)) {
                        System.out.println(routeClassInfo.getName());
                    } else {
                        System.err.println(routeClassInfo.getName());
                    }
//                    Assertions.assertThat(json1).isEqualTo(json2);

                } catch (final NoSuchMethodException e) {
//                    System.err.println("No default constructor: " + routeClassInfo.getName());
                } catch (final RuntimeException |
                        IOException |
                        InvocationTargetException |
                        InstantiationException |
                        IllegalAccessException e) {
                    System.err.println(e.getMessage());
                }
//                AnnotationInfo routeAnnotationInfo = routeClassInfo.getAnnotationInfo(routeAnnotation);
//                List<AnnotationParameterValue> routeParamVals = routeAnnotationInfo.getParameterValues();
//                // @com.xyz.Route has one required parameter
//                String route = (String) routeParamVals.get(0).getValue();
//                System.out.println(routeClassInfo.getName() + " is annotated with route " + route);
            }
        }
    }
}
