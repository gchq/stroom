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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb2.KV;

import java.nio.ByteBuffer;

public class LmdbKV extends KV<ByteBuffer, ByteBuffer> implements LmdbQueueItem {

    private final CurrentDbState currentDbState;

    public LmdbKV(final CurrentDbState currentDbState,
                  final ByteBuffer rowKey,
                  final ByteBuffer rowValue) {
        super(rowKey, rowValue);
        this.currentDbState = currentDbState;
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
