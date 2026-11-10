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

package stroom.test.common;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a test class or test method as a performance test, e.g. a benchmark or a timing
/// comparison, rather than a test of behaviour.
///
/// Performance tests are excluded from the normal `test` task, so they do not slow down the
/// build. Run them with the `performanceTest` task, e.g.
///
/// ```
/// ./gradlew performanceTest
/// ./gradlew :stroom-lmdb:performanceTest
/// ```
///
/// This only tags the test, so a test method still needs `@Test` (or `@TestFactory` etc.).
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag(PerformanceTest.TAG)
public @interface PerformanceTest {

    /// The JUnit tag used to include or exclude performance tests.
    String TAG = "performance";
}
