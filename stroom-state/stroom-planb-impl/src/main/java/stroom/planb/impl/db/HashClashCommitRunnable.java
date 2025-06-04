package stroom.planb.impl.db;

import stroom.planb.impl.serde.hash.HashClashCount;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class HashClashCommitRunnable implements Consumer<Txn<ByteBuffer>>, HashClashCount {

    private int hashClashes;
    private Consumer<Txn<ByteBuffer>> runnable = txn -> {
    };

    public void setHashClashes(final int hashClashes) {
        this.hashClashes = hashClashes;
    }

    public void setRunnable(final Consumer<Txn<ByteBuffer>> runnable) {
        this.runnable = runnable;
    }

    public int getHashClashes() {
        return hashClashes;
    }

    @Override
    public void increment() {
        // We must have had a hash clash here because we didn't find a row for the key even
        // though the db contains the key hash.
        hashClashes++;
    }

    @Override
    public void accept(final Txn<ByteBuffer> txn) {
        runnable.accept(txn);
    }
}
