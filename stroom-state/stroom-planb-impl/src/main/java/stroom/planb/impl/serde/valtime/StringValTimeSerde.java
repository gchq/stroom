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

package stroom.planb.impl.serde.valtime;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

public class StringValTimeSerde implements ValTimeSerde {

    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public StringValTimeSerde(final ByteBuffers byteBuffers,
                              final TimeSerde timeSerde) {
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
    }

    @Override
    public ValTime read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valSlice = byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
        final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final Val val = ValString.create(ByteBufferUtils.toString(valSlice));
        final Instant insertTime = timeSerde.read(timeSlice);
        return new ValTime(val, insertTime);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final ValTime value, final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = ValSerdeUtil.getBytes(value.val());
        byteBuffers.use(bytes.length + timeSerde.getSize(), byteBuffer -> {
            byteBuffer.put(bytes);
            timeSerde.write(byteBuffer, value.insertTime());
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
