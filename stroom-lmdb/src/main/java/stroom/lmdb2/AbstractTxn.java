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

package stroom.lmdb2;

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public abstract class AbstractTxn implements AutoCloseable {

    final Env<ByteBuffer> env;
    final LmdbErrorHandler lmdbErrorHandler;
    private final Thread thread;

    AbstractTxn(final Env<ByteBuffer> env, final LmdbErrorHandler lmdbErrorHandler) {
        this.env = env;
        this.lmdbErrorHandler = lmdbErrorHandler;
        this.thread = Thread.currentThread();
    }

    void check() {
        if (thread != Thread.currentThread()) {
            throw new RuntimeException("Unexpected thread used. This will break LMDB.");
        }
        if (env.isClosed()) {
            throw new RuntimeException("Environment is closed. This will break LMDB.");
        }
    }

    abstract Txn<ByteBuffer> get();
}
