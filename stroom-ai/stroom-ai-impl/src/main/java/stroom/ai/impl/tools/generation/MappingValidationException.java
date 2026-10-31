/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.generation;

import java.util.List;

/// A mapping (or a part of one) that doesn't validate, with each [MappingError] found.
public final class MappingValidationException extends IllegalArgumentException {

    private final String model;
    private final transient List<MappingError> errors;

    /// @param model  The name of the model read, e.g. `TranslationMapping`.
    /// @param errors What is wrong, at least one.
    public MappingValidationException(final String model, final List<MappingError> errors) {
        super(message(model, errors));
        this.model = model;
        this.errors = List.copyOf(errors);
    }

    /// @return The name of the model read.
    public String model() {
        return model;
    }

    /// @return What is wrong, in the order it was found.
    public List<MappingError> errors() {
        return errors;
    }

    // The count, the model, then each error's place and message (`1 validation error for TranslationMapping`).
    private static String message(final String model, final List<MappingError> errors) {
        final StringBuilder sb = new StringBuilder().append(errors.size()).append(" validation error")
                .append(errors.size() == 1
                        ? ""
                        : "s").append(" for ").append(model);
        for (final MappingError error : errors) {
            sb.append('\n').append(error.dotted()).append("\n  ").append(error.message())
                    .append(" [type=").append(error.type()).append(']');
        }
        return sb.toString();
    }
}
