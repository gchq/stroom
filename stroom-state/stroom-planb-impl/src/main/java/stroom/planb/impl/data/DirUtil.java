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

package stroom.planb.impl.data;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringIdUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DirUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DirUtil.class);

    private static final int MAX_DEPTH = calculateDepth(Long.MAX_VALUE);

    static final Comparator<DirId> DIR_ID_COMPARATOR_ASC = Comparator.comparingLong(DirId::num);
    static final Comparator<DirId> DIR_ID_COMPARATOR_DESC = DIR_ID_COMPARATOR_ASC.reversed();

    public static Path createPath(final Path root,
                                  final long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        // Convert the id to a padded string.
        final String idString = StringIdUtil.idToString(id);
        return createPath(root, idString);
    }

    private static Path createPath(final Path root, final String idString) {
        // E.g. 333555777 => <root path>/2/333/555/333555777

        // Add depth dir, i.e. '/2/'
        Path dir = root;
        final int depth = calculateDepth(idString);
        dir = dir.resolve(Integer.toString(depth));

        // Add dirs from parts of id string, i.e. '/333/555/'
        final int len = idString.length();
        for (int i = 0; i < len - 3; i += 3) {
            dir = dir.resolve(idString.substring(i, i + 3));
        }
        // Add the full id string, i.e '/333555777'
        dir = dir.resolve(idString);

        return dir;
    }

    private static int calculateDepth(final long id) {
        final String idString = StringIdUtil.idToString(id);
        return calculateDepth(idString);
    }

    private static int calculateDepth(final String idString) {
        Objects.requireNonNull(idString);
        return (idString.length() / 3) - 1;
    }

    static boolean isValidDepthPath(final Path path) {
        return path != null
               && isValidDepthPart(path.getFileName().toString());
    }

    static boolean isValidDepthPart(final String depthStr) {
        if (depthStr != null
            && depthStr.length() == 1) {
            final char chr = depthStr.charAt(0);
            if (Character.isDigit(chr)) {
                final int numericValue = Character.getNumericValue(chr);
                return numericValue >= 0 && numericValue <= MAX_DEPTH;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * @return True if the part of a path is a valid branch part when view in isolation
     * i.e. ignoring other path parts. A valid branch part may also be a valid leaf
     * part for leaf parts of length 3.
     */
    static boolean isValidBranchPart(final String branchPart) {
        if (branchPart != null) {
            final int len = branchPart.length();
            if (len == 3) {
                boolean isAllNumeric = true;
                for (int i = 0; i < len; i++) {
                    final char chr = branchPart.charAt(i);
                    if (!Character.isDigit(chr)) {
                        isAllNumeric = false;
                        break;
                    }
                }
                return isAllNumeric;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * @return True if the leaf part of a path is valid when viewed in isolation,
     * i.e. ignoring other path parts.
     */
    static boolean isValidLeafPart(final String leafPart) {
        if (leafPart != null) {
            final int len = leafPart.length();
            if (len % 3 == 0) {
                boolean isAllNumeric = true;
                for (int i = 0; i < len; i++) {
                    final char chr = leafPart.charAt(i);
                    if (!Character.isDigit(chr)) {
                        isAllNumeric = false;
                        break;
                    }
                }
                return isAllNumeric;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Paths should look something like this '....../2/333/555/333555777', where '2' is the depth
     *
     * @return True if the path conforms to the expected dir structure.
     */
    public static boolean isValidLeafPath(final Path path) {
        if (path == null) {
            return false;
        } else {
            final int nameCount = path.getNameCount();
            final int lastNameIdx = nameCount - 1;
            final String leafPart = path.getName(lastNameIdx).toString();
            final int leafLen = leafPart.length();
            LOGGER.trace("isValidLeafPath() - path: {}, nameCount: {}, lastNameIdx: {}, leafPart: {}, leafLen: {}",
                    path, nameCount, lastNameIdx, leafPart, leafLen);
            if (!isValidLeafPart(leafPart)) {
                LOGGER.trace("isValidLeafPath() - Invalid leafPart: {}", leafPart);
                return false;
            } else {
                final Path expectedPath = createPath(Path.of(""), leafPart);
                if (!path.endsWith(expectedPath)) {
                    LOGGER.trace("isValidLeafPath() - path {} doesn't end with expectedPath {}", path, expectedPath);
                    return false;
                } else {
                    return true;
                }
            }
        }
    }

    /**
     * Paths would be one of these (assuming a leaf path of '....../2/333/555/333555777')
     * <ul>
     *     <li>{@code .../2/}</li>
     *     <li>{@code .../2/333/}</li>
     *     <li>{@code .../2/333/555}</li>
     *     <li>{@code .../2/333/333555777}</li>
     * </ul>
     *
     * @return True if the path conforms to the expected dir structure.
     */
    public static boolean isValidLeafOrBranchPath(final Path path) {
        if (path == null) {
            return false;
        } else {
            final int nameCount = path.getNameCount();
            final int lastNameIdx = nameCount - 1;
            final String leafPart = path.getName(lastNameIdx).toString();
            final int leafLen = leafPart.length();
            LOGGER.trace(
                    "isValidLeafOrBranchPath() - path: {}, nameCount: {}, lastNameIdx: {}, leafPart: {}, leafLen: {}",
                    path,
                    nameCount,
                    lastNameIdx,
                    leafPart,
                    leafLen);
            if (isValidDepthPart(leafPart)) {
                // The depth part
                return true;
            } else {
                final boolean isValidLeafPart = isValidLeafPath(path);
                if (isValidLeafPart) {
                    return true;
                } else {
                    boolean foundDepthPart = false;
                    int depth = -1;
                    int branchPartCount = 0;
                    for (int i = lastNameIdx; i >= 0; i--) {
                        final String part = path.getName(i).toString();
                        if (isValidDepthPart(part)) {
                            depth = (int) parse(part)
                                    .orElse(-1);
                            if (depth != -1) {
                                foundDepthPart = true;
                            } else {
                                return false;
                            }
                            break;
                        } else if (isValidBranchPart(part)) {
                            branchPartCount++;
                        } else {
                            return false;
                        }
                    }
                    if (foundDepthPart) {
                        // If we are in here then the path is NOT a valid leaf path, so
                        // number of branch parts must be less than or equal to the depth
                        // e.g.
                        // /0/ OK
                        // /1/123 OK
                        // /1/123/456 NOT OK
                        return branchPartCount <= depth;
                    } else {
                        return false;
                    }
                }
            }
        }
    }

    /**
     * Get the min id of any numerically named file found in the supplied dir.
     *
     * @param parentDir The parent dir to look at.
     * @return The min id of all files found in a dir or 0 if non found.
     */
    public static long getMinDirId(final Path parentDir) {
        return LOGGER.logDurationIfDebugEnabled(
                () ->
                        getDirIdFromRoot(parentDir, Mode.MIN),
                () ->
                        LogUtil.message("Get min dirId for {}", parentDir));
    }

    /**
     * Get the max id of any numerically named file found in the supplied dir.
     *
     * @param parentDir The parent dir to look at.
     * @return The max id of all files found in a dir or 0 if non found.
     */
    public static long getMaxDirId(final Path parentDir) {
        return LOGGER.logDurationIfDebugEnabled(
                () ->
                        getDirIdFromRoot(parentDir, Mode.MAX),
                () ->
                        LogUtil.message("Get max dirId for {}", parentDir));
    }

    /**
     * Get the ID for the dir matching mode in the root directory, i.e. the directory
     * that contains the depth paths.
     */
    private static long getDirIdFromRoot(final Path rootPath, final Mode mode) {
        LOGGER.debug("getLastDirId - path: {}, mode: {}", rootPath, mode);

        final List<DirId> depthDirs = findDirs(rootPath, mode, DirUtil::isValidDepthPath);
        DirId dirId = null;
        final AtomicReference<Path> incompletePath = new AtomicReference<>();
        for (final DirId depthDirId : depthDirs) {
            final Path depthDir = depthDirId.dir;
            LOGGER.debug("Checking depthPath: '{}'", depthDir);
            dirId = getDirId(depthDir, mode, incompletePath);
            if (dirId != null) {
                // Found it
                break;
            } else {
                incompletePath.set(depthDir);
            }
        }

        if (dirId != null && incompletePath.get() != null && mode == Mode.MAX) {
            // In MAX mode we are scanning in reverse order, so we must have found
            // an incomplete path BEFORE the valid leaf path. This means we have at least one
            // DirId but an incomplete branch belonging to a higher DirId. Thus, we can't be
            // sure what the max ID is as it must have been higher than dirId.
            // If dirId == null then we have an empty root, so hopefully it is ok.
            throw new IllegalStateException(
                    LogUtil.message(
                            "Incomplete directory ID path found '{}'. This implies that " +
                            "an ID has previously been allocated with this branch so we cannot be sure what the " +
                            "maximum ID is.", LogUtil.path(incompletePath.get())));
        }

        LOGGER.debug("getDirIdFromRoot - returning dirId {}", dirId);
        return NullSafe.getOrElse(dirId, DirId::num, 0L);
    }

    private static DirId getDirId(final Path path, final Mode mode, final AtomicReference<Path> incompletePath) {
        LOGGER.trace("getDirId() - path: {}, mode: {}", path, mode);

        LOGGER.trace("getDirId() - depthPath: '{}'", path);
        final Predicate<Path> pathPredicate = DirUtil::isValidLeafOrBranchPath;
        final List<DirId> dirs = findDirs(path, mode, pathPredicate);
        LOGGER.trace(() -> LogUtil.message("getDirId() - Found {} dirs in {}", dirs.size(), path));
        DirId dirId = null;
        if (!dirs.isEmpty()) {
            for (final DirId aDirId : dirs) {
                if (isValidLeafPath(aDirId.dir)) {
                    // Found what we are looking for so break out
                    dirId = aDirId;
                } else {
                    // Is a branch so recurse in
                    dirId = getDirId(aDirId.dir, mode, incompletePath);
                }
                if (dirId != null) {
                    break;
                }
            }
        }
        if (dirId == null && isValidLeafOrBranchPath(path)) {
            // No leaf or child branch found so this path is incomplete
            // Just record the first case we find
            incompletePath.compareAndSet(null, path);
        }

        LOGGER.trace("getDirId() - returning dirId {}", dirId);
        return dirId;
    }

    /**
     * MUST be called within a try-with-resources block.
     * Find all direct child directories in path.
     */
    private static Stream<Path> findDirectories(final Path path) throws IOException {
        //noinspection resource // try-with-resources is handled by the caller
        return Files.find(path,
                        1,
                        (aPath, basicFileAttributes) -> {
                            LOGGER.trace(() -> LogUtil.message("findDirectories() - aPath: {}, isDirectory {}",
                                    aPath, basicFileAttributes.isDirectory()));
                            if (basicFileAttributes.isDirectory()) {
                                return true;
                            } else {
                                LOGGER.warn(() -> LogUtil.message(
                                        "findDirectories() - Found unexpected file '{}'. It will be ignored.",
                                        LogUtil.path(aPath)));
                                return false;
                            }
                        })
                .filter(aPath -> !path.equals(aPath));  // ignore the start path which Files.find includes
    }

    /**
     * Get all the directories that are a direct child of path and
     * that have a valid leaf or branch filename.
     * Sorted according to mode.
     */
    private static List<DirId> findDirs(final Path path,
                                        final Mode mode,
                                        final Predicate<Path> pathPredicate) {

        final Comparator<DirId> comparator = switch (mode) {
            case MIN -> DIR_ID_COMPARATOR_ASC;
            case MAX -> DIR_ID_COMPARATOR_DESC;
        };
        LOGGER.trace("findDirs() - path: {}, mode: {}", path, mode);
        try (final Stream<Path> dirStream = findDirectories(path)) {
            return dirStream.map(
                            aPath -> {
                                final DirId dirId;
                                final String fileNamePart = aPath.getFileName().toString();

                                if (pathPredicate.test(aPath)) {
                                    // Parse numeric part of dir.
                                    final OptionalLong optNum = parse(fileNamePart);
                                    if (optNum.isPresent()) {
                                        dirId = new DirId(aPath, optNum.getAsLong());
                                    } else {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "Found unexpected non-numeric directory '{}' in '{}' " +
                                                "when looking for the minimum directory ID. " +
                                                "It will be ignored.",
                                                LogUtil.path(aPath), LogUtil.path(path)));
                                        dirId = null;
                                    }
                                } else {
                                    LOGGER.warn(() -> LogUtil.message(
                                            "Found unexpected directory '{}'", LogUtil.path(aPath)));
                                    dirId = null;
                                }
                                return dirId;
                            })
                    .filter(Objects::nonNull)
                    .sorted(comparator)
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Extract the number represented by the supplied `0` padded string.
     *
     * @param string A directory name.
     * @return The long part of the string if found, else empty.
     */
    private static OptionalLong parse(final String string) {
        if (string == null) {
            return OptionalLong.empty();
        } else {
            try {
                if (NullSafe.isNonBlankString(string)) {
                    // Parse numeric part of dir name.
                    return OptionalLong.of(Long.parseLong(string));
                } else {
                    return OptionalLong.empty();
                }
            } catch (final NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
    }

    public static void ensureDirExists(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            LOGGER.error(() -> "Error creating directories for: " + FileUtil.getCanonicalPath(path), e);
            throw new UncheckedIOException(e);
        }
    }


    private enum Mode {
        MIN,
        MAX
    }

    private record DirId(Path dir, long num) {

    }
}
