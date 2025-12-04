/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.config.global.impl.db;

import stroom.config.global.impl.ConfigPropertyDao;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.util.exception.DataChangedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;

import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.config.impl.db.jooq.tables.Config.CONFIG;
import static stroom.config.impl.db.jooq.tables.ConfigUpdateTracker.CONFIG_UPDATE_TRACKER;

class ConfigPropertyDaoImpl implements ConfigPropertyDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfigPropertyDaoImpl.class);

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
        LOGGER.debug(() -> LogUtil.message("Updating config prop {} with id {}, ver {}",
                configProperty.getName(), configProperty.getId(), configProperty.getVersion()));
        try {
            return JooqUtil.transactionResultWithOptimisticLocking(
                    globalConfigDbConnProvider,
                    context -> {
                        final ConfigProperty updatedConfigProperty = genericDao.update(
                                context,
                                configProperty);
                        updateTracker(context, updatedConfigProperty);
                        return updatedConfigProperty;
                    });
        } catch (final RuntimeException e) {
            if (e instanceof DataChangedException) {
                // Possible someone has made a direct change in the db, naughty, so change the tracker
                // such that the props will get refreshed on all nodes
                JooqUtil.context(globalConfigDbConnProvider, context ->
                        updateTracker(context, System.currentTimeMillis()));
            }
            throw e;
        }
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

    @Override
    public void ensureTracker(final long updateTimeMs) {
        JooqUtil.context(globalConfigDbConnProvider, context ->
                ensureTracker(context, updateTimeMs));

    }

    public void ensureTracker(final DSLContext context, final long updateTimeMs) {
        // Insert the rec only if it doesn't already exist
        context.insertInto(CONFIG_UPDATE_TRACKER,
                        CONFIG_UPDATE_TRACKER.ID,
                        CONFIG_UPDATE_TRACKER.UPDATE_TIME_MS)
                .select(context.select(DSL.inline(TRACKER_ID), DSL.val(updateTimeMs))
                        .where(DSL.notExists(context.select(CONFIG_UPDATE_TRACKER.ID)
                                .from(CONFIG_UPDATE_TRACKER)
                                .where(CONFIG_UPDATE_TRACKER.ID.equal(TRACKER_ID)))))
                .execute();
    }

    /**
     * The tracker table means we have a simple way of checking if any of the DB config has changed
     * which can include removal of records. ANY mutation of the config table MUST also
     * call updateTracker
     */
    // pkg private for testing
    long updateTracker(final DSLContext context, final long updateTimeMs) {
        ensureTracker(context, updateTimeMs);

        // Lock the row so we can check then update
        final Long dbUpdateTimeMs = NullSafe.get(context.select(CONFIG_UPDATE_TRACKER.UPDATE_TIME_MS)
                        .from(CONFIG_UPDATE_TRACKER)
                        .where(CONFIG_UPDATE_TRACKER.ID.eq(TRACKER_ID))
                        .forUpdate()
                        .fetchOne(),
                Record1::value1);

        Objects.requireNonNull(dbUpdateTimeMs);

        if (dbUpdateTimeMs >= updateTimeMs) {
            LOGGER.debug("Config tracker {} is ahead of {}", dbUpdateTimeMs, updateTimeMs);
            // Another thread has updated it with a more recent time so just use that
            return dbUpdateTimeMs;
        } else {
            LOGGER.debug("Updated config tracker to {}", updateTimeMs);
            context.update(CONFIG_UPDATE_TRACKER)
                    .set(CONFIG_UPDATE_TRACKER.UPDATE_TIME_MS, updateTimeMs)
                    .where(CONFIG_UPDATE_TRACKER.ID.eq(TRACKER_ID))
                    .execute();
            return updateTimeMs;
        }
    }
}
