package stroom.proxy.repo;

import stroom.data.meta.api.AttributeMap;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public interface StroomZipOutputStream extends Closeable, AutoCloseable {
    long getProgressSize();

    OutputStream addEntry(String name) throws IOException;

    long getEntryCount();

    void addMissingAttributeMap(AttributeMap attributeMap) throws IOException;

    void closeDelete() throws IOException;
}