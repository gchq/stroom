package stroom.proxy.repo.db;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.db.jooq.tables.records.ZipDataRecord;
import stroom.proxy.repo.db.jooq.tables.records.ZipEntryRecord;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;

import java.util.Optional;
import java.util.stream.Stream;
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
    public Optional<Integer> getSource(final DSLContext context, final String path) {
        return context
                .select(ZIP_SOURCE.ID)
                .from(ZIP_SOURCE)
                .where(ZIP_SOURCE.PATH.eq(path))
                .fetchOptional()
                .map(r -> r.get(ZIP_SOURCE.ID));
    }

    @Override
    public int addSource(final DSLContext context, final String path) {
        return context
                .insertInto(ZIP_SOURCE, ZIP_SOURCE.PATH)
                .values(path)
                .returning(ZIP_SOURCE.ID)
                .fetchOne()
                .getId();
    }

    @Override
    public int addData(final DSLContext context,
                       final int sourceId,
                       final String name,
                       final String feedName,
                       final String typeName) {
        final Optional<ZipDataRecord> optional = context
                .selectFrom(ZIP_DATA)
                .where(ZIP_DATA.FK_ZIP_SOURCE_ID.eq(sourceId))
                .and(ZIP_DATA.NAME.eq(name))
                .fetchOptional();

        if (optional.isPresent()) {
            final ZipDataRecord record = optional.get();
            if ((feedName != null && record.getFeedName() == null) ||
                    (typeName != null && record.getTypeName() == null)) {
                // Update the record with the feed and type name.
                context
                        .update(ZIP_DATA)
                        .set(ZIP_DATA.FEED_NAME, feedName)
                        .set(ZIP_DATA.TYPE_NAME, typeName)
                        .where(ZIP_DATA.ID.eq(record.getId()))
                        .execute();
            }

            return record.getId();
        }

        return context
                .insertInto(ZIP_DATA, ZIP_DATA.FK_ZIP_SOURCE_ID, ZIP_DATA.NAME, ZIP_DATA.FEED_NAME, ZIP_DATA.TYPE_NAME)
                .values(sourceId, name, feedName, typeName)
                .returning(ZIP_DATA.ID)
                .fetchOne()
                .getId();
    }

    @Override
    public int addEntry(final DSLContext context,
                        final int dataId,
                        final String extension,
                        final int extensionType,
                        final long size) {
        return context
                .insertInto(ZIP_ENTRY,
                        ZIP_ENTRY.FK_ZIP_DATA_ID,
                        ZIP_ENTRY.EXTENSION,
                        ZIP_ENTRY.EXTENSION_TYPE,
                        ZIP_ENTRY.BYTE_SIZE)
                .values(dataId, extension, extensionType, size)
                .returning(ZIP_ENTRY.ID)
                .fetchOne()
                .getId();
    }

    public void makeAllDestinations() {
//        JooqUtil.context(connProvider, context -> {
//            context
//                    .select(ZIP_DATA.ID, ZIP_DATA.FEEDNAME)
//                    .from(ZIP_DATA)
//                    .where(ZIP_DATA.HAS_DEST.isFalse())
//                    .and(ZIP_DATA.FEEDNAME.isNotNull())
//
//
//
//
//        });
    }

    public void makeDestinations(final int sourceId) {
        JooqUtil.context(connProvider, context -> {
            // Get zip data items for the source zip.
            try (final Stream<Record2<Integer, String>> stream = context
                    .select(ZIP_DATA.ID, ZIP_DATA.FEED_NAME)
                    .from(ZIP_DATA)
                    .where(ZIP_DATA.FK_ZIP_SOURCE_ID.eq(sourceId))
                    .and(ZIP_DATA.HAS_DEST.isFalse())
                    .and(ZIP_DATA.FEED_NAME.isNotNull())
                    .stream()) {

                stream.forEach(record -> {
                    // Get zip entries for the data item.
                    final Result<ZipEntryRecord> result = JooqUtil.contextResult(connProvider, context2 -> context2
                            .selectFrom(ZIP_ENTRY)
                            .where(ZIP_ENTRY.FK_ZIP_DATA_ID.eq(record.get(ZIP_DATA.ID)))
                            .fetch());


//
//                    JooqUtil.context(connProvider, context2 -> {
//                        context2
//                                .insertInto(ZIP_DEST)
//
//
//                    });


                });
            }


        });
    }
}
