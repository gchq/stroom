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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LmdbUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbUtils.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(LmdbUtils.class);

    private LmdbUtils() {
        // only static util methods
    }

    /**
     * Do work inside a read txn, returning a result of the work
     */
    public static <T> T getInReadTxn(final Env<ByteBuffer> env, final Function<Txn<ByteBuffer>, T> work) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            return work.apply(txn);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error performing work in read transaction", e);
        }
    }

    /**
     * Do work inside a write txn then commit, return a result of the work
     */
    public static <T> T getInWriteTxn(final Env<ByteBuffer> env, Function<Txn<ByteBuffer>, T> work) {
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
    public static void doInWriteTxn(final Env<ByteBuffer> env, Consumer<Txn<ByteBuffer>> work) {
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            work.accept(txn);
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException("Error performing work in write transaction", e);
        }
    }

    public static Map<String, String> getDbInfo(final Env<ByteBuffer> env, Dbi<ByteBuffer> db) {

        return LmdbUtils.getInReadTxn(env, txn -> {
            Stat stat = db.stat(txn);
            return convertStatToMap(stat);
        });
    }

    public static Map<String, String> getEnvInfo(final Env<ByteBuffer> env) {
        return LmdbUtils.getInReadTxn(env, txn -> {
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

    private static ImmutableMap<String, String> convertStatToMap(final Stat stat) {
        return ImmutableMap.<String, String>builder()
                .put("pageSize", Integer.toString(stat.pageSize))
                .put("branchPages", Long.toString(stat.branchPages))
                .put("depth", Integer.toString(stat.depth))
                .put("entries", Long.toString(stat.entries))
                .put("leafPages", Long.toString(stat.leafPages))
                .put("overFlowPages", Long.toString(stat.overflowPages))
                .build();
    }

    private static ImmutableMap<String, String> convertEnvInfoToMap(final EnvInfo envInfo) {
        return ImmutableMap.<String, String>builder()
                .put("maxReaders", Integer.toString(envInfo.maxReaders))
                .put("numReaders", Integer.toString(envInfo.numReaders))
                .put("lastPageNumber", Long.toString(envInfo.lastPageNumber))
                .put("lastTransactionId", Long.toString(envInfo.lastTransactionId))
                .put("mapAddress", Long.toString(envInfo.mapAddress))
                .put("mapSize", Long.toString(envInfo.mapSize))
                .build();
    }
}
