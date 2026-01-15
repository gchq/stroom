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
public enum TaskStatus implements HasDisplayValue, HasPrimitiveValue {
    /**
     * The task has been created but not currently queued for processing.
     */
    CREATED("Created", 0),
    /**
     * The task is queued for processing.
     */
    QUEUED("Queued", 1),
    /**
     * It has been assigned to a node for processing.
     */
    @Deprecated
    ASSIGNED("Assigned", 2),
    /**
     * A node is processing.
     */
    PROCESSING("Processing", 3),
    /**
     * A node has completed processing the task.
     */
    COMPLETE("Complete", 10),
    /**
     * The task failed to process for some reason.
     */
    FAILED("Failed", 22),
    /**
     * The task has been deleted.
     */
    DELETED("Deleted", 99);

    public static final PrimitiveValueConverter<TaskStatus> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(TaskStatus.class, TaskStatus.values());

    private final String displayValue;
    private final byte primitiveValue;

    TaskStatus(final String displayValue, final int primitiveValue) {
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
