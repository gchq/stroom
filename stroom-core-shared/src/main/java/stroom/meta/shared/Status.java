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
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * The type of lock held on the data. For the moment this is just a simple locked
 * and unlocked.
 * </p>
 */
public enum Status implements HasDisplayValue, HasPrimitiveValue {

    UNLOCKED(0, "Unlocked"),
    /**
     * Open exclusive lock.
     */
    LOCKED(1, "Locked"),
    /**
     * Logical Delete
     */
    DELETED(99, "Deleted");

    public static final PrimitiveValueConverter<Status> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(Status.class, Status.values());

    /**
     * The DB primitive value
     */
    private final int id;
    private final String displayValue;

    Status(final int id,
           final String displayValue) {
        PrimitiveValueConverter.castId(Status.class, id);
        this.id = id;
        this.displayValue = displayValue;
    }

    /**
     * @return drop down string value.
     */
    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * @return Value stored in the database.
     */
    @Override
    public byte getPrimitiveValue() {
        return (byte) id;
    }

    /**
     * Gets the primitive value for the given status name.
     *
     * @param statusName The status name.
     * @return The primitive value.
     */
    public static byte getPrimitiveValue(final String statusName) {
        if (NullSafe.isBlankString(statusName)) {
            throw new IllegalArgumentException("No status name supplied");
        }
        try {
            final Status status = Status.valueOf(statusName.toUpperCase());
            return status.getPrimitiveValue();
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException("Status '" + statusName + "' is not a valid status value. Valid values are: "
                                       + Arrays.stream(Status.values())
                                               .map(Objects::toString)
                                               .collect(Collectors.joining(", ")), e);
        }
    }
}
