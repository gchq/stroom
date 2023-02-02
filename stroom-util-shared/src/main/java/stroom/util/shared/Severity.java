/*
 * Copyright 2016 Crown Copyright
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

import java.util.Collection;
import java.util.Comparator;


public enum Severity implements HasDisplayValue {
    // In case anyone is using the default enum ordinal compare, these must be defined
    // in ascending order of severity and so must the IDs
    INFO(1, "INFO", "Information"),
    WARNING(2, "WARN", "Warnings"),
    ERROR(3, "ERROR", "Errors"),
    FATAL_ERROR(4, "FATAL", "Fatal Errors");

    public static final Severity[] SEVERITIES = {FATAL_ERROR, ERROR, WARNING, INFO};

    /**
     * Comparator for comparing severities with nulls first
     */
    public static final Comparator<Severity> COMPARATOR = Comparator.nullsFirst(Comparator.comparing(Severity::getId));

    private final int id;
    private final String displayValue;
    private final String summaryValue;

    Severity(final int id, final String displayValue, final String summaryValue) {
        this.id = id;
        this.displayValue = displayValue;
        this.summaryValue = summaryValue;
    }

    public static Severity getSeverity(final String displayValue) {
        if (displayValue != null) {
            final String val = displayValue.trim();
            if (INFO.getDisplayValue().equalsIgnoreCase(val)) {
                return INFO;
            } else if (WARNING.getDisplayValue().equalsIgnoreCase(val)) {
                return WARNING;
            } else if (ERROR.getDisplayValue().equalsIgnoreCase(val)) {
                return ERROR;
            } else if (FATAL_ERROR.getDisplayValue().equalsIgnoreCase(val)) {
                return FATAL_ERROR;
            }
        }

        return null;
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
                    .max(COMPARATOR)
                    .orElse(defaultValue);
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
