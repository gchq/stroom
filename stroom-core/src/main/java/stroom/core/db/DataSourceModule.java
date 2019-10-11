/*
 * Copyright 2016 Crown Copyright
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

package stroom.core.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.HikariConfigHolder;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.GuiceUtil;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Configures anything related to persistence, e.g. transaction management, the
 * entity manager factory, data sources.
 */
public class DataSourceModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceModule.class);
    private static final String MODULE = "datasource-module";

    @Override
    protected void configure() {
        // Force creation of connection provider so that legacy migration code executes.
        bind(DataSource.class).toProvider(DataSourceProvider.class).asEagerSingleton();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(CoreDbConnectionProvider.class);

        TaskHandlerBinder.create(binder())
                .bind(FindSystemTableStatusAction.class, FindSystemTableStatusHandler.class);
    }

    @Provides
    @Singleton
    CoreDbConnectionProvider getConnectionProvider(final Provider<CoreConfig> configProvider,
                                                   final HikariConfigHolder hikariConfigHolder) {
        LOGGER.info("Creating connection provider for {}", MODULE);
        final HikariConfig config = hikariConfigHolder.getHikariConfig(configProvider.get());
        final CoreDbConnectionProvider coreDbConnectionProvider = new CoreDbConnectionProvider(config);
//        flyway(connectionProvider);
        return coreDbConnectionProvider;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
