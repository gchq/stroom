package stroom.data.store.impl.fs;

import stroom.data.store.api.Target;

import java.io.OutputStream;

interface InternalTarget extends Target {
    OutputStream getOutputStream();

    OutputStream getChildOutputStream(String type);
}
