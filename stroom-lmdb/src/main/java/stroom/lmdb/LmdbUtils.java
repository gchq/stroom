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

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.stream.LmdbIterable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.xml.bind.DatatypeConverter;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Class of static utility methods for working with lmdbjava
 */
public class LmdbUtils {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbUtils.class);

    private LmdbUtils() {
        // only static util methods
    }

//    /**
//     * Do work inside a read txn, returning a result of the work
//     */
//    public static <T> T getWithReadTxn(final Environment env,
//                                       final Function<Txn<ByteBuffer>, T> work) {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            return work.apply(txn);
//        } catch (RuntimeException e) {
//            throw new RuntimeException(LogUtil.message("Error performing work in read transaction: {}",
//                    e.getMessage()), e);
//        }
//    }

    /**
     * Do work inside a read txn using a keyBuffer as supplied by the the byteBufferPool
     */
    @SuppressWarnings("checkstyle:Indentation")
    public static <T> T getWithReadTxn(final LmdbEnv env,
                                       final ByteBufferPool byteBufferPool,
                                       final int requiredBufferCapacity,
                                       final BiFunction<Txn<ByteBuffer>, ByteBuffer, T> work) {
        return env.getWithReadTxn(txn ->
                byteBufferPool.getWithBuffer(requiredBufferCapacity, keyBuffer ->
                        work.apply(txn, keyBuffer)));
    }

//    /**
//     * Do work inside a read txn then commit
//     */
//    public static void doWithReadTxn(final Environment env, Consumer<Txn<ByteBuffer>> work) {
//        env.do
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            work.accept(txn);
//        } catch (RuntimeException e) {
//            throw new RuntimeException(LogUtil.message("Error performing work in read transaction: {}",
//                    e.getMessage()), e);
//        }
//    }

//    /**
//     * Do work inside a write txn then commit, return a result of the work
//     */
//    public static <T> T getWithWriteTxn(final Environment env, Function<Txn<ByteBuffer>, T> work) {
//        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
//            T result = work.apply(txn);
//            txn.commit();
//            return result;
//        } catch (RuntimeException e) {
//            throw new RuntimeException(LogUtil.message("Error performing work in write transaction: {}",
//                    e.getMessage()), e);
//        }
//    }

//    /**
//     * Do work inside a write txn then commit. work should be as short lived as possible
//     * to avoid tying up the single write txn for too long
//     */
//    public static void doWithWriteTxn(final Environment env, Consumer<Txn<ByteBuffer>> work) {
//        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
//            work.accept(txn);
//            txn.commit();
//        } catch (RuntimeException e) {
//            throw new RuntimeException(LogUtil.message("Error performing work in write transaction: {}",
//                    e.getMessage()), e);
//        }
//    }

//    public static Map<String, String> getDbInfo(final Environment env,
//                                                final Dbi<ByteBuffer> db) {
//        return env.getWithReadTxn(txn -> {
//            final Stat stat = db.stat(txn);
//            return convertStatToMap(stat);
//        });
//    }

