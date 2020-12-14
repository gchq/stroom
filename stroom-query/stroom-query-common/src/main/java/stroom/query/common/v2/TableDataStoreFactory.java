package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.pipeline.refdata.util.ByteBufferPoolConfig;
import stroom.pipeline.refdata.util.ByteBufferPoolImpl4;
import stroom.pipeline.refdata.util.PooledByteBufferOutputStream;
import stroom.query.api.v2.TableSettings;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Map;

public class TableDataStoreFactory {
    private final Provider<PooledByteBufferOutputStream> outputStreamProvider;

    public TableDataStoreFactory() {
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl4(new ByteBufferPoolConfig());
        outputStreamProvider = () -> new PooledByteBufferOutputStream(byteBufferPool, 10);
    }

    @Inject
    public TableDataStoreFactory(final Provider<PooledByteBufferOutputStream> outputStreamProvider) {
        this.outputStreamProvider = outputStreamProvider;
    }

    public TableDataStore create(final TableSettings tableSettings,
                                 final FieldIndex fieldIndex,
                                 final Map<String, String> paramMap,
                                 final Sizes maxResults,
                                 final Sizes storeSize) {
        return new TableDataStore(
                outputStreamProvider,
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize);
    }
}
