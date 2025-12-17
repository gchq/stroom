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
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class FastInfoSetValueSerde implements RefDataValueSerde {

    @Override
    public RefDataValue deserialize(final ByteBuffer byteBuffer) {

        // just wrap the buffer, let the caller clone if required.
        return new FastInfosetValue(byteBuffer);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {
        try {
            // copy our buffer into the other buffer, flipping the dest buffer in the process
            ByteBufferUtils.copy(((FastInfosetValue) refDataValue).getByteBuffer(), byteBuffer);

        } catch (final ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Unable to cast {} to {}",
                    refDataValue.getClass().getCanonicalName(),
                    FastInfosetValue.class.getCanonicalName()),
                    e);
        }
    }

    @Override
    public ByteBuffer serialize(final Supplier<ByteBuffer> byteBufferSupplier,
                                final RefDataValue refDataValue) {

        try {
            // the FastInfosetValue just wraps a ByteBuffer so just return that, no
            // serialisation to do.
            return ((FastInfosetValue) refDataValue).getByteBuffer();
        } catch (final ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Unable to cast {} to {}",
                    refDataValue.getClass().getCanonicalName(),
                    FastInfosetValue.class.getCanonicalName()),
                    e);
        }
    }

    @Override
    public ByteBuffer serialize(final PooledByteBufferOutputStream pooledByteBufferOutputStream,
                                final RefDataValue refDataValue) {
        try {
            // the FastInfosetValue just wraps a ByteBuffer so just return that, no
            // serialisation to do.
            return ((FastInfosetValue) refDataValue).getByteBuffer();
        } catch (final ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Unable to cast {} to {}",
                    refDataValue.getClass().getCanonicalName(),
                    FastInfosetValue.class.getCanonicalName()),
                    e);
        }
    }
}
