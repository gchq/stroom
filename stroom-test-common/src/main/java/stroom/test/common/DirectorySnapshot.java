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

package stroom.test.common;

import stroom.util.io.FileUtil;
import stroom.util.io.PathWithAttributes;
import stroom.util.shared.NullSafe;

import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DirectorySnapshot {

    private DirectorySnapshot() {
    }

    /**
     * Take a deep snapshot of a directory capturing all relative paths, file sizes,
     * path types and sha256 hashes of each file. Runs in parallel by default. The root path
     * is not included in the output and all paths in the snapshot are relative to it.
     * <p>
     * It means you can do something like
     * <pre>{@code
     * assertThat(DirectorySnapshot.of(path1))
     *     .isEqualTo(DirectorySnapshot.of(path2))
     * }</pre>
     * </p>
     *
     * @param path The root path to create a snapshot of
     */
    public static Snapshot of(final Path path) {
        return of(path, true);
    }

    /**
     * Take a deep snapshot of a directory capturing all relative paths, file sizes,
     * path types and sha256 hashes of each file. The root path
     * is not included in the output and all paths in the snapshot are relative to it.
     * <p>
     * It means you can do something like
     * <pre>{@code
     * assertThat(DirectorySnapshot.of(path1))
     *     .isEqualTo(DirectorySnapshot.of(path2))
     * }</pre>
     * </p>
     *
     * @param path     The root path to create a snapshot of
     * @param parallel Whether to use multiple threads to scan the dir
     */
    public static Snapshot of(final Path path, final boolean parallel) {
        Objects.requireNonNull(path);
        final Path root = path.normalize().toAbsolutePath();

        // Don't include the root dir as we are just comparing contents
        final List<PathWithAttributes> paths = FileUtil.deepListContents(root,
                parallel,
                pathWithAttributes2 ->
                        !root.equals(pathWithAttributes2.path().normalize().toAbsolutePath()));

        Stream<PathWithAttributes> stream = paths.stream();
        if (parallel) {
            //noinspection ReassignedVariable
            stream = stream.parallel();
        }
        final List<PathSnapshot> pathSnapshots = stream
                .map(pathWithAttributes -> {
                    final Set<PathFlag> flags = getPathFlags(pathWithAttributes);
                    final String hash = hashFileContents(pathWithAttributes);
                    final long size = pathWithAttributes.isRegularFile()
                            ? pathWithAttributes.size()
                            : 0;
                    return new PathSnapshot(
                            root.relativize(pathWithAttributes.path().normalize()),
                            hash,
                            size,
                            flags);
                })
                .sorted()
                .toList();

        return new Snapshot(root, pathSnapshots);
    }

    private static Set<PathFlag> getPathFlags(final PathWithAttributes pathWithAttributes) {
        final Set<PathFlag> flags = EnumSet.noneOf(PathFlag.class);
        if (pathWithAttributes.isDirectory()) {
            flags.add(PathFlag.DIRECTORY);
        }
        if (pathWithAttributes.isRegularFile()) {
            flags.add(PathFlag.REGULAR_FILE);
        }
        if (pathWithAttributes.isSymbolicLink()) {
            flags.add(PathFlag.SYMBOLIC_LINK);
        }
        if (pathWithAttributes.isOther()) {
            flags.add(PathFlag.OTHER);
        }
        return flags;
    }

    private static String hashFileContents(final PathWithAttributes pathWithAttributes) {
        if (pathWithAttributes.isRegularFile()) {
            final String algo = "SHA-256";
            try {
                final MessageDigest digest = MessageDigest.getInstance(algo);
                digest.reset();
                final byte[] hashBytes = digest.digest(Files.readAllBytes(pathWithAttributes.path()));
                return Hex.encodeHexString(hashBytes);
            } catch (final NoSuchAlgorithmException e) {
                throw new RuntimeException("Algorithm " + algo + " not found", e);
            } catch (final IOException e) {
                throw new RuntimeException("Error hashing file " + pathWithAttributes.path(), e);
            }
        } else {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    public record Snapshot(Path root,
                           List<PathSnapshot> pathSnapshots) {

        public Snapshot {
            Objects.requireNonNull(root);
            Objects.requireNonNull(pathSnapshots);
        }

        public Stream<PathSnapshot> stream() {
            return pathSnapshots.stream();
        }

        @Override
        public String toString() {
            return pathSnapshots.stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining("\n"));
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Snapshot snapshot = (Snapshot) o;
            return Objects.equals(pathSnapshots, snapshot.pathSnapshots);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pathSnapshots);
        }
    }


    // --------------------------------------------------------------------------------


    public record PathSnapshot(Path path,
                               String hash,
                               long size,
                               Set<PathFlag> flags) implements Comparable<PathSnapshot> {

        public PathSnapshot {
            Objects.requireNonNull(path);
            flags = NullSafe.set(flags);
        }

        @Override
        public String toString() {
            return path +
                   ", size=" + size +
                   ", flags=" + flags +
                   ", hash='" + hash + '\'';
        }

        @Override
        public int compareTo(final PathSnapshot o) {
            return this.path.compareTo(o.path);
        }
    }


    // --------------------------------------------------------------------------------


    public enum PathFlag {
        DIRECTORY,
        REGULAR_FILE,
        SYMBOLIC_LINK,
        OTHER,
        ;
    }
}
