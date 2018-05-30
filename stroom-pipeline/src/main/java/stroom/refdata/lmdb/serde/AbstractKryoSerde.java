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

package stroom.refdata.lmdb.serde;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public abstract class AbstractKryoSerde<T> implements
        Serde<T>,
        Serializer<T>,
        Deserializer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKryoSerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(AbstractKryoSerde.class);

    public T deserialize(final KryoPool kryoPool, final ByteBuffer byteBuffer) {
        return kryoPool.run(kryo -> {
            ByteBufferInputStream stream = new ByteBufferInputStream(byteBuffer);
            Input input = new Input(stream);

            Object object = null;
            try {
                object = kryo.readClassAndObject(input);
            } catch (Exception e) {
                throw new RuntimeException(LambdaLogger.buildMessage("Error de-serialising bytebuffer in {}",
                        this.getClass().getCanonicalName()), e);
            }
            byteBuffer.flip();
            try {
                T castObject = (T) object;
                return castObject;
            } catch (ClassCastException e) {
                throw new RuntimeException(LambdaLogger.buildMessage("Unable to cast de-serialised object in {}",
                        this.getClass().getCanonicalName()), e);
            }
        });
    }

    public abstract T deserialize(final ByteBuffer byteBuffer);

    public void serialize(final KryoPool kryoPool, final ByteBuffer byteBuffer, final T object) {
        kryoPool.run(kryo -> {
            // TODO how do we know how big the serialized form will be
            ByteBufferOutputStream stream = new ByteBufferOutputStream(byteBuffer);
            Output output = new Output(stream);
            kryo.writeClassAndObject(output, object);
            output.close();
            //TODO how do we ensure bb has enough remaining length when passed in
            byteBuffer.flip();
            return null;
        });
    }

    public ByteBuffer serialize(final KryoPool kryoPool, int bufferCapacity, final T object) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferCapacity);
        serialize(kryoPool, byteBuffer, object);
        return byteBuffer;
    }

    @Override
    public abstract void serialize(final ByteBuffer byteBuffer, final T object);
}
