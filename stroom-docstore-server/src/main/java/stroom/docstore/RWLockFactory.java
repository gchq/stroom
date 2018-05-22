package stroom.docstore;

import java.util.function.Supplier;

public interface RWLockFactory {
    void lock(String uuid, Runnable runnable);

    <T> T lockResult(String uuid, Supplier<T> supplier);
}
