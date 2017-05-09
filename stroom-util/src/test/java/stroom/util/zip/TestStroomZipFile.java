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
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomTestUtil;
import stroom.util.test.StroomUnitTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStroomZipFile extends StroomUnitTest {
    @Test
    public void testRealZip1() throws IOException {
        File testDir = getCurrentTestDir();
        Assert.assertTrue(testDir.exists());
        final File uniqueTestDir = StroomTestUtil.createUniqueTestDir(testDir);
        Assert.assertTrue(uniqueTestDir.exists());
        final File file = File.createTempFile("TestStroomZipFile", ".zip", uniqueTestDir );
        System.out.println(file.getAbsolutePath());
        ZipOutputStream zipOutputStream = null;
        try {
            zipOutputStream = new ZipOutputStream(new FileOutputStream(file));

            zipOutputStream.putNextEntry(new ZipEntry("test/test.dat"));
            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        } finally {
            CloseableUtil.close(zipOutputStream);
        }

        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(file);

            Assert.assertEquals(stroomZipFile.getStroomZipNameSet().getBaseNameSet(),
                    new HashSet<>(Arrays.asList("test/test.dat")));

            Assert.assertNotNull(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Data));
            Assert.assertNull(stroomZipFile.getInputStream("test/test.dat", StroomZipFileType.Context));

        } finally {
            CloseableUtil.close(stroomZipFile);

            Assert.assertTrue(file.delete());
        }
    }

    @Test
    public void testRealZip2() throws IOException {
        final File file = File.createTempFile("TestStroomZipFile", ".zip",
                StroomTestUtil.createUniqueTestDir(getCurrentTestDir()));
        ZipOutputStream zipOutputStream = null;
        try {
            zipOutputStream = new ZipOutputStream(new FileOutputStream(file));

            zipOutputStream.putNextEntry(new ZipEntry("request.hdr"));
            zipOutputStream.write("header".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("request.dat"));
            zipOutputStream.write("data".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("request.ctx"));
            zipOutputStream.write("context".getBytes(StreamUtil.DEFAULT_CHARSET));
            zipOutputStream.closeEntry();
        } finally {
            CloseableUtil.close(zipOutputStream);
        }

        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(file);

            Assert.assertEquals(stroomZipFile.getStroomZipNameSet().getBaseNameSet(),
                    new HashSet<>(Arrays.asList("request")));

            Assert.assertNotNull(stroomZipFile.getInputStream("request", StroomZipFileType.Data));
            Assert.assertNotNull(stroomZipFile.getInputStream("request", StroomZipFileType.Meta));
            Assert.assertNotNull(stroomZipFile.getInputStream("request", StroomZipFileType.Context));

        } finally {
            CloseableUtil.close(stroomZipFile);
            Assert.assertTrue(file.delete());
        }
    }
}
