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

package stroom.index.impl;

import java.util.Arrays;

/// How far the documents a run of a task wrote to an index shard have got.
public enum IndexShardTaskState {
    /// The run is still adding documents to the shard.
    WRITING((byte) 1),
    /// The run has ended, and the shard has not committed since.
    WRITTEN((byte) 2),
    /// The shard has committed everything the run gave it.
    COMMITTED((byte) 3);

    private final byte primitiveValue;

    IndexShardTaskState(final byte primitiveValue) {
        this.primitiveValue = primitiveValue;
    }

    /// Gets the value held in the database.
    ///
    /// @return The value.
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    /// Gets the state that a value held in the database stands for.
    ///
    /// @param primitiveValue The value.
    /// @return The state.
    /// @throws IllegalArgumentException If the value stands for no state.
    public static IndexShardTaskState fromPrimitiveValue(final byte primitiveValue) {
        return Arrays.stream(values())
                .filter(state -> state.primitiveValue == primitiveValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown index shard task state " + primitiveValue));
    }
}
