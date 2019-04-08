package stroom.data.store.impl.fs;

public interface FsTypePathDao {
    String getOrCreatePath(String typeName);

    String getType(String path);
}
