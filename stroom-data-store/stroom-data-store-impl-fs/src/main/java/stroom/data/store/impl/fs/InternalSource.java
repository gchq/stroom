package stroom.data.store.impl.fs;

import stroom.data.store.api.Source;

import java.io.InputStream;

interface InternalSource extends Source {
    InputStream getInputStream();

    InputStream getChildInputStream(String type);
}
