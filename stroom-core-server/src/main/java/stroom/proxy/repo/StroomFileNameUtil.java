package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;

import java.util.Arrays;

/**
 * Utility to build a path for a given id in such a way that we don't exceed OS
 * dir file limits.
 * <p>
 * So file 1 is 001 and file 1000 is 001/000 etc...
 */
public final class StroomFileNameUtil {
    private final static int MAX_PART_LENGTH = 255;

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomFileNameUtil.class);

    private static final String MISSING_VALUE = "_";
    private static final char TRUNCATED_VALUE = '_';
    private static final char INVALID_CHAR_REPLACEMENT = '_';
    private static final char PATH_SEPARATOR = '/';
    private static final String PATH_SEPARATOR_STRING = "/";

    private StroomFileNameUtil() {
        //static util methods only
    }

    public static String getIdPath(final long id) {
        final String idString = idToString(id);
        final String path = idToPathId(idString) + PATH_SEPARATOR + idString;
        return clean(path);
    }

    static String idToString(long id) {
        final StringBuilder sb = new StringBuilder();
        sb.append(id);
        // Pad out e.g. 10100 -> 010100
        while ((sb.length() % 3) != 0) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    private static String idToPathId(final String id) {
        final StringBuilder sb = new StringBuilder();
        if (id.length() > 3) {
            for (int i = 0; i < id.length() - 3; i += 3) {
                sb.append(id.subSequence(i, i + 3));
                sb.append(PATH_SEPARATOR);
            }
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Clean a path so that it doesn't contain leading or trailing separators, no double separators and no invalid characters.
     *
     * @param path The path to clean.
     * @return A cleaned path.
     */
    private static String clean(String path) {
        final StringBuilder sb = new StringBuilder();
        final String[] parts = path.split(PATH_SEPARATOR_STRING);
        for (final String part : parts) {
            if (part.length() > 0) {
                sb.append(cleanPart(part));
                sb.append("/");
            }
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Clean part of a path so that it doesn't contain invalid characters and is less than the maximum length of a part.
     *
     * @param part The part of the path to clean.
     * @return A cleaned path part.
     */
    private static String cleanPart(String part) {
        final char[] in = part.toCharArray();
        final char[] out = new char[in.length];
        int outIndex = 0;

        for (final char c : in) {
            if (Character.isAlphabetic(c) || Character.isDigit(c) || c == '.' || c == '_' || c == '-') {
                out[outIndex++] = c;
            } else {
                out[outIndex++] = INVALID_CHAR_REPLACEMENT;
            }
        }

        if (outIndex > MAX_PART_LENGTH) {
            LOGGER.debug("File part exceeds max length: {}", part);
            outIndex = MAX_PART_LENGTH - 1;
            out[outIndex++] = TRUNCATED_VALUE;
        }

        return new String(out, 0, outIndex);
    }

    static String constructFilename(long id, final String template,
                                    final MetaMap metaMap,
                                    String... fileExtensions) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Using" +
                    " template " +
                    template +
                    " and fileExtensions " +
                    Arrays.toString(fileExtensions));
        }

        final String idStr = idToString(id);
        final String pathIdStr = idToPathId(idStr);

        String path = template;
        // Replace path template with header and time values.
        path = PathCreator.replaceAll(path, metaMap);
        // Replace pathId variable with path id.
        path = PathCreator.replace(path, "pathId", pathIdStr);
        // Replace id variable with file id.
        path = PathCreator.replace(path, "id", idStr);

        // Check for any remaining variables.
        final String[] remainingVars = PathCreator.findVars(path);
        if (remainingVars.length > 0) {
            LOGGER.warn("Unused variables found: " + Arrays.toString(remainingVars));

            // Replace all missing values with an underscore.
            for (final String remainingVar : remainingVars) {
                path = PathCreator.replace(path, remainingVar, MISSING_VALUE);
            }
        }

        // Clean the path.
        path = clean(path);

        // Append file extensions.
        if (fileExtensions != null) {
            final StringBuilder sb = new StringBuilder(path);
            for (String extension : fileExtensions) {
                if (extension != null) {
                    sb.append(extension);
                }
            }
            path = sb.toString();
        }

        return path;
    }
}