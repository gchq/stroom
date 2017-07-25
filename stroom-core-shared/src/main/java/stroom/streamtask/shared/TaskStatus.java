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

package stroom.streamtask.shared;

import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.util.shared.HasDisplayValue;

/**
 * The status of this stream process.
 */
public enum TaskStatus implements HasDisplayValue, HasPrimitiveValue {
    /**
     * Unprocessed - yet to be locked
     */
    UNPROCESSED("Unprocessed", 1), /**
     * Assigned - some one has locked it
     */
    ASSIGNED("Assigned", 2), PROCESSING("Processing", 3), COMPLETE("Complete", 10), FAILED("Failed",
            22), DELETED("Deleted", 99);

    public static final PrimitiveValueConverter<TaskStatus> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<TaskStatus>(
            TaskStatus.values());

    private final String displayValue;
    private final byte primitiveValue;

    TaskStatus(final String displayValue, int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    /**
     * @return string used in drop downs.
     */
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
