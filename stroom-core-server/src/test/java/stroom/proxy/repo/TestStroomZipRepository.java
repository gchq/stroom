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

package stroom.proxy.repo;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.StroomZipEntry;
import stroom.util.zip.StroomZipFile;
import stroom.util.zip.StroomZipFileType;
import stroom.util.zip.StroomZipOutputStream;
import stroom.util.zip.StroomZipOutputStreamUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStroomZipRepository extends StroomUnitTest {

    private static final String ZIP_FILENAME_DELIMITER = "!";

    @Before
    public void setup() {
        clearTestDir();
    }

    @Test
    public void testScan() throws IOException {
        final String repoDir = getCurrentTestDir().getCanonicalPath() + "/repo1";

        final StroomZipRepository StroomZipRepository = new StroomZipRepository(repoDir, true, 100, ZIP_FILENAME_DELIMITER);

        final StroomZipOutputStream out1 = StroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        out1.close();

        StroomZipRepository.setCount(10000000000L);

        final StroomZipOutputStream out2 = StroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out2, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        out2.close();

        StroomZipRepository.finish();

        // Re open.
        final StroomZipRepository reopenStroomZipRepository = new StroomZipRepository(repoDir, false, 100, ZIP_FILENAME_DELIMITER);

        Assert.assertEquals(1L, reopenStroomZipRepository.getFirstFileId().longValue());
        Assert.assertEquals(10000000001L, reopenStroomZipRepository.getLastFileId().longValue());

        final HashSet<File> allZips = new HashSet<>();
        for (final File file : reopenStroomZipRepository.getZipFiles()) {
            allZips.add(file);
        }

        Assert.assertEquals(2, allZips.size());
        for (File file : reopenStroomZipRepository.getZipFiles()) {
            reopenStroomZipRepository.delete(new StroomZipFile(file));
        }

        Assert.assertTrue(reopenStroomZipRepository.deleteIfEmpty());
        Assert.assertFalse("Deleted REPO", new File(repoDir).isDirectory());
    }

    @Test
    public void testClean() throws IOException {
        final String repoDir = getCurrentTestDir().getCanonicalPath() + File.separator + "repo2";

        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, false, 10000, ZIP_FILENAME_DELIMITER);

        final StroomZipOutputStream out1 = stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        Assert.assertFalse(out1.getFinalFile().isFile());
        out1.close();
        Assert.assertTrue(out1.getFinalFile().isFile());

        final StroomZipOutputStream out2 = stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out2, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        Assert.assertFalse(out2.getFinalFile().isFile());
        Assert.assertTrue(new File(out2.getFinalFile().getAbsolutePath() + StroomZipRepository.LOCK_EXTENSION).isFile());

        // Leave open

        stroomZipRepository = new StroomZipRepository(repoDir, false, 10000, ZIP_FILENAME_DELIMITER);
        Assert.assertTrue("Expecting pucker file to be left", out1.getFinalFile().isFile());
        Assert.assertTrue("Expecting lock file to not be deleted",
                new File(out2.getFinalFile().getAbsolutePath() + StroomZipRepository.LOCK_EXTENSION).isFile());

        final StroomZipOutputStream out3 = stroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out3, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        final File lockFile3 = new File(out3.getFinalFile().getAbsolutePath() + StroomZipRepository.LOCK_EXTENSION);
        Assert.assertTrue(lockFile3.isFile());

        stroomZipRepository.clean();
        Assert.assertTrue(lockFile3.isFile());

        if (!lockFile3.setLastModified(System.currentTimeMillis() - (48 * 60 * 60 * 1000))) {
            Assert.fail("Unable to set LastModified");
        }
        stroomZipRepository.clean();
        Assert.assertFalse("Expecting old lock file to be deleted", lockFile3.isFile());
    }

    @Test
    public void testTemplatedFilename() throws IOException {

        final String repoDir = getCurrentTestDir().getCanonicalPath() + File.separator + "repo3";
        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, false, 10000, ZIP_FILENAME_DELIMITER);

        HeaderMap headerMap = new HeaderMap();
        headerMap.put("feed", "myFeed");
        headerMap.put("key1", "myKey1");
        headerMap.put("key2", "myKey2");
        headerMap.put("key3", "myKey3");

        //template should be case insensitive as far as key names go as the headermap is case insensitive
        final String zipFilenameTemplate = "${FEED}_${key2}_${kEy1}_${Key3}";

        final StroomZipOutputStream out1 = stroomZipRepository.getStroomZipOutputStream(headerMap, zipFilenameTemplate);

        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        Assert.assertFalse(out1.getFinalFile().isFile());
        out1.close();
        File zipFile = out1.getFinalFile();
        Assert.assertTrue(zipFile.isFile());
        final String expectedFilename = new StringBuilder().append("001").append("!").append(
                "myFeed_myKey2_myKey1_myKey3.zip").toString();
        Assert.assertEquals(expectedFilename, zipFile.getName());

        Assert.assertEquals(1L, stroomZipRepository.getFirstFileId().longValue());
        Assert.assertEquals(1L, stroomZipRepository.getLastFileId().longValue());

    }

    @Test
    public void testInvalidDelimiter() throws IOException {
        HeaderMap headerMap = new HeaderMap();
        headerMap.put("feed", "myFeed");
        headerMap.put("key1", "myKey1");

        final String zipFilenameTemplate = "${FEED}_${kEy1}";
        final String repoDir = getCurrentTestDir().getCanonicalPath() + File.separator + "repo3";

        final AtomicInteger fileNum = new AtomicInteger(1);

        Arrays.stream(StroomZipRepository.INVALID_ZIP_FILENAME_DELIMITERS)
                .forEach(delim -> {
                    try {
                        StroomZipRepository stroomZipRepository = new StroomZipRepository(repoDir, false, 10000, delim);
                        final StroomZipOutputStream out1 = stroomZipRepository.getStroomZipOutputStream(headerMap, zipFilenameTemplate);

                        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
                        Assert.assertFalse(out1.getFinalFile().isFile());
                        out1.close();
                        File zipFile = out1.getFinalFile();
                        Assert.assertTrue(zipFile.isFile());
                        final String expectedFilename = new StringBuilder()
                                .append(String.format("%03d", fileNum.getAndIncrement()))
                                //supplied delimiter is invalid so the default will be used
                                .append(StroomZipRepository.DEFAULT_ZIP_FILENAME_DELIMITER)
                                .append("myFeed_myKey1.zip")
                                .toString();

                        Assert.assertEquals(expectedFilename, zipFile.getName());
                        //will only get here if no exception is thrown
//                        allThrewException.set(false);
                    } catch (Exception e) {
                        throw new RuntimeException(String.format("error"), e);
                    }
                });
    }

    public File getCurrentTestDir() {
        return FileUtil.getTempDir();
    }

    public void clearTestDir() {
        File dir = getCurrentTestDir();
        try {
            FileUtils.cleanDirectory(dir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to clear directory " + dir.getAbsolutePath(), e);
        }
    }
}
