package stroom.docstore;

public interface RWLock extends AutoCloseable {
    @Override
    void close();
}