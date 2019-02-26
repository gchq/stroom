package stroom.data.store.impl.fs;

interface FsTypePaths {
    String getPath(String typeName);

    String getType(String path);
}
