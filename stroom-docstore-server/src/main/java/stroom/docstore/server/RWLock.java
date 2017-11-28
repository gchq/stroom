package stroom.docstore.server;

public interface RWLock extends AutoCloseable {
    @Override
    void close();
}