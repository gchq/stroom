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

package stroom.cell.tickbox.shared;


import java.util.Objects;
import java.util.function.Function;

public enum TickBoxState {
    TICK,
    HALF_TICK,
    UNTICK,
    ;

    public static TickBoxState fromBoolean(final Boolean state) {
        if (state == null) {
            return null;
        }

        if (state) {
            return TICK;
        }

        return UNTICK;
    }

    public Boolean toBoolean() {
        return this.equals(TICK);
    }

    public static <T> Function<T, TickBoxState> createTickBoxFunc(
            final Function<T, Boolean> booleanExtractor) {

        return row -> {
            final Boolean bool = Objects.requireNonNull(booleanExtractor).apply(row);
            return TickBoxState.fromBoolean(bool);
        };
    }
}
