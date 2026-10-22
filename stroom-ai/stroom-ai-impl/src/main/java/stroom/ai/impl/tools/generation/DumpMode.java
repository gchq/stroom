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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/// How a mapping model is written as JSON, as pydantic's `model_dump` writes it in the Stroom MCP server: every
/// field (`model_dump(mode='json')`), without the null ones (`exclude_none=True`), or without those either null or
/// at their default (`exclude_none=True, exclude_defaults=True`: the form a mapping is kept in, in a document's
/// description). Keys are in the models' field order.
public enum DumpMode {
    /// Every field, null ones too.
    ALL,
    /// Every field that isn't null.
    WITHOUT_NONE,
    /// Every field that is neither null nor its default.
    COMPACT;

    /// @return A builder of one model's JSON in this mode.
    Builder builder() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    /// Builds one model's JSON.
    static final class Builder {

        private final DumpMode mode;
        private final Map<String, Object> map = new LinkedHashMap<>();

        private Builder(final DumpMode mode) {
            this.mode = mode;
        }

        /// @param key          A field's name.
        /// @param value        Its value.
        /// @param defaultValue Its default.
        /// @return This builder, with the field unless the mode leaves it out.
        Builder put(final String key, final Object value, final Object defaultValue) {
            if (value == null && mode != ALL) {
                return this;
            }
            if (mode == COMPACT && Objects.equals(value, defaultValue)) {
                return this;
            }
            map.put(key, value);
            return this;
        }

        /// @param key          A field holding a model.
        /// @param value        The model, or null.
        /// @param defaultValue Its default.
        /// @param dump         How the model is written.
        /// @param <T>          The model.
        /// @return This builder, with the field unless the mode leaves it out.
        <T> Builder model(final String key, final T value, final T defaultValue, final Function<T, Object> dump) {
            if (value == null) {
                return put(key, null, defaultValue);
            }
            if (mode == COMPACT && value.equals(defaultValue)) {
                return this;
            }
            map.put(key, dump.apply(value));
            return this;
        }

        /// @param key    A field holding a list of models.
        /// @param values The models.
        /// @param dump   How each is written.
        /// @param <T>    The model.
        /// @return This builder, with the field unless the mode leaves an empty list out.
        <T> Builder models(final String key, final List<T> values, final Function<T, Object> dump) {
            if (mode == COMPACT && values.isEmpty()) {
                return this;
            }
            map.put(key, values.stream().map(dump).toList());
            return this;
        }

        /// @return The model's JSON.
        Map<String, Object> build() {
            return map;
        }
    }
}
