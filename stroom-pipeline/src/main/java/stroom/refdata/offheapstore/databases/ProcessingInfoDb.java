package stroom.refdata.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.ByteBufferUtils;
import stroom.refdata.offheapstore.PooledByteBufferPair;
import stroom.refdata.offheapstore.ProcessingState;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.offheapstore.serdes.RefStreamDefinitionSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Predicate;

public class ProcessingInfoDb extends AbstractLmdbDb<RefStreamDefinition, RefDataProcessingInfo> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingInfoDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ProcessingInfoDb.class);

    private static final String DB_NAME = "ProcessingInfo";
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
        LmdbUtils.doWithWriteTxn(lmdbEnvironment, writeTxn ->
                updateValue(writeTxn,
                        refStreamDefinition,
                        valueSerde::updateLastAccessedTime));
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
        return LmdbUtils.getWithReadTxn(lmdbEnvironment, byteBufferPool, (readTxn, keyBuffer) -> {
            keySerde.serialize(keyBuffer, refStreamDefinition);
            ByteBuffer valueBuffer = lmdbDbi.get(readTxn, keyBuffer);
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

        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(txn, keyRange)) {
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

    public interface Factory {
        ProcessingInfoDb create(final Env<ByteBuffer> lmdbEnvironment);
    }

}
