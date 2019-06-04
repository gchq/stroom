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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FetchDataRetentionRulesAction;
import stroom.data.retention.shared.SaveDataRetentionRulesAction;
import stroom.docref.DocRef;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.RestResource;
import stroom.util.guice.GuiceUtil;

import java.util.Set;

public class DataRetentionModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DataRetentionRulesService.class).to(DataRetentionRulesServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(DataRetentionRulesResource.class);

        TaskHandlerBinder.create(binder())
                .bind(FetchDataRetentionRulesAction.class, FetchDataRetentionPolicyHandler.class)
                .bind(SaveDataRetentionRulesAction.class, SaveDataRetentionPolicyHandler.class);
    }

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
}