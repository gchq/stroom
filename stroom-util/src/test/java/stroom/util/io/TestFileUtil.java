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

package stroom.util.io;

import stroom.util.concurrent.SimpleExecutor;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestFileUtil {
    @TempDir
    static Path tempDir;

    @Test
    void testMkdirs() throws InterruptedException {
        final Path rootDir = tempDir.resolve("TestFileUtil_" + System.currentTimeMillis());

        final Path[] dirArray = new Path[10];
        for (int i = 0; i < dirArray.length; i++) {
            dirArray[i] = buildDir(rootDir);
        }
        final AtomicBoolean exception = new AtomicBoolean(false);

        final SimpleExecutor simpleExecutor = new SimpleExecutor(4);
        for (int i = 0; i < 200; i++) {
            final int count = i;
            simpleExecutor.execute(() -> {
                try {
                    final Path dir = dirArray[count % dirArray.length];
                    FileUtil.mkdirs(dir);
                } catch (final RuntimeException e) {
                    e.printStackTrace();
                    exception.set(true);
                }
            });
        }
        simpleExecutor.waitForComplete();
        simpleExecutor.stop(false);

        assertThat(exception.get()).isFalse();

        FileUtil.deleteDir(rootDir);
    }

    private Path buildDir(final Path rootDir) {
        Path path = rootDir;
        for (int i = 0; i < 10; i++) {
            path = path.resolve(String.valueOf(RandomUtils.nextInt(0, 10)));
        }
        return path;
    }

    @Test
    void testMkdirsUnableToCreate() {
        try {
            FileUtil.mkdirs(Paths.get("/dev/null"));
            fail("Not expecting that this directory can be created");
        } catch (final RuntimeException e) {
            // Ignore.
        }
    }
}
