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

package stroom.streamstore.shared;

import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.util.shared.HasDisplayValue;

/**
 * <p>
 * The type of lock held on the stream. For the moment this is just simple lock
 * and unlocked.
 * </p>
 */
public enum StreamStatus implements HasDisplayValue, HasPrimitiveValue {
    UNLOCKED("Unlocked", 0), /**
     * Open exclusive lock.
     */
    LOCKED("Locked", 1), /**
     * Logical Delete
     */
    DELETED("Deleted", 99);

    public static final PrimitiveValueConverter<StreamStatus> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<StreamStatus>(
            StreamStatus.values());

    private final String displayValue;
    private final byte primitiveValue;

    StreamStatus(final String displayValue, int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    /**
     * @return drop down string value.
     */
    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
