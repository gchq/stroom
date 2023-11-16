package stroom.lmdb.topic;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

class TxnAction {

    private final Consumer<Txn<ByteBuffer>> action;
    private final boolean commit;

    TxnAction(final Consumer<Txn<ByteBuffer>> action,
              final boolean commit) {
        this.action = Objects.requireNonNull(action);
        this.commit = commit;
    }

    public void run(final Txn<ByteBuffer> txn) {
        action.accept(txn);
    }

    public boolean shouldCommit() {
        return commit;
    }
}
