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

import stroom.lmdb.stream.LmdbKeyRange;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.ref.StoredValues;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface LmdbRowKeyFactory {

    /**
     * Create an LMDB row key.
     *
     * @param depth        The nesting depth of the key.
     * @param parentRowKey The buffer for the parent row key.
     * @param storedValues The stored values.
     * @return A new LMDB row key.
     */
    ByteBuffer create(int depth,
                      ByteBuffer parentRowKey,
                      StoredValues storedValues);

    /**
     * Change a specific part of the supplied keys byte buffer to ensure it is unique if necessary.
     *
     * @param lmdbKV The row to change.
     * @return A row key that has been altered to make it unique if necessary.
     */
    LmdbKV makeUnique(LmdbKV lmdbKV);

    /**
     * Determine if we are grouped at the supplied depth.
     *
     * @param depth The depth.
     * @return True if the supplied depth is grouped.
     */
    boolean isGroup(int depth);

    /**
     * Get the depth for the supplied key.
     *
     * @param lmdbKV The row to test.
     * @return The depth of the supplied key.
     */
    int getDepth(LmdbKV lmdbKV);

    /**
     * Create a key range to filter rows to find the children of the supplied parent key.
     *
     * @param parentKey The parent key to create the child key range for.
     * @param consumer  A consumer that will receive the child key.
     */
    void createChildKeyRange(Key parentKey, Consumer<LmdbKeyRange> consumer);

    /**
     * Create a key range to filter rows to find the children of the supplied parent key that also filters by time.
     *
     * @param parentKey  The parent key to create the child key range for.
     * @param timeFilter The time filter to apply to the key range.
     * @param consumer   A consumer that will receive the child key.
     */
    void createChildKeyRange(Key parentKey, TimeFilter timeFilter, Consumer<LmdbKeyRange> consumer);

    Key createKey(Key parentKey, StoredValues storedValues, ByteBuffer keyBuffer);
}
