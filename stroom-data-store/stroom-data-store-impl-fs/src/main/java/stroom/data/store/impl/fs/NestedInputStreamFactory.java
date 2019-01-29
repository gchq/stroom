package stroom.data.store.impl.fs;

import java.io.InputStream;

interface NestedInputStreamFactory {
    NestedInputStreamFactory getChild(String streamTypeName);

    InputStream getInputStream();
}
