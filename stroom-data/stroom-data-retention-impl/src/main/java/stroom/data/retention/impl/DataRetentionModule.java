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

package stroom.data.retention.impl;

import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class DataRetentionModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DataRetentionRulesService.class).to(DataRetentionRulesServiceImpl.class);
        bind(DataRetentionRulesProvider.class).to(DataRetentionRulesServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(DataRetentionRulesResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DataRetention.class, builder -> builder
                        .name(DataRetentionPolicyExecutor.JOB_NAME)
                        .description("Delete data that exceeds the retention period " +
                                     "specified by data retention policy")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression()));
    }

    private static class DataRetention extends RunnableWrapper {

        @Inject
        DataRetention(final DataRetentionPolicyExecutor dataRetentionPolicyExecutor) {
            super(dataRetentionPolicyExecutor::exec);
        }
    }
}
