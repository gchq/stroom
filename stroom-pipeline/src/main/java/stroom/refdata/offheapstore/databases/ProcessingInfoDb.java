package stroom.refdata.offheapstore.databases;

import org.lmdbjava.Cursor;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.offheapstore.serdes.RefStreamDefinitionSerde;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

public class ProcessingInfoDb extends AbstractLmdbDb<RefStreamDefinition, RefDataProcessingInfo> {

    private static final String DB_NAME = "ProcessingInfo";
    private final RefStreamDefinitionSerde keySerde;
    private final RefDataProcessingInfoSerde valueSerde;

    public ProcessingInfoDb(final Env<ByteBuffer> lmdbEnvironment,
                            final RefStreamDefinitionSerde keySerde,
                            final RefDataProcessingInfoSerde valueSerde) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public void updateProcessingState(final Txn<ByteBuffer> writeTxn,
                               final RefStreamDefinition refStreamDefinition,
                               final RefDataProcessingInfo.ProcessingState newProcessingState) {

        try (Cursor<ByteBuffer> cursor = lmdbDbi.openCursor(writeTxn)) {
            final ByteBuffer keyBuf = LmdbUtils.buildDbKeyBuffer(lmdbEnvironment, refStreamDefinition, keySerde);

            boolean isFound = cursor.get(keyBuf, GetOp.MDB_SET_KEY);
            if (!isFound) {
                throw new RuntimeException(LambdaLogger.buildMessage(
                        "Expecting to find entry for {}", refStreamDefinition));
            }
            final ByteBuffer valueBuf = cursor.val();

            // We run LMDB in its default mode of read only mmaps so we cannot mutate the key/value
            // bytebuffers.  Instead we must copy the content and put the replacement entry.
            // We could run LMDB in MDB_WRITEMAP mode which allows mutation of the buffers (and
            // thus avoids the buffer copy cost) but adds more risk of DB corruption. As we are not
            // doing a high volume of value mutations read-only mode is a safer bet.
            final ByteBuffer newValueBuf = LmdbUtils.copyDirectBuffer(valueBuf);

            valueSerde.updateState(newValueBuf, newProcessingState);
            valueSerde.updateLastAccessedTime(newValueBuf);

            cursor.put(cursor.key(), newValueBuf, PutFlags.MDB_CURRENT);
        }
    }

    public interface Factory {
        ProcessingInfoDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
