/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.policy;

import stroom.ruleset.shared.DataRetentionPolicy;
import stroom.ruleset.shared.FetchDataRetentionPolicyAction;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchDataRetentionPolicyAction.class)
class FetchDataRetentionPolicyHandler extends AbstractTaskHandler<FetchDataRetentionPolicyAction, DataRetentionPolicy> {
    private final DataRetentionService dataRetentionService;
    private final Security security;

    @Inject
    FetchDataRetentionPolicyHandler(final DataRetentionService dataRetentionService,
                                    final Security security) {
        this.dataRetentionService = dataRetentionService;
        this.security = security;
    }

    @Override
    public DataRetentionPolicy exec(final FetchDataRetentionPolicyAction task) {
        return security.secureResult(dataRetentionService::load);
    }
}
