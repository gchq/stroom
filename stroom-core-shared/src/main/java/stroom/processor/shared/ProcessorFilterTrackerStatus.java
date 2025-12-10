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

package stroom.processor.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/**
 * The status of this stream process.
 */
public enum ProcessorFilterTrackerStatus implements HasDisplayValue, HasPrimitiveValue {
    /**
     * The default state for a processor filter tracker after it is created.
     */
    CREATED("Created", 0),
    /**
     * The filter has created all tasks on the DB and will not create more.
     */
    COMPLETE("Complete", 10),
    /**
     * An error occurred when trying to create tasks for some reason.
     */
    ERROR("Error", 22);

    public static final PrimitiveValueConverter<ProcessorFilterTrackerStatus> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(ProcessorFilterTrackerStatus.class, ProcessorFilterTrackerStatus.values());

    private final String displayValue;
    private final byte primitiveValue;

    ProcessorFilterTrackerStatus(final String displayValue, final int primitiveValue) {
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
