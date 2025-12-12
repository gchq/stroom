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

package stroom.data.store.impl.fs;

import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TestFsFileDeleter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFsFileDeleter.class);

    final AtomicLong fileDeleteCounter = new AtomicLong(0);
    final AtomicLong dirDeleteCounter = new AtomicLong(0);

    @Test
    void deleteFilesByBaseName(@TempDir final Path tempDir) throws IOException {

        final String baseName1 = "001";
        Files.createFile(tempDir.resolve(baseName1 + ".dat"));
        Files.createFile(tempDir.resolve(baseName1 + ".ctx"));
        Files.createFile(tempDir.resolve(baseName1 + ".meta"));

        final String baseName2 = "002";
        Files.createFile(tempDir.resolve(baseName2 + ".dat"));
        Files.createFile(tempDir.resolve(baseName2 + ".ctx"));
        Files.createFile(tempDir.resolve(baseName2 + ".meta"));

        final String baseName3 = "001_";
        Files.createFile(tempDir.resolve(baseName3 + ".dat"));
        Files.createFile(tempDir.resolve(baseName3 + ".ctx"));
        Files.createFile(tempDir.resolve(baseName3 + ".meta"));

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(9);

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        final boolean success = fsFileDeleter.deleteFilesByBaseName(
                123, tempDir, baseName1, fileDeleteCounter::addAndGet);

        assertThat(fileDeleteCounter)
                .hasValue(3);

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(6);

        assertThat(success)
                .isEqualTo(true);
    }

    @Test
    void deleteFilesByBaseName_rerun(@TempDir final Path tempDir) throws IOException {

        final String baseName1 = "001";
        Files.createFile(tempDir.resolve(baseName1 + ".dat"));
        Files.createFile(tempDir.resolve(baseName1 + ".ctx"));
        Files.createFile(tempDir.resolve(baseName1 + ".meta"));

        final String baseName2 = "002";
        Files.createFile(tempDir.resolve(baseName2 + ".dat"));
        Files.createFile(tempDir.resolve(baseName2 + ".ctx"));
        Files.createFile(tempDir.resolve(baseName2 + ".meta"));

        final String baseName3 = "001_";
        Files.createFile(tempDir.resolve(baseName3 + ".dat"));
        Files.createFile(tempDir.resolve(baseName3 + ".ctx"));
        Files.createFile(tempDir.resolve(baseName3 + ".meta"));

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(9);

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        boolean success = fsFileDeleter.deleteFilesByBaseName(
                123, tempDir, baseName1, fileDeleteCounter::addAndGet);

        assertThat(fileDeleteCounter)
                .hasValue(3);

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(6);

        assertThat(success)
                .isEqualTo(true);

        // Re-run to make sure the outcome is the same
        success = fsFileDeleter.deleteFilesByBaseName(
                123, tempDir, baseName1, fileDeleteCounter::addAndGet);

        assertThat(fileDeleteCounter)
                .hasValue(3);

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(6);

        assertThat(success)
                .isEqualTo(true);
    }

    @Disabled // Too time sensitive and unpredictable, fine in dev, fails in CI
    @Test
    void deleteFilesByBaseName_interrupted(@TempDir final Path tempDir)
            throws IOException, InterruptedException, ExecutionException {

        final String baseName1 = "001";

        final int fileCount = 20;
        for (int i = 1; i <= fileCount; i++) {
            Files.createFile(tempDir.resolve(baseName1 + ".ext" + i));
        }

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(fileCount);

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final CountDownLatch countDownLatch2 = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<Thread> threadRef = new AtomicReference<>();

        // Kick it off then immediately interrupt it
        CompletableFuture.runAsync(() -> {
            threadRef.set(Thread.currentThread());
            countDownLatch.countDown();
            success.set(fsFileDeleter.deleteFilesByBaseName(
                    123, tempDir, baseName1, fileDeleteCounter::addAndGet));
            countDownLatch2.countDown();
        });

        countDownLatch.await();
        ThreadUtil.sleepIgnoringInterrupts(2);
        threadRef.get().interrupt();
        countDownLatch2.await();

        assertThat(FileUtil.count(tempDir))
                .isGreaterThan(0);

        assertThat(success)
                .isFalse();

        // No re-run
        success.set(fsFileDeleter.deleteFilesByBaseName(
                123, tempDir, baseName1, fileDeleteCounter::addAndGet));

        assertThat(fileDeleteCounter)
                .hasValue(fileCount);

        assertThat(FileUtil.count(tempDir))
                .isZero();

        assertThat(success)
                .isTrue();
    }

    @Test
    void deleteFilesByBaseName_noFiles(@TempDir final Path tempDir) throws IOException {

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(0);

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        final boolean success = fsFileDeleter.deleteFilesByBaseName(
                123, tempDir, "001", fileDeleteCounter::addAndGet);

        assertThat(fileDeleteCounter)
                .hasValue(0);

        assertThat(FileUtil.count(tempDir))
                .isEqualTo(0);

        assertThat(success)
                .isEqualTo(true);
    }

    @Test
    void deleteFilesByBaseName_badDir(@TempDir final Path tempDir) throws IOException {


        final Path notExistsDir = tempDir.resolve("i_dont_exist");

        assertThat(Files.exists(notExistsDir))
                .isFalse();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        final boolean success = fsFileDeleter.deleteFilesByBaseName(
                123, notExistsDir, "001", fileDeleteCounter::addAndGet);

        assertThat(fileDeleteCounter)
                .hasValue(0);

        assertThat(success)
                .isEqualTo(false);
    }

    @Test
    void tryDeleteDir_badDir(@TempDir final Path tempDir) {

        final Path notExistsDir = tempDir.resolve(UUID.randomUUID().toString());

        assertThat(Files.exists(notExistsDir))
                .isFalse();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        final boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                notExistsDir,
                Instant.now().plusSeconds(5).toEpochMilli(),
                dirDeleteCounter::addAndGet);

        assertThat(success)
                .isTrue();

        assertThat(dirDeleteCounter)
                .hasValue(0);
    }

    @Test
    void tryDeleteDir_sameDir(@TempDir final Path tempDir) {

        assertThat(tempDir)
                .exists()
                .isDirectory();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        final boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                tempDir,
                Instant.now().plusSeconds(5).toEpochMilli(),
                dirDeleteCounter::addAndGet);

        assertThat(success)
                .isTrue();

        // dir same as root so not deleted
        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(0);
    }

    @Test
    void tryDeleteDir_oneLevelDeep(@TempDir final Path tempDir) throws IOException {

        final Path level1 = tempDir.resolve("level1");
        Files.createDirectories(level1);

        assertThat(level1)
                .exists()
                .isDirectory();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        final boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                level1,
                Instant.now().plusSeconds(5).toEpochMilli(),
                dirDeleteCounter::addAndGet);

        assertThat(success)
                .isTrue();

        assertThat(level1)
                .doesNotExist();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(1);
    }

    @Test
    void tryDeleteDir_twoLevelsDeep(@TempDir final Path tempDir) throws IOException {

        final Path level1 = tempDir.resolve("level1");
        final Path level2 = level1.resolve("level2");
        Files.createDirectories(level2);

        assertThat(level2)
                .exists()
                .isDirectory();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        final boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                level2,
                Instant.now().plusSeconds(5).toEpochMilli(),
                dirDeleteCounter::addAndGet);

        assertThat(success)
                .isTrue();

        assertThat(level2)
                .doesNotExist();

        assertThat(level1)
                .doesNotExist();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(2);
    }

    @Test
    void tryDeleteDir_twoLevelsDeep_reRun(@TempDir final Path tempDir) throws IOException {

        final Path level1 = tempDir.resolve("level1");
        final Path level2 = level1.resolve("level2");
        Files.createDirectories(level2);

        assertThat(level2)
                .exists()
                .isDirectory();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                level2,
                Instant.now().plusSeconds(5).toEpochMilli(),
                dirDeleteCounter::addAndGet);

        assertThat(success)
                .isTrue();

        assertThat(level2)
                .doesNotExist();

        assertThat(level1)
                .doesNotExist();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(2);

        // No re-run with no change to outcome
        success = fsFileDeleter.tryDeleteDir(
                tempDir,
                level2,
                Instant.now().plusSeconds(5).toEpochMilli(),
                dirDeleteCounter::addAndGet);

        assertThat(success)
                .isTrue();

        assertThat(level2)
                .doesNotExist();

        assertThat(level1)
                .doesNotExist();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(2);
    }

    @Test
    void tryDeleteDir_twoLevelsDeep_nonEmptyLevel1(@TempDir final Path tempDir) throws IOException {

        final Path level1Dir = tempDir.resolve("level1");
        final Path level2Dir = level1Dir.resolve("level2");
        Files.createDirectories(level2Dir);

        final Path level1File = Files.createFile(level1Dir.resolve("a_file"));

        assertThat(level2Dir)
                .exists()
                .isDirectory();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        final boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                level2Dir,
                Instant.now().plusSeconds(5).toEpochMilli(),
                dirDeleteCounter::addAndGet);

        // Being unable to delete a non-empty dir is acceptable behaviour
        assertThat(success)
                .isTrue();

        // Is empty so is gone
        assertThat(level2Dir)
                .doesNotExist();

        // Non-empty so still exists
        assertThat(level1Dir)
                .exists()
                .isDirectory();

        assertThat(level1File)
                .exists()
                .isRegularFile();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(1);
    }

    @Test
    void tryDeleteDir_twoLevelsDeep_notADir(@TempDir final Path tempDir) throws IOException {

        final Path level1Dir = tempDir.resolve("level1");
        final Path level2Dir = level1Dir.resolve("level2");

        Files.createDirectories(level2Dir);
        final Path level2File = Files.createFile(level2Dir.resolve("level2File"));


        assertThat(level2Dir)
                .exists()
                .isDirectory();

        assertThat(level2File)
                .exists()
                .isRegularFile();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        Assertions.assertThatThrownBy(
                        () -> {
                            fsFileDeleter.tryDeleteDir(
                                    tempDir,
                                    level2File,
                                    Instant.now().plusSeconds(5).toEpochMilli(),
                                    dirDeleteCounter::addAndGet);
                        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(level2File.toAbsolutePath().normalize().toString())
                .hasMessageContaining("not a directory");

        assertThat(level2Dir)
                .exists()
                .isDirectory();

        assertThat(level2File)
                .exists()
                .isRegularFile();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(0);
    }

    @Test
    void tryDeleteDir_twoLevelsDeep_tooNew(@TempDir final Path tempDir) throws IOException {

        final Path level1Dir = tempDir.resolve("level1");
        final Path level2Dir = level1Dir.resolve("level2");
        Files.createDirectories(level2Dir);

        assertThat(level2Dir)
                .exists()
                .isDirectory();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        final boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                level2Dir,
                Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli(), dirDeleteCounter::addAndGet);

        // Being unable to delete a dir that is too new is acceptable behaviour
        assertThat(success)
                .isTrue();

        // Too new so still there
        assertThat(level2Dir)
                .exists()
                .isDirectory();

        // Too new so still there
        assertThat(level1Dir)
                .exists()
                .isDirectory();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(0);
    }

    @Test
    void tryDeleteDir_twoLevelsDeep_missingLevel2(@TempDir final Path tempDir) throws IOException {

        final Path level1Dir = tempDir.resolve("level1");
        final Path level2Dir = level1Dir.resolve("level2");

        // Only create up to level 1
        Files.createDirectories(level1Dir);

        assertThat(level1Dir)
                .exists()
                .isDirectory();

        assertThat(level2Dir)
                .doesNotExist();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        // Make sure oldFileTime is in the future so all modified dates are considered old
        final boolean success = fsFileDeleter.tryDeleteDir(
                tempDir,
                level2Dir,
                Instant.now().plusSeconds(5).toEpochMilli(), dirDeleteCounter::addAndGet);

        assertThat(success)
                .isTrue();

        assertThat(level2Dir)
                .doesNotExist();

        assertThat(level1Dir)
                .doesNotExist();

        assertThat(tempDir)
                .exists()
                .isDirectory();

        assertThat(dirDeleteCounter)
                .hasValue(1);
    }

    @Test
    void tryDeleteDir_dirNotInRoot(@TempDir final Path tempDir1, @TempDir final Path tempDir2) throws IOException {

        assertThat(tempDir1)
                .exists()
                .isDirectory();

        assertThat(tempDir2)
                .exists()
                .isDirectory();

        final FsFileDeleter fsFileDeleter = new FsFileDeleter(new SimpleTaskContextFactory());
        Assertions.assertThatThrownBy(
                        () ->
                                // Make sure oldFileTime is in the future so all modified dates are considered old
                                fsFileDeleter.tryDeleteDir(
                                        tempDir1,
                                        tempDir2,
                                        Instant.now().plusSeconds(5).toEpochMilli(),
                                        dirDeleteCounter::addAndGet))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("must be an descendant of root")
                .hasMessageContaining(tempDir1.normalize().toAbsolutePath().toString())
                .hasMessageContaining(tempDir2.normalize().toAbsolutePath().toString());

        assertThat(dirDeleteCounter)
                .hasValue(0);
    }
}
