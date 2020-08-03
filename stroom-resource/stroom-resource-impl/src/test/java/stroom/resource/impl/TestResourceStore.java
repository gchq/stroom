/*
 * Copyright 2016 Crown Copyright
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

package stroom.resource.impl;

import stroom.util.shared.ResourceKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestResourceStore {
    @TempDir
    static Path tempDir;

    @Test
    void testSimple() throws IOException {
        final ResourceStoreImpl resourceStore = new ResourceStoreImpl(() -> tempDir);
        resourceStore.execute();

        final ResourceKey key1 = resourceStore.createTempFile("TestResourceStore1.dat");
        assertThat(key1.toString().endsWith("TestResourceStore1.dat")).isTrue();

        final ResourceKey key2 = resourceStore.createTempFile("TestResourceStore2.dat");
        assertThat(key2.toString().endsWith("TestResourceStore2.dat")).isTrue();

        Files.createFile(resourceStore.getTempFile(key1));
        Files.createFile(resourceStore.getTempFile(key2));

        assertThat(Files.isRegularFile(resourceStore.getTempFile(key1))).isTrue();
        assertThat(Files.isRegularFile(resourceStore.getTempFile(key2))).isTrue();

        // Roll to Old
        resourceStore.execute();
        final Path file1 = resourceStore.getTempFile(key1);
        assertThat(Files.isRegularFile(file1)).isTrue();
        final Path file2 = resourceStore.getTempFile(key2);
        assertThat(Files.isRegularFile(file2)).isTrue();

        // Roll to Delete
        resourceStore.execute();
        assertThat(resourceStore.getTempFile(key1)).isNull();
        assertThat(Files.isRegularFile(file1)).isFalse();
        assertThat(resourceStore.getTempFile(key2)).isNull();
        assertThat(Files.isRegularFile(file2)).isFalse();
    }
}
