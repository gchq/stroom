package stroom.config.global.impl.db;

import stroom.config.global.impl.ConfigPropertyDao;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PropertyPath;

import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.inject.Inject;

import static stroom.config.impl.db.jooq.tables.Config.CONFIG;
import static stroom.config.impl.db.jooq.tables.ConfigUpdateTracker.CONFIG_UPDATE_TRACKER;

class ConfigPropertyDaoImpl implements ConfigPropertyDao {

    private static final int TRACKER_ID = 1;

    private static final Function<Record, ConfigProperty> RECORD_TO_CONFIG_PROPERTY_MAPPER = record -> {
        final ConfigProperty configProperty = new ConfigProperty(PropertyPath.fromPathString(record.get(CONFIG.NAME)));
        configProperty.setId(record.get(CONFIG.ID));
        configProperty.setVersion(record.get(CONFIG.VERSION));
        configProperty.setCreateTimeMs(record.get(CONFIG.CREATE_TIME_MS));
        configProperty.setCreateUser(record.get(CONFIG.CREATE_USER));
        configProperty.setUpdateTimeMs(record.get(CONFIG.UPDATE_TIME_MS));
        configProperty.setUpdateUser(record.get(CONFIG.UPDATE_USER));
        String value = record.get(CONFIG.VAL);
        // value col is not-null
        if (value.isEmpty()) {
            value = null;
        }
        configProperty.setDatabaseOverrideValue(value);
        return configProperty;
    };

    private static final BiFunction<ConfigProperty, ConfigRecord, ConfigRecord> CONFIG_PROPERTY_TO_RECORD_MAPPER =
            (configProperty, record) -> {
                record.from(configProperty);
                record.set(CONFIG.ID, configProperty.getId());
                record.set(CONFIG.VERSION, configProperty.getVersion());
                record.set(CONFIG.CREATE_TIME_MS, configProperty.getCreateTimeMs());
                record.set(CONFIG.CREATE_USER, configProperty.getCreateUser());
                record.set(CONFIG.UPDATE_TIME_MS, configProperty.getUpdateTimeMs());
                record.set(CONFIG.UPDATE_USER, configProperty.getUpdateUser());
                record.set(CONFIG.NAME, configProperty.getNameAsString());
                // DB doesn't allow null values so use empty string
                if (!configProperty.hasDatabaseOverride()) {
                    // If there is no value override then we don't want it in the DB
                    // Code further up the chain should have dealt with this
                    throw new RuntimeException(LogUtil.message(
                            "Trying to save a config record when there is no databaseValue {}",
                            configProperty));
                }
                record.set(CONFIG.VAL, configProperty.getDatabaseOverrideValue().getValueOrElse(""));
                return record;
            };

    private final GlobalConfigDbConnProvider globalConfigDbConnProvider;
    private final GenericDao<ConfigRecord, ConfigProperty, Integer> genericDao;

    @Inject
    ConfigPropertyDaoImpl(final GlobalConfigDbConnProvider globalConfigDbConnProvider) {
        this.globalConfigDbConnProvider = globalConfigDbConnProvider;
        this.genericDao = new GenericDao<>(
                globalConfigDbConnProvider,
                CONFIG,
                CONFIG.ID,
                CONFIG_PROPERTY_TO_RECORD_MAPPER,
                RECORD_TO_CONFIG_PROPERTY_MAPPER);
    }

    @Override
    public ConfigProperty create(final ConfigProperty configProperty) {
        return JooqUtil.transactionResult(globalConfigDbConnProvider, context -> {
            final ConfigProperty storedConfigProperty = genericDao.create(context, configProperty);
            updateTracker(context, storedConfigProperty);
            return storedConfigProperty;
        });
    }

    @Override
    public Optional<ConfigProperty> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public Optional<ConfigProperty> fetch(final String propertyName) {
        Objects.requireNonNull(propertyName);
        return JooqUtil.contextResult(globalConfigDbConnProvider, context -> context
                .selectFrom(CONFIG)
                .where(CONFIG.NAME.eq(propertyName))
                .fetchOptional())
                .map(RECORD_TO_CONFIG_PROPERTY_MAPPER);
    }

    @Override
    public Optional<Long> getLatestConfigUpdateTimeMs() {
        return JooqUtil.contextResult(globalConfigDbConnProvider, context ->
                context.select(CONFIG_UPDATE_TRACKER.UPDATE_TIME_MS)
                        .from(CONFIG_UPDATE_TRACKER)
                        .limit(1)
                        .fetchOptional(CONFIG_UPDATE_TRACKER.UPDATE_TIME_MS));
    }

    @Override
    public ConfigProperty update(final ConfigProperty configProperty) {
        return JooqUtil.transactionResultWithOptimisticLocking(
                globalConfigDbConnProvider,
                context -> {
                    final ConfigProperty updatedConfigProperty = genericDao.update(
                            context,
                            configProperty);
                    updateTracker(context, updatedConfigProperty);
                    return updatedConfigProperty;
                });
    }

    @Override
    public boolean delete(final int id) {
        return JooqUtil.transactionResult(globalConfigDbConnProvider, context -> {
            updateTracker(context, System.currentTimeMillis());
            return genericDao.delete(context, id);
        });
    }

    @Override
    public boolean delete(final String name) {
        return JooqUtil.transactionResult(globalConfigDbConnProvider, context -> {
            updateTracker(context, System.currentTimeMillis());
            return context.deleteFrom(CONFIG)
                    .where(CONFIG.NAME.eq(name))
                    .execute() > 0;
        });
    }

    @Override
    public boolean delete(final PropertyPath propertyPath) {
        return delete(propertyPath.toString());
    }

    @Override
    public List<ConfigProperty> list() {
        return JooqUtil.contextResult(globalConfigDbConnProvider, context -> context
                .fetch(CONFIG))
                .map(RECORD_TO_CONFIG_PROPERTY_MAPPER::apply);
    }

    private void updateTracker(final DSLContext context,
                               final ConfigProperty configProperty) {
        final long updateTimeMs = Objects.requireNonNull(configProperty.getUpdateTimeMs());
        updateTracker(context, updateTimeMs);
    }

    /**
     * The tracker table means we have a simple way of checking if any of the DB config has changed
     * which can include removal of records. ANY mutation of the config table MUST also
     * call updateTracker
     */
    private void updateTracker(final DSLContext context, final long updateTimeMs) {
        context.insertInto(
                CONFIG_UPDATE_TRACKER,
                CONFIG_UPDATE_TRACKER.ID,
                CONFIG_UPDATE_TRACKER.UPDATE_TIME_MS)
                .values(TRACKER_ID, updateTimeMs)
                .onDuplicateKeyUpdate()
                .set(CONFIG_UPDATE_TRACKER.UPDATE_TIME_MS, updateTimeMs)
                .execute();
    }
}
