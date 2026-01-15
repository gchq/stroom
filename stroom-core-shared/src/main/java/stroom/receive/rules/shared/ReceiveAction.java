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

package stroom.receive.rules.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasCaseInsensitiveForm.CaseInsensitiveConverter;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.function.Supplier;

/**
 * The action to perform when data is received by stroom/proxy.
 */
public enum ReceiveAction implements HasDisplayValue {
    /**
     * Data will be accepted.
     */
    RECEIVE("Receive", true),
    /**
     * The client will receive an error response. The data will not be accepted.
     */
    REJECT("Reject", false),
    /**
     * Data will be silently dropped with no error. The client will receive a 200 response.
     */
    DROP("Drop", false),
    ;

    private static final CaseInsensitiveConverter<ReceiveAction> CASE_INSENSITIVE_CONVERTER =
            CaseInsensitiveConverter.create(ReceiveAction.class);

    private final String displayValue;
    private final boolean filterOutcome;

    ReceiveAction(final String displayValue, final boolean filterOutcome) {
        this.displayValue = displayValue;
        this.filterOutcome = filterOutcome;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public boolean getFilterOutcome() {
        return filterOutcome;
    }

    /**
     * Convert the {@link ReceiveAction} into a boolean filter result, throwing the exception
     * supplied by rejectionExceptionSupplier if the {@link ReceiveAction} is {@link ReceiveAction#REJECT}.
     */
    public boolean toFilterResultOrThrow(final Supplier<RuntimeException> rejectionExceptionSupplier) {
        if (this == REJECT && rejectionExceptionSupplier != null) {
            throw rejectionExceptionSupplier.get();
        } else {
            return filterOutcome;
        }
    }

    /**
     * Allow deserialisation from the enum's name in any case.
     */
    @JsonCreator
    public static ReceiveAction fromCaseInsensitiveString(final String name) {
        return CASE_INSENSITIVE_CONVERTER.convert(name);
    }

//    @JsonValue
//    @Override
//    public String toString() {
//        return super.toString();
//    }
}
