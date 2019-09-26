package stroom.config.global.impl.db;

import org.jooq.Record;
import stroom.config.global.api.ConfigProperty;
import stroom.config.global.impl.ConfigPropertyDao;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.config.impl.db.jooq.tables.Config.CONFIG;

class ConfigPropertyDaoImpl implements ConfigPropertyDao {
    private static final Function<Record, ConfigProperty> RECORD_TO_CONFIG_PROPERTY_MAPPER = record -> {
        final ConfigProperty configProperty = new ConfigProperty();
        configProperty.setId(record.get(CONFIG.ID));
        configProperty.setVersion(record.get(CONFIG.VERSION));
        configProperty.setCreateTimeMs(record.get(CONFIG.CREATE_TIME_MS));
        configProperty.setCreateUser(record.get(CONFIG.CREATE_USER));
        configProperty.setUpdateTimeMs(record.get(CONFIG.UPDATE_TIME_MS));
        configProperty.setUpdateUser(record.get(CONFIG.UPDATE_USER));
        configProperty.setName(record.get(CONFIG.NAME));
        String value = record.get(CONFIG.VAL);
        // value col is not-null
        if (value.isEmpty()) {
            value = null;
        }
        configProperty.setDatabaseOverrideValue(value);
        return configProperty;
    };

    private static final BiFunction<ConfigProperty, ConfigRecord, ConfigRecord> CONFIG_PROPERTY_TO_RECORD_MAPPER = (configProperty, record) -> {
        record.from(configProperty);
        record.set(CONFIG.ID, configProperty.getId());
        record.set(CONFIG.VERSION, configProperty.getVersion());
        record.set(CONFIG.CREATE_TIME_MS, configProperty.getCreateTimeMs());
        record.set(CONFIG.CREATE_USER, configProperty.getCreateUser());
        record.set(CONFIG.UPDATE_TIME_MS, configProperty.getUpdateTimeMs());
        record.set(CONFIG.UPDATE_USER, configProperty.getUpdateUser());
        record.set(CONFIG.NAME, configProperty.getName());
        // DB doesn't allow null values so use empty string
        if (!configProperty.hasDatabaseOverride()) {
            // If there is no value override then we don't want it in the DB
            // Code further up the chain should have dealt with this
            throw new RuntimeException(LogUtil.message("Trying to save a config record when there is no databaseValue {}",
                    configProperty));
        }
        record.set(CONFIG.VAL, configProperty.getDatabaseOverrideValue().getValueOrElse(""));
        return record;
    };

    private final ConnectionProvider connectionProvider;
    private final GenericDao<ConfigRecord, ConfigProperty, Integer> genericDao;

    @Inject
    ConfigPropertyDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.genericDao = new GenericDao<>(CONFIG, CONFIG.ID, ConfigProperty.class, connectionProvider);
        genericDao.setRecordToObjectMapper(RECORD_TO_CONFIG_PROPERTY_MAPPER);
        genericDao.setObjectToRecordMapper(CONFIG_PROPERTY_TO_RECORD_MAPPER);
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
                .map(RECORD_TO_CONFIG_PROPERTY_MAPPER::apply));
    }
}
