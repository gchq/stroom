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

package stroom.meta.impl;

import stroom.data.retention.shared.DataRetentionRule;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * A utility class to calculate times from supplied retention ages.
 */
public final class DataRetentionAgeUtil {

    private DataRetentionAgeUtil() {
        // Utility class.
    }

    public static Long minus(final LocalDateTime now, final DataRetentionRule rule) {
        if (rule.isForever()) {
            return null;
        }

        final LocalDateTime age;
        switch (rule.getTimeUnit()) {
            case MINUTES:
                age = now.minusMinutes(rule.getAge());
                break;
            case HOURS:
                age = now.minusHours(rule.getAge());
                break;
            case DAYS:
                age = now.minusDays(rule.getAge());
                break;
            case WEEKS:
                age = now.minusWeeks(rule.getAge());
                break;
            case MONTHS:
                age = now.minusMonths(rule.getAge());
                break;
            case YEARS:
                age = now.minusYears(rule.getAge());
                break;
            default:
                age = null;
        }

        if (age == null) {
            return null;
        }

        return age.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static Long plus(final LocalDateTime now, final DataRetentionRule rule) {
        if (rule.isForever()) {
            return null;
        }

        final LocalDateTime age;
        switch (rule.getTimeUnit()) {
            case MINUTES:
                age = now.plusMinutes(rule.getAge());
                break;
            case HOURS:
                age = now.plusHours(rule.getAge());
                break;
            case DAYS:
                age = now.plusDays(rule.getAge());
                break;
            case WEEKS:
                age = now.plusWeeks(rule.getAge());
                break;
            case MONTHS:
                age = now.plusMonths(rule.getAge());
                break;
            case YEARS:
                age = now.plusYears(rule.getAge());
                break;
            default:
                age = null;
        }

        if (age == null) {
            return null;
        }

        return age.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
