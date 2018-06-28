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

import stroom.docref.SharedObject;

public enum Severity implements HasDisplayValue, SharedObject {
    INFO(1, "INFO", "Information"), WARNING(2, "WARN", "Warnings"), ERROR(3, "ERROR", "Errors"), FATAL_ERROR(4, "FATAL",
            "Fatal Errors");

    public static final Severity[] SEVERITIES = {FATAL_ERROR, ERROR, WARNING, INFO};

    private int id;
    private String displayValue;
    private String summaryValue;

    private Severity(final int id, final String displayValue, final String summaryValue) {
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

    @Override
    public String toString() {
        return displayValue;
    }
}
