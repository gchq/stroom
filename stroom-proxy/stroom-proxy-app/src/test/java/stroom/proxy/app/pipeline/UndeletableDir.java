/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;

/**
 * Makes a directory's contents impossible to delete, so that a cleanup step can be driven down its
 * failure path.
 * <p>
 * Layer 0's whole subject is what each site does when a recursive delete fails, and every one of
 * those paths is unreachable from a test unless a delete can be made to fail on demand. Removing
 * write permission from the directory itself is the way to do that: the entries inside it can no
 * longer be unlinked, so {@code FileUtil.deleteDir} returns {@code false} having deleted nothing.
 * </p>
 * <p>
 * <strong>It can fail to work, and that must not look like a pass.</strong> Root ignores the
 * permission bits, and a non-POSIX filesystem has none to clear. In either case the delete would
 * quietly succeed and a test asserting "the directory survived" would fail for a reason that has
 * nothing to do with the code under test. {@link #lock(Path)} therefore probes whether the lock
 * actually took effect and returns empty if it did not, so the caller can skip instead.
 * </p>
 */
public final class UndeletableDir implements AutoCloseable {

    private static final Set<PosixFilePermission> READ_AND_TRAVERSE_ONLY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_EXECUTE);

    private final Path dir;
    private final Set<PosixFilePermission> originalPermissions;

    private UndeletableDir(final Path dir,
                           final Set<PosixFilePermission> originalPermissions) {
        this.dir = dir;
        this.originalPermissions = originalPermissions;
    }

    /**
     * Make the contents of {@code dir} undeletable until {@link #close()} restores it.
     *
     * @return the lock, or empty if this environment cannot enforce one - in which case the caller
     * must skip rather than assert.
     */
    public static Optional<UndeletableDir> lock(final Path dir) throws IOException {
        if (!Files.getFileStore(dir).supportsFileAttributeView(PosixFileAttributeView.class)) {
            return Optional.empty();
        }

        final Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(dir);
        Files.setPosixFilePermissions(dir, READ_AND_TRAVERSE_ONLY);

        // Probe rather than trust: if this write succeeds the bits are being ignored - root - and
        // the lock is worthless.
        final Path probe = dir.resolve("undeletable-probe.tmp");
        try {
            Files.createFile(probe);
        } catch (final IOException e) {
            return Optional.of(new UndeletableDir(dir, originalPermissions));
        }

        Files.setPosixFilePermissions(dir, originalPermissions);
        Files.deleteIfExists(probe);
        return Optional.empty();
    }

    @Override
    public void close() throws IOException {
        Files.setPosixFilePermissions(dir, originalPermissions);
    }
}
