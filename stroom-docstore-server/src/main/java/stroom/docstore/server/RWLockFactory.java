package stroom.docstore.server;

public interface RWLockFactory {
    RWLock lock(String uuid);
}
