/*
 * Copyright 2017 Crown Copyright
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

package stroom.util.zip;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

import java.io.File;
import java.io.OutputStream;
import java.util.UUID;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStroomZipOutputStream extends StroomUnitTest {
    public final static int TEST_SIZE = 100;

    @Test
    public void testBigFile() throws Exception {
        final File testFile = File.createTempFile("TestStroomZipFile", ".zip", getCurrentTestDir());
        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStream(testFile);
        try {
            String uuid = null;
            OutputStream stream = null;

            for (int i = 0; i < TEST_SIZE; i++) {
                uuid = UUID.randomUUID().toString();
                stream = stroomZipOutputStream.addEntry(new StroomZipEntry(null, uuid, StroomZipFileType.Meta));
                stream.write("Header".getBytes(StreamUtil.DEFAULT_CHARSET));
                stream.close();
                stream = stroomZipOutputStream.addEntry(new StroomZipEntry(null, uuid, StroomZipFileType.Context));
                stream.write("Context".getBytes(StreamUtil.DEFAULT_CHARSET));
                stream.close();
                stream = stroomZipOutputStream.addEntry(new StroomZipEntry(null, uuid, StroomZipFileType.Data));
                stream.write("Data".getBytes(StreamUtil.DEFAULT_CHARSET));
                stream.close();
            }

            stroomZipOutputStream.close();

            final StroomZipFile stroomZipFile = new StroomZipFile(testFile);

            Assert.assertEquals(TEST_SIZE, stroomZipFile.getStroomZipNameSet().getBaseNameSet().size());

            stroomZipFile.close();
        } finally {
            Assert.assertTrue(testFile.delete());
        }
    }

    @Test
    public void testBlankProducesNothing() throws Exception {
        final File testFile = File.createTempFile("TestStroomZipFile", ".zip", getCurrentTestDir());
        final StroomZipOutputStream stroomZipOutputStream = new StroomZipOutputStream(testFile);
        stroomZipOutputStream.close();
        Assert.assertFalse("Not expecting to write a file", testFile.isFile());
    }

}
