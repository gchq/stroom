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

package stroom.planb.impl.db;

import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.PlanBEnv.Usage;
import stroom.planb.shared.ArchivalGranularity;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;

public interface Db<K, V> extends AutoCloseable {

    int MAX_KEY_LENGTH = 511;

    void insert(LmdbWriter writer,
                KV<K, V> kv);

    V get(K key);

    void search(ExpressionCriteria criteria,
                FieldIndex fieldIndex,
                DateTimeSettings dateTimeSettings,
                ExpressionPredicateFactory expressionPredicateFactory,
                ValuesConsumer consumer);

    void merge(Path source);

    default void mergeComplete() {
    }

    long runRetention(Instant deleteBefore,
                       boolean useStateTime);

    default long runArchival(final Instant archiveBefore,
                                final ArchivalGranularity granularity,
                                final Path archiveBaseDir) {
        return 0L;
    }

    long condense(Instant condenseBefore);

    void compact(Path destination);

    /**
     * How much of the store's fixed-size LMDB map is allocated. Callers use this to avoid starting
     * work that cannot complete, because a full map fails the write rather than growing.
     */
    Usage getUsage();

    LmdbWriter createWriter();

    void write(Consumer<LmdbWriter> consumer);

    /**
     * Runs one operation against a writer obtained from {@link #createWriter()} and held across many
     * operations, aborting it on failure. Callers that hold a writer must route every write through
     * here, because {@link #write(Consumer)}'s failure handling only covers the writer it owns.
     */
    void writeWith(LmdbWriter writer, Runnable operation);

    void lock(Runnable runnable);

    void close();

    long count();

    String getInfoString();
}
