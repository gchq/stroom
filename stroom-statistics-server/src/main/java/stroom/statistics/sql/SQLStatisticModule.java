/*
 * Copyright 2018 Crown Copyright
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

package stroom.statistics.sql;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.StroomDatabaseInfo;
import stroom.jobsystem.ClusterLockService;
import stroom.properties.StroomPropertyService;
import stroom.statistics.sql.datasource.StatisticStoreCache;
import stroom.statistics.sql.datasource.StatisticStoreValidator;
import stroom.task.TaskContext;
import stroom.task.TaskHandler;
import stroom.task.TaskManager;
import stroom.util.spring.StroomScope;

import javax.inject.Named;
import javax.sql.DataSource;

public class SQLStatisticModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SQLStatisticCache.class).to(SQLStatisticCacheImpl.class);
        bind(Statistics.class).to(SQLStatisticEventStore.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(SQLStatisticFlushTaskHandler.class);
    }
}