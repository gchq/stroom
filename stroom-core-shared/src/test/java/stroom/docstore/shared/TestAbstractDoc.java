/*
 * Copyright 2026 Crown Copyright
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

import stroom.docs.shared.Description;
import stroom.docs.shared.NotDocumented;
import stroom.test.common.docs.StroomDocsUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestAbstractDoc {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAbstractDoc.class);
    private static final Class<Description> DOCUMENTATION_CLASS = Description.class;

    @Disabled // Enable this once all the docs have documentation
    @Test
    void ensureDocDocumentation() {
        StroomDocsUtil.doWithClassScanResult(scanResult -> {
            final AtomicInteger count = new AtomicInteger();

            streamAbstractDocSubClasses(scanResult)
                    .filter(classInfo -> !classInfo.hasAnnotation(DOCUMENTATION_CLASS))
                    .filter(classInfo -> !classInfo.hasAnnotation(NotDocumented.class))
                    .forEach(classInfo -> {
                        final Class<?> clazz = loadClass(classInfo.getName());
                        LOGGER.error("Document class {} is missing annotation {}. " +
                                     "It's time to write some documentation.",
                                clazz.getName(),
                                DOCUMENTATION_CLASS.getName());
                        count.incrementAndGet();
                    });

            assertThat(count)
                    .withFailMessage(
                            LogUtil.message(
                                    "Found subclasses of {} that are missing annotation {}, see logs for details.",
                                    AbstractDoc.class.getName(), DOCUMENTATION_CLASS.getName()))
                    .hasValue(0);
        });
    }

    private Stream<ClassInfo> streamAbstractDocSubClasses(final ScanResult scanResult) {
        return scanResult.getAllSubclasses(AbstractDoc.class)
                .stream()
                .filter(classInfo -> !classInfo.isInterface())
                .filter(classInfo -> !classInfo.isAbstract());
    }

    private static Class<?> loadClass(final String className) {
        try {
            return Class.forName(className, false, TestAbstractDoc.class.getClassLoader());
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
