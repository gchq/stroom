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

package stroom.meta.shared;

import stroom.docref.HasDisplayValue;

/**
 * <p>
 * The type of lock held on the data. For the moment this is just simple lock
 * and unlocked.
 * </p>
 */
public enum Status implements HasDisplayValue {
    UNLOCKED("Unlocked"),
    /**
     * Open exclusive lock.
     */
    LOCKED("Locked"),
    /**
     * Logical Delete
     */
    DELETED("Deleted");

    private final String displayValue;

    Status(final String displayValue) {
        this.displayValue = displayValue;
    }

    /**
     * @return drop down string value.
     */
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
