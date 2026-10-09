package stroom.pipeline.xsltfunctions;

import stroom.test.common.docs.StroomDocsUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a test as such, more of a QA to ensure our XSLT functions have annotations on them so
 * we can document them.
 */
public class TestXsltFunctions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestXsltFunctions.class);

    @Test
    void allFunctionClassesHaveAnnotations() {
        StroomDocsUtil.doWithClassScanResult(scanResult -> {
            final AtomicInteger count = new AtomicInteger();

            streamFunctionClasses(scanResult)
                    .filter(classInfo -> !classInfo.hasAnnotation(XsltFunctionDef.class))
                    .forEach(classInfo -> {
                        final Class<?> clazz = classInfo.loadClass();
                        LOGGER.error("XSLT Function {} is missing annotation {}. " +
                                     "It's time to write some documentation.",
                                clazz.getName(),
                                XsltFunctionDef.class.getName());
                        count.incrementAndGet();
                    });

            assertThat(count)
                    .hasValue(0);
        });
    }

    @Test
    void checkFunctionAnnotations() {
        StroomDocsUtil.doWithClassScanResult(scanResult -> {
            final Map<String, Class<?>> nameToClassMap = new HashMap<>();
            final AtomicInteger count = new AtomicInteger();
            streamFunctionClasses(scanResult)
                    .filter(classInfo -> classInfo.hasAnnotation(XsltFunctionDef.class))
                    .forEach(classInfo -> {
                        final Class<?> clazz = classInfo.loadClass();
                        final XsltFunctionDef anno = clazz.getAnnotation(XsltFunctionDef.class);
                        final String funcName = anno.name();
                        assertThat(funcName)
                                .withFailMessage(() -> LogUtil.message(
                                        "Function {} does not have a name in its {} annotation",
                                        clazz.getName(), XsltFunctionDef.class))
                                .isNotBlank();

                        assertThat(anno.commonDescription())
                                .withFailMessage(() -> LogUtil.message(
                                        "Function {} does not have a commonDescription in its {} annotation",
                                        clazz.getName(), XsltFunctionDef.class))
                                .isNotBlank();

                        checkSignatures(anno, clazz);

                        final Class<?> prevVal = nameToClassMap.put(funcName, clazz);
                        if (prevVal != null) {
                            Assertions.fail(
                                    "Function name '{}' used by at least two different classes {} and {}",
                                    prevVal.getName(), clazz.getName());
                        }
                        count.incrementAndGet();
                    });
            LOGGER.info("Found {} annotated XSLT functions", count);
        });
    }

    private static void checkSignatures(final XsltFunctionDef anno, final Class<?> clazz) {
        final int sigCount = NullSafe.size(anno.signatures());

        assertThat(sigCount)
                .withFailMessage(() -> LogUtil.message(
                        "Function {} does not have any signatures in its {} annotation",
                        clazz.getName(), XsltFunctionDef.class))
                .isGreaterThan(0);

        if (sigCount == 1) {
            final XsltFunctionSignature signature = anno.signatures()[0];
            assertThat(signature.description())
                    .withFailMessage(() -> LogUtil.message(
                            "The signature in function {} should not have a description " +
                            "in the signature in its {} annotation. " +
                            "When a function has just one signature, " +
                            "the description should be in commonDescription, not on the signature.",
                            clazz.getName(), XsltFunctionDef.class))
                    .isBlank();
            checkSignature(anno, clazz, signature, 1, sigCount);
        } else {
            // Multiple sigs so each one must have its own description
            final XsltFunctionSignature[] signatures = anno.signatures();
            for (int i = 0; i < signatures.length; i++) {
                final int sigNo = i + 1;
                final XsltFunctionSignature signature = signatures[i];
                assertThat(signature.description())
                        .withFailMessage(() -> LogUtil.message(
                                "Signature {} in function {} does not have a description " +
                                "in its {} annotation. When a function has more than one signature, " +
                                "each signature must have its own description.",
                                sigNo, clazz.getName(), XsltFunctionDef.class))
                        .isNotBlank();

                checkSignature(anno, clazz, signature, sigNo, sigCount);
            }
        }
    }

    private static void checkSignature(final XsltFunctionDef anno,
                                       final Class<?> clazz,
                                       final XsltFunctionSignature signature,
                                       final int sigNo,
                                       final int sigCount) {

        final XsltFunctionCategory[] categories = signature.category();

        assertThat(NullSafe.size(categories))
                .withFailMessage(() -> LogUtil.message(
                        "Function {}, signature {}, must have at most one category",
                        clazz.getName(), sigNo))
                .isLessThanOrEqualTo(1);

        if (sigCount == 1) {
            assertThat(NullSafe.size(signature.returnType()))
                    .withFailMessage(() -> LogUtil.message(
                            "Function {}, signature {}, should not set returnType. Instead set commonReturnType " +
                            "on the parent annotation.",
                            clazz.getName(), sigNo))
                    .isZero();

            assertThat(signature.returnDescription())
                    .withFailMessage(() -> LogUtil.message(
                            "Function {}, signature {}, should not set returnDescription. Instead set " +
                            "commonReturnDescription on the parent annotation.",
                            clazz.getName(), sigNo))
                    .isEmpty();
        } else {
            // Multiple signatures
            if (NullSafe.firstNonNull(anno.commonReturnType()).isPresent()) {
                // Common return type so sig should not set the return type
                assertThat(NullSafe.firstNonNull(signature.returnType()))
                        .withFailMessage(() -> LogUtil.message(
                                "Function {}, signature {}, returnType should not be set as commonReturnType is set",
                                clazz.getName(), sigNo))
                        .isEmpty();
            } else {
                assertThat(NullSafe.firstNonNull(signature.returnType()))
                        .withFailMessage(() -> LogUtil.message(
                                "Function {}, signature {}, returnType should be set as commonReturnType is not set",
                                clazz.getName(), sigNo))
                        .isPresent();

            }

            if (NullSafe.isNonBlankString(anno.commonReturnDescription())) {
                assertThat(signature.returnDescription())
                        .withFailMessage(() -> LogUtil.message(
                                "Function {}, signature {}, returnDescription should not be set as " +
                                "commonReturnDescription is set.",
                                clazz.getName(), sigNo))
                        .isEmpty();
            } else {
                assertThat(signature.returnDescription())
                        .withFailMessage(() -> LogUtil.message(
                                "Function {}, signature {}, returnDescription should be set as " +
                                "commonReturnDescription is not set.",
                                clazz.getName(), sigNo))
                        .isNotBlank();
            }
        }

        checkSignatureArgs(anno, clazz, signature, sigNo);
    }

    private static void checkSignatureArgs(final XsltFunctionDef anno,
                                           final Class<?> clazz,
                                           final XsltFunctionSignature signature,
                                           final int sigNo) {
        final XsltFunctionArg[] args = signature.args();
        if (NullSafe.hasItems(args)) {
            for (int i = 0; i < args.length; i++) {
                final int argNo = i + 1;
                final XsltFunctionArg arg = args[i];
                assertThat(arg.name())
                        .withFailMessage(() -> LogUtil.message(
                                "Function {}, signature {}, arg {} does not have a name",
                                clazz.getName(), sigNo, argNo))
                        .isNotBlank();

                assertThat(arg.description())
                        .withFailMessage(() -> LogUtil.message(
                                "Function {}, signature {}, arg {} does not have a description",
                                clazz.getName(), sigNo, argNo))
                        .isNotBlank();

                assertThat(arg.argType())
                        .withFailMessage(() -> LogUtil.message(
                                "Function {}, signature {}, arg {} does not have a description",
                                clazz.getName(), sigNo, argNo))
                        .isNotNull();
            }
        }
    }

    private Stream<ClassInfo> streamFunctionClasses(final ScanResult scanResult) {
        return scanResult.getSubclasses(StroomExtensionFunctionCall.class)
                .stream()
                .filter(classInfo -> !classInfo.isInterface())
                .filter(classInfo -> !classInfo.isAbstract());
    }
}
