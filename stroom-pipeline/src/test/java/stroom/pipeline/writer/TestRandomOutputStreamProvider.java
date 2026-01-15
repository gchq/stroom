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

package stroom.pipeline.writer;


import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestRandomOutputStreamProvider extends StroomUnitTest {

    @Test
    void testCycleDirs(@TempDir final Path tempDir) throws IOException {
        final FileAppender provider = buildTestObject(tempDir);

        boolean found1 = false;
        boolean found2 = false;
        boolean found3 = false;

        for (int i = 0; i < 1000; i++) {
            final Output output = provider.createOutput();
            final LockedOutput lockedOutput = (LockedOutput) output;
            final Path file = lockedOutput.lockFile;
            final String path = FileUtil.getCanonicalPath(file);

            if (path.contains("/t1/")) {
                found1 = true;
            } else if (path.contains("/t2/")) {
                found2 = true;
            } else if (path.contains("/t3/")) {
                found3 = true;
            }

            if (found1 && found2 && found3) {
                break;
            }
        }

        assertThat(found1 && found2 && found3).isTrue();
    }

    private FileAppender buildTestObject(final Path tempDir) {
        final String name = "/${year}-${month}-${day}T${hour}:${minute}:${second}.${millis}Z-${uuid}.xml";
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final FileAppender provider = new FileAppender(null, null, pathCreator);
        provider.setOutputPaths(
                FileUtil.getCanonicalPath(getCurrentTestDir()) + "/t1" + name + "," +
                        FileUtil.getCanonicalPath(getCurrentTestDir()) + "/t2" + name + "," +
                        FileUtil.getCanonicalPath(getCurrentTestDir()) + "/t3" + name);
        return provider;
    }
}
