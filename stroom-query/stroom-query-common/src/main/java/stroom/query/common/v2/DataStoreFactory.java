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

public class DataStoreFactory {

    public DataStoreFactory() {
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl4(new ByteBufferPoolConfig());
    }

    @Inject
    public DataStoreFactory(final Provider<PooledByteBufferOutputStream> outputStreamProvider) {
    }

    public DataStore create(final TableSettings tableSettings,
                               final FieldIndex fieldIndex,
                               final Map<String, String> paramMap,
                               final Sizes maxResults,
                               final Sizes storeSize) {
        return new MapDataStore(
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize);
    }
}
