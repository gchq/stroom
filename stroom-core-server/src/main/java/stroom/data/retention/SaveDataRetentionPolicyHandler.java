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

package stroom.data.retention;

import stroom.receive.rules.shared.DataRetentionPolicy;
import stroom.receive.rules.shared.SaveDataRetentionPolicyAction;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class SaveDataRetentionPolicyHandler extends AbstractTaskHandler<SaveDataRetentionPolicyAction, DataRetentionPolicy> {
    private final DataRetentionService dataRetentionService;
    private final Security security;

    @Inject
    SaveDataRetentionPolicyHandler(final DataRetentionService dataRetentionService,
                                   final Security security) {
        this.dataRetentionService = dataRetentionService;
        this.security = security;
    }

    @Override
    public DataRetentionPolicy exec(final SaveDataRetentionPolicyAction task) {
        return security.secureResult(() -> dataRetentionService.save(task.getDataRetentionPolicy()));
    }
}
