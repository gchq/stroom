package stroom.dashboard.expression.v1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class PatternCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(PatternCache.class);

    // Create cache
    private static final int MAX_ENTRIES = 1000;

    private static final Map<String, CachedPattern> MAP = Collections.synchronizedMap(new LinkedHashMap<String, CachedPattern>(MAX_ENTRIES + 1, .75F, true) {
        // This method is called just after a new entry has been added
        public boolean removeEldestEntry(Map.Entry eldest) {
            if (size() > MAX_ENTRIES) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Evicting old pattern: " + eldest.getKey());
                }
                return true;
            }
            return false;
        }
    });

    private PatternCache() {
        // Utility
    }

    static Pattern get(final String regex) {
        Objects.requireNonNull(regex, "Null regex");
        final CachedPattern cachedPattern = MAP.computeIfAbsent(regex, k -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiling pattern: " + k);
            }
            try {
                return new CachedPattern(Pattern.compile(k));
            } catch (final RuntimeException e) {
                return new CachedPattern(e);
            }
        });
        if (cachedPattern.exception != null) {
            throw cachedPattern.exception;
        }
        return cachedPattern.pattern;
    }

    private static class CachedPattern {
        private final Pattern pattern;
        private final RuntimeException exception;

        CachedPattern(final Pattern pattern) {
            this.pattern = pattern;
            this.exception = null;
        }

        CachedPattern(final RuntimeException exception) {
            this.pattern = null;
            this.exception = exception;
        }
    }
}
