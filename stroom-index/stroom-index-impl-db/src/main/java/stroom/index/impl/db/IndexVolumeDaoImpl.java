package stroom.index.impl.db;

import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import java.util.List;

import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP;
import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP_LINK;
import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;

class IndexVolumeDaoImpl implements IndexVolumeDao {
    private final ConnectionProvider connectionProvider;
    private final SecurityContext securityContext;

    private static final Byte FIRST_VERSION = 1;

    @Inject
    IndexVolumeDaoImpl(final SecurityContext securityContext,
                       final ConnectionProvider connectionProvider) {
        this.securityContext = securityContext;
        this.connectionProvider = connectionProvider;
    }

    @Override
    public List<IndexVolume> getAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME)
                .fetchInto(IndexVolume.class));
    }

    @Override
    public IndexVolume create(final String nodeName,
                              final String path) {
        final Long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        return JooqUtil.contextResult(connectionProvider, context -> {
            final Long id = context.insertInto(INDEX_VOLUME,
                    INDEX_VOLUME.VERSION,
                    INDEX_VOLUME.NODE_NAME,
                    INDEX_VOLUME.PATH,
                    INDEX_VOLUME.CREATE_USER,
                    INDEX_VOLUME.CREATE_TIME_MS,
                    INDEX_VOLUME.UPDATE_USER,
                    INDEX_VOLUME.UPDATE_TIME_MS)
                    .values(FIRST_VERSION,
                            nodeName,
                            path,
                            userId,
                            now,
                            userId,
                            now)
                    .returning(INDEX_VOLUME.ID)
                    .fetchOne()
                    .getId();

            return context.select()
                    .from(INDEX_VOLUME)
                    .where(INDEX_VOLUME.ID.eq(id))
                    .fetchOneInto(IndexVolume.class);
        });
    }

    @Override
    public IndexVolume getById(final Long id) {
        return JooqUtil.contextResult(connectionProvider, context -> context.select()
                .from(INDEX_VOLUME)
                .where(INDEX_VOLUME.ID.eq(id))
                .fetchOneInto(IndexVolume.class)
        );
    }

    @Override
    public void delete(final Long id) {
        JooqUtil.context(connectionProvider, context -> context
                .deleteFrom(INDEX_VOLUME)
                .where(INDEX_VOLUME.ID.eq(id))
                .execute()
        );
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final String groupName) {
        return JooqUtil.contextResult(connectionProvider, context -> context.select()
                .from(INDEX_VOLUME)
                .innerJoin(INDEX_VOLUME_GROUP_LINK)
                .on(INDEX_VOLUME.ID.eq(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID))
                .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_NAME.eq(groupName))
                .fetchInto(IndexVolume.class)
        );
    }

    @Override
    public List<IndexVolumeGroup> getGroupsForVolume(final Long id) {
        return JooqUtil.contextResult(connectionProvider, context -> context.select()
                .from(INDEX_VOLUME_GROUP)
                .innerJoin(INDEX_VOLUME_GROUP_LINK)
                .on(INDEX_VOLUME_GROUP.NAME.eq(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_NAME))
                .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID.eq(id))
                .fetchInto(IndexVolumeGroup.class)
        );
    }

    @Override
    public List<IndexVolume> getVolumesInGroupOnNode(final String groupName,
                                                     final String nodeName) {
        return JooqUtil.contextResult(connectionProvider, context -> context.select()
                .from(INDEX_VOLUME)
                .innerJoin(INDEX_VOLUME_GROUP_LINK)
                .on(INDEX_VOLUME.ID.eq(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID))
                .where(INDEX_VOLUME.NODE_NAME.eq(nodeName))
                .and(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_NAME.eq(groupName))
                .fetchInto(IndexVolume.class)
        );
    }

    @Override
    public void addVolumeToGroup(final Long volumeId,
                                 final String name) {
        JooqUtil.context(connectionProvider, context -> context
                .insertInto(INDEX_VOLUME_GROUP_LINK,
                        INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID,
                        INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_NAME)
                .values(volumeId, name)
                .onDuplicateKeyIgnore()
                .execute());
    }

    @Override
    public void removeVolumeFromGroup(final Long volumeId,
                                      final String name) {
        JooqUtil.context(connectionProvider, context -> context
                .deleteFrom(INDEX_VOLUME_GROUP_LINK)
                .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID.eq(volumeId))
                .and(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_NAME.eq(name))
                .execute()
        );
    }

    @Override
    public void clearVolumeGroupMemberships(final Long volumeId) {
        JooqUtil.context(connectionProvider, context -> context
                .deleteFrom(INDEX_VOLUME_GROUP_LINK)
                .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID.eq(volumeId))
                .execute()
        );
    }
}
