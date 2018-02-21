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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public class TestHDFSFileAppender extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestHDFSFileAppender.class);
    private static final String ROOT_TEST_PATH = FileUtil.getTempDir() + "/junitTests/TestHDFSFileAppender";
    private static final String FS_DEFAULT_FS = "file:///";
    private static final String RUN_AS_USER = "hdfs";
    private Configuration conf;
    private UserGroupInformation userGroupInformation;
    private FileSystem hdfs;

    @Before
    public void setup() throws IOException {
        conf = HDFSFileAppender.buildConfiguration(FS_DEFAULT_FS);
        // force it to use the local file system instead of a HDFS cluster
        conf.set("fs.defaultFS", FS_DEFAULT_FS);
        conf.set("fs.hdfs.impl", org.apache.hadoop.fs.RawLocalFileSystem.class.getName());
        conf.set("fs.file.impl", org.apache.hadoop.fs.RawLocalFileSystem.class.getName());

        // gets a new FileSystem object for this conf but this
        // will return a cached instance if one is already open for this conf
        // FS will be shutdown automatically with a JVM shutdown hook
        hdfs = FileSystem.get(conf);

        final Path rootPath = new Path(ROOT_TEST_PATH);

        userGroupInformation = HDFSFileAppender.buildRemoteUser(Optional.of(RUN_AS_USER));

        HDFSFileAppender.runOnHDFS(userGroupInformation, conf, (final FileSystem hdfs) -> {
            try {
                if (hdfs.exists(rootPath)) {
                    hdfs.delete(rootPath, true);
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @After
    public void teardown() throws IOException {
    }

    @Ignore
    @Test
    public void basicTest() throws IOException {
        final Path path = new Path("/dateTest.txt");

        HDFSFileAppender.runOnHDFS(userGroupInformation, conf, hdfs -> {
            try {
                hdfs.exists(path);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        });

        final ContentSummary summary = hdfs.getContentSummary(path);

        System.out.println(summary);
    }

    @Test
    public void testCycleDirs() throws Exception {
        final HDFSFileAppender provider = buildTestObject();

        boolean found1 = false;
        boolean found2 = false;
        boolean found3 = false;

        for (int i = 0; i < 100; i++) {
            provider.startProcessing();
            final OutputStream outputStream = provider.createOutputStream();
            final HDFSLockedOutputStream lockedOutputStream = (HDFSLockedOutputStream) outputStream;
            final Path file = lockedOutputStream.getLockFile();
            final String path = file.toString();

            LOGGER.info("lockfile: " + file);

            if (path.contains("/t1/")) {
                found1 = true;
            } else if (path.contains("/t2/")) {
                found2 = true;
            } else if (path.contains("/t3/")) {
                found3 = true;
            }

            HDFSFileAppender.runOnHDFS(userGroupInformation, conf, hdfs -> {
                try {
                    Assert.assertTrue(hdfs.exists(file));
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });

            lockedOutputStream.close();

            provider.endProcessing();

            if (found1 && found2 && found3) {
                break;
            }
        }

        Assert.assertTrue(found1 && found2 && found3);
    }

    private HDFSFileAppender buildTestObject() {
        final String name = "/${year}-${month}-${day}T${hour}:${minute}:${second}.${millis}Z-${uuid}.xml";
        final PathCreator pathCreator = new PathCreator(null, null, null, null, null);
        final HDFSFileAppender provider = new HDFSFileAppender(null, pathCreator);

        provider.setOutputPaths(ROOT_TEST_PATH + "/t1" + name + "," + ROOT_TEST_PATH + "/t2" + name + ","
                + ROOT_TEST_PATH + "/t3" + name);
        provider.setFileSystemUri(FS_DEFAULT_FS);
        provider.setRunAsUser(RUN_AS_USER);
        provider.setConf(conf);
        return provider;
    }

    public static class MockFileSystem extends DistributedFileSystem {
    }
}
