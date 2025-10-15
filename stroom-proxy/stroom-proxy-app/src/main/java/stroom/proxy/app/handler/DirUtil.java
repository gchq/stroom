package stroom.proxy.app.handler;

import stroom.proxy.repo.FeedKey;
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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <p>
 * Util methods for working with a folder structure that maps an ID (long) to a path such
 * that no directory has more than 1000 items in it.
 * </p>
 * <p>
 * The first directory level indicates the depth of the directories it contains.
 * that no directory has more than 1000 items in it.
 * </p>
 * <p>
 * The leaf directory has a name that is the full ID, zero padded to a multiple of 3 digits.
 * e.g. 001, 002300123, etc.
 * </p>
 * <p>
 * All intermediate directories (if present) will have a 3 digit name with a value of 000 -> 999.
 * </p>
 * <ul>
 *     <li>{@code <rootDir>/0/001/} - ID: 1</li>
 *     <li>{@code <rootDir>/0/999/} - ID: 999</li>
 *     <li>{@code <rootDir>/1/001/001000/} - ID: 1,000</li>
 *     <li>{@code <rootDir>/1/001/001999/} - ID: 1,999</li>
 *     <li>{@code <rootDir>/1/002/002000/} - ID: 2,000</li>
 *     <li>{@code <rootDir>/2/002/300/002300123/} - ID: 2,300,123</li>
 *     <li>{@code <rootDir>/6/009/223/372/036/854/775/009223372036854775807/} - ID: 9,223,372,036,854,775,807
 *     ({@link Long#MAX_VALUE})</li>
 * </ul>
 */
public class DirUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DirUtil.class);

    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final int MAX_DEPTH = calculateDepth(Long.MAX_VALUE); // 6
    private static final IntPredicate LEN_ONE_PREDICATE = len -> len == 1;
    private static final IntPredicate LEN_MULTIPLE_OF_THREE_PREDICATE = len -> len % 3 == 0;

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

    /**
     * e.g.
     * <pre>
     * 123 -> 0
     * 123456 -> 1
     * 123456789 -> 2
     * </pre>
     *
     * @return The depth of the id if it were represented as a path
     */
    private static int calculateDepth(final long id) {
        final int digitCount = StringIdUtil.getDigitCountAsId(id);
        return (digitCount / 3) - 1;
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
    static boolean isValidBranchOrLeafPart(final String branchPart) {
        if (branchPart != null) {
            final int len = branchPart.length();
            if (len % 3 == 0) {
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
            LOGGER.trace("isValidLeafOrBranchPath path: {}, nameCount: {}, lastNameIdx: {}, leafPart: {}, leafLen: {}",
                    path, nameCount, lastNameIdx, leafPart, leafLen);
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
    static long getDirIdFromRoot(final Path rootPath, final Mode mode) {
        LOGGER.debug("getLastDirId - path: {}, mode: {}", rootPath, mode);

        final List<DirId> depthDirs = findDirs(rootPath, mode, DirUtil::isValidDepthPath);
        DirId dirId = null;
        // Only 0-6 of these depthDirs
        for (final DirId depthDirId : depthDirs) {
            final Path depthDir = depthDirId.dir;
            LOGGER.debug("Checking depthPath: '{}'", depthDir);
            dirId = getDirId(rootPath, depthDir, mode);
            if (dirId != null) {
                // Found it
                break;
            } else {
                // Incomplete branch
                final Long id = getIdFromIncompleteBranch(rootPath, depthDir, mode);
                dirId = NullSafe.get(id, anId -> new DirId(depthDir, anId));
            }
        }

        LOGGER.debug("getDirIdFromRoot - returning dirId {}", dirId);
        return NullSafe.getOrElse(dirId, DirId::num, 0L);
    }

    private static DirId getDirId(final Path rootDir, final Path path, final Mode mode) {
        LOGGER.trace("getDirId - path: {}, mode: {}", path, mode);

        LOGGER.trace("getDirId() - Checking depthPath: '{}'", path);
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
                    dirId = getDirId(rootDir, aDirId.dir, mode);
                    if (dirId == null) {
                        // Is incomplete branch
                        final Long id = getIdFromIncompleteBranch(rootDir, aDirId.dir, mode);
                        dirId = NullSafe.get(id, anId -> new DirId(path, anId));
                    }
                }
                if (dirId != null) {
                    break;
                }
            }
        } else {
            // Is incomplete branch
            final Long id = getIdFromIncompleteBranch(rootDir, path, mode);
            dirId = NullSafe.get(id, anId -> new DirId(path, anId));
        }
        LOGGER.trace("getDirId() - returning dirId {}", dirId);
        return dirId;
    }

    /**
     * IDs are in blocks of 1000 (0-999) so return the lowest ID in the next block of 1000, e.g.
     * <pre>
     * 123 -> 1000
     * 999 -> 1000
     * 1000 -> 2000
     * 1001 -> 2000
     * </pre>
     */
    static long getIdInNextBlock(final long id) {
        final long remainder = id % 1000;
        return remainder == 0
                ? id + 1000
                : id + (1000 - remainder);
    }

    static long getNumberInDir(final long id) {
        return id % 1000;
    }

    static Long getIdFromIncompleteBranch(final Path rootDir,
                                          final Path path,
                                          final Mode mode) {
        final Path relPath = rootDir.relativize(path);
        final Iterator<Path> iterator = relPath.iterator();
        final int depth;
        int currDepth;
        long id = 0;
        if (iterator.hasNext()) {
            final Path depthPart = iterator.next();
            if (isValidDepthPath(depthPart)) {
                depth = Integer.parseInt(depthPart.getFileName().toString());
                currDepth = depth;
            } else {
                return null;
            }
        } else {
            return null;
        }

        // Now iterate over the remaining parts
        while (iterator.hasNext()) {
            final Path pathPart = iterator.next();
            final String pathPartStr = pathPart.getFileName().toString();
            if (isValidBranchPart(pathPartStr)) {
                final long value = Long.parseLong(pathPartStr);
                final long delta = ((long) Math.pow(1000, currDepth)) * value;
                id += delta;
//            LOGGER.info("Real  - currDepth: {}, value: {}, delta: {}, id: {}, mode: {}",
//                    ModelStringUtil.formatCsv(currDepth),
//                    ModelStringUtil.formatCsv(value),
//                    ModelStringUtil.formatCsv(delta),
//                    id,
//                    mode);
                currDepth--;
            }
        }
        while (currDepth >= 0) {
            final long delta;
            if (mode == Mode.MIN) {
                if (currDepth != 0 && currDepth == depth) {
                    delta = (long) Math.pow(1000, currDepth);
                } else {
                    delta = 0;
                }
            } else {
                delta = ((long) Math.pow(1000, currDepth)) * 999;
            }
            id += delta;
//            LOGGER.info("Guess - currDepth: {}, delta: {}, id: {}, mode: {}",
//                    ModelStringUtil.formatCsv(currDepth),
//                    ModelStringUtil.formatCsv(delta),
//                    id,
//                    mode);
            currDepth--;
        }
        return id;
    }

    /**
     * Find all direct child directories in path.
     * MUST be called within a try-with-resources block.
     */
    static Stream<Path> findDirectories(final Path path) throws IOException {
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
     * Get the {@link DirId} with the highest/lowest {@link DirId#num}.
     * Ignores files and non-numeric dirs.
     */
    private static Optional<DirId> findDir(final Path path,
                                           final Mode mode,
                                           final Predicate<String> filenamePredicate,
                                           final boolean warnIfInvalid) {

        LOGGER.trace("findDir() - path: {}, mode: {}", path, mode);
        try (final Stream<Path> dirStream = findDirectories(path)) {

            final Stream<DirId> dirIdStream = dirStream.map(
                            aPath -> {
                                final DirId dirId;
                                final String fileNamePart = aPath.getFileName().toString();

                                if (!filenamePredicate.test(fileNamePart)) {
                                    if (warnIfInvalid) {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "Found directory '{}' with unexpected filename length, " +
                                                "fileNamePart: '{}'. It will be ignored.",
                                                LogUtil.path(aPath), fileNamePart));
                                    }
                                    dirId = null;
                                } else {
                                    // Parse numeric part of dir.
                                    final OptionalLong optNum = parse(fileNamePart);
                                    if (optNum.isPresent()) {
                                        dirId = new DirId(aPath, optNum.getAsLong());
                                    } else {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "Found unexpected non-numeric directory '{}' in '{}'. " +
                                                "It will be ignored.",
                                                LogUtil.path(aPath), LogUtil.path(path)));
                                        dirId = null;
                                    }
                                }
                                return dirId;
                            })
                    .filter(Objects::nonNull);

            return switch (mode) {
                case MIN -> dirIdStream.min(DIR_ID_COMPARATOR_ASC);
                case MAX -> dirIdStream.max(DIR_ID_COMPARATOR_ASC);
            };

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
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
        LOGGER.trace("findDirs - path: {}, mode: {}", path, mode);
        try (final Stream<Path> dirStream = findDirectories(path)) {
            return dirStream.map(
                            aPath -> {
                                final DirId dirId;
                                if (pathPredicate.test(aPath)) {
                                    // Parse numeric part of dir.
                                    final String fileNamePart = aPath.getFileName().toString();
                                    final OptionalLong optNum = parse(fileNamePart);
                                    if (optNum.isPresent()) {
                                        dirId = new DirId(aPath, optNum.getAsLong());
                                    } else {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "findDirs() - Found unexpected non-numeric directory '{}' in '{}' " +
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

    public static Long pathToId(final Path path) {
        if (path == null) {
            return null;
        } else if (isValidLeafPath(path)) {
            final String fileNamePart = path.getFileName().toString();
            if (NullSafe.isNonBlankString(fileNamePart)) {
                return Long.parseLong(fileNamePart);
            } else {
                return null;
            }
        } else {
            return null;
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

    public static String makeSafeName(final String string) {
        return NullSafe.get(string, str ->
                SAFE_NAME_PATTERN.matcher(string)
                        .replaceAll("_"));
    }

    /**
     * @param feedKey
     * @return A dir name like '{@code <feed>__<type>}',
     * where {@link DirUtil#makeSafeName(String)} has been called for each part.
     */
    public static String makeSafeName(final FeedKey feedKey) {
        // Make a dir name.
        final StringBuilder sb = new StringBuilder();
        if (feedKey.feed() != null) {
            sb.append(DirUtil.makeSafeName(feedKey.feed()));
        }
        sb.append("__");
        if (feedKey.type() != null) {
            sb.append(DirUtil.makeSafeName(feedKey.type()));
        }
        return sb.toString();
    }

    /**
     * Creates a provider of unique paths such that each directory never contains more than 999 items.
     * <p>
     * See also {@link NumberedDirProvider} for a non-nested directory structure.
     * </p>
     * <p>
     * e.g. {@code root_path/2/333/555/333555777}
     * </p>
     */
    public static NestedNumberedDirProvider createNestedNumberedDirProvider(final Path root) {
        return new NestedNumberedDirProvider(root);
    }


    // --------------------------------------------------------------------------------


    enum Mode {
        MIN,
        MAX,
        ;
    }


    // --------------------------------------------------------------------------------


    private record DirId(Path dir, long num) {

    }
}
