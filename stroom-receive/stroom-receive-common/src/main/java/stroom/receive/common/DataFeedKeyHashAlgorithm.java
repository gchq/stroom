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

package stroom.receive.common;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.NullSafe;

import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DataFeedKeyHashAlgorithm implements HasDisplayValue {
    // uniqueIds should be
    ARGON2("Argon2", 0),
    BCRYPT_2A("BCrypt (2a)", 1),
    ;

    private static final DataFeedKeyHashAlgorithm[] SPARSE_ALGORITHM_ARRAY;
    private static final Map<String, DataFeedKeyHashAlgorithm> NAME_TO_VALUE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(DataFeedKeyHashAlgorithm::getDisplayValue, Function.identity()));

    static {
        final DataFeedKeyHashAlgorithm[] values = DataFeedKeyHashAlgorithm.values();
        final int maxPrimitive = Arrays.stream(values)
                .mapToInt(dataFeedKeyHashAlgorithm -> dataFeedKeyHashAlgorithm.uniqueId)
                .max()
                .orElseThrow(() -> new RuntimeException("Empty values array supplied"));
        SPARSE_ALGORITHM_ARRAY = new DataFeedKeyHashAlgorithm[maxPrimitive + 1];
        for (final DataFeedKeyHashAlgorithm value : values) {
            SPARSE_ALGORITHM_ARRAY[value.uniqueId] = value;
        }
    }

    private final String displayValue;
    private final int uniqueId;

    DataFeedKeyHashAlgorithm(final String displayValue, final int uniqueId) {
        if (uniqueId < 0) {
            throw new IllegalArgumentException("Min uniqueId is 0");
        }
        if (uniqueId > 999) {
            throw new IllegalArgumentException("Max uniqueId is 999");
        }
        this.displayValue = displayValue;
        this.uniqueId = uniqueId;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * @return A 3 digit, zero padded number.
     */
    public String getUniqueId() {
        return Strings.padStart(String.valueOf(uniqueId), 3, '0');
    }

    public static DataFeedKeyHashAlgorithm fromDisplayValue(final String displayValue) {
        if (displayValue == null) {
            return null;
        } else if (NullSafe.isBlankString(displayValue)) {
            throw new IllegalArgumentException("Blank displayValue");
        } else {
            final DataFeedKeyHashAlgorithm hashAlgorithm = NAME_TO_VALUE_MAP.get(displayValue);
            if (hashAlgorithm == null) {
                throw new IllegalArgumentException("Unknown displayValue " + displayValue);
            }
            return hashAlgorithm;
        }
    }

    public static DataFeedKeyHashAlgorithm fromUniqueId(final String uniqueId) {
        if (uniqueId == null) {
            return null;
        } else if (uniqueId.isBlank()) {
            throw new IllegalArgumentException("Blank uniqueId");
        } else {
            final int intVal = Integer.parseInt(uniqueId);
            final DataFeedKeyHashAlgorithm dataFeedKeyHashAlgorithm;
            try {
                dataFeedKeyHashAlgorithm = SPARSE_ALGORITHM_ARRAY[intVal];
                if (dataFeedKeyHashAlgorithm == null) {
                    throw new IllegalArgumentException("Unknown uniqueId " + uniqueId);
                }
                return dataFeedKeyHashAlgorithm;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String toString() {
        return "DataFeedKeyHashAlgorithm{" +
               "displayValue='" + displayValue + '\'' +
               ", uniqueId=" + uniqueId +
               '}';
    }
}
