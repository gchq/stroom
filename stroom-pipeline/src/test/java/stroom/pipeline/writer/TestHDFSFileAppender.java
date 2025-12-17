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
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// FIXME : BROKEN BY JAVA21
@Disabled
class TestHDFSFileAppender extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHDFSFileAppender.class);
    //    private static final String ROOT_TEST_PATH = FileUtil.getTempDir() + "/junitTests/TestHDFSFileAppender";
    private static final String FS_DEFAULT_FS = "file:///";
    private static final String RUN_AS_USER = "hdfs";

    @TempDir
    java.nio.file.Path rootTestDir;

    private Configuration conf;
    private UserGroupInformation userGroupInformation;
    private FileSystem hdfs;

    @BeforeEach
    void setup() throws IOException {
        conf = HDFSFileAppender.buildConfiguration(FS_DEFAULT_FS);
        // force it to use the local file system instead of a HDFS cluster
        conf.set("fs.defaultFS", FS_DEFAULT_FS);
        conf.set("fs.hdfs.impl", org.apache.hadoop.fs.RawLocalFileSystem.class.getName());
        conf.set("fs.file.impl", org.apache.hadoop.fs.RawLocalFileSystem.class.getName());

        // gets a new FileSystem object for this conf but this
        // will return a cached instance if one is already open for this conf
        // FS will be shutdown automatically with a JVM shutdown hook
        hdfs = FileSystem.get(conf);

        final Path rootPath = new Path(rootTestDir.toAbsolutePath().toString());

        userGroupInformation = HDFSFileAppender.buildRemoteUser(Optional.of(RUN_AS_USER));

        HDFSFileAppender.runOnHDFS(userGroupInformation, conf, (final FileSystem hdfs) -> {
            try {
                if (hdfs.exists(rootPath)) {
                    hdfs.delete(rootPath, true);
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Disabled
    @Test
    void basicTest() throws IOException {
        final Path path = new Path("/dateTest.txt");

        HDFSFileAppender.runOnHDFS(userGroupInformation, conf, hdfs -> {
            try {
                hdfs.exists(path);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        final ContentSummary summary = hdfs.getContentSummary(path);

        System.out.println(summary);
    }

    @Test
    void testCycleDirs() throws IOException {
        final HDFSFileAppender provider = buildTestObject(rootTestDir);

        boolean found1 = false;
        boolean found2 = false;
        boolean found3 = false;

        for (int i = 0; i < 100; i++) {
            provider.startProcessing();
            final HDFSLockedOutputStream lockedOutputStream = provider.createHDFSLockedOutputStream();
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
                    assertThat(hdfs.exists(file)).isTrue();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            lockedOutputStream.close();

            provider.endProcessing();

            if (found1 && found2 && found3) {
                break;
            }
        }

        assertThat(found1 && found2 && found3).isTrue();
    }

    private HDFSFileAppender buildTestObject(final java.nio.file.Path tempDir) {
        final String name = "/${year}-${month}-${day}T${hour}:${minute}:${second}.${millis}Z-${uuid}.xml";
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final HDFSFileAppender provider = new HDFSFileAppender(null, pathCreator);

        final String dir = tempDir.toAbsolutePath().toString();
        provider.setOutputPaths(
                dir + "/t1" + name + "," +
                dir + "/t2" + name + "," +
                dir + "/t3" + name);
        provider.setFileSystemUri(FS_DEFAULT_FS);
        provider.setRunAsUser(RUN_AS_USER);
        provider.setConf(conf);
        return provider;
    }

    public static class MockFileSystem extends DistributedFileSystem {

    }
}
