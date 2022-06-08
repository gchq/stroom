package stroom.proxy.repo.store;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public interface Entries extends Closeable, AutoCloseable {

    OutputStream addEntry(String name) throws IOException;

    void closeDelete() throws IOException;
}
