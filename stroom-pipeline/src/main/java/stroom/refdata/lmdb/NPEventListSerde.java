/*
 * Copyright 2017 Crown Copyright
 *
 * This file is part of Stroom-Stats.
 *
 * Stroom-Stats is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Stroom-Stats is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Stroom-Stats.  If not, see <http://www.gnu.org/licenses/>.
 */

package stroom.refdata.lmdb;

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
            kryo.register(byte[].class);
            kryo.register(char[].class);
            kryo.register(int.class);
            kryo.register(int[].class);
            kryo.register(short[].class);
            kryo.register(short[][].class);
            kryo.register(String[].class);

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
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
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
            Input input = new Input(bytes);
            return (NPEventList) kryo.readClassAndObject(input);
        });
    }
}
