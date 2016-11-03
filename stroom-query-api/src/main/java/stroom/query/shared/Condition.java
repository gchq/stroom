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

package stroom.query.shared;

import stroom.util.shared.HasDisplayValue;

/**
 * Represents query values
 */
public enum Condition implements HasDisplayValue {
    CONTAINS("contains"), EQUALS("="), GREATER_THAN(">"), GREATER_THAN_OR_EQUAL_TO(">="), LESS_THAN(
            "<"), LESS_THAN_OR_EQUAL_TO("<="), BETWEEN("between"), IN("in"), IN_DICTIONARY("in dictionary");

    private final String displayValue;

    public static final Condition[] SIMPLE_CONDITIONS = { EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO, LESS_THAN,
            LESS_THAN_OR_EQUAL_TO, BETWEEN };

    public static final String IN_CONDITION_DELIMITER = ",";

    Condition(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
