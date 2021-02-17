package stroom.proxy.repo.db;

import org.jooq.DSLContext;

public interface ZipInfoStoreDao {
    Long getSource(DSLContext context, String path);

    Long addSource(DSLContext context, String path);

    Long addData(DSLContext context, long sourceId, String name, String feedName);

    Long addEntry(DSLContext context, long sourceId, long dataId, String extension, Long size);

}
