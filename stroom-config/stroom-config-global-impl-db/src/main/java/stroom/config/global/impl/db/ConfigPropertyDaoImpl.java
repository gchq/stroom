package stroom.config.global.impl.db;

import stroom.config.global.api.ConfigProperty;
import stroom.config.global.impl.ConfigPropertyDao;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static stroom.config.impl.db.jooq.tables.Config.CONFIG;

class ConfigPropertyDaoImpl implements ConfigPropertyDao {
    private final ConnectionProvider connectionProvider;
    private final GenericDao<ConfigRecord, ConfigProperty, Integer> genericDao;

    @Inject
    ConfigPropertyDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.genericDao = new GenericDao<>(CONFIG, CONFIG.ID, ConfigProperty.class, connectionProvider);
    }

    @Override
    public ConfigProperty create(final ConfigProperty configProperty) {
        return genericDao.create(configProperty);
    }

    @Override
    public Optional<ConfigProperty> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public ConfigProperty update(final ConfigProperty configProperty) {
        return genericDao.update(configProperty);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public boolean delete(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .deleteFrom(CONFIG)
                .where(CONFIG.NAME.eq(name))
                .execute()) > 0;
    }

    @Override
    public List<ConfigProperty> list() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .fetch(CONFIG)
                .into(ConfigProperty.class));
    }
}
