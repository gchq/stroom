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

package stroom.processor.impl.db.migration;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.processor.impl.ProcessorConfig.ProcessorDbConfig;
import stroom.processor.impl.db.ProcessorDbConnProvider;
import stroom.processor.impl.db.ProcessorDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractProcessorMigrationTest
        extends AbstractSingleFlywayMigrationTest<ProcessorDbConfig, ProcessorDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<ProcessorDbConfig, ProcessorDbConnProvider> getDatasourceModule() {

        return new ProcessorDbModule() {

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
    protected Class<ProcessorDbConnProvider> getConnectionProviderType() {
        return ProcessorDbConnProvider.class;
    }
}
