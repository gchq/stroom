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

public class ReadTxn extends AbstractTxn {

    private Txn<ByteBuffer> txn;

    ReadTxn(final Env<ByteBuffer> env, final LmdbErrorHandler lmdbErrorHandler) {
        super(env, lmdbErrorHandler);
    }

    @Override
    public synchronized Txn<ByteBuffer> get() {
        check();
        try {
            if (txn == null) {
                txn = env.txnRead();
            }
            return txn;
        } catch (final RuntimeException e) {
            lmdbErrorHandler.error(e);
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        if (txn != null) {
            check();
            try {
                txn.close();
            } catch (final RuntimeException e) {
                lmdbErrorHandler.error(e);
                throw e;
            } finally {
                txn = null;
            }
        }
    }
}
