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

package stroom.config.global.impl.db.migration;

import stroom.config.app.PropertyServiceConfig.PropertyServiceDbConfig;
import stroom.config.global.impl.db.GlobalConfigDbConnProvider;
import stroom.config.global.impl.db.GlobalConfigDbModule;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractConfigMigrationTest
        extends AbstractSingleFlywayMigrationTest<PropertyServiceDbConfig, GlobalConfigDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<PropertyServiceDbConfig, GlobalConfigDbConnProvider> getDatasourceModule() {
        return new GlobalConfigDbModule() {
            @Override
            protected List<String> getFlyWayLocations() {
                return mergeLocations(super.getFlyWayLocations(), getTestDataMigrationLocation());
            }

            // Override this, so we target a specific version and don't run all migrations
            @Override
            protected Optional<MigrationVersion> getMigrationTarget() {
                return Optional.ofNullable(getTargetVersion());
            }
        };
    }

    @Override
    protected Class<GlobalConfigDbConnProvider> getConnectionProviderType() {
        return GlobalConfigDbConnProvider.class;
    }
}
