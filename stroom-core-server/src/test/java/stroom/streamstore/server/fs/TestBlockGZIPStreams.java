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

package stroom.streamstore.server.fs;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestBlockGZIPStreams extends StroomUnitTest {
    @Test
    public void testSimple() throws IOException {
        final File testFile = File.createTempFile("test", ".bgz", getCurrentTestDir());
        FileUtil.deleteFile(testFile);
        final OutputStream os = new BufferedOutputStream(new BlockGZIPOutputFile(testFile, 100));

        for (int i = 0; i < 1000; i++) {
            os.write((i + "\n").getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        os.close();

        try (BlockGZIPInputStream bgzi = new BlockGZIPInputStream(new FileInputStream(testFile));
                final LineNumberReader in = new LineNumberReader(
                        new InputStreamReader(bgzi, StreamUtil.DEFAULT_CHARSET))) {
            String line;
            int expected = 0;
            while ((line = in.readLine()) != null) {
                Assert.assertEquals(expected, Integer.parseInt(line));
                expected++;
            }
            Assert.assertEquals(1000, expected);

            bgzi.close();
            FileUtil.deleteFile(testFile);

            Assert.assertTrue("must have been at least 5 blcoks read", bgzi.getBlockCount() > 5);
        }

    }

}
