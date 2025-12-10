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

import stroom.planb.impl.serde.time.TimeSerde;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

public abstract class SimpleValTimeSerde implements ValTimeSerde {

    final TimeSerde timeSerde;
    private final ByteBuffer reusableWriteBuffer;

    public SimpleValTimeSerde(final TimeSerde timeSerde) {
        this.timeSerde = timeSerde;
        final int totalSize = size() + timeSerde.getSize();
        reusableWriteBuffer = ByteBuffer.allocateDirect(totalSize);
    }

    @Override
    public final ValTime read(final Txn<ByteBuffer> txn,
                              final ByteBuffer byteBuffer) {
        final Val val = readVal(byteBuffer);
        final Instant insertTime = this.timeSerde.read(byteBuffer);
        return new ValTime(val, insertTime);
    }

    @Override
    public final void write(final Txn<ByteBuffer> txn, final ValTime value, final Consumer<ByteBuffer> consumer) {
        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        writeVal(reusableWriteBuffer, value.val());
        timeSerde.write(reusableWriteBuffer, value.insertTime());
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    abstract Val readVal(ByteBuffer byteBuffer);

    abstract void writeVal(ByteBuffer byteBuffer, Val val);

    abstract int size();
}
