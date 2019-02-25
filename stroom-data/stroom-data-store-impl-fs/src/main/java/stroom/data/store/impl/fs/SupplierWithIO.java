package stroom.data.store.impl.fs;

import java.io.IOException;

@FunctionalInterface
public interface SupplierWithIO<T> {
    T getWithIO() throws IOException;
}