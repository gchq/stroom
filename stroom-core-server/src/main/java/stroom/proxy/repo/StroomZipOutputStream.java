package stroom.proxy.repo;

import stroom.feed.MetaMap;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public interface StroomZipOutputStream extends Closeable, AutoCloseable {
    long getProgressSize();

    OutputStream addEntry(String name) throws IOException;

    long getEntryCount();

    void addMissingMetaMap(MetaMap metaMap) throws IOException;

    void closeDelete() throws IOException;
}