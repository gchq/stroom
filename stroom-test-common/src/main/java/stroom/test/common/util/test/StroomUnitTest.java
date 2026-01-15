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

package stroom.test.common.util.test;

import stroom.test.common.MockMetrics;
import stroom.util.metrics.Metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public abstract class StroomUnitTest implements StroomTest {

    private Path testDir;

    @BeforeEach
    public void setup(@TempDir final Path tempDir) {
        this.testDir = tempDir;
    }

    @Override
    public Path getCurrentTestDir() {
        return testDir;
    }

    public Metrics getMetrics() {
        return new MockMetrics();
    }
}
