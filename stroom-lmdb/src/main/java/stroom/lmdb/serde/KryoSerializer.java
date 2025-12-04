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

package stroom.lmdb.serde;

import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * A simpler interface than that presented by Kryo for where we know exactly what we are expecting to (de)ser.
 * Saves the cost of instantiating a {@link com.esotericsoftware.kryo.Kryo} object.
 */
public interface KryoSerializer<T> {

    T read(final Input input);

    void write(final Output output, final T object);

    /**
     * Adapter to allow the use of {@link Serializer} objects unless they make use of kryo or type arguments
     */
    static <T> KryoSerializer<T> fromSerializer(final Serializer<T> serializer) {
        return new KryoSerializer<T>() {
            @Override
            public T read(final Input input) {
                return serializer.read(null, input, null);
            }

            @Override
            public void write(final Output output, final T object) {
                serializer.write(null, output, object);
            }
        };
    }
}
