package stroom.data.store.impl.fs;

interface FileSystemTypePaths {
    String getPath(String typeName);

    String getType(String path);
}
