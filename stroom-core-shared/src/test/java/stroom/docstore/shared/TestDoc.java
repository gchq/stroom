package stroom.docstore.shared;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Document;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestDoc {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDoc.class);

    private static final String PACKAGE_NAME = "stroom";
    public static final String METHOD_NAME = "buildDocRef";

    @Test
    void test() {
        try (final ScanResult scanResult = new ClassGraph()
                .acceptPackages(PACKAGE_NAME)  // Scan com.xyz and subpackages (omit to scan all packages)
                .enableClassInfo()
                .enableMethodInfo()
                .scan()) {
            final ClassInfoList classes = scanResult.getClassesImplementing(Document.class);
            final List<String> classNames = new ArrayList<>();
            for (final ClassInfo classInfo : classes) {
                LOGGER.debug("class: {}", classInfo.getName());
                if (!classInfo.isAbstract() && !classInfo.isInterface()) {
                    final MethodInfoList methodInfoList = classInfo.getMethodInfo(METHOD_NAME);
                    if (methodInfoList.isEmpty()) {
                        LOGGER.error("Missing '{}' method on class: {}", METHOD_NAME, classInfo.getName());
                        classNames.add(classInfo.getName());
                    }
                }
            }
            Collections.sort(classNames);

            Assertions.assertThat(classNames)
                    .withFailMessage(() -> LogUtil.message("""
                                    Expecting the following classes to have a method like this:

                                    /**
                                     * @return A new builder for creating a {@link DocRef} for this document's type.
                                     */
                                    public static DocRef.TypedBuilder buildDocRef() {
                                        return DocRef.builder(TYPE);
                                    }

                                    Failing classes:
                                    {}

                                    Please add 'buildDocRef' to each.""",
                            LogUtil.toPaddedMultiLine("  ", classNames)))
                    .isEmpty();
        }
    }
}
