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

package stroom.searchable;


import stroom.searchable.api.Searchable;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TestSearchable extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSearchable.class);
    private static final String STROOM_PACKAGE_NAME = "stroom";

    @Inject
    Map<String, Searchable> searchables;

    /**
     * Make sure all classes that implement {@link Searchable} also have a MapBinding to Set&lt;Searchable&gt;
     */
    @Test
    void test() {
        Assertions.assertThat(searchables)
                .isNotNull();

        LOGGER.debug("Searchables: {}", searchables.values().stream()
                .map(searchable -> searchable.getClass() + " - " + searchable.getDataSourceType())
                .sorted()
                .collect(Collectors.joining("\n")));

        final long docRefCount = searchables.values().stream()
                .flatMap(searchable -> NullSafe.stream(searchable.getDataSourceDocRefs()))
                .count();
        LOGGER.info("docRefCount: {}", docRefCount);

        try (final ScanResult scanResult = createScanResult()) {
            final ClassInfoList classInfoList = scanResult.getClassesImplementing(Searchable.class);

            final Set<Class<?>> classesNotBound = classInfoList.stream()
                    .peek(classInfo -> LOGGER.debug("classInfo: {}", classInfo))
                    .filter(Predicate.not(ClassInfo::isInterface))
                    .map(ClassInfo::loadClass)
                    .filter(clazz -> searchables.values().stream()
                            .noneMatch(searchable -> Objects.equals(searchable.getClass(), clazz)))
                    .collect(Collectors.toSet());

            // If this assertion fails, create a binding for your searchable a bit like this
            // GuiceUtil.buildMultiBinder(binder(), Searchable.class)
            //        .addBinding(ReferenceDataServiceImpl.class);
            Assertions.assertThat(classesNotBound)
                    .isEmpty();
        }
    }

    private ScanResult createScanResult() {
        return new ClassGraph()
                .enableClassInfo()
                .acceptPackages(STROOM_PACKAGE_NAME)  // Scan com.xyz and subpkgs (omit to scan all packages)
                .scan();
    }
}
