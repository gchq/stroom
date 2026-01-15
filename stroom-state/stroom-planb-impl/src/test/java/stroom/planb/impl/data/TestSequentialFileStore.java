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

package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestSequentialFileStore {

    @Test
    void test() throws IOException {
        final Path rootDir = Files.createTempDirectory("root");
        try {
            final StatePaths statePaths = new StatePaths(rootDir);
            final SequentialFileStore fileStore = new SequentialFileStore(statePaths.getStagingDir());
            assertThat(fileStore.getMinStoreId()).isEqualTo(-1);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(-1);

            int i = 0;
            addFile(rootDir, fileStore, i);

            assertThat(fileStore.getMinStoreId()).isEqualTo(0);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(0);

            for (; i < 9; i++) {
                addFile(rootDir, fileStore, i);
            }

            assertThat(fileStore.getMinStoreId()).isEqualTo(0);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(9);

            long currentId = fileStore.getMinStoreId();
            SequentialFile sequentialFile = fileStore.awaitNext(currentId);

            assertThat(fileStore.getMinStoreId()).isEqualTo(0);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(9);

            fileStore.delete(sequentialFile);

            assertThat(fileStore.getMinStoreId()).isEqualTo(1);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(9);

            for (; currentId < 10; currentId++) {
                assertThat(fileStore.getMaxStoreId()).isEqualTo(9);
                sequentialFile = fileStore.awaitNext(currentId);
                fileStore.delete(sequentialFile);
            }

            assertThat(fileStore.getMinStoreId()).isEqualTo(-1);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(-1);

        } finally {
            FileUtil.deleteDir(rootDir);
        }
    }

    private void addFile(final Path rootDir, final SequentialFileStore fileStore, final int i) throws IOException {
        final Path file = rootDir.resolve(i + ".txt");
        Files.writeString(file, "test");
        final String fileHash = FileHashUtil.hash(file);
        fileStore.add(new FileDescriptor(System.currentTimeMillis(), 1, fileHash), file, null);
    }
}
