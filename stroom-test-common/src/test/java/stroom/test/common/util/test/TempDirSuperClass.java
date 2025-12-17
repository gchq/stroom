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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public abstract class TempDirSuperClass {

    private Path instanceTempDir;

    @TempDir
    Path instanceTempDir2;

    @BeforeEach
    public void setup(@TempDir final Path tempDir) {
        Assertions.assertThat(tempDir).isNotNull();
        Assertions.assertThat(instanceTempDir2).isNotNull();

        this.instanceTempDir = tempDir;
    }

    Path getInstanceTempDir() {
        return instanceTempDir;
    }
}
