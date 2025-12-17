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

package stroom.resource.impl;

import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.util.exception.ThrowingSupplier;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestUserResourceStoreImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestUserResourceStoreImpl.class);

    @TempDir
    static Path tempDir;
    @Mock
    private SecurityContext mockSecurityContext;

    @Test
    void testSimple() throws IOException {
        final UserResourceStoreImpl resourceStore = new UserResourceStoreImpl(
                () -> tempDir,
                MockSecurityContext.getInstance(),
                new SimpleTaskContextFactory());
        resourceStore.startup();
        resourceStore.execute();

        final ResourceKey key1 = resourceStore.createTempFile("TestResourceStore1.dat");
        assertThat(key1.getName())
                .isEqualTo("TestResourceStore1.dat");

        final ResourceKey key2 = resourceStore.createTempFile("TestResourceStore2.dat");
        assertThat(key2.getName())
                .isEqualTo("TestResourceStore2.dat");

        Instant now = Instant.now().plus(Duration.ofMinutes(1));
        resourceStore.logContents(now, msg -> LOGGER.debug("\n{}", msg));

        Files.createFile(resourceStore.getTempFile(key1, now));
        Files.createFile(resourceStore.getTempFile(key2, now));

        now = now.plus(Duration.ofMinutes(1));
        assertThat(resourceStore.getTempFile(key1, now))
                .isRegularFile();
        assertThat(resourceStore.getTempFile(key2, now))
                .isRegularFile();

        resourceStore.logContents(now, msg -> LOGGER.debug("\n{}", msg));

        // Cleanup
        now = now.plus(Duration.ofMinutes(1));
        resourceStore.execute(now);
        Path file1 = resourceStore.getTempFile(key1, now);
        assertThat(file1)
                .isRegularFile();
        Path file2 = resourceStore.getTempFile(key2, now);
        assertThat(file2)
                .isRegularFile();
        assertThat(resourceStore.size())
                .isEqualTo(2);

        // Cleanup
        now = now.plus(Duration.ofMinutes(1));
        resourceStore.logContents(now, msg -> LOGGER.debug("\n{}", msg));
        resourceStore.execute(now);

        // Files should still exist as their access age is not old enough
        file1 = resourceStore.getTempFile(key1, now);
        assertThat(file1)
                .isRegularFile();
        file2 = resourceStore.getTempFile(key2, now);
        assertThat(file2)
                .isRegularFile();
        assertThat(resourceStore.size())
                .isEqualTo(2);

        // Cleanup
        now = now.plus(Duration.ofMinutes(1));
        resourceStore.logContents(now, msg -> LOGGER.debug("\n{}", msg));
        resourceStore.execute(now);

        now = now.plus(Duration.ofMinutes(61));
        file1 = resourceStore.getTempFile(key1, now);
        assertThat(file1)
                .isRegularFile();
        resourceStore.logContents(now, msg -> LOGGER.debug("\n{}", msg));
        resourceStore.execute(now);

        // We should have deleted file2 as we didn't access since last cleanup.
        file1 = resourceStore.getTempFile(key1, now);
        resourceStore.logContents(now, msg -> LOGGER.debug("\n{}", msg));
        assertThat(file1)
                .isRegularFile();
        assertThat(resourceStore.getTempFile(key2, now))
                .isNull();
        assertThat(file2)
                .doesNotExist();

        // Now delete everything.
        now = now.plus(Duration.ofMinutes(61));
        resourceStore.execute(now);
        assertThat(resourceStore.getTempFile(key1, now))
                .isNull();
        assertThat(file1).doesNotExist();
        assertThat(resourceStore.getTempFile(key2, now))
                .isNull();
        assertThat(file2)
                .doesNotExist();
    }

    @Test
    void testShutdown() throws IOException {
        final UserResourceStoreImpl resourceStore = new UserResourceStoreImpl(
                () -> tempDir,
                MockSecurityContext.getInstance(),
                new SimpleTaskContextFactory());

        final ResourceKey key1 = resourceStore.createTempFile("TestResourceStore1.dat");
        assertThat(key1.getName())
                .isEqualTo("TestResourceStore1.dat");

        final ResourceKey key2 = resourceStore.createTempFile("TestResourceStore2.dat");
        assertThat(key2.getName())
                .isEqualTo("TestResourceStore2.dat");
        final Path file1 = resourceStore.getTempFile(key1);
        final Path file2 = resourceStore.getTempFile(key2);

        Files.createFile(file1);
        Files.createFile(file2);

        assertThat(resourceStore.size())
                .isEqualTo(2);

        final Snapshot snapshot1 = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot1:\n{}", snapshot1.pathSnapshots()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        // resources dir + two files
        assertThat(snapshot1.pathSnapshots())
                .hasSize(3);

        resourceStore.shutdown();

        final Snapshot snapshot2 = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot2:\n{}", snapshot2.pathSnapshots()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        // resources dir still there
        assertThat(snapshot2.pathSnapshots())
                .hasSize(1);
    }

    @Test
    void testStartup() throws IOException {
        final UserResourceStoreImpl resourceStore = new UserResourceStoreImpl(
                () -> tempDir,
                MockSecurityContext.getInstance(),
                new SimpleTaskContextFactory());

        final ResourceKey key1 = resourceStore.createTempFile("TestResourceStore1.dat");
        assertThat(key1.getName())
                .isEqualTo("TestResourceStore1.dat");

        final ResourceKey key2 = resourceStore.createTempFile("TestResourceStore2.dat");
        assertThat(key2.getName())
                .isEqualTo("TestResourceStore2.dat");
        final Path file1 = resourceStore.getTempFile(key1);
        final Path file2 = resourceStore.getTempFile(key2);

        Files.createFile(file1);
        Files.createFile(file2);

        assertThat(resourceStore.size())
                .isEqualTo(2);

        final Snapshot snapshot1 = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot1:\n{}", snapshot1.pathSnapshots()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        // resources dir + two files
        assertThat(snapshot1.pathSnapshots())
                .hasSize(3);

        resourceStore.startup();

        final Snapshot snapshot2 = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot2:\n{}", snapshot2.pathSnapshots()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        // resources dir still there
        assertThat(snapshot2.pathSnapshots())
                .hasSize(1);
    }

    @Test
    void testDelete() throws IOException {
        final UserResourceStoreImpl resourceStore = new UserResourceStoreImpl(
                () -> tempDir,
                MockSecurityContext.getInstance(),
                new SimpleTaskContextFactory());

        final ResourceKey key1 = resourceStore.createTempFile("TestResourceStore1.dat");
        assertThat(key1.getName())
                .isEqualTo("TestResourceStore1.dat");

        final ResourceKey key2 = resourceStore.createTempFile("TestResourceStore2.dat");
        assertThat(key2.getName())
                .isEqualTo("TestResourceStore2.dat");
        final Path file1 = resourceStore.getTempFile(key1);
        final Path file2 = resourceStore.getTempFile(key2);

        Files.createFile(file1);
        Files.createFile(file2);

        assertThat(resourceStore.size())
                .isEqualTo(2);

        final Snapshot snapshot1 = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot1:\n{}", snapshot1.pathSnapshots()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        // resources dir + two files
        assertThat(snapshot1.pathSnapshots())
                .hasSize(3);

        resourceStore.deleteTempFile(key1);

        final Snapshot snapshot2 = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot2:\n{}", snapshot2.pathSnapshots()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        // resources dir still there
        assertThat(snapshot2.pathSnapshots())
                .hasSize(2);

        // No change as already deleted
        resourceStore.deleteTempFile(key1);
        assertThat(snapshot2.pathSnapshots())
                .hasSize(2);

        resourceStore.deleteTempFile(key2);

        final Snapshot snapshot3 = DirectorySnapshot.of(tempDir);
        LOGGER.debug("Snapshot3:\n{}", snapshot3.pathSnapshots()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        // resources dir still there
        assertThat(snapshot3.pathSnapshots())
                .hasSize(1);
    }

    @Test
    void testDifferentUsers() {

        final UserRef userRef1 = UserRef.builder()
                .uuid("user1-uuid")
                .subjectId("user1")
                .build();
        final UserRef userRef2 = UserRef.builder()
                .uuid("user2-uuid")
                .subjectId("user2")
                .build();

        final UserResourceStoreImpl resourceStore = new UserResourceStoreImpl(
                () -> tempDir,
                mockSecurityContext,
                new SimpleTaskContextFactory());

        final ResourceKey key1 = asUser(userRef1, ThrowingSupplier.unchecked(() -> {
            final ResourceKey key = resourceStore.createTempFile("TestResourceStore1.dat");
            assertThat(key.getName())
                    .isEqualTo("TestResourceStore1.dat");
            Files.createFile(resourceStore.getTempFile(key));
            return key;
        }));

        final ResourceKey key2 = asUser(userRef2, ThrowingSupplier.unchecked(() -> {
            final ResourceKey key = resourceStore.createTempFile("TestResourceStore2.dat");
            assertThat(key.getName())
                    .isEqualTo("TestResourceStore2.dat");
            Files.createFile(resourceStore.getTempFile(key));
            return key;
        }));

        asUser(userRef1, () -> {
            final Path file1 = resourceStore.getTempFile(key1);
            assertThat(file1.getFileName().toString())
                    .isNotNull()
                    .startsWith(userRef1.getUuid() + UserResourceStoreImpl.SEPARATOR);

            // User1 can't see user2's file
            final Path file2 = resourceStore.getTempFile(key2);
            assertThat(file2)
                    .isNull();
        });

        asUser(userRef2, () -> {
            // User2 can't see user1's file
            final Path file1 = resourceStore.getTempFile(key1);
            assertThat(file1)
                    .isNull();

            final Path file2 = resourceStore.getTempFile(key2);
            assertThat(file2.getFileName().toString())
                    .isNotNull()
                    .startsWith(userRef2.getUuid() + UserResourceStoreImpl.SEPARATOR);
        });

        assertThat(DirectorySnapshot.of(tempDir).pathSnapshots().size())
                .isEqualTo(3);
        assertThat(resourceStore.size())
                .isEqualTo(2);

        asUser(userRef1, () -> {
            assertThat(resourceStore.getTempFile(key1))
                    .isNotNull();
            resourceStore.deleteTempFile(key1);
            assertThat(resourceStore.getTempFile(key1))
                    .isNull();

            // Can't see it
            assertThat(resourceStore.getTempFile(key2))
                    .isNull();
            // is a no-op as does not belong to user1
            resourceStore.deleteTempFile(key2);
            // Can't see it
            assertThat(resourceStore.getTempFile(key2))
                    .isNull();
        });

        assertThat(DirectorySnapshot.of(tempDir).pathSnapshots().size())
                .isEqualTo(2);
        assertThat(resourceStore.size())
                .isEqualTo(1);

        asUser(userRef2, () -> {
            assertThat(resourceStore.getTempFile(key2))
                    .isNotNull();
            resourceStore.deleteTempFile(key2);
            assertThat(resourceStore.getTempFile(key2))
                    .isNull();
        });
        assertThat(DirectorySnapshot.of(tempDir).pathSnapshots().size())
                .isEqualTo(1);
        assertThat(resourceStore.size())
                .isEqualTo(0);

    }

    private <T> T asUser(final UserRef userRef, final Supplier<T> supplier) {
        Mockito.when(mockSecurityContext.getUserRef())
                .thenReturn(userRef);
        return supplier.get();
    }

    private void asUser(final UserRef userRef, final Runnable runnable) {
        Mockito.when(mockSecurityContext.getUserRef())
                .thenReturn(userRef);
        runnable.run();
    }
}
