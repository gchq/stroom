package stroom.index.impl.db;

import stroom.db.util.JooqUtil;
import stroom.index.dao.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolumeGroup;
import stroom.security.SecurityContext;

import javax.inject.Inject;

import java.util.List;

import static stroom.index.impl.db.Tables.INDEX_VOLUME_GROUP;
import static stroom.index.impl.db.Tables.INDEX_VOLUME_GROUP_LINK;

public class IndexVolumeGroupDaoImpl implements IndexVolumeGroupDao {

    private final ConnectionProvider connectionProvider;
    private final SecurityContext securityContext;

    @Inject
    public IndexVolumeGroupDaoImpl(final SecurityContext securityContext,
                                   final ConnectionProvider connectionProvider) {
        this.securityContext = securityContext;
        this.connectionProvider = connectionProvider;
    }

    @Override
    public List<String> getNames() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(INDEX_VOLUME_GROUP.NAME)
                .from(INDEX_VOLUME_GROUP)
                .fetch(INDEX_VOLUME_GROUP.NAME));
    }

    @Override
    public IndexVolumeGroup create(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> {
            context
                    .insertInto(INDEX_VOLUME_GROUP,
                            INDEX_VOLUME_GROUP.NAME,
                            INDEX_VOLUME_GROUP.CREATE_USER,
                            INDEX_VOLUME_GROUP.CREATE_TIME_MS)
                    .values(name, securityContext.getUserId(), System.currentTimeMillis())
                    .onDuplicateKeyIgnore()
                    .execute();

            return context
                    .select()
                    .from(INDEX_VOLUME_GROUP)
                    .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                    .fetchOneInto(IndexVolumeGroup.class);
        });
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME_GROUP)
                .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                .fetchOneInto(IndexVolumeGroup.class)
        );
    }

    @Override
    public void delete(final String name) {
        JooqUtil.context(connectionProvider, context -> {
            context
                    .deleteFrom(INDEX_VOLUME_GROUP_LINK)
                    .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_NAME.eq(name))
                    .execute();
            context.deleteFrom(INDEX_VOLUME_GROUP)
                    .where(INDEX_VOLUME_GROUP.NAME.eq(name)).execute();
        });
    }
}
