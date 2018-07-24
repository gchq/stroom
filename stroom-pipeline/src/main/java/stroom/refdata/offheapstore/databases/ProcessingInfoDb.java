package stroom.refdata.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.ByteBufferPair;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.ProcessingState;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.offheapstore.serdes.RefStreamDefinitionSerde;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Predicate;

public class ProcessingInfoDb extends AbstractLmdbDb<RefStreamDefinition, RefDataProcessingInfo> {

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

    public Optional<ByteBufferPair> getNextEntryAsBytes(final Txn<ByteBuffer> txn,
                                                        final ByteBuffer startKeyBuffer,
                                                        final Predicate<ByteBuffer> valueBufferPredicate) {

        Optional<ByteBufferPair> foundEntry = Optional.empty();
        final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(startKeyBuffer);
        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(txn, keyRange)) {
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {

                if (valueBufferPredicate.test(keyVal.val())) {
                    // got a match
                    foundEntry = Optional.of(ByteBufferPair.of(keyVal.key(), keyVal.val()));
                }
            }
        }

        return foundEntry;
    }

    public interface Factory {
        ProcessingInfoDb create(final Env<ByteBuffer> lmdbEnvironment);
    }

}
