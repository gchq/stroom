package stroom.planb.impl.db;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Function;

public class UsedLookupsRecorderProxy implements UsedLookupsRecorder {

    private final UsedLookupsRecorder usedLookupsRecorder;
    private final Function<ByteBuffer, ByteBuffer> keyExtractionFunction;

    public UsedLookupsRecorderProxy(final UsedLookupsRecorder usedLookupsRecorder,
                                    final Function<ByteBuffer, ByteBuffer> keyExtractionFunction) {
        this.usedLookupsRecorder = usedLookupsRecorder;
        this.keyExtractionFunction = keyExtractionFunction;
    }

    @Override
    public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
        usedLookupsRecorder.recordUsed(writer, keyExtractionFunction.apply(byteBuffer));
    }

    @Override
    public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
        usedLookupsRecorder.deleteUnused(readTxn, writer);
    }
}
