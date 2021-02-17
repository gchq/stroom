package stroom.proxy.repo.db;

import stroom.proxy.repo.db.jooq.tables.records.ZipDataRecord;

import org.jooq.DSLContext;

import java.util.Optional;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.ZipData.ZIP_DATA;
import static stroom.proxy.repo.db.jooq.tables.ZipEntry.ZIP_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.ZipSource.ZIP_SOURCE;

public class ZipInfoStoreDaoImpl implements ZipInfoStoreDao {

    private final ProxyRepoDbConnProvider connProvider;

    @Inject
    public ZipInfoStoreDaoImpl(final ProxyRepoDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    @Override
    public Long getSource(final DSLContext context, final String path) {
        return context
                .select(ZIP_SOURCE.ID)
                .from(ZIP_SOURCE)
                .where(ZIP_SOURCE.PATH.eq(path))
                .fetchOptional()
                .map(r -> r.get(ZIP_SOURCE.ID))
                .orElse(null);
    }

    @Override
    public Long addSource(final DSLContext context, final String path) {
        return context
                .insertInto(ZIP_SOURCE, ZIP_SOURCE.PATH)
                .values(path)
                .returning(ZIP_SOURCE.ID)
                .fetchOptional()
                .map(r -> r.get(ZIP_SOURCE.ID))
                .orElse(null);
    }

    @Override
    public Long addData(final DSLContext context,
                        final long sourceId,
                        final String name,
                        final String feedName) {
        final Optional<ZipDataRecord> optional = context
                .selectFrom(ZIP_DATA)
                .where(ZIP_DATA.FK_ZIP_SOURCE_ID.eq(sourceId))
                .and(ZIP_DATA.NAME.eq(name))
                .fetchOptional();

        if (optional.isPresent()) {
            final ZipDataRecord record = optional.get();
            if (feedName != null && record.getFeedname() == null) {
                // Update the record with the feed name.
                context
                        .update(ZIP_DATA)
                        .set(ZIP_DATA.FEEDNAME, feedName)
                        .where(ZIP_DATA.ID.eq(record.getId()))
                        .execute();
            }

            return record.getId();
        }

        return context
                .insertInto(ZIP_DATA, ZIP_DATA.FK_ZIP_SOURCE_ID, ZIP_DATA.NAME, ZIP_DATA.FEEDNAME)
                .values(sourceId, name, feedName)
                .returning(ZIP_DATA.ID)
                .fetchOptional()
                .map(r -> r.get(ZIP_DATA.ID))
                .orElse(null);
    }

    @Override
    public Long addEntry(final DSLContext context,
                         final long sourceId,
                         final long dataId,
                         final String extension,
                         final Long size) {
        return context
                .insertInto(ZIP_ENTRY, ZIP_ENTRY.FK_ZIP_SOURCE_ID, ZIP_ENTRY.FK_ZIP_DATA_ID, ZIP_ENTRY.EXTENSION)
                .values(sourceId, dataId, extension)
                .returning(ZIP_ENTRY.ID)
                .fetchOptional()
                .map(r -> r.get(ZIP_ENTRY.ID))
                .orElse(null);
    }
}
