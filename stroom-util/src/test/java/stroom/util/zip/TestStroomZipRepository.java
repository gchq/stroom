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

package stroom.util.zip;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStroomZipRepository extends StroomUnitTest {
    @Test
    public void testScan() throws IOException {
        final String repoDir = getCurrentTestDir().getCanonicalPath() + "/repo1";

        final StroomZipRepository StroomZipRepository = new StroomZipRepository(repoDir, true, 100);

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
        final StroomZipRepository reopenStroomZipRepository = new StroomZipRepository(repoDir, false, 100);

        Assert.assertEquals(1L, reopenStroomZipRepository.getFirstFileId().longValue());
        Assert.assertEquals(10000000001L, reopenStroomZipRepository.getLastFileId().longValue());

        final HashSet<File> allZips = new HashSet<File>();
        for (final File file : reopenStroomZipRepository.getZipFiles()) {
            allZips.add(file);
        }

        Assert.assertEquals(2, allZips.size());

        reopenStroomZipRepository.delete(reopenStroomZipRepository.getZipFile(1L));
        reopenStroomZipRepository.delete(reopenStroomZipRepository.getZipFile(10000000001L));

        Assert.assertTrue(reopenStroomZipRepository.deleteIfEmpty());

        Assert.assertFalse("Deleted REPO", new File(repoDir).isDirectory());
    }

    @Test
    public void testClean() throws IOException {
        final String repoDir = getCurrentTestDir().getCanonicalPath() + File.separator + "repo2";

        StroomZipRepository StroomZipRepository = new StroomZipRepository(repoDir, false, 10000);

        final StroomZipOutputStream out1 = StroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out1, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        Assert.assertFalse(out1.getFinalFile().isFile());
        out1.close();
        Assert.assertTrue(out1.getFinalFile().isFile());

        final StroomZipOutputStream out2 = StroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out2, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        Assert.assertFalse(out2.getFinalFile().isFile());
        Assert.assertTrue(new File(out2.getFinalFile().getAbsolutePath() + StroomZipRepository.LOCK_EXTENSION).isFile());

        // Leave open

        StroomZipRepository = new StroomZipRepository(repoDir, false, 1000);
        Assert.assertTrue("Expecting pucker file to be left", out1.getFinalFile().isFile());
        Assert.assertTrue("Expecting lock file to not be deleted",
                new File(out2.getFinalFile().getAbsolutePath() + StroomZipRepository.LOCK_EXTENSION).isFile());

        final StroomZipOutputStream out3 = StroomZipRepository.getStroomZipOutputStream();
        StroomZipOutputStreamUtil.addSimpleEntry(out3, new StroomZipEntry(null, "file", StroomZipFileType.Data),
                "SOME_DATA".getBytes(StreamUtil.DEFAULT_CHARSET));
        final File lockFile3 = new File(out3.getFinalFile().getAbsolutePath() + StroomZipRepository.LOCK_EXTENSION);
        Assert.assertTrue(lockFile3.isFile());

        StroomZipRepository.clean();
        Assert.assertTrue(lockFile3.isFile());

        if (!lockFile3.setLastModified(System.currentTimeMillis() - (48 * 60 * 60 * 1000))) {
            Assert.fail("Unable to set LastModified");
        }
        StroomZipRepository.clean();
        Assert.assertFalse("Expecting old lock file to be deleted", lockFile3.isFile());
    }
}
