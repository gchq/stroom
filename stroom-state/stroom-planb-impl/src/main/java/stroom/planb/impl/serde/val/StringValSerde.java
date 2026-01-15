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

package stroom.planb.impl.serde.val;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class StringValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public StringValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        return ValString.create(ByteBufferUtils.toString(byteBuffer));
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        byteBuffers.use(bytes.length, byteBuffer -> {
            byteBuffer.put(bytes);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
