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

package stroom.proxy.app.handler;

import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestNestedNumberedDirProvider {

    @Test
    void test1(@TempDir final Path rootDir) {
        NestedNumberedDirProvider dirProvider = NestedNumberedDirProvider.create(rootDir);

        Path numberedPath = rootDir.relativize(dirProvider.createNumberedPath());
        assertThat(numberedPath.toString())
                .isEqualTo("0/001");

        numberedPath = rootDir.relativize(dirProvider.createNumberedPath());
        assertThat(numberedPath.toString())
                .isEqualTo("0/002");

        // Create a new one, should continue the numbering
        dirProvider = NestedNumberedDirProvider.create(rootDir);

        numberedPath = rootDir.relativize(dirProvider.createNumberedPath());
        assertThat(numberedPath.toString())
                .isEqualTo("0/003");
    }

    @Test
    void test2(@TempDir final Path rootDir) {
        FileUtil.ensureDirExists(rootDir.resolve("0/998"));

        final NestedNumberedDirProvider dirProvider = NestedNumberedDirProvider.create(rootDir);

        Path numberedPath = rootDir.relativize(dirProvider.createNumberedPath());
        assertThat(numberedPath.toString())
                .isEqualTo("0/999");

        numberedPath = rootDir.relativize(dirProvider.createNumberedPath());
        assertThat(numberedPath.toString())
                .isEqualTo("1/001/001000");

        numberedPath = rootDir.relativize(dirProvider.createNumberedPath());
        assertThat(numberedPath.toString())
                .isEqualTo("1/001/001001");
    }
}
