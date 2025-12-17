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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbIterable.EntryConsumer;
import stroom.planb.impl.db.PlanBEnv.EnvInf;
import stroom.planb.shared.PlanBDoc;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasPrimitiveValue;

import org.lmdbjava.CopyFlags;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

public abstract class AbstractDb<K, V> implements Db<K, V> {

    protected static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDb.class);

    private static final String NAME = "db";
    private static final String INFO_NAME = "info_db";

    protected final PutFlags[] putFlags;

    protected final PlanBEnv env;
    protected final ByteBuffers byteBuffers;
    protected final PlanBDoc doc;
    protected final Dbi<ByteBuffer> dbi;
    protected final Dbi<ByteBuffer> infoDbi;
    protected final SchemaInfo schemaInfo;

    public AbstractDb(final PlanBEnv env,
                      final ByteBuffers byteBuffers,
                      final PlanBDoc doc,
                      final Boolean overwrite,
                      final HashClashCommitRunnable hashClashCommitRunnable,
                      final SchemaInfo schema) {
        this.env = env;
        this.byteBuffers = byteBuffers;
        this.doc = doc;

        dbi = env.openDbi(NAME, DbiFlags.MDB_CREATE);

        // Read and validate that the schema is as expected.
        infoDbi = env.openDbi(INFO_NAME, DbiFlags.MDB_CREATE);
        this.schemaInfo = env.read(txn -> {
            final Optional<SchemaInfo> optionalSchemaInfo = readSchema(txn);
            // Validate schema.
            return optionalSchemaInfo
                    .map(actual -> {
                        validateSchema(schema, actual);
                        return actual;
                    })
                    .orElse(schema);
        });

        if (!env.isReadOnly()) {
            env.write(writer -> {
                // Write schema.
                writeSchema(writer, schema);

                // Read and set the hash clash count.
                hashClashCommitRunnable.setHashClashes(readHashClashes(writer.getWriteTxn()));
            });
            hashClashCommitRunnable.setRunnable(txn ->
                    writeHashClashes(txn, hashClashCommitRunnable.getHashClashes()));
        }

        this.putFlags = overwrite
                ? new PutFlags[]{}
                : new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
    }

    protected void validateSchema(final SchemaInfo expected,
                                  final SchemaInfo actual) {
        // Validate schema version.
        if (!Objects.equals(expected.getSchemaVersion(), actual.getSchemaVersion())) {
            throw new RuntimeException(LogUtil.message("Schema version mismatch for '{}': expected={}, actual={}",
                    doc.getName(),
                    expected.getSchemaVersion(),
                    actual.getSchemaVersion()));
        }

        // Validate key schema.
        if (!Objects.equals(expected.getKeySchema(), actual.getKeySchema())) {
            throw new RuntimeException(LogUtil.message("Key schema mismatch for '{}': expected={}, actual={}",
                    doc.getName(),
                    expected.getKeySchema(),
                    actual.getKeySchema()));
        }

        // Validate value schema.
        if (!Objects.equals(expected.getValueSchema(), actual.getValueSchema())) {
            throw new RuntimeException(LogUtil.message("Value schema mismatch for '{}': expected={}, actual={}",
                    doc.getName(),
                    expected.getValueSchema(),
                    actual.getValueSchema()));
        }
    }

    @Override
    public final long count() {
        return env.read(readTxn -> dbi.stat(readTxn).entries);
    }

    protected final void iterate(final Txn<ByteBuffer> txn,
                                 final EntryConsumer consumer) {
        LmdbIterable.iterate(txn, dbi, consumer);
    }

    private Optional<SchemaInfo> readSchema(final Txn<ByteBuffer> txn) {
        final OptionalInt optionalSchemaVersion = readInfoInt(txn, InfoKey.SCHEMA_VERSION);
        if (optionalSchemaVersion.isEmpty()) {
            return Optional.empty();
        }
        final String keySchema = readInfoString(txn, InfoKey.KEY_SCHEMA).orElse(null);
        final String valueSchema = readInfoString(txn, InfoKey.VALUE_SCHEMA).orElse(null);
        final SchemaInfo schemaInfo = new SchemaInfo(optionalSchemaVersion.getAsInt(), keySchema, valueSchema);
        LOGGER.debug(() -> LogUtil.message("store={}, schemaInfo={}", doc.getName(), schemaInfo));
        return Optional.of(schemaInfo);
    }

    private OptionalInt readInfoInt(final Txn<ByteBuffer> txn,
                                    final InfoKey infoKey) {
        OptionalInt version = OptionalInt.empty();
        try {
            final ByteBuffer valueBuffer = infoDbi.get(txn, infoKey.getByteBuffer());
            if (valueBuffer != null) {
                final int v = valueBuffer.getInt();
                version = OptionalInt.of(v);
            }
        } catch (final Exception e) {
            debug(e);
        }
        return version;
    }

    private Optional<String> readInfoString(final Txn<ByteBuffer> txn,
                                            final InfoKey infoKey) {
        Optional<String> info = Optional.empty();
        try {
            final ByteBuffer valueBuffer = infoDbi.get(txn, infoKey.getByteBuffer());
            if (valueBuffer != null) {
                final String string = ByteBufferUtils.toString(valueBuffer);
                info = Optional.of(string);
            }
        } catch (final Exception e) {
            error(e);
        }
        return info;
    }

    private void writeSchema(final LmdbWriter writer, final SchemaInfo schemaInfo) {
        writeInfoInt(writer.getWriteTxn(), InfoKey.SCHEMA_VERSION, schemaInfo.getSchemaVersion());
        writeInfoString(writer.getWriteTxn(), InfoKey.KEY_SCHEMA, schemaInfo.getKeySchema());
        writeInfoString(writer.getWriteTxn(), InfoKey.VALUE_SCHEMA, schemaInfo.getValueSchema());
        writer.commit();
    }

    private void writeInfoInt(final Txn<ByteBuffer> txn,
                              final InfoKey infoKey,
                              final int schemaVersion) {
        byteBuffers.useInt(schemaVersion, byteBuffer -> {
            infoDbi.put(txn, infoKey.getByteBuffer(), byteBuffer);
        });
    }

    private void writeInfoString(final Txn<ByteBuffer> txn,
                                 final InfoKey infoKey,
                                 final String info) {
        if (info != null) {
            final byte[] bytes = info.getBytes(StandardCharsets.UTF_8);
            byteBuffers.useBytes(bytes, byteBuffer -> {
                infoDbi.put(txn, infoKey.getByteBuffer(), byteBuffer);
            });
        }
    }


    private int readHashClashes(final Txn<ByteBuffer> txn) {
        int hashClashes = -1;
        try {
            final ByteBuffer valueBuffer = infoDbi.get(txn, InfoKey.HASH_CLASHES.getByteBuffer());
            if (valueBuffer != null) {
                hashClashes = valueBuffer.getInt();
            }

        } catch (final Exception e) {
            debug(e);
        }
        return hashClashes;
    }

    private void writeHashClashes(final Txn<ByteBuffer> txn, final int schemaVersion) {
        byteBuffers.useInt(schemaVersion, byteBuffer -> {
            infoDbi.put(txn, InfoKey.HASH_CLASHES.getByteBuffer(), byteBuffer);
        });
    }

    private enum InfoKey implements HasPrimitiveValue {
        SCHEMA_VERSION(0),
        HASH_CLASHES(1),
        KEY_SCHEMA(2),
        VALUE_SCHEMA(3);

        private final byte primitiveValue;
        private final ByteBuffer byteBuffer;

        InfoKey(final int primitiveValue) {
            this.primitiveValue = (byte) primitiveValue;
            this.byteBuffer = ByteBuffer.allocateDirect(1);
            byteBuffer.put((byte) primitiveValue);
            byteBuffer.flip();
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }

        public ByteBuffer getByteBuffer() {
            return byteBuffer.duplicate();
        }
    }

    @Override
    public LmdbWriter createWriter() {
        return env.createWriter();
    }

    @Override
    public void write(final Consumer<LmdbWriter> consumer) {
        try (final LmdbWriter writer = createWriter()) {
            consumer.accept(writer);
        }
    }

    @Override
    public void compact(final Path destination) {
        env.copy(destination.toFile(), CopyFlags.MDB_CP_COMPACT);
    }

    @Override
    public void lock(final Runnable runnable) {
        env.lock(runnable);
    }

    @Override
    public void close() {
        env.close();
    }

    public final String getInfoString() {
        final Inf inf = getInfo();
        if (inf == null) {
            return null;
        }
        return JsonUtil.writeValueAsString(inf);
    }

    protected SchemaInfo getSchemaInfo() {
        return schemaInfo;
    }

    public final Inf getInfo() {
        try {
            return env.read(txn -> {
                try {
                    final EnvInf envInf = env.getInfo();
                    final Stat stat = dbi.stat(txn);
                    final DbInf dbInf = new DbInf("db", stat);
                    final int schemaVersion = schemaInfo.getSchemaVersion();

                    return new Inf(
                            envInf,
                            Collections.singletonList(dbInf),
                            env.isReadOnly(),
                            schemaVersion,
                            readHashClashes(txn));
                } catch (final Exception e) {
                    debug(e);
                }
                return null;
            });
        } catch (final Exception e) {
            debug(e);
        }
        return null;
    }

    protected void debug(final Exception e) {
        LOGGER.debug(LogUtil.message("store={}, message={}", doc.getName(), e.getMessage()), e);
    }

    protected void error(final Exception e) {
        LOGGER.debug(LogUtil.message("store={}, message={}", doc.getName(), e.getMessage()), e);
    }

    public record Inf(EnvInf env, List<DbInf> db, boolean readOnly, int schemaVersion, int hashClashes) {

    }

    public record DbInf(String name, Stat stat) {

    }
}
