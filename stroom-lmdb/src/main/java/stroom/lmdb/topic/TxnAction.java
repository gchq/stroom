package stroom.lmdb.topic;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

class TxnAction<T> {

    private final Function<Txn<ByteBuffer>, T> action;
    private final boolean commit;
    private final boolean providesResult;
    private final CompletableFuture<T> completableFuture;

    private TxnAction(final Function<Txn<ByteBuffer>, T> action,
                      final boolean commit,
                      final boolean providesResult) {
        this.action = action;
        this.commit = commit;
        this.providesResult = providesResult;
        this.completableFuture = new CompletableFuture<>();
    }

    public static <T> TxnAction<T> create(final Function<Txn<ByteBuffer>, T> action,
                                          final boolean commit) {
        Objects.requireNonNull(action);
        return new TxnAction<>(action, commit, true);
    }

    public static TxnAction<Void> create(final Consumer<Txn<ByteBuffer>> action,
                                         final boolean commit) {
        Objects.requireNonNull(action);
        return new TxnAction<>(txn -> {
            action.accept(txn);
            return Void.TYPE.cast(null);
        }, commit, false);
    }

    public static TxnAction<Void> withCommit(final Consumer<Txn<ByteBuffer>> action) {
        Objects.requireNonNull(action);
        return new TxnAction<Void>(txn -> {
            action.accept(txn);
            return Void.TYPE.cast(null);
        }, true, false);
    }

    public static <T> TxnAction<T> withCommit(final Function<Txn<ByteBuffer>, T> action) {
        return new TxnAction<>(Objects.requireNonNull(action), true, true);
    }

    public static TxnAction<Void> withoutCommit(final Consumer<Txn<ByteBuffer>> action) {
        Objects.requireNonNull(action);
        return new TxnAction<>(txn -> {
            action.accept(txn);
            return Void.TYPE.cast(null);
        }, false, false);
    }

    public static <T> TxnAction<T> withoutCommit(final Function<Txn<ByteBuffer>, T> action) {
        return new TxnAction<>(Objects.requireNonNull(action), false, true);
    }

    public static TxnAction<Void> commitOnly() {
        return new TxnAction<>(null, true, false);
    }

    void execute(final Txn<ByteBuffer> txn) {
        if (action != null && !completableFuture.isDone()) {
            try {
                final T result = action.apply(txn);
                completableFuture.complete(result);
//                return result;
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
                throw e;
            }
        } else {
//            return null;
        }
    }

    public CompletableFuture<T> geFuture() {
        return completableFuture;
    }

    public boolean shouldCommit() {
        return commit;
    }

    public boolean providesResult() {
        return providesResult;
    }
}
