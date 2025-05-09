package stroom.planb.impl.db.serde;

import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface ValSerde {

    Val read(Txn<ByteBuffer> readTxn, ByteBuffer byteBuffer);

    void write(Txn<ByteBuffer> writeTxn, Val value,
               Consumer<ByteBuffer> consumer);
}
