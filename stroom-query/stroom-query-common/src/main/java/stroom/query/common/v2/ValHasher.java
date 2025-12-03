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

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValSerialiser;
import stroom.query.language.functions.ref.KryoDataWriter;

import com.esotericsoftware.kryo.io.Output;
import net.openhft.hashing.LongHashFunction;

public class ValHasher {

    private final DataWriterFactory writerFactory;
    private int bufferSize = 16;

    public ValHasher(final DataWriterFactory writerFactory) {
        this.writerFactory = writerFactory;
    }

    public long hash(final Val[] values) {
        if (values == null) {
            return -1;
        } else if (values.length == 0) {
            return 0;
        }

        final byte[] bytes;

        try (final Output output = new Output(bufferSize, -1)) {
            try (final KryoDataWriter writer = writerFactory.create(output)) {
                ValSerialiser.writeArray(writer, values);
            }
            bytes = output.toBytes();
        }
        bufferSize = Math.max(bufferSize, bytes.length);
        return LongHashFunction.xx3().hashBytes(bytes);
    }
}
