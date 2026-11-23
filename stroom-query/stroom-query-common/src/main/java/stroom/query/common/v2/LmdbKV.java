/*
 * Copyright 2021 Crown Copyright
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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb2.KV;

import java.nio.ByteBuffer;

public class LmdbKV extends KV<ByteBuffer, ByteBuffer> implements LmdbQueueItem {

    private final CurrentDbState currentDbState;
    private final boolean windowKnown;
    private final Long realWindowKey;

    public LmdbKV(final CurrentDbState currentDbState,
                  final ByteBuffer rowKey,
                  final ByteBuffer rowValue) {
        super(rowKey, rowValue);
        this.currentDbState = currentDbState;
        this.windowKnown = false;
        this.realWindowKey = null;
    }

    /// Creates a row for which it is known whether it is a real row of a time window.
    ///
    /// @param currentDbState The event that the row is from.
    /// @param rowKey         The key of the row.
    /// @param rowValue       The value of the row.
    /// @param realWindowKey  The key of the time window, if this is a top level row of a table with
    ///                       time windows and is of an event in the window. It is `null` if the row
    ///                       is a copy of a row from another window, is below the top level, or the
    ///                       table has no windows.
    public LmdbKV(final CurrentDbState currentDbState,
                  final ByteBuffer rowKey,
                  final ByteBuffer rowValue,
                  final Long realWindowKey) {
        super(rowKey, rowValue);
        this.currentDbState = currentDbState;
        this.windowKnown = true;
        this.realWindowKey = realWindowKey;
    }

    /// Tells whether [#getRealWindowKey()] can be relied on. It can't for a row whose windows are
    /// not noted in some other way, as those of a row from another node are by the span that comes
    /// with it.
    ///
    /// @return True if it is known whether this is a real row of a time window.
    public boolean isWindowKnown() {
        return windowKnown;
    }

    /// Gets the key of the time window that this is a real top level row of.
    ///
    /// @return The key, or `null` if this is not such a row or [#isWindowKnown()] is false.
    public Long getRealWindowKey() {
        return realWindowKey;
    }

    public CurrentDbState getCurrentDbState() {
        return currentDbState;
    }

    @Override
    public String toString() {
        return "LmdbKV{" +
               "currentDbState=" + currentDbState +
               ", rowKey=" + ByteBufferUtils.byteBufferInfo(key()) +
               ", rowValue=" + ByteBufferUtils.byteBufferInfo(val()) +
               '}';
    }
}
