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

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestFileSystemIterator extends StroomUnitTest {
    @Test
    public void testSimple() throws IOException {
        final File rootDir = getCurrentTestDir();
        try {
            FileUtils.cleanDirectory(rootDir);

            final File sub1 = new File(rootDir, "sub1");
            final File sub2 = new File(rootDir, "sub2");

            FileUtil.mkdirs(sub1);
            FileUtil.mkdirs(sub2);

            final File f1 = new File(rootDir, "f1.zip");
            final File f2 = new File(sub1, "f2.zip");
            final File f3 = new File(sub2, "f3.zip");

            final File i1 = new File(sub1, "f4.zip.bad");
            final File i2 = new File(sub2, "f5.zip.lock");

            FileUtil.createNewFile(f1);
            FileUtil.createNewFile(f2);
            FileUtil.createNewFile(f3);
            FileUtil.createNewFile(i1);
            FileUtil.createNewFile(i2);

            final Set<File> filesFound = new HashSet<>();
            for (final File file : new FileSystemIterator(rootDir,
                    FileSystemIterator.buildSimpleExtensionPattern("zip"))) {
                filesFound.add(file);
            }

            Assert.assertEquals(3, filesFound.size());
            Assert.assertTrue(filesFound.contains(f1));
            Assert.assertTrue(filesFound.contains(f2));
            Assert.assertTrue(filesFound.contains(f3));

        } finally {
            FileUtils.cleanDirectory(rootDir);
        }
    }

    @Test
    public void testError() throws IOException {
        Assert.assertFalse(new FileSystemIterator(new File(getCurrentTestDir(), "unknown"),
                FileSystemIterator.buildSimpleExtensionPattern("zip")).iterator().hasNext());
    }
}
