package stroom.dashboard.expression.v1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class FormatterCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(FormatterCache.class);

    // Create cache
    private static final int MAX_ENTRIES = 1000;

    private static final Map<String, CachedFormatter> FORMATTER_CACHE = Collections.synchronizedMap(new LinkedHashMap<String, CachedFormatter>(MAX_ENTRIES + 1, .75F, true) {
        // This method is called just after a new entry has been added
        public boolean removeEldestEntry(Map.Entry eldest) {
            if (size() > MAX_ENTRIES) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Evicting old formatter: " + eldest.getKey());
                }
                return true;
            }
            return false;
        }
    });

    private static final Map<String, CachedZoneId> ZONEID_CACHE = Collections.synchronizedMap(new LinkedHashMap<String, CachedZoneId>(MAX_ENTRIES + 1, .75F, true) {
        // This method is called just after a new entry has been added
        public boolean removeEldestEntry(Map.Entry eldest) {
            if (size() > MAX_ENTRIES) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Evicting old zone id: " + eldest.getKey());
                }
                return true;
            }
            return false;
        }
    });

    private FormatterCache() {
        // Utility
    }

    static long parse(final String value, final String pattern, final String timeZone) {
        return DateUtil.parse(value, getFormatter(pattern), getZoneId(timeZone));
    }

    static String format(final Long value, final String pattern, final String timeZone) {
        return DateUtil.format(value, getFormatter(pattern), getZoneId(timeZone));
    }

    static DateTimeFormatter getFormatter(final String pattern) {
        if (pattern == null || pattern.equals(DateUtil.DEFAULT_PATTERN)) {
            return DateUtil.DEFAULT_FORMATTER;
        }

        // Get cached formatter.
        final CachedFormatter cachedFormatter = FORMATTER_CACHE.computeIfAbsent(pattern, k -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiling formatter: " + k);
            }
            try {
                return new CachedFormatter(DateTimeFormatter.ofPattern(k));
            } catch (final RuntimeException e) {
                return new CachedFormatter(e);
            }
        });
        if (cachedFormatter.exception != null) {
            throw cachedFormatter.exception;
        }
        return cachedFormatter.formatter;
    }

    static ZoneId getZoneId(final String timeZone) {
        if (timeZone == null) {
            return ZoneOffset.UTC;
        }

        // Get cached time zone.
        final CachedZoneId cachedZoneId = ZONEID_CACHE.computeIfAbsent(timeZone, k -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiling formatter: " + k);
            }
            try {
                return new CachedZoneId(DateUtil.getTimeZone(k));
            } catch (final ParseException e) {
                return new CachedZoneId(new RuntimeException(e.getMessage(), e));
            } catch (final RuntimeException e) {
                return new CachedZoneId(e);
            }
        });
        if (cachedZoneId.exception != null) {
            throw cachedZoneId.exception;
        }
        return cachedZoneId.zoneId;
    }

    private static class CachedFormatter {
        private final DateTimeFormatter formatter;
        private final RuntimeException exception;

        CachedFormatter(final DateTimeFormatter formatter) {
            this.formatter = formatter;
            this.exception = null;
        }

        CachedFormatter(final RuntimeException exception) {
            this.formatter = null;
            this.exception = exception;
        }
    }

    private static class CachedZoneId {
        private final ZoneId zoneId;
        private final RuntimeException exception;

        CachedZoneId(final ZoneId zoneId) {
            this.zoneId = zoneId;
            this.exception = null;
        }

        CachedZoneId(final RuntimeException exception) {
            this.zoneId = null;
            this.exception = exception;
        }
    }
}
