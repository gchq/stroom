package stroom.pipeline.xsltfunctions;

import stroom.test.common.docs.StroomDocsUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a test as such, more of a QA to ensure our XSLT functions have annotations on them so
 * we can document them.
 */
public class TestXsltFunctions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestXsltFunctions.class);

    @Test
    void findClassesWithNoAnnotation() {
        StroomDocsUtil.doWithClassScanResult(scanResult -> {
            final long count = streamFunctionClasses(scanResult)
                    .filter(classInfo -> !classInfo.hasAnnotation(XsltFunctionDef.class))
                    .peek(classInfo -> {
                        final Class<?> clazz = classInfo.loadClass();
                        LOGGER.error("XSLT Function {} is missing annotation {}. Please add it",
                                clazz.getName(),
                                XsltFunctionDef.class.getName());
                    })
                    .count();

            // TODO un-comment once all the annotations are added
//            assertThat(count)
//                    .isZero();
        });
    }

    @Test
    void checkFunctionAnnotations() {
        StroomDocsUtil.doWithClassScanResult(scanResult -> {
            final Map<String, Class<?>> nameToClassMap = new HashMap<>();
            final long count = streamFunctionClasses(scanResult)
                    .filter(classInfo -> classInfo.hasAnnotation(XsltFunctionDef.class))
                    .peek(classInfo -> {
                        final Class<?> clazz = classInfo.loadClass();
                        final XsltFunctionDef anno = clazz.getAnnotation(XsltFunctionDef.class);
                        final String funcName = anno.name();
                        assertThat(funcName)
                                .withFailMessage(() -> LogUtil.message(
                                        "Function {} does not have a name in its {} annotation",
                                        clazz.getName(), XsltFunctionDef.class))
                                .isNotBlank();

                        assertThat(anno.signatures())
                                .withFailMessage(() -> LogUtil.message(
                                        "Function {} does not have any signatures in its {} annotation",
                                        clazz.getName(), XsltFunctionDef.class))
                                .isNotEmpty();

                        final Class<?> prevVal = nameToClassMap.put(funcName, clazz);
                        if (prevVal != null) {
                            Assertions.fail(
                                    "Function name '{}' used by at least two different classes {} and {}",
                                    prevVal.getName(), clazz.getName());
                        }
                    })
                    .count();
            LOGGER.info("Found {} annotated XSLT functions", count);
        });
    }

    private Stream<ClassInfo> streamFunctionClasses(final ScanResult scanResult) {
        return scanResult.getSubclasses(StroomExtensionFunctionCall.class)
                .stream()
                .filter(classInfo -> !classInfo.isInterface())
                .filter(classInfo -> !classInfo.isAbstract());
    }
}
