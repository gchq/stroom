package stroom.proxy.app.handler;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class NumberedDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NumberedDirProvider.class);

    static final Comparator<DirId> DIR_ID_COMPARATOR = Comparator.comparingLong(DirId::num);

    private final Path parentDir;
    private final AtomicLong sequence = new AtomicLong();

    public NumberedDirProvider(final Path parentDir) {
        if (!Files.isDirectory(parentDir)) {
            throw new IllegalArgumentException(LogUtil.message(
                    "parentDir '{}' is not a directory or does not exist", LogUtil.path(parentDir)));
        }
        this.parentDir = parentDir;
        // Set the sequence to the number of the highest numbered file in the dir
        findDir(parentDir)
                .map(DirId::num)
                .ifPresent(sequence::set);
        LOGGER.debug(() -> LogUtil.message("parentDir '{}', sequence {}", LogUtil.path(parentDir), sequence));
    }

    /**
     * Gets a new numbered directory with a number one higher than the last one issued.
     */
    public Path get() throws IOException {
        final long id = sequence.incrementAndGet();
        final String name = create(id);
        final Path path = parentDir.resolve(name);
        return Files.createDirectory(path);
    }

    /**
     * Create a string to use as part of a file name that is a `0` padded number.
     *
     * @param num The number to create the name from.
     * @return A `0` padded string representing the supplied number.
     */
    private String create(final long num) {
        return Strings.padStart(Long.toString(num), 10, '0');
    }

    /**
     * MUST be called within a try-with-resources block.
     * Find all direct child directories in path.
     */
    private static Stream<Path> findDirectories(final Path path) throws IOException {
        return Files.find(path, 1,
                        (aPath, basicFileAttributes) -> {
                            LOGGER.trace(() -> LogUtil.message("aPath: {}, isDirectory {}",
                                    aPath, basicFileAttributes.isDirectory()));
                            if (basicFileAttributes.isDirectory()) {
                                return true;
                            } else {
                                LOGGER.warn(() -> LogUtil.message(
                                        "Found unexpected file '{}'. It will be ignored.",
                                        LogUtil.path(aPath)));
                                return false;
                            }
                        })
                .filter(aPath -> !path.equals(aPath));  // ignore the start path which Files.find includes
    }

    /**
     * Get the {@link DirId} with the highest {@link DirId#num}. Ignores files and non-numeric dirs.
     */
    private Optional<DirId> findDir(final Path path) {
        try (final Stream<Path> stream = findDirectories(path)) {
            return stream.map(
                            aPath -> {
                                final DirId dirId;
                                // Parse numeric part of dir.
                                final OptionalLong optNum = parse(aPath.getFileName().toString());
                                if (optNum.isPresent()) {
                                    dirId = new DirId(aPath, optNum.getAsLong());
                                } else {
                                    LOGGER.warn(() -> LogUtil.message(
                                            "Found unexpected non-numeric directory '{}' in '{}'. It will be ignored.",
                                            LogUtil.path(aPath), LogUtil.path(parentDir)));
                                    dirId = null;
                                }
                                return dirId;
                            })
                    .filter(Objects::nonNull)
                    .max(DIR_ID_COMPARATOR);
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

    @Override
    public String toString() {
        return "NumberedDirProvider{" +
               "parentDir=" + parentDir +
               ", sequence=" + sequence +
               '}';
    }

    // --------------------------------------------------------------------------------


    record DirId(Path dir, long num) {

    }
}
