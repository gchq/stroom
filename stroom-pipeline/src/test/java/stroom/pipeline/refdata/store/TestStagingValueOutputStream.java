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

package stroom.pipeline.refdata.store;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.bytebuffer.PooledByteBufferOutputStream.Factory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TestStagingValueOutputStream {

    private ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
    private PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory = new Factory() {
        @Override
        public PooledByteBufferOutputStream create(final int initialCapacity) {
            return new PooledByteBufferOutputStream(byteBufferPool, initialCapacity);
        }
    };
    private ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();
    private StagingValueOutputStream stagingValueOutputStream = new StagingValueOutputStream(
            valueStoreHashAlgorithm, pooledByteBufferOutputStreamFactory);

    @Test
    void testGetFullBytes() throws IOException {
        final String value = "foo";
        final ByteBuffer valueBuff = ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
        stagingValueOutputStream.clear();
        stagingValueOutputStream.write(value);
        stagingValueOutputStream.setTypeId(StringValue.TYPE_ID);

        final ByteBuffer fullByteBuffer = stagingValueOutputStream.getFullByteBuffer();

        assertThat(fullByteBuffer.position())
                .isZero();
        assertThat(fullByteBuffer.remaining())
                .isEqualTo(StagingValueOutputStream.META_LENGTH + valueBuff.remaining());
        assertThat(stagingValueOutputStream.getTypeId())
                .isEqualTo(StringValue.TYPE_ID);
        assertThat(stagingValueOutputStream.getValueHashCode())
                .isEqualTo(valueStoreHashAlgorithm.hash(valueBuff));
        assertThat(stagingValueOutputStream.getValueBuffer())
                .isEqualTo(valueBuff);
    }

    @Test
    void testClear() throws IOException {
        // populate the stagingValueOutputStream
        testGetFullBytes();

        stagingValueOutputStream.clear();

        final String msgPart = "typeId has not been set";

        Assertions.assertThatThrownBy(
                        () -> {
                            stagingValueOutputStream.getTypeId();
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(msgPart);

        Assertions.assertThatThrownBy(
                        () -> {
                            stagingValueOutputStream.getFullByteBuffer();
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(msgPart);

        Assertions.assertThatThrownBy(
                        () -> {
                            stagingValueOutputStream.getValueBuffer();
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(msgPart);

        Assertions.assertThatThrownBy(
                        () -> {
                            stagingValueOutputStream.getValueHashCode();
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(msgPart);
    }

    @Test
    void testIsNullValue_true() {
        stagingValueOutputStream.clear();
        stagingValueOutputStream.setTypeId(NullValue.TYPE_ID);
        assertThat(stagingValueOutputStream.isNullValue())
                .isTrue();

        assertThat(stagingValueOutputStream.getValueHashCode())
                .isEqualTo(valueStoreHashAlgorithm.hash(ByteBuffer.wrap(new byte[0])));
    }

    @Test
    void testIsNullValue_true_emptyString() {
        stagingValueOutputStream.clear();
        stagingValueOutputStream.setTypeId(StringValue.TYPE_ID);
        assertThat(stagingValueOutputStream.isNullValue())
                .isTrue();
    }

    @Test
    void testIsNullValue_false() throws IOException {
        stagingValueOutputStream.clear();
        stagingValueOutputStream.write("foo");
        stagingValueOutputStream.setTypeId(StringValue.TYPE_ID);
        assertThat(stagingValueOutputStream.isNullValue())
                .isFalse();
    }
}
