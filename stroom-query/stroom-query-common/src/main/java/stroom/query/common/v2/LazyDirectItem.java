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

package stroom.query.common.v2;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;

import java.util.Objects;

public class LazyDirectItem implements Item {

    private final Item item;
    private final Val[] values;
    private boolean arrayCreated;

    public LazyDirectItem(final int[] columnIndexMapping,
                          final Item item) {
        this.values = new Val[columnIndexMapping.length];
        this.item = item;
    }

    @Override
    public Key getKey() {
        return item.getKey();
    }

    @Override
    public Val getValue(final int index) {
        Val val = values[index];
        if (val == null) {
            val = Objects.requireNonNullElse(item.getValue(index), ValNull.INSTANCE);
            values[index] = val;
        }
        return val;
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public Val[] toArray() {
        // Force array population.
        if (!arrayCreated) {
            for (int i = 0; i < values.length; i++) {
                getValue(i);
            }
            arrayCreated = true;
        }

        return values;
    }
}
