package stroom.planb.impl.db.state;

import stroom.query.language.functions.Val;

import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public interface ValuesExtractor {

    Val[] apply(Txn<ByteBuffer> readTxn, KeyVal<ByteBuffer> kv);
}
