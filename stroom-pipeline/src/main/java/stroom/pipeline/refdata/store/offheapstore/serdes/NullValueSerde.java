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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;

public class NullValueSerde implements RefDataValueSerde {

    NullValueSerde() {
    }

    @Override
    public RefDataValue deserialize(final ByteBuffer byteBuffer) {
        // NullValue serialises to nothing, so as long as we are expecting a NullValue then
        // just return it
        if (byteBuffer.hasRemaining()) {
            throw new RuntimeException(LogUtil.message("Expecting byteBuffer to not have anything remaining, {}",
                    ByteBufferUtils.byteBufferInfo(byteBuffer)));
        }
        return NullValue.getInstance();
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {
        // NullValue serialises to nothing so just flip the buffer to set the limit
        byteBuffer.flip();
    }

    @Override
    public ByteBuffer serialize(final PooledByteBufferOutputStream pooledByteBufferOutputStream,
                                final RefDataValue refDataValue) {
        // NullValue serialises to nothing. Getting the pooled buffer will flip it to
        // set its limit
        return pooledByteBufferOutputStream.getByteBuffer();
    }
}
