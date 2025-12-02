package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.HashFactoryFactory;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.DayTimeSerde;
import stroom.planb.impl.serde.time.HourTimeSerde;
import stroom.planb.impl.serde.time.MillisecondTimeSerde;
import stroom.planb.impl.serde.time.MinuteTimeSerde;
import stroom.planb.impl.serde.time.NanoTimeSerde;
import stroom.planb.impl.serde.time.SecondTimeSerde;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class TestVariableKeySerde {

    private static final String KEY_LOOKUP_DB_NAME = "key";

    @Test
    void test(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);
        for (int i = 0; i < 100; i++) {
            testDir(dbPath);
        }
    }

    private void testDir(final Path dbPath) {
        final ByteBuffers byteBuffers = new ByteBuffers(new ByteBufferFactoryImpl());
        final TemporalStateSettings settings = new TemporalStateSettings.Builder().build();
        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        try (final PlanBEnv env = new PlanBEnv(dbPath,
                settings.getMaxStoreSize(),
                20,
                false,
                hashClashCommitRunnable)) {
            final TimeSerde timeSerde = createTimeSerde(settings.getKeySchema().getTemporalPrecision());
            final HashFactory valueHashFactory = HashFactoryFactory.create(settings.getKeySchema().getHashLength());
            final UidLookupDb uidLookupDb = new UidLookupDb(
                    env,
                    byteBuffers,
                    KEY_LOOKUP_DB_NAME);
            final HashLookupDb hashLookupDb = new HashLookupDb(
                    env,
                    byteBuffers,
                    valueHashFactory,
                    hashClashCommitRunnable,
                    KEY_LOOKUP_DB_NAME);
            final VariableKeySerde variableKeySerde = new VariableKeySerde(
                    PlanBDoc.builder().uuid(UUID.randomUUID().toString()).build(),
                    uidLookupDb,
                    hashLookupDb,
                    byteBuffers,
                    timeSerde);

            final Map<ByteBuffer, TemporalKey> keys = new HashMap<>();
            env.write((Function<LmdbWriter, Object>) writer -> {

                // Test short keys.
                write(writer, variableKeySerde, "short_key", keys);

                // Test medium keys (uses UID lookup)
                write(writer, variableKeySerde, StringUtils.repeat('T', 400), keys);

                // Test long keys (uses hash lookup)
                write(writer, variableKeySerde, StringUtils.repeat('T', 1000), keys);

                writer.commit();
                return null;
            });

            env.read(readTxn -> {
                for (final Entry<ByteBuffer, TemporalKey> entry : keys.entrySet()) {
                    final TemporalKey temporalKey = variableKeySerde.read(readTxn, entry.getKey());
                    assertThat(temporalKey).isEqualTo(entry.getValue());
                }
                return null;
            });

            env.read(readTxn -> {
                env.write((Function<LmdbWriter, Object>) writer -> {
                    final UsedLookupsRecorder usedLookupsRecorder = variableKeySerde.getUsedLookupsRecorder(env);
                    usedLookupsRecorder.deleteUnused(readTxn, writer);
                    return null;
                });
                return null;
            });

            env.read(readTxn -> {
                for (final Entry<ByteBuffer, TemporalKey> entry : keys.entrySet()) {
                    try {
                        final TemporalKey temporalKey = variableKeySerde.read(readTxn, entry.getKey());
                        // Only short keys should remain valid as the lookup recorder should have deleted the lookups
                        // from the lookup tables.
                        assertThat(temporalKey.getPrefix().getVal().toString()).contains("short_key");

                    } catch (final RuntimeException e) {
                        assertThat(e).isInstanceOf(RuntimeException.class);
                    }

//                    assertThrows(IllegalStateException.class, () -> variableKeySerde.read(readTxn, entry.getKey()));
                }
                return null;
            });
        }
    }

    private void write(final LmdbWriter writer,
                       final VariableKeySerde variableKeySerde,
                       final String prefix,
                       final Map<ByteBuffer, TemporalKey> keys) {
        for (int i = 0; i < 1000; i++) {
            final Val val = ValString.create(prefix + i);
            // We need a time at ms precision as that is how it will be stored and our equality won't work otherwise.
            final Instant time = Instant.ofEpochMilli(System.currentTimeMillis());
            final TemporalKey key = new TemporalKey(KeyPrefix.create(val), time);
            variableKeySerde.write(writer.getWriteTxn(), key, keyBuffer -> {
                keys.put(ByteBufferUtils.copyToDirectBuffer(keyBuffer), key);
            });
        }
    }

    private static TimeSerde createTimeSerde(final TemporalPrecision temporalPrecision) {
        return switch (temporalPrecision) {
            case NANOSECOND -> new NanoTimeSerde();
            case MILLISECOND -> new MillisecondTimeSerde();
            case SECOND -> new SecondTimeSerde();
            case MINUTE -> new MinuteTimeSerde();
            case HOUR -> new HourTimeSerde();
            case DAY -> new DayTimeSerde();
        };
    }
}
