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

package stroom.planb.impl.db.trace;

import stroom.bytebuffer.impl6.ByteBuffers;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class TraceRootKeySerde {

    protected final ByteBuffers byteBuffers;

    public TraceRootKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    public void write(final TraceRootKey key,
                      final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = key.getTraceId();
        byteBuffers.useBytes(bytes, consumer);
    }

    public TraceRootKey read(final ByteBuffer byteBuffer) {
        final byte[] bytes = new byte[16];
        byteBuffer.get(bytes);
        return new TraceRootKey(bytes);
    }
}
