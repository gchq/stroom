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

package stroom.pipeline.server.writer;

import org.junit.Assert;
import org.junit.Test;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import java.io.OutputStream;
import java.nio.file.Path;

public class TestRandomOutputStreamProvider extends StroomUnitTest {
    @Test
    public void testCycleDirs() throws Exception {
        final FileAppender provider = buildTestObject();

        boolean found1 = false;
        boolean found2 = false;
        boolean found3 = false;

        for (int i = 0; i < 1000; i++) {
            final OutputStream outputStream = provider.createOutputStream();
            final LockedOutputStream lockedOutputStream = (LockedOutputStream) outputStream;
            final Path file = lockedOutputStream.lockFile;
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

        Assert.assertTrue(found1 && found2 && found3);
    }

    private FileAppender buildTestObject() {
        final String name = "/${year}-${month}-${day}T${hour}:${minute}:${second}.${millis}Z-${uuid}.xml";
        final PathCreator pathCreator = new PathCreator(null, null, null, null, null);
        final FileAppender provider = new FileAppender(null, pathCreator);
        provider.setOutputPaths(
                FileUtil.getCanonicalPath(getCurrentTestDir()) + "/t1" + name + "," + FileUtil.getCanonicalPath(getCurrentTestDir())
                        + "/t2" + name + "," + FileUtil.getCanonicalPath(getCurrentTestDir()) + "/t3" + name);
        return provider;
    }
}
