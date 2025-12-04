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
