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

import stroom.planb.impl.serde.Serde;
import stroom.planb.impl.serde.time.TimeSerde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

public class InstantSerde implements Serde<Instant> {

    final TimeSerde timeSerde;
    private final ByteBuffer reusableWriteBuffer;

    public InstantSerde(final TimeSerde timeSerde) {
        this.timeSerde = timeSerde;
        reusableWriteBuffer = ByteBuffer.allocateDirect(timeSerde.getSize());
    }

    @Override
    public final Instant read(final Txn<ByteBuffer> txn,
                              final ByteBuffer byteBuffer) {
        return this.timeSerde.read(byteBuffer);
    }

    @Override
    public final void write(final Txn<ByteBuffer> txn, final Instant value, final Consumer<ByteBuffer> consumer) {
        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        timeSerde.write(reusableWriteBuffer, value);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }
}
