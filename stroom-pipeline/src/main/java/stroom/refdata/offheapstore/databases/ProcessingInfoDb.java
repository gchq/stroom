package stroom.refdata.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.offheapstore.serdes.RefStreamDefinitionSerde;

import javax.inject.Inject;
import java.nio.ByteBuffer;

public class ProcessingInfoDb extends AbstractLmdbDb<RefStreamDefinition, RefDataProcessingInfo> {

    private static final String DB_NAME = "ProcessingInfo";
    private final RefStreamDefinitionSerde keySerde;
    private final RefDataProcessingInfoSerde valueSerde;

    @Inject
    public ProcessingInfoDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                            final RefStreamDefinitionSerde keySerde,
                            final RefDataProcessingInfoSerde valueSerde) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
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
                                      final RefDataProcessingInfo.ProcessingState newProcessingState) {

        updateValue(writeTxn,
                refStreamDefinition, newValueBuf -> {
                    valueSerde.updateState(newValueBuf, newProcessingState);
                    valueSerde.updateLastAccessedTime(newValueBuf);
                });
    }

    public interface Factory {
        ProcessingInfoDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
