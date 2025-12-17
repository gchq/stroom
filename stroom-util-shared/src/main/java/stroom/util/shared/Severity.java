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

package stroom.util.shared;

import stroom.docref.HasDisplayValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum Severity implements HasDisplayValue {
    // In case anyone is using the default enum ordinal compare, these must be defined
    // in ascending order of severity and so must the IDs
    INFO(1, "INFO", "Information"),
    WARNING(2, "WARN", "Warnings"),
    ERROR(3, "ERROR", "Errors"),
    FATAL_ERROR(4, "FATAL", "Fatal Errors");

    /**
     * Comparator for comparing severities with nulls first then lowest to highest severity
     */
    public static final Comparator<Severity> LOW_TO_HIGH_COMPARATOR = Comparator.nullsFirst(
            Comparator.comparing(Severity::getId));

    /**
     * Comparator for comparing severities with nulls last and highest to lowest severity
     */
    public static final Comparator<Severity> HIGH_TO_LOW_COMPARATOR = Comparator.nullsLast(
            Comparator.comparing(Severity::getId).reversed());

    /**
     * Array of severities in descending order of importance, i.e. FATAL_ERROR first
     */
    public static final Severity[] SEVERITIES = Arrays.stream(Severity.values())
            .sorted(HIGH_TO_LOW_COMPARATOR)
            .toArray(Severity[]::new);

    private static final Map<String, Severity> NAME_TO_SEVERITY_MAP = new HashMap<>();

    static {
        for (final Severity severity : Severity.values()) {
            NAME_TO_SEVERITY_MAP.put(severity.displayValue.trim().toLowerCase(), severity);
        }
    }

    private final int id;
    private final String displayValue;
    private final String summaryValue;

    Severity(final int id, final String displayValue, final String summaryValue) {
        this.id = id;
        this.displayValue = displayValue;
        this.summaryValue = summaryValue;
    }

    public static Severity getSeverity(final String displayValue) {
        if (NullSafe.isBlankString(displayValue)) {
            return null;
        } else {
            return NAME_TO_SEVERITY_MAP.get(displayValue.trim().toLowerCase());
        }
    }

    public boolean greaterThan(final Severity o) {
        return id > o.id;
    }

    public boolean greaterThanOrEqual(final Severity o) {
        return id >= o.id;
    }

    public boolean lessThan(final Severity o) {
        return id < o.id;
    }

    public boolean lessThanOrEqual(final Severity o) {
        return id <= o.id;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getSummaryValue() {
        return summaryValue;
    }

    private int getId() {
        return id;
    }

    @Override
    public String toString() {
        return displayValue;
    }

    /**
     * Get the highest (most severe) severity in the list of severities. If there are no severities,
     * return {@code defaultValue}.
     */
    public static <T extends Collection<Severity>> Severity getMaxSeverity(final T severities,
                                                                           final Severity defaultValue) {
        if (severities == null || severities.isEmpty()) {
            return defaultValue;
        } else {
            // GWT so can't use requireNonNullElse
            return severities.stream()
                    .map(severity -> severity != null
                            ? severity
                            : defaultValue)
                    .max(LOW_TO_HIGH_COMPARATOR)
                    .orElse(defaultValue);
        }
    }

    public Optional<Severity> getMaxSeverity(final Severity other) {
        return getMaxSeverity(this, other);
    }

    public static Optional<Severity> getMaxSeverity(final Severity severity1, final Severity severity2) {
        if (severity1 == null) {
            return Optional.ofNullable(severity2);
        } else if (severity2 == null) {
            return Optional.of(severity1);
        } else if (severity1.greaterThanOrEqual(severity2)) {
            return Optional.of(severity1);
        } else {
            return Optional.of(severity2);
        }
    }

    /**
     * Return this severity if it is greater than or equal to minimumSeverity
     * else return minimumSeverity.
     */
    public Severity atLeast(final Severity minimumSeverity) {
        if (minimumSeverity == null || this.greaterThanOrEqual(minimumSeverity)) {
            return this;
        } else {
            return minimumSeverity;
        }
    }
}
