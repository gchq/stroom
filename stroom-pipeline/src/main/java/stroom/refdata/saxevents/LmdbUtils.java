/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.saxevents;

import com.google.common.collect.ImmutableMap;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class of static utility methods for working with lmdbjava
 */
public class LmdbUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbUtils.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(LmdbUtils.class);

    private LmdbUtils() {
        // only static util methods
    }

    /**
     * Do work inside a read txn, returning a result of the work
     */
    public static <T> T getWithReadTxn(final Env<ByteBuffer> env, final Function<Txn<ByteBuffer>, T> work) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            return work.apply(txn);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error performing work in read transaction", e);
        }
    }

    /**
     * Do work inside a read txn then commit
     */
    public static void doWithReadTxn(final Env<ByteBuffer> env, Consumer<Txn<ByteBuffer>> work) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            work.accept(txn);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error performing work in read transaction", e);
        }
    }

    /**
     * Do work inside a write txn then commit, return a result of the work
     */
    public static <T> T getWithWriteTxn(final Env<ByteBuffer> env, Function<Txn<ByteBuffer>, T> work) {
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            T result = work.apply(txn);
            txn.commit();
            return result;
        } catch (RuntimeException e) {
            throw new RuntimeException("Error performing work in write transaction", e);
        }
    }

    /**
     * Do work inside a write txn then commit
     */
    public static void doWithWriteTxn(final Env<ByteBuffer> env, Consumer<Txn<ByteBuffer>> work) {
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            work.accept(txn);
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException("Error performing work in write transaction", e);
        }
    }

    public static Map<String, String> getDbInfo(final Env<ByteBuffer> env, Dbi<ByteBuffer> db) {

        return LmdbUtils.getWithReadTxn(env, txn -> {
            Stat stat = db.stat(txn);
            return convertStatToMap(stat);
        });
    }

    public static Map<String, String> getEnvInfo(final Env<ByteBuffer> env) {
        return LmdbUtils.getWithReadTxn(env, txn -> {
            Map<String, String> statMap = convertStatToMap(env.stat());
            Map<String, String> envInfo = convertEnvInfoToMap(env.info());

            String dbNames = env.getDbiNames().stream()
                    .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                    .collect(Collectors.joining(","));

            return ImmutableMap.<String, String>builder()
                    .putAll(statMap)
                    .putAll(envInfo)
                    .put("maxKeySize", Integer.toString(env.getMaxKeySize()))
                    .put("dbNames", dbNames)
                    .build();
        });
    }

    public static ImmutableMap<String, String> convertStatToMap(final Stat stat) {
        return ImmutableMap.<String, String>builder()
                .put("pageSize", Integer.toString(stat.pageSize))
                .put("branchPages", Long.toString(stat.branchPages))
                .put("depth", Integer.toString(stat.depth))
                .put("entries", Long.toString(stat.entries))
                .put("leafPages", Long.toString(stat.leafPages))
                .put("overFlowPages", Long.toString(stat.overflowPages))
                .build();
    }

    public static ImmutableMap<String, String> convertEnvInfoToMap(final EnvInfo envInfo) {
        return ImmutableMap.<String, String>builder()
                .put("maxReaders", Integer.toString(envInfo.maxReaders))
                .put("numReaders", Integer.toString(envInfo.numReaders))
                .put("lastPageNumber", Long.toString(envInfo.lastPageNumber))
                .put("lastTransactionId", Long.toString(envInfo.lastTransactionId))
                .put("mapAddress", Long.toString(envInfo.mapAddress))
                .put("mapSize", Long.toString(envInfo.mapSize))
                .build();
    }

    public static void dumpBuffer(final ByteBuffer byteBuffer, final String description) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = byteBuffer.position(); i < byteBuffer.limit(); i++) {
            byte b = byteBuffer.get(i);
            stringBuilder.append(byteToHex(b));
            stringBuilder.append(" ");
        }
        LOGGER.info("{} byteBuffer: {}", description, stringBuilder.toString());
    }

    public static String byteBufferToHex(final ByteBuffer byteBuffer) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = byteBuffer.position(); i < byteBuffer.limit(); i++) {
            byte b = byteBuffer.get(i);
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
}
