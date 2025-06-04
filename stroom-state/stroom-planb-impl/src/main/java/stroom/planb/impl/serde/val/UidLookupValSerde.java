package stroom.planb.impl.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class UidLookupValSerde implements ValSerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;

    public UidLookupValSerde(final UidLookupDb uidLookupDb, final ByteBuffers byteBuffers) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, byteBuffer);
        return ValSerdeUtil.read(valueByteBuffer);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > Db.MAX_KEY_LENGTH) {
                throw new RuntimeException("Key length exceeds " + Db.MAX_KEY_LENGTH + " bytes");
            }

            uidLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                consumer.accept(idByteBuffer);
                return null;
            });
            return null;
        });
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UidLookupRecorder(env, uidLookupDb);
    }
}
