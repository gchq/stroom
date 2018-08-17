package stroom.feed;

import stroom.entity.shared.EntityServiceException;
import stroom.util.config.StroomProperties;

import java.util.Objects;
import java.util.regex.Pattern;

public class FeedNameValidator {
    private static final String FEED_NAME_PATTERN_PROPERTY = "stroom.feedNamePattern";
    private static final String FEED_NAME_PATTERN_VALUE = "^[A-Z0-9_\\-]{3,}$";

    private static Pattern pattern;
    private static String lastRegex;

    private FeedNameValidator() {
        // Utility.
    }

    static void validateName(final String name) {
        final String regex = StroomProperties.getProperty(FEED_NAME_PATTERN_PROPERTY, FEED_NAME_PATTERN_VALUE);
        if (name == null) {
            throw new EntityServiceException("Invalid name \"" + name + "\" ("
                    + regex + ")");
        }

        if (!Objects.equals(regex, lastRegex)) {
            pattern = Pattern.compile(regex);
            lastRegex = regex;
        }

        if (!pattern.matcher(name).matches()) {
            throw new EntityServiceException("Invalid name \"" + name + "\" ("
                    + pattern + ")");
        }
    }
}
