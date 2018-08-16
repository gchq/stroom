package stroom.refdata.store.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.store.offheapstore.lmdb.AbstractLmdbDb;
import stroom.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.refdata.util.ByteBufferPool;
import stroom.refdata.util.ByteBufferUtils;
import stroom.refdata.util.PooledByteBufferPair;
import stroom.refdata.store.ProcessingState;
import stroom.refdata.store.RefDataProcessingInfo;
import stroom.refdata.store.RefStreamDefinition;
import stroom.refdata.store.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.store.offheapstore.serdes.RefStreamDefinitionSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.Predicate;

public class ProcessingInfoDb extends AbstractLmdbDb<RefStreamDefinition, RefDataProcessingInfo> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingInfoDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ProcessingInfoDb.class);

    public static final String DB_NAME = "ProcessingInfo";
    private final RefStreamDefinitionSerde keySerde;
    private final RefDataProcessingInfoSerde valueSerde;

    @Inject
    public ProcessingInfoDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                            final ByteBufferPool byteBufferPool,
                            final RefStreamDefinitionSerde keySerde,
                            final RefDataProcessingInfoSerde valueSerde) {

        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public void updateLastAccessedTime(final RefStreamDefinition refStreamDefinition) {
        updateLastAccessedTime(refStreamDefinition, System.currentTimeMillis());
    }

    public void updateLastAccessedTime(final RefStreamDefinition refStreamDefinition, final long newLastAccessedTimeMs) {
        LmdbUtils.doWithWriteTxn(getLmdbEnvironment(), writeTxn ->
                updateValue(writeTxn,
                        refStreamDefinition,
                        valueBuffer ->
                                valueSerde.updateLastAccessedTime(valueBuffer, newLastAccessedTimeMs)));
    }

    public void updateProcessingState(final Txn<ByteBuffer> writeTxn,
                                      final RefStreamDefinition refStreamDefinition,
                                      final ProcessingState newProcessingState,
                                      final boolean touchLastAccessedTime) {

        updateValue(writeTxn,
                refStreamDefinition, newValueBuf -> {
                    valueSerde.updateState(newValueBuf, newProcessingState);
                    if (touchLastAccessedTime) {
                        valueSerde.updateLastAccessedTime(newValueBuf);
                    }
                });
    }

    public void updateProcessingState(final Txn<ByteBuffer> writeTxn,
                                      final ByteBuffer refStreamDefinitionBuffer,
                                      final ProcessingState newProcessingState,
                                      final boolean touchLastAccessedTime) {

        updateValue(writeTxn,
                refStreamDefinitionBuffer, newValueBuf -> {
                    valueSerde.updateState(newValueBuf, newProcessingState);
                    if (touchLastAccessedTime) {
                        valueSerde.updateLastAccessedTime(newValueBuf);
                    }
                });
    }

    public ProcessingState getProcessingState(final RefStreamDefinition refStreamDefinition) {
        return LmdbUtils.getWithReadTxn(getLmdbEnvironment(), getByteBufferPool(), (readTxn, keyBuffer) -> {
            keySerde.serialize(keyBuffer, refStreamDefinition);
            ByteBuffer valueBuffer = getLmdbDbi().get(readTxn, keyBuffer);
            return RefDataProcessingInfoSerde.extractProcessingState(valueBuffer);
        });
    }

    public Optional<PooledByteBufferPair> getNextEntryAsBytes(final Txn<ByteBuffer> txn,
                                                              final ByteBuffer startKeyBuffer,
                                                              final Predicate<ByteBuffer> valueBufferPredicate,
                                                              final PooledByteBufferPair pooledByteBufferPair) {

        Optional<PooledByteBufferPair> optMatchedEntry = Optional.empty();

        final KeyRange<ByteBuffer> keyRange;

        if (startKeyBuffer == null) {
            LOGGER.debug("Scanning from start of DB");
            keyRange = KeyRange.all();
        } else {
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                    "Scanning from {}", ByteBufferUtils.byteBufferInfo(startKeyBuffer)));
            keyRange = KeyRange.atLeast(startKeyBuffer);
        }
        int i = 0;

        try (CursorIterator<ByteBuffer> cursorIterator = getLmdbDbi().iterate(txn, keyRange)) {
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                i++;

                if (valueBufferPredicate.test(keyVal.val())) {
                    // got a match but was we are returning it out of the cursor scope we must copy
                    // the key/value buffers, else the buffers will/may be mutated and strange things will happen
                    ByteBufferUtils.copy(keyVal.key(), pooledByteBufferPair.getKeyBuffer());
                    ByteBufferUtils.copy(keyVal.val(), pooledByteBufferPair.getValueBuffer());

                    optMatchedEntry = Optional.of(pooledByteBufferPair);
                    break;
                }
            }
        }

        LOGGER.debug("getNextEntryAsBytes returning {} after {} iterations", optMatchedEntry, i);
        return optMatchedEntry;
    }

    public Tuple2<Instant, Instant> getLastAccessedTimeRange() {

        final LongAccumulator earliestLastAccessedTime = new LongAccumulator(Long::min, Long.MAX_VALUE);
        final LongAccumulator latestLastAccessedTime = new LongAccumulator(Long::max, 0);

        LmdbUtils.doWithReadTxn(getLmdbEnvironment(), txn ->
                forEachEntryAsBytes(txn, KeyRange.all(), keyVal -> {
                    // It would be quicker to keep a copy of the earliest/latest times in there byte
                    // form and do the comparison on that but as this is only intended for use
                    // in a health check it is probably ok as is.
                    long lastAccessedTime = valueSerde.extractLastAccessedTimeMs(keyVal.val());
                    earliestLastAccessedTime.accumulate(lastAccessedTime);
                    latestLastAccessedTime.accumulate(lastAccessedTime);
                }));

        return Tuple.of(
                Instant.ofEpochMilli(earliestLastAccessedTime.get()),
                Instant.ofEpochMilli(latestLastAccessedTime.get()));
    }

    public interface Factory {
        ProcessingInfoDb create(final Env<ByteBuffer> lmdbEnvironment);
    }

}
