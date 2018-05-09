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

package stroom.refdata.lmdb.eval;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.objenesis.strategy.StdInstantiatorStrategy;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.xml.event.np.NPAttributes;
import stroom.xml.event.np.NPEventList;
import stroom.xml.event.np.NPEventListNamePool;

import java.io.ByteArrayOutputStream;

public class NPEventListSerde {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(NPEventListSerde.class);

    private static final KryoFactory factory = () -> {
        Kryo kryo = new Kryo();
        try {
            LAMBDA_LOGGER.debug(() ->
                    LambdaLogger.buildMessage("Initialising Kryo instance for NPEventList on thread {}",
                            Thread.currentThread().getName()));

            //register all the classes used within NPEventList
            kryo.register(NPAttributes.class);
            kryo.register(NPEventList.class);
            kryo.register(NPEventListNamePool.NameEntry.class);
            kryo.register(NPEventListNamePool.NameEntry[].class);
            kryo.register(NPEventListNamePool.class);
            kryo.register(String[].class);
            kryo.register(byte[].class);
            kryo.register(char[].class);
            kryo.register(int.class);
            kryo.register(int[].class);
            kryo.register(short[].class);
            kryo.register(short[][].class);

            ((Kryo.DefaultInstantiatorStrategy) kryo.getInstantiatorStrategy()).setFallbackInstantiatorStrategy(
                    new StdInstantiatorStrategy());
            kryo.setRegistrationRequired(true);
        } catch (RuntimeException e) {
            LAMBDA_LOGGER.error(() -> "Exception occurred configuring kryo instance", e);
        }
        return kryo;
    };

    private static final KryoPool pool = new KryoPool.Builder(factory)
            .softReferences()
            .build();

    private NPEventListSerde() {
    }

    public static NPEventListSerde instance() {
        return new NPEventListSerde();
    }


    /**
     * @param npEventList typed data
     * @return serialized bytes
     */
    public byte[] serialize(NPEventList npEventList) {
        return pool.run(kryo -> {
//            Deflater deflater = new Deflater(9);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
//            DeflaterOutputStream deflatedStream = new DeflaterOutputStream(stream, deflater);
//            Output output = new Output(deflatedStream);
            Output output = new Output(stream);
            kryo.writeClassAndObject(output, npEventList);
            output.close();
            return stream.toByteArray();
        });
    }

    /**
     * Deserialize a record value from a bytearray into a value or object.
     *
     * @param bytes serialized bytes; may be null; implementations are recommended to handle null by returning
     *              a value or null rather than throwing an exception.
     * @return deserialized typed data; may be null
     */
    public NPEventList deserialize(byte[] bytes) {
        return pool.run(kryo -> {
//            Inflater inflater = new Inflater();
//            InflaterInputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(bytes), inflater);
//            Input input = new Input(inflaterInputStream);
            Input input = new Input(bytes);
            return (NPEventList) kryo.readClassAndObject(input);
        });
    }
}
