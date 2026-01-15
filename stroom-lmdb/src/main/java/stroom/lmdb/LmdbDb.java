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
import java.util.Map;
import java.util.function.Consumer;

public interface LmdbDb {

    String getDbName();

    Map<String, String> getDbInfo();

    long getEntryCount();

    long getEntryCount(Txn<ByteBuffer> txn);

    void logDatabaseContents(Txn<ByteBuffer> txn);

    void logDatabaseContents(Txn<ByteBuffer> txn, Consumer<String> logEntryConsumer);

    void logDatabaseContents();

    void logDatabaseContents(Consumer<String> logEntryConsumer);

    void logRawDatabaseContents();

    void logRawDatabaseContents(Txn<ByteBuffer> txn);

    void logRawDatabaseContents(Txn<ByteBuffer> txn, Consumer<String> logEntryConsumer);

    void logRawDatabaseContents(Consumer<String> logEntryConsumer);
}
