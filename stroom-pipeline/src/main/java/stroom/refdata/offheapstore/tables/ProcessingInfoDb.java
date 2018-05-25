package stroom.refdata.offheapstore.tables;

import org.lmdbjava.Env;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefStreamDefinition;

import java.nio.ByteBuffer;

public class ProcessingInfoDb extends AbstractLmdbDb<RefStreamDefinition, RefDataProcessingInfo> {

    private static final String DB_NAME = "ProcessingInfo";

    public ProcessingInfoDb(final Env<ByteBuffer> lmdbEnvironment,
                            final Serde<RefStreamDefinition> keySerde,
                            final Serde<RefDataProcessingInfo> valueSerde,
                            final String dbName) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    public interface Factory {
        ProcessingInfoDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
