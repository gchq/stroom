/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.retention.impl;

import stroom.data.retention.shared.DataRetentionRule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * A utility class to calculate times from supplied retention ages.
 */
final class DataRetentionCreationTimeUtil {
    private DataRetentionCreationTimeUtil() {
        // Utility class.
    }

    static long minus(final long ms, final DataRetentionRule rule) {
        if (rule.isForever()) {
            return 0;
        }

        LocalDateTime age = null;
        final LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC);
        switch (rule.getTimeUnit()) {
            case MINUTES:
                age = time.minusMinutes(rule.getAge());
                break;
            case HOURS:
                age = time.minusHours(rule.getAge());
                break;
            case DAYS:
                age = time.minusDays(rule.getAge());
                break;
            case WEEKS:
                age = time.minusWeeks(rule.getAge());
                break;
            case MONTHS:
                age = time.minusMonths(rule.getAge());
                break;
            case YEARS:
                age = time.minusYears(rule.getAge());
                break;
        }

        if (age == null) {
            return 0;
        }

        return age.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    static long plus(final long ms, final DataRetentionRule rule) {
        if (rule.isForever()) {
            return 0;
        }

        LocalDateTime age = null;
        final LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC);
        switch (rule.getTimeUnit()) {
            case MINUTES:
                age = time.plusMinutes(rule.getAge());
                break;
            case HOURS:
                age = time.plusHours(rule.getAge());
                break;
            case DAYS:
                age = time.plusDays(rule.getAge());
                break;
            case WEEKS:
                age = time.plusWeeks(rule.getAge());
                break;
            case MONTHS:
                age = time.plusMonths(rule.getAge());
                break;
            case YEARS:
                age = time.plusYears(rule.getAge());
                break;
        }

        if (age == null) {
            return 0;
        }

        return age.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
