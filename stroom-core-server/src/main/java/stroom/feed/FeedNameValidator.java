package stroom.feed;

import stroom.datafeed.DataFeedConfig;
import stroom.entity.shared.EntityServiceException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.regex.Pattern;

@Singleton
public class FeedNameValidator {
    private final DataFeedConfig config;
    private Pattern pattern;
    private String lastRegex;

    @Inject
    public FeedNameValidator(final DataFeedConfig config) {
        this.config = config;
    }

    void validateName(final String name) {
        final String regex = config.getFeedNamePattern();
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
