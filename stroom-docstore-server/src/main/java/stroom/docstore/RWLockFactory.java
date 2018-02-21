package stroom.docstore;

public interface RWLockFactory {
    RWLock lock(String uuid);
}