//    public static Map<String, String> getEnvInfo(final Environment env) {
//        return env.getWithReadTxn(txn -> {
//            Map<String, String> statMap = convertStatToMap(env.stat());
//            Map<String, String> envInfo = convertEnvInfoToMap(env.info());
//
//            String dbNames = String.join(",", env.getDbiNames());
//
//            return ImmutableMap.<String, String>builder()
//                    .putAll(statMap)
//                    .putAll(envInfo)
//                    .put("maxKeySize", Integer.toString(env.getMaxKeySize()))
//                    .put("dbNames", dbNames)
//                    .build();
//        });
//    }
//
//    public static ImmutableMap<String, String> convertStatToMap(final Stat stat) {
//        return ImmutableMap.<String, String>builder()
//                .put("pageSize", Integer.toString(stat.pageSize))
//                .put("branchPages", Long.toString(stat.branchPages))
//                .put("depth", Integer.toString(stat.depth))
//                .put("entries", Long.toString(stat.entries))
//                .put("leafPages", Long.toString(stat.leafPages))
//                .put("overFlowPages", Long.toString(stat.overflowPages))
//                .build();
//    }
//
//    public static ImmutableMap<String, String> convertEnvInfoToMap(final EnvInfo envInfo) {
//        return ImmutableMap.<String, String>builder()
//                .put("maxReaders", Integer.toString(envInfo.maxReaders))
//                .put("numReaders", Integer.toString(envInfo.numReaders))
//                .put("lastPageNumber", Long.toString(envInfo.lastPageNumber))
//                .put("lastTransactionId", Long.toString(envInfo.lastTransactionId))
//                .put("mapAddress", Long.toString(envInfo.mapAddress))
//                .put("mapSize", Long.toString(envInfo.mapSize))
//                .build();
//    }

    public static long getEntryCount(final LmdbEnv env, final Txn<ByteBuffer> txn, final Dbi<ByteBuffer> dbi) {

        return dbi.stat(txn).entries;
    }

    public static long getEntryCount(final LmdbEnv env, final Dbi<ByteBuffer> dbi) {

        return env.getWithReadTxn(txn ->
                getEntryCount(env, txn, dbi));
    }

    public static void dumpBuffer(final ByteBuffer byteBuffer, final String description) {
        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = byteBuffer.position(); i < byteBuffer.limit(); i++) {
            final byte b = byteBuffer.get(i);
            stringBuilder.append(byteToHex(b));
            stringBuilder.append(" ");
        }
        LOGGER.info(() -> LogUtil.message("{} byteBuffer: {}", description, stringBuilder.toString()));
    }

    public static String byteBufferToHex(final ByteBuffer byteBuffer) {
        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = byteBuffer.position(); i < byteBuffer.limit(); i++) {
            final byte b = byteBuffer.get(i);
            stringBuilder.append(byteToHex(b));
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    /**
     * Converts a byte array into a hex representation with a space between each
     * byte e.g 00 00 01 00 05 59 B3
     *
     * @param arr The byte array to convert
     * @return The byte array as a string of hex values separated by a spaces
     */
    public static String byteArrayToHex(final byte[] arr) {
        final StringBuilder sb = new StringBuilder();
        if (arr != null) {
            for (final byte b : arr) {
                sb.append(byteToHex(b));
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteToHex(final byte b) {
        final byte[] oneByteArr = new byte[1];
        oneByteArr[0] = b;
        return DatatypeConverter.printHexBinary(oneByteArr);

    }

    /**
     * Allocates a new direct {@link ByteBuffer} with a size equal to the max key size for the LMDB environment and
     * serialises the object into that {@link ByteBuffer}
     *
     * @return The newly allocated {@link ByteBuffer}
     */
    public static <T> ByteBuffer buildDbKeyBuffer(final Env<ByteBuffer> lmdbEnv,
                                                  final T keyObject,
                                                  final Serde<T> keySerde) {
        try {
            return buildDbBuffer(keyObject, keySerde, lmdbEnv.getMaxKeySize());
        } catch (final BufferOverflowException e) {
            throw new RuntimeException(LogUtil.message(
                    "The serialised form of keyObject {} is too big for an LMDB key, max bytes is {}",
                    keyObject, lmdbEnv.getMaxKeySize()), e);
        }
    }

    /**
     * Allocates a new direct {@link ByteBuffer} (of size bufferSize) and
     * serialises the object into that {@link ByteBuffer}
     *
     * @return The newly allocated {@link ByteBuffer}
     */
    public static <T> ByteBuffer buildDbBuffer(final T object, final Serde<T> serde, final int bufferSize) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
        serde.serialize(byteBuffer, object);
        return byteBuffer;
    }

    public static void logDatabaseContents(final LmdbEnv env,
                                           final Dbi<ByteBuffer> dbi,
                                           final Txn<ByteBuffer> txn,
                                           final Function<ByteBuffer, String> keyToStringFunc,
                                           final Function<ByteBuffer, String> valueToStringFunc,
                                           final Consumer<String> logEntryConsumer) {

        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(LogUtil.message("Dumping {} entries for database [{}]",
                getEntryCount(env, txn, dbi), new String(dbi.getName())));

        // loop over all DB entries
        LmdbIterable.iterate(txn, dbi, (key, val) ->
                stringBuilder.append(LogUtil.message("\n  key: [{}] - value [{}]",
                        keyToStringFunc.apply(key),
                        valueToStringFunc.apply(val))));
        logEntryConsumer.accept(stringBuilder.toString());
//        LOGGER.debug(stringBuilder.toString());
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keyToStringFunc and valueToStringFunc functions are
     * used to convert the keys/values into string form for output to the logger.
     */
    public static void logDatabaseContents(final LmdbEnv env,
                                           final Dbi<ByteBuffer> dbi,
                                           final Function<ByteBuffer, String> keyToStringFunc,
                                           final Function<ByteBuffer, String> valueToStringFunc,
                                           final Consumer<String> logEntryConsumer) {

        env.doWithReadTxn(txn ->
                logDatabaseContents(env, dbi, txn, keyToStringFunc, valueToStringFunc, logEntryConsumer));
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keys/values are output as hex representations of the
     * byte values.
     */
    public static void logRawDatabaseContents(final LmdbEnv env,
                                              final Dbi<ByteBuffer> dbi,
                                              final Consumer<String> logEntryConsumer) {
        logDatabaseContents(env,
                dbi,
                ByteBufferUtils::byteBufferToHex,
                ByteBufferUtils::byteBufferToHex,
                logEntryConsumer);
    }

    public static void logRawDatabaseContents(final LmdbEnv env,
                                              final Dbi<ByteBuffer> dbi,
                                              final Txn<ByteBuffer> txn,
                                              final Consumer<String> logEntryConsumer) {
        logDatabaseContents(env,
                dbi,
                txn,
                ByteBufferUtils::byteBufferToHex,
                ByteBufferUtils::byteBufferToHex,
                logEntryConsumer);
    }

//    /**
//     * Only intended for use in tests as the DB could be massive and thus produce a LOT of logging
//     */
//    public static void logContentsInRange(final LmdbEnv env,
//                                          final Dbi<ByteBuffer> dbi,
//                                          final Txn<ByteBuffer> txn,
//                                          final KeyRange<ByteBuffer> keyRange,
//                                          final Function<ByteBuffer, String> keyToStringFunc,
//                                          final Function<ByteBuffer, String> valueToStringFunc,
//                                          final Consumer<String> logEntryConsumer) {
//
//        final StringBuilder stringBuilder = new StringBuilder();
//        stringBuilder.append(LogUtil.message("Dumping entries in range {} [[{}] to [{}]] for database [{}]",
//                keyRange.getType().toString(),
//                ByteBufferUtils.byteBufferToHex(keyRange.getStart()),
//                ByteBufferUtils.byteBufferToHex(keyRange.getStop()),
//                new String(dbi.getName())));
//
//        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, keyRange)) {
//            for (final CursorIterable.KeyVal<ByteBuffer> keyVal : cursorIterable) {
//                stringBuilder.append(LogUtil.message("\n  key: [{}] - value [{}]",
//                        keyToStringFunc.apply(keyVal.key()),
//                        valueToStringFunc.apply(keyVal.val())));
//            }
//        }
//        logEntryConsumer.accept(stringBuilder.toString());
//    }
//
//    public static void logContentsInRange(final LmdbEnv env,
//                                          final Dbi<ByteBuffer> dbi,
//                                          final KeyRange<ByteBuffer> keyRange,
//                                          final Function<ByteBuffer, String> keyToStringFunc,
//                                          final Function<ByteBuffer, String> valueToStringFunc,
//                                          final Consumer<String> logEntryConsumer) {
//        env.doWithReadTxn(txn ->
//                logContentsInRange(env, dbi, txn, keyRange, keyToStringFunc, valueToStringFunc, logEntryConsumer)
//        );
//    }
//
//    public static void logRawContentsInRange(final LmdbEnv env,
//                                             final Dbi<ByteBuffer> dbi,
//                                             final Txn<ByteBuffer> txn,
//                                             final KeyRange<ByteBuffer> keyRange,
//                                             final Consumer<String> logEntryConsumer) {
//        logContentsInRange(env, dbi, txn, keyRange, ByteBufferUtils::byteBufferToHex,
//                ByteBufferUtils::byteBufferToHex, logEntryConsumer);
//    }
//
//    public static void logRawContentsInRange(final LmdbEnv env,
//                                             final Dbi<ByteBuffer> dbi,
//                                             final KeyRange<ByteBuffer> keyRange,
//                                             final Consumer<String> logEntryConsumer) {
//        env.doWithReadTxn(txn ->
//                logContentsInRange(env, dbi, txn, keyRange, ByteBufferUtils::byteBufferToHex,
//                        ByteBufferUtils::byteBufferToHex, logEntryConsumer)
//        );
//    }
}
