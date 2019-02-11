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
import stroom.data.retention.shared.FetchDataRetentionRulesAction;
import stroom.data.retention.shared.SaveDataRetentionRulesAction;
import stroom.task.api.TaskHandlerBinder;

public class DataRetentionModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DataRetentionRulesService.class).to(DataRetentionRulesServiceImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(FetchDataRetentionRulesAction.class, FetchDataRetentionPolicyHandler.class)
                .bind(SaveDataRetentionRulesAction.class, SaveDataRetentionPolicyHandler.class);
    }
}