/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.store.offheapstore.lmdb.serde;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public abstract class AbstractKryoSerde<T> implements Serde<T>, KryoSerializer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKryoSerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(AbstractKryoSerde.class);

    public static final int VARIABLE_LENGTH_LONG_BYTES = 9;
    public static final int BOOLEAN_BYTES = 1;

    @Override
    public T deserialize(final ByteBuffer byteBuffer) {
       try (final Input input = new Input(new ByteBufferInputStream(byteBuffer))) {
           T object = read(input);
           byteBuffer.flip();
           return object;
       }
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final T object) {
        try (final Output output = new Output(new ByteBufferOutputStream(byteBuffer))) {
            write(output, object);
            output.flush();
            byteBuffer.flip();
        }
    }
}
