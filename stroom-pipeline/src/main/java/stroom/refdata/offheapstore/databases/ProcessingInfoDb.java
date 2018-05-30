package stroom.refdata.offheapstore.databases;

import org.lmdbjava.Cursor;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.Txn;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.offheapstore.serdes.RefStreamDefinitionSerde;
import stroom.refdata.saxevents.LmdbUtils;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

public class ProcessingInfoDb extends AbstractLmdbDb<RefStreamDefinition, RefDataProcessingInfo> {

    private static final String DB_NAME = "ProcessingInfo";

    public ProcessingInfoDb(final Env<ByteBuffer> lmdbEnvironment,
                            final RefStreamDefinitionSerde keySerde,
                            final RefDataProcessingInfoSerde valueSerde) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    void updateProcessingState(final Txn<ByteBuffer> writeTxn,
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
        }
    }

    public interface Factory {
        ProcessingInfoDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
