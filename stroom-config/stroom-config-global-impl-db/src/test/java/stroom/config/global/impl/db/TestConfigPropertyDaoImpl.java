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

import stroom.config.global.shared.ConfigProperty;
import stroom.config.impl.db.jooq.tables.Config;
import stroom.config.impl.db.jooq.tables.ConfigUpdateTracker;
import stroom.db.util.JooqUtil;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PropertyPath;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.jooq.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(GlobalConfigTestModule.class)
class TestConfigPropertyDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestConfigPropertyDaoImpl.class);
    private static final List<Table<?>> TABLES = List.of(
            Config.CONFIG,
            ConfigUpdateTracker.CONFIG_UPDATE_TRACKER);

    @Inject
    ConfigPropertyDaoImpl configPropertyDao;
    @Inject
    GlobalConfigDbConnProvider globalConfigDbConnProvider;

    @BeforeEach
    void setUp() throws SQLException {
        try (final Connection connection = globalConfigDbConnProvider.getConnection()) {
            for (final Table<?> table : TABLES) {
                final String tableName = table.getName();
                LOGGER.debug("Clearing table {}", tableName);
                DbTestUtil.clearTables(connection, List.of(tableName));
            }
        }
    }

    @Test
    void createAndFetch_byID() {
        final ConfigProperty configProperty = new ConfigProperty(PropertyPath.fromPathString("a.b.c"));
        configProperty.setDatabaseOverrideValue("foo");
        AuditUtil.stamp(() -> "testUser", configProperty);

        final ConfigProperty configProperty2 = configPropertyDao.create(configProperty);

        final ConfigProperty configProperty3 = configPropertyDao.fetch(configProperty2.getId())
                .orElseThrow();

        assertThat(configProperty3)
                .isEqualTo(configProperty2);

        assertThat(configProperty3.getDatabaseOverrideValue().getValue())
                .isEqualTo(configProperty.getDatabaseOverrideValue().getValue());
        assertThat(configProperty3.getName())
                .isEqualTo(configProperty.getName());
    }

    @Test
    void createAndFetch_byName() {
        assertThat(configPropertyDao.fetch("a.b.c"))
                .isEmpty();

        final ConfigProperty configProperty = new ConfigProperty(PropertyPath.fromPathString("a.b.c"));
        configProperty.setDatabaseOverrideValue("foo");
        AuditUtil.stamp(() -> "testUser", configProperty);

        final ConfigProperty configProperty2 = configPropertyDao.create(configProperty);

        final ConfigProperty configProperty3 = configPropertyDao.fetch(configProperty2.getName().toString())
                .orElseThrow();

        assertThat(configProperty3)
                .isEqualTo(configProperty2);

        assertThat(configProperty3.getDatabaseOverrideValue().getValue())
                .isEqualTo(configProperty.getDatabaseOverrideValue().getValue());
        assertThat(configProperty3.getName())
                .isEqualTo(configProperty.getName());
    }

    @Test
    void testUpdateTracker() {
        JooqUtil.context(globalConfigDbConnProvider, context -> {
            final long val = configPropertyDao.updateTracker(context, 789L);

            assertThat(val)
                    .isEqualTo(789L);
        });
    }

    @Test
    void getLatestConfigUpdateTimeMs() {
        Optional<Long> optTimeMs = configPropertyDao.getLatestConfigUpdateTimeMs();
        assertThat(optTimeMs)
                .isEmpty();

        JooqUtil.context(globalConfigDbConnProvider, context -> {
            configPropertyDao.updateTracker(context, 456L);
        });

        optTimeMs = configPropertyDao.getLatestConfigUpdateTimeMs();
        assertThat(optTimeMs)
                .hasValue(456L);
    }

    @Test
    void ensureTracker() {
        Optional<Long> optTimeMs = configPropertyDao.getLatestConfigUpdateTimeMs();
        assertThat(optTimeMs)
                .isEmpty();

        configPropertyDao.ensureTracker(123L);

        optTimeMs = configPropertyDao.getLatestConfigUpdateTimeMs();
        assertThat(optTimeMs)
                .hasValue(123L);
    }

    @Test
    void update() {
    }

    @Test
    void delete_byId() {
        final ConfigProperty configProperty = new ConfigProperty(PropertyPath.fromPathString("a.b.c"));
        configProperty.setDatabaseOverrideValue("foo");
        AuditUtil.stamp(() -> "testUser", configProperty);

        final ConfigProperty configProperty2 = configPropertyDao.create(configProperty);

        final ConfigProperty configProperty3 = configPropertyDao.fetch(configProperty2.getId())
                .orElseThrow();

        assertThat(configProperty3)
                .isEqualTo(configProperty2);
        assertThat(configProperty3.getName())
                .isEqualTo(configProperty.getName());

        final boolean didDelete = configPropertyDao.delete(configProperty2.getId());

        assertThat(didDelete)
                .isEqualTo(true);

        assertThat(configPropertyDao.fetch(configProperty2.getId()))
                .isEmpty();
    }

    @Test
    void testDelete_byName() {
        final ConfigProperty configProperty = new ConfigProperty(PropertyPath.fromPathString("a.b.c"));
        configProperty.setDatabaseOverrideValue("foo");
        AuditUtil.stamp(() -> "testUser", configProperty);

        final ConfigProperty configProperty2 = configPropertyDao.create(configProperty);

        final ConfigProperty configProperty3 = configPropertyDao.fetch(configProperty2.getId())
                .orElseThrow();

        assertThat(configProperty3)
                .isEqualTo(configProperty2);
        assertThat(configProperty3.getName())
                .isEqualTo(configProperty.getName());

        final boolean didDelete = configPropertyDao.delete(configProperty2.getName().toString());

        assertThat(didDelete)
                .isEqualTo(true);

        assertThat(configPropertyDao.fetch(configProperty2.getId()))
                .isEmpty();
    }

    @Test
    void testDelete_byPropPath() {
        final ConfigProperty configProperty = new ConfigProperty(PropertyPath.fromPathString("a.b.c"));
        configProperty.setDatabaseOverrideValue("foo");
        AuditUtil.stamp(() -> "testUser", configProperty);

        final ConfigProperty configProperty2 = configPropertyDao.create(configProperty);

        final ConfigProperty configProperty3 = configPropertyDao.fetch(configProperty2.getId())
                .orElseThrow();

        assertThat(configProperty3)
                .isEqualTo(configProperty2);
        assertThat(configProperty3.getName())
                .isEqualTo(configProperty.getName());

        final boolean didDelete = configPropertyDao.delete(configProperty2.getName());

        assertThat(didDelete)
                .isEqualTo(true);

        assertThat(configPropertyDao.fetch(configProperty2.getId()))
                .isEmpty();
    }

    @Test
    void list() {
        Stream.of("foo", "bar")
                .forEach(val -> {
                    final ConfigProperty configProperty = new ConfigProperty(
                            PropertyPath.fromParts("root", val));
                    configProperty.setDatabaseOverrideValue(val);
                    AuditUtil.stamp(() -> "testUser", configProperty);
                    configPropertyDao.create(configProperty);
                });

        final List<ConfigProperty> list = configPropertyDao.list();
        assertThat(list)
                .hasSize(2);
        assertThat(list)
                .extracting(ConfigProperty::getName)
                .containsExactlyInAnyOrder(
                        PropertyPath.fromPathString("root.foo"),
                        PropertyPath.fromPathString("root.bar"));
    }
}
