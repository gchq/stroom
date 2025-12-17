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

package stroom.lmdb;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

/**
 * Intended for passing behaviour into methods that are working on LMDB database entries.
 */
@FunctionalInterface
public interface EntryConsumer {

    /**
     * Accept the key/value pair of buffers associated with the passed transaction. The buffers should
     * not be mutated or used outside the scope of the accept method.
     *
     * @param txn The current transaction
     */
    void accept(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer, final ByteBuffer valueBuffer);

    static EntryConsumer doNothingConsumer() {
        return (txn, keyBuffer, valueBuffer) -> {
        };
    }
}
