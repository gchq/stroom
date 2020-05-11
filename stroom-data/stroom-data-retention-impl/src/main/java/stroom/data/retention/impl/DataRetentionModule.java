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

import stroom.data.retention.shared.DataRetentionRules;
import stroom.docref.DocRef;
import stroom.job.api.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.inject.Inject;
import java.util.Set;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class DataRetentionModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DataRetentionRulesService.class).to(DataRetentionRulesServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(DataRetentionRulesResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DataRetention.class, builder -> builder
                        .withName("Data Retention")
                        .withDescription("Delete data that exceeds the retention period " +
                                "specified by data retention policy")
                        .withSchedule(CRON, "0 0 *"));
    }

    @SuppressWarnings("unused") // called by guice
    @Provides
    DataRetentionRules getRules(final DataRetentionRulesService dataRetentionRulesService) {
        DataRetentionRules dataRetentionRules = null;
        final Set<DocRef> set = dataRetentionRulesService.listDocuments();
        if (set != null && set.size() == 1) {
            dataRetentionRules = dataRetentionRulesService.readDocument(set.iterator().next());
        }

        if (dataRetentionRules != null) {
            return dataRetentionRules;
        }

        return null;
    }

    private static class DataRetention extends RunnableWrapper {
        @Inject
        DataRetention(final DataRetentionPolicyExecutor dataRetentionPolicyExecutor) {
            super(dataRetentionPolicyExecutor::exec);
        }
    }
}