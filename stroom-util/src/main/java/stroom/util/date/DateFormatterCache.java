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

package stroom.util.date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DateFormatterCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DateFormatterCache.class);

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";
    private static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern(DEFAULT_PATTERN, Locale.ENGLISH);
    private static final String GMT_BST_GUESS = "GMT/BST";
    private static final ZoneId EUROPE_LONDON_TIME_ZONE = ZoneId.of("Europe/London");

    // Create cache
    private static final int MAX_ENTRIES = 1000;

    private static final Map<CachedFormatterKey, CachedFormatterValue> FORMATTER_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(
                    MAX_ENTRIES + 1,
                    .75F,
                    true) {
                // This method is called just after a new entry has been added
                public boolean removeEldestEntry(final Map.Entry eldest) {
                    if (size() > MAX_ENTRIES) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Evicting old formatter: " + eldest.getKey());
                        }
                        return true;
                    }
                    return false;
                }
            });

    private static final Map<String, CachedZoneIdValue> ZONEID_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(
                    MAX_ENTRIES + 1,
                    .75F,
                    true) {
                // This method is called just after a new entry has been added
                public boolean removeEldestEntry(final Map.Entry eldest) {
                    if (size() > MAX_ENTRIES) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Evicting old zone id: " + eldest.getKey());
                        }
                        return true;
                    }
                    return false;
                }
            });

    private DateFormatterCache() {
        // Utility
    }

    public static DateTimeFormatter getFormatter(final String pattern) {
        if (pattern == null || pattern.equals(DEFAULT_PATTERN)) {
            return DEFAULT_FORMATTER;
        }

        final CachedFormatterKey key = new CachedFormatterKey(pattern, null);

        // Get cached formatter.
        final CachedFormatterValue cachedFormatter = FORMATTER_CACHE.computeIfAbsent(key, k -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiling formatter: " + k);
            }
            try {
                return new CachedFormatterValue(DateTimeFormatter.ofPattern(k.pattern, Locale.ENGLISH));
            } catch (final RuntimeException e) {
                return new CachedFormatterValue(e);
            }
        });
        if (cachedFormatter.exception != null) {
            throw cachedFormatter.exception;
        }
        return cachedFormatter.formatter;
    }

    public static DateTimeFormatter getDefaultingFormatter(final String pattern, final ZonedDateTime defaultTime) {
        if (pattern == null || pattern.equals(DEFAULT_PATTERN)) {
            return DEFAULT_FORMATTER;
        }

        final CachedFormatterKey key = new CachedFormatterKey(pattern, defaultTime);

        // Get cached formatter.
        final CachedFormatterValue cachedFormatter = FORMATTER_CACHE.computeIfAbsent(key, k -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiling defaulting formatter: " + k);
            }
            try {
                final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(k.pattern)
                        .parseDefaulting(ChronoField.YEAR_OF_ERA, k.defaultTime.get(ChronoField.YEAR_OF_ERA))
                        .parseDefaulting(ChronoField.MONTH_OF_YEAR, k.defaultTime.get(ChronoField.MONTH_OF_YEAR))
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, k.defaultTime.get(ChronoField.DAY_OF_MONTH))
                        .toFormatter(Locale.ENGLISH);
                return new CachedFormatterValue(formatter);
            } catch (final RuntimeException e) {
                return new CachedFormatterValue(e);
            }
        });
        if (cachedFormatter.exception != null) {
            throw cachedFormatter.exception;
        }
        return cachedFormatter.formatter;
    }

    public static ZoneId getZoneId(final String timeZone) {
        if (timeZone == null) {
            return ZoneOffset.UTC;
        }

        // Get cached time zone.
        final CachedZoneIdValue cachedZoneId = ZONEID_CACHE.computeIfAbsent(timeZone, k -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiling formatter: " + k);
            }
            try {
                return new CachedZoneIdValue(getTimeZone(k));
            } catch (final ParseException e) {
                return new CachedZoneIdValue(new RuntimeException(e.getMessage(), e));
            } catch (final RuntimeException e) {
                return new CachedZoneIdValue(e);
            }
        });
        if (cachedZoneId.exception != null) {
            throw cachedZoneId.exception;
        }
        return cachedZoneId.zoneId;
    }

    private static ZoneId getTimeZone(final String timeZone) throws ParseException {
        final ZoneId dateTimeZone;
        try {
            if (timeZone != null) {
                if (GMT_BST_GUESS.equals(timeZone)) {
                    dateTimeZone = EUROPE_LONDON_TIME_ZONE;
                } else {
                    dateTimeZone = ZoneId.of(timeZone);
                }
            } else {
                dateTimeZone = ZoneOffset.UTC;
            }
        } catch (final DateTimeException | IllegalArgumentException e) {
            throw new ParseException("Time Zone '" + timeZone + "' is not recognised", 0);
        }

        return dateTimeZone;
    }

    private static class CachedFormatterKey {

        private final String pattern;
        private final ZonedDateTime defaultTime;
        private final int hashCode;

        CachedFormatterKey(final String pattern, final ZonedDateTime defaultTime) {
            this.pattern = pattern;
            this.defaultTime = defaultTime;
            this.hashCode = Objects.hash(pattern, defaultTime);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final CachedFormatterKey that = (CachedFormatterKey) o;
            return Objects.equals(pattern, that.pattern) &&
                    Objects.equals(defaultTime, that.defaultTime);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return pattern;
        }
    }

    private static class CachedFormatterValue {

        private final DateTimeFormatter formatter;
        private final RuntimeException exception;

        CachedFormatterValue(final DateTimeFormatter formatter) {
            this.formatter = formatter;
            this.exception = null;
        }

        CachedFormatterValue(final RuntimeException exception) {
            this.formatter = null;
            this.exception = exception;
        }
    }

    private static class CachedZoneIdValue {

        private final ZoneId zoneId;
        private final RuntimeException exception;

        CachedZoneIdValue(final ZoneId zoneId) {
            this.zoneId = zoneId;
            this.exception = null;
        }

        CachedZoneIdValue(final RuntimeException exception) {
            this.zoneId = null;
            this.exception = exception;
        }
    }
}
