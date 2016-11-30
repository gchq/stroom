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

package stroom.util.test;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import stroom.util.io.FileUtil;
import stroom.util.thread.ThreadUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class StroomTestUtil {
    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyyMMdd_HHmmss_SSS")
            .withZone(DateTimeZone.UTC);

    public static File createRootTestDir(final File tempDir) throws IOException {
        return tempDir;
    }

    public static File createSingleTestDir(final File parentDir) throws IOException {
        if (!parentDir.isDirectory()) {
            throw new IOException("The parent directory '" + FileUtil.getCanonicalPath(parentDir) + "' does not exist");
        }

        if (parentDir.getName().equals("test")) {
            return parentDir;
        }

        final File dir = new File(parentDir, "test");
        dir.mkdir();

        if (!dir.isDirectory()) {
            throw new IOException("The test directory '" + FileUtil.getCanonicalPath(dir) + "' does not exist");
        }

        return dir;
    }

    public static File createPerThreadTestDir(final File parentDir) throws IOException {
        if (!parentDir.isDirectory()) {
            throw new IOException("The parent directory '" + FileUtil.getCanonicalPath(parentDir) + "' does not exist");
        }

        final File dir = new File(parentDir, String.valueOf(Thread.currentThread().getId()));
        dir.mkdir();

        FileUtils.cleanDirectory(dir);

        return dir;
    }

    public static File createUniqueTestDir(final File parentDir) throws IOException {
        if (!parentDir.isDirectory()) {
            throw new IOException("The parent directory '" + FileUtil.getCanonicalPath(parentDir) + "' does not exist");
        }

        File dir = null;
        for (int i = 0; i < 100; i++) {
            dir = new File(parentDir, FORMAT.print(System.currentTimeMillis()));
            if (dir.mkdir()) {
                break;
            } else {
                dir = null;
                ThreadUtil.sleep(100);
            }
        }

        if (dir == null) {
            throw new IOException("Unable to create unique test dir in: " + FileUtil.getCanonicalPath(parentDir));
        }

        return dir;
    }

    public static void destroyTestDir(final File testDir) {
        try {
            FileUtils.deleteDirectory(testDir);
        } catch (final IOException e) {
            // Ignore
        }
    }

    /**
     * Similar to the unix touch cammand. Sets the last modified time to now if the file
     * exists else, creates the file
     * @param file
     * @throws IOException
     */
    public static void touchFile(Path file) throws IOException {

       if (Files.exists(file)){
           if (!Files.isRegularFile(file)){
               throw new RuntimeException(String.format("File %s is not a regular file", file.toAbsolutePath().toString()));
           }
           try {
               Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
           } catch (IOException e) {
               e.printStackTrace();
           }
       } else {
               Files.createFile(file);
       }
    }
}
