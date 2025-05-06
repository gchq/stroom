package stroom.planb.impl.db;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public interface DBWriter {
    void write(Txn<ByteBuffer> writeTxn,
               ByteBuffer keyByteBuffer,
               ByteBuffer valueByteBuffer);
}
