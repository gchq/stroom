package stroom.data.store.impl.fs;

import java.io.OutputStream;

interface NestedOutputStreamFactory {
    NestedOutputStreamFactory addChild(String streamTypeName);

    OutputStream getOutputStream();
}
