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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.SimpleByteBufferFactory;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestLmdbRowValueFactory {

    private static final int LARGE = 100_000;

    private final ByteBufferFactory byteBufferFactory = new SimpleByteBufferFactory();
    private final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
    private final LmdbRowValueFactory lmdbRowValueFactory = new LmdbRowValueFactory(
            byteBufferFactory,
            valueReferenceIndex,
            new DataWriterFactory(new ErrorConsumerImpl(), 1000));

    // Many new rows can wait to be stored at once, so one large merged row must not make them all large.
    @Test
    void testLargeMergedRowDoesNotMakeNewRowsLarge() {
        valueReferenceIndex.addCount("count");
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();

        final ByteBuffer before = lmdbRowValueFactory.create(storedValues);
        final ByteBuffer merged = lmdbRowValueFactory.useOutput(10, output -> output.write(new byte[LARGE]));
        assertThat(merged.remaining()).isEqualTo(LARGE);

        final ByteBuffer after = lmdbRowValueFactory.create(storedValues);
        assertThat(after.capacity()).isEqualTo(before.capacity()).isLessThan(1000);
        assertThat(after).isEqualTo(before);
        byteBufferFactory.release(before);
        byteBufferFactory.release(merged);
        byteBufferFactory.release(after);
    }

    @Test
    void testExpectedSizeIsUsed() {
        final ByteBuffer merged = lmdbRowValueFactory.useOutput(LARGE, output -> output.write(new byte[LARGE]));
        assertThat(merged.remaining()).isEqualTo(LARGE);
        assertThat(merged.capacity()).isEqualTo(LARGE);
        byteBufferFactory.release(merged);
    }
}
