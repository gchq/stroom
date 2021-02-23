package stroom.proxy.repo.db;

import org.jooq.DSLContext;

import java.util.Optional;

public interface ZipInfoStoreDao {

    Optional<Integer> getSource(DSLContext context, String path);

    int addSource(DSLContext context, String path);

    int addData(DSLContext context, int sourceId, String name, String feedName, String typeName);

    int addEntry(DSLContext context, int dataId, String extension, int extensionType, long size);

}
