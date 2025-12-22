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

import stroom.query.language.functions.ref.DataReader;
import stroom.query.language.functions.ref.DataWriter;
import stroom.query.language.functions.ref.ErrorConsumer;

import java.util.Set;

public interface KeyFactory extends UniqueIdProvider {

    /**
     * Write a key to an output.
     *
     * @param key The key to serialise.
     */
    void write(Key key, DataWriter writer);

    /**
     * Read a key from an input.
     *
     * @param input The input to read the key from.
     * @return The key read from the input.
     */
    Key read(DataReader reader);

    /**
     * Decode a set of string encoded key bytes into a set of keys.
     *
     * @param openGroups The set of encoded key bytes to turn into a set of keys.
     * @return A decoded set of keys.
     */
    Set<Key> decodeSet(Set<String> openGroups);

    /**
     * Encode a key into an encoded string from the key bytes.
     *
     * @param key           The key to encode.
     * @param errorConsumer The error consumer to consume all errors that occur during the conversion.
     * @return The bytes of a key encoded into a string.
     */
    String encode(Key key, ErrorConsumer errorConsumer);
}
